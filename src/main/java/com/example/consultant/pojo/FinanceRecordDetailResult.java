package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FinanceRecordDetailResult {
    private Long id;
    private String type;
    private String billNo;
    private LocalDateTime billTime;
    private BigDecimal changeAmount;
    private BigDecimal discountMoney;
    private BigDecimal totalPrice;
    private String status;
    private String remark;
    private Long partnerId;
    private String partnerName;
    private Long accountId;
    private String accountName;
    private Long handsPersonId;
    private String handsPersonName;
    private List<FinanceItemResult> items;
}
