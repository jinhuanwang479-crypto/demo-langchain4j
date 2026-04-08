#!/usr/bin/env python3
"""
Convert a Markdown file into a PDF using reportlab.

The renderer is intentionally lightweight and aimed at text-first Markdown:
headings, paragraphs, lists, code fences, blockquotes, local images, and
simple pipe tables.
"""

from __future__ import annotations

import argparse
import re
import sys
import textwrap
from dataclasses import dataclass
from html import escape
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, LETTER
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    Image,
    ListFlowable,
    ListItem,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


PAGE_SIZES = {
    "a4": A4,
    "letter": LETTER,
}

HEADING_RE = re.compile(r"^(#{1,6})\s+(.*)$")
FENCE_RE = re.compile(r"^\s*(`{3,}|~{3,})(.*)$")
LIST_RE = re.compile(r"^(\s*)([-+*]|\d+[.)])\s+(.*)$")
RULE_RE = re.compile(r"^\s*([-*_]\s*){3,}\s*$")
BLOCKQUOTE_RE = re.compile(r"^\s*>\s?(.*)$")
IMAGE_RE = re.compile(r"^!\[(.*?)\]\((.*?)\)\s*$")
TABLE_SEPARATOR_RE = re.compile(r"^\s*\|?(\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$")


@dataclass
class HeadingBlock:
    level: int
    text: str


@dataclass
class ParagraphBlock:
    text: str


@dataclass
class ListBlock:
    ordered: bool
    items: list[str]


@dataclass
class CodeBlock:
    text: str
    language: str


@dataclass
class QuoteBlock:
    text: str


@dataclass
class ImageBlock:
    alt: str
    target: str


@dataclass
class TableBlock:
    rows: list[list[str]]


@dataclass
class RuleBlock:
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert Markdown to PDF.")
    parser.add_argument("input", help="Path to the source Markdown file")
    parser.add_argument("output", help="Path to the output PDF file")
    parser.add_argument(
        "--page-size",
        default="a4",
        choices=sorted(PAGE_SIZES),
        help="Target page size",
    )
    parser.add_argument(
        "--margin-mm",
        type=float,
        default=16.0,
        help="Page margins in millimeters",
    )
    parser.add_argument(
        "--font-size",
        type=float,
        default=10.5,
        help="Base body font size",
    )
    parser.add_argument("--title", default="", help="PDF title override")
    parser.add_argument("--author", default="", help="PDF author override")
    parser.add_argument(
        "--font-path",
        default="",
        help="Optional TTF or TTC font path for UTF-8 and CJK text",
    )
    return parser.parse_args()


def parse_front_matter(text: str) -> tuple[dict[str, str], str]:
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}, text

    for index in range(1, min(len(lines), 80)):
        if lines[index].strip() == "---":
            metadata: dict[str, str] = {}
            for line in lines[1:index]:
                if ":" not in line:
                    continue
                key, value = line.split(":", 1)
                metadata[key.strip().lower()] = value.strip().strip("\"'")
            body = "\n".join(lines[index + 1 :])
            return metadata, body
    return {}, text


def choose_font(font_path: str | None) -> tuple[str, str | None]:
    candidates: list[Path] = []
    if font_path:
        candidates.append(Path(font_path))

    candidates.extend(
        [
            Path(r"C:\Windows\Fonts\msyh.ttc"),
            Path(r"C:\Windows\Fonts\msyh.ttf"),
            Path(r"C:\Windows\Fonts\simhei.ttf"),
            Path(r"C:\Windows\Fonts\simsun.ttc"),
            Path("/System/Library/Fonts/PingFang.ttc"),
            Path("/System/Library/Fonts/Hiragino Sans GB.ttc"),
            Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
            Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
            Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"),
        ]
    )

    seen: set[Path] = set()
    for candidate in candidates:
        if candidate in seen or not candidate.exists():
            continue
        seen.add(candidate)
        try:
            pdfmetrics.registerFont(
                TTFont("DocFont", str(candidate), subfontIndex=0)
            )
            pdfmetrics.registerFontFamily(
                "DocFont",
                normal="DocFont",
                bold="DocFont",
                italic="DocFont",
                boldItalic="DocFont",
            )
            return "DocFont", str(candidate)
        except Exception:
            continue

    return "Helvetica", None


def contains_non_ascii(text: str) -> bool:
    return any(ord(char) > 127 for char in text)


def make_styles(font_name: str, font_size: float) -> dict[str, ParagraphStyle]:
    sample = getSampleStyleSheet()
    body = ParagraphStyle(
        "Body",
        parent=sample["BodyText"],
        fontName=font_name,
        fontSize=font_size,
        leading=font_size * 1.55,
        spaceAfter=6,
        wordWrap="CJK",
    )
    code = ParagraphStyle(
        "Code",
        parent=body,
        fontName=font_name,
        fontSize=max(font_size - 1, 8),
        leading=max(font_size + 1, 10),
        leftIndent=6,
        rightIndent=6,
        spaceBefore=2,
        spaceAfter=2,
    )
    quote = ParagraphStyle(
        "Quote",
        parent=body,
        leftIndent=12,
        rightIndent=6,
        textColor=colors.HexColor("#4b5563"),
        borderColor=colors.HexColor("#d1d5db"),
        borderPadding=6,
        borderWidth=0.75,
        borderLeft=True,
    )
    styles = {
        "body": body,
        "code": code,
        "quote": quote,
    }
    for level in range(1, 7):
        styles[f"h{level}"] = ParagraphStyle(
            f"Heading{level}",
            parent=body,
            fontName=font_name,
            fontSize=max(font_size + (16 - level * 2), font_size + 1),
            leading=max(font_size + (20 - level * 2), font_size + 3),
            spaceBefore=14 if level <= 2 else 10,
            spaceAfter=8 if level <= 2 else 6,
        )
    return styles


def is_table_start(lines: list[str], index: int) -> bool:
    if index + 1 >= len(lines):
        return False
    return "|" in lines[index] and TABLE_SEPARATOR_RE.match(lines[index + 1]) is not None


def is_block_start(lines: list[str], index: int) -> bool:
    stripped = lines[index].strip()
    if not stripped:
        return True
    if FENCE_RE.match(stripped):
        return True
    if HEADING_RE.match(stripped):
        return True
    if LIST_RE.match(lines[index]):
        return True
    if RULE_RE.match(stripped):
        return True
    if BLOCKQUOTE_RE.match(lines[index]):
        return True
    if IMAGE_RE.match(stripped):
        return True
    return is_table_start(lines, index)


def parse_table_row(line: str) -> list[str]:
    raw = line.strip().strip("|")
    return [cell.strip() for cell in raw.split("|")]


def collect_code_block(lines: list[str], index: int) -> tuple[CodeBlock, int]:
    match = FENCE_RE.match(lines[index].strip())
    assert match is not None
    fence = match.group(1)
    language = match.group(2).strip()
    index += 1
    collected: list[str] = []
    while index < len(lines):
        current = lines[index]
        if current.strip().startswith(fence):
            index += 1
            break
        collected.append(current.rstrip("\n"))
        index += 1
    return CodeBlock("\n".join(collected), language), index


def collect_list(lines: list[str], index: int) -> tuple[ListBlock, int]:
    first = LIST_RE.match(lines[index])
    assert first is not None
    ordered = first.group(2)[0].isdigit()
    items: list[str] = []

    while index < len(lines):
        match = LIST_RE.match(lines[index])
        if not match:
            break
        current_ordered = match.group(2)[0].isdigit()
        if current_ordered != ordered:
            break

        item_parts = [match.group(3).strip()]
        index += 1

        while index < len(lines):
            current = lines[index]
            stripped = current.strip()
            if not stripped:
                break
            if LIST_RE.match(current):
                break
            if is_block_start(lines, index):
                break
            if current.startswith("  ") or current.startswith("\t"):
                item_parts.append(stripped)
                index += 1
                continue
            break

        items.append(" ".join(part for part in item_parts if part))

        while index < len(lines) and not lines[index].strip():
            index += 1

    return ListBlock(ordered, items), index


def collect_quote(lines: list[str], index: int) -> tuple[QuoteBlock, int]:
    chunks: list[str] = []
    while index < len(lines):
        match = BLOCKQUOTE_RE.match(lines[index])
        if not match:
            break
        chunks.append(match.group(1).strip())
        index += 1
    return QuoteBlock(" ".join(part for part in chunks if part)), index


def collect_table(lines: list[str], index: int) -> tuple[TableBlock, int]:
    rows: list[list[str]] = [parse_table_row(lines[index])]
    index += 2
    while index < len(lines):
        current = lines[index].strip()
        if not current or "|" not in current:
            break
        rows.append(parse_table_row(lines[index]))
        index += 1
    width = max(len(row) for row in rows)
    normalized = [row + [""] * (width - len(row)) for row in rows]
    return TableBlock(normalized), index


def collect_paragraph(lines: list[str], index: int) -> tuple[ParagraphBlock, int]:
    chunks: list[str] = []
    while index < len(lines):
        stripped = lines[index].strip()
        if not stripped:
            break
        if chunks and is_block_start(lines, index):
            break
        chunks.append(stripped)
        index += 1
    return ParagraphBlock(" ".join(chunks)), index


def parse_markdown(text: str) -> list[object]:
    lines = text.splitlines()
    blocks: list[object] = []
    index = 0

    while index < len(lines):
        stripped = lines[index].strip()
        if not stripped:
            index += 1
            continue

        if FENCE_RE.match(stripped):
            block, index = collect_code_block(lines, index)
            blocks.append(block)
            continue

        heading = HEADING_RE.match(stripped)
        if heading:
            blocks.append(HeadingBlock(len(heading.group(1)), heading.group(2).strip()))
            index += 1
            continue

        if RULE_RE.match(stripped):
            blocks.append(RuleBlock())
            index += 1
            continue

        image = IMAGE_RE.match(stripped)
        if image:
            blocks.append(ImageBlock(image.group(1).strip(), image.group(2).strip()))
            index += 1
            continue

        if BLOCKQUOTE_RE.match(lines[index]):
            block, index = collect_quote(lines, index)
            blocks.append(block)
            continue

        if is_table_start(lines, index):
            block, index = collect_table(lines, index)
            blocks.append(block)
            continue

        if LIST_RE.match(lines[index]):
            block, index = collect_list(lines, index)
            blocks.append(block)
            continue

        block, index = collect_paragraph(lines, index)
        blocks.append(block)

    return blocks


def render_inline(text: str, code_font_name: str) -> str:
    rendered = escape(text)
    rendered = re.sub(
        r"!\[([^\]]*)\]\(([^)]+)\)",
        lambda match: f"[Image: {match.group(1) or match.group(2)}]",
        rendered,
    )
    rendered = re.sub(
        r"(?<!!)\[([^\]]+)\]\(([^)]+)\)",
        lambda match: (
            f'<link href="{match.group(2).replace(chr(34), "&quot;")}">'
            f"{match.group(1)}</link>"
        ),
        rendered,
    )
    rendered = re.sub(
        r"`([^`\n]+)`",
        lambda match: f'<font face="{code_font_name}">{match.group(1)}</font>',
        rendered,
    )
    rendered = re.sub(r"\*\*([^*\n]+)\*\*", r"<b>\1</b>", rendered)
    rendered = re.sub(r"__([^_\n]+)__", r"<b>\1</b>", rendered)
    rendered = re.sub(r"(?<!\*)\*([^*\n]+)\*(?!\*)", r"<i>\1</i>", rendered)
    rendered = re.sub(r"(?<!_)_([^_\n]+)_(?!_)", r"<i>\1</i>", rendered)
    return rendered


def wrap_code_block(text: str, width: int = 90) -> str:
    wrapped_lines: list[str] = []
    for line in text.splitlines() or [""]:
        if not line:
            wrapped_lines.append("")
            continue
        wrapped_lines.extend(
            textwrap.wrap(
                line,
                width=width,
                replace_whitespace=False,
                drop_whitespace=False,
            )
            or [""]
        )
    return "\n".join(wrapped_lines)


def resolve_image(base_dir: Path, target: str) -> Path | None:
    if target.startswith(("http://", "https://")):
        return None
    candidate = (base_dir / target).resolve()
    if candidate.exists():
        return candidate
    return None


def build_story(
    blocks: list[object],
    doc: SimpleDocTemplate,
    styles: dict[str, ParagraphStyle],
    base_dir: Path,
    code_font_name: str,
) -> tuple[list[object], list[str]]:
    story: list[object] = []
    warnings: list[str] = []

    for block in blocks:
        if isinstance(block, HeadingBlock):
            story.append(
                Paragraph(
                    render_inline(block.text, code_font_name),
                    styles[f"h{min(block.level, 6)}"],
                )
            )
            continue

        if isinstance(block, ParagraphBlock):
            story.append(
                Paragraph(render_inline(block.text, code_font_name), styles["body"])
            )
            continue

        if isinstance(block, QuoteBlock):
            story.append(
                Paragraph(render_inline(block.text, code_font_name), styles["quote"])
            )
            story.append(Spacer(1, 2))
            continue

        if isinstance(block, ListBlock):
            list_items = [
                ListItem(
                    Paragraph(render_inline(item, code_font_name), styles["body"])
                )
                for item in block.items
            ]
            story.append(
                ListFlowable(
                    list_items,
                    bulletType="1" if block.ordered else "bullet",
                    leftIndent=18,
                )
            )
            story.append(Spacer(1, 4))
            continue

        if isinstance(block, CodeBlock):
            code_text = wrap_code_block(block.text)
            code_table = Table(
                [[Preformatted(code_text, styles["code"])]],
                colWidths=[doc.width],
            )
            code_table.setStyle(
                TableStyle(
                    [
                        ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f3f4f6")),
                        ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#d1d5db")),
                        ("LEFTPADDING", (0, 0), (-1, -1), 6),
                        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                        ("TOPPADDING", (0, 0), (-1, -1), 6),
                        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                    ]
                )
            )
            story.append(code_table)
            story.append(Spacer(1, 6))
            continue

        if isinstance(block, ImageBlock):
            image_path = resolve_image(base_dir, block.target)
            if image_path is None:
                warnings.append(f"Missing or unsupported image: {block.target}")
                story.append(
                    Paragraph(
                        f"[Missing image: {escape(block.target)}]",
                        styles["quote"],
                    )
                )
                continue

            reader = ImageReader(str(image_path))
            width, height = reader.getSize()
            max_width = doc.width
            max_height = doc.height * 0.35
            scale = min(max_width / width, max_height / height, 1.0)
            image = Image(str(image_path), width=width * scale, height=height * scale)
            story.append(image)
            if block.alt:
                story.append(
                    Paragraph(
                        render_inline(block.alt, code_font_name),
                        styles["quote"],
                    )
                )
            story.append(Spacer(1, 6))
            continue

        if isinstance(block, TableBlock):
            column_count = max(len(row) for row in block.rows)
            rows = [row + [""] * (column_count - len(row)) for row in block.rows]
            table_data = [
                [
                    Paragraph(render_inline(cell, code_font_name), styles["body"])
                    for cell in row
                ]
                for row in rows
            ]
            table = Table(
                table_data,
                colWidths=[doc.width / column_count] * column_count,
                repeatRows=1,
            )
            table.setStyle(
                TableStyle(
                    [
                        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e5e7eb")),
                        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cbd5e1")),
                        ("LEFTPADDING", (0, 0), (-1, -1), 6),
                        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                        ("TOPPADDING", (0, 0), (-1, -1), 4),
                        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
                        ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ]
                )
            )
            story.append(table)
            story.append(Spacer(1, 6))
            continue

        if isinstance(block, RuleBlock):
            story.append(
                HRFlowable(
                    width="100%",
                    thickness=0.6,
                    color=colors.HexColor("#cbd5e1"),
                    spaceBefore=6,
                    spaceAfter=8,
                )
            )

    return story, warnings


def add_page_decorations(title: str, author: str, font_name: str, font_size: float):
    def _decorate(canvas, doc):
        canvas.setTitle(title or "Markdown PDF")
        if author:
            canvas.setAuthor(author)
        canvas.saveState()
        canvas.setFont(font_name, max(font_size - 1, 8))
        canvas.setFillColor(colors.HexColor("#6b7280"))
        canvas.drawRightString(
            doc.pagesize[0] - doc.rightMargin,
            10 * mm,
            str(canvas.getPageNumber()),
        )
        canvas.restoreState()

    return _decorate


def main() -> int:
    args = parse_args()
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()

    if not input_path.exists():
        print(f"Input file not found: {input_path}", file=sys.stderr)
        return 1

    output_path.parent.mkdir(parents=True, exist_ok=True)

    raw_text = input_path.read_text(encoding="utf-8-sig")
    metadata, markdown_body = parse_front_matter(raw_text)
    title = args.title or metadata.get("title", "") or input_path.stem
    author = args.author or metadata.get("author", "")

    font_name, chosen_font = choose_font(args.font_path or None)
    if chosen_font:
        print(f"Using font: {chosen_font}")
    elif contains_non_ascii(markdown_body):
        print(
            "Warning: no UTF-8/CJK font found; non-Latin characters may not render correctly.",
            file=sys.stderr,
        )

    styles = make_styles(font_name, args.font_size)
    page_size = PAGE_SIZES[args.page_size.lower()]
    margin = args.margin_mm * mm

    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=page_size,
        leftMargin=margin,
        rightMargin=margin,
        topMargin=margin,
        bottomMargin=margin,
        title=title,
        author=author,
    )

    blocks = parse_markdown(markdown_body)
    story, warnings = build_story(
        blocks=blocks,
        doc=doc,
        styles=styles,
        base_dir=input_path.parent,
        code_font_name=font_name,
    )

    doc.build(
        story,
        onFirstPage=add_page_decorations(title, author, font_name, args.font_size),
        onLaterPages=add_page_decorations(title, author, font_name, args.font_size),
    )

    for warning in warnings:
        print(f"Warning: {warning}", file=sys.stderr)

    print(f"Wrote PDF: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
