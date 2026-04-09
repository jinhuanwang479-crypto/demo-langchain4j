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

/**
 * AI 在线评估服务。
 *
 * <p>该服务不依赖第二个大模型，而是基于请求 trace 和工具执行结果做规则化评分。
 * 这样做的好处是：
 * <ul>
 *   <li><b>结果稳定</b>：规则化评分避免了大模型评估的不确定性</li>
 *   <li><b>零成本</b>：没有额外的模型调用开销</li>
 *   <li><b>可解释性强</b>：每个扣分项都有明确的 reasonCode，便于定位问题</li>
 * </ul>
 *
 * <p>评估维度（加权求和）：
 * <ul>
 *   <li>执行质量（35%）：请求是否成功、响应是否为空、首字延迟是否过高</li>
 *   <li>证据支撑（30%）：检索结果数量、相似度分数是否达标、是否存在低置信度回答</li>
 *   <li>工具执行（20%）：工具调用是否成功、返回结果是否包含异常关键词</li>
 *   <li>回答文本（15%）：回答长度是否充足、是否包含兜底话术</li>
 * </ul>
 *
 * @author consultant
 * @see AiRequestTrace
 * @see AiToolTrace
 * @see AiEvaluationResult
 */
@Service
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
     *
     * <p>评估流程：
     * <ol>
     *   <li>分别计算四个维度的得分（执行、证据、工具、文本）</li>
     *   <li>按权重加权计算总分（执行35% + 证据30% + 工具20% + 文本15%）</li>
     *   <li>根据总分和特殊规则判定最终状态（PASS / WARN / FAIL）</li>
     *   <li>评估风险等级（HIGH / MEDIUM / LOW），用于告警决策</li>
     * </ol>
     *
     * @param trace       AI 请求追踪信息，包含请求状态、响应内容、检索结果等
     * @param toolTraces  工具调用追踪列表，包含每个工具的执行结果和异常信息
     * @return 评估结果，包含总分、状态、风险等级和扣分原因列表
     */
    public AiEvaluationResult evaluate(AiRequestTrace trace, List<AiToolTrace> toolTraces) {
        List<String> reasonCodes = new ArrayList<>();

        // 四个维度独立评分，每个维度都会向 reasonCodes 中添加扣分原因
        int executionScore = executionScore(trace, reasonCodes);   // 执行质量（35%）
        int groundingScore = groundingScore(trace, reasonCodes);   // 证据支撑（30%）
        int toolScore = toolScore(toolTraces, reasonCodes);        // 工具执行（20%）
        int responseScore = responseScore(trace, reasonCodes);     // 回答文本（15%）

        // 加权计算总分
        int overallScore = (int) Math.round(executionScore * 0.35
                + groundingScore * 0.30
                + toolScore * 0.20
                + responseScore * 0.15);

        // 根据总分和特殊规则判定最终状态
        String status = resolveStatus(trace, overallScore, reasonCodes);
        // 评估风险等级（用于告警和人工介入决策）
        String riskLevel = resolveRiskLevel(reasonCodes, status);

        return new AiEvaluationResult(overallScore, status, riskLevel, reasonCodes);
    }

    /**
     * 评估执行质量维度。
     *
     * <p>关注点：这轮调用是否正常完成，不关心回答内容本身是否专业。
     *
     * <p>扣分规则：
     * <ul>
     *   <li>trace 缺失 → 0分（TRACE_MISSING）</li>
     *   <li>请求失败（网络错误等）→ 0分（REQUEST_ERROR）</li>
     *   <li>响应内容为空 → 0分（EMPTY_RESPONSE）</li>
     *   <li>首字延迟超过慢阈值 → 80分（FIRST_TOKEN_SLOW）</li>
     *   <li>正常完成 → 100分</li>
     * </ul>
     *
     * @param trace       AI 请求追踪信息
     * @param reasonCodes 扣分原因列表（用于收集本次扣分的 reasonCode）
     * @return 执行质量得分（0-100）
     */
    private int executionScore(AiRequestTrace trace, List<String> reasonCodes) {
        // 系统异常，没有生成 trace
        if (trace == null) {
            reasonCodes.add("TRACE_MISSING");
            return 0;
        }
        // 网络错误，请求失败
        if (!"SUCCESS".equalsIgnoreCase(trace.getStatus())) {
            reasonCodes.add("REQUEST_ERROR");
            return 0;
        }
        // AI 返回空字符串
        if (!StringUtils.hasText(trace.getResponse())) {
            reasonCodes.add("EMPTY_RESPONSE");
            return 0;
        }
        // AI 正常回答，但首字延迟过高（超过配置的慢阈值）
        if (trace.getFirstTokenLatencyMs() != null
                && trace.getFirstTokenLatencyMs() > observabilityProperties.getSlowThresholdMs()) {
            reasonCodes.add("FIRST_TOKEN_SLOW");
            return 80;
        }
        // AI 正常回答，响应速度快
        return 100;
    }

    /**
     * 评估证据支撑维度。
     *
     * <p>关注点：回答有没有被检索结果支撑，特别防止低置信度下硬回答。
     *
     * <p>扣分规则：
     * <ul>
     *   <li>无检索结果但回答内容充分（≥60字）→ 15分 + 记录 LOW_CONFIDENCE_CONFIDENT_ANSWER</li>
     *   <li>无检索结果 → 70分</li>
     *   <li>最高分 ≥ answerableMinScore（0.74）→ 100分</li>
     *   <li>最高分 ∈ [minScore, answerableMinScore) → 55分 + 记录 LOW_CONFIDENCE_RETRIEVAL</li>
     *   <li>最高分 < minScore → 25分 + 记录 RETRIEVAL_TOO_WEAK</li>
     * </ul>
     *
     * @param trace       AI 请求追踪信息
     * @param reasonCodes 扣分原因列表（用于收集本次扣分的 reasonCode）
     * @return 证据支撑得分（0-100）
     */
    private int groundingScore(AiRequestTrace trace, List<String> reasonCodes) {
        // 情况1：没有任何检索结果
        if (trace.getRetrievedCount() == null || trace.getRetrievedCount() <= 0) {
            // 没有检索结果，但 AI 仍然给出了实质性回答（长度≥60字）→ 这是高风险行为，可能产生幻觉
            if (StringUtils.hasText(trace.getRetrievalRejectedReason())
                    && hasSubstantiveAnswer(trace.getResponse())) {
                reasonCodes.add("LOW_CONFIDENCE_CONFIDENT_ANSWER");
                return 15;
            }
            return 70;
        }

        // 情况2：有检索结果，根据最高相似度分数判断
        double topScore = trace.getTopRetrievalScore() == null ? 0D : trace.getTopRetrievalScore();

        // 最高分达到可回答阈值（≥0.74）→ 证据充分
        if (topScore >= aiRagProperties.getRetrieval().getAnswerableMinScore()) {
            return 100;
        }

        // 最高分在 [minScore, answerableMinScore) 区间 → 置信度不足
        if (topScore >= aiRagProperties.getRetrieval().getMinScore()) {
            reasonCodes.add("LOW_CONFIDENCE_RETRIEVAL");
            // 在置信度不足的情况下仍然给出了实质性回答 → 额外标记高风险
            if (hasSubstantiveAnswer(trace.getResponse())) {
                reasonCodes.add("LOW_CONFIDENCE_CONFIDENT_ANSWER");
            }
            return 55;
        }

        // 最高分低于 minScore → 检索结果太弱，基本不相关
        reasonCodes.add("RETRIEVAL_TOO_WEAK");
        return 25;
    }

    /**
     * 评估工具执行维度。
     *
     * <p>关注点：工具是否调用成功，以及结果文本中是否出现失败迹象。
     *
     * <p>扣分规则：
     * <ul>
     *   <li>无工具调用 → 80分</li>
     *   <li>存在工具调用失败（success=false）→ 30分（TOOL_ERROR）</li>
     *   <li>工具返回结果包含失败关键词 → 50分（TOOL_RESULT_SUSPICIOUS）</li>
     *   <li>所有工具调用成功且结果正常 → 100分</li>
     * </ul>
     *
     * @param toolTraces  工具调用追踪列表
     * @param reasonCodes 扣分原因列表（用于收集本次扣分的 reasonCode）
     * @return 工具执行得分（0-100）
     */
    private int toolScore(List<AiToolTrace> toolTraces, List<String> reasonCodes) {
        // 没有工具调用，不扣分
        if (toolTraces == null || toolTraces.isEmpty()) {
            return 80;
        }

        // 检查是否有工具调用失败（success = false）
        boolean hasFailure = toolTraces.stream().anyMatch(trace -> !Boolean.TRUE.equals(trace.getSuccess()));
        if (hasFailure) {
            reasonCodes.add("TOOL_ERROR");
            return 30;
        }

        // 检查工具返回结果中是否包含失败/异常等关键词
        boolean suspicious = toolTraces.stream()
                .map(AiToolTrace::getResultPreview)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .anyMatch(this::containsFailureHint);
        if (suspicious) {
            reasonCodes.add("TOOL_RESULT_SUSPICIOUS");
            return 50;
        }

        // 所有工具调用成功且结果正常
        return 100;
    }

    /**
     * 评估回答文本质量维度。
     *
     * <p>关注点：回答是否过短、是否过于空泛或明显使用兜底话术。
     *
     * <p>扣分规则：
     * <ul>
     *   <li>回答为空 → 0分（EMPTY_RESPONSE）</li>
     *   <li>回答长度 < 20字符 → 35分（RESPONSE_TOO_SHORT）</li>
     *   <li>回答包含兜底话术 → 45分（GENERIC_FALLBACK）</li>
     *   <li>正常回答 → 90分（留10分余量，因为文本质量很难完美）</li>
     * </ul>
     *
     * @param trace       AI 请求追踪信息
     * @param reasonCodes 扣分原因列表（用于收集本次扣分的 reasonCode）
     * @return 回答文本质量得分（0-100）
     */
    private int responseScore(AiRequestTrace trace, List<String> reasonCodes) {
        String response = trace.getResponse();

        if (!StringUtils.hasText(response)) {
            reasonCodes.add("EMPTY_RESPONSE");
            return 0;
        }

        // 回答过短（少于20个字符），信息量不足
        if (response.trim().length() < 20) {
            reasonCodes.add("RESPONSE_TOO_SHORT");
            return 35;
        }

        // 回答包含通用兜底话术（如"无法确定"、"作为AI"等），说明 AI 没有给出实质性答案
        if (containsGenericFallback(response.toLowerCase())) {
            reasonCodes.add("GENERIC_FALLBACK");
            return 45;
        }

        return 90;
    }

    /**
     * 根据总分和特殊规则判定最终状态。
     *
     * <p>状态定义：
     * <ul>
     *   <li><b>PASS</b>：总分 ≥ 80 且没有高风险标记（LOW_CONFIDENCE_CONFIDENT_ANSWER），表示回答质量合格</li>
     *   <li><b>WARN</b>：总分 ≥ 60，表示存在一些问题但不致命，需要关注</li>
     *   <li><b>FAIL</b>：总分 < 60，或请求失败，或响应为空，表示本次回答不合格</li>
     * </ul>
     *
     * @param trace        AI 请求追踪信息
     * @param overallScore 加权总分
     * @param reasonCodes  扣分原因列表
     * @return 状态（PASS / WARN / FAIL）
     */
    private String resolveStatus(AiRequestTrace trace, int overallScore, List<String> reasonCodes) {
        // 请求失败或响应为空 → 直接判定为 FAIL
        if (!"SUCCESS".equalsIgnoreCase(trace.getStatus()) || reasonCodes.contains("EMPTY_RESPONSE")) {
            return "FAIL";
        }
        // 总分 ≥ 80 且没有"低置信度硬回答"标记 → PASS
        if (overallScore >= 80 && !reasonCodes.contains("LOW_CONFIDENCE_CONFIDENT_ANSWER")) {
            return "PASS";
        }
        // 总分 ≥ 60 → WARN（存在问题但不致命）
        if (overallScore >= 60) {
            return "WARN";
        }
        // 总分 < 60 → FAIL
        return "FAIL";
    }

    /**
     * 评估风险等级。
     *
     * <p>风险等级用于告警决策和人工介入优先级判断：
     * <ul>
     *   <li><b>HIGH</b>：存在高风险行为（低置信度硬回答、请求失败），需要立即关注</li>
     *   <li><b>MEDIUM</b>：存在中等风险（WARN状态、工具错误），建议关注</li>
     *   <li><b>LOW</b>：低风险，可忽略或仅记录</li>
     * </ul>
     *
     * @param reasonCodes 扣分原因列表
     * @param status      评估状态
     * @return 风险等级（HIGH / MEDIUM / LOW）
     */
    private String resolveRiskLevel(List<String> reasonCodes, String status) {
        // 高风险：低置信度硬回答 或 请求失败
        if (reasonCodes.contains("LOW_CONFIDENCE_CONFIDENT_ANSWER") || reasonCodes.contains("REQUEST_ERROR")) {
            return "HIGH";
        }
        // 中风险：WARN状态 或 工具错误
        if ("WARN".equals(status) || reasonCodes.contains("TOOL_ERROR")) {
            return "MEDIUM";
        }
        // 低风险：其他情况
        return "LOW";
    }

    /**
     * 判断回答是否包含实质性内容。
     *
     * <p>实质性回答定义为：非空且长度 ≥ 60 字符。
     * 这个阈值用于识别"低置信度硬回答"场景——没有检索支撑但仍然给出了较长的回答，更容易产生幻觉。
     *
     * @param response AI 的回答内容
     * @return true 表示包含实质性内容，false 表示回答过短或为空
     */
    private boolean hasSubstantiveAnswer(String response) {
        return StringUtils.hasText(response) && response.trim().length() >= 60;
    }

    /**
     * 判断工具返回结果中是否包含失败/异常的关键词。
     *
     * <p>检测的关键词包括：
     * <ul>
     *   <li>中文：失败、错误、异常</li>
     *   <li>英文：not found、illegalargumentexception、runtimeexception</li>
     * </ul>
     *
     * @param normalized 已转为小写的工具返回结果预览
     * @return true 表示包含失败关键词，false 表示结果正常
     */
    private boolean containsFailureHint(String normalized) {
        return normalized.contains("失败")
                || normalized.contains("错误")
                || normalized.contains("异常")
                || normalized.contains("not found")
                || normalized.contains("illegalargumentexception")
                || normalized.contains("runtimeexception");
    }

    /**
     * 判断回答是否包含通用兜底话术。
     *
     * <p>兜底话术是 AI 无法回答时的典型表达，包含这类话术说明 AI 没有给出实质性答案。
     *
     * <p>检测的话术包括：
     * <ul>
     *   <li>中文：无法确定、不能确定、作为AI、请提供更多信息</li>
     *   <li>英文：i cannot、i'm sorry</li>
     * </ul>
     *
     * @param normalized 已转为小写的 AI 回答内容
     * @return true 表示包含兜底话术，false 表示没有
     */
    private boolean containsGenericFallback(String normalized) {
        return normalized.contains("无法确定")
                || normalized.contains("不能确定")
                || normalized.contains("作为ai")
                || normalized.contains("请提供更多信息")
                || normalized.contains("i cannot")
                || normalized.contains("i'm sorry");
    }
}
