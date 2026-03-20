package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpOrganization {
    private Long id;
    private String orgNo;
    private String orgAbr;
    private Long parentId;
    private String sort;
    private String remark;
    private LocalDateTime createTime;
}
