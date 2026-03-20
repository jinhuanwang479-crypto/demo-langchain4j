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

@Service
/**
 * AI 对话观测编排服务。
 * 这是 `/chat` 真正的监测入口，负责把 LangChain4j 的流式回调串成完整链路：
 * 1. 请求开始时创建上下文；
 * 2. partial token 到达时记录首字耗时和输出长度；
 * 3. 检索/工具回调时补充请求细节；
 * 4. 请求结束时持久化、评估并上报指标。
 */
public class AiChatObservationService {

    private static final Logger log = LoggerFactory.getLogger(AiChatObservationService.class);
    //set注入
    private final ConsultantService consultantService;
    private final AiObservabilityService aiObservabilityService;
    private final AiObservationRegistry aiObservationRegistry;
    private final AiRetrievalAuditService aiRetrievalAuditService;
    private final AiObservabilityProperties observabilityProperties;
    private final MeterRegistry meterRegistry;

    public AiChatObservationService(ConsultantService consultantService,
                                    AiObservabilityService aiObservabilityService,
                                    AiObservationRegistry aiObservationRegistry,
                                    AiRetrievalAuditService aiRetrievalAuditService,
                                    AiObservabilityProperties observabilityProperties,
                                    MeterRegistry meterRegistry) {
        this.consultantService = consultantService;
        this.aiObservabilityService = aiObservabilityService;
        this.aiObservationRegistry = aiObservationRegistry;
        this.aiRetrievalAuditService = aiRetrievalAuditService;
        this.observabilityProperties = observabilityProperties;
        this.meterRegistry = meterRegistry;
    }

    public void streamChat(String memoryId, String message, FluxSink<String> emitter) {
        // 每次请求都创建独立上下文，避免不同会话互相污染。
        AiChatObservationContext context = new AiChatObservationContext(
                memoryId,
                UserContextUtil.getTenantId(),
                UserContextUtil.getUserId(),
                message
        );
        AtomicBoolean finalized = new AtomicBoolean(false);
        aiObservationRegistry.register(context);

        log.info("AI 对话开始: requestId={}, memoryId={}, tenantId={}, userId={}",
                context.getRequestId(), memoryId, context.getTenantId(), context.getUserId());

        try {
            consultantService.chat(memoryId, message)
                    .onPartialResponse(partial -> {
                        context.onPartialResponse(partial);
                        emitter.next(partial);
                    })
                    .onRetrieved(context::onRetrieved)
                    .onToolExecuted(toolExecution -> context.onToolExecuted(toolExecution, observabilityProperties))
                    .onCompleteResponse(response -> finalizeSuccess(context, finalized, response, emitter))
                    .onError(error -> finalizeError(context, finalized, error, emitter))
                    .start();
        } catch (Exception ex) {
            finalizeError(context, finalized, ex, emitter);
        }
    }

    private void finalizeSuccess(AiChatObservationContext context,
                                 AtomicBoolean finalized,
                                 dev.langchain4j.model.chat.response.ChatResponse response,
                                 FluxSink<String> emitter) {
        // compareAndSet 保证收尾逻辑只执行一次，避免成功/失败回调重复落库。
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        try {
            context.onComplete(response, observabilityProperties);
            applyRetrievalAudit(context);
            AiRequestTrace trace = aiObservabilityService.saveObservation(context);
            publishMetrics(trace);
            log.info("AI 对话完成: requestId={}, memoryId={}, finishReason={}, latencyMs={}, evaluationStatus={}",
                    trace.getRequestId(), trace.getMemoryId(), trace.getFinishReason(), trace.getLatencyMs(), trace.getEvaluationStatus());
            emitter.complete();
        } catch (Exception ex) {
            log.error("AI 对话成功回调收尾失败: requestId={}", context.getRequestId(), ex);
            emitter.error(ex);
        } finally {
            aiObservationRegistry.remove(context);
        }
    }

    private void finalizeError(AiChatObservationContext context,
                               AtomicBoolean finalized,
                               Throwable error,
                               FluxSink<String> emitter) {
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        try {
            context.onError(error, observabilityProperties);
            applyRetrievalAudit(context);
            AiRequestTrace trace = aiObservabilityService.saveObservation(context);
            publishMetrics(trace);
            log.error("AI 对话失败: requestId={}, memoryId={}, latencyMs={}, error={}",
                    trace.getRequestId(), trace.getMemoryId(), trace.getLatencyMs(), trace.getErrorMessage());
        } catch (Exception ex) {
            log.error("AI 对话异常回调收尾失败: requestId={}", context.getRequestId(), ex);
        } finally {
            aiObservationRegistry.remove(context);
            emitter.error(error);
        }
    }

    private void applyRetrievalAudit(AiChatObservationContext context) {
        // 从审计缓存里取出检索结果，兼容某些异步时序下 onRetrieved 与收尾回调不一致的问题。
        AiRetrievalAuditService.RetrievalAuditRecord record =
                aiRetrievalAuditService.consume(context.getMemoryId(), context.getQuestion());
        if (record != null) {
            context.recordRetrievalOutcome(record.retrievedCount(), record.topScore(), record.rejectionReason());
        }
    }

    private void publishMetrics(AiRequestTrace trace) {
        // 请求级指标统一在这里发布，便于后续扩展。
        Counter.builder("ai_chat_requests_total")
                .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                .tag("evaluationStatus", trace.getEvaluationStatus() == null ? "UNKNOWN" : trace.getEvaluationStatus())
                .register(meterRegistry)
                .increment();

        if (trace.getLatencyMs() != null) {
            Timer.builder("ai_chat_latency")
                    .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                    .register(meterRegistry)
                    .record(trace.getLatencyMs(), TimeUnit.MILLISECONDS);
            if (trace.getLatencyMs() > observabilityProperties.getSlowThresholdMs()) {
                Counter.builder("ai_chat_slow_requests_total")
                        .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                        .register(meterRegistry)
                        .increment();
            }
        }

        if (trace.getFirstTokenLatencyMs() != null) {
            Timer.builder("ai_chat_first_token_latency")
                    .tag("status", trace.getStatus() == null ? "UNKNOWN" : trace.getStatus())
                    .register(meterRegistry)
                    .record(trace.getFirstTokenLatencyMs(), TimeUnit.MILLISECONDS);
        }

        if (trace.getEvaluationScore() != null) {
            DistributionSummary.builder("ai_chat_evaluation_score")
                    .register(meterRegistry)
                    .record(trace.getEvaluationScore());
        }
    }
}
