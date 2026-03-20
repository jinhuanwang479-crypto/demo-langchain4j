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

public class StrictContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(StrictContentRetriever.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AiRagProperties aiRagProperties;
    private final MeterRegistry meterRegistry;
    private final AiObservationRegistry aiObservationRegistry;
    private final AiRetrievalAuditService aiRetrievalAuditService;

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

    @Override
    /**
     * 执行一次“严格模式”的知识检索。
     * 与普通检索相比，这里额外做了三件事：
     * 1. 过滤过短片段；
     * 2. 低于 answerableMinScore 时直接拒答，不把弱证据交给模型；
     * 3. 记录检索监测指标，并把结果回填到请求级观测上下文。
     */
    public List<Content> retrieve(Query query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String rejectionReason = null;
        try {
            Response<Embedding> response = embeddingModel.embed(query.text());
            EmbeddingSearchRequest request = new EmbeddingSearchRequest(
                    response.content(),
                    aiRagProperties.getRetrieval().getMaxResults(),
                    aiRagProperties.getRetrieval().getMinScore(),
                    null
            );

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> rawMatches = searchResult.matches();
            List<EmbeddingMatch<TextSegment>> acceptedMatches = new ArrayList<>();
            boolean filteredShortSegment = false;

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

            double topScore = resolveTopScore(rawMatches, acceptedMatches);
            meterRegistry.summary("ai_rag_hits_count").record(acceptedMatches.size());
            if (topScore > 0) {
                meterRegistry.summary("ai_rag_top_score").record(topScore);
            }

            if (acceptedMatches.isEmpty()) {
                // 一个可用片段都没有时，直接记为 no_hits 或 short_segment。
                rejectionReason = filteredShortSegment ? "short_segment" : "no_hits";
                incrementRejection(rejectionReason);
                recordRetrievalOutcome(query, 0, topScore, rejectionReason);
                logEvidenceRejected(query, rejectionReason);
                return List.of();
            }

            if (topScore < aiRagProperties.getRetrieval().getAnswerableMinScore()) {
                // 检索到了，但置信度不足以支撑“确定性回答”，进入拒答分支。
                rejectionReason = "low_confidence";
                incrementRejection(rejectionReason);
                recordRetrievalOutcome(query, acceptedMatches.size(), topScore, rejectionReason);
                logEvidenceRejected(query, rejectionReason);
                return List.of();
            }

            int resultWindow = acceptedMatches.size() > 1 && isScoreClustered(acceptedMatches)
                    ? Math.min(3, acceptedMatches.size())
                    : Math.min(2, acceptedMatches.size());

            List<Content> contents = new ArrayList<>(resultWindow);
            for (int i = 0; i < resultWindow; i++) {
                contents.add(toContent(acceptedMatches.get(i)));
            }
            recordRetrievalOutcome(query, contents.size(), topScore, null);
            return contents;
        } finally {
            sample.stop(Timer.builder("ai_rag_search_latency").register(meterRegistry));
        }
    }

    private boolean isUsable(EmbeddingMatch<TextSegment> match) {
        if (match == null || match.embedded() == null || match.embedded().text() == null) {
            return false;
        }
        return match.embedded().text().trim().length() >= aiRagProperties.getRetrieval().getMinSegmentLength();
    }

    private boolean isScoreClustered(List<EmbeddingMatch<TextSegment>> matches) {
        double first = matches.get(0).score() == null ? 0D : matches.get(0).score();
        double second = matches.get(1).score() == null ? 0D : matches.get(1).score();
        return Math.abs(first - second) <= 0.03D;
    }

    private Content toContent(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata() == null ? new Metadata() : segment.metadata().copy();
        String decoratedText = buildSourceHeader(metadata) + segment.text();

        Map<ContentMetadata, Object> contentMetadata = new EnumMap<>(ContentMetadata.class);
        if (match.score() != null) {
            contentMetadata.put(ContentMetadata.SCORE, match.score());
        }
        if (match.embeddingId() != null) {
            contentMetadata.put(ContentMetadata.EMBEDDING_ID, match.embeddingId());
        }
        return Content.from(TextSegment.from(decoratedText, metadata), contentMetadata);
    }

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

    private void logEvidenceRejected(Query query, String reason) {
        log.info("知识检索拒答: memoryId={}, tenantId={}, userId={}, reason={}",
                query.metadata() == null ? null : query.metadata().chatMemoryId(),
                TenantContextHolder.getTenantId(),
                UserContextUtil.getUserId(),
                reason);
    }

    private void incrementRejection(String reason) {
        Counter.builder("ai_rag_rejections_total")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private void recordRetrievalOutcome(Query query, int retrievedCount, Double topScore, String rejectionReason) {
        // 同时回填两份记录：
        // 1. 回填给当前请求上下文，保证最终 trace 可见；
        // 2. 写入审计缓存，兼容某些异步时序下上下文尚未可用的情况。
        String memoryId = query.metadata() == null ? null : (String) query.metadata().chatMemoryId();
        Long tenantId = TenantContextHolder.getTenantId();
        String userId = UserContextUtil.getUserId();
        aiObservationRegistry.recordRetrievalOutcome(memoryId, tenantId, userId, retrievedCount, topScore, rejectionReason);
        aiRetrievalAuditService.record(memoryId, query.text(), retrievedCount, topScore, rejectionReason);
    }

    private double resolveTopScore(List<EmbeddingMatch<TextSegment>> rawMatches, List<EmbeddingMatch<TextSegment>> acceptedMatches) {
        List<EmbeddingMatch<TextSegment>> source = !acceptedMatches.isEmpty() ? acceptedMatches : rawMatches;
        if (source == null || source.isEmpty()) {
            return 0D;
        }
        return source.get(0).score() == null ? 0D : source.get(0).score();
    }
}
