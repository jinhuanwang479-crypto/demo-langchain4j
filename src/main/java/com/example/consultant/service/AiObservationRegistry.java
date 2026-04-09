package com.example.consultant.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * AI 请求观测上下文注册表。
 *
 * <p>该类解决了一个关键的分布式观测问题：<b>如何在无侵入的情况下让下游组件找到正确的请求上下文</b>。
 *
 * <p><b>问题背景</b>：
 * <br>在 RAG 应用中，检索器（{@link StrictContentRetriever}）执行向量检索时，需要将检索结果
 * （命中数、相似度分数、拒答原因）回填到当前请求的观测上下文中。但由于：
 * <ul>
 *   <li>检索器是 LangChain4j 框架的组件，无法直接注入请求范围的上下文对象</li>
 *   <li>同一个用户/会话可能同时有多个请求在进行（如快速连续提问）</li>
 *   <li>多租户环境下需要按租户隔离数据</li>
 * </ul>
 *
 * <p><b>解决方案</b>：
 * <br>提供一个线程安全的注册表，以 {@code (tenantId, userId, memoryId)} 为键存储当前进行中的请求上下文栈。
 * 检索器可以通过这些标识找到最近（栈顶）的请求上下文，完成数据回填。
 *
 * <p><b>核心特性</b>：
 * <ul>
 *   <li><b>线程安全</b>：使用 {@link ConcurrentHashMap} 和 {@link ConcurrentLinkedDeque}</li>
 *   <li><b>支持并发请求</b>：同一用户/会话的多个请求使用 Deque 按顺序存储，取最新的一条</li>
 *   <li><b>自动清理</b>：请求完成后主动移除上下文，避免内存泄漏</li>
 *   <li><b>容错性强</b>：key 中的缺失值使用默认值（unknown/default/anonymous）</li>
 * </ul>
 *
 * <p><b>典型调用链路</b>：
 * <pre>
 * 1. AiChatObservationService.streamChat()
 *    → registry.register(context)     // 请求开始时注册
 *
 * 2. StrictContentRetriever.retrieve()
 *    → registry.recordRetrievalOutcome(memoryId, tenantId, userId, ...)  // 检索器回填数据
 *      → findLatest() 找到正确的 context
 *      → context.recordRetrievalOutcome()
 *
 * 3. AiChatObservationService.finalizeSuccess/Error()
 *    → registry.remove(context)       // 请求结束时移除
 * </pre>
 *
 * <p><b>为什么使用 Deque 而不是单个 Context？</b>
 * <br>因为同一个用户可能在上一轮请求还未完成时发起新一轮请求（如快速点击、并行对话）。
 * 使用栈结构可以按时间顺序存储多个上下文，检索器总能找到最新的那个（peekLast）。
 *
 * @author consultant
 * @see AiChatObservationContext
 * @see AiChatObservationService
 * @see StrictContentRetriever
 */
@Component
public class AiObservationRegistry {

    /**
     * 存储所有进行中的请求上下文。
     *
     * <p><b>数据结构</b>：ConcurrentMap&lt;String, Deque&lt;AiChatObservationContext&gt;&gt;
     * <ul>
     *   <li><b>Key</b>：格式为 "{tenantId}:{userId}:{memoryId}"，用于多租户隔离和快速查找</li>
     *   <li><b>Value</b>：双端队列（Deque），按时间顺序存储同一用户/会话的多个并发请求</li>
     * </ul>
     */
    private final ConcurrentMap<String, Deque<AiChatObservationContext>> contexts = new ConcurrentHashMap<>();

    /**
     * 注册一个进行中的请求上下文。
     *
     * <p>在请求开始时调用，将上下文放入注册表。
     * 如果同一 key 下还没有队列，则创建一个新的 {@link ConcurrentLinkedDeque}。
     * 新上下文会被添加到队列末尾（作为最新的请求）。
     *
     * @param context 请求观测上下文
     */
    public void register(AiChatObservationContext context) {
        contexts.computeIfAbsent(buildKey(context.getMemoryId(), context.getTenantId(), context.getUserId()),
                key -> new ConcurrentLinkedDeque<>()).addLast(context);
    }

    /**
     * 移除已完成的请求上下文。
     *
     * <p>在请求结束时调用，将上下文从注册表中移除。
     * 如果移除后队列变为空，则同时删除该 key 对应的条目，避免内存泄漏。
     *
     * @param context 请求观测上下文
     */
    public void remove(AiChatObservationContext context) {
        contexts.computeIfPresent(buildKey(context.getMemoryId(), context.getTenantId(), context.getUserId()),
                (ignored, deque) -> {
                    deque.remove(context);
                    return deque.isEmpty() ? null : deque;
                });
    }

    /**
     * 记录检索结果到当前请求上下文。
     *
     * <p>这是供检索器调用的核心方法。检索器在执行向量检索后，
     * 根据 memoryId、tenantId、userId 定位到当前正在进行的请求上下文，
     * 并将检索结果回填进去。
     *
     * <p><b>设计考量</b>：
     * <br>检索器可能运行在不同于原始请求的线程上（如异步执行），
     * 因此不能使用 ThreadLocal。通过显式传递用户标识 + 注册表查找的方式，
     * 实现了跨线程的上下文传递。
     *
     * @param memoryId        对话记忆ID
     * @param tenantId        租户ID
     * @param userId          用户ID
     * @param retrievedCount  检索到的内容数量
     * @param topScore        最高相似度分数
     * @param rejectionReason 拒答原因（如 low_confidence、no_hits、short_segment）
     */
    public void recordRetrievalOutcome(String memoryId, Long tenantId, String userId,
                                       int retrievedCount, Double topScore, String rejectionReason) {
        // 根据用户标识定位当前最近的一次请求上下文，并回填检索结论
        AiChatObservationContext context = findLatest(memoryId, tenantId, userId);
        if (context != null) {
            context.recordRetrievalOutcome(retrievedCount, topScore, rejectionReason);
        }
        // 注意：如果找不到上下文（如请求已结束），则静默忽略
        // 这种情况可能发生在异步场景下检索器执行较晚，请求已经完成时
    }

    /**
     * 查找指定用户/会话的最新请求上下文。
     *
     * <p>使用 {@link Deque#peekLast()} 获取队列末尾的元素，
     * 即最近注册的请求上下文（LIFO 语义）。
     *
     * @param memoryId 对话记忆ID
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 最新的请求上下文，如果不存在则返回 null
     */
    private AiChatObservationContext findLatest(String memoryId, Long tenantId, String userId) {
        Deque<AiChatObservationContext> deque = contexts.get(buildKey(memoryId, tenantId, userId));
        return deque == null ? null : deque.peekLast();
    }

    /**
     * 构建注册表的 key。
     *
     * <p>key 格式：<code>{tenantId}:{userId}:{memoryId}</code>
     *
     * <p><b>容错处理</b>：
     * <ul>
     *   <li>memoryId 为空时使用 "unknown"</li>
     *   <li>tenantId 为空时使用 "default"</li>
     *   <li>userId 为空时使用 "anonymous"</li>
     * </ul>
     *
     * <p>这样设计确保了即使在部分标识缺失的情况下，也能正确隔离数据，
     * 同时避免 NullPointerException。
     *
     * @param memoryId 对话记忆ID
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 格式化的 key 字符串
     */
    private String buildKey(String memoryId, Long tenantId, String userId) {
        String safeMemoryId = StringUtils.hasText(memoryId) ? memoryId : "unknown";
        String safeTenantId = tenantId == null ? "default" : String.valueOf(tenantId);
        String safeUserId = StringUtils.hasText(userId) ? userId : "anonymous";
        return safeTenantId + ":" + safeUserId + ":" + safeMemoryId;
    }
}
