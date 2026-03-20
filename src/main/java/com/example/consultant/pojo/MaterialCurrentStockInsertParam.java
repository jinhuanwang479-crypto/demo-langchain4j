package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialCurrentStockInsertParam {
    private Long materialId;
    private Long depotId;
    private BigDecimal currentNumber;
    private BigDecimal currentUnitPrice;
    private Long tenantId;
}
