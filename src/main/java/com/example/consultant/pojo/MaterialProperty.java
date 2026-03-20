package com.example.consultant.pojo;

import lombok.Data;

@Data
public class MaterialProperty {
    private Long id;
    private String nativeName;
    private String anotherName;
    private Integer enabled;
    private String sort;
}
