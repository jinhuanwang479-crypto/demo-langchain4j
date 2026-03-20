package com.example.consultant.service;

import com.example.consultant.config.AiObservabilityProperties;
import com.example.consultant.config.AiRagProperties;
import com.example.consultant.pojo.AiEvaluationResult;
import com.example.consultant.pojo.AiRequestTrace;
import com.example.consultant.pojo.AiToolTrace;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * AI 在线评估服务。
 * 该服务不依赖第二个大模型，而是基于请求 trace 和工具执行结果做规则化评分。
 * 这样做的好处是：
 * 1. 结果稳定；
 * 2. 没有额外模型成本；
 * 3. 便于解释“为什么被判定为 WARN / FAIL”。
 */
public class AiResponseEvaluationService {

    private final AiRagProperties aiRagProperties;
    private final AiObservabilityProperties observabilityProperties;

    public AiResponseEvaluationService(AiRagProperties aiRagProperties,
                                       AiObservabilityProperties observabilityProperties) {
        this.aiRagProperties = aiRagProperties;
        this.observabilityProperties = observabilityProperties;
    }

    /**
     * 评估一轮回答的整体质量。
     * 最终得分由执行质量、证据支撑、工具执行情况、回答文本质量四部分加权组成。
     */
    public AiEvaluationResult evaluate(AiRequestTrace trace, List<AiToolTrace> toolTraces) {
        List<String> reasonCodes = new ArrayList<>();
        int executionScore = executionScore(trace, reasonCodes);
        int groundingScore = groundingScore(trace, reasonCodes);
        int toolScore = toolScore(toolTraces, reasonCodes);
        int responseScore = responseScore(trace, reasonCodes);

        int overallScore = (int) Math.round(executionScore * 0.35
                + groundingScore * 0.30
                + toolScore * 0.20
                + responseScore * 0.15);

        String status = resolveStatus(trace, overallScore, reasonCodes);
        String riskLevel = resolveRiskLevel(reasonCodes, status);
        return new AiEvaluationResult(overallScore, status, riskLevel, reasonCodes);
    }

    private int executionScore(AiRequestTrace trace, List<String> reasonCodes) {
        // 执行层关注“这轮调用是不是正常完成”，不关心回答内容本身是否专业。
        if (trace == null) {
            //系统异常，没有生成 trace
            reasonCodes.add("TRACE_MISSING（）");
            return 0;
        }
        if (!"SUCCESS".equalsIgnoreCase(trace.getStatus())) {
            //网络错误，请求失败
            reasonCodes.add("REQUEST_ERROR");
            return 0;
        }
        if (!StringUtils.hasText(trace.getResponse())) {
            //AI 返回空字符串
            reasonCodes.add("EMPTY_RESPONSE");
            return 0;
        }
        if (trace.getFirstTokenLatencyMs() != null
                && trace.getFirstTokenLatencyMs() > observabilityProperties.getSlowThresholdMs()) {
            //AI 正常回答，但首字等了 8 秒
            reasonCodes.add("FIRST_TOKEN_SLOW");
            return 80;
        }
        //AI 正常回答，响应速度快
        return 100;
    }

    private int groundingScore(AiRequestTrace trace, List<String> reasonCodes) {
        // 证据层关注“回答有没有被检索结果支撑”，特别防止低置信度下硬回答。
        if (trace.getRetrievedCount() == null || trace.getRetrievedCount() <= 0) {
            if (StringUtils.hasText(trace.getRetrievalRejectedReason())
                    && hasSubstantiveAnswer(trace.getResponse())) {
                reasonCodes.add("LOW_CONFIDENCE_CONFIDENT_ANSWER");
                return 15;
            }
            return 70;
        }

        double topScore = trace.getTopRetrievalScore() == null ? 0D : trace.getTopRetrievalScore();
        if (topScore >= aiRagProperties.getRetrieval().getAnswerableMinScore()) {
            return 100;
        }
        if (topScore >= aiRagProperties.getRetrieval().getMinScore()) {
            reasonCodes.add("LOW_CONFIDENCE_RETRIEVAL");
            if (hasSubstantiveAnswer(trace.getResponse())) {
                reasonCodes.add("LOW_CONFIDENCE_CONFIDENT_ANSWER");
            }
            return 55;
        }
        reasonCodes.add("RETRIEVAL_TOO_WEAK");
        return 25;
    }

    private int toolScore(List<AiToolTrace> toolTraces, List<String> reasonCodes) {
        // 工具层关注“是否调用成功，以及结果文本里是否出现失败迹象”。
        if (toolTraces == null || toolTraces.isEmpty()) {
            return 80;
        }
        boolean hasFailure = toolTraces.stream().anyMatch(trace -> !Boolean.TRUE.equals(trace.getSuccess()));
        if (hasFailure) {
            reasonCodes.add("TOOL_ERROR");
            return 30;
        }
        boolean suspicious = toolTraces.stream()
                .map(AiToolTrace::getResultPreview)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .anyMatch(this::containsFailureHint);
        if (suspicious) {
            reasonCodes.add("TOOL_RESULT_SUSPICIOUS");
            return 50;
        }
        return 100;
    }

    private int responseScore(AiRequestTrace trace, List<String> reasonCodes) {
        // 文本层关注回答是否过短、是否过于空泛或明显兜底。
        String response = trace.getResponse();
        if (!StringUtils.hasText(response)) {
            reasonCodes.add("EMPTY_RESPONSE");
            return 0;
        }
        if (response.trim().length() < 20) {
            reasonCodes.add("RESPONSE_TOO_SHORT");
            return 35;
        }
        if (containsGenericFallback(response.toLowerCase())) {
            reasonCodes.add("GENERIC_FALLBACK");
            return 45;
        }
        return 90;
    }

    private String resolveStatus(AiRequestTrace trace, int overallScore, List<String> reasonCodes) {
        if (!"SUCCESS".equalsIgnoreCase(trace.getStatus()) || reasonCodes.contains("EMPTY_RESPONSE")) {
            return "FAIL";
        }
        if (overallScore >= 80 && !reasonCodes.contains("LOW_CONFIDENCE_CONFIDENT_ANSWER")) {
            return "PASS";
        }
        if (overallScore >= 60) {
            return "WARN";
        }
        return "FAIL";
    }

    private String resolveRiskLevel(List<String> reasonCodes, String status) {
        if (reasonCodes.contains("LOW_CONFIDENCE_CONFIDENT_ANSWER") || reasonCodes.contains("REQUEST_ERROR")) {
            return "HIGH";
        }
        if ("WARN".equals(status) || reasonCodes.contains("TOOL_ERROR")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean hasSubstantiveAnswer(String response) {
        return StringUtils.hasText(response) && response.trim().length() >= 60;
    }

    private boolean containsFailureHint(String normalized) {
        return normalized.contains("失败")
                || normalized.contains("错误")
                || normalized.contains("异常")
                || normalized.contains("not found")
                || normalized.contains("illegalargumentexception")
                || normalized.contains("runtimeexception");
    }

    private boolean containsGenericFallback(String normalized) {
        return normalized.contains("无法确定")
                || normalized.contains("不能确定")
                || normalized.contains("作为ai")
                || normalized.contains("请提供更多信息")
                || normalized.contains("i cannot")
                || normalized.contains("i'm sorry");
    }
}
