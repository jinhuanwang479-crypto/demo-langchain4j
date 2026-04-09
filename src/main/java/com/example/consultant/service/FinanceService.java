package com.example.consultant.service;

import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.mapper.FinanceMapper;
import com.example.consultant.mapper.InventoryBillMapper;
import com.example.consultant.pojo.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 财务单据服务。
 * <p>
 * 负责财务单据的查询、详情组装、创建和状态更新，
 * 同时处理财务明细解析、金额汇总、业务编号生成和租户隔离。
 * </p>
 */
@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private static final Long DEFAULT_TENANT_ID = 160L;
    private static final Long DEFAULT_CREATOR_ID = 160L;
    private static final int DEFAULT_LIMIT = 20;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FinanceMapper financeMapper;
    private final InventoryBillMapper inventoryBillMapper;
    private final ObjectMapper objectMapper;

    public FinanceService(FinanceMapper financeMapper, InventoryBillMapper inventoryBillMapper, ObjectMapper objectMapper) {
        this.financeMapper = financeMapper;
        this.inventoryBillMapper = inventoryBillMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询财务单据列表。
     */
    public List<FinanceSummaryResult> listFinanceRecords(String type, String status, String keyword,
                                                         String startDate, String endDate, Long tenantId, Integer limit) {
        return financeMapper.listFinanceRecords(normalized(type), normalized(status), normalized(keyword),
                parseQueryDateTime(startDate, false), parseQueryDateTime(endDate, true), tenantId(tenantId), limit(limit));
    }

    /**
     * 查询财务单据详情及其明细。
     */
    public FinanceRecordDetailResult getFinanceRecordDetail(String billNo, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        log.info("查询财务单据详情: billNo={}, explicitTenantId={}, contextTenantId={}, resolvedTenantId={}",
                billNo, tenantId, TenantContextHolder.getTenantId(), resolvedTenantId);
        FinanceRecordDetailResult detail = financeMapper.getFinanceHeadByBillNo(billNo, resolvedTenantId);
        if (detail != null) {
            detail.setItems(financeMapper.listFinanceItemsByBillNo(billNo, resolvedTenantId));
        }
        return detail;
    }

    /**
     * 创建财务单据。
     */
    public ToolActionResult createFinanceRecord(String type, Long partnerId, Long accountId,
                                                Long handsPersonId, String billTime, String itemsJson,
                                                BigDecimal changeAmount, BigDecimal discountMoney,
                                                String remark, Long creator, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        List<FinanceItemRequest> items = parseItems(itemsJson);
        if (items.isEmpty() && accountId != null) {
            FinanceItemRequest fallback = new FinanceItemRequest();
            fallback.setAccountId(accountId);
            fallback.setEachAmount(changeAmount);
            items = List.of(fallback);
        }

        BigDecimal totalPrice = items.stream()
                .map(this::itemAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal resolvedChangeAmount = changeAmount == null ? totalPrice : changeAmount;

        // 财务单号与业务单据共用序列，保持编号体系一致。
        String billNo = nextBusinessNumber(prefix(type));

        FinanceHeadInsertParam param = new FinanceHeadInsertParam();
        param.setType(type);
        param.setOrganId(partnerId);
        param.setHandsPersonId(handsPersonId);
        param.setCreator(creator == null ? DEFAULT_CREATOR_ID : creator);
        param.setChangeAmount(resolvedChangeAmount);
        param.setDiscountMoney(defaultDecimal(discountMoney));
        param.setTotalPrice(totalPrice);
        param.setAccountId(accountId);
        param.setBillNo(billNo);
        param.setBillTime(parseBusinessDateTime(billTime));
        param.setRemark(normalized(remark));
        param.setStatus("0");
        param.setSource("0");
        param.setTenantId(resolvedTenantId);
        financeMapper.insertFinanceHead(param);

        for (FinanceItemRequest item : items) {
            FinanceItemInsertParam itemParam = new FinanceItemInsertParam();
            itemParam.setHeaderId(param.getId());
            itemParam.setAccountId(item.getAccountId());
            itemParam.setInOutItemId(item.getInOutItemId());
            itemParam.setBillId(item.getBillId());
            itemParam.setNeedDebt(item.getNeedDebt());
            itemParam.setFinishDebt(item.getFinishDebt());
            itemParam.setEachAmount(itemAmount(item));
            itemParam.setRemark(item.getRemark());
            itemParam.setTenantId(resolvedTenantId);
            financeMapper.insertFinanceItem(itemParam);
        }

        applyAccountBalanceChange(type, accountId, resolvedChangeAmount, items, resolvedTenantId);
        return new ToolActionResult("创建财务单据", "财务单据创建成功", param.getId(), billNo);
    }

    /**
     * 更新财务单据状态。
     */
    public ToolActionResult updateFinanceStatus(String billNo, String status, Long tenantId) {
        int rows = financeMapper.updateFinanceStatus(billNo, status, tenantId(tenantId));
        return new ToolActionResult("更新财务单据状态", rows > 0 ? "财务单据状态更新成功" : "未找到财务单", null, billNo);
    }

    private void applyAccountBalanceChange(String type, Long headerAccountId, BigDecimal changeAmount,
                                           List<FinanceItemRequest> items, Long tenantId) {
        BigDecimal absoluteChange = defaultDecimal(changeAmount).abs();
        if ("转账".equals(type)) {
            if (headerAccountId != null) {
                financeMapper.increaseAccountBalance(headerAccountId, absoluteChange.negate(), tenantId);
            }
            for (FinanceItemRequest item : items) {
                if (item.getAccountId() != null && itemAmount(item) != null) {
                    financeMapper.increaseAccountBalance(item.getAccountId(), itemAmount(item).abs(), tenantId);
                }
            }
            return;
        }

        BigDecimal delta;
        if (List.of("收款", "收入", "收预付款").contains(type)) {
            delta = absoluteChange;
        } else if (List.of("付款", "支出").contains(type)) {
            delta = absoluteChange.negate();
        } else {
            delta = changeAmount;
        }

        if (headerAccountId != null && delta != null) {
            financeMapper.increaseAccountBalance(headerAccountId, delta, tenantId);
            return;
        }

        for (FinanceItemRequest item : items) {
            if (item.getAccountId() != null) {
                BigDecimal eachAmount = itemAmount(item).abs();
                financeMapper.increaseAccountBalance(item.getAccountId(),
                        delta != null && delta.signum() < 0 ? eachAmount.negate() : eachAmount, tenantId);
            }
        }
    }

    private List<FinanceItemRequest> parseItems(String itemsJson) {
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("财务明细 JSON 格式不正确: " + e.getMessage(), e);
        }
    }

    private BigDecimal itemAmount(FinanceItemRequest item) {
        return item.getAmount() != null ? item.getAmount() : defaultDecimal(item.getEachAmount());
    }

    private String nextBusinessNumber(String prefix) {
        inventoryBillMapper.increaseSequence();
        Long currentValue = inventoryBillMapper.getCurrentSequenceValue();
        return prefix + String.format("%011d", currentValue == null ? 1L : currentValue);
    }

    private String prefix(String type) {
        if (!StringUtils.hasText(type)) {
            return "CW";
        }
        return switch (type.trim()) {
            case "收入" -> "SR";
            case "支出" -> "ZC";
            case "收款" -> "SK";
            case "付款" -> "FK";
            case "转账" -> "ZZ";
            case "收预付款" -> "SYF";
            default -> "CW";
        };
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long tenantId(Long tenantId) {
        // 显式传参优先，其次读取当前请求租户，最后回退到默认租户。
        return TenantContextHolder.resolveTenantId(tenantId, DEFAULT_TENANT_ID);
    }

    private int limit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
    }

    private LocalDateTime parseQueryDateTime(String value, boolean endOfDay) {
        return parseOptionalDateTime(value, endOfDay);
    }

    private LocalDateTime parseBusinessDateTime(String value) {
        LocalDateTime parsed = parseOptionalDateTime(value, false);
        return parsed == null ? LocalDateTime.now() : parsed;
    }

    private LocalDateTime parseOptionalDateTime(String value, boolean endOfDay) {
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
