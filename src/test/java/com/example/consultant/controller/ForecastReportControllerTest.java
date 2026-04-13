package com.example.consultant.controller;

import com.example.consultant.pojo.ForecastReportRequest;
import com.example.consultant.pojo.TrendForecastResult;
import com.example.consultant.service.ForecastReportExportService;
import com.example.consultant.service.ForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ForecastReportControllerTest {

    @Mock
    private ForecastService forecastService;

    @Mock
    private ForecastReportExportService forecastReportExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ForecastReportController(forecastService, forecastReportExportService)).build();
    }

    @Test
    void shouldReturnExcelAttachment() throws Exception {
        ForecastReportRequest request = new ForecastReportRequest();
        request.setReportKind("business");
        TrendForecastResult result = new TrendForecastResult();
        result.setReportFileName("业务趋势预测_sale_day_2026-04-13.xls");

        when(forecastService.normalizeDownloadRequest("business", "sale", "day", 7, "2026-04-01", "2026-04-08", null))
                .thenReturn(request);
        when(forecastService.forecast(eq(request), eq(null))).thenReturn(result);
        when(forecastReportExportService.exportReport(eq(request), eq(result))).thenReturn("excel".getBytes());

        mockMvc.perform(get("/forecast/report/download")
                        .param("reportKind", "business")
                        .param("metricType", "sale")
                        .param("granularity", "day")
                        .param("periods", "7")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-08"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.ms-excel"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")))
                .andExpect(content().bytes("excel".getBytes()));
    }

    @Test
    void shouldReturnBadRequestWhenRequiredBusinessMetricMissing() throws Exception {
        when(forecastService.normalizeDownloadRequest("business", null, null, null, null, null, null))
                .thenThrow(new IllegalArgumentException("business 预测下载必须提供 metricType"));

        mockMvc.perform(get("/forecast/report/download")
                        .param("reportKind", "business"))
                .andExpect(status().isBadRequest());
    }
}
