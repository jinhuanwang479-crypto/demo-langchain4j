package com.example.consultant.mapper;

import com.example.consultant.pojo.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemManagementMapper {

    @Select("""
            select id, number, name, parent_number as parentNumber, url, component, type, push_btn as pushBtn, icon
            from jsh_function
            where delete_flag = '0'
              and (#{keyword} is null or name like concat('%', #{keyword}, '%') or url like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<ErpFunction> listFunctions(@Param("keyword") String keyword);

    @Select("""
            select u.id, u.username, u.login_name as loginName, u.phonenum, u.email, u.position, u.department,
                   u.status, u.description, u.remark
            from jsh_user u
            where u.delete_flag = '0'
              and u.tenant_id = #{tenantId}
              and (#{keyword} is null or u.username like concat('%', #{keyword}, '%')
                   or u.login_name like concat('%', #{keyword}, '%')
                   or u.phonenum like concat('%', #{keyword}, '%'))
            order by u.id desc
            limit #{limit}
            """)
    List<ErpUser> searchUsers(@Param("keyword") String keyword, @Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    @Select("""
            select count(1)
            from jsh_user
            where delete_flag = '0' and tenant_id = #{tenantId} and login_name = #{loginName}
            """)
    Integer countUserByLoginName(@Param("loginName") String loginName, @Param("tenantId") Long tenantId);

    @Insert("""
            insert into jsh_user(username, login_name, password, leader_flag, position, department, email, phonenum,
                                 ismanager, isystem, status, tenant_id, delete_flag)
            values(#{username}, #{loginName}, #{password}, '0', #{position}, #{department}, #{email}, #{phonenum},
                   1, 0, 0, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(ErpUserInsertParam param);

    @Insert("""
            insert into jsh_user_business(type, key_id, value, btn_str, tenant_id, delete_flag)
            values('UserRole', #{keyId}, #{value}, null, #{tenantId}, '0')
            """)
    int insertUserRole(@Param("keyId") String keyId, @Param("value") String value, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_user
            set status = #{status}
            where id = #{userId} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateUserStatus(@Param("userId") Long userId, @Param("status") Integer status, @Param("tenantId") Long tenantId);

    @Select("""
            select id, name, type, price_limit as priceLimit, description, sort
            from jsh_role
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or type like concat('%', #{keyword}, '%')
                   or description like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<ErpRole> searchRoles(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, org_no as orgNo, org_abr as orgAbr, parent_id as parentId, sort, remark, create_time as createTime
            from jsh_organization
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or org_no like concat('%', #{keyword}, '%')
                   or org_abr like concat('%', #{keyword}, '%'))
            order by id
            """)
    List<ErpOrganization> searchOrganizations(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, type, name, enabled, sort
            from jsh_person
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or type like concat('%', #{keyword}, '%'))
            order by id desc
            """)
    List<ErpPerson> searchHandlers(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, name, serial_no as serialNo, initial_amount as initialAmount, current_amount as currentAmount,
                   remark, enabled, is_default as isDefault
            from jsh_account
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or serial_no like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<ErpAccount> searchAccounts(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, name, address, warehousing, truckage, type, sort, remark, principal,
                   enabled, is_default as isDefault
            from jsh_depot
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or address like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<ErpDepot> searchDepots(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, supplier, contacts, phone_num as phoneNum, telephone, email, type, address,
                   advance_in as advanceIn, begin_need_get as beginNeedGet, begin_need_pay as beginNeedPay,
                   all_need_get as allNeedGet, all_need_pay as allNeedPay, tax_rate as taxRate, enabled
            from jsh_supplier
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{type} is null or type = #{type})
              and (#{keyword} is null or supplier like concat('%', #{keyword}, '%')
                   or contacts like concat('%', #{keyword}, '%')
                   or phone_num like concat('%', #{keyword}, '%')
                   or telephone like concat('%', #{keyword}, '%'))
            order by sort, id
            limit #{limit}
            """)
    List<ErpPartner> searchPartners(@Param("keyword") String keyword, @Param("type") String type,
                                    @Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    @Select("""
            select id, name, basic_unit as basicUnit, other_unit as otherUnit, other_unit_two as otherUnitTwo,
                   other_unit_three as otherUnitThree, ratio, ratio_two as ratioTwo, ratio_three as ratioThree, enabled
            from jsh_unit
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or basic_unit like concat('%', #{keyword}, '%')
                   or other_unit like concat('%', #{keyword}, '%'))
            order by id
            """)
    List<ErpUnit> searchUnits(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    @Select("""
            select id, name, basic_unit as basicUnit, other_unit as otherUnit, other_unit_two as otherUnitTwo,
                   other_unit_three as otherUnitThree, ratio, ratio_two as ratioTwo, ratio_three as ratioThree, enabled
            from jsh_unit
            where id = #{id}
              and tenant_id = #{tenantId}
              and delete_flag = '0'
            limit 1
            """)
    ErpUnit getUnitById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            select id, name, type, remark, enabled, sort
            from jsh_in_out_item
            where delete_flag = '0'
              and tenant_id = #{tenantId}
              and (#{type} is null or type = #{type})
              and (#{keyword} is null or name like concat('%', #{keyword}, '%')
                   or remark like concat('%', #{keyword}, '%'))
            order by sort, id
            """)
    List<ErpInOutItem> searchInOutItems(@Param("keyword") String keyword, @Param("type") String type,
                                        @Param("tenantId") Long tenantId);

    @Select("""
            select company_name as companyName, company_contacts as companyContacts, company_address as companyAddress,
                   company_tel as companyTel, company_fax as companyFax, company_post_code as companyPostCode,
                   sale_agreement as saleAgreement, depot_flag as depotFlag, customer_flag as customerFlag,
                   minus_stock_flag as minusStockFlag, purchase_by_sale_flag as purchaseBySaleFlag,
                   multi_level_approval_flag as multiLevelApprovalFlag, force_approval_flag as forceApprovalFlag,
                   update_unit_price_flag as updateUnitPriceFlag, over_link_bill_flag as overLinkBillFlag,
                   in_out_manage_flag as inOutManageFlag, multi_account_flag as multiAccountFlag,
                   move_avg_price_flag as moveAvgPriceFlag, audit_print_flag as auditPrintFlag,
                   zero_change_amount_flag as zeroChangeAmountFlag, customer_static_price_flag as customerStaticPriceFlag
            from jsh_system_config
            where delete_flag = '0' and tenant_id = #{tenantId}
            order by id desc
            limit 1
            """)
    ErpSystemConfig getSystemConfig(@Param("tenantId") Long tenantId);

    @Select("""
            select id, platform_key as platformKey, platform_key_info as platformKeyInfo, platform_value as platformValue
            from jsh_platform_config
            order by id
            """)
    List<ErpPlatformConfig> listPlatformConfig();

    @Select("""
            select id, msg_title as msgTitle, msg_content as msgContent, create_time as createTime, type, user_id as userId, status
            from jsh_msg
            where delete_Flag = '0'
              and tenant_id = #{tenantId}
              and (#{userId} is null or user_id = #{userId})
              and (#{status} is null or status = #{status})
            order by create_time desc, id desc
            """)
    List<ErpMessageNotice> listMessages(@Param("userId") Long userId, @Param("status") String status,
                                        @Param("tenantId") Long tenantId);

    @Insert("""
            insert into jsh_msg(msg_title, msg_content, create_time, type, user_id, status, tenant_id, delete_Flag)
            values(#{msgTitle}, #{msgContent}, #{createTime}, #{type}, #{userId}, #{status}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(ErpMessageNotice messageNotice);

    @Select("""
            select id, user_id as userId, operation, client_ip as clientIp, create_time as createTime, status, content
            from jsh_log
            where tenant_id = #{tenantId}
              and (#{userId} is null or user_id = #{userId})
              and (#{keyword} is null or operation like concat('%', #{keyword}, '%')
                   or content like concat('%', #{keyword}, '%'))
            order by create_time desc, id desc
            limit #{limit}
            """)
    List<ErpLogRecord> listLogs(@Param("keyword") String keyword, @Param("userId") Long userId,
                                @Param("tenantId") Long tenantId, @Param("limit") Integer limit);

    @Insert("""
            insert into jsh_role(name, type, price_limit, value, description, enabled, sort, tenant_id, delete_flag)
            values(#{name}, #{type}, #{priceLimit}, #{value}, #{description}, #{enabled}, #{sort}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(ErpRoleInsertParam param);

    @Insert("""
            insert into jsh_organization(org_no, org_abr, parent_id, sort, remark, create_time, update_time, tenant_id, delete_flag)
            values(#{orgNo}, #{orgAbr}, #{parentId}, #{sort}, #{remark}, #{createTime}, #{updateTime}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrganization(ErpOrganizationInsertParam param);

    @Insert("""
            insert into jsh_person(type, name, enabled, sort, tenant_id, delete_flag)
            values(#{type}, #{name}, #{enabled}, #{sort}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPerson(ErpPersonInsertParam param);

    @Insert("""
            insert into jsh_account(name, serial_no, initial_amount, current_amount, remark, enabled, sort, is_default, tenant_id, delete_flag)
            values(#{name}, #{serialNo}, #{initialAmount}, #{currentAmount}, #{remark}, #{enabled}, #{sort}, #{isDefault}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAccount(ErpAccountInsertParam param);

    @Insert("""
            insert into jsh_depot(name, address, warehousing, truckage, type, sort, remark, principal, enabled, tenant_id, delete_Flag, is_default)
            values(#{name}, #{address}, #{warehousing}, #{truckage}, #{type}, #{sort}, #{remark}, #{principal}, #{enabled}, #{tenantId}, '0', #{isDefault})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDepot(ErpDepotInsertParam param);

    @Insert("""
            insert into jsh_supplier(supplier, contacts, phone_num, email, description, isystem, type, enabled,
                                     advance_in, begin_need_get, begin_need_pay, all_need_get, all_need_pay,
                                     fax, telephone, address, tax_num, bank_name, account_number, tax_rate,
                                     sort, creator, tenant_id, delete_flag)
            values(#{supplier}, #{contacts}, #{phoneNum}, #{email}, #{description}, null, #{type}, #{enabled},
                   #{advanceIn}, #{beginNeedGet}, #{beginNeedPay}, #{allNeedGet}, #{allNeedPay},
                   #{fax}, #{telephone}, #{address}, #{taxNum}, #{bankName}, #{accountNumber}, #{taxRate},
                   #{sort}, #{creator}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPartner(ErpPartnerInsertParam param);

    @Insert("""
            insert into jsh_unit(name, basic_unit, other_unit, other_unit_two, other_unit_three,
                                 ratio, ratio_two, ratio_three, enabled, tenant_id, delete_flag)
            values(#{name}, #{basicUnit}, #{otherUnit}, #{otherUnitTwo}, #{otherUnitThree},
                   #{ratio}, #{ratioTwo}, #{ratioThree}, #{enabled}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUnit(ErpUnitInsertParam param);

    @Insert("""
            insert into jsh_in_out_item(name, type, remark, enabled, sort, tenant_id, delete_flag)
            values(#{name}, #{type}, #{remark}, #{enabled}, #{sort}, #{tenantId}, '0')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertInOutItem(ErpInOutItemInsertParam param);

    @Update("""
            update jsh_role set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateRoleEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_person set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updatePersonEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_account set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateAccountEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_depot set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_Flag = '0'
            """)
    int updateDepotEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_supplier set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updatePartnerEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_unit set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateUnitEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);

    @Update("""
            update jsh_in_out_item set enabled = #{enabled}
            where id = #{id} and tenant_id = #{tenantId} and delete_flag = '0'
            """)
    int updateInOutItemEnabled(@Param("id") Long id, @Param("enabled") Integer enabled, @Param("tenantId") Long tenantId);
}
