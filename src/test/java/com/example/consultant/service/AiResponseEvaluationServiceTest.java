package com.example.consultant.service;

import com.example.consultant.config.AiObservabilityProperties;
import com.example.consultant.config.AiRagProperties;
import com.example.consultant.pojo.AiEvaluationResult;
import com.example.consultant.pojo.AiRequestTrace;
import com.example.consultant.pojo.AiToolTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResponseEvaluationServiceTest {

    private AiResponseEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new AiResponseEvaluationService(new AiRagProperties(), new AiObservabilityProperties());
    }

    @Test
    void shouldPassWhenGroundedAnswerCompletesNormally() {
        AiRequestTrace trace = baseTrace();
        trace.setRetrievedCount(2);
        trace.setTopRetrievalScore(0.91D);

        AiEvaluationResult result = evaluationService.evaluate(trace, List.of());

        assertEquals("PASS", result.getStatus());
        assertEquals("LOW", result.getRiskLevel());
        assertTrue(result.getOverallScore() >= 80);
    }

    @Test
    void shouldWarnWhenLowConfidenceRetrievalStillHasLongAnswer() {
        AiRequestTrace trace = baseTrace();
        trace.setRetrievedCount(1);
        trace.setTopRetrievalScore(0.70D);
        trace.setResponse("这是一个很长的回答，用来模拟在检索分数并不高的情况下，模型依然给出了比较确定的业务性结论和执行建议。");

        AiEvaluationResult result = evaluationService.evaluate(trace, List.of());

        assertTrue(result.getReasonCodes().contains("LOW_CONFIDENCE_CONFIDENT_ANSWER"));
        assertEquals("HIGH", result.getRiskLevel());
    }

    @Test
    void shouldWarnWhenToolExecutionLooksBroken() {
        AiRequestTrace trace = baseTrace();
        AiToolTrace toolTrace = new AiToolTrace();
        toolTrace.setSuccess(false);
        toolTrace.setResultPreview("执行失败");

        AiEvaluationResult result = evaluationService.evaluate(trace, List.of(toolTrace));

        assertTrue(result.getReasonCodes().contains("TOOL_ERROR"));
        assertEquals("MEDIUM", result.getRiskLevel());
    }

    @Test
    void shouldFailWhenRequestErrorsOrResponseEmpty() {
        AiRequestTrace trace = baseTrace();
        trace.setStatus("ERROR");
        trace.setResponse("");

        AiEvaluationResult result = evaluationService.evaluate(trace, List.of());

        assertEquals("FAIL", result.getStatus());
        assertTrue(result.getReasonCodes().contains("REQUEST_ERROR"));
    }

    private AiRequestTrace baseTrace() {
        AiRequestTrace trace = new AiRequestTrace();
        trace.setStatus("SUCCESS");
        trace.setResponse("这是一个正常的回答，包含足够的业务说明。");
        trace.setFirstTokenLatencyMs(100L);
        trace.setRetrievedCount(0);
        return trace;
    }
}
