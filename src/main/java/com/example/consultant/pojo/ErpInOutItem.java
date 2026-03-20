package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpInOutItem {
    private Long id;
    private String name;
    private String type;
    private String remark;
    private Integer enabled;
    private String sort;
}
