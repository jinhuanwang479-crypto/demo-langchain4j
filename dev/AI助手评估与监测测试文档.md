# AI 助手评估与监测测试文档

## 1. 文档目的

这份文档用于验证下面几类能力是否真的已经落到代码里，而不是只停留在提示词里：

1. 请求级 trace 是否落库
2. RAG 检索是否有监测指标和拒答标记
3. 工具调用是否有全局监测和请求级留痕
4. 回答结束后是否自动做了在线评估
5. 后台查询接口是否能把这些数据查出来
6. Actuator / Prometheus 是否能暴露监控指标

---

## 2. 前置准备

### 2.1 创建数据库表

先执行观测表脚本：

- [ai_observability.sql](/e:/langchain4j/demo-langchain4j/dev/sql/ai_observability.sql)

会创建两张表：

1. `ai_request_trace`
2. `ai_tool_trace`

### 2.2 确认配置已开启

检查 [application.yml](/e:/langchain4j/demo-langchain4j/src/main/resources/application.yml) 中以下配置存在：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

app:
  ai:
    observability:
      enabled: true
      trace-persistence-enabled: true
      eval-enabled: true
```

### 2.3 启动应用

启动后确认以下地址可访问：

1. `GET /actuator/health`
2. `GET /actuator/metrics`
3. `GET /actuator/prometheus`

如果这三个地址都能打开，说明监控端点已经接上。

---

## 3. 验收结论标准

只要下面 5 类证据都能拿到，就可以证明功能已经实现：

1. `ai_request_trace` 表中出现本次请求记录
2. `ai_tool_trace` 表中出现工具调用明细
3. `/actuator/metrics` 或 `/actuator/prometheus` 能看到新增指标
4. `/admin/ai/observations/*` 能查到汇总、列表、详情
5. `ai_request_trace` 里有 `evaluation_score`、`evaluation_status`、`risk_level`

---

## 4. 测试场景

建议按下面顺序验。

### 场景 A：验证请求 trace 落库

#### 测试问题

发送一个普通知识库问题：

```text
采购入库怎么操作？
```

请求示例：

```http
GET /chat?memoryId=test-ob-001&message=%E9%87%87%E8%B4%AD%E5%85%A5%E5%BA%93%E6%80%8E%E4%B9%88%E6%93%8D%E4%BD%9C%EF%BC%9F
```

#### 预期结果

流式回答结束后，执行 SQL：

```sql
SELECT
    request_id,
    memory_id,
    tenant_id,
    user_id,
    status,
    model_name,
    finish_reason,
    latency_ms,
    first_token_latency_ms,
    streamed_chars,
    retrieved_count,
    top_retrieval_score,
    evaluation_score,
    evaluation_status,
    risk_level
FROM ai_request_trace
WHERE memory_id = 'test-ob-001'
ORDER BY id DESC
LIMIT 1;
```

#### 验收点

必须满足：

1. 有数据
2. `status = SUCCESS`
3. `latency_ms` 不为空
4. `first_token_latency_ms` 不为空
5. `streamed_chars > 0`
6. `evaluation_score` 不为空
7. `evaluation_status` 不为空
8. `risk_level` 不为空

---

### 场景 B：验证 RAG 检索监测

#### 测试问题

继续提一个明显应该命中知识库的问题：

```text
采购入库前需要先做什么？
```

#### 预期结果

执行 SQL：

```sql
SELECT
    memory_id,
    retrieved_count,
    top_retrieval_score,
    retrieval_rejected_reason,
    retrieval_snapshot_json
FROM ai_request_trace
WHERE memory_id = 'test-ob-001'
ORDER BY id DESC
LIMIT 1;
```

#### 验收点

至少满足：

1. `retrieved_count > 0`
2. `top_retrieval_score` 不为空
3. `retrieval_snapshot_json` 不为空

同时访问：

1. `GET /actuator/metrics/ai_rag_hits_count`
2. `GET /actuator/metrics/ai_rag_top_score`
3. `GET /actuator/metrics/ai_rag_search_latency`

说明 RAG 监测已接入。

---

### 场景 C：验证低置信度拒答监测

#### 测试问题

发送一个和当前 ERP 知识库明显无关的问题：

```text
请告诉我火星基地 2077 年的旅游签证政策
```

#### 预期结果

执行 SQL：

```sql
SELECT
    status,
    retrieved_count,
    top_retrieval_score,
    retrieval_rejected_reason,
    evaluation_status,
    risk_level
FROM ai_request_trace
WHERE memory_id = 'test-ob-001'
ORDER BY id DESC
LIMIT 1;
```

#### 验收点

满足任一合理拒答表现即可：

1. `retrieved_count = 0`
2. `retrieval_rejected_reason = 'no_hits'`
3. `retrieval_rejected_reason = 'low_confidence'`

再访问：

```http
GET /actuator/metrics/ai_rag_rejections_total
```

如果指标里能看到 `reason=no_hits` 或 `reason=low_confidence`，说明拒答监测生效。

---

### 场景 D：验证工具调用监测

#### 测试问题

发送一个应触发工具调用的问题，例如：

```text
帮我查询当前商品清单
```

或：

```text
查询最近的财务单据
```

#### 预期结果

先查主表：

```sql
SELECT
    request_id,
    tool_call_count,
    evaluation_status,
    risk_level
FROM ai_request_trace
WHERE memory_id = 'test-ob-001'
ORDER BY id DESC
LIMIT 1;
```

再把拿到的 `request_id` 用于查子表：

```sql
SELECT
    request_id,
    sequence_no,
    tool_name,
    arguments_json,
    result_preview,
    success,
    error_message
FROM ai_tool_trace
WHERE request_id = '上一步查到的request_id'
ORDER BY sequence_no ASC;
```

#### 验收点

必须满足：

1. `tool_call_count > 0`
2. `ai_tool_trace` 至少有一条记录
3. `tool_name` 不为空
4. `arguments_json` 不为空
5. `result_preview` 不为空或 `error_message` 不为空

同时访问：

1. `GET /actuator/metrics/ai_tool_invocations_total`
2. `GET /actuator/metrics/ai_tool_latency`

如果两个指标都能查到，说明工具监测已生效。

---

### 场景 E：验证在线自动评估

#### 检查 SQL

```sql
SELECT
    request_id,
    status,
    response,
    evaluation_score,
    evaluation_status,
    risk_level,
    evaluation_reasons_json
FROM ai_request_trace
WHERE memory_id = 'test-ob-001'
ORDER BY id DESC
LIMIT 5;
```

#### 验收点

正常情况下应满足：

1. `evaluation_score` 有值
2. `evaluation_status` 是 `PASS` / `WARN` / `FAIL` 之一
3. `risk_level` 是 `LOW` / `MEDIUM` / `HIGH` 之一
4. `evaluation_reasons_json` 不为空或为空数组 JSON

#### 建议对照

可以通过下面两种问题对照看评分变化：

1. 高质量问题：`采购入库怎么操作？`
2. 低相关问题：`火星基地 2077 年旅游签证政策`

预期现象：

1. 高质量问题更容易得到较高 `evaluation_score`
2. 低相关问题更容易出现 `WARN` 或更高风险等级

---

### 场景 F：验证后台查询接口

#### 1. 汇总接口

```http
GET /admin/ai/observations/summary
```

#### 预期结果

返回结构里应包含：

1. `totalRequests`
2. `errorRate`
3. `averageLatencyMs`
4. `p95LatencyMs`
5. `averageScore`
6. `passCount`
7. `warnCount`
8. `failCount`

#### 2. 列表接口

```http
GET /admin/ai/observations?page=1&size=10&memoryId=test-ob-001
```

#### 预期结果

返回结构里应包含：

1. `page`
2. `size`
3. `total`
4. `items`

#### 3. 详情接口

```http
GET /admin/ai/observations/{requestId}
```

#### 预期结果

返回结构里应包含：

1. `requestTrace`
2. `toolTraces`

并且 `requestTrace` 内应有：

1. `retrievalSnapshotJson`
2. `evaluationScore`
3. `evaluationStatus`
4. `riskLevel`

---

## 5. Prometheus 指标清单

这次改造后，至少应该能在 `/actuator/metrics` 或 `/actuator/prometheus` 中看到下面这些指标：

### 对话链路

1. `ai_chat_requests_total`
2. `ai_chat_latency`
3. `ai_chat_first_token_latency`
4. `ai_chat_slow_requests_total`
5. `ai_chat_evaluation_score`

### RAG

1. `ai_rag_search_latency`
2. `ai_rag_hits_count`
3. `ai_rag_top_score`
4. `ai_rag_rejections_total`

### Tool

1. `ai_tool_invocations_total`
2. `ai_tool_latency`

### 知识库同步

1. `ai_kb_sync_latency`
2. `ai_kb_docs_processed_total`
3. `ai_kb_docs_skipped_total`
4. `ai_kb_segments_embedded_total`

---

## 6. 一次完整证明链路的最小操作

如果你只想做一次最短验收，按下面顺序就够了：

1. 创建 `ai_request_trace` 和 `ai_tool_trace`
2. 启动项目
3. 访问 `GET /actuator/health`
4. 发起一次知识库问答：`采购入库怎么操作？`
5. 查询 `ai_request_trace`
6. 访问 `GET /actuator/metrics/ai_rag_hits_count`
7. 再发起一次工具问答：`帮我查询当前商品清单`
8. 查询 `ai_tool_trace`
9. 访问 `GET /admin/ai/observations/summary`
10. 访问 `GET /admin/ai/observations`

如果这 10 步都通过，就可以证明：

1. 监测端点存在
2. 请求级 trace 已落库
3. RAG 监测已生效
4. 工具监测已生效
5. 在线评估已生效
6. 后台查询接口已生效

---

## 7. 常见失败排查

### 现象 1：`/actuator/metrics` 打不开

优先检查：

1. `spring-boot-starter-actuator` 是否已引入
2. `management.endpoints.web.exposure.include` 是否包含 `metrics,prometheus`

### 现象 2：表里没有数据

优先检查：

1. SQL 是否已执行
2. `app.ai.observability.trace-persistence-enabled` 是否为 `true`
3. 当前请求是否真正走到了 `/chat`

### 现象 3：有主表数据但没有子表数据

优先检查：

1. 当前问题是否真的触发了工具
2. `tool_call_count` 是否大于 0

### 现象 4：有 trace 但没有评估结果

优先检查：

1. `app.ai.observability.eval-enabled` 是否为 `true`
2. `evaluation_score`、`evaluation_status` 字段是否已建表

---

## 8. 最终验收建议

建议你截图或保留下面 4 份证据作为“功能完成证明”：

1. `ai_request_trace` 的一条完整记录
2. `ai_tool_trace` 的一条工具记录
3. `/actuator/metrics/ai_rag_rejections_total` 或 `/actuator/prometheus` 页面截图
4. `/admin/ai/observations/summary` 返回结果截图

这 4 份证据放在一起，基本就能完整证明“评估与监测功能已经实现并能运行”。
