# AI 对话、知识库分片与 Redis 存储说明

## 1. 先看 AI 对话这一段是怎么跑起来的

这套对话能力不是 `Controller` 里手写调用大模型，而是交给 LangChain4j 的 `@AiService` 做编排。

核心入口有 3 个：

1. `ChatController`
2. `ConsultantService`
3. `CommonConfig`

### 1.1 请求入口：`ChatController`

前端请求 `GET /chat?memoryId=xxx&message=xxx` 时，会进入 `ChatController.chat()`。

这里做的事情很简单，但非常关键：

- 接收 `memoryId` 和用户输入 `message`
- 调用 `consultantService.chat(memoryId, message)`
- 以 `Flux<String>` + `text/event-stream` 的形式，把模型的流式输出一段一段推回前端
- 在流式过程中记录工具调用日志

所以 `ChatController` 更像是“流式对话网关”，它自己不负责推理，只负责把 AI 的输出桥接成 SSE 流。

### 1.2 真正的 AI 编排入口：`ConsultantService`

`ConsultantService` 是一个接口，但它被 `@AiService` 标注后，LangChain4j 会自动给它生成实现。

这个接口把 AI 对话所需的几样能力一次性装配进来：

- `chatModel = "openAiChatModel"`：普通聊天模型
- `streamingChatModel = "openAiStreamingChatModel"`：流式聊天模型
- `chatMemoryProvider = "chatMemoryProvider"`：对话记忆
- `contentRetriever = "contentRetriever"`：知识库检索
- `tools = {...}`：业务工具
- `@SystemMessage(fromResource = "system.txt")`：系统提示词

也就是说，这一行：

```java
TokenStream chat(@MemoryId String memoryId, @UserMessage String input);
```

实际不是“只把一句话发给模型”，而是等价于：

1. 带上系统提示词
2. 带上这个 `memoryId` 对应的历史消息
3. 带上知识库检索能力
4. 带上工具调用能力
5. 然后再把当前用户消息发给模型

### 1.3 对话记忆和知识库是在 `CommonConfig` 里接上的

`CommonConfig` 里主要干了 3 件事：

1. 创建 `ChatMemoryProvider`
2. 创建 `RedisEmbeddingStore`
3. 创建 `StrictContentRetriever`

也就是：

- 对话历史放 Redis
- 知识库向量也放 Redis
- 检索时不是“搜到就塞给模型”，而是先做一层严格过滤

## 2. 一次对话的完整调用链

从代码视角看，一次 `/chat` 请求大致会这样流动：

```text
前端 -> ChatController
     -> ConsultantService(LangChain4j 生成实现)
     -> 读取 system.txt
     -> 读取 memoryId 对应的历史消息
     -> 必要时做知识库向量检索
     -> 必要时调用 ERP 工具
     -> 流式返回模型输出
     -> 前端逐段显示
```

如果再展开一点，就是下面这样：

### 2.1 进入控制器

- `ChatController.chat()` 接收请求
- `startStreamingChat()` 调用 `consultantService.chat(memoryId, message)`
- 注册四个回调：
  - `onPartialResponse`
  - `onToolExecuted`
  - `onCompleteResponse`
  - `onError`

### 2.2 LangChain4j 组装 prompt

LangChain4j 会自动把下面这些东西拼进当前轮对话：

- `system.txt` 中的系统规则
- `memoryId` 对应的聊天历史
- 当前用户消息
- 如果模型认为需要查知识库，就调用 `contentRetriever`
- 如果模型认为需要查业务数据或执行操作，就调用 `tools`

### 2.3 业务工具的角色

当前接入的工具有：

- `systemManagementTool`
- `materialTool`
- `inventoryBillTool`
- `financeTool`
- `reportTool`

它们不是给前端直接调的，而是给模型当“外挂能力”用的。模型可以自己决定什么时候查库存、查用户、开单、查报表。

### 2.4 返回前端

由于用的是 `TokenStream` + SSE，所以前端不是等整段回答生成完才看到结果，而是边生成边显示。

这也是为什么页面会有“打字机式输出”的体验。

## 3. 聊天记忆是怎么做的

### 3.1 使用的是窗口记忆，不是无限历史

`CommonConfig` 里创建的是 `MessageWindowChatMemory`：

- 最多保留 `maxMessages`
- 当前项目配置值是 `14`

配置在 `application.yml` 里：

- `app.ai.memory.max-messages: 14`
- `app.ai.memory.ttl-seconds: 86400`
- `app.ai.memory.key-prefix: erp:chat-memory`

所以它的策略是：

- 只保留最近 14 条消息参与上下文
- 消息写到 Redis
- 24 小时后过期

### 3.2 Redis 里的聊天记忆长什么样

聊天记忆由 `RedisChatMemoryStore` 负责读写。

它的 key 不是简单的 `memoryId`，而是：

```text
erp:chat-memory:tenant:{tenantId}:user:{userId}:memory:{memoryId}
```

比如：

```text
erp:chat-memory:tenant:160:user:501:memory:memory-1
```

这样设计的好处是同一个 `memoryId` 在不同租户、不同用户下不会串数据。

### 3.3 value 的格式

`RedisChatMemoryStore.updateMessages()` 会把 `List<ChatMessage>` 用 `ChatMessageSerializer.messagesToJson()` 序列化成 JSON 字符串，再以 Redis string 的形式保存。

所以这一类数据在 Redis 里：

- key：带租户和用户维度的字符串 key
- value：一个 JSON 数组字符串

可以理解成类似这样：

```json
[
  {
    "type": "system",
    "text": "你是企业智能顾问..."
  },
  {
    "type": "user",
    "text": "采购入库怎么做？"
  },
  {
    "type": "ai",
    "text": "采购入库通常分为以下几步..."
  }
]
```

注意两点：

1. 这里是“聊天上下文存储”，不是 MySQL 里的会话归档表。
2. 这个 JSON 的精确字段名由 LangChain4j 的 `ChatMessageSerializer` 决定，但本质就是“消息数组的 JSON 字符串”。

## 4. MySQL 会话归档和 Redis 聊天记忆不是一回事

这个项目里有两套“对话相关数据”：

### 4.1 Redis 聊天记忆

- 给模型当上下文
- 有 TTL
- 只保留窗口内消息
- 用 `memoryId` 读写

### 4.2 MySQL 会话归档

- 给前端历史列表和详情页展示
- 长期保存
- 由 `ConversationService` 维护
- 走 `/conversation/save`、`/conversations`、`/conversation/{memoryId}` 这些接口

所以要特别注意：

- `/chat` 负责实时对话
- `/conversation/save` 负责归档持久化

它们是分开的两条链路。

## 5. 文档分片是怎么做的

文档分片逻辑主要在 `PdfKnowledgeDocumentLoader` 里。

### 5.1 只处理 PDF

入口先判断文件名是否以 `.pdf` 结尾：

```java
public boolean supports(String filename) {
    return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
}
```

### 5.2 先按“页”抽文本

处理过程先做每页文本提取：

1. 用 `PDFTextStripper` 抽文本
2. 对抽出的文本做清洗
3. 如果开启了 OCR，且抽取质量差，再尝试 OCR

当前配置里：

```yaml
app.ai.rag.pdf.ocr-enabled: false
```

所以默认情况下只走 PDF 文本抽取，不走 OCR。

### 5.3 清洗规则

`cleanPageText()` 会做几件事：

- 去掉空行
- 去掉纯页码、纯数字噪声行
- 合并多余空白
- 根据标点和步骤行规则，决定是否保留换行

换句话说，它不是原样拿 PDF 抽出来的字符串去切，而是先做一次“适合语义检索的文本整理”。

### 5.4 再按页窗口拼装

处理每页文本后，会按 `pageWindow` 把页拼成窗口文本。

当前配置：

```yaml
app.ai.rag.chunk.page-window: 1
```

这表示：

- 1 个页窗口 = 1 页
- 当前项目实际上是“一页一页地送去继续切块”

如果以后把 `pageWindow` 改成 `2`，那就会把相邻两页先拼起来，再进入下一步递归分片。

### 5.5 真正的 chunk 切分：递归分片

核心代码是：

```java
DocumentSplitter splitter = DocumentSplitters.recursive(
    aiRagProperties.getChunk().getMaxSegmentSize(),
    aiRagProperties.getChunk().getOverlap()
);
```

当前配置：

```yaml
app.ai.rag.chunk.max-segment-size: 350
app.ai.rag.chunk.overlap: 60
```

可以直接理解成：

- 每个分片的目标最大长度约 350
- 相邻分片之间保留约 60 的重叠

这样切分的原因是：

1. 太长，向量语义会变散
2. 太短，信息不完整
3. 加 overlap 可以减少“关键句子正好被切断”的问题

### 5.6 分片后的 metadata

每个切出来的 `TextSegment` 都会带上 metadata：

- `docId`
- `docName`
- `docSha256`
- `pageNumber`
- `sourceType`
- `chunkIndex`

其中：

- `pageNumber` 表示这个 chunk 来自哪一页窗口
- `chunkIndex` 表示这一页窗口下的第几个分片

### 5.7 一个分片示例

假设某个 PDF 第 12 页清洗后的文本很长，被切成 3 段，那么大致会变成：

```text
segment 0:
  pageNumber = 12
  chunkIndex = 0
  text = "采购入库前先创建采购订单 ..."

segment 1:
  pageNumber = 12
  chunkIndex = 1
  text = "... 入库审核通过后系统会更新库存 ..."

segment 2:
  pageNumber = 12
  chunkIndex = 2
  text = "... 如需退货则进入采购退货流程 ..."
```

## 6. 分片之后怎么入库到 Redis

分片后的入库逻辑在 `KnowledgeBaseIngestionService`。

### 6.1 启动时自动同步

当前配置：

```yaml
app.ai.rag.ingestion.auto-sync-on-startup: true
```

也就是说，应用启动时会自动扫描：

```yaml
app.ai.rag.resource-pattern: classpath*:content/*
```

当前项目里就是把 `resources/content/` 目录下的 PDF 同步进知识库。

### 6.2 先算两个哈希

入库前会算两个维度的哈希：

1. `docId`
   - 由文件名做 SHA-256
   - 用来标识“这是哪份文档”
2. `docSha256`
   - 由文件内容做 SHA-256
   - 用来判断“内容有没有变化”

### 6.3 manifest 的作用

Redis 里还有一个清单 hash：

```text
erp:kb:v1:manifest
```

这个 hash 的结构可以理解成：

```text
key = erp:kb:v1:manifest

field(docId) -> value(docSha256)
```

例如：

```text
HGETALL erp:kb:v1:manifest

{
  "8f6b...a1": "c1d3...ef",
  "4c2a...90": "7f92...11"
}
```

它的作用有三个：

1. 判断文档是否变化
2. 变化了就重建向量
3. 文档被删除了就把旧向量也清掉

### 6.4 embeddingId 的生成规则

真正写入向量库前，每个 chunk 都会生成一个 id：

```text
{docId}:{pageNumber}:{chunkIndex}
```

例如：

```text
8f6b...a1:12:0
8f6b...a1:12:1
8f6b...a1:12:2
```

## 7. Redis 向量库里的数据格式是什么样

这部分要分两层看：

1. Redis key 长什么样
2. key 对应的 JSON 文档长什么样

### 7.1 Redis key 的命名

`RedisEmbeddingStore` 使用的前缀来自配置：

```yaml
app.ai.rag.vector-prefix: erp:kb:v1:segment:
```

而 chunk 的 id 是：

```text
{docId}:{pageNumber}:{chunkIndex}
```

所以最终 Redis key 形如：

```text
erp:kb:v1:segment:{docId}:{pageNumber}:{chunkIndex}
```

例如：

```text
erp:kb:v1:segment:8f6b...a1:12:0
```

### 7.2 value 不是普通字符串，而是 RedisJSON 文档

`RedisEmbeddingStore.addAll()` 最终往 RedisJSON 里写的是一个 map，字段包括：

- `vector`
- `text`
- metadata 中的所有字段

结合当前项目的 metadata 配置，最终一条记录大概就是这样：

```json
{
  "vector": [0.0123, -0.0911, 0.2388, "..."],
  "text": "第12页\n采购入库先创建采购订单，再执行入库审核并更新库存。",
  "docId": "8f6b...a1",
  "docName": "企智通 ERP 系统 V1.0 用户手册（含详细操作步骤）.pdf",
  "docSha256": "c1d3...ef",
  "sourceType": "pdf",
  "pageNumber": 12,
  "chunkIndex": 0
}
```

这里的几个点很关键：

- `vector`：向量数组，默认字段名就是 `vector`
- `text`：原始文本分片，默认字段名就是 `text`
- 其余字段：来自 `TextSegment.metadata()`

### 7.3 RediSearch 建的索引字段

`CommonConfig.metadataConfig()` 里声明了这些 metadata 字段会进入 RediSearch 索引：

- `$.docId AS docId`
- `$.docName AS docName`
- `$.docSha256 AS docSha256`
- `$.sourceType AS sourceType`
- `$.pageNumber AS pageNumber`
- `$.chunkIndex AS chunkIndex`

而 `RedisEmbeddingStore` 自己还会额外建两个默认字段：

- `$.text AS text`
- `$.vector AS vector`

所以这个索引本质上就是：

- `text` 字段可检索
- `vector` 字段可做 KNN 向量检索
- metadata 字段可用于过滤和回显来源

### 7.4 当前项目的索引名

配置里索引名是：

```text
erp-kb-index-v1
```

所以可以把它理解成：

- RedisJSON 存每个 chunk 的完整文档
- RediSearch 在 `erp-kb-index-v1` 上为这些 JSON 文档建向量索引和 metadata 索引

## 8. 检索时是怎么把 chunk 拿出来的

真正检索发生在 `StrictContentRetriever.retrieve()`。

### 8.1 检索步骤

1. 把用户问题做 embedding
2. 用 `EmbeddingSearchRequest` 去 Redis 向量库搜索
3. 最多取 `maxResults = 4`
4. 低于 `minScore = 0.68` 的结果会被过滤
5. 文本太短的分片也会被过滤
6. 如果最高分都达不到 `answerableMinScore = 0.74`，直接拒答

所以这个项目不是“搜到一点像的内容就回答”，而是：

- 有证据再答
- 证据不够就明确说不确定

### 8.2 返回给模型之前还会加来源头

每个命中的 chunk 在送回模型前，会在文本前面拼一段来源说明，类似：

```text
来源：企智通 ERP 系统 V1.0 用户手册（含详细操作步骤）.pdf 第12页
采购入库先创建采购订单，再执行入库审核并更新库存。
```

这样模型回答时更容易“带着出处说话”。

## 9. 可以把这套设计总结成一句话

这个项目里：

- `ChatController` 负责把 AI 对话做成流式接口
- `ConsultantService` 负责把模型、记忆、知识库、工具拼在一起
- `RedisChatMemoryStore` 负责保存对话上下文
- `PdfKnowledgeDocumentLoader` 负责 PDF 清洗和分片
- `KnowledgeBaseIngestionService` 负责 embedding 和入库
- `StrictContentRetriever` 负责高置信度检索

最终 Redis 里其实同时存在两套 AI 相关数据：

### 9.1 对话记忆

- key 类似：

```text
erp:chat-memory:tenant:160:user:501:memory:memory-1
```

- value 是聊天消息 JSON 字符串

### 9.2 知识库向量

- key 类似：

```text
erp:kb:v1:segment:8f6b...a1:12:0
```

- value 是 RedisJSON 文档，包含：

```json

  "vector": [...],
  "text": "...",
  "docId": "...",
  "docName": "...",
  "docSha256": "...",
  "sourceType": "pdf",
  "pageNumber": 12,
  "chunkIndex": 0
}
```

