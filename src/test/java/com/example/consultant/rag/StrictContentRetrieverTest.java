package com.example.consultant.rag;

import com.example.consultant.config.AiRagProperties;
import com.example.consultant.service.AiObservationRegistry;
import com.example.consultant.service.AiRetrievalAuditService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrictContentRetrieverTest {

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private AiObservationRegistry aiObservationRegistry;

    @Mock
    private AiRetrievalAuditService aiRetrievalAuditService;

    private StrictContentRetriever strictContentRetriever;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        strictContentRetriever = new StrictContentRetriever(
                embeddingStore,
                embeddingModel,
                new AiRagProperties(),
                meterRegistry,
                aiObservationRegistry,
                aiRetrievalAuditService
        );
        when(embeddingModel.embed(any(String.class))).thenReturn(Response.from(Embedding.from(new float[]{1F, 2F})));
    }

    @Test
    void retrieveShouldReturnEmptyWhenTopScoreIsTooLow() {
        EmbeddingMatch<TextSegment> lowScoreMatch = new EmbeddingMatch<>(
                0.70D,
                "embedding-1",
                Embedding.from(new float[]{1F, 2F}),
                TextSegment.from("这是一个足够长但分数不够高的知识片段，用来触发低置信度拒答逻辑。", metadata("用户手册.pdf", 8))
        );
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(lowScoreMatch)));

        List<Content> contents = strictContentRetriever.retrieve(Query.from("采购流程"));

        assertTrue(contents.isEmpty());
        assertEquals(1D, meterRegistry.get("ai_rag_rejections_total")
                .tag("reason", "low_confidence").counter().count());
    }

    @Test
    void retrieveShouldDecorateHighConfidenceMatchesWithSource() {
        EmbeddingMatch<TextSegment> highScoreMatch = new EmbeddingMatch<>(
                0.82D,
                "embedding-2",
                Embedding.from(new float[]{1F, 2F}),
                TextSegment.from("采购入库需要先创建采购订单，然后审核入库并同步库存。", metadata("用户手册.pdf", 12))
        );
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(highScoreMatch)));

        List<Content> contents = strictContentRetriever.retrieve(Query.from("采购入库怎么操作"));

        assertEquals(1, contents.size());
        assertTrue(contents.get(0).textSegment().text().startsWith("来源：用户手册.pdf 第12页"));
        assertTrue(contents.get(0).textSegment().text().contains("采购入库需要先创建采购订单"));
    }

    @Test
    void retrieveShouldRecordShortSegmentRejectionMetric() {
        EmbeddingMatch<TextSegment> shortMatch = new EmbeddingMatch<>(
                0.88D,
                "embedding-short",
                Embedding.from(new float[]{1F, 2F}),
                TextSegment.from("太短了", metadata("manual.pdf", 2))
        );
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(shortMatch)));

        List<Content> contents = strictContentRetriever.retrieve(Query.from("测试"));

        assertTrue(contents.isEmpty());
        assertEquals(2D, meterRegistry.get("ai_rag_rejections_total")
                .tag("reason", "short_segment").counter().count());
    }

    private Metadata metadata(String docName, int pageNumber) {
        return new Metadata()
                .put("docName", docName)
                .put("pageNumber", pageNumber)
                .put("sourceType", "pdf");
    }
}
