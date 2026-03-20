package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialStatisticResult {
    private Long materialId;
    private String materialName;
    private BigDecimal totalNumber;
    private BigDecimal totalAmount;
}
