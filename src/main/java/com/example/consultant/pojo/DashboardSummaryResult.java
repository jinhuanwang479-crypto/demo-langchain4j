package com.example.consultant.pojo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardSummaryResult {
    private String startDate;
    private String endDate;
    private DashboardBillSummary billSummary;
    private List<DashboardFinanceSummary> financeSummary;
}
