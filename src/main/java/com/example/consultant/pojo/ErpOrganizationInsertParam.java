package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpOrganizationInsertParam {
    private Long id;
    private String orgNo;
    private String orgAbr;
    private Long parentId;
    private String sort;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long tenantId;
}
