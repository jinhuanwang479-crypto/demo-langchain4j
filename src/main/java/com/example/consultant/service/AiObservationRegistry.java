package com.example.consultant.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

@Component
/**
 * AI 请求观测上下文注册表。
 * 作用是把“当前正在进行中的对话请求上下文”暂存起来，
 * 便于检索器等下游组件在执行过程中把检索结果回填到正确的请求对象上。
 */
public class AiObservationRegistry {

    private final ConcurrentMap<String, Deque<AiChatObservationContext>> contexts = new ConcurrentHashMap<>();

    public void register(AiChatObservationContext context) {
        contexts.computeIfAbsent(buildKey(context.getMemoryId(), context.getTenantId(), context.getUserId()),
                key -> new ConcurrentLinkedDeque<>()).addLast(context);
    }

    /** 请求结束后移除上下文，避免内存残留。 */
    public void remove(AiChatObservationContext context) {
        contexts.computeIfPresent(buildKey(context.getMemoryId(), context.getTenantId(), context.getUserId()),
                (ignored, deque) -> {
                    deque.remove(context);
                    return deque.isEmpty() ? null : deque;
                });
    }

    public void recordRetrievalOutcome(String memoryId, Long tenantId, String userId,
                                       int retrievedCount, Double topScore, String rejectionReason) {
        // 检索器根据 memoryId/tenantId/userId 定位当前最近的一次请求上下文，并回填检索结论。
        AiChatObservationContext context = findLatest(memoryId, tenantId, userId);
        if (context != null) {
            context.recordRetrievalOutcome(retrievedCount, topScore, rejectionReason);
        }
    }

    private AiChatObservationContext findLatest(String memoryId, Long tenantId, String userId) {
        Deque<AiChatObservationContext> deque = contexts.get(buildKey(memoryId, tenantId, userId));
        return deque == null ? null : deque.peekLast();
    }

    private String buildKey(String memoryId, Long tenantId, String userId) {
        String safeMemoryId = StringUtils.hasText(memoryId) ? memoryId : "unknown";
        String safeTenantId = tenantId == null ? "default" : String.valueOf(tenantId);
        String safeUserId = StringUtils.hasText(userId) ? userId : "anonymous";
        return safeTenantId + ":" + safeUserId + ":" + safeMemoryId;
    }
}
