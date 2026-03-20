package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiRequestTrace {

    private Long id;
    private String requestId;
    private String memoryId;
    private Long tenantId;
    private String userId;
    private String question;
    private String response;
    private String status;
    private String errorMessage;
    private String modelName;
    private String finishReason;
    private Long latencyMs;
    private Long firstTokenLatencyMs;
    private Integer streamedChars;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer retrievedCount;
    private Double topRetrievalScore;
    private String retrievalRejectedReason;
    private Integer toolCallCount;
    private Integer evaluationScore;
    private String evaluationStatus;
    private String riskLevel;
    private String evaluationReasonsJson;
    private String retrievalSnapshotJson;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
