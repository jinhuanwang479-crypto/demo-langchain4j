package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardBillSummary {
    private BigDecimal purchaseAmount;
    private BigDecimal saleAmount;
    private BigDecimal retailAmount;
    private BigDecimal saleReturnAmount;
    private BigDecimal purchaseReturnAmount;
    private Long billCount;
}
