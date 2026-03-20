package com.example.consultant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
