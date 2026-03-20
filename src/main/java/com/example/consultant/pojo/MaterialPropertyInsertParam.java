package com.example.consultant.pojo;

import lombok.Data;

@Data
public class MaterialPropertyInsertParam {
    private Long id;
    private String nativeName;
    private Integer enabled;
    private String sort;
    private String anotherName;
    private Long tenantId;
}
