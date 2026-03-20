package com.example.consultant.mapper;

import com.example.consultant.pojo.AiToolTrace;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiToolTraceMapper {

    @Insert("<script>" +
            "INSERT INTO ai_tool_trace(request_id, sequence_no, tool_name, arguments_json, result_preview, success, error_message, created_at) VALUES " +
            "<foreach item='trace' collection='list' separator=','>" +
            "(#{trace.requestId}, #{trace.sequenceNo}, #{trace.toolName}, #{trace.argumentsJson}, #{trace.resultPreview}, #{trace.success}, #{trace.errorMessage}, #{trace.createdAt})" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<AiToolTrace> traces);

    @Select("SELECT * FROM ai_tool_trace WHERE request_id = #{requestId} ORDER BY sequence_no ASC")
    List<AiToolTrace> findByRequestId(@Param("requestId") String requestId);
}
