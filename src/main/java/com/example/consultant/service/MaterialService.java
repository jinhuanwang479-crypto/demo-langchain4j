package com.example.consultant.service;

import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.mapper.MaterialMapper;
import com.example.consultant.mapper.SystemManagementMapper;
import com.example.consultant.pojo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 商品资料服务。
 * <p>
 * 负责商品分类、商品属性、商品基础资料、价格和库存等能力，
 * 是 `materialTool` 的核心业务实现层。
 * </p>
 */
@Service
public class MaterialService {

    private static final Long DEFAULT_TENANT_ID = 160L;
    private static final int DEFAULT_LIMIT = 20;

    private final MaterialMapper materialMapper;
    private final SystemManagementMapper systemManagementMapper;
    private final ObjectMapper objectMapper;

    public MaterialService(MaterialMapper materialMapper, SystemManagementMapper systemManagementMapper,
                           ObjectMapper objectMapper) {
        this.materialMapper = materialMapper;
        this.systemManagementMapper = systemManagementMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询商品分类。
     */
    public List<MaterialCategory> listMaterialCategories(String keyword, Long tenantId) {
        return materialMapper.listMaterialCategories(normalized(keyword), tenantId(tenantId));
    }

    /**
     * 查询商品属性。
     */
    public List<MaterialAttribute> listMaterialAttributes(String keyword, Long tenantId) {
        return materialMapper.listMaterialAttributes(normalized(keyword), tenantId(tenantId));
    }

    /**
     * 查询商品扩展属性。
     */
    public List<MaterialProperty> listMaterialProperties(String keyword, Long tenantId) {
        return materialMapper.listMaterialProperties(normalized(keyword), tenantId(tenantId));
    }

    /**
     * 查询商品列表。
     */
    public List<MaterialInfo> searchMaterials(String keyword, Long categoryId, Long tenantId, Integer limit) {
        return materialMapper.searchMaterials(normalized(keyword), categoryId, tenantId(tenantId), limit(limit));
    }

    /**
     * 查询当前租户下的商品清单。
     */
    public List<MaterialInfo> listCurrentMaterials(Long tenantId, Integer limit) {
        // 用户直接问“目前有哪些商品”时，不需要关键字，直接返回当前租户下的商品列表。
        return materialMapper.searchMaterials(null, null, tenantId(tenantId), limit(limit));
    }

    /**
     * 查询商品详情。
     */
    public MaterialDetailResult getMaterialDetail(Long materialId, Long tenantId) {
        MaterialDetailResult result = new MaterialDetailResult();
        result.setMaterial(materialMapper.getMaterialById(materialId, tenantId(tenantId)));
        result.setPrices(materialMapper.listMaterialPrices(materialId, tenantId(tenantId)));
        result.setStocks(materialMapper.listMaterialStocksByMaterial(materialId, tenantId(tenantId)));
        return result;
    }

    /**
     * 创建商品基础资料。
     */
    public ToolActionResult createMaterial(String name, Long categoryId, String unit, Long unitId,
                                           String model, String standard, String brand, String mnemonic,
                                           String remark, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        String normalizedName = normalized(name);
        String normalizedUnit = normalized(unit);

        ToolActionResult validationResult = validateCreateMaterial(normalizedName, categoryId, normalizedUnit, unitId,
                resolvedTenantId);
        if (validationResult != null) {
            return validationResult;
        }

        MaterialInsertParam param = new MaterialInsertParam();
        param.setCategoryId(categoryId);
        param.setName(normalizedName);
        param.setUnit(normalizedUnit);
        param.setUnitId(unitId);
        param.setModel(normalized(model));
        param.setStandard(normalized(standard));
        param.setBrand(normalized(brand));
        param.setMnemonic(normalized(mnemonic));
        param.setRemark(normalized(remark));
        param.setEnabled(1);
        param.setTenantId(resolvedTenantId);
        materialMapper.insertMaterial(param);
        return new ToolActionResult("创建商品", "商品创建成功", param.getId(), null);
    }

    /**
     * 保存商品元数据，如分类、属性或扩展属性。
     */
    public ToolActionResult saveMaterialMeta(String entityType, String dataJson, Long tenantId) {
        String type = normalizeMetaType(entityType);
        return switch (type) {
            case "category" -> saveCategory(dataJson, tenantId(tenantId));
            case "attribute" -> saveAttribute(dataJson, tenantId(tenantId));
            case "property" -> saveProperty(dataJson, tenantId(tenantId));
            default -> new ToolActionResult("保存商品资料", "不支持的商品资料类型", null, null);
        };
    }

    /**
     * 更新商品默认价格。
     */
    public ToolActionResult updateMaterialPrice(Long materialId, BigDecimal purchasePrice, BigDecimal retailPrice,
                                                BigDecimal salePrice, String barCode, String unitName, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        Long priceId = materialMapper.findDefaultMaterialPriceId(materialId, resolvedTenantId);
        if (priceId == null) {
            MaterialPriceInsertParam insertParam = new MaterialPriceInsertParam();
            insertParam.setMaterialId(materialId);
            insertParam.setBarCode(normalized(barCode));
            insertParam.setCommodityUnit(normalized(unitName));
            insertParam.setPurchasePrice(purchasePrice);
            insertParam.setRetailPrice(retailPrice);
            insertParam.setSalePrice(salePrice);
            insertParam.setLowPrice(salePrice);
            insertParam.setCreateTime(LocalDateTime.now());
            insertParam.setTenantId(resolvedTenantId);
            materialMapper.insertMaterialPrice(insertParam);
            return new ToolActionResult("更新商品价格", "已新增默认价格行", insertParam.getId(), null);
        }

        MaterialPriceUpdateParam updateParam = new MaterialPriceUpdateParam();
        updateParam.setId(priceId);
        updateParam.setBarCode(normalized(barCode));
        updateParam.setCommodityUnit(normalized(unitName));
        updateParam.setPurchasePrice(purchasePrice);
        updateParam.setRetailPrice(retailPrice);
        updateParam.setSalePrice(salePrice);
        updateParam.setLowPrice(salePrice);
        updateParam.setUpdateTime(System.currentTimeMillis());
        updateParam.setTenantId(resolvedTenantId);
        materialMapper.updateMaterialPrice(updateParam);
        return new ToolActionResult("更新商品价格", "商品价格更新成功", priceId, null);
    }

    /**
     * 查询商品库存。
     */
    public List<MaterialStockResult> getMaterialStock(String keyword, Long depotId, Long tenantId, Integer limit) {
        return materialMapper.listMaterialStocks(normalized(keyword), depotId, tenantId(tenantId), limit(limit));
    }

    /**
     * 按商品ID查询库存。
     */
    public List<MaterialStockResult> getMaterialStockByMaterialId(Long materialId, Long tenantId) {
        if (materialId == null) {
            return List.of();
        }
        return materialMapper.listMaterialStocksByMaterial(materialId, tenantId(tenantId));
    }

    /**
     * 调整商品当前库存。
     */
    public void changeCurrentStock(Long materialId, Long depotId, BigDecimal delta, BigDecimal unitPrice, Long tenantId) {
        if (materialId == null || depotId == null || delta == null) {
            return;
        }
        Long resolvedTenantId = tenantId(tenantId);
        Long stockId = materialMapper.findCurrentStockId(materialId, depotId, resolvedTenantId);
        if (stockId == null) {
            MaterialCurrentStockInsertParam param = new MaterialCurrentStockInsertParam();
            param.setMaterialId(materialId);
            param.setDepotId(depotId);
            param.setCurrentNumber(delta);
            param.setCurrentUnitPrice(unitPrice);
            param.setTenantId(resolvedTenantId);
            materialMapper.insertCurrentStock(param);
            return;
        }
        // 只做库存变更，具体业务方向由上层单据服务控制。
        materialMapper.updateCurrentStock(stockId, delta, unitPrice);
    }

    private ToolActionResult saveCategory(String dataJson, Long tenantId) {
        MaterialCategoryInsertParam param = parseObject(dataJson, MaterialCategoryInsertParam.class);
        param.setTenantId(tenantId);
        param.setCreateTime(LocalDateTime.now());
        param.setUpdateTime(LocalDateTime.now());
        materialMapper.insertCategory(param);
        return new ToolActionResult("保存商品分类", "商品分类创建成功", param.getId(), null);
    }

    private ToolActionResult saveAttribute(String dataJson, Long tenantId) {
        MaterialAttributeInsertParam param = parseObject(dataJson, MaterialAttributeInsertParam.class);
        param.setTenantId(tenantId);
        materialMapper.insertAttribute(param);
        return new ToolActionResult("保存商品属性", "商品属性创建成功", param.getId(), null);
    }

    private ToolActionResult saveProperty(String dataJson, Long tenantId) {
        MaterialPropertyInsertParam param = parseObject(dataJson, MaterialPropertyInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        materialMapper.insertProperty(param);
        return new ToolActionResult("保存商品扩展属性", "商品扩展属性创建成功", param.getId(), null);
    }

    private ToolActionResult validateCreateMaterial(String name, Long categoryId, String unit, Long unitId, Long tenantId) {
        if (!StringUtils.hasText(name)) {
            return new ToolActionResult("创建商品", "商品名称不能为空", null, null);
        }
        if (categoryId == null) {
            return new ToolActionResult("创建商品", "商品分类不能为空，请先查询并确认分类", null, null);
        }
        if (!StringUtils.hasText(unit)) {
            return new ToolActionResult("创建商品", "商品单位不能为空，请先查询并确认单位", null, null);
        }
        if (unitId == null) {
            return new ToolActionResult("创建商品", "商品单位ID不能为空，请先查询并确认单位", null, null);
        }
        Integer categoryCount = materialMapper.countMaterialCategoryById(categoryId, tenantId);
        if (categoryCount == null || categoryCount <= 0) {
            return new ToolActionResult("创建商品", "未找到对应的商品分类，请重新确认分类", null, null);
        }

        ErpUnit erpUnit = systemManagementMapper.getUnitById(unitId, tenantId);
        if (erpUnit == null) {
            return new ToolActionResult("创建商品", "未找到对应的计量单位，请重新确认单位", null, null);
        }

        if (!resolveAllowedUnits(erpUnit).contains(unit)) {
            return new ToolActionResult("创建商品", "商品单位必须是所选计量单位下的实际单位值", null, null);
        }
        return null;
    }

    private Set<String> resolveAllowedUnits(ErpUnit erpUnit) {
        Set<String> units = new LinkedHashSet<>();
        Stream.of(erpUnit.getBasicUnit(), erpUnit.getOtherUnit(), erpUnit.getOtherUnitTwo(), erpUnit.getOtherUnitThree())
                .map(this::normalized)
                .filter(StringUtils::hasText)
                .forEach(units::add);
        return units;
    }

    private String normalizeMetaType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        return switch (entityType.trim().toLowerCase(Locale.ROOT)) {
            case "分类", "商品分类", "materialcategory" -> "category";
            case "属性", "商品属性", "materialattribute" -> "attribute";
            case "扩展属性", "商品扩展属性", "materialproperty" -> "property";
            default -> entityType.trim().toLowerCase(Locale.ROOT);
        };
    }

    private <T> T parseObject(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式不正确: " + e.getMessage(), e);
        }
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
}
