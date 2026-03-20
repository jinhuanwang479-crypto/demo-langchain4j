package com.example.consultant.mapper;

import com.example.consultant.pojo.FinanceHeadInsertParam;
import com.example.consultant.pojo.FinanceItemInsertParam;
import com.example.consultant.pojo.FinanceItemResult;
import com.example.consultant.pojo.FinanceRecordDetailResult;
import com.example.consultant.pojo.FinanceSummaryResult;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FinanceMapper {

    @Select("""
            select h.id, h.type, h.bill_no as billNo, h.bill_time as billTime, h.change_amount as changeAmount,
                   h.discount_money as discountMoney, h.total_price as totalPrice, h.status, h.remark,
                   s.supplier as partnerName, a.name as accountName
            from jsh_account_head h
            left join jsh_supplier s on h.organ_id = s.id and s.delete_flag = '0'
            left join jsh_account a on h.account_id = a.id and a.delete_flag = '0'
            where h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and (#{type} is null or h.type = #{type})
              and (#{status} is null or h.status = #{status})
              and (#{keyword} is null or h.bill_no like concat('%', #{keyword}, '%')
                   or s.supplier like concat('%', #{keyword}, '%')
                   or h.remark like concat('%', #{keyword}, '%'))
              and (#{startTime} is null or h.bill_time >= #{startTime})
              and (#{endTime} is null or h.bill_time <= #{endTime})
            order by h.bill_time desc, h.id desc
            limit #{limit}
            """)
    List<FinanceSummaryResult> listFinanceRecords(@Param("type") String type, @Param("status") String status,
                                                  @Param("keyword") String keyword, @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime, @Param("tenantId") Long tenantId,
                                                  @Param("limit") Integer limit);

    @Select("""
            select h.id, h.type, h.bill_no as billNo, h.bill_time as billTime, h.change_amount as changeAmount,
                   h.discount_money as discountMoney, h.total_price as totalPrice, h.status, h.remark,
                   s.id as partnerId, s.supplier as partnerName, a.id as accountId, a.name as accountName,
                   p.id as handsPersonId, p.name as handsPersonName
            from jsh_account_head h
            left join jsh_supplier s on h.organ_id = s.id and s.delete_flag = '0'
            left join jsh_account a on h.account_id = a.id and a.delete_flag = '0'
            left join jsh_person p on h.hands_person_id = p.id and p.delete_flag = '0'
            where h.bill_no = #{billNo} and h.tenant_id = #{tenantId} and h.delete_flag = '0'
            limit 1
            """)
    FinanceRecordDetailResult getFinanceHeadByBillNo(@Param("billNo") String billNo, @Param("tenantId") Long tenantId);

    @Select("""
            select i.id, i.account_id as accountId, a.name as accountName, i.in_out_item_id as inOutItemId,
                   io.name as inOutItemName, i.bill_id as billId, i.need_debt as needDebt,
                   i.finish_debt as finishDebt, i.each_amount as eachAmount, i.remark
            from jsh_account_item i
            left join jsh_account a on i.account_id = a.id and a.delete_flag = '0'
            left join jsh_in_out_item io on i.in_out_item_id = io.id and io.delete_flag = '0'
            join jsh_account_head h on i.header_id = h.id
            where h.bill_no = #{billNo} and h.tenant_id = #{tenantId} and i.delete_flag = '0'
            order by i.id
            """)
    List<FinanceItemResult> listFinanceItemsByBillNo(@Param("billNo") String billNo, @Param("tenantId") Long tenantId);

    @Insert("""
            insert into jsh_account_head(type, organ_id, hands_person_id, creator, change_amount, discount_money,
                                         total_price, account_id, bill_no, bill_time, remark, status, source,
                                         tenant_id, delete_flag)
            values(#{type}, #{organId}, #{handsPersonId}, #{creator}, #{changeAmount}, #{discountMoney},
                   #{totalPrice}, #{accountId}, #{billNo}, #{billTime}, #{remark}, #{status}, #{source},
                   #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFinanceHead(FinanceHeadInsertParam param);

    @Insert("""
            insert into jsh_account_item(header_id, account_id, in_out_item_id, bill_id, need_debt, finish_debt,
                                         each_amount, remark, tenant_id, delete_flag)
            values(#{headerId}, #{accountId}, #{inOutItemId}, #{billId}, #{needDebt}, #{finishDebt},
                   #{eachAmount}, #{remark}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFinanceItem(FinanceItemInsertParam param);

    @Update("""
            update jsh_account_head
            set status = #{status}
            where bill_no = #{billNo} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateFinanceStatus(@Param("billNo") String billNo, @Param("status") String status, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_account
            set current_amount = coalesce(current_amount, 0) + #{delta}
            where id = #{accountId} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int increaseAccountBalance(@Param("accountId") Long accountId, @Param("delta") BigDecimal delta, @Param("tenantId") Long tenantId);
}
