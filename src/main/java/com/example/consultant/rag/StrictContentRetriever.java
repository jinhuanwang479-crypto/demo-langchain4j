package com.example.consultant.rag;

import com.example.consultant.config.AiRagProperties;
import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.service.AiObservationRegistry;
import com.example.consultant.service.AiRetrievalAuditService;
import com.example.consultant.utils.UserContextUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 严格检索器（Strict Content Retriever）。
 *
 * <p>该检索器在普通向量检索的基础上增加了多层质量控制机制，确保只有高质量的检索结果
 * 才会被传递给大语言模型进行回答，避免模型基于低质量或无关证据产生幻觉。
 *
 * <p>核心增强功能：
 * <ul>
 *   <li><b>片段可用性判断</b>：过滤过短、为空或格式异常的文本片段</li>
 *   <li><b>置信度门槛</b>：设置可回答的最低分数阈值（answerableMinScore），低于阈值时直接拒答</li>
 *   <li><b>智能结果窗口</b>：根据分数分布情况动态决定返回的结果数量（聚类时返回3条，否则返回2条）</li>
 *   <li><b>来源标注</b>：为每个检索结果添加文档名称和页码等来源信息</li>
 *   <li><b>观测埋点</b>：记录检索延迟、命中数、拒答原因等监控指标和审计日志</li>
 * </ul>
 *
 * <p>适用场景：需要高准确性、可解释性强的企业级 RAG 应用，技术客服
 *
 * @author consultant
 * @see ContentRetriever
 * @see EmbeddingStore
 * @see EmbeddingModel
 */
public class StrictContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(StrictContentRetriever.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AiRagProperties aiRagProperties;
    private final MeterRegistry meterRegistry;
    private final AiObservationRegistry aiObservationRegistry;
    private final AiRetrievalAuditService aiRetrievalAuditService;

    /**
     * 构造严格检索器。
     *
     * @param embeddingStore            向量存储服务，用于执行相似度检索
     * @param embeddingModel            嵌入模型，负责将查询文本转换为向量
     * @param aiRagProperties           RAG 配置属性，包含检索参数（最大结果数、最低分数、最小片段长度等）
     * @param meterRegistry             指标注册器，用于记录检索延迟、命中数等监控指标
     * @param aiObservationRegistry     AI 观测注册中心，用于记录检索结果到请求级观测上下文
     * @param aiRetrievalAuditService   检索审计服务，用于持久化检索记录（异步写入）
     */
    public StrictContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                  EmbeddingModel embeddingModel,
                                  AiRagProperties aiRagProperties,
                                  MeterRegistry meterRegistry,
                                  AiObservationRegistry aiObservationRegistry,
                                  AiRetrievalAuditService aiRetrievalAuditService) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.aiRagProperties = aiRagProperties;
        this.meterRegistry = meterRegistry;
        this.aiObservationRegistry = aiObservationRegistry;
        this.aiRetrievalAuditService = aiRetrievalAuditService;
    }

    /**
     * 执行一次“严格模式”的知识检索。
     *
     * <p>检索流程：
     * <ol>
     *   <li>将用户查询文本转换为向量（Embedding）；</li>
     *   <li>在向量存储中执行相似度检索，获取原始匹配结果；</li>
     *   <li>过滤不可用的片段（过短、为空等）；</li>
     *   <li>如果没有可用片段，根据原因记录拒答（short_segment 或 no_hits）；</li>
     *   <li>如果最高分数低于可回答最低阈值，记录低置信拒答（low_confidence）；</li>
     *   <li>根据分数分布动态决定返回结果数量（分数相近时返回3条，否则返回2条）；</li>
     *   <li>为每个结果添加来源信息并返回；</li>
     *   <li>记录检索结果到观测上下文和审计服务。</li>
     * </ol>
     *
     * @param query 查询对象，包含用户问题和对话元数据（如 memoryId）
     * @return 检索到的内容列表，若无可回答证据则返回空列表
     */
    @Override
    public List<Content> retrieve(Query query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String rejectionReason = null;
        try {
            // 步骤1：将用户查询文本转换为向量
            Response<Embedding> response = embeddingModel.embed(query.text());

            // 步骤2：构建检索请求并执行向量相似度搜索
            EmbeddingSearchRequest request = new EmbeddingSearchRequest(
                    response.content(),
                    // 最大结果数
                    aiRagProperties.getRetrieval().getMaxResults(),
                    // 最低分数
                    aiRagProperties.getRetrieval().getMinScore(),
                    null
            );

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> rawMatches = searchResult.matches();
            List<EmbeddingMatch<TextSegment>> acceptedMatches = new ArrayList<>();
            boolean filteredShortSegment = false;

            // 步骤3：过滤不可用的片段（过短、为空等）
            for (EmbeddingMatch<TextSegment> match : rawMatches) {
                // 先过滤无效或过短的片段，避免把噪声文本送给模型。
                if (!isUsable(match)) {
                    filteredShortSegment = true;
                    incrementRejection("short_segment");
                    continue;
                }
                acceptedMatches.add(match);
                logMatch(query, match);
            }

            // 计算最高分数（优先使用可接受片段，若无则使用原始结果）
            double topScore = resolveTopScore(rawMatches, acceptedMatches);

            // 记录命中数量和高分指标
            meterRegistry.summary("ai_rag_hits_count").record(acceptedMatches.size());
            if (topScore > 0) {
                meterRegistry.summary("ai_rag_top_score").record(topScore);
            }

            // 步骤4：无可接受片段 → 拒答
            if (acceptedMatches.isEmpty()) {
                // 一个可用片段都没有时，直接记为 no_hits 或 short_segment。
                rejectionReason = filteredShortSegment ? "short_segment" : "no_hits";
                incrementRejection(rejectionReason);
                recordRetrievalOutcome(query, 0, topScore, rejectionReason);
                logEvidenceRejected(query, rejectionReason);
                return List.of();
            }
            //检查检索到的最佳片段的分数是否达到0.74，如果连最佳片段都不够可信，就整体拒答。
            // 步骤5：置信度不足 → 拒答
            if (topScore < aiRagProperties.getRetrieval().getAnswerableMinScore()) {
                // 检索到了，但置信度不足以支撑“确定性回答”，进入拒答分支。
                rejectionReason = "low_confidence";
                incrementRejection(rejectionReason);
                recordRetrievalOutcome(query, acceptedMatches.size(), topScore, rejectionReason);
                logEvidenceRejected(query, rejectionReason);
                return List.of();
            }

            // 步骤6：动态决定返回结果数量
            // 策略：如果前两条结果分数相近（差距 ≤ 0.03），说明相关性差异不大，返回3条让模型综合判断
            //       否则返回2条最高置信度的结果
            int resultWindow = acceptedMatches.size() > 1 && isScoreClustered(acceptedMatches)
                    ? Math.min(3, acceptedMatches.size())
                    : Math.min(2, acceptedMatches.size());

            // 步骤7：构建返回内容，添加来源信息
            List<Content> contents = new ArrayList<>(resultWindow);
            for (int i = 0; i < resultWindow; i++) {
                contents.add(toContent(acceptedMatches.get(i)));
            }

            // 步骤8：记录成功检索结果
            recordRetrievalOutcome(query, contents.size(), topScore, null);
            return contents;
        } finally {
            // 记录总检索耗时到监控指标
            sample.stop(Timer.builder("ai_rag_search_latency").register(meterRegistry));
        }
    }

    /**
     * 判断检索到的片段是否可用。
     *
     * <p>可用性判断标准：
     * <ul>
     *   <li>片段对象不为空</li>
     *   <li>片段内嵌的 TextSegment 不为空</li>
     *   <li>文本内容不为空</li>
     *   <li>去除首尾空格后的长度 ≥ 配置的最小片段长度（minSegmentLength）</li>
     * </ul>
     *
     * @param match 检索匹配结果
     * @return true 表示片段可用，false 表示不可用
     */
    private boolean isUsable(EmbeddingMatch<TextSegment> match) {
        if (match == null || match.embedded() == null || match.embedded().text() == null) {
            return false;
        }
        return match.embedded().text().trim().length() >= aiRagProperties.getRetrieval().getMinSegmentLength();
    }

    /**
     * 判断前两条检索结果的分数是否聚集（分数相近）。
     *
     * <p>当分数差距 ≤ 0.03 时视为聚集，此时返回更多结果有助于模型综合判断。
     * 该阈值可以根据实际业务场景调整。
     *
     * @param matches 检索匹配结果列表（至少包含2个元素）
     * @return true 表示分数聚集，false 表示分数分散
     */
    private boolean isScoreClustered(List<EmbeddingMatch<TextSegment>> matches) {
        double first = matches.get(0).score() == null ? 0D : matches.get(0).score();
        double second = matches.get(1).score() == null ? 0D : matches.get(1).score();
        return Math.abs(first - second) <= 0.03D;
    }

    /**
     * 将检索匹配结果转换为 RAG 内容对象。
     *
     * <p>转换过程：
     * <ul>
     *   <li>复制原始 Metadata（避免污染原始数据）</li>
     *   <li>添加来源信息前缀（文档名称和页码）</li>
     *   <li>构建内容元数据（包含分数和 embedding ID）</li>
     *   <li>创建 Content 对象供后续 LLM 使用</li>
     * </ul>
     *
     * @param match 检索匹配结果
     * @return 符合 LangChain4j 规范的 Content 对象
     */
    private Content toContent(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        // 复制元数据，避免修改原始数据
        Metadata metadata = segment.metadata() == null ? new Metadata() : segment.metadata().copy();
        // 在文本前添加来源信息（如：来源：产品手册 第3页\n）
        String decoratedText = buildSourceHeader(metadata) + segment.text();

        // 构建内容元数据（使用 EnumMap 提高性能）
        Map<ContentMetadata, Object> contentMetadata = new EnumMap<>(ContentMetadata.class);
        if (match.score() != null) {
            contentMetadata.put(ContentMetadata.SCORE, match.score());
        }
        if (match.embeddingId() != null) {
            contentMetadata.put(ContentMetadata.EMBEDDING_ID, match.embeddingId());
        }
        return Content.from(TextSegment.from(decoratedText, metadata), contentMetadata);
    }

    /**
     * 构建来源信息头。
     *
     * <p>格式示例：
     * <ul>
     *   <li>"来源：产品使用手册 第5页\n"</li>
     *   <li>"来源：产品使用手册\n"</li>
     *   <li>"来源：知识库文档 第2页\n"</li>
     *   <li>空字符串（当文档名称和页码都为空时）</li>
     * </ul>
     *
     * @param metadata 片段元数据，包含 docName 和 pageNumber
     * @return 格式化的来源信息字符串
     */
    private String buildSourceHeader(Metadata metadata) {
        String docName = metadata.getString("docName");
        Integer pageNumber = metadata.getInteger("pageNumber");
        if (docName == null && pageNumber == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder("来源：");
        builder.append(docName == null ? "知识库文档" : docName);
        if (pageNumber != null) {
            builder.append(" 第").append(pageNumber).append("页");
        }
        builder.append('\n');
        return builder.toString();
    }

    /**
     * 记录命中的检索结果到日志。
     *
     * <p>日志包含以下信息：
     * <ul>
     *   <li>memoryId：对话记忆标识</li>
     *   <li>tenantId：租户ID</li>
     *   <li>userId：用户ID</li>
     *   <li>docName：命中的文档名称</li>
     *   <li>pageNumber：命中的页码</li>
     *   <li>score：相似度分数</li>
     * </ul>
     *
     * @param query 查询对象
     * @param match 检索匹配结果
     */
    private void logMatch(Query query, EmbeddingMatch<TextSegment> match) {
        Metadata metadata = match.embedded().metadata();
        log.info("知识检索命中: memoryId={}, tenantId={}, userId={}, docName={}, pageNumber={}, score={}",
                query.metadata() == null ? null : query.metadata().chatMemoryId(),
                TenantContextHolder.getTenantId(),
                UserContextUtil.getUserId(),
                metadata == null ? null : metadata.getString("docName"),
                metadata == null ? null : metadata.getInteger("pageNumber"),
                match.score());
    }

    /**
     * 记录证据被拒绝（拒答）的日志。
     *
     * @param query  查询对象
     * @param reason 拒绝原因（short_segment / no_hits / low_confidence）
     */
    private void logEvidenceRejected(Query query, String reason) {
        log.info("知识检索拒答: memoryId={}, tenantId={}, userId={}, reason={}",
                query.metadata() == null ? null : query.metadata().chatMemoryId(),
                TenantContextHolder.getTenantId(),
                UserContextUtil.getUserId(),
                reason);
    }

    /**
     * 增加拒答计数器的监控指标。
     *
     * <p>指标名称：ai_rag_rejections_total
     * <br>标签：reason（拒答原因）
     *
     * @param reason 拒答原因（short_segment / no_hits / low_confidence）
     */
    private void incrementRejection(String reason) {
        Counter.builder("ai_rag_rejections_total")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录检索结果到观测上下文和审计服务。
     *
     * <p>同时写入两份记录：
     * <ol>
     *   <li><b>观测注册中心</b>：回填给当前请求上下文，保证最终 trace 可见，用于实时监控和调试</li>
     *   <li><b>审计服务</b>：持久化到缓存/数据库，兼容异步场景下上下文不可用的情况，用于后续审计和分析</li>
     * </ol>
     *
     * @param query           查询对象
     * @param retrievedCount  检索到的内容数量（成功返回的数量）
     * @param topScore        最高相似度分数
     * @param rejectionReason 拒答原因（成功时为 null）
     */
    private void recordRetrievalOutcome(Query query, int retrievedCount, Double topScore, String rejectionReason) {
        String memoryId = query.metadata() == null ? null : (String) query.metadata().chatMemoryId();
        Long tenantId = TenantContextHolder.getTenantId();
        String userId = UserContextUtil.getUserId();
        // 写入观测上下文（实时）
        aiObservationRegistry.recordRetrievalOutcome(memoryId, tenantId, userId, retrievedCount, topScore, rejectionReason);
        // 写入审计缓存（持久化）
        aiRetrievalAuditService.record(memoryId, query.text(), retrievedCount, topScore, rejectionReason);
    }

    /**
     * 解析最高相似度分数。
     *
     * <p>优先级：
     * <ol>
     *   <li>优先使用可接受片段列表中的最高分（如果有）</li>
     *   <li>否则使用原始匹配列表中的最高分</li>
     *   <li>如果都为空则返回 0</li>
     * </ol>
     *
     * @param rawMatches      原始检索匹配结果（未过滤）
     * @param acceptedMatches 过滤后的可接受匹配结果
     * @return 最高相似度分数，无结果时返回 0
     */
    private double resolveTopScore(List<EmbeddingMatch<TextSegment>> rawMatches, List<EmbeddingMatch<TextSegment>> acceptedMatches) {
        List<EmbeddingMatch<TextSegment>> source = !acceptedMatches.isEmpty() ? acceptedMatches : rawMatches;
        if (source == null || source.isEmpty()) {
            return 0D;
        }
        return source.get(0).score() == null ? 0D : source.get(0).score();
    }
}
