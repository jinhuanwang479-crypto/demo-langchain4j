package com.example.consultant.utils;

import com.example.consultant.config.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类
 */
public class UserContextUtil {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String LOGIN_NAME_HEADER = "X-Login-Name";
    private static final String ACCESS_TOKEN_HEADER = "X-Access-Token";

    /**
     * 获取当前登录用户 ID
     * @return 用户 ID
     */
    public static String getUserId() {
        return getUserId(getCurrentRequest());
    }

    /**
     * 从指定请求中获取当前登录用户 ID。
     * 供拦截器等已经持有 request 的场景复用，避免各处直接写请求头名。
     */
    public static String getUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader(USER_ID_HEADER);
    }

    /**
     * 获取当前登录用户名
     * @return 登录名
     */
    public static String getLoginName() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader(LOGIN_NAME_HEADER);
    }

    /**
     * 获取 Token
     * @return Token
     */
    public static String getToken() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader(ACCESS_TOKEN_HEADER);
    }

    /**
     * 获取当前租户 ID。
     * 当前项目按“X-User-Id -> jsh_user.tenant_id”解析租户，
     * 这里直接读取请求上下文中已经解析好的租户结果。
     */
    public static Long getTenantId() {
        return TenantContextHolder.getTenantId();
    }

    /**
     * 生成带租户作用域的用户标识。
     * 这样即使不同租户里 userId 重复，会话数据也不会串。
     */
    public static String getTenantScopedUserId() {
        String userId = getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        Long tenantId = getTenantId();
        if (tenantId == null) {
            return userId;
        }
        return tenantId + ":" + userId;
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }
}
