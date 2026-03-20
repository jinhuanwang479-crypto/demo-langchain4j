package com.example.consultant.repository;

import com.example.consultant.config.AiMemoryProperties;
import com.example.consultant.config.TenantContextHolder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisChatMemoryStore redisChatMemoryStore;

    @BeforeEach
    void setUp() {
        AiMemoryProperties properties = new AiMemoryProperties();
        properties.setKeyPrefix("erp:chat-memory");
        properties.setTtlSeconds(3600);
        redisChatMemoryStore = new RedisChatMemoryStore(redisTemplate, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void updateMessagesShouldUseTenantScopedRedisKey() {
        bindRequest("160", "501");
        TenantContextHolder.setTenantId(160L);
        List<ChatMessage> messages = List.of(UserMessage.from("你好"));

        redisChatMemoryStore.updateMessages("memory-1", messages);

        verify(valueOperations).set(
                "erp:chat-memory:tenant:160:user:501:memory:memory-1",
                dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson(messages),
                3600,
                TimeUnit.SECONDS
        );
    }

    @Test
    void getMessagesShouldReturnEmptyWhenRedisValueMissing() {
        bindRequest("160", "501");
        TenantContextHolder.setTenantId(160L);
        when(valueOperations.get("erp:chat-memory:tenant:160:user:501:memory:memory-2")).thenReturn(null);

        List<ChatMessage> messages = redisChatMemoryStore.getMessages("memory-2");

        assertTrue(messages.isEmpty());
    }

    @Test
    void buildKeyShouldChangeWithDifferentUsers() {
        bindRequest("160", "501");
        TenantContextHolder.setTenantId(160L);
        String firstKey = redisChatMemoryStore.buildKey("memory-3");

        bindRequest("160", "777");
        String secondKey = redisChatMemoryStore.buildKey("memory-3");

        assertEquals("erp:chat-memory:tenant:160:user:501:memory:memory-3", firstKey);
        assertEquals("erp:chat-memory:tenant:160:user:777:memory:memory-3", secondKey);
    }

    private void bindRequest(String tenantId, String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContextHolder.setTenantId(Long.parseLong(tenantId));
    }
}
