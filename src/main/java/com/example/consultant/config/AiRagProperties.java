package com.example.consultant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.rag")
public class AiRagProperties {

    private String resourcePattern = "classpath*:content/*";
    private String indexName = "erp-kb-index-v1";
    private String vectorPrefix = "erp:kb:v1:segment:";
    private String manifestKey = "erp:kb:v1:manifest";
    private final Retrieval retrieval = new Retrieval();
    private final Chunk chunk = new Chunk();
    private final Ingestion ingestion = new Ingestion();
    private final Pdf pdf = new Pdf();

    public String getResourcePattern() {
        return resourcePattern;
    }

    public void setResourcePattern(String resourcePattern) {
        this.resourcePattern = resourcePattern;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getVectorPrefix() {
        return vectorPrefix;
    }

    public void setVectorPrefix(String vectorPrefix) {
        this.vectorPrefix = vectorPrefix;
    }

    public String getManifestKey() {
        return manifestKey;
    }

    public void setManifestKey(String manifestKey) {
        this.manifestKey = manifestKey;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Pdf getPdf() {
        return pdf;
    }

    public static class Retrieval {
        private double minScore = 0.68D;
        private int maxResults = 4;
        private double answerableMinScore = 0.74D;
        private int minSegmentLength = 40;

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public double getAnswerableMinScore() {
            return answerableMinScore;
        }

        public void setAnswerableMinScore(double answerableMinScore) {
            this.answerableMinScore = answerableMinScore;
        }

        public int getMinSegmentLength() {
            return minSegmentLength;
        }

        public void setMinSegmentLength(int minSegmentLength) {
            this.minSegmentLength = minSegmentLength;
        }
    }

    public static class Chunk {
        private int pageWindow = 1;
        private int maxSegmentSize = 350;
        private int overlap = 60;

        public int getPageWindow() {
            return pageWindow;
        }

        public void setPageWindow(int pageWindow) {
            this.pageWindow = pageWindow;
        }

        public int getMaxSegmentSize() {
            return maxSegmentSize;
        }

        public void setMaxSegmentSize(int maxSegmentSize) {
            this.maxSegmentSize = maxSegmentSize;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    public static class Ingestion {
        private boolean autoSyncOnStartup = true;

        public boolean isAutoSyncOnStartup() {
            return autoSyncOnStartup;
        }

        public void setAutoSyncOnStartup(boolean autoSyncOnStartup) {
            this.autoSyncOnStartup = autoSyncOnStartup;
        }
    }

    public static class Pdf {
        private boolean ocrEnabled = false;
        private String ocrLanguage = "chi_sim+eng";
        private String tesseractDataPath;
        private int minTextLength = 80;
        private double minChineseRatio = 0.15D;
        private double maxReplacementCharRatio = 0.10D;
        private int renderDpi = 200;

        public boolean isOcrEnabled() {
            return ocrEnabled;
        }

        public void setOcrEnabled(boolean ocrEnabled) {
            this.ocrEnabled = ocrEnabled;
        }

        public String getOcrLanguage() {
            return ocrLanguage;
        }

        public void setOcrLanguage(String ocrLanguage) {
            this.ocrLanguage = ocrLanguage;
        }

        public String getTesseractDataPath() {
            return tesseractDataPath;
        }

        public void setTesseractDataPath(String tesseractDataPath) {
            this.tesseractDataPath = tesseractDataPath;
        }

        public int getMinTextLength() {
            return minTextLength;
        }

        public void setMinTextLength(int minTextLength) {
            this.minTextLength = minTextLength;
        }

        public double getMinChineseRatio() {
            return minChineseRatio;
        }

        public void setMinChineseRatio(double minChineseRatio) {
            this.minChineseRatio = minChineseRatio;
        }

        public double getMaxReplacementCharRatio() {
            return maxReplacementCharRatio;
        }

        public void setMaxReplacementCharRatio(double maxReplacementCharRatio) {
            this.maxReplacementCharRatio = maxReplacementCharRatio;
        }

        public int getRenderDpi() {
            return renderDpi;
        }

        public void setRenderDpi(int renderDpi) {
            this.renderDpi = renderDpi;
        }
    }
}
