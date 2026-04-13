package com.example.consultant.controller;

import com.example.consultant.pojo.ForecastReportRequest;
import com.example.consultant.pojo.TrendForecastResult;
import com.example.consultant.service.ForecastReportExportService;
import com.example.consultant.service.ForecastService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/forecast/report")
public class ForecastReportController {

    private final ForecastService forecastService;
    private final ForecastReportExportService forecastReportExportService;

    public ForecastReportController(ForecastService forecastService,
                                    ForecastReportExportService forecastReportExportService) {
        this.forecastService = forecastService;
        this.forecastReportExportService = forecastReportExportService;
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport(@RequestParam String reportKind,
                                                 @RequestParam(required = false) String metricType,
                                                 @RequestParam(required = false) String granularity,
                                                 @RequestParam(required = false) Integer periods,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) String materialKeyword) {
        ForecastReportRequest request;
        try {
            request = forecastService.normalizeDownloadRequest(
                    reportKind, metricType, granularity, periods, startDate, endDate, materialKeyword);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        }

        TrendForecastResult result = forecastService.forecast(request, null);
        byte[] bytes = forecastReportExportService.exportReport(request, result);
        String fileName = result.getReportFileName();
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .body(bytes);
    }
}
