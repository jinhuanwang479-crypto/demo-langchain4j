package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialPriceUpdateParam {
    private Long id;
    private String barCode;
    private String commodityUnit;
    private BigDecimal purchasePrice;
    private BigDecimal retailPrice;
    private BigDecimal salePrice;
    private BigDecimal lowPrice;
    private Long updateTime;
    private Long tenantId;
}
