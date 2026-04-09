package com.example.consultant.service;

import com.example.consultant.config.AiObservabilityProperties;
import com.example.consultant.pojo.AiRequestTrace;
import com.example.consultant.pojo.AiToolTrace;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 对话观测上下文。
 *
 * <p>该类是 AI 可观测性系统的核心数据收集器，用于在一次流式对话过程中累积所有关键信息，
 * 最终统一转换成请求 trace 与工具 trace 进行持久化和分析。
 *
 * <p><b>核心职责</b>：
 * <ul>
 *   <li>累积流式响应的部分内容，记录首字延迟</li>
 *   <li>记录检索结果的快照（文档名、页码、相似度分数、文本预览等）</li>
 *   <li>记录工具调用的执行结果、成功/失败状态、错误信息</li>
 *   <li>统计 Token 使用量、响应延迟等性能指标</li>
 *   <li>处理异常情况，生成错误状态的 trace</li>
 *   <li>最终生成标准化的 {@link AiRequestTrace} 和 {@link AiToolTrace} 对象</li>
 * </ul>
 *
 * <p><b>线程安全</b>：所有公共方法都使用 {@code synchronized} 修饰，确保在多线程环境下
 * （如流式响应的回调）数据一致性。
 *
 * @author consultant
 * @see AiRequestTrace
 * @see AiToolTrace
 * @see AiObservabilityProperties
 */
public class AiChatObservationContext {

    private static final Logger log = LoggerFactory.getLogger(AiChatObservationContext.class);

    /**
     * 用于从工具返回的 JSON 字符串中提取 message 字段的正则表达式。
     * 示例匹配：{"message": "错误信息"} → 提取 "错误信息"
     */
    private static final Pattern JSON_MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    // ==================== 请求标识信息 ====================
    private final String requestId = UUID.randomUUID().toString().replace("-", "");  // 全局唯一请求ID
    private final String memoryId;      // 对话记忆ID（会话标识）
    private final Long tenantId;        // 租户ID（多租户隔离）
    private final String userId;        // 用户ID
    private final String question;      // 用户原始问题

    // ==================== 时间信息 ====================
    private final LocalDateTime startedAt = LocalDateTime.now();    // 请求开始时间（本地时间）
    private final Instant startedInstant = Instant.now();           // 请求开始时间（瞬时时间，用于精确计算延迟）
    private LocalDateTime completedAt;                              // 请求完成时间
    private Instant firstTokenAt;                                   // 首字返回时间

    // ==================== 响应内容 ====================
    private final StringBuilder responseBuilder = new StringBuilder();  // 累积的完整响应文本
    private int streamedChars;                                          // 流式响应的字符总数

    // ==================== 检索信息 ====================
    private final List<Map<String, Object>> retrievalSnapshots = new ArrayList<>();  // 检索结果快照列表
    private int retrievedCount;                                          // 检索到的内容数量
    private Double topRetrievalScore;                                    // 最高相似度分数
    private String retrievalRejectedReason;                              // 检索被拒绝的原因（如 low_confidence、no_hits）

    // ==================== 工具调用信息 ====================
    private final List<AiToolTrace> toolTraces = new ArrayList<>();      // 工具调用追踪列表

    // ==================== 执行状态 ====================
    private String status = "SUCCESS";       // 执行状态：SUCCESS / ERROR
    private String errorMessage;              // 错误信息（status为ERROR时填充）

    // ==================== 模型信息 ====================
    private String modelName;                 // 使用的模型名称（如 gpt-4、qwen-max）
    private String finishReason;              // 模型结束原因（如 STOP、LENGTH）

    // ==================== 性能指标 ====================
    private Long latencyMs;                   // 总延迟（毫秒）
    private Long firstTokenLatencyMs;         // 首字延迟（毫秒）

    // ==================== Token 使用量 ====================
    private Integer inputTokens;              // 输入 Token 数量
    private Integer outputTokens;             // 输出 Token 数量
    private Integer totalTokens;              // 总 Token 数量

    /**
     * 构造 AI 对话观测上下文。
     *
     * @param memoryId 对话记忆ID（会话标识，用于关联同一会话的多轮对话）
     * @param tenantId 租户ID（用于多租户数据隔离）
     * @param userId   用户ID
     * @param question 用户原始问题
     */
    public AiChatObservationContext(String memoryId, Long tenantId, String userId, String question) {
        this.memoryId = memoryId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.question = question;
    }

    /**
     * 处理流式响应的部分内容。
     *
     * <p>每次收到流式响应的一个片段时调用此方法。该方法会：
     * <ul>
     *   <li>记录首字到达时间（第一次调用时）</li>
     *   <li>将片段追加到响应构建器中</li>
     *   <li>累加流式字符数</li>
     * </ul>
     *
     * @param partial 流式响应的部分文本片段
     */
    public synchronized void onPartialResponse(String partial) {
        if (partial == null) {
            return;
        }
        // 首次收到响应时，记录首字延迟
        if (firstTokenAt == null) {
            firstTokenAt = Instant.now();
            firstTokenLatencyMs = Duration.between(startedInstant, firstTokenAt).toMillis();
        }
        responseBuilder.append(partial);
        streamedChars += partial.length();
    }

    /**
     * 处理检索结果。
     *
     * <p>当执行完向量检索后调用此方法，记录检索到的内容快照。
     * 每个快照包含：文档名、页码、分块索引、相似度分数、embedding ID、文本预览。
     *
     * <p>注意：此方法会清空之前的检索快照，确保只保留最新一次检索的结果。
     *
     * @param contents 检索到的内容列表（来自 {@link dev.langchain4j.rag.content.retriever.ContentRetriever}）
     */
    public synchronized void onRetrieved(List<Content> contents) {
        retrievalSnapshots.clear();
        retrievedCount = contents == null ? 0 : contents.size();
        topRetrievalScore = null;
        if (contents == null) {
            return;
        }
        for (Content content : contents) {
            if (content == null || content.textSegment() == null) {
                continue;
            }
            Metadata metadata = content.textSegment().metadata();
            Map<ContentMetadata, Object> contentMetadata = content.metadata();
            Double score = contentMetadata == null ? null : asDouble(contentMetadata.get(ContentMetadata.SCORE));

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("docName", metadata == null ? null : metadata.getString("docName"));
            snapshot.put("pageNumber", metadata == null ? null : metadata.getInteger("pageNumber"));
            snapshot.put("chunkIndex", metadata == null ? null : metadata.getInteger("chunkIndex"));
            snapshot.put("score", score);
            snapshot.put("embeddingId", contentMetadata == null ? null : contentMetadata.get(ContentMetadata.EMBEDDING_ID));
            snapshot.put("textPreview", abbreviate(content.textSegment().text(), 300));
            retrievalSnapshots.add(snapshot);

            // 更新最高相似度分数
            if (score != null && (topRetrievalScore == null || score > topRetrievalScore)) {
                topRetrievalScore = score;
            }
        }
        // 有检索结果时，清除拒绝原因
        if (!retrievalSnapshots.isEmpty()) {
            retrievalRejectedReason = null;
        }
    }

    /**
     * 处理工具执行结果。
     *
     * <p>当 AI 调用工具并收到执行结果后调用此方法。
     * 会记录工具名称、参数、结果预览、执行成功/失败状态、错误信息等。
     *
     * @param toolExecution 工具执行信息（包含请求参数和执行结果）
     * @param properties    可观测性配置（用于控制结果预览的最大长度）
     */
    public synchronized void onToolExecuted(ToolExecution toolExecution, AiObservabilityProperties properties) {
        if (toolExecution == null || toolExecution.request() == null) {
            return;
        }

        String toolName = toolExecution.request().name();
        String arguments = toolExecution.request().arguments();
        String result = toolExecution.result();

        log.info("[AI-TOOL-EXECUTED] RequestId: {}, Sequence: {}, Tool: {}, Arguments: {}, Result: {}",
                requestId, toolTraces.size() + 1, toolName,
                abbreviate(arguments, 200), abbreviate(result, 300));

        AiToolTrace trace = new AiToolTrace();
        trace.setRequestId(requestId);
        trace.setSequenceNo(toolTraces.size() + 1);
        trace.setToolName(toolName);
        trace.setArgumentsJson(arguments);
        trace.setResultPreview(abbreviate(result, properties.getMaxToolResultPreviewLength()));
        trace.setSuccess(toolExecutionLooksSuccessful(result));
        trace.setCreatedAt(LocalDateTime.now());

        // 如果执行失败，提取错误信息
        if (!Boolean.TRUE.equals(trace.getSuccess())) {
            trace.setErrorMessage(extractToolErrorMessage(result));
        }
        toolTraces.add(trace);
    }

    /**
     * 处理对话完成事件。
     *
     * <p>当流式响应正常结束时调用此方法。该方法会：
     * <ul>
     *   <li>记录完成时间和总延迟</li>
     *   <li>提取模型名称、结束原因</li>
     *   <li>记录 Token 使用量</li>
     *   <li>如果响应构建器为空但 ChatResponse 中有消息，则从中提取响应内容</li>
     *   <li>根据配置截断过长的响应内容</li>
     * </ul>
     *
     * @param response   完整的 ChatResponse（包含模型元数据和 Token 使用量）
     * @param properties 可观测性配置（用于控制响应预览的最大长度）
     */
    public synchronized void onComplete(ChatResponse response, AiObservabilityProperties properties) {
        completedAt = LocalDateTime.now();
        latencyMs = Duration.between(startedInstant, Instant.now()).toMillis();

        if (response != null) {
            modelName = response.modelName();
            finishReason = response.finishReason() == null ? null : response.finishReason().name();

            TokenUsage tokenUsage = response.tokenUsage();
            if (tokenUsage != null) {
                inputTokens = tokenUsage.inputTokenCount();
                outputTokens = tokenUsage.outputTokenCount();
                totalTokens = tokenUsage.totalTokenCount();
            }

            // 处理非流式场景：响应构建器可能为空，需要从 ChatResponse 中提取
            if (responseBuilder.length() == 0 && response.aiMessage() != null) {
                responseBuilder.append(response.aiMessage().text());
                streamedChars = responseBuilder.length();
            }
        }

        trimResponse(properties);
    }

    /**
     * 处理错误事件。
     *
     * <p>当流式响应过程中发生异常时调用此方法。
     * 会将状态设置为 "ERROR"，记录错误信息、完成时间和总延迟。
     *
     * @param error      发生的异常
     * @param properties 可观测性配置（用于控制响应预览的最大长度）
     */
    public synchronized void onError(Throwable error, AiObservabilityProperties properties) {
        status = "ERROR";
        errorMessage = error == null ? "Unknown error" : abbreviate(error.getMessage(), 1000);
        completedAt = LocalDateTime.now();
        latencyMs = Duration.between(startedInstant, Instant.now()).toMillis();
        trimResponse(properties);
    }

    /**
     * 记录检索结果（用于从外部更新检索状态）。
     *
     * <p>此方法主要用于在检索器拒绝回答时，记录拒绝原因和检索统计信息。
     * 只有在当前值未设置时才会更新，避免覆盖已有的更详细的数据。
     *
     * @param retrievedCount  检索到的内容数量（大于0时且当前为0时更新）
     * @param topScore        最高相似度分数（非空且当前为空时更新）
     * @param rejectionReason 拒绝原因（非空且当前为空时更新）
     */
    public synchronized void recordRetrievalOutcome(int retrievedCount, Double topScore, String rejectionReason) {
        if (this.retrievedCount == 0 && retrievedCount > 0) {
            this.retrievedCount = retrievedCount;
        }
        if (this.topRetrievalScore == null && topScore != null) {
            this.topRetrievalScore = topScore;
        }
        if (this.retrievalRejectedReason == null && rejectionReason != null) {
            this.retrievalRejectedReason = rejectionReason;
        }
    }

    /**
     * 将观测上下文转换为标准的请求追踪对象。
     *
     * <p>将上下文中累积的所有信息组装成 {@link AiRequestTrace} 对象，
     * 用于持久化到数据库或发送到监控系统。
     *
     * @return 标准化的 AI 请求追踪对象
     */
    public synchronized AiRequestTrace toRequestTrace() {
        AiRequestTrace trace = new AiRequestTrace();
        trace.setRequestId(requestId);
        trace.setMemoryId(memoryId);
        trace.setTenantId(tenantId);
        trace.setUserId(userId);
        trace.setQuestion(question);
        trace.setResponse(responseBuilder.toString());
        trace.setStatus(status);
        trace.setErrorMessage(errorMessage);
        trace.setModelName(modelName);
        trace.setFinishReason(finishReason);
        trace.setLatencyMs(latencyMs);
        trace.setFirstTokenLatencyMs(firstTokenLatencyMs);
        trace.setStreamedChars(streamedChars);
        trace.setInputTokens(inputTokens);
        trace.setOutputTokens(outputTokens);
        trace.setTotalTokens(totalTokens);
        trace.setRetrievedCount(retrievedCount);
        trace.setTopRetrievalScore(topRetrievalScore);
        trace.setRetrievalRejectedReason(retrievalRejectedReason);
        trace.setToolCallCount(toolTraces.size());
        trace.setStartedAt(startedAt);
        trace.setCompletedAt(completedAt);
        return trace;
    }

    // ==================== Getter 方法 ====================

    public String getRequestId() {
        return requestId;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    /**
     * 获取检索快照列表的副本。
     *
     * @return 检索快照列表的新副本（避免外部修改）
     */
    public synchronized List<Map<String, Object>> getRetrievalSnapshots() {
        return new ArrayList<>(retrievalSnapshots);
    }

    /**
     * 获取工具追踪列表的副本。
     *
     * @return 工具追踪列表的新副本（避免外部修改）
     */
    public synchronized List<AiToolTrace> getToolTraces() {
        return new ArrayList<>(toolTraces);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据配置截断过长的响应内容。
     *
     * <p>防止存储过长的响应文本导致数据库或日志膨胀。
     *
     * @param properties 可观测性配置（包含 maxResponsePreviewLength）
     */
    private void trimResponse(AiObservabilityProperties properties) {
        int maxLength = properties.getMaxResponsePreviewLength();
        if (maxLength > 0 && responseBuilder.length() > maxLength) {
            responseBuilder.setLength(maxLength);
        }
    }

    /**
     * 将对象安全地转换为 Double。
     *
     * @param value 可能是 Number 类型或字符串类型的值
     * @return 转换后的 Double，如果无法转换则返回 null
     */
    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 截断字符串到指定最大长度。
     *
     * <p>用于控制日志和存储中的文本预览长度，避免过长。
     *
     * @param value    原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串（如果原字符串长度 ≤ maxLength 则返回原字符串）
     */
    private String abbreviate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 判断工具执行是否成功。
     *
     * <p>通过分析工具返回的结果文本来判断：
     * <ul>
     *   <li>结果为空或仅空白 → 失败</li>
     *   <li>包含 "success":false 或 "errorcode":"permission_denied" → 失败</li>
     *   <li>包含中文失败关键词（失败、错误、异常、无权）→ 失败</li>
     *   <li>包含英文失败关键词（permission_denied、forbidden、not found 等）→ 失败</li>
     *   <li>其他情况 → 成功</li>
     * </ul>
     *
     * @param result 工具返回的结果文本
     * @return true 表示工具执行成功，false 表示失败
     */
    private boolean toolExecutionLooksSuccessful(String result) {
        if (result == null || result.isBlank()) {
            return false;
        }
        String normalized = result.toLowerCase();

        // JSON 格式的失败标记
        if (normalized.contains("\"success\":false") || normalized.contains("\"success\" : false")) {
            return false;
        }
        // 权限错误标记
        if (normalized.contains("\"errorcode\":\"permission_denied\"")
                || normalized.contains("\"errorcode\" : \"permission_denied\"")) {
            return false;
        }
        // 失败关键词检查
        return !(normalized.contains("失败")
                || normalized.contains("错误")
                || normalized.contains("异常")
                || normalized.contains("无权")
                || normalized.contains("permission_denied")
                || normalized.contains("forbidden")
                || normalized.contains("not found")
                || normalized.contains("illegalargumentexception")
                || normalized.contains("runtimeexception"));
    }

    /**
     * 从工具返回结果中提取错误信息。
     *
     * <p>优先级：
     * <ol>
     *   <li>如果结果是空的 → 返回 "Tool returned empty result"</li>
     *   <li>如果包含权限错误 → 尝试从 JSON 中提取 message 字段，否则返回默认的权限错误信息</li>
     *   <li>如果包含 JSON message 字段 → 返回该字段的值</li>
     *   <li>其他情况 → 返回 "Tool result contains failure markers"</li>
     * </ol>
     *
     * @param result 工具返回的结果文本
     * @return 提取出的错误信息
     */
    private String extractToolErrorMessage(String result) {
        if (result == null || result.isBlank()) {
            return "Tool returned empty result";
        }
        String normalized = result.toLowerCase();

        // 处理权限错误
        if (normalized.contains("\"errorcode\":\"permission_denied\"")
                || normalized.contains("\"errorcode\" : \"permission_denied\"")
                || normalized.contains("permission_denied")
                || normalized.contains("无权")) {
            String jsonMessage = extractJsonMessage(result);
            return jsonMessage != null ? jsonMessage : "当前角色无权调用该工具";
        }

        // 尝试从 JSON 中提取 message 字段
        String jsonMessage = extractJsonMessage(result);
        if (jsonMessage != null) {
            return jsonMessage;
        }

        return "Tool result contains failure markers";
    }

    /**
     * 从字符串中提取 JSON 格式的 message 字段值。
     *
     * <p>示例输入：{"code": 500, "message": "用户不存在"}
     * 输出："用户不存在"
     *
     * @param result 可能包含 JSON 格式的字符串
     * @return message 字段的值，如果未找到则返回 null
     */
    private String extractJsonMessage(String result) {
        Matcher matcher = JSON_MESSAGE_PATTERN.matcher(result);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replace("\\\"", "\"");
    }
}
