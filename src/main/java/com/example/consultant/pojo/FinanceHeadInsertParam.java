package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceHeadInsertParam {
    private Long id;
    private String type;
    private Long organId;
    private Long handsPersonId;
    private Long creator;
    private BigDecimal changeAmount;
    private BigDecimal discountMoney;
    private BigDecimal totalPrice;
    private Long accountId;
    private String billNo;
    private LocalDateTime billTime;
    private String remark;
    private String status;
    private String source;
    private Long tenantId;
}
