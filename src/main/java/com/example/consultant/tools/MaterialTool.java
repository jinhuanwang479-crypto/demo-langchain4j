package com.example.consultant.tools;

import com.example.consultant.pojo.*;
import com.example.consultant.service.MaterialService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class MaterialTool {

    private static final Logger log = LoggerFactory.getLogger(MaterialTool.class);

    private final MaterialService materialService;

    public MaterialTool(MaterialService materialService) {
        this.materialService = materialService;
    }

    @Tool("查询商品分类，用于新增商品前给用户展示可选分类并解析categoryId")
    public List<MaterialCategory> listMaterialCategories(@P("关键字，可选") String keyword) {
        return materialService.listMaterialCategories(keyword, null);
    }

    @Tool("查询商品属性")
    public List<MaterialAttribute> listMaterialAttributes(@P("关键字，可选") String keyword) {
        return materialService.listMaterialAttributes(keyword, null);
    }

    @Tool("查询商品扩展属性")
    public List<MaterialProperty> listMaterialProperties(@P("关键字，可选") String keyword) {
        return materialService.listMaterialProperties(keyword, null);
    }

    @Tool("按名称、关键字或分类查找商品")
    public List<MaterialInfo> searchMaterials(@P("商品名称或关键字，可选") String keyword,
                                              @P("分类ID，可选") Long categoryId,
                                              @P("返回条数，可选") Integer limit) {
        log.info("调用商品查询工具: keyword={}, categoryId={}, limit={}", keyword, categoryId, limit);
        return materialService.searchMaterials(keyword, categoryId, null, limit);
    }

    @Tool("查看当前商品清单或者明细（用户问“有哪些商品”时直接调用，不追问）")
    public List<MaterialInfo> listCurrentMaterials(@P("返回条数，可选，默认20条") Integer limit) {
        log.info("调用当前商品列表工具: limit={}", limit);
        return materialService.listCurrentMaterials(null, limit);
    }
    @Tool("查询商品详情")
    public MaterialDetailResult getMaterialDetail(@P("商品ID") Long materialId) {
        return materialService.getMaterialDetail(materialId, null);
    }

    @Tool("创建商品，调用前必须先确认categoryId、unit和unitId，其中unit必须是用户确认的实际单位值")
    public ToolActionResult createMaterial(@P("商品名称") String name,
                                           @P("分类ID，必填，需先通过查询商品分类工具解析") Long categoryId,
                                           @P("单位名称，必填，需填写实际单位值，如支、包、kg") String unit,
                                           @P("单位ID，必填，需先通过查询计量单位工具解析") Long unitId,
                                           @P("型号，可选") String model,
                                           @P("规格，可选") String standard,
                                           @P("品牌，可选") String brand,
                                           @P("助记码，可选") String mnemonic,
                                           @P("备注，可选") String remark) {
        return materialService.createMaterial(name, categoryId, unit, unitId, model, standard, brand, mnemonic, remark, null);
    }

    @Tool("保存商品基础资料")
    public ToolActionResult saveMaterialMeta(@P("实体类型，例如分类、属性、扩展属性") String entityType,
                                             @P("JSON对象数据") String dataJson) {
        return materialService.saveMaterialMeta(entityType, dataJson, null);
    }

    @Tool("更新商品默认价格")
    public ToolActionResult updateMaterialPrice(@P("商品ID") Long materialId,
                                                @P("采购价，可选") BigDecimal purchasePrice,
                                                @P("零售价，可选") BigDecimal retailPrice,
                                                @P("销售价，可选") BigDecimal salePrice,
                                                @P("条码，可选") String barCode,
                                                @P("单位名称，可选") String unitName) {
        return materialService.updateMaterialPrice(materialId, purchasePrice, retailPrice, salePrice, barCode, unitName, null);
    }

    @Tool("查询商品库存")
    public List<MaterialStockResult> getMaterialStock(@P("关键字，可选") String keyword,
                                                      @P("仓库ID，可选") Long depotId,
                                                      @P("返回条数，可选") Integer limit) {
        return materialService.getMaterialStock(keyword, depotId, null, limit);
    }
}
