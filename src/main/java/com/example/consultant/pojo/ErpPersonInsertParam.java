package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpPersonInsertParam {
    private Long id;
    private String type;
    private String name;
    private Integer enabled;
    private String sort;
    private Long tenantId;
}
