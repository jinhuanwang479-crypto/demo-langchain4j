package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpUserInsertParam {
    private Long id;
    private String username;
    private String loginName;
    private String password;
    private String position;
    private String department;
    private String email;
    private String phonenum;
    private Long tenantId;
}
