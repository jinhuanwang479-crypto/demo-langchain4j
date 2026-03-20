package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillSummaryResult {
    private Long id;
    private String type;
    private String subType;
    private String number;
    private LocalDateTime operTime;
    private BigDecimal totalPrice;
    private BigDecimal changeAmount;
    private String status;
    private String payType;
    private String remark;
    private String partnerName;
    private String accountName;
}
