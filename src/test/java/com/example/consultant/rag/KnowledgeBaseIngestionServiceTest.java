package com.example.consultant.rag;

import com.example.consultant.config.AiRagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseIngestionServiceTest {

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private PdfKnowledgeDocumentLoader pdfKnowledgeDocumentLoader;

    private KnowledgeBaseIngestionService knowledgeBaseIngestionService;
    private AiRagProperties aiRagProperties;

    @BeforeEach
    void setUp() {
        aiRagProperties = new AiRagProperties();
        knowledgeBaseIngestionService = new KnowledgeBaseIngestionService(
                embeddingStore,
                embeddingModel,
                redisTemplate,
                pdfKnowledgeDocumentLoader,
                aiRagProperties,
                new SimpleMeterRegistry()
        );
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void syncResourcesShouldSkipUnchangedDocument() throws Exception {
        Resource resource = namedPdf("manual.pdf", "same-content");
        String docId = sha256("manual.pdf".getBytes(StandardCharsets.UTF_8));
        String docSha256 = sha256("same-content".getBytes(StandardCharsets.UTF_8));

        when(hashOperations.get(aiRagProperties.getManifestKey(), docId)).thenReturn(docSha256);
        when(hashOperations.keys(aiRagProperties.getManifestKey())).thenReturn(Set.of(docId));
        when(pdfKnowledgeDocumentLoader.supports("manual.pdf")).thenReturn(true);

        knowledgeBaseIngestionService.syncResources(new Resource[]{resource});

        verify(pdfKnowledgeDocumentLoader, never()).load(any(), any(), any(), any());
        verify(embeddingStore, never()).addAll(anyList(), anyList(), anyList());
    }

    @Test
    void syncResourcesShouldReindexChangedDocument() throws Exception {
        Resource resource = namedPdf("manual.pdf", "new-content");
        String docId = sha256("manual.pdf".getBytes(StandardCharsets.UTF_8));
        String docSha256 = sha256("new-content".getBytes(StandardCharsets.UTF_8));
        List<TextSegment> segments = List.of(TextSegment.from("采购流程内容片段", new Metadata()
                .put("docId", docId)
                .put("docName", "manual.pdf")
                .put("docSha256", docSha256)
                .put("pageNumber", 1)
                .put("chunkIndex", 0)
                .put("sourceType", "pdf")));

        when(hashOperations.get(aiRagProperties.getManifestKey(), docId)).thenReturn("old-sha");
        when(hashOperations.keys(aiRagProperties.getManifestKey())).thenReturn(Set.of(docId));
        when(pdfKnowledgeDocumentLoader.supports("manual.pdf")).thenReturn(true);
        when(pdfKnowledgeDocumentLoader.load(any(), eq(docId), eq("manual.pdf"), eq(docSha256))).thenReturn(segments);
        when(embeddingModel.embedAll(segments)).thenReturn(Response.from(List.of(Embedding.from(new float[]{1F, 2F}))));

        knowledgeBaseIngestionService.syncResources(new Resource[]{resource});

        verify(embeddingStore).removeAll(any(Filter.class));
        verify(embeddingStore).addAll(anyList(), anyList(), eq(segments));
        verify(hashOperations).put(aiRagProperties.getManifestKey(), docId, docSha256);
    }

    private Resource namedPdf(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte b : hashed) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }
}
