package com.example.consultant.dto;

import com.example.consultant.pojo.AiRequestTrace;
import com.example.consultant.pojo.AiToolTrace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiObservationDetail {

    private AiRequestTrace requestTrace;
    private List<AiToolTrace> toolTraces;
}
