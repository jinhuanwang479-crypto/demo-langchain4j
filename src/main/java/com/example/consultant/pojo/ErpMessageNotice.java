package com.example.consultant.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpMessageNotice {
    private Long id;
    private String msgTitle;
    private String msgContent;
    private LocalDateTime createTime;
    private String type;
    private Long userId;
    private String status;
    private Long tenantId;
}
