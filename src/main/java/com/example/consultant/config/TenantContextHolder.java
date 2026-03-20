package com.example.consultant.config;

/**
 * 当前请求租户上下文。
 * service 层在没有显式 tenantId 入参时，会优先从这里取值。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID_HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(Long tenantId) {
        if (tenantId == null) {
            TENANT_ID_HOLDER.remove();
            return;
        }
        TENANT_ID_HOLDER.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    public static Long resolveTenantId(Long explicitTenantId, Long defaultTenantId) {
        if (explicitTenantId != null) {
            return explicitTenantId;
        }
        Long currentTenantId = getTenantId();
        return currentTenantId != null ? currentTenantId : defaultTenantId;
    }

    public static void clear() {
        TENANT_ID_HOLDER.remove();
    }
}
