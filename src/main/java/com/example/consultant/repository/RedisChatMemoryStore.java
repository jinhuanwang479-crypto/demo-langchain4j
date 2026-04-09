package com.example.consultant.repository;

import com.example.consultant.config.AiMemoryProperties;
import com.example.consultant.utils.UserContextUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);

    private final StringRedisTemplate redisTemplate;
    private final AiMemoryProperties aiMemoryProperties;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate, AiMemoryProperties aiMemoryProperties) {
        this.redisTemplate = redisTemplate;
        this.aiMemoryProperties = aiMemoryProperties;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = redisTemplate.opsForValue().get(buildKey(memoryId));
            if (!StringUtils.hasText(json)) {
                return List.of();
            }
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (RuntimeException ex) {
            log.warn("读取 Redis 会话记忆失败，返回空上下文: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        try {
            String json = ChatMessageSerializer.messagesToJson(list);
            redisTemplate.opsForValue().set(buildKey(memoryId), json, aiMemoryProperties.getTtlSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            log.warn("写入 Redis 会话记忆失败，本次对话不会持久化: {}", ex.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            redisTemplate.delete(buildKey(memoryId));
        } catch (RuntimeException ex) {
            log.warn("删除 Redis 会话记忆失败: {}", ex.getMessage());
        }
    }

    String buildKey(Object memoryId) {
        String resolvedMemoryId = String.valueOf(memoryId);
        Long tenantId = UserContextUtil.getTenantId();
        String userId = UserContextUtil.getUserId();
        String tenantPart = tenantId == null ? "default" : String.valueOf(tenantId);
        String userPart = StringUtils.hasText(userId) ? userId.trim() : "anonymous";
        String key = aiMemoryProperties.getKeyPrefix()
                + ":tenant:" + tenantPart
                + ":user:" + userPart
                + ":memory:" + resolvedMemoryId;
        log.debug("构建会话记忆Key: {}", key);
        return key;
    }
}
