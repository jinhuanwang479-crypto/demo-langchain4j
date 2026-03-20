package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ErpPartner {
    private Long id;
    private String supplier;
    private String contacts;
    private String phoneNum;
    private String telephone;
    private String email;
    private String type;
    private String address;
    private BigDecimal advanceIn;
    private BigDecimal beginNeedGet;
    private BigDecimal beginNeedPay;
    private BigDecimal allNeedGet;
    private BigDecimal allNeedPay;
    private BigDecimal taxRate;
    private Integer enabled;
}
