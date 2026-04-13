package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ForecastReportRequest {
    private String reportKind;
    private String metricType;
    private String granularity;
    private Integer periods;
    private String startDate;
    private String endDate;
    private String materialKeyword;
}
