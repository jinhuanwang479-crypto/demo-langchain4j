package com.example.consultant.aspect;

import com.example.consultant.pojo.ToolActionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * 工具执行监控切面
 * <p>
 * 用于拦截所有带有 @Tool 注解的方法，自动收集以下监控指标：
 * <ul>
 *     <li>调用次数（成功/失败）</li>
 *     <li>执行耗时</li>
 * </ul>
 * 指标数据会被推送到 Micrometer 监控系统，供 Prometheus、Graphite 等工具采集。
 * </p>
 *
 * @author consultant-team
 * @since 1.0.0
 */
@Aspect
@Component
public class ToolExecutionMonitoringAspect {

    private final MeterRegistry meterRegistry;

    /**
     * 构造器注入 MeterRegistry
     *
     * @param meterRegistry Micrometer 指标注册中心，用于创建和注册监控指标
     */
    public ToolExecutionMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 环绕通知：监控所有标记了 @Tool 注解的工具方法
     * <p>
     * 切点表达式说明：
     * <ul>
     *     <li>within(com.example.consultant.tools..*) - 拦截 tools 包及其子包下的所有类</li>
     *     <li>@annotation(dev.langchain4j.agent.tool.Tool) - 拦截标记了 @Tool 注解的方法</li>
     * </ul>
     * </p>
     *
     * @param joinPoint 切点连接点，封装了被拦截方法的信息
     * @return 原方法的执行结果
     * @throws Throwable 原方法抛出的异常会继续向上抛出
     */
    @Around("within(com.example.consultant.tools..*) && @annotation(dev.langchain4j.agent.tool.Tool)")
    public Object monitorTool(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取被拦截方法的签名信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 构建工具名称：类名#方法名
        // 使用 ClassUtils.getUserClass 获取原始类（避免 CGLIB 代理类干扰）
        String toolName = ClassUtils.getUserClass(joinPoint.getTarget()).getSimpleName()
                + "#"
                + signature.getMethod().getName();

        // 开始计时（记录开始时间点）
        Timer.Sample sample = Timer.start(meterRegistry);

        // 创建或获取耗时指标的 Timer 对象，绑定 tool 标签用于区分不同工具
        Timer timer = Timer.builder("ai_tool_latency")
                .tag("tool", toolName)           // 添加标签：工具名称
                .register(meterRegistry);

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 创建或获取调用计数器的 Counter 对象，增加计数
            // 根据执行结果判断成功/失败状态（通过 ToolActionResult.getSuccess() 判断）
            Counter.builder("ai_tool_invocations_total")
                    .tag("tool", toolName)                                 // 添加标签：工具名称
                    .tag("status", isFailureResult(result) ? "failure" : "success") // 添加标签：调用结果状态
                    .register(meterRegistry)                               // 注册到指标注册中心
                    .increment();                                          // 计数器 +1

            return result;
        } catch (Throwable throwable) {
            // 方法执行抛出异常时，记录失败调用
            Counter.builder("ai_tool_invocations_total")
                    .tag("tool", toolName)
                    .tag("status", "failure")
                    .register(meterRegistry)
                    .increment();
            // 继续向上抛出异常，不吞掉异常信息
            throw throwable;
        } finally {
            // 无论成功还是失败，都要停止计时并记录耗时
            sample.stop(timer);
        }
    }

    /**
     * 判断工具执行结果是否为失败
     * <p>
     * 判断逻辑：
     * <ul>
     *     <li>如果返回结果是 ToolActionResult 类型，则检查其 success 字段是否为 true</li>
     *     <li>其他类型的返回值默认视为成功</li>
     * </ul>
     * </p>
     *
     * @param result 工具方法的执行结果
     * @return true：执行失败；false：执行成功
     */
    private boolean isFailureResult(Object result) {
        // 如果返回结果是 ToolActionResult 类型，则根据 success 字段判断
        if (result instanceof ToolActionResult toolActionResult) {
            // 当 success 不为 true（即 false 或 null）时，视为失败
            return !Boolean.TRUE.equals(toolActionResult.getSuccess());
        }
        // 非 ToolActionResult 类型的返回值，默认视为执行成功
        return false;
    }
}
