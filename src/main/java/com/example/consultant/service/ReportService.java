package com.example.consultant.service;

import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.mapper.ReportMapper;
import com.example.consultant.pojo.DashboardFinanceSummary;
import com.example.consultant.pojo.DashboardSummaryResult;
import com.example.consultant.pojo.MaterialStatisticResult;
import com.example.consultant.pojo.StockWarningResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class ReportService {

    private static final Long DEFAULT_TENANT_ID = 160L;
    private static final int DEFAULT_LIMIT = 20;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public DashboardSummaryResult getDashboardSummary(String startDate, String endDate, Long tenantId) {
        DashboardSummaryResult result = new DashboardSummaryResult();
        result.setStartDate(startDate);
        result.setEndDate(endDate);
        // 看板汇总把业务单据和财务数据聚合到一个返回对象里，便于前端直接渲染。
        result.setBillSummary(reportMapper.getDashboardBillSummary(parseDateTime(startDate, false), parseDateTime(endDate, true), tenantId(tenantId)));
        result.setFinanceSummary(reportMapper.getDashboardFinanceSummary(parseDateTime(startDate, false), parseDateTime(endDate, true), tenantId(tenantId)));
        return result;
    }

    public List<MaterialStatisticResult> getSaleStatistics(String startDate, String endDate, Long tenantId, Integer limit) {
        return reportMapper.getSaleStatistics(parseDateTime(startDate, false), parseDateTime(endDate, true), tenantId(tenantId), limit(limit));
    }

    public List<MaterialStatisticResult> getPurchaseStatistics(String startDate, String endDate, Long tenantId, Integer limit) {
        return reportMapper.getPurchaseStatistics(parseDateTime(startDate, false), parseDateTime(endDate, true), tenantId(tenantId), limit(limit));
    }

    public List<DashboardFinanceSummary> getAccountStatistics(String startDate, String endDate, Long tenantId) {
        return reportMapper.getAccountStatistics(parseDateTime(startDate, false), parseDateTime(endDate, true), tenantId(tenantId));
    }

    public List<StockWarningResult> getStockWarning(Long tenantId, Integer limit) {
        return reportMapper.getStockWarning(tenantId(tenantId), limit(limit));
    }

    private Long tenantId(Long tenantId) {
        // 显式传参优先，其次读取当前请求租户，最后回退到默认租户。
        return TenantContextHolder.resolveTenantId(tenantId, DEFAULT_TENANT_ID);
    }

    private int limit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }
        LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
        return LocalDateTime.of(date, endOfDay ? LocalTime.of(23, 59, 59) : LocalTime.MIN);
    }
}
