package com.example.consultant.security;

import com.example.consultant.pojo.ErpUser;

/**
 * Request-scoped current user holder based on ThreadLocal.
 */
public final class UserContextHolder {

    private static final InheritableThreadLocal<ErpUser> CURRENT_USER = new InheritableThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setCurrentUser(ErpUser user) {
        CURRENT_USER.set(user);
    }

    public static ErpUser getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static Long getCurrentUserId() {
        ErpUser user = CURRENT_USER.get();
        return user == null ? null : user.getId();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
