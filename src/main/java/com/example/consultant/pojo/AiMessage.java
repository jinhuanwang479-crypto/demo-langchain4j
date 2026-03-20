package com.example.consultant.pojo;

import lombok.Data;

import java.util.Date;

@Data
public class AiMessage {

    private Long id;

    private String memoryId;

    private String role;

    private String content;

    private Date createTime;
}
