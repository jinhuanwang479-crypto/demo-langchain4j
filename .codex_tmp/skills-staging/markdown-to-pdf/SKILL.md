---
name: markdown-to-pdf
description: 将 Markdown 文档（`.md`、`.markdown`）转换为适合分享、打印和投递的 PDF 文件。适用于用户要求把 Markdown 笔记、简历、报告、README 或生成内容导出为 PDF，或要求保留标题层级、检查最终排版时。对于以纯文本为主的 Markdown，优先使用内置脚本；如果源文件依赖原生 HTML、复杂 CSS、Mermaid 或严格网页渲染效果，则切换到浏览器打印或 Pandoc 路线。
---

# Markdown 转 PDF

## 概述

把常见的 Markdown 文档转换成结构清晰、可直接交付的 PDF，并在交付前做基本校验。默认使用 `scripts/md_to_pdf.py` 处理以文本为主的 Markdown；如果文档更像网页模板或强依赖 HTML/CSS，就不要硬套脚本，改走浏览器或 Pandoc。

## 工作流

1. 先快速判断 Markdown 类型。
   - 如果内容主要是标题、段落、列表、引用、代码块、链接、本地图片和简单表格，优先使用内置脚本。
   - 如果内容依赖原生 HTML、Mermaid、复杂表格、自定义 CSS、脚注或 GitHub 风格的精确渲染，切换到浏览器打印或 Pandoc。
2. 在写文件前确认输入和输出路径。
   - 保持原始 Markdown 不变。
   - 在仓库里工作时，中间文件可放到 `tmp/markdown-pdf/`。
3. 运行内置脚本。
   ```bash
   python scripts/md_to_pdf.py input.md output.pdf
   ```
4. 重新检查生成的 PDF。
   - 重点看标题层级、段间距、分页、换行和图片缩放。
   - 如果有 `pdftoppm`，优先渲染成 PNG 做肉眼检查。
   - 否则用 `pypdf` 或 `pdfplumber` 做文本级快速核对。
5. 迭代到可交付为止。
   - 优先调整标题、页边距、纸张大小、字体路径。
   - 如果发现当前脚本不是合适的渲染后端，直接换路线，不要继续硬修。

## 内置脚本

默认转换入口是 `scripts/md_to_pdf.py`。

当前支持的内容：
- ATX 标题
- 普通段落
- 有序和无序列表
- 围栏代码块
- 引用块
- 分隔线
- 本地图片
- 简单管道表格
- 行内粗体、斜体、代码和链接

常用命令：

```bash
python scripts/md_to_pdf.py input.md output.pdf --page-size a4
python scripts/md_to_pdf.py input.md output.pdf --title "周报"
python scripts/md_to_pdf.py input.md output.pdf --font-path "C:\\Windows\\Fonts\\msyh.ttc"
```

## 依赖

优先使用 `uv`。

内置脚本必需依赖：

```bash
uv pip install reportlab
```

回退方式：

```bash
python -m pip install reportlab
```

可选工具：
- `pdftoppm`：把 PDF 渲染成图片，便于做视觉检查
- `pypdf` 或 `pdfplumber`：做文本提取和快速校验
- `pandoc` 或浏览器打印：处理重 HTML / CSS 风格的 Markdown

## 质量要求

- 保持标题层级和版面结构清晰。
- 确保 UTF-8 文本可读；如果中文出现方块字，传入 `--font-path` 或切换到真实系统字体。
- 保持代码块可读，避免长行被裁切得难以理解。
- 图片要缩放到页宽内，不要溢出。
- 简单表格要尽量按结构输出，而不是退化成纯文本堆叠。
- 使用内置脚本时，不要承诺与浏览器或 GitHub CSS 完全一致。

## 异常处理

- 如果缺少 `reportlab`，直接安装，或明确告诉用户安装命令。
- 如果相对路径图片不存在，要给出清晰提示，并继续完成其他内容转换。
- 如果 Markdown 的核心价值来自 HTML/CSS 视觉效果，就升级到浏览器或 Pandoc 路线，而不是继续勉强使用内置脚本。
