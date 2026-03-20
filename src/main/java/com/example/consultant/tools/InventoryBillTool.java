package com.example.consultant.tools;

import com.example.consultant.pojo.BillDetailResult;
import com.example.consultant.pojo.BillSummaryResult;
import com.example.consultant.pojo.ToolActionResult;
import com.example.consultant.service.InventoryBillService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InventoryBillTool {

    private final InventoryBillService inventoryBillService;

    public InventoryBillTool(InventoryBillService inventoryBillService) {
        this.inventoryBillService = inventoryBillService;
    }

    @Tool("查询业务单据")
    public List<BillSummaryResult> listBills(@P("单据子类型，可选") String subType,
                                             @P("单据状态，可选") String status,
                                             @P("编号、往来单位或备注关键字，可选") String keyword,
                                             @P("开始日期或时间，可选") String startDate,
                                             @P("结束日期或时间，可选") String endDate,
                                             @P("返回条数，可选") Integer limit) {
        return inventoryBillService.listBills(subType, status, keyword, startDate, endDate, null, limit);
    }

    @Tool("查询业务单据详情")
    public BillDetailResult getBillDetail(@P("单据编号") String number) {
        return inventoryBillService.getBillDetail(number, null);
    }

    @Tool("创建业务单据")
    public ToolActionResult createBill(@P("单据类型，例如入库、出库") String type,
                                       @P("单据子类型") String subType,
                                       @P("往来单位ID，可选") Long partnerId,
                                       @P("账户ID，可选") Long accountId,
                                       @P("业务时间，格式支持 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd") String operTime,
                                       @P("付款类型，可选") String payType,
                                       @P("创建人用户ID，可选") Long creator,
                                       @P("商品明细JSON数组") String itemsJson,
                                       @P("变动金额，可选") BigDecimal changeAmount,
                                       @P("优惠金额，可选") BigDecimal discountMoney,
                                       @P("其他费用，可选") BigDecimal otherMoney,
                                       @P("订金，可选") BigDecimal deposit,
                                       @P("备注，可选") String remark) {
        return inventoryBillService.createBill(type, subType, partnerId, accountId, operTime, payType, creator,
                itemsJson, changeAmount, discountMoney, otherMoney, deposit, remark, null);
    }

    @Tool("更新业务单据状态")
    public ToolActionResult updateBillStatus(@P("单据编号") String number,
                                             @P("状态值") String status) {
        return inventoryBillService.updateBillStatus(number, status, null);
    }
}
