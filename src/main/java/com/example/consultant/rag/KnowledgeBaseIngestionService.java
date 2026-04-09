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

/**
 * 知识库同步服务。
 *
 * <p>应用启动时或手动触发时，会扫描资源目录中的知识文档，完成以下工作：
 * <ol>
 *   <li>计算文档指纹，判断是否需要重建；</li>
 *   <li>把文档切分成片并生成 embedding；</li>
 *   <li>写入 Redis 向量库；</li>
 *   <li>记录知识库同步相关监测指标。</li>
 * </ol>
 *
 * <p>该服务实现了 {@link ApplicationRunner} 接口，会在 Spring Boot 应用启动后自动执行。
 * 同时提供了同步方法，支持手动触发知识库的增量或全量同步。
 *
 * @author consultant
 * @see ApplicationRunner
 * @see EmbeddingStore
 * @see EmbeddingModel
 */
@Service
public class KnowledgeBaseIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestionService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final PdfKnowledgeDocumentLoader pdfKnowledgeDocumentLoader;
    private final AiRagProperties aiRagProperties;
    private final MeterRegistry meterRegistry;
    private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    /**
     * 构造知识库同步服务。
     *
     * @param embeddingStore            向量存储服务，用于保存和检索文本片段的向量表示
     * @param embeddingModel            嵌入模型，负责将文本转换为向量
     * @param redisTemplate             Redis 模板，用于存储文档清单（manifest）
     * @param pdfKnowledgeDocumentLoader PDF 文档加载器，负责解析 PDF 并切分成文本片段
     * @param aiRagProperties           RAG 配置属性，包含资源路径、清单键等配置
     * @param meterRegistry             指标注册器，用于记录同步相关的监控指标
     */
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

    /**
     * 应用启动时的回调方法。
     *
     * <p>根据配置项 {@code ai.rag.ingestion.auto-sync-on-startup} 决定是否在启动时自动同步知识库。
     * 若 Redis 不可用，会捕获异常并记录警告日志，不会影响应用正常启动。
     *
     * @param args 启动参数，本方法中未使用
     */
    @Override
    public void run(ApplicationArguments args) {
        // 是否在启动时自动同步，由配置控制，便于开发环境和生产环境分开管理。
        if (!aiRagProperties.getIngestion().isAutoSyncOnStartup()) {
            return;
        }
        try {
            syncKnowledgeBase();
        } catch (RuntimeException ex) {
            log.warn("知识库启动同步已跳过，通常是 Redis 不可用: {}", ex.getMessage());
        }
    }

    /**
     * 执行一次完整知识库同步。
     *
     * <p>该方法为同步方法，使用 {@code synchronized} 关键字防止并发同步。
     * 最外层包一层计时器，用于统计整次同步耗时并上报到监控系统。
     *
     * @throws IllegalStateException 当扫描知识库资源失败时抛出
     */
    public synchronized void syncKnowledgeBase() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 根据配置的资源路径模式（如 classpath:knowledge/*.pdf）扫描所有文档资源
            syncResources(resourcePatternResolver.getResources(aiRagProperties.getResourcePattern()));
        } catch (IOException ex) {
            throw new IllegalStateException("扫描知识库资源失败", ex);
        } finally {
            // 记录整次同步的总耗时
            sample.stop(Timer.builder("ai_kb_sync_latency").register(meterRegistry));
        }
    }

    /**
     * 同步一组资源文件到知识库。
     *
     * <p>核心同步逻辑：
     * <ul>
     *   <li>遍历所有资源文件，跳过不支持的格式（非 PDF）；</li>
     *   <li>对每个文档计算 SHA-256 指纹，与 Redis manifest 中记录的指纹比对；</li>
     *   <li>若指纹未变则跳过；若指纹变化或文档新增，则重新加载、切分、生成向量并存储；</li>
     *   <li>最后清理 manifest 中已不存在于资源目录的失效文档。</li>
     * </ul>
     *
     * @param resources 待同步的资源文件数组
     */
    void syncResources(Resource[] resources) {
        // manifest 负责记录“文档标识 -> 文档内容指纹”的映射，用来判断是否增量更新。
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        // 记录本次同步中实际处理过的文档 ID，用于后续清理失效文档
        Set<String> activeDocIds = new HashSet<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            // 检查文件格式是否支持（当前仅支持 PDF）
            if (!StringUtils.hasText(filename) || !pdfKnowledgeDocumentLoader.supports(filename)) {
                incrementCounter("ai_kb_docs_skipped_total");
                continue;
            }

            try {
                // 读取文档的完整字节内容
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                String docId = docIdFor(filename);
                String docSha256 = sha256(bytes);
                activeDocIds.add(docId);
                incrementCounter("ai_kb_docs_processed_total");

                // 从 Redis manifest 中获取该文档上次同步时的指纹
                String previousSha256 = asString(hashOperations.get(aiRagProperties.getManifestKey(), docId));

                // 指纹未变，说明文档内容无更新，跳过同步
                if (Objects.equals(previousSha256, docSha256)) {
                    incrementCounter("ai_kb_docs_skipped_total");
                    log.info("知识库文档未变化，跳过同步: docName={}", filename);
                    continue;
                }

                // 指纹变化，如果之前存在该文档，先删除旧的向量数据
                if (StringUtils.hasText(previousSha256)) {
                    removeDocument(docId);
                }

                // 加载 PDF 文档并切分成文本片段（chunks）
                // 该方法内部会执行 PDF 清洗、递归分块等操作
                List<TextSegment> segments = pdfKnowledgeDocumentLoader.load(bytes, docId, filename, docSha256);

                // 如果没有生成任何分片，跳过写入并删除 manifest 记录
                if (segments.isEmpty()) {
                    incrementCounter("ai_kb_docs_skipped_total");
                    log.warn("知识库文档没有可用分片，跳过写入: docName={}", filename);
                    hashOperations.delete(aiRagProperties.getManifestKey(), docId);
                    continue;
                }

                // 批量生成所有文本片段的向量嵌入（embeddings）
                Response<List<Embedding>> response = embeddingModel.embedAll(segments);

                // 为每个片段构建唯一 ID，格式：文档SHA256:页码:块索引
                List<String> ids = buildEmbeddingIds(docId, segments);

                // 将向量、片段以及对应的 ID 批量写入向量存储
                embeddingStore.addAll(ids, response.content(), segments);

                // 更新 manifest，记录最新的文档指纹
                hashOperations.put(aiRagProperties.getManifestKey(), docId, docSha256);

                // 记录本次同步生成的片段数量到监控指标
                DistributionSummary.builder("ai_kb_segments_embedded_total")
                        .register(meterRegistry)
                        .record(segments.size());
                log.info("知识库文档同步完成: docName={}, segments={}", filename, segments.size());
            } catch (IOException ex) {
                throw new IllegalStateException("读取知识库文档失败 " + filename, ex);
            }
        }

        // 清理失效文档：manifest 中存在但资源目录中已不存在的文档
        removeStaleDocuments(hashOperations, activeDocIds);
    }

    /**
     * 移除失效的文档。
     *
     * <p>遍历 Redis manifest 中的所有文档 ID，如果某个文档 ID 不在本次同步的活跃文档集合中，
     * 说明该文档已从资源目录中删除，需要同步删除其向量数据和 manifest 记录。
     *
     * @param hashOperations Redis Hash 操作对象
     * @param activeDocIds   本次同步中实际处理的活跃文档 ID 集合
     */
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
            // 删除向量存储中的文档数据
            removeDocument(docId);
            // 删除 manifest 中的记录
            hashOperations.delete(aiRagProperties.getManifestKey(), docId);
            log.info("知识库已移除失效文档: docId={}", docId);
        }
    }

    /**
     * 从向量存储中删除指定文档的所有分片。
     *
     * <p>使用 Metadata 过滤条件，删除所有 docId 匹配的向量记录。
     *
     * @param docId 文档唯一标识符（文档内容的 SHA-256 值）
     */
    private void removeDocument(String docId) {
        // 根据 docId 删除整个文档对应的全部向量分片。
        // MetadataFilterBuilder 构建过滤条件：metadata 中的 "docId" 字段等于指定值
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId));
    }

    /**
     * 为每个文本片段构建唯一标识符。
     *
     * <p>ID 格式：{docId}:{pageNumber}:{chunkIndex}
     * <ul>
     *   <li>docId：文档内容的 SHA-256 值</li>
     *   <li>pageNumber：片段所在的页码（若无法获取则使用 0）</li>
     *   <li>chunkIndex：片段在文档中的索引序号</li>
     * </ul>
     *
     * <p>这种 ID 设计保证了每个片段的全局唯一性，且便于按文档或页码进行查询和删除操作。
     *
     * @param docId    文档唯一标识符
     * @param segments 文本片段列表
     * @return 片段 ID 列表，顺序与 segments 列表一致
     */
    private List<String> buildEmbeddingIds(String docId, List<TextSegment> segments) {
        List<String> ids = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            Metadata metadata = segments.get(i).metadata();
            Integer pageNumber = metadata == null ? null : metadata.getInteger("pageNumber");
            Integer chunkIndex = metadata == null ? null : metadata.getInteger("chunkIndex");
            // 使用页码和块索引构建唯一 ID，缺失时使用默认值
            ids.add(docId + ":" + (pageNumber == null ? 0 : pageNumber) + ":" + (chunkIndex == null ? i : chunkIndex));
        }
        return ids;
    }

    /**
     * 根据文件名生成文档唯一标识符。
     *
     * <p>为了保持与内容指纹一致，同样使用 SHA-256 算法。
     * 将文件名转为小写后计算哈希值，确保相同文件名（不同大小写）生成相同的 ID。
     *
     * @param filename 文件名
     * @return 文档标识符（文件名的 SHA-256 哈希值）
     */
    private String docIdFor(String filename) {
        return sha256(filename.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-256 哈希值。
     *
     * <p>用于生成文档内容指纹，判断文档是否发生变化。
     *
     * @param bytes 待计算哈希的字节数组
     * @return 十六进制字符串形式的 SHA-256 哈希值
     * @throws IllegalStateException 如果当前环境不支持 SHA-256 算法
     */
    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            return bytesToHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前环境不支持 SHA-256", ex);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * <p>每个字节转换为两位十六进制数，字母使用小写格式。
     *
     * @param bytes 待转换的字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        return java.util.stream.IntStream.range(0, bytes.length)
                .mapToObj(i -> String.format("%02x", bytes[i] & 0xff))
                .collect(Collectors.joining());
    }

    /**
     * 安全地将对象转换为字符串。
     *
     * <p>当对象为 {@code null} 时返回 {@code null}，否则返回 {@code obj.toString()}。
     *
     * @param value 待转换的对象
     * @return 对象的字符串表示，若为 null 则返回 null
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 增加计数器类型的监控指标。
     *
     * <p>用于记录文档处理数量、跳过数量等计数类指标。
     *
     * @param name 指标名称
     */
    private void incrementCounter(String name) {
        Counter.builder(name).register(meterRegistry).increment();
    }
}
