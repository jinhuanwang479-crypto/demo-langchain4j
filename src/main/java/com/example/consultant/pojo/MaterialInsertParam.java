package com.example.consultant.pojo;

import lombok.Data;

@Data
public class MaterialInsertParam {
    private Long id;
    private Long categoryId;
    private String name;
    private String model;
    private String standard;
    private String brand;
    private String mnemonic;
    private String unit;
    private String remark;
    private Long unitId;
    private Integer enabled;
    private Long tenantId;
}
