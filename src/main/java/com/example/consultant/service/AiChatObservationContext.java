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

public class AiChatObservationContext {

    private static final Logger log = LoggerFactory.getLogger(AiChatObservationContext.class);

    private final String requestId = UUID.randomUUID().toString().replace("-", "");
    private final String memoryId;
    private final Long tenantId;
    private final String userId;
    private final String question;
    private final LocalDateTime startedAt = LocalDateTime.now();
    private final Instant startedInstant = Instant.now();
    private final StringBuilder responseBuilder = new StringBuilder();
    private final List<Map<String, Object>> retrievalSnapshots = new ArrayList<>();
    private final List<AiToolTrace> toolTraces = new ArrayList<>();

    private LocalDateTime completedAt;
    private Instant firstTokenAt;
    private String status = "SUCCESS";
    private String errorMessage;
    private String modelName;
    private String finishReason;
    private Long latencyMs;
    private Long firstTokenLatencyMs;
    private int streamedChars;
    private int retrievedCount;
    private Double topRetrievalScore;
    private String retrievalRejectedReason;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    public AiChatObservationContext(String memoryId, Long tenantId, String userId, String question) {
        this.memoryId = memoryId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.question = question;
    }

    public synchronized void onPartialResponse(String partial) {
        if (partial == null) {
            return;
        }
        if (firstTokenAt == null) {
            firstTokenAt = Instant.now();
            firstTokenLatencyMs = Duration.between(startedInstant, firstTokenAt).toMillis();
        }
        responseBuilder.append(partial);
        streamedChars += partial.length();
    }

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
            if (score != null && (topRetrievalScore == null || score > topRetrievalScore)) {
                topRetrievalScore = score;
            }
        }
        if (!retrievalSnapshots.isEmpty()) {
            retrievalRejectedReason = null;
        }
    }

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
        if (!Boolean.TRUE.equals(trace.getSuccess())) {
            trace.setErrorMessage("Tool result contains failure markers");
        }
        toolTraces.add(trace);
    }

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
            if (responseBuilder.length() == 0 && response.aiMessage() != null) {
                responseBuilder.append(response.aiMessage().text());
                streamedChars = responseBuilder.length();
            }
        }
        trimResponse(properties);
    }

    public synchronized void onError(Throwable error, AiObservabilityProperties properties) {
        status = "ERROR";
        errorMessage = error == null ? "Unknown error" : abbreviate(error.getMessage(), 1000);
        completedAt = LocalDateTime.now();
        latencyMs = Duration.between(startedInstant, Instant.now()).toMillis();
        trimResponse(properties);
    }

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

    public synchronized List<Map<String, Object>> getRetrievalSnapshots() {
        return new ArrayList<>(retrievalSnapshots);
    }

    public synchronized List<AiToolTrace> getToolTraces() {
        return new ArrayList<>(toolTraces);
    }

    private void trimResponse(AiObservabilityProperties properties) {
        int maxLength = properties.getMaxResponsePreviewLength();
        if (maxLength > 0 && responseBuilder.length() > maxLength) {
            responseBuilder.setLength(maxLength);
        }
    }

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

    private String abbreviate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean toolExecutionLooksSuccessful(String result) {
        if (result == null || result.isBlank()) {
            return false;
        }
        String normalized = result.toLowerCase();
        return !(normalized.contains("失败")
                || normalized.contains("错误")
                || normalized.contains("异常")
                || normalized.contains("not found")
                || normalized.contains("illegalargumentexception")
                || normalized.contains("runtimeexception"));
    }
}
