package com.example.consultant.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluationResult {

    private Integer overallScore;
    private String status;
    private String riskLevel;
    private List<String> reasonCodes = new ArrayList<>();
}
