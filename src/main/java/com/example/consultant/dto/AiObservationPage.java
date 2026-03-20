package com.example.consultant.dto;

import com.example.consultant.pojo.AiRequestTrace;
import lombok.Data;

import java.util.List;

@Data
public class AiObservationPage {

    private int page;
    private int size;
    private long total;
    private List<AiRequestTrace> items;
}
