package com.example.consultant.mapper;

import com.example.consultant.pojo.AiMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiMessageMapper {

    /**
     * 添加消息
     */
    @Insert("INSERT INTO ai_message(memory_id, role, content, create_time) " +
            "VALUES(#{memoryId}, #{role}, #{content}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiMessage message);

    /**
     * 批量添加消息
     */
    @Insert("<script>" +
            "INSERT INTO ai_message(memory_id, role, content, create_time) VALUES " +
            "<foreach item='msg' collection='list' separator=','>" +
            "(#{msg.memoryId}, #{msg.role}, #{msg.content}, NOW())" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<AiMessage> messages);

    /**
     * 根据 memoryId 查询所有消息（按时间正序）
     */
    @Select("SELECT * FROM ai_message WHERE memory_id=#{memoryId} ORDER BY create_time ASC")
    List<AiMessage> findByMemoryId(String memoryId);

    /**
     * 根据 memoryId 删除消息
     */
    @Delete("DELETE FROM ai_message WHERE memory_id=#{memoryId}")
    void deleteByMemoryId(String memoryId);

    /**
     * 根据用户ID删除消息（通过会话关联）
     */
    @Delete("DELETE FROM ai_message WHERE memory_id IN " +
            "(SELECT memory_id FROM ai_conversation WHERE user_id=#{userId})")
    void deleteByUserId(@Param("userId") String userId);

    /**
     * 删除所有消息
     */
    @Delete("DELETE FROM ai_message")
    void deleteAll();

    /**
     * 统计某个会话的消息数量
     */
    @Select("SELECT COUNT(*) FROM ai_message WHERE memory_id=#{memoryId}")
    int countByMemoryId(String memoryId);
}
