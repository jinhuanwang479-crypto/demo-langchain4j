package com.example.consultant.pojo;

import lombok.Data;

@Data
public class AiToolDefinition {
    private Long id;
    private String toolCode;
    private String toolName;
    private String beanName;
    private String methodName;
    private String functionIds;
    private Integer registeredInAi;
    private Integer enabled;
}
