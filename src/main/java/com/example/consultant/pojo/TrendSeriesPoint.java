package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrendSeriesPoint {
    private String periodLabel;
    private BigDecimal amount;
    private Integer recordCount;
}
