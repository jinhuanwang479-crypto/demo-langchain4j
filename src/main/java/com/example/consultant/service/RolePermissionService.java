package com.example.consultant.service;

import com.example.consultant.pojo.ErpUser;
import com.example.consultant.security.PermissionDeniedException;
import com.example.consultant.security.Role;
import com.example.consultant.security.UserContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限验证服务。
 */
@Service
public class RolePermissionService {

    public ErpUser getCurrentUser() {
        return UserContextHolder.getCurrentUser();
    }

    public boolean hasAnyRole(Role... requiredRoles) {
        return hasAnyRole(getCurrentUser(), requiredRoles);
    }

    public boolean hasAnyRole(ErpUser user, Role... requiredRoles) {
        if (user == null || requiredRoles == null || requiredRoles.length == 0) {
            return false;
        }
        Set<Role> userRoles = user.getRoles();
        if (CollectionUtils.isEmpty(userRoles)) {
            return false;
        }
        return Arrays.stream(requiredRoles).anyMatch(userRoles::contains);
    }

    public void assertHasAnyRole(String resourceName, Role... requiredRoles) {
        ErpUser currentUser = getCurrentUser();
        if (hasAnyRole(currentUser, requiredRoles)) {
            return;
        }

        String roleNames = Arrays.stream(requiredRoles)
                .map(Role::getDisplayName)
                .collect(Collectors.joining(" / "));
        String username = currentUser == null ? "当前用户" : currentUser.getUsername();
        throw new PermissionDeniedException(
                String.format("%s 无权调用 %s，该功能仅允许 [%s] 使用。", username, resourceName, roleNames)
        );
    }
}
