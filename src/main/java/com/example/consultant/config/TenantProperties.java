package com.example.consultant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户隔离配置。
 */
@ConfigurationProperties(prefix = "app.tenant")
public class TenantProperties {

    /**
     * 当前请求未显式传租户时使用的默认租户。
     */
    private Long defaultTenantId = 160L;

    public Long getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(Long defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }
}
