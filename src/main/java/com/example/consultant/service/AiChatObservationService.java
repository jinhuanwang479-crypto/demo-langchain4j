package com.example.consultant.service;

import com.example.consultant.aiService.ConsultantService;
import com.example.consultant.config.AiObservabilityProperties;
import com.example.consultant.pojo.AiRequestTrace;
import com.example.consultant.utils.UserContextUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话观测编排服务。
 *
 * <p>该类是 AI 可观测性系统的核心编排入口，负责将 LangChain4j 的流式回调串联成完整的观测链路。
 *
 * <p><b>核心职责</b>：
 * <ul>
 *   <li>每次对话请求创建独立的观测上下文（{@link AiChatObservationContext}）</li>
 *   <li>注册上下文到观测注册中心（{@link AiObservationRegistry}），便于跨组件访问</li>
 *   <li>将 LangChain4j 的各种回调（partial response、retrieved、tool executed、complete、error）
 *       桥接到观测上下文中</li>
 *   <li>请求结束时触发持久化、评估和指标上报</li>
 *   <li>确保收尾逻辑只执行一次（通过 AtomicBoolean）</li>
 * </ul>
 *
 * <p><b>典型调用链路</b>：
 * <pre>
 * /chat 请求
 *   → streamChat()
 *     → 创建 Context
 *     → 注册到 Registry
 *     → consultantService.chat() 启动流式调用
 *       → onPartialResponse → 记录首字延迟、累积响应
 *       → onRetrieved → 记录检索快照
 *       → onToolExecuted → 记录工具调用
 *       → onCompleteResponse → 持久化、上报指标
 *       → onError → 记录错误、持久化
 * </pre>
 *
 * <p><b>线程安全</b>：使用 AtomicBoolean 确保 finalize 方法只执行一次，
 * 避免成功和错误回调同时触发导致重复持久化。
 *
 * @author consultant
 * @see AiChatObservationContext
 * @see AiObservationRegistry
 * @see AiObservabilityService
 * @see ConsultantService
 */
@Service
public class AiChatObservationService {

    private static final Logger log = LoggerFactory.getLogger(AiChatObservationService.class);

    // ==================== 依赖注入 ====================
    private final ConsultantService consultantService;           // AI 对话服务（LangChain4j 封装）
    private final AiObservabilityService aiObservabilityService; // 可观测性服务（持久化、评估）
    private final AiObservationRegistry aiObservationRegistry;   // 观测上下文注册中心
    private final AiRetrievalAuditService aiRetrievalAuditService; // 检索审计服务（缓存检索结果）
    private final AiToolGuidanceService aiToolGuidanceService;   // 动态工具提示词服务
    private final AiObservabilityProperties observabilityProperties; // 可观测性配置
    private final MeterRegistry meterRegistry;                   // Micrometer 指标注册器

    /**
     * 构造 AI 对话观测编排服务。
     *
     * @param consultantService          AI 对话服务
     * @param aiObservabilityService     可观测性服务
     * @param aiObservationRegistry      观测上下文注册中心
     * @param aiRetrievalAuditService    检索审计服务
     * @param observabilityProperties    可观测性配置
     * @param meterRegistry              Micrometer 指标注册器
     */
    public AiChatObservationService(ConsultantService consultantService,
                                    AiObservabilityService aiObservabilityService,
                                    AiObservationRegistry aiObservationRegistry,
                                    AiRetrievalAuditService aiRetrievalAuditService,
                                    AiToolGuidanceService aiToolGuidanceService,
                                    AiObservabilityProperties observabilityProperties,
                                    MeterRegistry meterRegistry) {
        this.consultantService = consultantService;
        this.aiObservabilityService = aiObservabilityService;
        this.aiObservationRegistry = aiObservationRegistry;
        this.aiRetrievalAuditService = aiRetrievalAuditService;
        this.aiToolGuidanceService = aiToolGuidanceService;
        this.observabilityProperties = observabilityProperties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 执行流式对话并观测全过程。
     *
     * <p>这是对外暴露的核心方法，通过 FluxSink 实现响应式流式输出。
     *
     * <p><b>执行流程</b>：
     * <ol>
     *   <li>创建独立的观测上下文（每次请求都是全新的）</li>
     *   <li>将上下文注册到 AiObservationRegistry，便于其他组件（如检索器、工具）访问</li>
     *   <li>调用 consultantService.chat() 启动流式对话</li>
     *   <li>为各种回调绑定观测逻辑：</li>
     *   <ul>
     *     <li>onPartialResponse：记录响应片段到上下文，同时推送给客户端</li>
     *     <li>onRetrieved：记录检索快照到上下文</li>
     *     <li>onToolExecuted：记录工具调用信息到上下文</li>
     *     <li>onCompleteResponse：触发成功收尾流程</li>
     *     <li>onError：触发异常收尾流程</li>
     *   </ul>
     * </ol>
     *
     * @param memoryId  对话记忆ID（会话标识，用于多轮对话上下文关联）
     * @param message   用户发送的消息内容
     * @param emitter   Reactor 流式发射器，用于将响应片段推送给客户端
     */
    public void streamChat(String memoryId, String message, FluxSink<String> emitter) {
        // 每次请求都创建独立上下文，避免不同会话互相污染
        AiChatObservationContext context = new AiChatObservationContext(
                memoryId,
                UserContextUtil.getTenantId(),
                UserContextUtil.getUserId(),
                message
        );

        AtomicBoolean finalized = new AtomicBoolean(false);  // 确保收尾逻辑只执行一次
        aiObservationRegistry.register(context);

        log.info("AI 对话开始: requestId={}, memoryId={}, tenantId={}, userId={}",
                context.getRequestId(), memoryId, context.getTenantId(), context.getUserId());

        try {
            String availableToolGuidance = aiToolGuidanceService.buildAvailableToolGuidance();
            consultantService.chat(memoryId, availableToolGuidance, message)
                    .onPartialResponse(partial -> {
                        // 记录响应片段并推送给客户端
                        context.onPartialResponse(partial);
                        emitter.next(partial);
                    })
                    .onRetrieved(context::onRetrieved)
                    .onToolExecuted(toolExecution -> context.onToolExecuted(toolExecution, observabilityProperties))
                    .onCompleteResponse(response -> finalizeSuccess(context, finalized, response, emitter))
                    .onError(error -> finalizeError(context, finalized, error, emitter))
                    .start();
        } catch (Exception ex) {
            // 捕获启动阶段的异常（如参数校验失败、服务不可用等）
            finalizeError(context, finalized, ex, emitter);
        }
    }

    /**
     * 成功完成时的收尾处理。
     *
     * <p><b>执行流程</b>：
     * <ol>
     *   <li>使用 CAS 确保收尾逻辑只执行一次（防止成功和错误回调同时触发）</li>
     *   <li>调用 context.onComplete() 记录完成时间和 Token 使用量</li>
     *   <li>从审计服务中补充检索结果（解决异步时序问题）</li>
     *   <li>持久化观测数据并触发评估</li>
     *   <li>发布监控指标</li>
     *   <li>完成流式响应（emitter.complete()）</li>
     *   <li>从注册中心移除上下文</li>
     * </ol>
     *
     * @param context    观测上下文
     * @param finalized  防重复标记
     * @param response   LangChain4j 的完整响应（包含模型元数据和 Token 使用量）
     * @param emitter    流式发射器
     */
    private void finalizeSuccess(AiChatObservationContext context,
                                 AtomicBoolean finalized,
                                 dev.langchain4j.model.chat.response.ChatResponse response,
                                 FluxSink<String> emitter) {
        // CAS 保证收尾逻辑只执行一次，避免成功/失败回调重复落库
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        try {
            // 记录完成时间、Token 使用量等
            context.onComplete(response, observabilityProperties);

            // 从审计缓存中补充检索结果（兼容某些异步时序下 onRetrieved 与收尾回调不一致的问题）
            applyRetrievalAudit(context);

            // 持久化观测数据并触发质量评估
            AiRequestTrace trace = aiObservabilityService.saveObservation(context);

            // 发布监控指标
            publishMetrics(trace);

            log.info("AI 对话完成: requestId={}, memoryId={}, finishReason={}, latencyMs={}, evaluationStatus={}",
                    trace.getRequestId(), trace.getMemoryId(), trace.getFinishReason(),
                    trace.getLatencyMs(), trace.getEvaluationStatus());

            emitter.complete();
        } catch (Exception ex) {
            log.error("AI 对话成功回调收尾失败: requestId={}", context.getRequestId(), ex);
            emitter.error(ex);
        } finally {
            // 清理注册中心，避免内存泄漏
            aiObservationRegistry.remove(context);
        }
    }

    /**
     * 异常发生时的收尾处理。
     *
     * <p><b>执行流程</b>：
     * <ol>
     *   <li>使用 CAS 确保收尾逻辑只执行一次</li>
     *   <li>调用 context.onError() 记录错误信息和完成时间</li>
     *   <li>从审计服务中补充检索结果</li>
     *   <li>持久化观测数据（状态为 ERROR）</li>
     *   <li>发布监控指标</li>
     *   <li>从注册中心移除上下文</li>
     *   <li>将错误传递给客户端（emitter.error()）</li>
     * </ol>
     *
     * @param context    观测上下文
     * @param finalized  防重复标记
     * @param error      发生的异常
     * @param emitter    流式发射器
     */
    private void finalizeError(AiChatObservationContext context,
                               AtomicBoolean finalized,
                               Throwable error,
                               FluxSink<String> emitter) {
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        try {
            // 记录错误信息
            context.onError(error, observabilityProperties);

            // 从审计缓存中补充检索结果
            applyRetrievalAudit(context);

            // 持久化观测数据（状态为 ERROR）
            AiRequestTrace trace = aiObservabilityService.saveObservation(context);

            // 发布监控指标
            publishMetrics(trace);

            log.error("AI 对话失败: requestId={}, memoryId={}, latencyMs={}, error={}",
                    trace.getRequestId(), trace.getMemoryId(), trace.getLatencyMs(), trace.getErrorMessage());
        } catch (Exception ex) {
            log.error("AI 对话异常回调收尾失败: requestId={}", context.getRequestId(), ex);
        } finally {
            // 清理注册中心
            aiObservationRegistry.remove(context);
            // 将错误传递给客户端
            emitter.error(error);
        }
    }

    /**
     * 从审计缓存中补充检索结果到观测上下文。
     *
     * <p><b>为什么需要这个方法？</b>
     * <br>在某些异步场景下，检索器（StrictContentRetriever）的 recordRetrievalOutcome 调用
     * 可能发生在 onRetrieved 回调之后，导致上下文中缺少检索状态信息。
     * 审计服务作为中间缓存，可以在收尾阶段补充这些信息。
     *
     * <p>补充的数据包括：
     * <ul>
     *   <li>retrievedCount：检索到的内容数量</li>
     *   <li>topScore：最高相似度分数</li>
     *   <li>rejectionReason：拒答原因（如 low_confidence、no_hits）</li>
     * </ul>
     *
     * @param context 观测上下文
     */
    private void applyRetrievalAudit(AiChatObservationContext context) {
        // 从审计缓存里取出检索结果，兼容某些异步时序下 onRetrieved 与收尾回调不一致的问题
        AiRetrievalAuditService.RetrievalAuditRecord record =
                aiRetrievalAuditService.consume(context.getMemoryId(), context.getQuestion());
        if (record != null) {
            context.recordRetrievalOutcome(record.retrievedCount(), record.topScore(), record.rejectionReason());
        }
    }

    /**
     * 发布监控指标到 Micrometer。
     *
     * <p>发布的指标包括：
     * <ul>
     *   <li><b>ai_chat_requests_total</b>：请求总数（按 status 和 evaluationStatus 分组）</li>
     *   <li><b>ai_chat_latency</b>：请求总延迟分布</li>
     *   <li><b>ai_chat_slow_requests_total</b>：慢请求计数（延迟超过配置阈值）</li>
     *   <li><b>ai_chat_first_token_latency</b>：首字延迟分布</li>
     *   <li><b>ai_chat_evaluation_score</b>：质量评估分数分布</li>
     * </ul>
     *
     * <p>这些指标可以用于：
     * <ul>
     *   <li>Grafana 监控大盘</li>
     *   <li>告警规则配置（如慢请求率过高时告警）</li>
     *   <li>性能分析和容量规划</li>
     * </ul>
     *
     * @param trace AI 请求追踪对象（包含状态、延迟、评估分数等信息）
     */
    private void publishMetrics(AiRequestTrace trace) {
        // 请求总数计数器
        Counter.builder("ai_chat_requests_total")
                .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                .tag("evaluationStatus", trace.getEvaluationStatus() == null ? "UNKNOWN" : trace.getEvaluationStatus())
                .register(meterRegistry)
                .increment();

        // 总延迟分布
        if (trace.getLatencyMs() != null) {
            Timer.builder("ai_chat_latency")
                    .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                    .register(meterRegistry)
                    .record(trace.getLatencyMs(), TimeUnit.MILLISECONDS);

            // 慢请求计数（延迟超过配置的慢阈值）
            if (trace.getLatencyMs() > observabilityProperties.getSlowThresholdMs()) {
                Counter.builder("ai_chat_slow_requests_total")
                        .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                        .register(meterRegistry)
                        .increment();
            }
        }

        // 首字延迟分布
        if (trace.getFirstTokenLatencyMs() != null) {
            Timer.builder("ai_chat_first_token_latency")
                    .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                    .register(meterRegistry)
                    .record(trace.getFirstTokenLatencyMs(), TimeUnit.MILLISECONDS);
        }

        // 质量评估分数分布
        if (trace.getEvaluationScore() != null) {
            DistributionSummary.builder("ai_chat_evaluation_score")
                    .register(meterRegistry)
                    .record(trace.getEvaluationScore());
        }
    }
}
