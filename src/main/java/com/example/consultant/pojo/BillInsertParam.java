package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillInsertParam {
    private Long id;
    private String type;
    private String subType;
    private String defaultNumber;
    private String number;
    private LocalDateTime createTime;
    private LocalDateTime operTime;
    private Long organId;
    private Long creator;
    private Long accountId;
    private BigDecimal changeAmount;
    private BigDecimal totalPrice;
    private String payType;
    private String remark;
    private BigDecimal discountMoney;
    private BigDecimal discountLastMoney;
    private BigDecimal otherMoney;
    private BigDecimal deposit;
    private String status;
    private String purchaseStatus;
    private String source;
    private Long tenantId;
}
