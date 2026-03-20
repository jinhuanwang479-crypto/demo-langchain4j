package com.example.consultant.pojo;

import lombok.Data;

@Data
public class MaterialAttributeInsertParam {
    private Long id;
    private String attributeName;
    private String attributeValue;
    private Long tenantId;
}
