package com.example.consultant.pojo;

import lombok.Data;

import java.util.Date;

@Data
public class AiConversation {

    private Long id;

    private String memoryId;

    private String userId;

    private String title;

    private Integer messageCount = 0;

    private Date createTime;

    private Date updateTime;
}
