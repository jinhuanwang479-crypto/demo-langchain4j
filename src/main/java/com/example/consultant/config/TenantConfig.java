package com.example.consultant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户配置。
 * 这里不再使用旧版 MyBatis-Plus 的租户 SQL 解析器，
 * 而是改为当前项目更容易维护的“请求 -> 租户上下文 -> service 默认取值”方案。
 * 这样能和前端仅传 X-Access-Token、X-User-Id 的约束保持一致。
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantConfig implements WebMvcConfigurer {

    private final TenantRequestInterceptor tenantRequestInterceptor;

    public TenantConfig(TenantRequestInterceptor tenantRequestInterceptor) {
        this.tenantRequestInterceptor = tenantRequestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantRequestInterceptor).addPathPatterns("/**");
    }

    @Bean
    public RequestContextFilter requestContextFilter() {
        RequestContextFilter filter = new RequestContextFilter();
        filter.setThreadContextInheritable(true);
        return filter;
    }
}
