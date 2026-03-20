package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialInfo {
    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String mfrs;
    private String model;
    private String standard;
    private String brand;
    private String mnemonic;
    private String color;
    private String unit;
    private String remark;
    private Long unitId;
    private Integer expiryNum;
    private BigDecimal weight;
    private Integer enabled;
    private String enableSerialNumber;
    private String enableBatchNumber;
    private String position;
    private String attribute;
    private String barCode;
    private BigDecimal purchasePrice;
    private BigDecimal retailPrice;
    private BigDecimal salePrice;
}
