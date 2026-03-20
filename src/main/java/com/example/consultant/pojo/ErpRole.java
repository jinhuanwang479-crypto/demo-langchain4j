package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpRole {
    private Long id;
    private String name;
    private String type;
    private String priceLimit;
    private String description;
    private String sort;
}
