package com.example.consultant.mapper;

import com.example.consultant.pojo.DashboardBillSummary;
import com.example.consultant.pojo.DashboardFinanceSummary;
import com.example.consultant.pojo.MaterialStatisticResult;
import com.example.consultant.pojo.StockWarningResult;
import com.example.consultant.pojo.TrendSeriesPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper {

    @Select("""
            select
                coalesce(sum(case when sub_type = '采购' then abs(total_price) else 0 end), 0) as purchaseAmount,
                coalesce(sum(case when sub_type = '销售' then total_price else 0 end), 0) as saleAmount,
                coalesce(sum(case when sub_type = '零售' then total_price else 0 end), 0) as retailAmount,
                coalesce(sum(case when sub_type = '销售退货' then abs(total_price) else 0 end), 0) as saleReturnAmount,
                coalesce(sum(case when sub_type = '采购退货' then total_price else 0 end), 0) as purchaseReturnAmount,
                count(1) as billCount
            from jsh_depot_head
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{startTime} is null or oper_time >= #{startTime})
              and (#{endTime} is null or oper_time <= #{endTime})
            """)
    DashboardBillSummary getDashboardBillSummary(@Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime,
                                                 @Param("tenantId") Long tenantId);

    @Select("""
            select type, coalesce(sum(change_amount), 0) as totalAmount, count(1) as recordCount
            from jsh_account_head
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{startTime} is null or bill_time >= #{startTime})
              and (#{endTime} is null or bill_time <= #{endTime})
            group by type
            order by type
            """)
    List<DashboardFinanceSummary> getDashboardFinanceSummary(@Param("startTime") LocalDateTime startTime,
                                                             @Param("endTime") LocalDateTime endTime,
                                                             @Param("tenantId") Long tenantId);

    @Select("""
            select i.material_id as materialId, m.name as materialName,
                   coalesce(sum(i.oper_number), 0) as totalNumber,
                   coalesce(sum(i.all_price), 0) as totalAmount
            from jsh_depot_item i
            join jsh_depot_head h on i.header_id = h.id
            join jsh_material m on i.material_id = m.id and m.delete_flag = '0'
            where i.delete_flag = '0'
              and h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and h.sub_type in ('销售', '零售')
              and (#{startTime} is null or h.oper_time >= #{startTime})
              and (#{endTime} is null or h.oper_time <= #{endTime})
            group by i.material_id, m.name
            order by totalAmount desc, totalNumber desc
            limit #{limit}
            """)
    List<MaterialStatisticResult> getSaleStatistics(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("tenantId") Long tenantId,
                                                    @Param("limit") Integer limit);

    @Select("""
            select i.material_id as materialId, m.name as materialName,
                   coalesce(sum(i.oper_number), 0) as totalNumber,
                   coalesce(sum(i.all_price), 0) as totalAmount
            from jsh_depot_item i
            join jsh_depot_head h on i.header_id = h.id
            join jsh_material m on i.material_id = m.id and m.delete_flag = '0'
            where i.delete_flag = '0'
              and h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and h.sub_type = '采购'
              and (#{startTime} is null or h.oper_time >= #{startTime})
              and (#{endTime} is null or h.oper_time <= #{endTime})
            group by i.material_id, m.name
            order by totalAmount desc, totalNumber desc
            limit #{limit}
            """)
    List<MaterialStatisticResult> getPurchaseStatistics(@Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("tenantId") Long tenantId,
                                                        @Param("limit") Integer limit);

    @Select("""
            select type, coalesce(sum(change_amount), 0) as totalAmount, count(1) as recordCount
            from jsh_account_head
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{startTime} is null or bill_time >= #{startTime})
              and (#{endTime} is null or bill_time <= #{endTime})
            group by type
            order by totalAmount desc
            """)
    List<DashboardFinanceSummary> getAccountStatistics(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       @Param("tenantId") Long tenantId);

    @Select("""
            select s.material_id as materialId, m.name as materialName, s.depot_id as depotId, d.name as depotName,
                   s.current_number as currentNumber, i.low_safe_stock as lowSafeStock, i.high_safe_stock as highSafeStock
            from jsh_material_current_stock s
            join jsh_material m on s.material_id = m.id and m.delete_flag = '0'
            left join jsh_depot d on s.depot_id = d.id and d.delete_Flag = '0'
            join jsh_material_initial_stock i on i.material_id = s.material_id and i.depot_id = s.depot_id
                 and i.tenant_id = s.tenant_id and i.delete_flag = '0'
            where s.delete_flag = '0'
              and s.tenant_id = #{tenantId}
              and ((i.low_safe_stock is not null and s.current_number < i.low_safe_stock)
                   or (i.high_safe_stock is not null and s.current_number > i.high_safe_stock))
            order by s.current_number asc
            limit #{limit}
            """)
    List<StockWarningResult> getStockWarning(@Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    @Select({
            "<script>",
            "select date_format(h.oper_time, '%Y-%m-%d') as periodLabel,",
            "       coalesce(sum(h.total_price), 0) as amount,",
            "       count(1) as recordCount",
            "from jsh_depot_head h",
            "where h.delete_flag = '0'",
            "  and h.tenant_id = #{tenantId}",
            "  and h.sub_type in",
            "  <foreach collection='subTypes' item='subType' open='(' separator=',' close=')'>",
            "    #{subType}",
            "  </foreach>",
            "  and (#{startTime} is null or h.oper_time &gt;= #{startTime})",
            "  and (#{endTime} is null or h.oper_time &lt;= #{endTime})",
            "group by date_format(h.oper_time, '%Y-%m-%d')",
            "order by min(h.oper_time)",
            "</script>"
    })
    List<TrendSeriesPoint> getBusinessTrendSeriesByDay(@Param("subTypes") List<String> subTypes,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       @Param("tenantId") Long tenantId);

    @Select({
            "<script>",
            "select date_format(date_sub(h.oper_time, interval weekday(h.oper_time) day), '%Y-%m-%d') as periodLabel,",
            "       coalesce(sum(h.total_price), 0) as amount,",
            "       count(1) as recordCount",
            "from jsh_depot_head h",
            "where h.delete_flag = '0'",
            "  and h.tenant_id = #{tenantId}",
            "  and h.sub_type in",
            "  <foreach collection='subTypes' item='subType' open='(' separator=',' close=')'>",
            "    #{subType}",
            "  </foreach>",
            "  and (#{startTime} is null or h.oper_time &gt;= #{startTime})",
            "  and (#{endTime} is null or h.oper_time &lt;= #{endTime})",
            "group by date_format(date_sub(h.oper_time, interval weekday(h.oper_time) day), '%Y-%m-%d')",
            "order by min(h.oper_time)",
            "</script>"
    })
    List<TrendSeriesPoint> getBusinessTrendSeriesByWeek(@Param("subTypes") List<String> subTypes,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("tenantId") Long tenantId);

    @Select({
            "<script>",
            "select date_format(h.oper_time, '%Y-%m') as periodLabel,",
            "       coalesce(sum(h.total_price), 0) as amount,",
            "       count(1) as recordCount",
            "from jsh_depot_head h",
            "where h.delete_flag = '0'",
            "  and h.tenant_id = #{tenantId}",
            "  and h.sub_type in",
            "  <foreach collection='subTypes' item='subType' open='(' separator=',' close=')'>",
            "    #{subType}",
            "  </foreach>",
            "  and (#{startTime} is null or h.oper_time &gt;= #{startTime})",
            "  and (#{endTime} is null or h.oper_time &lt;= #{endTime})",
            "group by date_format(h.oper_time, '%Y-%m')",
            "order by min(h.oper_time)",
            "</script>"
    })
    List<TrendSeriesPoint> getBusinessTrendSeriesByMonth(@Param("subTypes") List<String> subTypes,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime,
                                                         @Param("tenantId") Long tenantId);

    @Select({
            "<script>",
            "select date_format(h.bill_time, '%Y-%m-%d') as periodLabel,",
            "       coalesce(sum(h.change_amount), 0) as amount,",
            "       count(1) as recordCount",
            "from jsh_account_head h",
            "where h.delete_flag = '0'",
            "  and h.tenant_id = #{tenantId}",
            "  and h.type in",
            "  <foreach collection='types' item='type' open='(' separator=',' close=')'>",
            "    #{type}",
            "  </foreach>",
            "  and (#{startTime} is null or h.bill_time &gt;= #{startTime})",
            "  and (#{endTime} is null or h.bill_time &lt;= #{endTime})",
            "group by date_format(h.bill_time, '%Y-%m-%d')",
            "order by min(h.bill_time)",
            "</script>"
    })
    List<TrendSeriesPoint> getFinanceTrendSeriesByDay(@Param("types") List<String> types,
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime,
                                                      @Param("tenantId") Long tenantId);

    @Select({
            "<script>",
            "select date_format(date_sub(h.bill_time, interval weekday(h.bill_time) day), '%Y-%m-%d') as periodLabel,",
            "       coalesce(sum(h.change_amount), 0) as amount,",
            "       count(1) as recordCount",
            "from jsh_account_head h",
            "where h.delete_flag = '0'",
            "  and h.tenant_id = #{tenantId}",
            "  and h.type in",
            "  <foreach collection='types' item='type' open='(' separator=',' close=')'>",
            "    #{type}",
            "  </foreach>",
            "  and (#{startTime} is null or h.bill_time &gt;= #{startTime})",
            "  and (#{endTime} is null or h.bill_time &lt;= #{endTime})",
            "group by date_format(date_sub(h.bill_time, interval weekday(h.bill_time) day), '%Y-%m-%d')",
            "order by min(h.bill_time)",
            "</script>"
    })
    List<TrendSeriesPoint> getFinanceTrendSeriesByWeek(@Param("types") List<String> types,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       @Param("tenantId") Long tenantId);

    @Select({
            "<script>",
            "select date_format(h.bill_time, '%Y-%m') as periodLabel,",
            "       coalesce(sum(h.change_amount), 0) as amount,",
            "       count(1) as recordCount",
            "from jsh_account_head h",
            "where h.delete_flag = '0'",
            "  and h.tenant_id = #{tenantId}",
            "  and h.type in",
            "  <foreach collection='types' item='type' open='(' separator=',' close=')'>",
            "    #{type}",
            "  </foreach>",
            "  and (#{startTime} is null or h.bill_time &gt;= #{startTime})",
            "  and (#{endTime} is null or h.bill_time &lt;= #{endTime})",
            "group by date_format(h.bill_time, '%Y-%m')",
            "order by min(h.bill_time)",
            "</script>"
    })
    List<TrendSeriesPoint> getFinanceTrendSeriesByMonth(@Param("types") List<String> types,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("tenantId") Long tenantId);

    @Select("""
            select date_format(h.oper_time, '%Y-%m-%d') as periodLabel,
                   coalesce(sum(i.oper_number), 0) as amount,
                   count(1) as recordCount
            from jsh_depot_item i
            join jsh_depot_head h on i.header_id = h.id
            where i.delete_flag = '0'
              and h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and i.material_id = #{materialId}
              and h.sub_type in ('销售', '零售')
              and (#{startTime} is null or h.oper_time >= #{startTime})
              and (#{endTime} is null or h.oper_time <= #{endTime})
            group by date_format(h.oper_time, '%Y-%m-%d')
            order by min(h.oper_time)
            """)
    List<TrendSeriesPoint> getMaterialDemandSeriesByDay(@Param("materialId") Long materialId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("tenantId") Long tenantId);

    @Select("""
            select date_format(date_sub(h.oper_time, interval weekday(h.oper_time) day), '%Y-%m-%d') as periodLabel,
                   coalesce(sum(i.oper_number), 0) as amount,
                   count(1) as recordCount
            from jsh_depot_item i
            join jsh_depot_head h on i.header_id = h.id
            where i.delete_flag = '0'
              and h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and i.material_id = #{materialId}
              and h.sub_type in ('销售', '零售')
              and (#{startTime} is null or h.oper_time >= #{startTime})
              and (#{endTime} is null or h.oper_time <= #{endTime})
            group by date_format(date_sub(h.oper_time, interval weekday(h.oper_time) day), '%Y-%m-%d')
            order by min(h.oper_time)
            """)
    List<TrendSeriesPoint> getMaterialDemandSeriesByWeek(@Param("materialId") Long materialId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime,
                                                         @Param("tenantId") Long tenantId);

    @Select("""
            select date_format(h.oper_time, '%Y-%m') as periodLabel,
                   coalesce(sum(i.oper_number), 0) as amount,
                   count(1) as recordCount
            from jsh_depot_item i
            join jsh_depot_head h on i.header_id = h.id
            where i.delete_flag = '0'
              and h.delete_flag = '0'
              and h.tenant_id = #{tenantId}
              and i.material_id = #{materialId}
              and h.sub_type in ('销售', '零售')
              and (#{startTime} is null or h.oper_time >= #{startTime})
              and (#{endTime} is null or h.oper_time <= #{endTime})
            group by date_format(h.oper_time, '%Y-%m')
            order by min(h.oper_time)
            """)
    List<TrendSeriesPoint> getMaterialDemandSeriesByMonth(@Param("materialId") Long materialId,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime,
                                                          @Param("tenantId") Long tenantId);
}
