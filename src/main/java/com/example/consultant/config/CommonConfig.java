package com.example.consultant.config;

import com.example.consultant.rag.StrictContentRetriever;
import com.example.consultant.service.AiObservationRegistry;
import com.example.consultant.service.AiRetrievalAuditService;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
/**
 * AI 公共基础设施配置。
 * 这里把会话记忆、向量库以及带监测能力的内容检索器统一注册为 Spring Bean，
 * 让 LangChain4j 在装配 ConsultantService 时直接复用这些组件。
 */
public class CommonConfig {

    @Bean
    /**
     * 创建聊天记忆提供器。
     * 每个 memoryId 对应一个独立的窗口式聊天记忆，底层消息存储放在 Redis 中，
     * 既能控制上下文长度，又能让不同会话互相隔离。
     */
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore redisChatMemoryStore,
                                                 AiMemoryProperties aiMemoryProperties) {
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(aiMemoryProperties.getMaxMessages())
                .id(memoryId)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    @Bean
    @Primary
    /**
     * 创建 Redis 向量存储。
     * 该 Bean 既负责保存 embedding 向量，也负责把 metadata 建成 RediSearch 索引字段，
     * 便于后续检索时返回文档名、页码、分片序号等上下文信息。
     */
    public RedisEmbeddingStore redisEmbeddingStore(RedisProperties redisProperties,
                                                   EmbeddingModel embeddingModel,
                                                   AiRagProperties aiRagProperties) {
        return RedisEmbeddingStore.builder()
                .host(redisProperties.getHost())
                .port(redisProperties.getPort())
                .user(redisProperties.getUsername())
                .password(redisProperties.getPassword())
                .indexName(aiRagProperties.getIndexName())
                .prefix(aiRagProperties.getVectorPrefix())
                .dimension(embeddingModel.dimension())
                .metadataConfig(metadataConfig())
                .build();
    }

    @Bean
    /**
     * 创建带监测能力的内容检索器。
     * 与普通 ContentRetriever 不同，这里额外注入了 MeterRegistry、请求注册表、检索审计服务，
     * 用于记录检索耗时、命中数、拒答原因，并把检索结果回填到请求级 trace 中。
     */
    public ContentRetriever contentRetriever(RedisEmbeddingStore redisEmbeddingStore,
                                             EmbeddingModel embeddingModel,
                                             AiRagProperties aiRagProperties,
                                             MeterRegistry meterRegistry,
                                             AiObservationRegistry aiObservationRegistry,
                                             AiRetrievalAuditService aiRetrievalAuditService) {
        return new StrictContentRetriever(
                redisEmbeddingStore,
                embeddingModel,
                aiRagProperties,
                meterRegistry,
                aiObservationRegistry,
                aiRetrievalAuditService
        );
    }

    private Map<String, SchemaField> metadataConfig() {
        // metadata 字段会同步进入 RediSearch 索引，后续既能检索也能做结果回显。
        Map<String, SchemaField> config = new LinkedHashMap<>();
        config.put("docId", TagField.of("$.docId").as("docId"));
        config.put("docName", TextField.of("$.docName").as("docName"));
        config.put("docSha256", TagField.of("$.docSha256").as("docSha256"));
        config.put("sourceType", TagField.of("$.sourceType").as("sourceType"));
        config.put("pageNumber", NumericField.of("$.pageNumber").as("pageNumber"));
        config.put("chunkIndex", NumericField.of("$.chunkIndex").as("chunkIndex"));
        return config;
    }
}
