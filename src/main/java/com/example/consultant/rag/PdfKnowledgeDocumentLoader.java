package com.example.consultant.rag;

import com.example.consultant.config.AiRagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Component
public class PdfKnowledgeDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(PdfKnowledgeDocumentLoader.class);
    private static final Pattern PAGE_NUMBER_ONLY = Pattern.compile("^page\\s*\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern STEP_LINE = Pattern.compile("^(第[一二三四五六七八九十百千0-9]+[章节部分]|[0-9]+[.)、].*|[一二三四五六七八九十]+、.*)$");

    private final AiRagProperties aiRagProperties;
    private final AtomicBoolean ocrAvailable = new AtomicBoolean(true);

    public PdfKnowledgeDocumentLoader(AiRagProperties aiRagProperties) {
        this.aiRagProperties = aiRagProperties;
    }

    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    public List<TextSegment> load(byte[] pdfBytes, String docId, String docName, String docSha256) {
        List<TextSegment> segments = new ArrayList<>();
        DocumentSplitter splitter = DocumentSplitters.recursive(
                aiRagProperties.getChunk().getMaxSegmentSize(),
                aiRagProperties.getChunk().getOverlap()
        );

        try (PDDocument pdDocument = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            List<String> pageTexts = extractPageTexts(pdDocument, stripper, renderer, docName);

            int pageWindow = Math.max(1, aiRagProperties.getChunk().getPageWindow());
            for (int start = 0; start < pageTexts.size(); start += pageWindow) {
                String windowText = buildPageWindow(pageTexts, start, pageWindow);
                if (!StringUtils.hasText(windowText)) {
                    continue;
                }

                Metadata metadata = new Metadata()
                        .put("docId", docId)
                        .put("docName", docName)
                        .put("docSha256", docSha256)
                        .put("pageNumber", start + 1)
                        .put("sourceType", "pdf");

                List<TextSegment> splitSegments = splitter.split(Document.from(windowText, metadata));
                for (int i = 0; i < splitSegments.size(); i++) {
                    TextSegment splitSegment = splitSegments.get(i);
                    Metadata segmentMetadata = splitSegment.metadata() == null ? metadata.copy() : splitSegment.metadata().copy();
                    segmentMetadata.put("chunkIndex", i);
                    segments.add(TextSegment.from(splitSegment.text(), segmentMetadata));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse PDF: " + docName, ex);
        }
        return segments;
    }

    private List<String> extractPageTexts(PDDocument pdDocument,
                                          PDFTextStripper stripper,
                                          PDFRenderer renderer,
                                          String docName) throws IOException {
        List<String> pageTexts = new ArrayList<>(pdDocument.getNumberOfPages());
        for (int pageIndex = 0; pageIndex < pdDocument.getNumberOfPages(); pageIndex++) {
            String extracted = extractPageText(stripper, pdDocument, pageIndex + 1);
            //- 去掉空行
            //- 去掉纯页码、纯数字噪声行
            //- 合并多余空白
            //- 根据标点和步骤行规则，决定是否保留换行
            String cleaned = cleanPageText(extracted);
            if (shouldTryOcr(cleaned)) {
                String ocrText = tryOcr(renderer, pageIndex, docName);
                if (StringUtils.hasText(ocrText)) {
                    String cleanedOcr = cleanPageText(ocrText);
                    if (isBetterThanExtracted(cleanedOcr, cleaned)) {
                        cleaned = cleanedOcr;
                    }
                }
            }
            pageTexts.add(cleaned);
        }
        return pageTexts;
    }

    private String extractPageText(PDFTextStripper stripper, PDDocument document, int pageNumber) throws IOException {
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(document);
    }

    private boolean shouldTryOcr(String text) {
        AiRagProperties.Pdf pdf = aiRagProperties.getPdf();
        if (!pdf.isOcrEnabled() || !ocrAvailable.get()) {
            return false;
        }
        if (!isTesseractConfigured(pdf)) {
            return false;
        }
        if (!StringUtils.hasText(text)) {
            return true;
        }
        TextQuality quality = analyzeText(text);
        return quality.textLength < pdf.getMinTextLength()
                || quality.chineseRatio < pdf.getMinChineseRatio()
                || quality.replacementRatio > pdf.getMaxReplacementCharRatio();
    }

    private boolean isTesseractConfigured(AiRagProperties.Pdf pdf) {
        String dataPath = pdf.getTesseractDataPath();
        if (!StringUtils.hasText(dataPath)) {
            return false;
        }
        try {
            return Files.isDirectory(Path.of(dataPath));
        } catch (RuntimeException ex) {
            log.warn("Invalid tesseract data path: {}", dataPath);
            return false;
        }
    }

    private String tryOcr(PDFRenderer renderer, int pageIndex, String docName) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, aiRagProperties.getPdf().getRenderDpi());
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(aiRagProperties.getPdf().getTesseractDataPath());
            tesseract.setLanguage(aiRagProperties.getPdf().getOcrLanguage());
            return tesseract.doOCR(image);
        } catch (TesseractException | RuntimeException | IOException ex) {
            log.warn("PDF OCR fallback failed: docName={}, pageNumber={}, reason={}",
                    docName, pageIndex + 1, ex.getMessage());
            return null;
        } catch (Throwable ex) {
            ocrAvailable.set(false);
            log.error("PDF OCR disabled for current process: docName={}, pageNumber={}, reason={}",
                    docName, pageIndex + 1, ex.toString());
            return null;
        }
    }

    private boolean isBetterThanExtracted(String ocrText, String extractedText) {
        TextQuality ocrQuality = analyzeText(ocrText);
        TextQuality extractedQuality = analyzeText(extractedText);
        return ocrQuality.textLength > extractedQuality.textLength
                && ocrQuality.chineseRatio >= extractedQuality.chineseRatio
                && ocrQuality.replacementRatio <= extractedQuality.replacementRatio;
    }

    private TextQuality analyzeText(String text) {
        if (!StringUtils.hasText(text)) {
            return new TextQuality(0, 0D, 1D);
        }
        int chineseCount = 0;
        int replacementCount = 0;
        int visibleCount = 0;
        for (char c : text.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                visibleCount++;
            }
            if (isChinese(c)) {
                chineseCount++;
            }
            if (c == '\uFFFD') {
                replacementCount++;
            }
        }
        if (visibleCount == 0) {
            return new TextQuality(0, 0D, 1D);
        }
        return new TextQuality(
                visibleCount,
                chineseCount / (double) visibleCount,
                replacementCount / (double) visibleCount
        );
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private String cleanPageText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String normalized = rawText.replace('\u0000', ' ').replace("\r", "\n");
        String[] lines = normalized.split("\n");
        List<String> cleanedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!StringUtils.hasText(trimmed) || isNoiseLine(trimmed)) {
                continue;
            }
            cleanedLines.add(trimmed.replaceAll("\\s{2,}", " "));
        }

        StringBuilder builder = new StringBuilder();
        for (String line : cleanedLines) {
            appendMergedLine(builder, line);
        }
        return builder.toString().trim();
    }

    private boolean isNoiseLine(String line) {
        return PAGE_NUMBER_ONLY.matcher(line).matches() || NUMERIC_ONLY.matcher(line).matches();
    }

    private void appendMergedLine(StringBuilder builder, String currentLine) {
        if (builder.length() == 0) {
            builder.append(currentLine);
            return;
        }

        char lastChar = builder.charAt(builder.length() - 1);
        boolean keepLineBreak = lastChar == '。'
                || lastChar == '：'
                || lastChar == ':'
                || lastChar == '；'
                || lastChar == ';'
                || STEP_LINE.matcher(currentLine).matches();

        if (keepLineBreak) {
            builder.append('\n');
        } else {
            builder.append(' ');
        }
        builder.append(currentLine);
    }

    private String buildPageWindow(List<String> pageTexts, int start, int pageWindow) {
        StringBuilder builder = new StringBuilder();
        int end = Math.min(pageTexts.size(), start + pageWindow);
        for (int i = start; i < end; i++) {
            String pageText = pageTexts.get(i);
            if (!StringUtils.hasText(pageText)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("第").append(i + 1).append("页").append('\n').append(pageText);
        }
        return builder.toString();
    }

    private record TextQuality(int textLength, double chineseRatio, double replacementRatio) {
    }
}
