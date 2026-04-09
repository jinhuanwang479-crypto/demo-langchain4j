package com.example.consultant.mapper;

import com.example.consultant.pojo.AiToolDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiToolPermissionMapper {

    @Select("""
            select id,
                   tool_code as toolCode,
                   tool_name as toolName,
                   bean_name as beanName,
                   method_name as methodName,
                   function_ids as functionIds,
                   registered_in_ai as registeredInAi,
                   enabled
            from jsh_ai_tool
            where tool_code = #{toolCode}
              and delete_flag = '0'
            limit 1
            """)
    AiToolDefinition findByToolCode(@Param("toolCode") String toolCode);
}
