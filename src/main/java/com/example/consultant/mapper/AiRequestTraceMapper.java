package com.example.consultant.mapper;

import com.example.consultant.pojo.AiRequestTrace;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiRequestTraceMapper {

    @Insert("INSERT INTO ai_request_trace(" +
            "request_id, memory_id, tenant_id, user_id, question, response, status, error_message, " +
            "model_name, finish_reason, latency_ms, first_token_latency_ms, streamed_chars, input_tokens, " +
            "output_tokens, total_tokens, retrieved_count, top_retrieval_score, retrieval_rejected_reason, " +
            "tool_call_count, evaluation_score, evaluation_status, risk_level, evaluation_reasons_json, " +
            "retrieval_snapshot_json, started_at, completed_at, create_time, update_time" +
            ") VALUES (" +
            "#{requestId}, #{memoryId}, #{tenantId}, #{userId}, #{question}, #{response}, #{status}, #{errorMessage}, " +
            "#{modelName}, #{finishReason}, #{latencyMs}, #{firstTokenLatencyMs}, #{streamedChars}, #{inputTokens}, " +
            "#{outputTokens}, #{totalTokens}, #{retrievedCount}, #{topRetrievalScore}, #{retrievalRejectedReason}, " +
            "#{toolCallCount}, #{evaluationScore}, #{evaluationStatus}, #{riskLevel}, #{evaluationReasonsJson}, " +
            "#{retrievalSnapshotJson}, #{startedAt}, #{completedAt}, NOW(), NOW()" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiRequestTrace trace);

    @Select("SELECT * FROM ai_request_trace WHERE request_id = #{requestId}")
    AiRequestTrace findByRequestId(@Param("requestId") String requestId);

    @Select("<script>" +
            "SELECT * FROM ai_request_trace WHERE 1=1 " +
            "<if test='startTime != null'> AND started_at <![CDATA[>=]]> #{startTime}</if>" +
            "<if test='endTime != null'> AND started_at <![CDATA[<=]]> #{endTime}</if>" +
            "<if test='memoryId != null and memoryId != \"\"'> AND memory_id = #{memoryId}</if>" +
            "<if test='userId != null and userId != \"\"'> AND user_id = #{userId}</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='riskLevel != null and riskLevel != \"\"'> AND risk_level = #{riskLevel}</if>" +
            " ORDER BY started_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<AiRequestTrace> findPage(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("memoryId") String memoryId,
                                  @Param("userId") String userId,
                                  @Param("status") String status,
                                  @Param("riskLevel") String riskLevel,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM ai_request_trace WHERE 1=1 " +
            "<if test='startTime != null'> AND started_at <![CDATA[>=]]> #{startTime}</if>" +
            "<if test='endTime != null'> AND started_at <![CDATA[<=]]> #{endTime}</if>" +
            "<if test='memoryId != null and memoryId != \"\"'> AND memory_id = #{memoryId}</if>" +
            "<if test='userId != null and userId != \"\"'> AND user_id = #{userId}</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='riskLevel != null and riskLevel != \"\"'> AND risk_level = #{riskLevel}</if>" +
            "</script>")
    long countPage(@Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("memoryId") String memoryId,
                   @Param("userId") String userId,
                   @Param("status") String status,
                   @Param("riskLevel") String riskLevel);

    @Select("<script>" +
            "SELECT * FROM ai_request_trace WHERE 1=1 " +
            "<if test='startTime != null'> AND started_at <![CDATA[>=]]> #{startTime}</if>" +
            "<if test='endTime != null'> AND started_at <![CDATA[<=]]> #{endTime}</if>" +
            " ORDER BY started_at ASC" +
            "</script>")
    List<AiRequestTrace> findForSummary(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);
}
