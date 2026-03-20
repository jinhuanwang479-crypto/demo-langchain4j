package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceItemResult {
    private Long id;
    private Long accountId;
    private String accountName;
    private Long inOutItemId;
    private String inOutItemName;
    private Long billId;
    private BigDecimal needDebt;
    private BigDecimal finishDebt;
    private BigDecimal eachAmount;
    private String remark;
}
