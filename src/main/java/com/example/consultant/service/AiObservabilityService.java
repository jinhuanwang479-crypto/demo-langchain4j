package com.example.consultant.service;

import com.example.consultant.config.AiObservabilityProperties;
import com.example.consultant.dto.AiObservationDetail;
import com.example.consultant.dto.AiObservationPage;
import com.example.consultant.dto.AiObservationSummary;
import com.example.consultant.mapper.AiRequestTraceMapper;
import com.example.consultant.mapper.AiToolTraceMapper;
import com.example.consultant.pojo.AiEvaluationResult;
import com.example.consultant.pojo.AiRequestTrace;
import com.example.consultant.pojo.AiToolTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * AI 观测服务
 * <p>
 * 负责 AI 请求的可观测性数据收集、评估和查询，涵盖三类核心职责：
 * </p>
 * <ul>
 *     <li><b>数据持久化</b>：将一次 AI 请求的完整上下文（请求/响应、工具调用、检索快照等）整理成 trace 并落库</li>
 *     <li><b>在线评估</b>：在持久化前自动执行响应质量评估，计算得分、风险等级和问题原因码</li>
 *     <li><b>监控查询</b>：为后台监控页面提供汇总统计、分页列表和详情查询能力</li>
 * </ul>
 *
 * @author consultant-team
 * @since 1.0.0
 */
@Service
public class AiObservabilityService {

    private final AiRequestTraceMapper aiRequestTraceMapper;
    private final AiToolTraceMapper aiToolTraceMapper;// 工具调用追踪记录 Mapper
    // 响应评估服务
    private final AiResponseEvaluationService aiResponseEvaluationService;
    private final AiObservabilityProperties observabilityProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入所有依赖
     *
     * @param aiRequestTraceMapper       AI 请求追踪记录 Mapper
     * @param aiToolTraceMapper          AI 工具调用追踪记录 Mapper
     * @param aiResponseEvaluationService AI 响应评估服务（在线评估）
     * @param observabilityProperties    可观测性配置属性（如是否启用评估、是否持久化等）
     * @param objectMapper               JSON 序列化工具
     */
    public AiObservabilityService(AiRequestTraceMapper aiRequestTraceMapper,
                                  AiToolTraceMapper aiToolTraceMapper,
                                  AiResponseEvaluationService aiResponseEvaluationService,
                                  AiObservabilityProperties observabilityProperties,
                                  ObjectMapper objectMapper) {
        this.aiRequestTraceMapper = aiRequestTraceMapper;
        this.aiToolTraceMapper = aiToolTraceMapper;
        this.aiResponseEvaluationService = aiResponseEvaluationService;
        this.observabilityProperties = observabilityProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存一次 AI 观测记录
     * <p>
     * 执行流程：
     * <ol>
     *     <li>将运行时的观测上下文转换为可落库的 trace 对象</li>
     *     <li>序列化检索快照列表为 JSON 字符串</li>
     *     <li>如果启用了在线评估，调用评估服务并填充评估结果（得分、状态、风险等级、原因码）</li>
     *     <li>如果启用了持久化，先插入请求 trace，再批量插入工具调用 trace</li>
     * </ol>
     * </p>
     *
     * @param context AI 聊天观测上下文（包含请求、响应、工具调用、检索快照等运行时信息）
     * @return 保存后的 AI 请求追踪记录（包含生成的 ID 和评估结果）
     */
    public AiRequestTrace saveObservation(AiChatObservationContext context) {
        // 第一步：将运行态上下文转成可落库的 trace 对象
        AiRequestTrace trace = context.toRequestTrace();

        // 第二步：序列化检索快照（RAG 检索到的文档片段）为 JSON 存储
        trace.setRetrievalSnapshotJson(toJson(context.getRetrievalSnapshots()));

        // 第三步：如果配置启用了在线评估，执行评估并填充结果
        if (observabilityProperties.isEvalEnabled()) {
            AiEvaluationResult evaluationResult = aiResponseEvaluationService.evaluate(trace, context.getToolTraces());
            trace.setEvaluationScore(evaluationResult.getOverallScore());      // 总体得分（如 0-100）
            trace.setEvaluationStatus(evaluationResult.getStatus());           // 评估状态：PASS/WARN/FAIL
            trace.setRiskLevel(evaluationResult.getRiskLevel());               // 风险等级：HIGH/MEDIUM/LOW
            trace.setEvaluationReasonsJson(toJson(evaluationResult.getReasonCodes())); // 问题原因码列表
        }

        // 第四步：如果配置启用了持久化，将数据写入数据库
        if (observabilityProperties.isTracePersistenceEnabled()) {
            // 插入主请求追踪记录
            aiRequestTraceMapper.insert(trace);

            // 如果有工具调用记录，批量插入工具调用追踪表
            List<AiToolTrace> toolTraces = context.getToolTraces();
            if (!CollectionUtils.isEmpty(toolTraces)) {
                aiToolTraceMapper.batchInsert(toolTraces);
            }
        }

        return trace;
    }

    /**
     * 统计指定时间窗口内的观测数据汇总
     * <p>
     * 提供监控看板的核心指标，包括：
     * <ul>
     *     <li>请求总量</li>
     *     <li>错误率（非 SUCCESS 状态的请求占比）</li>
     *     <li>平均耗时 & P95 耗时</li>
     *     <li>平均评估得分</li>
     *     <li>按评估状态（PASS/WARN/FAIL）的分布计数</li>
     * </ul>
     * </p>
     *
     * @param startTime 起始时间（可选，为空时使用默认窗口）
     * @param endTime   结束时间（可选，为空时取当前时间）
     * @return 观测数据汇总对象
     */
    public AiObservationSummary summarize(LocalDateTime startTime, LocalDateTime endTime) {
        // 解析时间窗口：如果前端不传时间参数，则使用默认配置
        LocalDateTime resolvedEnd = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime resolvedStart = startTime == null
                ? resolvedEnd.minusHours(observabilityProperties.getDefaultSummaryWindowHours())
                : startTime;

        // 查询时间窗口内的所有请求追踪记录
        List<AiRequestTrace> traces = aiRequestTraceMapper.findForSummary(resolvedStart, resolvedEnd);

        AiObservationSummary summary = new AiObservationSummary();
        summary.setTotalRequests(traces.size());

        // 无数据时直接返回空汇总
        if (traces.isEmpty()) {
            return summary;
        }

        // 统计错误请求数（status 不为 "SUCCESS"）
        long errorCount = traces.stream()
                .filter(trace -> !"SUCCESS".equalsIgnoreCase(trace.getStatus()))
                .count();

        // 计算平均耗时（过滤掉耗时为空的记录）
        double avgLatency = traces.stream()
                .map(AiRequestTrace::getLatencyMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0D);

        // 计算平均评估得分（得分范围通常为 0-100）
        double avgScore = traces.stream()
                .map(AiRequestTrace::getEvaluationScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0D);

        // 收集所有有效耗时，用于后续的百分位数计算
        List<Long> latencies = traces.stream()
                .map(AiRequestTrace::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        // 填充汇总指标
        summary.setErrorRate((double) errorCount / traces.size());      // 错误率 = 错误数 / 总数
        summary.setAverageLatencyMs(avgLatency);                        // 平均耗时
        summary.setP95LatencyMs(percentile(latencies, 0.95D));          // P95 耗时（95% 的请求耗时低于此值）
        summary.setAverageScore(avgScore);                              // 平均评估得分

        // 按评估状态统计数量
        summary.setPassCount(traces.stream().filter(trace -> "PASS".equalsIgnoreCase(trace.getEvaluationStatus())).count());
        summary.setWarnCount(traces.stream().filter(trace -> "WARN".equalsIgnoreCase(trace.getEvaluationStatus())).count());
        summary.setFailCount(traces.stream().filter(trace -> "FAIL".equalsIgnoreCase(trace.getEvaluationStatus())).count());

        return summary;
    }

    /**
     * 分页查询观测记录列表
     * <p>
     * 主要用于前端监控台列表页，支持多维度筛选。
     * </p>
     *
     * @param startTime 起始时间（可选）
     * @param endTime   结束时间（可选）
     * @param memoryId  记忆 ID（可选，用于筛选特定对话的请求）
     * @param userId    用户 ID（可选，用于筛选特定用户的请求）
     * @param status    请求状态（可选，如 SUCCESS/ERROR/TIMEOUT）
     * @param riskLevel 风险等级（可选，如 HIGH/MEDIUM/LOW）
     * @param page      页码（从 1 开始）
     * @param size      每页条数（最大 100 条）
     * @return 分页结果对象，包含总数、当前页数据等
     */
    public AiObservationPage findPage(LocalDateTime startTime, LocalDateTime endTime, String memoryId,
                                      String userId, String status, String riskLevel, int page, int size) {
        // 参数规范化：页码最小为 1，每页条数限制在 1-100 之间
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        int offset = (resolvedPage - 1) * resolvedSize;  // MySQL LIMIT 偏移量

        AiObservationPage response = new AiObservationPage();
        response.setPage(resolvedPage);
        response.setSize(resolvedSize);

        // 查询符合条件的总记录数（用于前端分页组件计算总页数）
        response.setTotal(aiRequestTraceMapper.countPage(startTime, endTime, memoryId, userId, status, riskLevel));

        // 查询当前页的数据列表
        response.setItems(new ArrayList<>(aiRequestTraceMapper.findPage(
                startTime, endTime, memoryId, userId, status, riskLevel, offset, resolvedSize
        )));

        return response;
    }

    /**
     * 查询单次 AI 请求的完整详情
     * <p>
     * 详情页需要同时返回：
     * <ul>
     *     <li>主请求追踪记录（请求参数、响应内容、评估结果等）</li>
     *     <li>该请求涉及的所有工具调用明细（工具名称、输入输出、耗时等）</li>
     * </ul>
     * </p>
     *
     * @param requestId 请求唯一标识（由上游系统生成，用于关联请求和工具调用）
     * @return 观测详情对象，如果 requestId 不存在则返回 null
     */
    public AiObservationDetail findDetail(String requestId) {
        // 查询主请求追踪记录
        AiRequestTrace requestTrace = aiRequestTraceMapper.findByRequestId(requestId);
        if (requestTrace == null) {
            return null;
        }

        // 组装详情对象：主 trace + 关联的工具调用列表
        return new AiObservationDetail(requestTrace, aiToolTraceMapper.findByRequestId(requestId));
    }

    /**
     * 将对象序列化为 JSON 字符串
     * <p>
     * 用于持久化检索快照列表、评估原因码等复杂结构字段。
     * </p>
     *
     * @param value 待序列化的对象
     * @return JSON 字符串，若输入为 null 则返回 null
     * @throws IllegalStateException 序列化失败时抛出（包装为运行时异常）
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize observability payload", e);
        }
    }

    /**
     * 计算百分位数（Percentile）
     * <p>
     * 例如：P95 = 95% 的请求耗时低于此值，常用于衡量系统性能的典型上界。
     * </p>
     * <p>
     * 算法：使用 ceil 向上取整计算索引位置，确保结果能覆盖到实际请求。
     * </p>
     *
     * @param values    已排序的数值列表（升序）
     * @param percentile 百分位数，范围 0.0 ~ 1.0（如 0.95 表示 P95）
     * @return 对应百分位的数值，若列表为空则返回 0
     */
    private long percentile(List<Long> values, double percentile) {
        if (CollectionUtils.isEmpty(values)) {
            return 0L;
        }
        // 计算索引位置：ceil(n * p) - 1
        // 使用 ceil 确保结果不会偏小（例如：100条数据取P95，应取第95条而非第94条）
        int index = (int) Math.ceil(values.size() * percentile) - 1;
        return values.get(Math.max(index, 0));
    }
}
