package com.example.consultant.pojo;

import lombok.Data;

@Data
public class MaterialCategory {
    private Long id;
    private String name;
    private Integer categoryLevel;
    private Long parentId;
    private String sort;
    private String serialNo;
    private String remark;
}
