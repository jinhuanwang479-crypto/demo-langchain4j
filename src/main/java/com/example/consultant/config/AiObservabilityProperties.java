package com.example.consultant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.observability")
/**
 * AI 监测与评估配置。
 * 这一组配置用于控制“是否开启监测、是否持久化 trace、是否执行在线评估、
 * 慢请求阈值、前端汇总窗口默认值、结果预览截断长度”等行为。
 */
public class AiObservabilityProperties {

    /** 是否整体开启 AI 监测能力。 */
    private boolean enabled = true;
    /** 是否将请求级 trace 与工具调用明细落库。 */
    private boolean tracePersistenceEnabled = true;
    /** 是否在回答完成后执行规则化在线评估。 */
    private boolean evalEnabled = true;
    /** 慢请求阈值，超过该值会额外计入慢请求指标。 */
    private long slowThresholdMs = 5000L;
    /** 汇总接口默认统计最近多少小时的数据。 */
    private int defaultSummaryWindowHours = 24;
    /** 工具调用结果预览的最大保留长度，避免把超长结果直接写入数据库。 */
    private int maxToolResultPreviewLength = 4000;
    /** AI 最终回答预览的最大保留长度，避免 trace 表无限膨胀。 */
    private int maxResponsePreviewLength = 12000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTracePersistenceEnabled() {
        return tracePersistenceEnabled;
    }

    public void setTracePersistenceEnabled(boolean tracePersistenceEnabled) {
        this.tracePersistenceEnabled = tracePersistenceEnabled;
    }

    public boolean isEvalEnabled() {
        return evalEnabled;
    }

    public void setEvalEnabled(boolean evalEnabled) {
        this.evalEnabled = evalEnabled;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    public int getDefaultSummaryWindowHours() {
        return defaultSummaryWindowHours;
    }

    public void setDefaultSummaryWindowHours(int defaultSummaryWindowHours) {
        this.defaultSummaryWindowHours = defaultSummaryWindowHours;
    }

    public int getMaxToolResultPreviewLength() {
        return maxToolResultPreviewLength;
    }

    public void setMaxToolResultPreviewLength(int maxToolResultPreviewLength) {
        this.maxToolResultPreviewLength = maxToolResultPreviewLength;
    }

    public int getMaxResponsePreviewLength() {
        return maxResponsePreviewLength;
    }

    public void setMaxResponsePreviewLength(int maxResponsePreviewLength) {
        this.maxResponsePreviewLength = maxResponsePreviewLength;
    }
}
