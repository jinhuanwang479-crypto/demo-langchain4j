package com.example.consultant.pojo;

import lombok.Data;

import java.util.Set;

@Data
public class ErpUser {
    private Long id;
    private String username;
    private String loginName;
    private String phonenum;
    private String email;
    private String position;
    private String department;
    private Integer status;
    private String description;
    private String remark;
    private Long tenantId;
    private String roleIdsRaw;
    private Set<Long> roleIds;
    private Set<Long> toolIds;
}
