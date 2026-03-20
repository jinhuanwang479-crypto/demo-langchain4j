package com.example.consultant.dto;

import lombok.Data;

@Data
public class AiObservationSummary {

    private long totalRequests;
    private double errorRate;
    private double averageLatencyMs;
    private long p95LatencyMs;
    private double averageScore;
    private long passCount;
    private long warnCount;
    private long failCount;
}
