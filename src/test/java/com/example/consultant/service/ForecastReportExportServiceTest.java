package com.example.consultant.service;

import com.example.consultant.pojo.ForecastReportRequest;
import com.example.consultant.pojo.TrendForecastPoint;
import com.example.consultant.pojo.TrendForecastResult;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastReportExportServiceTest {

    private final ForecastReportExportService exportService = new ForecastReportExportService();

    @Test
    void shouldCreateWorkbookWithOverviewHistoryAndForecastSheets() throws Exception {
        ForecastReportRequest request = new ForecastReportRequest();
        request.setReportKind(ForecastService.REPORT_KIND_MATERIAL);
        request.setGranularity("week");
        request.setPeriods(4);
        request.setStartDate("2026-01-01");
        request.setEndDate("2026-03-31");
        request.setMaterialKeyword("智能办公本Pro");

        TrendForecastResult result = new TrendForecastResult();
        result.setReportTitle("商品销量预测报告（智能办公本Pro）");
        result.setMetricType("material_demand");
        result.setForecastPeriods(4);
        result.setMethod("LinearTrend+MovingAverage");
        result.setConfidenceLevel("MEDIUM");
        result.setTrendDirection("UP");
        result.setVolatilityLevel("LOW");
        result.setRecentAverage(new BigDecimal("128.50"));
        result.setForecastAverage(new BigDecimal("146.00"));
        result.setPredictedChangeRate(new BigDecimal("13.62"));
        result.setBusinessSummary("未来 4 周销量预计上升。");
        result.setBusinessSuggestions(List.of("建议提前补货", "结合促销活动复盘需求"));
        result.setResolvedMaterialId(1001L);
        result.setResolvedMaterialName("智能办公本Pro");
        result.setHistoryPoints(List.of(point("2026-03-03", "126.00")));
        result.setForecastPoints(List.of(point("2026-04-07", "150.00")));

        byte[] bytes = exportService.exportReport(request, result);

        assertThat(bytes).isNotEmpty();
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("预测概览");
            assertThat(workbook.getSheetName(1)).isEqualTo("历史数据");
            assertThat(workbook.getSheetName(2)).isEqualTo("预测数据");

            assertThat(workbook.getSheet("预测概览").getRow(0).getCell(0).getStringCellValue()).isEqualTo("报表标题");
            assertThat(workbook.getSheet("预测概览").getRow(0).getCell(1).getStringCellValue()).contains("智能办公本Pro");
            assertThat(workbook.getSheet("预测概览").getRow(15).getCell(0).getStringCellValue()).isEqualTo("Warning");
            assertThat(workbook.getSheet("预测概览").getRow(16).getCell(0).getStringCellValue()).isEqualTo("resolvedMaterialId");

            assertThat(workbook.getSheet("历史数据").getRow(0).getCell(0).getStringCellValue()).isEqualTo("periodLabel");
            assertThat(workbook.getSheet("历史数据").getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-03-03");
            assertThat(workbook.getSheet("预测数据").getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-04-07");
        }
    }

    private TrendForecastPoint point(String label, String value) {
        TrendForecastPoint point = new TrendForecastPoint();
        point.setPeriodLabel(label);
        point.setValue(new BigDecimal(value));
        return point;
    }
}
