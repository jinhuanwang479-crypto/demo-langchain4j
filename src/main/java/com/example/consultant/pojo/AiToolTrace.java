package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiToolTrace {

    private Long id;
    private String requestId;
    private Integer sequenceNo;
    private String toolName;
    private String argumentsJson;
    private String resultPreview;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}
