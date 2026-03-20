package com.example.consultant.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
/**
 * 工具调用监测切面。
 * 该切面专门拦截 `com.example.consultant.tools..*` 下带 `@Tool` 注解的方法，
 * 统一记录工具调用次数与耗时指标。
 *
 * 注意：
 * 1. 这里记录的是“全局工具监测指标”；
 * 2. 请求级工具留痕仍由 AiChatObservationContext 在 onToolExecuted 回调里负责。
 */
public class ToolExecutionMonitoringAspect {

    private final MeterRegistry meterRegistry;

    public ToolExecutionMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("within(com.example.consultant.tools..*) && @annotation(dev.langchain4j.agent.tool.Too  l)")
    /**
     * 包裹工具方法执行。
     * 成功与失败都会产生日志指标，避免只统计成功样本导致观测失真。
     */
    public Object monitorTool(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer timer = Timer.builder("ai_tool_latency")
                .tag("tool", toolName)
                .register(meterRegistry);
        try {
            Object result = joinPoint.proceed();
            Counter.builder("ai_tool_invocations_total")
                    .tag("tool", toolName)
                    .tag("status", "success")
                    .register(meterRegistry)
                    .increment();
            return result;
        } catch (Throwable throwable) {
            Counter.builder("ai_tool_invocations_total")
                    .tag("tool", toolName)
                    .tag("status", "failure")
                    .register(meterRegistry)
                    .increment();
            throw throwable;
        } finally {
            sample.stop(timer);
        }
    }
}
