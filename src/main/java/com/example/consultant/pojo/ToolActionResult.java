package com.example.consultant.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolActionResult {
    private String action;
    private String message;
    private Long affectedId;
    private String businessNo;
}
