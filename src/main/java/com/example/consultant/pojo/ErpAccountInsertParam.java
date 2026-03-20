package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ErpAccountInsertParam {
    private Long id;
    private String name;
    private String serialNo;
    private BigDecimal initialAmount;
    private BigDecimal currentAmount;
    private String remark;
    private Integer enabled;
    private String sort;
    private Integer isDefault;
    private Long tenantId;
}
