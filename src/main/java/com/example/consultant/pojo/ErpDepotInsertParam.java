package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ErpDepotInsertParam {
    private Long id;
    private String name;
    private String address;
    private BigDecimal warehousing;
    private BigDecimal truckage;
    private Integer type;
    private String sort;
    private String remark;
    private Long principal;
    private Integer enabled;
    private Integer isDefault;
    private Long tenantId;
}
