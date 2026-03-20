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

@Service
/**
 * AI 观测服务。
 * 负责三类工作：
 * 1. 把一次请求的上下文整理成 trace 并持久化；
 * 2. 在持久化前执行在线评估；
 * 3. 为后台监控页面提供汇总、分页列表、详情查询能力。
 */
public class AiObservabilityService {

    private final AiRequestTraceMapper aiRequestTraceMapper;
    private final AiToolTraceMapper aiToolTraceMapper;
    private final AiResponseEvaluationService aiResponseEvaluationService;
    private final AiObservabilityProperties observabilityProperties;
    private final ObjectMapper objectMapper;

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

    public AiRequestTrace saveObservation(AiChatObservationContext context) {
        // 先把运行态上下文转成可落库对象，再补充 JSON 结构字段和评估结果。
        AiRequestTrace trace = context.toRequestTrace();
        trace.setRetrievalSnapshotJson(toJson(context.getRetrievalSnapshots()));

        if (observabilityProperties.isEvalEnabled()) {
            AiEvaluationResult evaluationResult = aiResponseEvaluationService.evaluate(trace, context.getToolTraces());
            trace.setEvaluationScore(evaluationResult.getOverallScore());
            trace.setEvaluationStatus(evaluationResult.getStatus());
            trace.setRiskLevel(evaluationResult.getRiskLevel());
            trace.setEvaluationReasonsJson(toJson(evaluationResult.getReasonCodes()));
        }

        if (observabilityProperties.isTracePersistenceEnabled()) {
            aiRequestTraceMapper.insert(trace);
            List<AiToolTrace> toolTraces = context.getToolTraces();
            if (!CollectionUtils.isEmpty(toolTraces)) {
                aiToolTraceMapper.batchInsert(toolTraces);
            }
        }

        return trace;
    }

    public AiObservationSummary summarize(LocalDateTime startTime, LocalDateTime endTime) {
        // 如果前端不传时间窗口，则默认统计最近 N 小时数据。
        LocalDateTime resolvedEnd = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime resolvedStart = startTime == null
                ? resolvedEnd.minusHours(observabilityProperties.getDefaultSummaryWindowHours())
                : startTime;

        List<AiRequestTrace> traces = aiRequestTraceMapper.findForSummary(resolvedStart, resolvedEnd);
        AiObservationSummary summary = new AiObservationSummary();
        summary.setTotalRequests(traces.size());
        if (traces.isEmpty()) {
            return summary;
        }

        long errorCount = traces.stream()
                .filter(trace -> !"SUCCESS".equalsIgnoreCase(trace.getStatus()))
                .count();
        double avgLatency = traces.stream()
                .map(AiRequestTrace::getLatencyMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0D);
        double avgScore = traces.stream()
                .map(AiRequestTrace::getEvaluationScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0D);
        List<Long> latencies = traces.stream()
                .map(AiRequestTrace::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        summary.setErrorRate((double) errorCount / traces.size());
        summary.setAverageLatencyMs(avgLatency);
        summary.setP95LatencyMs(percentile(latencies, 0.95D));
        summary.setAverageScore(avgScore);
        summary.setPassCount(traces.stream().filter(trace -> "PASS".equalsIgnoreCase(trace.getEvaluationStatus())).count());
        summary.setWarnCount(traces.stream().filter(trace -> "WARN".equalsIgnoreCase(trace.getEvaluationStatus())).count());
        summary.setFailCount(traces.stream().filter(trace -> "FAIL".equalsIgnoreCase(trace.getEvaluationStatus())).count());
        return summary;
    }

    public AiObservationPage findPage(LocalDateTime startTime, LocalDateTime endTime, String memoryId,
                                      String userId, String status, String riskLevel, int page, int size) {
        // 分页查询主要用于前端监控台列表页。
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        int offset = (resolvedPage - 1) * resolvedSize;

        AiObservationPage response = new AiObservationPage();
        response.setPage(resolvedPage);
        response.setSize(resolvedSize);
        response.setTotal(aiRequestTraceMapper.countPage(startTime, endTime, memoryId, userId, status, riskLevel));
        response.setItems(new ArrayList<>(aiRequestTraceMapper.findPage(
                startTime, endTime, memoryId, userId, status, riskLevel, offset, resolvedSize
        )));
        return response;
    }

    public AiObservationDetail findDetail(String requestId) {
        // 详情页需要同时返回主 trace 和工具调用明细。
        AiRequestTrace requestTrace = aiRequestTraceMapper.findByRequestId(requestId);
        if (requestTrace == null) {
            return null;
        }
        return new AiObservationDetail(requestTrace, aiToolTraceMapper.findByRequestId(requestId));
    }

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

    private long percentile(List<Long> values, double percentile) {
        if (CollectionUtils.isEmpty(values)) {
            return 0L;
        }
        int index = (int) Math.ceil(values.size() * percentile) - 1;
        return values.get(Math.max(index, 0));
    }
}
