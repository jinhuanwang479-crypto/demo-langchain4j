package com.example.consultant.tools;

import com.example.consultant.pojo.DashboardFinanceSummary;
import com.example.consultant.pojo.DashboardSummaryResult;
import com.example.consultant.pojo.MaterialStatisticResult;
import com.example.consultant.pojo.StockWarningResult;
import com.example.consultant.service.ReportService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportTool {

    private final ReportService reportService;

    public ReportTool(ReportService reportService) {
        this.reportService = reportService;
    }

    @Tool("查询经营汇总")
    public DashboardSummaryResult getDashboardSummary(@P("开始日期或时间，可选") String startDate,
                                                      @P("结束日期或时间，可选") String endDate) {
        return reportService.getDashboardSummary(startDate, endDate, null);
    }

    @Tool("查询销售统计")
    public List<MaterialStatisticResult> getSaleStatistics(@P("开始日期或时间，可选") String startDate,
                                                           @P("结束日期或时间，可选") String endDate,
                                                           @P("返回条数，可选") Integer limit) {
        return reportService.getSaleStatistics(startDate, endDate, null, limit);
    }

    @Tool("查询采购统计")
    public List<MaterialStatisticResult> getPurchaseStatistics(@P("开始日期或时间，可选") String startDate,
                                                               @P("结束日期或时间，可选") String endDate,
                                                               @P("返回条数，可选") Integer limit) {
        return reportService.getPurchaseStatistics(startDate, endDate, null, limit);
    }

    @Tool("查询资金统计")
    public List<DashboardFinanceSummary> getAccountStatistics(@P("开始日期或时间，可选") String startDate,
                                                              @P("结束日期或时间，可选") String endDate) {
        return reportService.getAccountStatistics(startDate, endDate, null);
    }

    @Tool("查询库存预警")
    public List<StockWarningResult> getStockWarning(@P("返回条数，可选") Integer limit) {
        return reportService.getStockWarning(null, limit);
    }
}
