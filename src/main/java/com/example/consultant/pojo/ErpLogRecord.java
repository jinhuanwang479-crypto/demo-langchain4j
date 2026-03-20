package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpLogRecord {
    private Long id;
    private Long userId;
    private String operation;
    private String clientIp;
    private LocalDateTime createTime;
    private Integer status;
    private String content;
}
