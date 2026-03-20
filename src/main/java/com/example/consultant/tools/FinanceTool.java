package com.example.consultant.tools;

import com.example.consultant.pojo.FinanceRecordDetailResult;
import com.example.consultant.pojo.FinanceSummaryResult;
import com.example.consultant.pojo.ToolActionResult;
import com.example.consultant.service.FinanceService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FinanceTool {

    private final FinanceService financeService;

    public FinanceTool(FinanceService financeService) {
        this.financeService = financeService;
    }

    @Tool("查询财务单据")
    public List<FinanceSummaryResult> listFinanceRecords(@P("财务类型，可选") String type,
                                                         @P("状态，可选") String status,
                                                         @P("编号、往来单位或备注关键字，可选") String keyword,
                                                         @P("开始日期或时间，可选") String startDate,
                                                         @P("结束日期或时间，可选") String endDate,
                                                         @P("返回条数，可选") Integer limit) {
        return financeService.listFinanceRecords(type, status, keyword, startDate, endDate, null, limit);
    }

    @Tool("查询财务单据详情")
    public FinanceRecordDetailResult getFinanceRecordDetail(@P("财务单编号") String billNo) {
        return financeService.getFinanceRecordDetail(billNo, null);
    }

    @Tool("创建财务单据")
    public ToolActionResult createFinanceRecord(@P("财务类型") String type,
                                                @P("往来单位ID，可选") Long partnerId,
                                                @P("主账户ID，可选") Long accountId,
                                                @P("经手人ID，可选") Long handsPersonId,
                                                @P("单据时间，格式支持 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd") String billTime,
                                                @P("财务明细JSON数组") String itemsJson,
                                                @P("变动金额，可选") BigDecimal changeAmount,
                                                @P("优惠金额，可选") BigDecimal discountMoney,
                                                @P("备注，可选") String remark,
                                                @P("创建人用户ID，可选") Long creator) {
        return financeService.createFinanceRecord(type, partnerId, accountId, handsPersonId, billTime, itemsJson,
                changeAmount, discountMoney, remark, creator, null);
    }

    @Tool("更新财务单据状态")
    public ToolActionResult updateFinanceStatus(@P("财务单编号") String billNo,
                                                @P("状态值") String status) {
        return financeService.updateFinanceStatus(billNo, status, null);
    }
}
