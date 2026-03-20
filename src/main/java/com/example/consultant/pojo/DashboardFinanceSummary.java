package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardFinanceSummary {
    private String type;
    private BigDecimal totalAmount;
    private Long recordCount;
}
