package com.example.consultant.service;

import com.example.consultant.mapper.AiToolPermissionMapper;
import com.example.consultant.mapper.TenantUserMapper;
import com.example.consultant.pojo.AiToolDefinition;
import com.example.consultant.pojo.ErpUser;
import com.example.consultant.security.PermissionDeniedException;
import com.example.consultant.security.UserContextHolder;
import com.example.consultant.utils.BracketValueParser;
import com.example.consultant.utils.UserContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Tool 权限服务。
 * <p>
 * 基于 `jsh_ai_tool + RoleTools` 判定当前用户是否有权调用某个 AI Tool，
 * 并在必要时从请求上下文中兜底恢复用户信息。
 * </p>
 */
@Service
public class RolePermissionService {
    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);


    private final AiToolPermissionMapper aiToolPermissionMapper;
    private final TenantUserMapper tenantUserMapper;

    public RolePermissionService(AiToolPermissionMapper aiToolPermissionMapper, TenantUserMapper tenantUserMapper) {
        this.aiToolPermissionMapper = aiToolPermissionMapper;
        this.tenantUserMapper = tenantUserMapper;
    }

    /**
     * 获取当前用户。
     * 优先使用线程上下文，异步线程拿不到时再从请求头兜底恢复。
     */
    public ErpUser getCurrentUser() {
        ErpUser currentUser = UserContextHolder.getCurrentUser();
        if (currentUser != null) {
            return currentUser;
        }
        return resolveCurrentUserFromRequest();
    }

    /**
     * 判断当前用户是否有指定工具权限。
     */
    public boolean hasToolPermission(String toolCode) {
        return hasToolPermission(getCurrentUser(), aiToolPermissionMapper.findByToolCode(toolCode));
    }

    public boolean hasToolPermission(ErpUser user, AiToolDefinition toolDefinition) {
        if (user == null || toolDefinition == null || !Integer.valueOf(1).equals(toolDefinition.getEnabled())) {
            return false;
        }
        Set<Long> toolIds = user.getToolIds();
        if (CollectionUtils.isEmpty(toolIds)) {
            return false;
        }
        return toolIds.contains(toolDefinition.getId());
    }

    /**
     * 断言当前用户具备指定工具权限。
     */
    public void assertHasToolPermission(String toolCode, String resourceName) {
        AiToolDefinition toolDefinition = aiToolPermissionMapper.findByToolCode(toolCode);
        ErpUser currentUser = getCurrentUser();
        //查看这个用户的角色的可以使用的全部的tools在不在这个tool集合中
        if (hasToolPermission(currentUser, toolDefinition)) {
            String username = currentUser == null ? "unknown" : currentUser.getUsername();
            log.info("鉴权成功：用户[{}] 调用 [{}] 通过", username, resourceName);
            return;
        }
        if (toolDefinition == null) {
            throw new PermissionDeniedException(resourceName + " 未注册或已被禁用");
        }

        String username = currentUser == null ? "当前用户" : currentUser.getUsername();
        throw new PermissionDeniedException(String.format("%s 无权调用 %s", username, resourceName));
    }

    private ErpUser resolveCurrentUserFromRequest() {
        String userIdHeader = UserContextUtil.getUserId();
        if (!StringUtils.hasText(userIdHeader)) {
            return null;
        }
        try {
            Long userId = Long.parseLong(userIdHeader.trim());
            ErpUser user = tenantUserMapper.findActiveUserWithRoles(userId);
            if (user == null) {
                return null;
            }
            Set<Long> roleIds = BracketValueParser.parseIds(user.getRoleIdsRaw());
            user.setRoleIds(roleIds);
            List<String> roleToolValues = roleIds.isEmpty()
                    ? List.of()
                    : tenantUserMapper.findRoleToolValuesByRoleIds(user.getTenantId(), roleIds.stream().toList());
            user.setToolIds(BracketValueParser.parseIds(roleToolValues));
            return user;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
