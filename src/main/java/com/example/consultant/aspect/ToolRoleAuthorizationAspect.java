package com.example.consultant.aspect;

import com.example.consultant.pojo.ToolActionResult;
import com.example.consultant.security.PermissionDeniedException;
import com.example.consultant.security.UserContextHolder;
import com.example.consultant.service.RolePermissionService;
import com.example.consultant.utils.UserContextUtil;
import dev.langchain4j.agent.tool.Tool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 工具角色授权切面
 * <p>
 * 用于拦截所有带有 @Tool 注解的方法，在方法执行前进行权限校验。
 * 校验逻辑：检查当前用户是否拥有调用指定工具（toolCode）的权限。
 * </p>
 * <p>
 * 权限校验失败时的行为：
 * <ul>
 *     <li>如果方法返回类型是 {@link ToolActionResult}，则返回一个失败的响应对象（不抛异常）</li>
 *     <li>否则直接抛出 {@link PermissionDeniedException}</li>
 * </ul>
 * </p>
 * <p>
 * 切面优先级说明：使用 {@link Ordered#HIGHEST_PRECEDENCE} 确保权限校验在其他切面（如监控切面）之前执行，
 * 避免未授权用户产生不必要的监控数据。
 * </p>
 *
 * @author consultant-team
 * @since 1.0.0
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)  // 最高优先级，确保权限校验最先执行
@Component
public class ToolRoleAuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(ToolRoleAuthorizationAspect.class);

    private final RolePermissionService rolePermissionService;

    /**
     * 构造器注入权限校验服务
     *
     * @param rolePermissionService 角色权限服务，提供工具权限校验的核心逻辑
     */
    public ToolRoleAuthorizationAspect(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    /**
     * 环绕通知：对所有标记了 @Tool 注解的工具方法进行权限校验
     * <p>
     * 切点表达式说明：
     * <ul>
     *     <li>within(com.example.consultant.tools..*) - 拦截 tools 包及其子包下的所有类</li>
     *     <li>@annotation(dev.langchain4j.agent.tool.Tool) - 拦截标记了 @Tool 注解的方法</li>
     * </ul>
     * </p>
     * <p>
     * 执行流程：
     * <ol>
     *     <li>构建工具标识码（toolCode）：类名#方法名</li>
     *     <li>解析工具展示名称（从 @Tool 注解的 value 获取）</li>
     *     <li>调用权限服务校验当前用户是否拥有该工具的权限</li>
     *     <li>校验通过：执行原方法</li>
     *     <li>校验失败：记录日志，根据返回类型决定是返回失败响应还是抛出异常</li>
     * </ol>
     * </p>
     *
     * @param joinPoint 切点连接点，封装了被拦截方法的信息
     * @return 原方法的执行结果，或权限失败时的 ToolActionResult 失败响应
     * @throws Throwable 权限失败且方法返回类型不是 ToolActionResult 时，抛出 PermissionDeniedException
     */
    @Around("within(com.example.consultant.tools..*) && @annotation(dev.langchain4j.agent.tool.Tool)")
    public Object authorizeTool(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取被拦截方法的签名和原始类信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = ClassUtils.getUserClass(joinPoint.getTarget());

        // 构建工具唯一标识码：类名#方法名（用于权限系统的内部匹配）
        String toolCode = targetClass.getSimpleName() + "#" + method.getName();

        // 解析工具展示名称（优先使用 @Tool 注解的 value，否则使用方法名）
        String resourceName = resolveToolName(method);

        try {
            // 调用权限服务进行校验
            // 如果当前用户没有该工具权限，内部会抛出 PermissionDeniedException
            rolePermissionService.assertHasToolPermission(toolCode, resourceName);

            // 权限校验通过，执行原工具方法
            return joinPoint.proceed();

        } catch (PermissionDeniedException ex) {
            // 权限校验失败：记录警告日志，包含工具标识和用户信息
            log.warn("工具权限校验未通过: toolCode={}, userId={}, message={}",
                    toolCode,
                    // 优先从 UserContextHolder 获取用户ID（当前请求上下文），
                    // 如果获取不到则从 UserContextUtil 获取（可能来自其他场景）
                    UserContextHolder.getCurrentUserId() != null
                            ? UserContextHolder.getCurrentUserId()
                            : UserContextUtil.getUserId(),
                    ex.getMessage());

            // 判断原方法的返回类型是否可以被 ToolActionResult 替代
            // 这样做的好处：前端/AI 可以收到友好的失败提示，而不是直接抛异常导致流程中断
            if (ToolActionResult.class.isAssignableFrom(signature.getReturnType())) {
                // 返回一个失败的响应对象，包含资源名称、错误信息和错误码
                return ToolActionResult.failure(resourceName, ex.getMessage(), "PERMISSION_DENIED");
            }

            // 如果原方法返回类型不支持 ToolActionResult，则继续向上抛出异常
            throw ex;
        }
    }

    /**
     * 解析工具的展示名称
     * <p>
     * 优先级：
     * <ol>
     *     <li>从 @Tool 注解的 value 数组中取第一个非空元素</li>
     *     <li>如果注解未配置或配置为空，则使用方法名作为展示名称</li>
     * </ol>
     * </p>
     *
     * @param method 被拦截的方法对象
     * @return 工具的展示名称，用于错误提示和日志输出
     */
    private String resolveToolName(Method method) {
        // 从方法上查找 @Tool 注解（支持从接口或父类继承）
        Tool tool = AnnotationUtils.findAnnotation(method, Tool.class);

        // 如果注解存在，且 value 数组有内容，且第一个元素不为空，则使用注解值
        if (tool != null && tool.value().length > 0 && StringUtils.hasText(tool.value()[0])) {
            return tool.value()[0];
        }

        // 兜底：使用方法名
        return method.getName();
    }
}
