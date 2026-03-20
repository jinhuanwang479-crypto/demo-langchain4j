package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceItemRequest {
    private Long accountId;
    private Long inOutItemId;
    private Long billId;
    private BigDecimal needDebt;
    private BigDecimal finishDebt;
    private BigDecimal eachAmount;
    private BigDecimal amount;
    private String remark;
}
