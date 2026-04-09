package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrendForecastPoint {
    private String periodLabel;
    private BigDecimal value;
}
