package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ErpPartnerInsertParam {
    private Long id;
    private String supplier;
    private String contacts;
    private String phoneNum;
    private String email;
    private String description;
    private String type;
    private Integer enabled;
    private BigDecimal advanceIn;
    private BigDecimal beginNeedGet;
    private BigDecimal beginNeedPay;
    private BigDecimal allNeedGet;
    private BigDecimal allNeedPay;
    private String fax;
    private String telephone;
    private String address;
    private String taxNum;
    private String bankName;
    private String accountNumber;
    private BigDecimal taxRate;
    private String sort;
    private Long creator;
    private Long tenantId;
}
