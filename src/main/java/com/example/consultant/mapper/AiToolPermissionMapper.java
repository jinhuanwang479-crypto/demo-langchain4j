package com.example.consultant.mapper;

import com.example.consultant.pojo.AiToolDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiToolPermissionMapper {

    @Select("""
            select id,
                   tenant_id as tenantId,
                   tool_code as toolCode,
                   tool_name as toolName,
                   bean_name as beanName,
                   method_name as methodName,
                   function_ids as functionIds,
                   registered_in_ai as registeredInAi,
                   enabled,
                   sort,
                   remark
            from jsh_ai_tool
            where tool_code = #{toolCode}
              and delete_flag = '0'
            limit 1
            """)
    AiToolDefinition findByToolCode(@Param("toolCode") String toolCode);

    @Select("""
            select id,
                   tenant_id as tenantId,
                   tool_code as toolCode,
                   tool_name as toolName,
                   bean_name as beanName,
                   method_name as methodName,
                   function_ids as functionIds,
                   registered_in_ai as registeredInAi,
                   enabled,
                   sort,
                   remark
            from jsh_ai_tool
            where tenant_id = #{tenantId}
              and registered_in_ai = 1
              and enabled = 1
              and delete_flag = '0'
            order by sort asc, id asc
            """)
    List<AiToolDefinition> findAllRegisteredEnabledByTenantId(@Param("tenantId") Long tenantId);
}
