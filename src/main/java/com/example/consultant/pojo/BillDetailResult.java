package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BillDetailResult {
    private Long id;
    private String type;
    private String subType;
    private String number;
    private LocalDateTime operTime;
    private BigDecimal totalPrice;
    private BigDecimal changeAmount;
    private BigDecimal discountMoney;
    private BigDecimal otherMoney;
    private BigDecimal deposit;
    private String status;
    private String payType;
    private String remark;
    private String linkNumber;
    private Long partnerId;
    private String partnerName;
    private Long accountId;
    private String accountName;
    private List<BillItemResult> items;
}
