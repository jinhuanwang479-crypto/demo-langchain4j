package com.example.consultant.mapper;

import com.example.consultant.pojo.ErpUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantUserMapper {

    /**
     * 根据用户 ID 查询其所属租户。
     * 仅允许查询未删除且状态正常的用户，避免禁用账号继续访问租户数据。
     */
    @Select("""
            select tenant_id
            from jsh_user
            where id = #{userId}
              and delete_flag = '0'
              and status = 0
            limit 1
            """)
    Long findTenantIdByUserId(@Param("userId") Long userId);

    @Select("""
            select u.id,
                   u.username,
                   u.login_name as loginName,
                   u.phonenum,
                   u.email,
                   u.position,
                   u.department,
                   u.status,
                   u.description,
                   u.remark,
                   u.tenant_id as tenantId,
                   ub.value as roleIdsRaw
            from jsh_user u
            left join jsh_user_business ub
                   on ub.type = 'UserRole'
                  and ub.key_id = cast(u.id as char)
                  and ub.tenant_id = u.tenant_id
                  and ub.delete_flag = '0'
            where u.id = #{userId}
              and u.delete_flag = '0'
              and u.status = 0
            group by u.id, u.username, u.login_name, u.phonenum, u.email, u.position, u.department,
                     u.status, u.description, u.remark, u.tenant_id, ub.value
            limit 1
            """)
    ErpUser findActiveUserWithRoles(@Param("userId") Long userId);

    @Select({
            "<script>",
            "select value",
            "from jsh_user_business",
            "where type = 'RoleTools'",
            "  and tenant_id = #{tenantId}",
            "  and delete_flag = '0'",
            "  and key_id in",
            "  <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>",
            "    #{roleId}",
            "  </foreach>",
            "</script>"
    })
    List<String> findRoleToolValuesByRoleIds(@Param("tenantId") Long tenantId, @Param("roleIds") List<Long> roleIds);
}
