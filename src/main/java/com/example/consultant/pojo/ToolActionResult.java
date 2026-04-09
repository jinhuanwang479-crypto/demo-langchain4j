package com.example.consultant.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ToolActionResult {
    private String action;
    private String message;
    private Long affectedId;
    private String businessNo;
    private Boolean success;
    private String errorCode;

    public ToolActionResult(String action, String message, Long affectedId, String businessNo) {
        this(action, message, affectedId, businessNo, true, null);
    }

    public ToolActionResult(String action, String message, Long affectedId, String businessNo,
                            Boolean success, String errorCode) {
        this.action = action;
        this.message = message;
        this.affectedId = affectedId;
        this.businessNo = businessNo;
        this.success = success;
        this.errorCode = errorCode;
    }

    public static ToolActionResult failure(String action, String message, String errorCode) {
        return new ToolActionResult(action, message, null, null, false, errorCode);
    }
}
