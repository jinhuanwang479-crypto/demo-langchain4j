package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpPerson {
    private Long id;
    private String type;
    private String name;
    private Integer enabled;
    private String sort;
}
