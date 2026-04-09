"use strict";

const PptxGenJS = require("pptxgenjs");
const {
  autoFontSize,
  calcTextBox,
  codeToRuns,
  safeOuterShadow,
  warnIfSlideHasOverlaps,
  warnIfSlideElementsOutOfBounds,
} = require("./pptxgenjs_helpers");

const pptx = new PptxGenJS();

const FONT_UI = "Microsoft YaHei";
const FONT_CODE = "Consolas";

const COLORS = {
  navy: "0E1A2B",
  ink: "162033",
  blue: "2F6BFF",
  teal: "19A7A8",
  amber: "F0B429",
  coral: "FF7A59",
  mist: "EEF3FB",
  pale: "F8FAFD",
  text: "233044",
  subtext: "5D6B82",
  white: "FFFFFF",
  line: "D9E2F1",
  slate: "97A6BA",
  green: "1E9E5A",
};

const DECK_PATH = "ai-interview-demo.pptx";

pptx.layout = "LAYOUT_WIDE";
pptx.author = "OpenAI Codex";
pptx.company = "OpenAI";
pptx.subject = "ERP AI backend interview presentation";
pptx.title = "ERP 智能助手后端 AI 架构亮点";
pptx.lang = "zh-CN";
pptx.theme = {
  headFontFace: FONT_UI,
  bodyFontFace: FONT_UI,
  lang: "zh-CN",
};

function addBackdrop(slide, tone = "light") {
  slide.background = { color: tone === "dark" ? COLORS.navy : COLORS.pale };
  if (tone === "dark") {
    slide.addShape(pptx.ShapeType.rect, {
      x: 12.94,
      y: 0,
      w: 0.12,
      h: 7.5,
      line: { color: COLORS.blue, transparency: 100 },
      fill: { color: COLORS.blue, transparency: 45 },
    });
    slide.addShape(pptx.ShapeType.rect, {
      x: 0,
      y: 7.28,
      w: 13.333,
      h: 0.08,
      line: { color: COLORS.teal, transparency: 100 },
      fill: { color: COLORS.teal, transparency: 38 },
    });
  } else {
    slide.addShape(pptx.ShapeType.rect, {
      x: 12.96,
      y: 0,
      w: 0.08,
      h: 7.5,
      line: { color: COLORS.blue, transparency: 100 },
      fill: { color: COLORS.blue, transparency: 82 },
    });
    slide.addShape(pptx.ShapeType.rect, {
      x: 0,
      y: 7.34,
      w: 13.333,
      h: 0.06,
      line: { color: COLORS.teal, transparency: 100 },
      fill: { color: COLORS.teal, transparency: 80 },
    });
  }
}

function addDeckFrame(slide, tone = "light") {
  slide.addShape(pptx.ShapeType.line, {
    x: 0.7,
    y: 0.56,
    w: 11.9,
    h: 0,
    line: { color: tone === "dark" ? "33415E" : COLORS.line, width: 1.1 },
  });
  slide.addText("AI 面试演示稿", {
    x: 11.02,
    y: 0.24,
    w: 1.5,
    h: 0.22,
    fontFace: FONT_UI,
    fontSize: 9,
    bold: true,
    color: tone === "dark" ? "A9B7D4" : COLORS.slate,
    margin: 0,
    align: "right",
  });
}

function addSlideHeader(slide, index, title, subtitle) {
  addBackdrop(slide, "light");
  addDeckFrame(slide, "light");

  slide.addText(String(index).padStart(2, "0"), {
    x: 0.72,
    y: 0.76,
    w: 0.6,
    h: 0.28,
    fontFace: FONT_UI,
    fontSize: 11,
    bold: true,
    color: COLORS.blue,
    margin: 0,
  });
  slide.addText(title, {
    x: 1.22,
    y: 0.72,
    w: 7.5,
    h: 0.38,
    fontFace: FONT_UI,
    fontSize: 24,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  slide.addText(subtitle, {
    x: 1.24,
    y: 1.12,
    w: 8.3,
    h: 0.24,
    fontFace: FONT_UI,
    fontSize: 10.5,
    color: COLORS.subtext,
    margin: 0,
  });
}

function addFittedText(slide, text, box, style = {}, sizing = {}) {
  const fontFace = style.fontFace || FONT_UI;
  const opts = autoFontSize(text, fontFace, {
    x: box.x,
    y: box.y,
    w: box.w,
    h: box.h,
    fontSize: sizing.fontSize || 20,
    minFontSize: sizing.minFontSize || 12,
    maxFontSize: sizing.maxFontSize || 24,
    leading: sizing.leading || 1.18,
    padding: sizing.padding || 0.03,
    bold: style.bold === true,
    italic: style.italic === true,
    margin: style.margin,
  });
  slide.addText(text, {
    ...opts,
    fontFace,
    color: style.color || COLORS.text,
    bold: style.bold || false,
    italic: style.italic || false,
    margin: style.margin !== undefined ? style.margin : 0,
    valign: style.valign || "top",
    breakLine: false,
    align: style.align || "left",
  });
}

function addMetricPill(slide, x, y, w, label, value, tone = "blue") {
  const fill = tone === "amber" ? "FFF4DE" : tone === "teal" ? "E5FAF8" : "EAF1FF";
  const edge = tone === "amber" ? "F0B429" : tone === "teal" ? "19A7A8" : "2F6BFF";
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h: 0.72,
    rectRadius: 0.08,
    line: { color: edge, transparency: 76, width: 1 },
    fill: { color: fill },
  });
  slide.addText(value, {
    x: x + 0.18,
    y: y + 0.12,
    w: w - 0.36,
    h: 0.24,
    fontFace: FONT_UI,
    fontSize: 18,
    bold: true,
    color: COLORS.ink,
    margin: 0,
    align: "center",
  });
  slide.addText(label, {
    x: x + 0.12,
    y: y + 0.41,
    w: w - 0.24,
    h: 0.14,
    fontFace: FONT_UI,
    fontSize: 8.5,
    color: COLORS.subtext,
    margin: 0,
    align: "center",
  });
}

function addCard(slide, x, y, w, h, title, body, accent = COLORS.blue) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h,
    rectRadius: 0.08,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
    shadow: safeOuterShadow("9AA9C0", 0.14, 45, 2, 1),
  });
  slide.addShape(pptx.ShapeType.rect, {
    x,
    y,
    w: 0.08,
    h,
    line: { color: accent, transparency: 100 },
    fill: { color: accent },
  });
  slide.addText(title, {
    x: x + 0.22,
    y: y + 0.16,
    w: w - 0.34,
    h: 0.26,
    fontFace: FONT_UI,
    fontSize: 14,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  addFittedText(
    slide,
    body,
    { x: x + 0.22, y: y + 0.5, w: w - 0.36, h: h - 0.64 },
    { color: COLORS.subtext },
    { minFontSize: 10.5, maxFontSize: 13, fontSize: 12.5, leading: 1.16, padding: 0 }
  );
}

function addStep(slide, x, y, w, h, index, title, body, tone) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addShape(pptx.ShapeType.ellipse, {
    x: x + 0.18,
    y: y + 0.18,
    w: 0.42,
    h: 0.42,
    line: { color: tone, transparency: 100 },
    fill: { color: tone },
  });
  slide.addText(String(index), {
    x: x + 0.18,
    y: y + 0.22,
    w: 0.42,
    h: 0.16,
    align: "center",
    fontFace: FONT_UI,
    fontSize: 11,
    bold: true,
    color: COLORS.white,
    margin: 0,
  });
  slide.addText(title, {
    x: x + 0.74,
    y: y + 0.16,
    w: w - 0.92,
    h: 0.2,
    fontFace: FONT_UI,
    fontSize: 12.5,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  addFittedText(
    slide,
    body,
    { x: x + 0.74, y: y + 0.42, w: w - 0.92, h: h - 0.54 },
    { color: COLORS.subtext },
    { minFontSize: 10, maxFontSize: 11.5, fontSize: 11, leading: 1.12, padding: 0 }
  );
}

function addFooter(slide, text, tone = "light") {
  slide.addText(text, {
    x: 0.72,
    y: 7.14,
    w: 7.7,
    h: 0.14,
    fontFace: FONT_UI,
    fontSize: 8.5,
    color: tone === "dark" ? "C6D3EA" : COLORS.slate,
    margin: 0,
  });
}

function finalizeSlide(slide) {
  if (process.env.CHECK_LAYOUT === "1") {
    warnIfSlideHasOverlaps(slide, pptx, { muteContainment: true });
    warnIfSlideElementsOutOfBounds(slide, pptx);
  }
}

function slide1Title() {
  const slide = pptx.addSlide();
  addBackdrop(slide, "dark");
  addDeckFrame(slide, "dark");

  slide.addText("ERP 智能助手后端", {
    x: 0.82,
    y: 1.05,
    w: 4.2,
    h: 0.42,
    fontFace: FONT_UI,
    fontSize: 16,
    bold: true,
    color: "C6D3EA",
    margin: 0,
  });

  const titleHeight = calcTextBox(28, {
    text: "AI 架构亮点与面试讲法",
    w: 6.2,
    fontFace: FONT_UI,
    bold: true,
    padding: 0,
  }).h;
  slide.addText("AI 架构亮点与面试讲法", {
    x: 0.8,
    y: 1.55,
    w: 6.3,
    h: titleHeight,
    fontFace: FONT_UI,
    fontSize: 28,
    bold: true,
    color: COLORS.white,
    margin: 0,
  });

  addFittedText(
    slide,
    "基于《代码详情.md》提炼的可演示版本，强调真实代码可证、技术决策理由与现场答辩节奏。",
    { x: 0.84, y: 2.45, w: 5.9, h: 0.72 },
    { color: "D9E4F5" },
    { minFontSize: 13, maxFontSize: 16, fontSize: 15, leading: 1.2, padding: 0 }
  );

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 7.35,
    y: 1.18,
    w: 4.98,
    h: 4.98,
    rectRadius: 0.08,
    line: { color: "3C4C6A", width: 1.2 },
    fill: { color: "15233A", transparency: 4 },
  });
  slide.addText("演示框架", {
    x: 7.72,
    y: 1.5,
    w: 1.7,
    h: 0.22,
    fontFace: FONT_UI,
    fontSize: 11,
    bold: true,
    color: "AFC5F8",
    margin: 0,
  });
  const frameworkItems = [
    ["01", "声明式 AI 编排"],
    ["02", "RAG 证据门控"],
    ["03", "多租户记忆隔离"],
    ["04", "Tool 执行闭环"],
    ["05", "可观测与在线评估"],
  ];
  frameworkItems.forEach((item, idx) => {
    const y = 1.95 + idx * 0.72;
    slide.addShape(pptx.ShapeType.line, {
      x: 7.72,
      y: y + 0.32,
      w: 3.86,
      h: 0,
      line: { color: "2B3B59", width: 0.8 },
    });
    slide.addText(item[0], {
      x: 7.72,
      y,
      w: 0.5,
      h: 0.24,
      fontFace: FONT_UI,
      fontSize: 11,
      bold: true,
      color: COLORS.amber,
      margin: 0,
    });
    slide.addText(item[1], {
      x: 8.38,
      y: y - 0.01,
      w: 2.95,
      h: 0.24,
      fontFace: FONT_UI,
      fontSize: 14,
      bold: true,
      color: COLORS.white,
      margin: 0,
    });
  });

  addMetricPill(slide, 0.84, 4.2, 1.72, "主 AI Tool", "5 个", "amber");
  addMetricPill(slide, 2.74, 4.2, 1.72, "记忆 TTL", "86400s", "teal");
  addMetricPill(slide, 4.64, 4.2, 1.72, "慢请求阈值", "5000ms", "blue");

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.82,
    y: 5.18,
    w: 5.96,
    h: 0.98,
    rectRadius: 0.04,
    line: { color: "41526F", width: 1 },
    fill: { color: "132139" },
  });
  slide.addText("讲法原则", {
    x: 1.02,
    y: 5.38,
    w: 0.96,
    h: 0.18,
    fontFace: FONT_UI,
    fontSize: 10,
    bold: true,
    color: "8DB9FF",
    margin: 0,
  });
  addFittedText(
    slide,
    "只讲代码里能证实的事实，不虚构线上收益；把架构设计、阈值参数、容灾处理和观测指标讲清楚。",
    { x: 2.12, y: 5.28, w: 4.34, h: 0.52 },
    { color: COLORS.white },
    { minFontSize: 12, maxFontSize: 13.5, fontSize: 13, leading: 1.14, padding: 0 }
  );

  addFooter(slide, "来源：dev/代码详情.md | 生成方式：slides skill + PptxGenJS", "dark");
  finalizeSlide(slide);
}

function slide2Overview() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 2, "核心 AI 亮点总览", "把散落的模型接入点收口为一套可控、可证、可运营的企业级 AI 服务。");

  addCard(slide, 0.9, 1.65, 3.72, 1.38, "声明式 AI 编排", "用 @AiService 把模型、提示词、记忆、RAG、Tool 统一装配，不在 Controller 手写 SDK 调用。", COLORS.blue);
  addCard(slide, 4.82, 1.65, 3.72, 1.38, "企业级 RAG 检索", "不是“搜到就答”，而是先做短文本过滤、最低分阈值和低置信拒答。", COLORS.teal);
  addCard(slide, 8.74, 1.65, 3.72, 1.38, "多租户会话记忆", "tenantId + userId + memoryId 三段式记忆键，避免跨租户串话。", COLORS.amber);
  addCard(slide, 0.9, 3.28, 5.76, 1.6, "Tool 执行型助手", "把 AI 从问答升级为能查、能办、能开单的执行入口，同时接入角色鉴权、监控计量和请求级 trace。", COLORS.coral);
  addCard(slide, 6.9, 3.28, 5.54, 1.6, "可观测性 + 在线评估", "把每次 AI 请求沉淀成结构化 trace，用规则化评分代替“再上一个模型做质检”。", COLORS.blue);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.92,
    y: 5.18,
    w: 11.56,
    h: 1.42,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addText("代码可证指标", {
    x: 1.16,
    y: 5.42,
    w: 1.3,
    h: 0.22,
    fontFace: FONT_UI,
    fontSize: 12,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  addMetricPill(slide, 2.72, 5.32, 1.75, "模型超时", "60s", "blue");
  addMetricPill(slide, 4.67, 5.32, 2.15, "RAG 阈值", "0.68 / 0.74", "teal");
  addMetricPill(slide, 7.02, 5.32, 1.78, "消息窗口", "14 条", "amber");
  addMetricPill(slide, 9.01, 5.32, 1.74, "主 AI Tool", "5 个", "teal");
  addMetricPill(slide, 10.95, 5.32, 1.28, "评分", "35/30/20/15", "blue");

  addFooter(slide, "适合讲开场：先用 1 页把“我做了什么”和“为什么不是简单接模型”讲清楚。");
  finalizeSlide(slide);
}

function slide3Orchestration() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 3, "声明式 AI 编排 + 流式响应", "核心价值不是“能接模型”，而是“能稳定接进业务”，并把等待过程变成可见流程。");

  addCard(slide, 0.92, 1.62, 3.48, 2.22, "为什么这样设计", "把模型调用、记忆读取、RAG 检索、Tool 调用都抽离出 Controller，统一进入受控的业务编排层。", COLORS.blue);
  addCard(slide, 0.92, 4.02, 3.48, 1.48, "讲法抓手", "“我没有把大模型当 HTTP Client 去调用，而是把它抽象成一个受控的业务编排层。”", COLORS.amber);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 4.66,
    y: 1.62,
    w: 3.48,
    h: 3.88,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: "101826" },
    shadow: safeOuterShadow("9AA9C0", 0.12, 45, 2, 1),
  });
  slide.addText("核心接口", {
    x: 4.94,
    y: 1.88,
    w: 1.2,
    h: 0.22,
    fontFace: FONT_UI,
    fontSize: 11,
    bold: true,
    color: "9CB9FF",
    margin: 0,
  });
  slide.addText(codeToRuns(`@AiService(
  wiringMode = EXPLICIT,
  chatModel = "openAiChatModel",
  streamingChatModel = "openAiStreamingChatModel",
  chatMemoryProvider = "chatMemoryProvider",
  contentRetriever = "contentRetriever",
  tools = { "systemManagementTool",
            "materialTool",
            "inventoryBillTool",
            "financeTool",
            "reportTool" }
)
TokenStream chat(@MemoryId String memoryId,
                 @UserMessage String input);`, "java"), {
    x: 4.92,
    y: 2.18,
    w: 2.96,
    h: 2.98,
    margin: 0,
    breakLine: false,
    valign: "top",
  });

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 8.44,
    y: 1.62,
    w: 3.98,
    h: 3.88,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addText("请求到响应的数据流", {
    x: 8.72,
    y: 1.88,
    w: 1.9,
    h: 0.2,
    fontFace: FONT_UI,
    fontSize: 11,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  const flow = [
    ["前端 /chat", COLORS.blue],
    ["AiChatObservationService", COLORS.teal],
    ["ConsultantService.chat()", COLORS.amber],
    ["Memory / RAG / Tool 注入", COLORS.coral],
    ["SSE 持续推送给前端", COLORS.blue],
  ];
  flow.forEach((entry, idx) => {
    const y = 2.28 + idx * 0.56;
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 8.74,
      y,
      w: 3.28,
      h: 0.38,
      rectRadius: 0.05,
      line: { color: entry[1], transparency: 100 },
      fill: { color: entry[1], transparency: 86 },
    });
    slide.addText(entry[0], {
      x: 8.96,
      y: y + 0.1,
      w: 2.8,
      h: 0.14,
      fontFace: FONT_UI,
      fontSize: 10.5,
      bold: idx < 2,
      color: COLORS.ink,
      margin: 0,
    });
    if (idx < flow.length - 1) {
      slide.addShape(pptx.ShapeType.line, {
        x: 10.38,
        y: y + 0.4,
        w: 0,
        h: 0.16,
        line: { color: COLORS.slate, width: 1.2, beginArrowType: "none", endArrowType: "triangle" },
      });
    }
  });

  addMetricPill(slide, 8.74, 5.74, 1.55, "首要体验", "SSE", "blue");
  addMetricPill(slide, 10.47, 5.74, 1.55, "模型超时", "60s", "amber");

  addFooter(slide, "现场建议：先讲抽象层次，再讲为什么流式返回能提高 ERP 场景中的交互确定性。");
  finalizeSlide(slide);
}

function slide4Rag() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 4, "企业级 RAG：不是“搜到就答”", "关键不是召回多少，而是证据门控和低置信拒答，避免模型用“不像事实的事实”作答。");

  addStep(slide, 0.92, 1.72, 2.34, 1.18, 1, "文档切块", "PDF 清洗、递归分块，默认 chunk=350 / overlap=60。", COLORS.blue);
  addStep(slide, 3.36, 1.72, 2.34, 1.18, 2, "向量化", "EmbeddingModel 把片段入库到 Redis 向量存储。", COLORS.teal);
  addStep(slide, 5.8, 1.72, 2.34, 1.18, 3, "检索", "query 也先向量化，再按 maxResults 和 minScore 检索。", COLORS.amber);
  addStep(slide, 8.24, 1.72, 2.34, 1.18, 4, "门控", "短文本过滤 + topScore 校验，不够就拒答。", COLORS.coral);

  for (let i = 0; i < 3; i += 1) {
    slide.addShape(pptx.ShapeType.line, {
      x: 3.16 + i * 2.44,
      y: 2.31,
      w: 0.16,
      h: 0,
      line: { color: COLORS.slate, width: 1.2, beginArrowType: "none", endArrowType: "triangle" },
    });
  }

  addCard(slide, 0.92, 3.3, 4.2, 2.24, "StrictContentRetriever 的价值", "不是把检索到的文本硬塞给模型，而是先判断这份证据值不值得信。这个设计点非常适合在面试里强调“可信问答”思维。", COLORS.teal);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 5.34,
    y: 3.3,
    w: 3.1,
    h: 2.24,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addText("关键阈值", {
    x: 5.58,
    y: 3.58,
    w: 1.1,
    h: 0.2,
    fontFace: FONT_UI,
    fontSize: 12,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  [
    ["minScore", "0.68"],
    ["answerableMinScore", "0.74"],
    ["maxResults", "4"],
    ["minSegmentLength", "40"],
  ].forEach((row, idx) => {
    const y = 3.96 + idx * 0.34;
    slide.addText(row[0], {
      x: 5.58,
      y,
      w: 1.6,
      h: 0.16,
      fontFace: FONT_CODE,
      fontSize: 10,
      color: COLORS.subtext,
      margin: 0,
    });
    slide.addText(row[1], {
      x: 7.3,
      y,
      w: 0.68,
      h: 0.16,
      fontFace: FONT_UI,
      fontSize: 12,
      bold: true,
      color: COLORS.blue,
      margin: 0,
      align: "right",
    });
  });

  addCard(slide, 8.66, 3.3, 3.76, 2.24, "增量同步链路", "文档按 docId + docSha256 做变更检测。没变就跳过，变化则删旧向量、写新向量，并清理失效索引。", COLORS.blue);

  addFooter(slide, "这里最能体现工程味：检索并不是“越会答越好”，而是“证据不够时宁可不答”。");
  finalizeSlide(slide);
}

function slide5Memory() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 5, "多租户会话记忆：不让 AI 串话", "会话记忆不是普通缓存，而是带隔离语义的会话状态存储。");

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.92,
    y: 1.7,
    w: 4.08,
    h: 4.46,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addText("记忆键设计", {
    x: 1.18,
    y: 1.98,
    w: 1.3,
    h: 0.2,
    fontFace: FONT_UI,
    fontSize: 12,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  ["tenantId", "userId", "memoryId"].forEach((part, idx) => {
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 1.16,
      y: 2.48 + idx * 0.92,
      w: 3.56,
      h: 0.54,
      rectRadius: 0.05,
      line: { color: idx === 0 ? COLORS.blue : idx === 1 ? COLORS.teal : COLORS.amber, transparency: 100 },
      fill: { color: idx === 0 ? "EAF1FF" : idx === 1 ? "E5FAF8" : "FFF4DE" },
    });
    slide.addText(part, {
      x: 1.38,
      y: 2.66 + idx * 0.92,
      w: 1.6,
      h: 0.14,
      fontFace: FONT_CODE,
      fontSize: 12,
      bold: true,
      color: COLORS.ink,
      margin: 0,
    });
  });
  slide.addText("组合键让同租户不同用户、同用户不同会话都能安全隔离。", {
    x: 1.18,
    y: 5.46,
    w: 3.54,
    h: 0.3,
    fontFace: FONT_UI,
    fontSize: 11,
    color: COLORS.subtext,
    margin: 0,
  });

  addCard(slide, 5.28, 1.7, 3.18, 2.08, "上下文解析链路", "TenantInterceptor 解析租户，上下文进入 ThreadLocal，再交由 RedisChatMemoryStore / MessageWindowChatMemory 落地。", COLORS.teal);
  addCard(slide, 5.28, 4.08, 3.18, 2.08, "为什么不用单纯 memoryId", "只按 memoryId 存最容易发生跨租户数据串线，企业场景必须先考虑租户隔离和权限边界。", COLORS.coral);
  addCard(slide, 8.72, 1.7, 3.7, 2.08, "代码可证指标", "窗口消息数 14 条；Redis TTL 86400 秒；异常时可以退化为非持久化或空上下文，不打崩主链路。", COLORS.blue);
  addCard(slide, 8.72, 4.08, 3.7, 2.08, "面试可强调的能力", "你不是在“存聊天记录”，而是在设计企业 AI 的上下文安全模型。这个表述会比“做了记忆”更有分量。", COLORS.amber);

  addFooter(slide, "这一页适合回答“多租户怎么做隔离”“为什么会话记忆不能只靠 memoryId”这类问题。");
  finalizeSlide(slide);
}

function slide6Tools() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 6, "Tool 执行型助手：把 AI 接到 ERP 真业务", "让模型不只会答，还能查数据、调服务、走业务流程，同时保留权限和审计边界。");

  addCard(slide, 0.92, 1.72, 3.44, 1.84, "场景升级", "从“问答助手”升级为“能查、能办、能开单”的执行入口，贴近 ERP 真正可落地的价值。", COLORS.coral);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 4.6,
    y: 1.72,
    w: 4.2,
    h: 3.96,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addText("执行链路", {
    x: 4.88,
    y: 2,
    w: 1.1,
    h: 0.18,
    fontFace: FONT_UI,
    fontSize: 12,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  [
    ["自然语言请求", COLORS.blue],
    ["LLM 判断意图", COLORS.teal],
    ["匹配业务 Tool", COLORS.amber],
    ["Service / MyBatis 执行", COLORS.coral],
    ["trace + 结果回填", COLORS.blue],
  ].forEach((step, idx) => {
    const y = 2.38 + idx * 0.58;
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 4.92,
      y,
      w: 3.54,
      h: 0.38,
      rectRadius: 0.05,
      line: { color: step[1], transparency: 100 },
      fill: { color: step[1], transparency: 86 },
    });
    slide.addText(step[0], {
      x: 5.14,
      y: y + 0.1,
      w: 2.96,
      h: 0.14,
      fontFace: FONT_UI,
      fontSize: 10.5,
      bold: idx === 2,
      color: COLORS.ink,
      margin: 0,
    });
    if (idx < 4) {
      slide.addShape(pptx.ShapeType.line, {
        x: 6.68,
        y: y + 0.39,
        w: 0,
        h: 0.19,
        line: { color: COLORS.slate, width: 1.2, beginArrowType: "none", endArrowType: "triangle" },
      });
    }
  });

  addCard(slide, 9.02, 1.72, 3.38, 1.84, "控制栈", "Tool 链路同时接入角色鉴权、监控计量和请求级 trace，保证模型能调的动作和用户有权执行的动作是同一边界。", COLORS.teal);
  addCard(slide, 0.92, 3.84, 3.44, 1.84, "已接入 Tool", "systemManagementTool、materialTool、inventoryBillTool、financeTool、reportTool，共 5 个。", COLORS.blue);
  addCard(slide, 9.02, 3.84, 3.38, 1.84, "下一步演进方向", "把隐式意图路由升级为显式 Intent Router，再配合 slot filling 管理补参过程。", COLORS.amber);

  addFooter(slide, "这一页最适合体现“AI 不是聊天功能，而是能被约束地接进业务体系”。");
  finalizeSlide(slide);
}

function slide7Observability() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 7, "可观测性 + 在线评估", "把每次 AI 请求做成可回放、可分析、可评分的运行画像，而不是只打一行日志。");

  addCard(slide, 0.92, 1.7, 4.28, 1.72, "AiChatObservationService 是总线", "串起 partial response、retrieval、tool、complete、error 等全链路事件，负责 trace 采集与指标发布。", COLORS.blue);
  addCard(slide, 0.92, 3.66, 4.28, 2.34, "结构化请求画像", "requestId、latencyMs、firstTokenLatencyMs、retrievedCount、topRetrievalScore、toolTraces、token 用量等，都会进入请求级上下文。", COLORS.teal);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 5.48,
    y: 1.7,
    w: 6.94,
    h: 4.3,
    rectRadius: 0.06,
    line: { color: COLORS.line, width: 1 },
    fill: { color: COLORS.white },
  });
  slide.addText("在线评估权重", {
    x: 5.76,
    y: 1.96,
    w: 1.5,
    h: 0.2,
    fontFace: FONT_UI,
    fontSize: 12,
    bold: true,
    color: COLORS.ink,
    margin: 0,
  });
  slide.addChart(pptx.ChartType.bar, [
    {
      name: "权重",
      labels: ["执行", "证据", "Tool", "响应"],
      values: [35, 30, 20, 15],
    },
  ], {
    x: 5.82,
    y: 2.34,
    w: 3.32,
    h: 2.86,
    showLegend: false,
    showTitle: false,
    chartColors: [COLORS.blue],
    catAxisLabelFontFace: FONT_UI,
    catAxisLabelFontSize: 11,
    valAxisLabelFontFace: FONT_UI,
    valAxisLabelFontSize: 9,
    valAxisMinVal: 0,
    valAxisMaxVal: 40,
    valAxisMajorUnit: 10,
    valGridLine: { color: COLORS.line, transparency: 0, width: 1 },
    showValue: true,
    dataLabelColor: COLORS.ink,
    dataLabelPosition: "outEnd",
    layout: { x: 0.15, y: 0.12, w: 0.72, h: 0.78 },
  });
  addCard(slide, 9.42, 2.34, 2.72, 1.24, "慢请求阈值", "5000ms", COLORS.amber);
  addCard(slide, 9.42, 3.82, 2.72, 1.24, "响应预览上限", "12000 字符", COLORS.coral);
  addCard(slide, 9.42, 5.04, 2.72, 0.76, "Tool 预览", "400 字符", COLORS.teal);

  addFooter(slide, "面试表达建议：重点不是某个单点指标，而是我把 AI 请求做成了可运营对象。");
  finalizeSlide(slide);
}

function slide8Pitfalls() {
  const slide = pptx.addSlide();
  addSlideHeader(slide, 8, "踩坑与修复：最能体现工程能力的部分", "真正拉开差距的往往不是模型 API，而是系统在异常、编码和构建细节上的稳定性。");

  addCard(slide, 0.92, 1.72, 3.74, 4.44, "Redis 不可用导致 AI 启动失败", "修复思路：向量库初始化失败降级为 InMemoryEmbeddingStore；知识库同步失败只告警不阻塞启动；聊天记忆读写失败退化为非持久化，不打崩主链路。", COLORS.blue);
  addCard(slide, 4.82, 1.72, 3.74, 4.44, "中文乱码与编码环境不统一", "修复思路：固定 UTF-8 编译链路，显式处理 Maven 与 IDE 的编码差异。这个问题虽然不属于算法，但会直接影响团队协作和面试印象。", COLORS.coral);
  addCard(slide, 8.72, 1.72, 3.7, 4.44, "Lombok 在 Maven CLI 下不稳定", "修复思路：把 annotationProcessorPaths 显式配置到 maven-compiler-plugin，而不是默认依赖 IDE 的注解处理器行为。", COLORS.teal);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 1.3,
    y: 5.42,
    w: 10.78,
    h: 0.54,
    rectRadius: 0.05,
    line: { color: COLORS.line, width: 1 },
    fill: { color: "F1F6FD" },
  });
  slide.addText("这类案例特别适合回答“项目里最难的问题是什么、你怎么兜底”的追问，因为它体现的是生产稳定性思维。", {
    x: 1.56,
    y: 5.6,
    w: 10.3,
    h: 0.14,
    fontFace: FONT_UI,
    fontSize: 10.5,
    color: COLORS.subtext,
    margin: 0,
    align: "center",
  });

  addFooter(slide, "把“问题 - 修复 - 价值”讲完整，比空泛地说“做过性能优化”更有说服力。");
  finalizeSlide(slide);
}

function slide9Closing() {
  const slide = pptx.addSlide();
  addBackdrop(slide, "dark");
  addDeckFrame(slide, "dark");

  slide.addText("我的贡献可以收口为四层", {
    x: 0.84,
    y: 0.92,
    w: 3.2,
    h: 0.3,
    fontFace: FONT_UI,
    fontSize: 18,
    bold: true,
    color: "D6E2F6",
    margin: 0,
  });
  slide.addText("让大模型在企业 ERP 场景里稳定工作，而不是只是会聊天。", {
    x: 0.84,
    y: 1.34,
    w: 6.9,
    h: 0.34,
    fontFace: FONT_UI,
    fontSize: 24,
    bold: true,
    color: COLORS.white,
    margin: 0,
  });

  const layers = [
    ["01", "AI 编排", "把模型、提示词、记忆、RAG、Tool 收成一个服务入口。", COLORS.blue],
    ["02", "可信检索", "通过 Strict RAG 做证据过滤和低置信拒答。", COLORS.teal],
    ["03", "执行闭环", "让 AI 能查数据、调 Tool、走业务流程。", COLORS.amber],
    ["04", "可运营能力", "用 trace、评分和指标把 AI 从 Demo 带到可运维状态。", COLORS.coral],
  ];
  layers.forEach((layer, idx) => {
    const x = idx % 2 === 0 ? 0.92 : 6.88;
    const y = idx < 2 ? 2.22 : 4.18;
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y,
      w: 5.42,
      h: 1.46,
      rectRadius: 0.06,
      line: { color: "3C4C6A", width: 1 },
      fill: { color: "132139" },
    });
    slide.addText(layer[0], {
      x: x + 0.22,
      y: y + 0.22,
      w: 0.5,
      h: 0.2,
      fontFace: FONT_UI,
      fontSize: 12,
      bold: true,
      color: layer[3],
      margin: 0,
    });
    slide.addText(layer[1], {
      x: x + 0.86,
      y: y + 0.18,
      w: 1.8,
      h: 0.22,
      fontFace: FONT_UI,
      fontSize: 15,
      bold: true,
      color: COLORS.white,
      margin: 0,
    });
    addFittedText(
      slide,
      layer[2],
      { x: x + 0.86, y: y + 0.52, w: 4.18, h: 0.54 },
      { color: "D6E2F6" },
      { minFontSize: 11, maxFontSize: 12.5, fontSize: 12, leading: 1.13, padding: 0 }
    );
  });

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.92,
    y: 6.2,
    w: 11.38,
    h: 0.7,
    rectRadius: 0.06,
    line: { color: "50617F", width: 1 },
    fill: { color: "101A2A" },
  });
  slide.addText("收口话术：这个项目最能体现我的，不是我接了一个大模型，而是我把大模型真正接进了企业 ERP 业务里，让它可控、可信、可观测、可扩展。", {
    x: 1.18,
    y: 6.43,
    w: 10.86,
    h: 0.16,
    fontFace: FONT_UI,
    fontSize: 11,
    color: COLORS.white,
    margin: 0,
    align: "center",
  });

  addFooter(slide, "结束页可以直接进入问答：比如为什么不用纯 SDK、为什么不是搜到就答、Tool 怎么做鉴权。", "dark");
  finalizeSlide(slide);
}

async function main() {
  slide1Title();
  slide2Overview();
  slide3Orchestration();
  slide4Rag();
  slide5Memory();
  slide6Tools();
  slide7Observability();
  slide8Pitfalls();
  slide9Closing();

  await pptx.writeFile({ fileName: DECK_PATH });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
