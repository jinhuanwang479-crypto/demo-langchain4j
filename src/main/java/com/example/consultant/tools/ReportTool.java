package com.example.consultant.tools;

import com.example.consultant.pojo.DashboardFinanceSummary;
import com.example.consultant.pojo.DashboardSummaryResult;
import com.example.consultant.pojo.MaterialStatisticResult;
import com.example.consultant.pojo.StockWarningResult;
import com.example.consultant.pojo.TrendForecastResult;
import com.example.consultant.service.ForecastService;
import com.example.consultant.service.ReportService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportTool {

    private final ReportService reportService;
    private final ForecastService forecastService;

    public ReportTool(ReportService reportService, ForecastService forecastService) {
        this.reportService = reportService;
        this.forecastService = forecastService;
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

    @Tool("预测业务趋势")
    public TrendForecastResult forecastBusinessTrend(@P("预测类型，可选：sale、purchase、retail") String metricType,
                                                     @P("时间粒度，可选：day、week、month") String granularity,
                                                     @P("预测未来多少期，可选") Integer periods,
                                                     @P("历史开始日期或时间，可选") String startDate,
                                                     @P("历史结束日期或时间，可选") String endDate) {
        return forecastService.forecastBusinessTrend(metricType, granularity, periods, startDate, endDate, null);
    }

    @Tool("预测资金趋势")
    public TrendForecastResult forecastCashflowTrend(@P("预测类型，可选：receive、pay、income、expense、transfer、advance") String metricType,
                                                     @P("时间粒度，可选：day、week、month") String granularity,
                                                     @P("预测未来多少期，可选") Integer periods,
                                                     @P("历史开始日期或时间，可选") String startDate,
                                                     @P("历史结束日期或时间，可选") String endDate) {
        return forecastService.forecastCashflowTrend(metricType, granularity, periods, startDate, endDate, null);
    }

    @Tool("预测商品销量趋势")
    public TrendForecastResult forecastMaterialDemand(@P("商品名称、关键字或物料标识") String materialKeyword,
                                                      @P("时间粒度，可选：day、week、month") String granularity,
                                                      @P("预测未来多少期，可选") Integer periods,
                                                      @P("历史开始日期或时间，可选") String startDate,
                                                      @P("历史结束日期或时间，可选") String endDate) {
        return forecastService.forecastMaterialDemand(materialKeyword, granularity, periods, startDate, endDate, null);
    }
}
