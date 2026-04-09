package com.example.consultant.service;

import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 检索审计缓存服务。
 * 有些链路下，检索发生的时机和主请求完成回调的时机并不完全同步，
 * 因此额外保留一份“memoryId + queryText -> 检索结论”的短期缓存，
 * 用于在最终收尾时补录 retrievedCount / topScore / rejectionReason。
 */
@Service
public class AiRetrievalAuditService {

    private final ConcurrentMap<String, RetrievalAuditRecord> records = new ConcurrentHashMap<>();

    /** 写入一条检索审计记录。 */
    public void record(String memoryId, String queryText, int retrievedCount, Double topScore, String rejectionReason) {
        if (memoryId == null || queryText == null) {
            return;
        }
        records.put(buildKey(memoryId, queryText), new RetrievalAuditRecord(retrievedCount, topScore, rejectionReason, Instant.now()));
    }

    /** 取出并消费一条检索审计记录，避免同一条记录被重复使用。 */
    public RetrievalAuditRecord consume(String memoryId, String queryText) {
        if (memoryId == null || queryText == null) {
            return null;
        }
        return records.remove(buildKey(memoryId, queryText));
    }

    private String buildKey(String memoryId, String queryText) {
        String digest = DigestUtils.md5DigestAsHex(queryText.trim().getBytes(StandardCharsets.UTF_8));
        return memoryId + ":" + digest;
    }

    /** 审计记录的最小结构，只保留请求收尾所需的关键字段。 */
    public record RetrievalAuditRecord(int retrievedCount, Double topScore, String rejectionReason, Instant recordedAt) {
    }
}
