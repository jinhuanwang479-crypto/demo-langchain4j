package com.example.consultant.aspect;

import com.example.consultant.pojo.ToolActionResult;
import com.example.consultant.security.PermissionDeniedException;
import com.example.consultant.security.RequireRole;
import com.example.consultant.service.RolePermissionService;
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
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 工具调用角色校验切面。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class ToolRoleAuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(ToolRoleAuthorizationAspect.class);

    private final RolePermissionService rolePermissionService;

    public ToolRoleAuthorizationAspect(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @Around("within(com.example.consultant.tools..*) && @annotation(dev.langchain4j.agent.tool.Tool)")
    public Object authorizeTool(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole requireRole = resolveRequireRole(method, joinPoint.getTarget().getClass());
        if (requireRole == null) {
            return joinPoint.proceed();
        }

        String resourceName = resolveToolName(method);
        try {
            rolePermissionService.assertHasAnyRole(resourceName, requireRole.value());
            return joinPoint.proceed();
        } catch (PermissionDeniedException ex) {
            log.warn("工具权限校验未通过: tool={}, userId={}, message={}",
                    method.getName(),
                    com.example.consultant.security.UserContextHolder.getCurrentUserId(),
                    ex.getMessage());
            if (ToolActionResult.class.isAssignableFrom(signature.getReturnType())) {
                return new ToolActionResult(resourceName, ex.getMessage(), null, null);
            }
            throw ex;
        }
    }

    private RequireRole resolveRequireRole(Method method, Class<?> targetClass) {
        RequireRole methodAnnotation = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotationUtils.findAnnotation(targetClass, RequireRole.class);
    }

    private String resolveToolName(Method method) {
        Tool tool = AnnotationUtils.findAnnotation(method, Tool.class);
        if (tool != null && tool.value().length > 0 && StringUtils.hasText(tool.value()[0])) {
            return tool.value()[0];
        }
        return method.getName();
    }
}
