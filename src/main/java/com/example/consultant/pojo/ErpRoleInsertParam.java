package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpRoleInsertParam {
    private Long id;
    private String name;
    private String type;
    private String priceLimit;
    private String value;
    private String description;
    private Integer enabled;
    private String sort;
    private Long tenantId;
}
