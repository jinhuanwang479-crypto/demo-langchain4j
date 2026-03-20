package com.example.consultant.mapper;

import com.example.consultant.pojo.BillDetailResult;
import com.example.consultant.pojo.BillInsertParam;
import com.example.consultant.pojo.BillItemInsertParam;
import com.example.consultant.pojo.BillItemResult;
import com.example.consultant.pojo.BillSummaryResult;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InventoryBillMapper {

    @Select("""
            select h.id, h.type, h.sub_type as subType, h.number, h.oper_time as operTime, h.total_price as totalPrice,
                   h.change_amount as changeAmount, h.status, h.pay_type as payType, h.remark,
                   s.supplier as partnerName, a.name as accountName
            from jsh_depot_head h
            left join jsh_supplier s on h.organ_id = s.id and s.delete_flag = '0'
            left join jsh_account a on h.account_id = a.id and a.delete_flag = '0'
            where h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and (#{subType} is null or h.sub_type = #{subType})
              and (#{status} is null or h.status = #{status})
              and (#{keyword} is null or h.number like concat('%', #{keyword}, '%')
                   or s.supplier like concat('%', #{keyword}, '%')
                   or h.remark like concat('%', #{keyword}, '%'))
              and (#{startTime} is null or h.oper_time >= #{startTime})
              and (#{endTime} is null or h.oper_time <= #{endTime})
            order by h.oper_time desc, h.id desc
            limit #{limit}
            """)
    List<BillSummaryResult> listBills(@Param("subType") String subType, @Param("status") String status,
                                      @Param("keyword") String keyword, @Param("startTime") java.time.LocalDateTime startTime,
                                      @Param("endTime") java.time.LocalDateTime endTime, @Param("tenantId") Long tenantId,
                                      @Param("limit") Integer limit);

    @Select("""
            select h.id, h.type, h.sub_type as subType, h.number, h.oper_time as operTime, h.total_price as totalPrice,
                   h.change_amount as changeAmount, h.discount_money as discountMoney, h.other_money as otherMoney,
                   h.deposit, h.status, h.pay_type as payType, h.remark, h.link_number as linkNumber,
                   s.id as partnerId, s.supplier as partnerName, a.id as accountId, a.name as accountName
            from jsh_depot_head h
            left join jsh_supplier s on h.organ_id = s.id and s.delete_flag = '0'
            left join jsh_account a on h.account_id = a.id and a.delete_flag = '0'
            where h.number = #{number} and h.tenant_id = #{tenantId} and h.delete_flag = '0'
            limit 1
            """)
    BillDetailResult getBillHeadByNumber(@Param("number") String number, @Param("tenantId") Long tenantId);

    @Select("""
            select i.id, i.material_id as materialId, m.name as materialName, i.material_extend_id as materialExtendId,
                   i.material_unit as materialUnit, i.oper_number as operNumber, i.basic_number as basicNumber,
                   i.unit_price as unitPrice, i.purchase_unit_price as purchaseUnitPrice, i.all_price as allPrice,
                   i.depot_id as depotId, d.name as depotName, i.another_depot_id as anotherDepotId,
                   i.tax_rate as taxRate, i.tax_money as taxMoney, i.tax_last_money as taxLastMoney,
                   i.batch_number as batchNumber, i.expiration_date as expirationDate, i.remark
            from jsh_depot_item i
            join jsh_material m on i.material_id = m.id and m.delete_flag = '0'
            left join jsh_depot d on i.depot_id = d.id and d.delete_Flag = '0'
            join jsh_depot_head h on i.header_id = h.id
            where h.number = #{number} and h.tenant_id = #{tenantId} and i.delete_flag = '0'
            order by i.id
            """)
    List<BillItemResult> listBillItemsByNumber(@Param("number") String number, @Param("tenantId") Long tenantId);

    @Insert("""
            insert into jsh_depot_head(type, sub_type, default_number, number, create_time, oper_time, organ_id, creator,
                                       account_id, change_amount, back_amount, total_price, pay_type, bill_type, remark,
                                       discount_money, discount_last_money, other_money, deposit, status,
                                       purchase_status, source, tenant_id, delete_flag)
            values(#{type}, #{subType}, #{defaultNumber}, #{number}, #{createTime}, #{operTime}, #{organId}, #{creator},
                   #{accountId}, #{changeAmount}, 0, #{totalPrice}, #{payType}, null, #{remark},
                   #{discountMoney}, #{discountLastMoney}, #{otherMoney}, #{deposit}, #{status},
                   #{purchaseStatus}, #{source}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBillHead(BillInsertParam param);

    @Insert("""
            insert into jsh_depot_item(header_id, material_id, material_extend_id, material_unit, sku,
                                       oper_number, basic_number, unit_price, purchase_unit_price, tax_unit_price,
                                       all_price, remark, depot_id, another_depot_id, tax_rate, tax_money,
                                       tax_last_money, material_type, sn_list, batch_number, expiration_date,
                                       link_id, tenant_id, delete_flag)
            values(#{headerId}, #{materialId}, #{materialExtendId}, #{materialUnit}, #{sku},
                   #{operNumber}, #{basicNumber}, #{unitPrice}, #{purchaseUnitPrice}, null,
                   #{allPrice}, #{remark}, #{depotId}, #{anotherDepotId}, #{taxRate}, #{taxMoney},
                   #{taxLastMoney}, #{materialType}, #{snList}, #{batchNumber}, #{expirationDate},
                   #{linkId}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBillItem(BillItemInsertParam param);

    @Update("""
            update jsh_depot_head
            set status = #{status}
            where number = #{number} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateBillStatus(@Param("number") String number, @Param("status") String status, @Param("tenantId") Long tenantId);

    @Update("update jsh_sequence set current_val = current_val + increment_val where seq_name = 'depot_number_seq'")
    int increaseSequence();

    @Select("select current_val from jsh_sequence where seq_name = 'depot_number_seq'")
    Long getCurrentSequenceValue();
}
