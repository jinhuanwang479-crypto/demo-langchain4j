package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ErpUnit {
    private Long id;
    private String name;
    private String basicUnit;
    private String otherUnit;
    private String otherUnitTwo;
    private String otherUnitThree;
    private BigDecimal ratio;
    private BigDecimal ratioTwo;
    private BigDecimal ratioThree;
    private Integer enabled;
}
