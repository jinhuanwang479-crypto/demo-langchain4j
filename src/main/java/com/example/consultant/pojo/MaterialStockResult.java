package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialStockResult {
    private Long materialId;
    private String materialName;
    private String model;
    private String standard;
    private String unit;
    private Long depotId;
    private String depotName;
    private BigDecimal currentNumber;
    private BigDecimal currentUnitPrice;
    private BigDecimal lowSafeStock;
    private BigDecimal highSafeStock;
}
