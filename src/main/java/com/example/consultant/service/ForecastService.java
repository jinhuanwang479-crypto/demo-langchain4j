package com.example.consultant.service;

import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.mapper.MaterialMapper;
import com.example.consultant.mapper.ReportMapper;
import com.example.consultant.pojo.ForecastReportRequest;
import com.example.consultant.pojo.MaterialInfo;
import com.example.consultant.pojo.TrendForecastPoint;
import com.example.consultant.pojo.TrendForecastResult;
import com.example.consultant.pojo.TrendSeriesPoint;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ForecastService {

    public static final String REPORT_KIND_BUSINESS = "business";
    public static final String REPORT_KIND_CASHFLOW = "cashflow";
    public static final String REPORT_KIND_MATERIAL = "material";

    private static final Long DEFAULT_TENANT_ID = 160L;
    private static final String REPORT_DOWNLOAD_PATH = "/new-backend-ai/forecast/report/download";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReportMapper reportMapper;
    private final MaterialMapper materialMapper;

    public ForecastService(ReportMapper reportMapper, MaterialMapper materialMapper) {
        this.reportMapper = reportMapper;
        this.materialMapper = materialMapper;
    }

    public TrendForecastResult forecastBusinessTrend(String metricType, String granularity, Integer periods,
                                                     String startDate, String endDate, Long tenantId) {
        return forecast(normalizeBusinessReportRequest(metricType, granularity, periods, startDate, endDate), tenantId);
    }

    public TrendForecastResult forecastCashflowTrend(String metricType, String granularity, Integer periods,
                                                     String startDate, String endDate, Long tenantId) {
        return forecast(normalizeCashflowReportRequest(metricType, granularity, periods, startDate, endDate), tenantId);
    }

    public TrendForecastResult forecastMaterialDemand(String materialKeyword, String granularity, Integer periods,
                                                      String startDate, String endDate, Long tenantId) {
        return forecast(normalizeMaterialReportRequest(materialKeyword, granularity, periods, startDate, endDate), tenantId);
    }

    public ForecastReportRequest normalizeDownloadRequest(String reportKind, String metricType, String granularity,
                                                          Integer periods, String startDate, String endDate,
                                                          String materialKeyword) {
        String resolvedReportKind = normalizeReportKind(reportKind);
        return switch (resolvedReportKind) {
            case REPORT_KIND_BUSINESS -> {
                if (!StringUtils.hasText(metricType)) {
                    throw new IllegalArgumentException("business 预测下载必须提供 metricType");
                }
                yield normalizeBusinessReportRequest(metricType, granularity, periods, startDate, endDate);
            }
            case REPORT_KIND_CASHFLOW -> {
                if (!StringUtils.hasText(metricType)) {
                    throw new IllegalArgumentException("cashflow 预测下载必须提供 metricType");
                }
                yield normalizeCashflowReportRequest(metricType, granularity, periods, startDate, endDate);
            }
            default -> {
                if (!StringUtils.hasText(materialKeyword)) {
                    throw new IllegalArgumentException("material 预测下载必须提供 materialKeyword");
                }
                yield normalizeMaterialReportRequest(materialKeyword, granularity, periods, startDate, endDate);
            }
        };
    }

    public TrendForecastResult forecast(ForecastReportRequest request, Long tenantId) {
        ForecastReportRequest normalizedRequest = normalizeRequest(request);
        TrendForecastResult result = switch (normalizedRequest.getReportKind()) {
            case REPORT_KIND_BUSINESS -> runBusinessForecast(normalizedRequest, tenantId);
            case REPORT_KIND_CASHFLOW -> runCashflowForecast(normalizedRequest, tenantId);
            default -> runMaterialForecast(normalizedRequest, tenantId);
        };
        attachReportMetadata(normalizedRequest, result);
        return result;
    }

    public ForecastReportRequest normalizeBusinessReportRequest(String metricType, String granularity, Integer periods,
                                                                String startDate, String endDate) {
        ForecastReportRequest request = baseRequest(REPORT_KIND_BUSINESS, granularity, periods, startDate, endDate);
        request.setMetricType(normalizeBusinessMetricType(metricType));
        return request;
    }

    public ForecastReportRequest normalizeCashflowReportRequest(String metricType, String granularity, Integer periods,
                                                                String startDate, String endDate) {
        ForecastReportRequest request = baseRequest(REPORT_KIND_CASHFLOW, granularity, periods, startDate, endDate);
        request.setMetricType(normalizeFinanceMetricType(metricType));
        return request;
    }

    public ForecastReportRequest normalizeMaterialReportRequest(String materialKeyword, String granularity, Integer periods,
                                                                String startDate, String endDate) {
        ForecastReportRequest request = baseRequest(REPORT_KIND_MATERIAL, granularity, periods, startDate, endDate);
        request.setMaterialKeyword(normalized(materialKeyword));
        return request;
    }

    private ForecastReportRequest normalizeRequest(ForecastReportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("预测请求不能为空");
        }
        return switch (normalizeReportKind(request.getReportKind())) {
            case REPORT_KIND_BUSINESS -> normalizeBusinessReportRequest(
                    request.getMetricType(), request.getGranularity(), request.getPeriods(), request.getStartDate(), request.getEndDate());
            case REPORT_KIND_CASHFLOW -> normalizeCashflowReportRequest(
                    request.getMetricType(), request.getGranularity(), request.getPeriods(), request.getStartDate(), request.getEndDate());
            default -> normalizeMaterialReportRequest(
                    request.getMaterialKeyword(), request.getGranularity(), request.getPeriods(), request.getStartDate(), request.getEndDate());
        };
    }

    private ForecastReportRequest baseRequest(String reportKind, String granularity, Integer periods,
                                              String startDate, String endDate) {
        String resolvedGranularity = normalizeGranularity(granularity);
        int forecastPeriods = normalizeForecastPeriods(periods, resolvedGranularity);
        LocalDate resolvedStartDate = resolveStartDate(startDate, resolvedGranularity);
        LocalDate resolvedEndDate = resolveEndDate(endDate, resolvedGranularity);

        ForecastReportRequest request = new ForecastReportRequest();
        request.setReportKind(reportKind);
        request.setGranularity(resolvedGranularity);
        request.setPeriods(forecastPeriods);
        request.setStartDate(resolvedStartDate.format(DAY_FORMATTER));
        request.setEndDate(resolvedEndDate.format(DAY_FORMATTER));
        return request;
    }

    private TrendForecastResult runBusinessForecast(ForecastReportRequest request, Long tenantId) {
        LocalDate start = parseDate(request.getStartDate());
        LocalDate end = parseDate(request.getEndDate());
        List<String> subTypes = switch (request.getMetricType()) {
            case "purchase" -> List.of("采购");
            case "retail" -> List.of("零售");
            default -> List.of("销售", "零售");
        };
        List<TrendSeriesPoint> rawSeries = switch (request.getGranularity()) {
            case "week" -> reportMapper.getBusinessTrendSeriesByWeek(subTypes, atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
            case "month" -> reportMapper.getBusinessTrendSeriesByMonth(subTypes, atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
            default -> reportMapper.getBusinessTrendSeriesByDay(subTypes, atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
        };
        if ("purchase".equals(request.getMetricType())) {
            rawSeries = normalizeSeriesToPositive(rawSeries);
        }
        TrendForecastResult result = buildForecastResult(rawSeries, request.getMetricType(), request.getGranularity(),
                request.getPeriods(), start, end);
        result.setMethod("LinearTrend+MovingAverage");
        return result;
    }

    private TrendForecastResult runCashflowForecast(ForecastReportRequest request, Long tenantId) {
        LocalDate start = parseDate(request.getStartDate());
        LocalDate end = parseDate(request.getEndDate());
        List<String> types = switch (request.getMetricType()) {
            case "pay" -> List.of("付款");
            case "income" -> List.of("收入");
            case "expense" -> List.of("支出");
            case "transfer" -> List.of("转账");
            case "advance" -> List.of("收预付款");
            default -> List.of("收款");
        };
        List<TrendSeriesPoint> rawSeries = switch (request.getGranularity()) {
            case "week" -> reportMapper.getFinanceTrendSeriesByWeek(types, atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
            case "month" -> reportMapper.getFinanceTrendSeriesByMonth(types, atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
            default -> reportMapper.getFinanceTrendSeriesByDay(types, atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
        };
        TrendForecastResult result = buildForecastResult(rawSeries, request.getMetricType(), request.getGranularity(),
                request.getPeriods(), start, end);
        result.setMethod("LinearTrend+MovingAverage");
        return result;
    }

    private TrendForecastResult runMaterialForecast(ForecastReportRequest request, Long tenantId) {
        LocalDate start = parseDate(request.getStartDate());
        LocalDate end = parseDate(request.getEndDate());
        TrendForecastResult result = new TrendForecastResult();
        result.setMetricType("material_demand");
        result.setGranularity(request.getGranularity());
        result.setForecastPeriods(request.getPeriods());
        result.setMethod("LinearTrend+MovingAverage");
        if (!StringUtils.hasText(request.getMaterialKeyword())) {
            result.setWarning("未提供商品关键词，无法进行销量预测");
            result.setConfidenceLevel("LOW");
            return result;
        }
        List<MaterialInfo> materials = materialMapper.searchMaterials(request.getMaterialKeyword(), null, tenantId(tenantId), 1);
        if (materials == null || materials.isEmpty()) {
            result.setWarning("未找到匹配商品，下载报告时将保留空数据说明");
            result.setConfidenceLevel("LOW");
            return result;
        }
        MaterialInfo material = materials.get(0);
        List<TrendSeriesPoint> rawSeries = switch (request.getGranularity()) {
            case "week" -> reportMapper.getMaterialDemandSeriesByWeek(material.getId(), atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
            case "month" -> reportMapper.getMaterialDemandSeriesByMonth(material.getId(), atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
            default -> reportMapper.getMaterialDemandSeriesByDay(material.getId(), atStartOfDay(start), atEndOfDay(end), tenantId(tenantId));
        };
        result = buildForecastResult(rawSeries, "material_demand", request.getGranularity(), request.getPeriods(), start, end);
        result.setMethod("LinearTrend+MovingAverage");
        result.setResolvedMaterialId(material.getId());
        result.setResolvedMaterialName(material.getName());
        return result;
    }

    private TrendForecastResult buildForecastResult(List<TrendSeriesPoint> rawSeries, String metricType, String granularity,
                                                    int forecastPeriods, LocalDate start, LocalDate end) {
        TrendForecastResult result = new TrendForecastResult();
        result.setMetricType(metricType);
        result.setGranularity(granularity);
        result.setForecastPeriods(forecastPeriods);
        List<TrendForecastPoint> historyPoints = fillMissingSeries(rawSeries, granularity, start, end);
        result.setHistoryPoints(historyPoints);
        result.setHistoricalPeriods(historyPoints.size());
        if (historyPoints.isEmpty()) {
            result.setWarning("当前历史数据为空，无法进行趋势预测");
            result.setConfidenceLevel("LOW");
            applyBusinessInterpretation(result);
            return result;
        }
        BigDecimal recentAverage = average(historyPoints);
        result.setRecentAverage(recentAverage);
        if (historyPoints.size() < 7) {
            result.setWarning("历史数据点少于 7 个，当前结果仅供参考");
            result.setConfidenceLevel("LOW");
            result.setTrendSlope(BigDecimal.ZERO);
            applyBusinessInterpretation(result);
            return result;
        }
        double slope = linearRegressionSlope(historyPoints);
        result.setTrendSlope(scale(BigDecimal.valueOf(slope)));
        result.setConfidenceLevel(resolveConfidence(historyPoints.size()));
        result.setForecastPoints(forecast(historyPoints, granularity, forecastPeriods, recentAverage, slope));
        applyBusinessInterpretation(result);
        return result;
    }

    private List<TrendForecastPoint> fillMissingSeries(List<TrendSeriesPoint> rawSeries, String granularity, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> valueMap = new HashMap<>();
        if (rawSeries != null) {
            for (TrendSeriesPoint point : rawSeries) {
                valueMap.put(point.getPeriodLabel(), point.getAmount() == null ? BigDecimal.ZERO : point.getAmount());
            }
        }
        List<TrendForecastPoint> points = new ArrayList<>();
        switch (granularity) {
            case "week" -> {
                LocalDate cursor = alignWeek(start);
                LocalDate alignedEnd = alignWeek(end);
                while (!cursor.isAfter(alignedEnd)) {
                    String label = formatWeek(cursor);
                    points.add(point(label, valueMap.getOrDefault(label, BigDecimal.ZERO)));
                    cursor = cursor.plusWeeks(1);
                }
            }
            case "month" -> {
                YearMonth cursor = YearMonth.from(start);
                YearMonth alignedEnd = YearMonth.from(end);
                while (!cursor.isAfter(alignedEnd)) {
                    String label = cursor.format(MONTH_FORMATTER);
                    points.add(point(label, valueMap.getOrDefault(label, BigDecimal.ZERO)));
                    cursor = cursor.plusMonths(1);
                }
            }
            default -> {
                LocalDate cursor = start;
                while (!cursor.isAfter(end)) {
                    String label = cursor.format(DAY_FORMATTER);
                    points.add(point(label, valueMap.getOrDefault(label, BigDecimal.ZERO)));
                    cursor = cursor.plusDays(1);
                }
            }
        }
        return points;
    }

    private List<TrendForecastPoint> forecast(List<TrendForecastPoint> historyPoints, String granularity, int periods,
                                              BigDecimal recentAverage, double slope) {
        List<TrendForecastPoint> forecastPoints = new ArrayList<>();
        BigDecimal lastValue = historyPoints.get(historyPoints.size() - 1).getValue();
        String lastLabel = historyPoints.get(historyPoints.size() - 1).getPeriodLabel();
        for (int i = 1; i <= periods; i++) {
            BigDecimal trendValue = scale(lastValue.add(BigDecimal.valueOf(slope * i)));
            BigDecimal predicted = scale(trendValue.multiply(BigDecimal.valueOf(0.6))
                    .add(recentAverage.multiply(BigDecimal.valueOf(0.4))));
            if (predicted.signum() < 0) {
                predicted = BigDecimal.ZERO;
            }
            forecastPoints.add(point(nextLabel(lastLabel, granularity, i), predicted));
        }
        return forecastPoints;
    }

    private double linearRegressionSlope(List<TrendForecastPoint> points) {
        int n = points.size();
        if (n < 2) {
            return 0D;
        }
        double sumX = 0D;
        double sumY = 0D;
        double sumXY = 0D;
        double sumXX = 0D;
        for (int i = 0; i < n; i++) {
            double x = i + 1D;
            double y = points.get(i).getValue() == null ? 0D : points.get(i).getValue().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-9) {
            return 0D;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }

    private BigDecimal average(List<TrendForecastPoint> points) {
        if (points == null || points.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int window = Math.min(3, points.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = points.size() - window; i < points.size(); i++) {
            sum = sum.add(points.get(i).getValue() == null ? BigDecimal.ZERO : points.get(i).getValue());
        }
        return scale(sum.divide(BigDecimal.valueOf(window), 4, RoundingMode.HALF_UP));
    }

    private String resolveConfidence(int historySize) {
        if (historySize >= 24) {
            return "HIGH";
        }
        if (historySize >= 12) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String nextLabel(String lastLabel, String granularity, int offset) {
        return switch (granularity) {
            case "week" -> formatWeek(LocalDate.parse(lastLabel, DAY_FORMATTER).plusWeeks(offset));
            case "month" -> YearMonth.parse(lastLabel, MONTH_FORMATTER).plusMonths(offset).format(MONTH_FORMATTER);
            default -> LocalDate.parse(lastLabel, DAY_FORMATTER).plusDays(offset).format(DAY_FORMATTER);
        };
    }

    private TrendForecastPoint point(String label, BigDecimal value) {
        TrendForecastPoint point = new TrendForecastPoint();
        point.setPeriodLabel(label);
        point.setValue(scale(value == null ? BigDecimal.ZERO : value));
        return point;
    }

    private List<TrendSeriesPoint> normalizeSeriesToPositive(List<TrendSeriesPoint> rawSeries) {
        if (rawSeries == null || rawSeries.isEmpty()) {
            return rawSeries;
        }
        List<TrendSeriesPoint> normalizedSeries = new ArrayList<>(rawSeries.size());
        for (TrendSeriesPoint rawPoint : rawSeries) {
            TrendSeriesPoint normalizedPoint = new TrendSeriesPoint();
            normalizedPoint.setPeriodLabel(rawPoint.getPeriodLabel());
            normalizedPoint.setRecordCount(rawPoint.getRecordCount());
            normalizedPoint.setAmount(rawPoint.getAmount() == null ? BigDecimal.ZERO : rawPoint.getAmount().abs());
            normalizedSeries.add(normalizedPoint);
        }
        return normalizedSeries;
    }

    private void applyBusinessInterpretation(TrendForecastResult result) {
        BigDecimal recentAverage = result.getRecentAverage() == null ? BigDecimal.ZERO : result.getRecentAverage();
        BigDecimal forecastAverage = average(result.getForecastPoints());
        result.setForecastAverage(forecastAverage);
        if (recentAverage.signum() == 0) {
            result.setPredictedChangeRate(forecastAverage.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100));
        } else {
            result.setPredictedChangeRate(scale(
                    forecastAverage.subtract(recentAverage)
                            .divide(recentAverage, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
            ));
        }
        result.setTrendDirection(resolveDirection(result.getPredictedChangeRate()));
        result.setVolatilityLevel(resolveVolatility(result.getHistoryPoints()));
        result.setBusinessSummary(buildBusinessSummary(result));
        result.setBusinessSuggestions(buildBusinessSuggestions(result));
    }

    private String resolveDirection(BigDecimal changeRate) {
        if (changeRate == null) {
            return "UNKNOWN";
        }
        if (changeRate.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "UP";
        }
        if (changeRate.compareTo(BigDecimal.valueOf(-5)) <= 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    private String resolveVolatility(List<TrendForecastPoint> historyPoints) {
        if (historyPoints == null || historyPoints.size() < 2) {
            return "LOW";
        }
        BigDecimal avg = average(historyPoints);
        if (avg.signum() == 0) {
            return "LOW";
        }
        BigDecimal diffSum = BigDecimal.ZERO;
        for (int i = 1; i < historyPoints.size(); i++) {
            BigDecimal current = historyPoints.get(i).getValue() == null ? BigDecimal.ZERO : historyPoints.get(i).getValue();
            BigDecimal previous = historyPoints.get(i - 1).getValue() == null ? BigDecimal.ZERO : historyPoints.get(i - 1).getValue();
            diffSum = diffSum.add(current.subtract(previous).abs());
        }
        BigDecimal avgDiff = diffSum.divide(BigDecimal.valueOf(historyPoints.size() - 1L), 4, RoundingMode.HALF_UP);
        BigDecimal ratio = avgDiff.divide(avg, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        if (ratio.compareTo(BigDecimal.valueOf(30)) >= 0) {
            return "HIGH";
        }
        if (ratio.compareTo(BigDecimal.valueOf(15)) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildBusinessSummary(TrendForecastResult result) {
        if (StringUtils.hasText(result.getWarning())) {
            return result.getWarning();
        }
        String targetName = resolveTargetName(result);
        String direction = switch (result.getTrendDirection()) {
            case "UP" -> "预计上升";
            case "DOWN" -> "预计下降";
            case "FLAT" -> "预计保持平稳";
            default -> "趋势暂不明确";
        };
        String changeRate = result.getPredictedChangeRate() == null
                ? "0%"
                : result.getPredictedChangeRate().stripTrailingZeros().toPlainString() + "%";
        return targetName + "在未来 " + result.getForecastPeriods() + " 个" + granularityLabel(result.getGranularity())
                + direction + "，预测均值约为 " + safeAmount(result.getForecastAverage())
                + "，相对近期均值变化约 " + changeRate + "。";
    }

    private List<String> buildBusinessSuggestions(TrendForecastResult result) {
        List<String> suggestions = new ArrayList<>();
        if (StringUtils.hasText(result.getWarning())) {
            suggestions.add("建议先补充更多连续历史数据后再做趋势判断。");
            return suggestions;
        }
        String metricType = result.getMetricType();
        String direction = result.getTrendDirection();
        String volatility = result.getVolatilityLevel();
        if ("material_demand".equals(metricType)) {
            if ("UP".equals(direction)) {
                suggestions.add("建议提前准备补货或锁定采购计划，避免短期缺货。");
            } else if ("DOWN".equals(direction)) {
                suggestions.add("建议控制补货节奏，避免库存积压。");
            } else {
                suggestions.add("建议维持当前补货节奏，并持续观察销量变化。");
            }
        } else if ("purchase".equals(metricType)) {
            if ("UP".equals(direction)) {
                suggestions.add("建议提前安排供应商备货和采购审批窗口。");
            } else if ("DOWN".equals(direction)) {
                suggestions.add("建议压缩采购节奏，优先消化现有库存。");
            } else {
                suggestions.add("建议按当前采购计划平稳执行。");
            }
        } else if ("pay".equals(metricType) || "expense".equals(metricType)) {
            if ("UP".equals(direction)) {
                suggestions.add("建议提前准备资金预算，避免付款高峰带来现金流压力。");
            } else if ("DOWN".equals(direction)) {
                suggestions.add("建议复核近期支出结构，确认成本下降是否可持续。");
            } else {
                suggestions.add("建议维持现有资金安排，并跟踪大额支出计划。");
            }
        } else {
            if ("UP".equals(direction)) {
                suggestions.add("建议提前准备资源和库存，承接增长趋势。");
            } else if ("DOWN".equals(direction)) {
                suggestions.add("建议关注下滑原因，必要时调整促销或采销节奏。");
            } else {
                suggestions.add("建议保持当前经营节奏，同时继续跟踪核心指标。");
            }
        }
        if ("HIGH".equals(volatility)) {
            suggestions.add("历史波动较大，建议结合人工判断，不要只依赖单次预测结果决策。");
        } else if ("MEDIUM".equals(volatility)) {
            suggestions.add("建议结合最近业务活动或节假日因素一起判断趋势。");
        }
        return suggestions;
    }

    private String resolveTargetName(TrendForecastResult result) {
        if (StringUtils.hasText(result.getResolvedMaterialName())) {
            return "商品“" + result.getResolvedMaterialName() + "”销量";
        }
        return switch (result.getMetricType()) {
            case "purchase" -> "采购金额";
            case "retail" -> "零售金额";
            case "receive" -> "收款金额";
            case "pay" -> "付款金额";
            case "income" -> "收入金额";
            case "expense" -> "支出金额";
            case "transfer" -> "转账金额";
            case "advance" -> "预付款金额";
            default -> "销售金额";
        };
    }

    private String granularityLabel(String granularity) {
        return switch (granularity) {
            case "week" -> "周";
            case "month" -> "月";
            default -> "天";
        };
    }

    private String safeAmount(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String normalizeBusinessMetricType(String metricType) {
        if (!StringUtils.hasText(metricType)) {
            return "sale";
        }
        return switch (metricType.trim().toLowerCase(Locale.ROOT)) {
            case "purchase", "procurement", "采购" -> "purchase";
            case "retail", "零售" -> "retail";
            default -> "sale";
        };
    }

    private String normalizeFinanceMetricType(String metricType) {
        if (!StringUtils.hasText(metricType)) {
            return "receive";
        }
        return switch (metricType.trim().toLowerCase(Locale.ROOT)) {
            case "pay", "payment", "付款" -> "pay";
            case "income", "收入" -> "income";
            case "expense", "支出" -> "expense";
            case "transfer", "转账" -> "transfer";
            case "advance", "预付款", "收预付款" -> "advance";
            default -> "receive";
        };
    }

    private String normalizeGranularity(String granularity) {
        if (!StringUtils.hasText(granularity)) {
            return "day";
        }
        return switch (granularity.trim().toLowerCase(Locale.ROOT)) {
            case "week", "weekly", "周" -> "week";
            case "month", "monthly", "月" -> "month";
            default -> "day";
        };
    }

    private String normalizeReportKind(String reportKind) {
        if (!StringUtils.hasText(reportKind)) {
            throw new IllegalArgumentException("reportKind 不能为空");
        }
        return switch (reportKind.trim().toLowerCase(Locale.ROOT)) {
            case REPORT_KIND_BUSINESS, "业务" -> REPORT_KIND_BUSINESS;
            case REPORT_KIND_CASHFLOW, "finance", "资金" -> REPORT_KIND_CASHFLOW;
            case REPORT_KIND_MATERIAL, "商品", "material_demand" -> REPORT_KIND_MATERIAL;
            default -> throw new IllegalArgumentException("不支持的 reportKind: " + reportKind);
        };
    }

    private int normalizeForecastPeriods(Integer periods, String granularity) {
        if (periods != null && periods > 0) {
            return Math.min(periods, 12);
        }
        return switch (granularity) {
            case "week" -> 4;
            case "month" -> 3;
            default -> 7;
        };
    }

    private LocalDate resolveStartDate(String startDate, String granularity) {
        if (StringUtils.hasText(startDate)) {
            return parseDate(startDate);
        }
        LocalDate now = LocalDate.now();
        return switch (granularity) {
            case "week" -> alignWeek(now).minusWeeks(11);
            case "month" -> now.withDayOfMonth(1).minusMonths(11);
            default -> now.minusDays(29);
        };
    }

    private LocalDate resolveEndDate(String endDate, String granularity) {
        LocalDate currentPeriodEnd = currentPeriodEnd(granularity);
        if (StringUtils.hasText(endDate)) {
            LocalDate parsedEnd = parseDate(endDate);
            return parsedEnd.isAfter(currentPeriodEnd) ? currentPeriodEnd : parsedEnd;
        }
        return currentPeriodEnd;
    }

    private LocalDate currentPeriodEnd(String granularity) {
        LocalDate now = LocalDate.now();
        return switch (granularity) {
            case "week" -> alignWeek(now);
            case "month" -> now.withDayOfMonth(1);
            default -> now;
        };
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        return LocalDate.now();
    }

    private LocalDate alignWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String formatWeek(LocalDate monday) {
        return alignWeek(monday).format(DAY_FORMATTER);
    }

    private LocalDateTime atStartOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIN);
    }

    private LocalDateTime atEndOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.of(23, 59, 59));
    }

    private Long tenantId(Long tenantId) {
        return TenantContextHolder.resolveTenantId(tenantId, DEFAULT_TENANT_ID);
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void attachReportMetadata(ForecastReportRequest request, TrendForecastResult result) {
        result.setReportTitle(buildReportTitle(request, result));
        result.setReportFileName(buildReportFileName(request, result));
        result.setDownloadUrl(buildDownloadUrl(request));
    }

    private String buildReportTitle(ForecastReportRequest request, TrendForecastResult result) {
        return switch (request.getReportKind()) {
            case REPORT_KIND_BUSINESS -> "业务趋势预测报告";
            case REPORT_KIND_CASHFLOW -> "资金趋势预测报告";
            default -> StringUtils.hasText(result.getResolvedMaterialName())
                    ? "商品销量预测报告（" + result.getResolvedMaterialName() + "）"
                    : "商品销量预测报告";
        };
    }

    private String buildReportFileName(ForecastReportRequest request, TrendForecastResult result) {
        String datePart = LocalDate.now().format(DAY_FORMATTER);
        return switch (request.getReportKind()) {
            case REPORT_KIND_BUSINESS ->
                    "业务趋势预测_" + request.getMetricType() + "_" + request.getGranularity() + "_" + datePart + ".xls";
            case REPORT_KIND_CASHFLOW ->
                    "资金趋势预测_" + request.getMetricType() + "_" + request.getGranularity() + "_" + datePart + ".xls";
            default -> "商品销量预测_" + sanitizeFileSegment(resolveMaterialFileLabel(request, result)) + "_" + datePart + ".xls";
        };
    }

    private String resolveMaterialFileLabel(ForecastReportRequest request, TrendForecastResult result) {
        if (StringUtils.hasText(result.getResolvedMaterialName())) {
            return result.getResolvedMaterialName();
        }
        if (StringUtils.hasText(request.getMaterialKeyword())) {
            return request.getMaterialKeyword();
        }
        return "未找到匹配商品";
    }

    private String sanitizeFileSegment(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String buildDownloadUrl(ForecastReportRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(REPORT_DOWNLOAD_PATH)
                .queryParam("reportKind", request.getReportKind())
                .queryParam("granularity", request.getGranularity())
                .queryParam("periods", request.getPeriods())
                .queryParam("startDate", request.getStartDate())
                .queryParam("endDate", request.getEndDate());
        if (StringUtils.hasText(request.getMetricType())) {
            builder.queryParam("metricType", request.getMetricType());
        }
        if (StringUtils.hasText(request.getMaterialKeyword())) {
            builder.queryParam("materialKeyword", request.getMaterialKeyword());
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUriString();
    }
}
