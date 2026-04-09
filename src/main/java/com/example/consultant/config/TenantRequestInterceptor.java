package com.example.consultant.config;

import com.example.consultant.mapper.TenantUserMapper;
import com.example.consultant.pojo.ErpUser;
import com.example.consultant.security.UserContextHolder;
import com.example.consultant.utils.BracketValueParser;
import com.example.consultant.utils.UserContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 在请求进入控制器前解析租户和当前用户，并在请求结束后清理上下文。
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long tenantId = resolveTenantId(request);
        TenantContextHolder.setTenantId(tenantId);
        UserContextHolder.setCurrentUser(resolveCurrentUser(request, tenantId));

        String currentUserId = UserContextUtil.getUserId(request);
        log.info("租户解析完成: path={}, userId={}, tenantId={}",
                request.getRequestURI(), currentUserId, tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
        UserContextHolder.clear();
    }

    private Long resolveTenantId(HttpServletRequest request) {
        String userIdHeader = UserContextUtil.getUserId(request);
        if (!StringUtils.hasText(userIdHeader)) {
            return tenantProperties.getDefaultTenantId();
        }

        try {
            Long userId = Long.parseLong(userIdHeader.trim());
            Long tenantId = tenantUserMapper.findTenantIdByUserId(userId);
            return tenantId != null ? tenantId : tenantProperties.getDefaultTenantId();
        } catch (NumberFormatException ex) {
            return tenantProperties.getDefaultTenantId();
        }
    }

    private ErpUser resolveCurrentUser(HttpServletRequest request, Long tenantId) {
        String userIdHeader = UserContextUtil.getUserId(request);
        if (!StringUtils.hasText(userIdHeader)) {
            return null;
        }

        try {
            Long userId = Long.parseLong(userIdHeader.trim());
            ErpUser user = tenantUserMapper.findActiveUserWithRoles(userId);
            if (user == null) {
                return null;
            }
            user.setTenantId(user.getTenantId() != null ? user.getTenantId() : tenantId);
            Set<Long> roleIds = BracketValueParser.parseIds(user.getRoleIdsRaw());
            user.setRoleIds(roleIds);
            List<String> roleToolValues = roleIds.isEmpty()
                    ? List.of()
                    : tenantUserMapper.findRoleToolValuesByRoleIds(user.getTenantId(), roleIds.stream().toList());
            user.setToolIds(BracketValueParser.parseIds(roleToolValues));
            return user;
        } catch (NumberFormatException ex) {
            log.warn("用户ID格式非法，无法构建用户上下文: {}", userIdHeader);
            return null;
        }
    }
}
