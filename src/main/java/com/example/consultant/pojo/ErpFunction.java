package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpFunction {
    private Long id;
    private String number;
    private String name;
    private String parentNumber;
    private String url;
    private String component;
    private String type;
    private String pushBtn;
    private String icon;
}
