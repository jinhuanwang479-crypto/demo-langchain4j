package com.example.consultant.service;

import com.example.consultant.mapper.MaterialMapper;
import com.example.consultant.mapper.ReportMapper;
import com.example.consultant.pojo.MaterialInfo;
import com.example.consultant.pojo.TrendForecastResult;
import com.example.consultant.pojo.TrendSeriesPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private MaterialMapper materialMapper;

    @InjectMocks
    private ForecastService forecastService;

    @Test
    void businessForecastShouldIncludeDownloadMetadata() {
        when(reportMapper.getBusinessTrendSeriesByDay(any(), any(), any(), anyLong())).thenReturn(sampleSeries());

        TrendForecastResult result = forecastService.forecastBusinessTrend(
                "sale", "day", 5, "2026-04-01", "2026-04-08", 160L);

        assertThat(result.getDownloadUrl()).startsWith("/new-backend-ai/forecast/report/download?");
        assertThat(result.getDownloadUrl()).contains("reportKind=business");
        assertThat(result.getDownloadUrl()).contains("metricType=sale");
        assertThat(result.getReportTitle()).isEqualTo("业务趋势预测报告");
        assertThat(result.getReportFileName()).startsWith("业务趋势预测_sale_day_").endsWith(".xls");
    }

    @Test
    void materialForecastShouldEncodeKeywordInDownloadUrl() {
        MaterialInfo material = new MaterialInfo();
        material.setId(1001L);
        material.setName("智能办公本Pro");
        when(materialMapper.searchMaterials(eq("智能 办公&本"), eq(null), anyLong(), eq(1))).thenReturn(List.of(material));
        when(reportMapper.getMaterialDemandSeriesByDay(eq(1001L), any(), any(), anyLong())).thenReturn(sampleSeries());

        TrendForecastResult result = forecastService.forecastMaterialDemand(
                "智能 办公&本", "day", 4, "2026-04-01", "2026-04-08", 160L);

        assertThat(result.getResolvedMaterialName()).isEqualTo("智能办公本Pro");
        assertThat(result.getReportTitle()).isEqualTo("商品销量预测报告（智能办公本Pro）");
        assertThat(result.getReportFileName()).startsWith("商品销量预测_智能办公本Pro_").endsWith(".xls");
        assertThat(result.getDownloadUrl()).contains("reportKind=material");
        assertThat(result.getDownloadUrl()).contains("materialKeyword=" + UriUtils.encodeQueryParam("智能 办公&本", StandardCharsets.UTF_8));
    }

    @Test
    void materialForecastShouldStillExposeDownloadMetadataWhenNoMaterialMatched() {
        when(materialMapper.searchMaterials(anyString(), eq(null), anyLong(), eq(1))).thenReturn(List.of());

        TrendForecastResult result = forecastService.forecastMaterialDemand(
                "未命中商品", "week", 4, "2026-03-01", "2026-04-05", 160L);

        assertThat(result.getWarning()).contains("未找到匹配商品");
        assertThat(result.getDownloadUrl()).contains("reportKind=material");
        assertThat(result.getDownloadUrl()).contains("materialKeyword=" + UriUtils.encodeQueryParam("未命中商品", StandardCharsets.UTF_8));
        assertThat(result.getReportFileName()).startsWith("商品销量预测_未命中商品_").endsWith(".xls");
    }

    @Test
    void purchaseForecastShouldUsePositiveAmountsForPrediction() {
        when(reportMapper.getBusinessTrendSeriesByDay(any(), any(), any(), anyLong())).thenReturn(List.of(
                point("2026-04-01", -100),
                point("2026-04-02", -120),
                point("2026-04-03", -140),
                point("2026-04-04", -160),
                point("2026-04-05", -180),
                point("2026-04-06", -200),
                point("2026-04-07", -220),
                point("2026-04-08", -240)
        ));

        TrendForecastResult result = forecastService.forecastBusinessTrend(
                "purchase", "day", 3, "2026-04-01", "2026-04-08", 160L);

        assertThat(result.getHistoryPoints()).allMatch(point -> point.getValue().signum() >= 0);
        assertThat(result.getRecentAverage()).isEqualByComparingTo("220.00");
        assertThat(result.getForecastAverage()).isPositive();
    }

    private List<TrendSeriesPoint> sampleSeries() {
        return List.of(
                point("2026-04-01", 120),
                point("2026-04-02", 135),
                point("2026-04-03", 128),
                point("2026-04-04", 140),
                point("2026-04-05", 150),
                point("2026-04-06", 155),
                point("2026-04-07", 160),
                point("2026-04-08", 168)
        );
    }

    private TrendSeriesPoint point(String date, int amount) {
        TrendSeriesPoint point = new TrendSeriesPoint();
        point.setPeriodLabel(date);
        point.setAmount(BigDecimal.valueOf(amount));
        point.setRecordCount(1);
        return point;
    }
}
