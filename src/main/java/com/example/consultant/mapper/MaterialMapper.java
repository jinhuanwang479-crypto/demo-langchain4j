package com.example.consultant.mapper;

import com.example.consultant.pojo.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MaterialMapper {

    @Select("""
            select id, name, category_level as categoryLevel, parent_id as parentId, sort, serial_no as serialNo, remark
            from jsh_material_category
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or serial_no like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<MaterialCategory> listMaterialCategories(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select count(1)
            from jsh_material_category
            where id = #{id}
              and tenant_id = #{tenantId}
              and delete_flag = '0'
            """)
    Integer countMaterialCategoryById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            select id, attribute_name as attributeName, attribute_value as attributeValue
            from jsh_material_attribute
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or attribute_name like concat('%', #{keyword}, '%')
                   or attribute_value like concat('%', #{keyword}, '%'))
            order by id
            """)
    List<MaterialAttribute> listMaterialAttributes(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, native_name as nativeName, another_name as anotherName, enabled, sort
            from jsh_material_property
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or native_name like concat('%', #{keyword}, '%')
                   or another_name like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<MaterialProperty> listMaterialProperties(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select m.id, m.name, m.category_id as categoryId, c.name as categoryName, m.model, m.standard,
                   m.brand, m.mnemonic, m.unit, m.unit_id as unitId, m.remark, m.enabled,
                   me.bar_code as barCode, me.purchase_decimal as purchasePrice,
                   me.commodity_decimal as retailPrice, me.wholesale_decimal as salePrice
            from jsh_material m
            left join jsh_material_category c on m.category_id = c.id and c.delete_flag = '0'
            left join jsh_material_extend me on m.id = me.material_id and me.default_flag = '1' and me.delete_Flag = '0'
            where m.delete_flag = '0'
              and m.tenant_id = #{tenantId}
              and (#{categoryId} is null or m.category_id = #{categoryId})
              and (#{keyword} is null or m.name like concat('%', #{keyword}, '%')
                   or m.mnemonic like concat('%', #{keyword}, '%')
                   or m.model like concat('%', #{keyword}, '%')
                   or me.bar_code like concat('%', #{keyword}, '%'))
            order by m.id desc
            limit #{limit}
            """)
    List<MaterialInfo> searchMaterials(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
                                       @Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    @Select("""
            select m.id, m.name, m.category_id as categoryId, c.name as categoryName, m.mfrs, m.model, m.standard,
                   m.brand, m.mnemonic, m.color, m.unit, m.remark, m.unit_id as unitId, m.expiry_num as expiryNum,
                   m.weight, m.enabled, m.enable_serial_number as enableSerialNumber,
                   m.enable_batch_number as enableBatchNumber, m.position, m.attribute
            from jsh_material m
            left join jsh_material_category c on m.category_id = c.id and c.delete_flag = '0'
            where m.id = #{materialId} and m.tenant_id = #{tenantId} and m.delete_flag = '0'
            """)
    MaterialInfo getMaterialById(@Param("materialId") Long materialId, @Param("tenantId") Long tenantId);

    @Select("""
            select id, bar_code as barCode, commodity_unit as commodityUnit, sku,
                   purchase_decimal as purchasePrice, commodity_decimal as retailPrice,
                   wholesale_decimal as salePrice, low_decimal as lowPrice, default_flag as defaultFlag
            from jsh_material_extend
            where material_id = #{materialId} and tenant_id = #{tenantId} and delete_Flag = '0'
            order by default_flag desc, id
            """)
    List<MaterialPrice> listMaterialPrices(@Param("materialId") Long materialId, @Param("tenantId") Long tenantId);

    @Select("""
            select s.material_id as materialId, m.name as materialName, m.model, m.standard, m.unit,
                   s.depot_id as depotId, d.name as depotName, s.current_number as currentNumber,
                   s.current_unit_price as currentUnitPrice, i.low_safe_stock as lowSafeStock, i.high_safe_stock as highSafeStock
            from jsh_material_current_stock s
            join jsh_material m on s.material_id = m.id and m.delete_flag = '0'
            left join jsh_depot d on s.depot_id = d.id and d.delete_Flag = '0'
            left join jsh_material_initial_stock i on i.material_id = s.material_id and i.depot_id = s.depot_id
                 and i.tenant_id = s.tenant_id and i.delete_flag = '0'
            where s.material_id = #{materialId} and s.tenant_id = #{tenantId} and s.delete_flag = '0'
            order by d.sort, s.id
            """)
    List<MaterialStockResult> listMaterialStocksByMaterial(@Param("materialId") Long materialId, @Param("tenantId") Long tenantId);

    @Select("""
            select s.material_id as materialId, m.name as materialName, m.model, m.standard, m.unit,
                   s.depot_id as depotId, d.name as depotName, s.current_number as currentNumber,
                   s.current_unit_price as currentUnitPrice, i.low_safe_stock as lowSafeStock, i.high_safe_stock as highSafeStock
            from jsh_material_current_stock s
            join jsh_material m on s.material_id = m.id and m.delete_flag = '0'
            left join jsh_depot d on s.depot_id = d.id and d.delete_Flag = '0'
            left join jsh_material_initial_stock i on i.material_id = s.material_id and i.depot_id = s.depot_id
                 and i.tenant_id = s.tenant_id and i.delete_flag = '0'
            where s.delete_flag = '0'
              and s.tenant_id = #{tenantId}
              and (#{depotId} is null or s.depot_id = #{depotId})
              and (#{keyword} is null or m.name like concat('%', #{keyword}, '%')
                   or m.mnemonic like concat('%', #{keyword}, '%')
                   or d.name like concat('%', #{keyword}, '%'))
            order by d.sort, m.id
            limit #{limit}
            """)
    List<MaterialStockResult> listMaterialStocks(@Param("keyword") String keyword, @Param("depotId") Long depotId,
                                                 @Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    @Insert("""
            insert into jsh_material(category_id, name, model, standard, brand, mnemonic, unit, remark,
                                     unit_id, enabled, enable_serial_number, enable_batch_number, tenant_id, delete_flag)
            values(#{categoryId}, #{name}, #{model}, #{standard}, #{brand}, #{mnemonic}, #{unit}, #{remark},
                   #{unitId}, #{enabled}, '0', '0', #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMaterial(MaterialInsertParam param);

    @Insert("""
            insert into jsh_material_category(name, category_level, parent_id, sort, serial_no, remark,
                                              create_time, update_time, tenant_id, delete_flag)
            values(#{name}, #{categoryLevel}, #{parentId}, #{sort}, #{serialNo}, #{remark},
                   #{createTime}, #{updateTime}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(MaterialCategoryInsertParam param);

    @Insert("""
            insert into jsh_material_attribute(attribute_name, attribute_value, tenant_id, delete_flag)
            values(#{attributeName}, #{attributeValue}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAttribute(MaterialAttributeInsertParam param);

    @Insert("""
            insert into jsh_material_property(native_name, enabled, sort, another_name, tenant_id, delete_flag)
            values(#{nativeName}, #{enabled}, #{sort}, #{anotherName}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProperty(MaterialPropertyInsertParam param);

    @Select("""
            select id
            from jsh_material_extend
            where material_id = #{materialId} and tenant_id = #{tenantId} and default_flag = '1' and delete_Flag = '0'
            limit 1
            """)
    Long findDefaultMaterialPriceId(@Param("materialId") Long materialId, @Param("tenantId") Long tenantId);

    @Insert("""
            insert into jsh_material_extend(material_id, bar_code, commodity_unit, sku, purchase_decimal,
                                            commodity_decimal, wholesale_decimal, low_decimal, default_flag,
                                            create_time, tenant_id, delete_Flag)
            values(#{materialId}, #{barCode}, #{commodityUnit}, '', #{purchasePrice},
                   #{retailPrice}, #{salePrice}, #{lowPrice}, '1', #{createTime}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMaterialPrice(MaterialPriceInsertParam param);

    @Update("""
            update jsh_material_extend
            set bar_code = #{barCode},
                commodity_unit = #{commodityUnit},
                purchase_decimal = #{purchasePrice},
                commodity_decimal = #{retailPrice},
                wholesale_decimal = #{salePrice},
                low_decimal = #{lowPrice},
                update_time = #{updateTime}
            where id = #{id} and tenant_id = #{tenantId} and delete_Flag = '0'
            """)
    int updateMaterialPrice(MaterialPriceUpdateParam param);

    @Select("""
            select id
            from jsh_material_current_stock
            where material_id = #{materialId} and depot_id = #{depotId} and tenant_id = #{tenantId} and delete_flag = '0'
            limit 1
            """)
    Long findCurrentStockId(@Param("materialId") Long materialId, @Param("depotId") Long depotId, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_material_current_stock
            set current_number = coalesce(current_number, 0) + #{delta},
                current_unit_price = coalesce(#{unitPrice}, current_unit_price)
            where id = #{id}
            """)
    int updateCurrentStock(@Param("id") Long id, @Param("delta") java.math.BigDecimal delta,
                           @Param("unitPrice") java.math.BigDecimal unitPrice);

    @Insert("""
            insert into jsh_material_current_stock(material_id, depot_id, current_number, current_unit_price, tenant_id, delete_flag)
            values(#{materialId}, #{depotId}, #{currentNumber}, #{currentUnitPrice}, #{tenantId}, '0')
            """)
    int insertCurrentStock(MaterialCurrentStockInsertParam param);
}
