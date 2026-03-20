package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockWarningResult {
    private Long materialId;
    private String materialName;
    private Long depotId;
    private String depotName;
    private BigDecimal currentNumber;
    private BigDecimal lowSafeStock;
    private BigDecimal highSafeStock;
}
