package com.example.consultant.rag;

import com.example.consultant.config.AiRagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
/**
 * 知识库同步服务。
 * 应用启动时或手动触发时，会扫描资源目录中的知识文档，完成以下工作：
 * 1. 计算文档指纹，判断是否需要重建；
 * 2. 把文档切成分片并生成 embedding；
 * 3. 写入 Redis 向量库；
 * 4. 记录知识库同步相关监测指标。
 */
public class KnowledgeBaseIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestionService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final PdfKnowledgeDocumentLoader pdfKnowledgeDocumentLoader;
    private final AiRagProperties aiRagProperties;
    private final MeterRegistry meterRegistry;
    private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    public KnowledgeBaseIngestionService(EmbeddingStore<TextSegment> embeddingStore,
                                         EmbeddingModel embeddingModel,
                                         StringRedisTemplate redisTemplate,
                                         PdfKnowledgeDocumentLoader pdfKnowledgeDocumentLoader,
                                         AiRagProperties aiRagProperties,
                                         MeterRegistry meterRegistry) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.pdfKnowledgeDocumentLoader = pdfKnowledgeDocumentLoader;
        this.aiRagProperties = aiRagProperties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 是否在启动时自动同步，由配置控制，便于开发环境和生产环境分开管理。
        if (!aiRagProperties.getIngestion().isAutoSyncOnStartup()) {
            return;
        }
        syncKnowledgeBase();
    }

    /**
     * 执行一次完整知识库同步。
     * 最外层包一层计时器，用于统计整次同步耗时。
     */
    public synchronized void syncKnowledgeBase() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            syncResources(resourcePatternResolver.getResources(aiRagProperties.getResourcePattern()));
        } catch (IOException ex) {
            throw new IllegalStateException("扫描知识库资源失败", ex);
        } finally {
            sample.stop(Timer.builder("ai_kb_sync_latency").register(meterRegistry));
        }
    }

    void syncResources(Resource[] resources) {
        // manifest 负责记录“文档标识 -> 文档内容指纹”的映射，用来判断是否增量更新。
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        Set<String> activeDocIds = new HashSet<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (!StringUtils.hasText(filename) || !pdfKnowledgeDocumentLoader.supports(filename)) {
                incrementCounter("ai_kb_docs_skipped_total");
                continue;
            }

            try {
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                String docId = docIdFor(filename);
                String docSha256 = sha256(bytes);
                activeDocIds.add(docId);
                incrementCounter("ai_kb_docs_processed_total");

                String previousSha256 = asString(hashOperations.get(aiRagProperties.getManifestKey(), docId));
                if (Objects.equals(previousSha256, docSha256)) {
                    incrementCounter("ai_kb_docs_skipped_total");
                    log.info("知识库文档未变化，跳过同步: docName={}", filename);
                    continue;
                }

                if (StringUtils.hasText(previousSha256)) {
                    removeDocument(docId);
                }

                List<TextSegment> segments = pdfKnowledgeDocumentLoader.load(bytes, docId, filename, docSha256);
                if (segments.isEmpty()) {
                    incrementCounter("ai_kb_docs_skipped_total");
                    log.warn("知识库文档没有可用分片，跳过写入: docName={}", filename);
                    hashOperations.delete(aiRagProperties.getManifestKey(), docId);
                    continue;
                }

                Response<List<Embedding>> response = embeddingModel.embedAll(segments);
                List<String> ids = buildEmbeddingIds(docId, segments);
                embeddingStore.addAll(ids, response.content(), segments);
                hashOperations.put(aiRagProperties.getManifestKey(), docId, docSha256);
                DistributionSummary.builder("ai_kb_segments_embedded_total")
                        .register(meterRegistry)
                        .record(segments.size());
                log.info("知识库文档同步完成: docName={}, segments={}", filename, segments.size());
            } catch (IOException ex) {
                throw new IllegalStateException("读取知识库文档失败 " + filename, ex);
            }
        }

        removeStaleDocuments(hashOperations, activeDocIds);
    }

    private void removeStaleDocuments(HashOperations<String, Object, Object> hashOperations, Set<String> activeDocIds) {
        // 把资源目录中已经不存在、但 Redis manifest 中还残留的文档一并清理掉。
        Set<Object> storedDocIds = hashOperations.keys(aiRagProperties.getManifestKey());
        if (storedDocIds == null || storedDocIds.isEmpty()) {
            return;
        }

        for (Object storedDocId : storedDocIds) {
            String docId = asString(storedDocId);
            if (!StringUtils.hasText(docId) || activeDocIds.contains(docId)) {
                continue;
            }
            removeDocument(docId);
            hashOperations.delete(aiRagProperties.getManifestKey(), docId);
            log.info("知识库已移除失效文档: docId={}", docId);
        }
    }

    private void removeDocument(String docId) {
        // 根据 docId 删除整个文档对应的全部向量分片。
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId));
    }

    private List<String> buildEmbeddingIds(String docId, List<TextSegment> segments) {
        List<String> ids = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            Metadata metadata = segments.get(i).metadata();
            Integer pageNumber = metadata == null ? null : metadata.getInteger("pageNumber");
            Integer chunkIndex = metadata == null ? null : metadata.getInteger("chunkIndex");
            ids.add(docId + ":" + (pageNumber == null ? 0 : pageNumber) + ":" + (chunkIndex == null ? i : chunkIndex));
        }
        return ids;
    }

    private String docIdFor(String filename) {
        return sha256(filename.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            return bytesToHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前环境不支持 SHA-256", ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        return java.util.stream.IntStream.range(0, bytes.length)
                .mapToObj(i -> String.format("%02x", bytes[i] & 0xff))
                .collect(Collectors.joining());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void incrementCounter(String name) {
        Counter.builder(name).register(meterRegistry).increment();
    }
}
