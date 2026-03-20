package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialCategoryInsertParam {
    private Long id;
    private String name;
    private Integer categoryLevel;
    private Long parentId;
    private String sort;
    private String serialNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long tenantId;
}
