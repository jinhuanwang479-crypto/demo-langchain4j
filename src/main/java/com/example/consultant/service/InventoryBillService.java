package com.example.consultant.service;

import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.mapper.InventoryBillMapper;
import com.example.consultant.pojo.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class InventoryBillService {

    private static final Long DEFAULT_TENANT_ID = 160L;
    private static final Long DEFAULT_CREATOR_ID = 160L;
    private static final int DEFAULT_LIMIT = 20;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InventoryBillMapper inventoryBillMapper;
    private final MaterialService materialService;
    private final ObjectMapper objectMapper;

    public InventoryBillService(InventoryBillMapper inventoryBillMapper, MaterialService materialService, ObjectMapper objectMapper) {
        this.inventoryBillMapper = inventoryBillMapper;
        this.materialService = materialService;
        this.objectMapper = objectMapper;
    }

    public List<BillSummaryResult> listBills(String subType, String status, String keyword,
                                             String startDate, String endDate, Long tenantId, Integer limit) {
        return inventoryBillMapper.listBills(normalized(subType), normalized(status), normalized(keyword),
                parseQueryDateTime(startDate, false), parseQueryDateTime(endDate, true), tenantId(tenantId), limit(limit));
    }

    public BillDetailResult getBillDetail(String number, Long tenantId) {
        BillDetailResult detail = inventoryBillMapper.getBillHeadByNumber(number, tenantId(tenantId));
        if (detail != null) {
            detail.setItems(inventoryBillMapper.listBillItemsByNumber(number, tenantId(tenantId)));
        }
        return detail;
    }

    public ToolActionResult createBill(String type, String subType, Long partnerId, Long accountId,
                                       String operTime, String payType, Long creator, String itemsJson,
                                       BigDecimal changeAmount, BigDecimal discountMoney, BigDecimal otherMoney,
                                       BigDecimal deposit, String remark, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        List<BillItemRequest> items = parseItems(itemsJson);
        BigDecimal totalPrice = items.stream()
                .map(this::itemAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 单据编号仍然沿用 ERP 的序列规则，方便和原系统数据对齐。
        String number = nextBusinessNumber(prefix(subType));
        BillInsertParam param = new BillInsertParam();
        param.setType(type);
        param.setSubType(subType);
        param.setDefaultNumber(number);
        param.setNumber(number);
        param.setCreateTime(LocalDateTime.now());
        param.setOperTime(parseBusinessDateTime(operTime));
        param.setOrganId(partnerId);
        param.setCreator(creator == null ? DEFAULT_CREATOR_ID : creator);
        param.setAccountId(accountId);
        param.setChangeAmount(changeAmount == null ? totalPrice : changeAmount);
        param.setTotalPrice(totalPrice);
        param.setPayType(normalized(payType));
        param.setRemark(normalized(remark));
        param.setDiscountMoney(defaultDecimal(discountMoney));
        param.setDiscountLastMoney(totalPrice.subtract(defaultDecimal(discountMoney)));
        param.setOtherMoney(defaultDecimal(otherMoney));
        param.setDeposit(defaultDecimal(deposit));
        param.setStatus("0");
        param.setPurchaseStatus("0");
        param.setSource("0");
        param.setTenantId(resolvedTenantId);
        inventoryBillMapper.insertBillHead(param);

        for (BillItemRequest item : items) {
            BillItemInsertParam itemParam = new BillItemInsertParam();
            itemParam.setHeaderId(param.getId());
            itemParam.setMaterialId(item.getMaterialId());
            itemParam.setMaterialExtendId(item.getMaterialExtendId());
            itemParam.setMaterialUnit(item.getMaterialUnit());
            itemParam.setSku(item.getSku());
            itemParam.setOperNumber(item.getOperNumber());
            itemParam.setBasicNumber(item.getBasicNumber() == null ? item.getOperNumber() : item.getBasicNumber());
            itemParam.setUnitPrice(item.getUnitPrice());
            itemParam.setPurchaseUnitPrice(item.getPurchaseUnitPrice());
            itemParam.setAllPrice(itemAmount(item));
            itemParam.setRemark(item.getRemark());
            itemParam.setDepotId(item.getDepotId());
            itemParam.setAnotherDepotId(item.getAnotherDepotId());
            itemParam.setTaxRate(item.getTaxRate());
            itemParam.setTaxMoney(item.getTaxMoney());
            itemParam.setTaxLastMoney(item.getTaxLastMoney());
            itemParam.setMaterialType(item.getMaterialType());
            itemParam.setSnList(item.getSnList());
            itemParam.setBatchNumber(item.getBatchNumber());
            itemParam.setExpirationDate(parseOptionalDateTime(item.getExpirationDate(), false));
            itemParam.setLinkId(item.getLinkId());
            itemParam.setTenantId(resolvedTenantId);
            inventoryBillMapper.insertBillItem(itemParam);

            // 入库与出库对当前库存做相反方向的数量变更。
            if ("入库".equals(type)) {
                materialService.changeCurrentStock(item.getMaterialId(), item.getDepotId(), item.getOperNumber(), item.getUnitPrice(), resolvedTenantId);
            } else if ("出库".equals(type)) {
                materialService.changeCurrentStock(item.getMaterialId(), item.getDepotId(), item.getOperNumber().negate(), item.getUnitPrice(), resolvedTenantId);
            }
        }

        return new ToolActionResult("创建单据", "单据创建成功", param.getId(), number);
    }

    public ToolActionResult updateBillStatus(String number, String status, Long tenantId) {
        int rows = inventoryBillMapper.updateBillStatus(number, status, tenantId(tenantId));
        return new ToolActionResult("更新单据状态", rows > 0 ? "单据状态更新成功" : "未找到单据", null, number);
    }

    private List<BillItemRequest> parseItems(String itemsJson) {
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("商品明细 JSON 格式不正确: " + e.getMessage(), e);
        }
    }

    private String nextBusinessNumber(String prefix) {
        inventoryBillMapper.increaseSequence();
        Long currentValue = inventoryBillMapper.getCurrentSequenceValue();
        return prefix + String.format("%011d", currentValue == null ? 1L : currentValue);
    }

    private String prefix(String subType) {
        if (!StringUtils.hasText(subType)) {
            return "DJ";
        }
        return switch (subType.trim()) {
            case "采购订单" -> "CGDD";
            case "采购" -> "CGRK";
            case "采购退货" -> "CGTH";
            case "销售订单" -> "XSDD";
            case "销售" -> "XSCK";
            case "销售退货" -> "XSTH";
            case "零售" -> "LSCK";
            case "零售退货" -> "LSTH";
            case "其它入库", "其他入库" -> "QTRK";
            case "其它出库", "其他出库" -> "QTCK";
            case "调拨出库" -> "DBCK";
            default -> "DJ";
        };
    }

    private BigDecimal itemAmount(BillItemRequest item) {
        if (item.getAllPrice() != null) {
            return item.getAllPrice();
        }
        return defaultDecimal(item.getOperNumber()).multiply(defaultDecimal(item.getUnitPrice()));
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
