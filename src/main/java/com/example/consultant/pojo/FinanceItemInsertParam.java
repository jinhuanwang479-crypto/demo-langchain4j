package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceItemInsertParam {
    private Long id;
    private Long headerId;
    private Long accountId;
    private Long inOutItemId;
    private Long billId;
    private BigDecimal needDebt;
    private BigDecimal finishDebt;
    private BigDecimal eachAmount;
    private String remark;
    private Long tenantId;
}
