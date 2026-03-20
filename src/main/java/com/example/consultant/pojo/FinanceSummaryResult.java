package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceSummaryResult {
    private Long id;
    private String type;
    private String billNo;
    private LocalDateTime billTime;
    private BigDecimal changeAmount;
    private BigDecimal discountMoney;
    private BigDecimal totalPrice;
    private String status;
    private String remark;
    private String partnerName;
    private String accountName;
}
