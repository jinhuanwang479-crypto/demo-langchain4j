package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MaterialPriceInsertParam {
    private Long id;
    private Long materialId;
    private String barCode;
    private String commodityUnit;
    private BigDecimal purchasePrice;
    private BigDecimal retailPrice;
    private BigDecimal salePrice;
    private BigDecimal lowPrice;
    private LocalDateTime createTime;
    private Long tenantId;
}
