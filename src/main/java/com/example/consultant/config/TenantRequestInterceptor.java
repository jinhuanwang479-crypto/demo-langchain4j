package com.example.consultant.config;

import com.example.consultant.mapper.TenantUserMapper;
import com.example.consultant.utils.UserContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 在请求进入控制器前解析租户，并在请求结束后清理上下文。
 * 当前项目的前端只会传 X-Access-Token 和 X-User-Id，
 * 所以后端这里固定按 X-User-Id 去数据库查询 tenant_id。
 */
@Component
public class TenantRequestInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantRequestInterceptor.class);

    private final TenantProperties tenantProperties;
    private final TenantUserMapper tenantUserMapper;

    public TenantRequestInterceptor(TenantProperties tenantProperties, TenantUserMapper tenantUserMapper) {
        this.tenantProperties = tenantProperties;
        this.tenantUserMapper = tenantUserMapper;
    }
    //拦截器进来之后先把租户id放到TenantContextHolder中
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long tenantId = resolveTenantId(request);
        TenantContextHolder.setTenantId(tenantId);
        String currentUserId = UserContextUtil.getUserId(request);
        log.info("租户解析完成: path={}, userId={}, tenantId={}",
                request.getRequestURI(), currentUserId, tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    private Long resolveTenantId(HttpServletRequest request) {
        String userIdHeader = UserContextUtil.getUserId(request);
        if (!StringUtils.hasText(userIdHeader)) {
            return tenantProperties.getDefaultTenantId();
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException ex) {
            return tenantProperties.getDefaultTenantId();
        }

        // 查不到租户时按兼容策略回退默认租户，而不是直接拒绝请求。
        Long tenantId = tenantUserMapper.findTenantIdByUserId(userId);
        return tenantId != null ? tenantId : tenantProperties.getDefaultTenantId();
    }
}
