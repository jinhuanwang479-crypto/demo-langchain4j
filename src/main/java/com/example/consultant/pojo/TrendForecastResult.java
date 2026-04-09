package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class TrendForecastResult {
    private String metricType;
    private String granularity;
    private Integer historicalPeriods;
    private Integer forecastPeriods;
    private String method;
    private String confidenceLevel;
    private String warning;
    private Long resolvedMaterialId;
    private String resolvedMaterialName;
    private BigDecimal recentAverage;
    private BigDecimal trendSlope;
    private BigDecimal forecastAverage;
    private BigDecimal predictedChangeRate;
    private String trendDirection;
    private String volatilityLevel;
    private String businessSummary;
    private List<String> businessSuggestions = new ArrayList<>();
    private List<TrendForecastPoint> historyPoints = new ArrayList<>();
    private List<TrendForecastPoint> forecastPoints = new ArrayList<>();
}
