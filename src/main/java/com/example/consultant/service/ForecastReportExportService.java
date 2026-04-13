package com.example.consultant.service;

import com.example.consultant.pojo.ForecastReportRequest;
import com.example.consultant.pojo.TrendForecastPoint;
import com.example.consultant.pojo.TrendForecastResult;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.StringJoiner;

@Service
public class ForecastReportExportService {

    public byte[] exportReport(ForecastReportRequest request, TrendForecastResult result) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            HSSFCellStyle headerStyle = createHeaderStyle(workbook);
            HSSFCellStyle wrapStyle = createWrapStyle(workbook);

            writeOverviewSheet(workbook.createSheet("预测概览"), headerStyle, wrapStyle, request, result);
            writeDataSheet(workbook.createSheet("历史数据"), headerStyle, result.getHistoryPoints());
            writeDataSheet(workbook.createSheet("预测数据"), headerStyle, result.getForecastPoints());

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成预测 Excel 报表失败", ex);
        }
    }

    private void writeOverviewSheet(Sheet sheet, HSSFCellStyle headerStyle, HSSFCellStyle wrapStyle,
                                    ForecastReportRequest request, TrendForecastResult result) {
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 48 * 256);
        int rowIndex = 0;
        rowIndex = writeKeyValue(sheet, rowIndex, "报表标题", result.getReportTitle(), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "预测类型", resolveReportKindLabel(request), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "指标类型", resolveMetricLabel(request, result), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "时间粒度", resolveGranularityLabel(request.getGranularity()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "历史区间", safe(request.getStartDate()) + " ~ " + safe(request.getEndDate()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "预测期数", String.valueOf(result.getForecastPeriods()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "算法", safe(result.getMethod()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "置信度", resolveConfidenceLabel(result.getConfidenceLevel()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "趋势方向", resolveDirectionLabel(result.getTrendDirection()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "波动等级", resolveVolatilityLabel(result.getVolatilityLevel()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "近期均值", formatNumber(result.getRecentAverage()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "预测均值", formatNumber(result.getForecastAverage()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "变化率", formatPercent(result.getPredictedChangeRate()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "业务总结", safe(result.getBusinessSummary()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "经营建议", joinSuggestions(result.getBusinessSuggestions()), headerStyle, wrapStyle);
        rowIndex = writeKeyValue(sheet, rowIndex, "Warning", safe(result.getWarning()), headerStyle, wrapStyle);
        if (ForecastService.REPORT_KIND_MATERIAL.equals(request.getReportKind())) {
            rowIndex = writeKeyValue(sheet, rowIndex, "resolvedMaterialId", result.getResolvedMaterialId() == null ? "" : String.valueOf(result.getResolvedMaterialId()), headerStyle, wrapStyle);
            writeKeyValue(sheet, rowIndex, "resolvedMaterialName", safe(result.getResolvedMaterialName()), headerStyle, wrapStyle);
        }
    }

    private void writeDataSheet(Sheet sheet, HSSFCellStyle headerStyle, List<TrendForecastPoint> points) {
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        Row headerRow = sheet.createRow(0);
        Cell periodCell = headerRow.createCell(0);
        periodCell.setCellValue("periodLabel");
        periodCell.setCellStyle(headerStyle);
        Cell valueCell = headerRow.createCell(1);
        valueCell.setCellValue("value");
        valueCell.setCellStyle(headerStyle);

        if (points == null) {
            return;
        }
        int rowIndex = 1;
        for (TrendForecastPoint point : points) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(safe(point.getPeriodLabel()));
            if (point.getValue() != null) {
                row.createCell(1).setCellValue(point.getValue().doubleValue());
            } else {
                row.createCell(1).setCellValue("");
            }
        }
    }

    private int writeKeyValue(Sheet sheet, int rowIndex, String key, String value,
                              HSSFCellStyle headerStyle, HSSFCellStyle wrapStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell keyCell = row.createCell(0);
        keyCell.setCellValue(key);
        keyCell.setCellStyle(headerStyle);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(wrapStyle);
        return rowIndex + 1;
    }

    private HSSFCellStyle createHeaderStyle(HSSFWorkbook workbook) {
        HSSFFont font = workbook.createFont();
        font.setBold(true);
        HSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private HSSFCellStyle createWrapStyle(HSSFWorkbook workbook) {
        HSSFCellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    private String resolveReportKindLabel(ForecastReportRequest request) {
        return switch (request.getReportKind()) {
            case ForecastService.REPORT_KIND_BUSINESS -> "业务趋势预测";
            case ForecastService.REPORT_KIND_CASHFLOW -> "资金趋势预测";
            default -> "商品销量预测";
        };
    }

    private String resolveMetricLabel(ForecastReportRequest request, TrendForecastResult result) {
        if (ForecastService.REPORT_KIND_MATERIAL.equals(request.getReportKind())) {
            return StringUtils.hasText(result.getResolvedMaterialName()) ? result.getResolvedMaterialName() : safe(request.getMaterialKeyword());
        }
        return switch (safe(request.getMetricType())) {
            case "purchase" -> "采购";
            case "retail" -> "零售";
            case "receive" -> "收款";
            case "pay" -> "付款";
            case "income" -> "收入";
            case "expense" -> "支出";
            case "transfer" -> "转账";
            case "advance" -> "预付款";
            default -> "销售";
        };
    }

    private String resolveGranularityLabel(String granularity) {
        return switch (safe(granularity)) {
            case "week" -> "周";
            case "month" -> "月";
            default -> "日";
        };
    }

    private String resolveConfidenceLabel(String value) {
        return switch (safe(value)) {
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            default -> safe(value);
        };
    }

    private String resolveDirectionLabel(String value) {
        return switch (safe(value)) {
            case "UP" -> "上升";
            case "DOWN" -> "下降";
            case "FLAT" -> "平稳";
            default -> safe(value);
        };
    }

    private String resolveVolatilityLabel(String value) {
        return resolveConfidenceLabel(value);
    }

    private String formatNumber(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String formatPercent(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString() + "%";
    }

    private String joinSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (String suggestion : suggestions) {
            joiner.add(suggestion == null ? "" : suggestion);
        }
        return joiner.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
