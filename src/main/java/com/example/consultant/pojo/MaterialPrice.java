package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialPrice {
    private Long id;
    private String barCode;
    private String commodityUnit;
    private String sku;
    private BigDecimal purchasePrice;
    private BigDecimal retailPrice;
    private BigDecimal salePrice;
    private BigDecimal lowPrice;
    private String defaultFlag;
}
