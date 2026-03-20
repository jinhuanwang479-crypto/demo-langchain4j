package com.example.consultant.mapper;

import com.example.consultant.pojo.AiConversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiConversationMapper {

    @Insert("INSERT INTO ai_conversation(memory_id, user_id, title, message_count, create_time, update_time) " +
            "VALUES(#{memoryId}, #{userId}, #{title}, #{messageCount}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiConversation conversation);

    @Update("UPDATE ai_conversation SET message_count=#{messageCount}, update_time=NOW() WHERE memory_id=#{memoryId}")
    void update(AiConversation conversation);

    @Select("SELECT * FROM ai_conversation WHERE memory_id=#{memoryId}")
    AiConversation findByMemoryId(String memoryId);

    @Select("SELECT * FROM ai_conversation WHERE memory_id=#{memoryId} AND user_id=#{userId}")
    AiConversation findByMemoryIdAndUserId(@Param("memoryId") String memoryId, @Param("userId") String userId);

    @Select("SELECT * FROM ai_conversation WHERE user_id=#{userId} ORDER BY update_time DESC")
    List<AiConversation> findAllByUserId(String userId);

    @Delete("DELETE FROM ai_conversation WHERE memory_id=#{memoryId}")
    void deleteByMemoryId(String memoryId);

    @Delete("DELETE FROM ai_conversation WHERE memory_id=#{memoryId} AND user_id=#{userId}")
    int deleteByMemoryIdAndUserId(@Param("memoryId") String memoryId, @Param("userId") String userId);

    @Delete("DELETE FROM ai_conversation WHERE user_id=#{userId}")
    void deleteAllByUserId(String userId);
}
