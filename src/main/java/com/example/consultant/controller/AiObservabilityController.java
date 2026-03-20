package com.example.consultant.controller;

import com.example.consultant.dto.AiObservationDetail;
import com.example.consultant.dto.AiObservationPage;
import com.example.consultant.dto.AiObservationSummary;
import com.example.consultant.service.AiObservabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/ai/observations")
/**
 * AI 观测查询接口。
 * 这一层只负责把“汇总、分页列表、详情”三类查询能力暴露成后端接口，
 * 不参与实际对话，只用于排查和运营观察。
 */
public class AiObservabilityController {

    private final AiObservabilityService aiObservabilityService;

    public AiObservabilityController(AiObservabilityService aiObservabilityService) {
        this.aiObservabilityService = aiObservabilityService;
    }

    @GetMapping("/summary")
    /**
     * 返回某个时间窗口内的汇总指标。
     * 适合前端做概览卡片，例如总请求数、错误率、平均耗时、P95、平均评分等。
     */
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Map<String, Object> result = new HashMap<>();
        AiObservationSummary summary = aiObservabilityService.summarize(startTime, endTime);
        result.put("code", 200);
        result.put("success", true);
        result.put("result", summary);
        return result;
    }

    @GetMapping
    /**
     * 按条件分页查询请求级 trace。
     * 支持按时间、memoryId、userId、请求状态、风险等级过滤，主要用于问题排查。
     */
    public Map<String, Object> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String memoryId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        AiObservationPage response = aiObservabilityService.findPage(startTime, endTime, memoryId, userId, status, riskLevel, page, size);
        result.put("code", 200);
        result.put("success", true);
        result.put("result", response);
        return result;
    }

    @GetMapping("/{requestId}")
    /**
     * 查询某一次请求的完整详情。
     * 返回主 trace 以及它的工具调用明细，便于前端打开详情抽屉或排障页面。
     */
    public Map<String, Object> detail(@PathVariable String requestId) {
        Map<String, Object> result = new HashMap<>();
        AiObservationDetail detail = aiObservabilityService.findDetail(requestId);
        if (detail == null) {
            result.put("code", 404);
            result.put("success", false);
            result.put("message", "Observation not found");
            return result;
        }
        result.put("code", 200);
        result.put("success", true);
        result.put("result", detail);
        return result;
    }
}
