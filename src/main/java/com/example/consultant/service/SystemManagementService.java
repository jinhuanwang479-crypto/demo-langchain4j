package com.example.consultant.service;

import com.example.consultant.config.TenantContextHolder;
import com.example.consultant.mapper.SystemManagementMapper;
import com.example.consultant.pojo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SystemManagementService {

    private static final Long DEFAULT_TENANT_ID = 160L;
    private static final Long DEFAULT_CREATOR_ID = 160L;
    private static final int DEFAULT_LIMIT = 20;
    private static final String DEFAULT_PASSWORD_MD5 = "e10adc3949ba59abbe56e057f20f883e";

    private final SystemManagementMapper systemManagementMapper;
    private final ObjectMapper objectMapper;

    public SystemManagementService(SystemManagementMapper systemManagementMapper, ObjectMapper objectMapper) {
        this.systemManagementMapper = systemManagementMapper;
        this.objectMapper = objectMapper;
    }

    public List<ErpFunction> listFunctions(String keyword) {
        return systemManagementMapper.listFunctions(normalized(keyword));
    }

    public List<ErpUser> searchUsers(String keyword, Long tenantId, Integer limit) {
        return systemManagementMapper.searchUsers(normalized(keyword), tenantId(tenantId), limit(limit));
    }

    public ToolActionResult createUser(String username, String loginName, String phone, String email,
                                       String position, String department, String roleIds, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        if (!StringUtils.hasText(username) || !StringUtils.hasText(loginName)) {
            return new ToolActionResult("创建用户", "用户名和登录名不能为空", null, null);
        }
        if (systemManagementMapper.countUserByLoginName(loginName, resolvedTenantId) > 0) {
            return new ToolActionResult("创建用户", "登录名已存在", null, null);
        }

        ErpUserInsertParam param = new ErpUserInsertParam();
        param.setUsername(username.trim());
        param.setLoginName(loginName.trim());
        param.setPassword(DEFAULT_PASSWORD_MD5);
        param.setPosition(normalized(position));
        param.setDepartment(normalized(department));
        param.setEmail(normalized(email));
        param.setPhonenum(normalized(phone));
        param.setTenantId(resolvedTenantId);
        systemManagementMapper.insertUser(param);

        if (StringUtils.hasText(roleIds)) {
            systemManagementMapper.insertUserRole(String.valueOf(param.getId()), toBracketRoleValue(roleIds), resolvedTenantId);
        }
        return new ToolActionResult("创建用户", "用户创建成功", param.getId(), null);
    }

    public ToolActionResult updateUserStatus(Long userId, Integer status, Long tenantId) {
        int rows = systemManagementMapper.updateUserStatus(userId, status, tenantId(tenantId));
        return new ToolActionResult("更新用户状态", rows > 0 ? "用户状态更新成功" : "未找到用户", userId, null);
    }

    public List<ErpRole> searchRoles(String keyword, Long tenantId) {
        return systemManagementMapper.searchRoles(normalized(keyword), tenantId(tenantId));
    }

    public List<ErpOrganization> searchOrganizations(String keyword, Long tenantId) {
        return systemManagementMapper.searchOrganizations(normalized(keyword), tenantId(tenantId));
    }

    public List<ErpPerson> searchHandlers(String keyword, Long tenantId) {
        return systemManagementMapper.searchHandlers(normalized(keyword), tenantId(tenantId));
    }

    public List<ErpAccount> searchAccounts(String keyword, Long tenantId) {
        return systemManagementMapper.searchAccounts(normalized(keyword), tenantId(tenantId));
    }

    public List<ErpDepot> searchDepots(String keyword, Long tenantId) {
        return systemManagementMapper.searchDepots(normalized(keyword), tenantId(tenantId));
    }

    public List<ErpPartner> searchPartners(String keyword, String type, Long tenantId, Integer limit) {
        return systemManagementMapper.searchPartners(normalized(keyword), normalizePartnerType(type), tenantId(tenantId), limit(limit));
    }

    public List<ErpUnit> searchUnits(String keyword, Long tenantId) {
        return systemManagementMapper.searchUnits(normalized(keyword), tenantId(tenantId));
    }

    public List<ErpInOutItem> searchInOutItems(String keyword, String type, Long tenantId) {
        return systemManagementMapper.searchInOutItems(normalized(keyword), normalizeInOutType(type), tenantId(tenantId));
    }

    public ErpSystemConfig getSystemConfig(Long tenantId) {
        return systemManagementMapper.getSystemConfig(tenantId(tenantId));
    }

    public List<ErpPlatformConfig> listPlatformConfig() {
        return systemManagementMapper.listPlatformConfig();
    }

    public List<ErpMessageNotice> listMessages(Long userId, String status, Long tenantId) {
        return systemManagementMapper.listMessages(userId, normalized(status), tenantId(tenantId));
    }

    public ToolActionResult createMessage(String title, String content, Long userId, String type, Long tenantId) {
        ErpMessageNotice notice = new ErpMessageNotice();
        notice.setMsgTitle(title);
        notice.setMsgContent(content);
        notice.setCreateTime(LocalDateTime.now());
        notice.setType(StringUtils.hasText(type) ? type.trim() : "系统消息");
        notice.setUserId(userId);
        notice.setStatus("1");
        notice.setTenantId(tenantId(tenantId));
        systemManagementMapper.insertMessage(notice);
        return new ToolActionResult("创建消息", "消息创建成功", notice.getId(), null);
    }

    public List<ErpLogRecord> listLogs(String keyword, Long userId, Long tenantId, Integer limit) {
        return systemManagementMapper.listLogs(normalized(keyword), userId, tenantId(tenantId), limit(limit));
    }

    public ToolActionResult saveBaseData(String entityType, String dataJson, Long tenantId) {
        Long resolvedTenantId = tenantId(tenantId);
        String type = normalizeBaseEntityType(entityType);
        return switch (type) {
            case "role" -> saveRole(dataJson, resolvedTenantId);
            case "organization" -> saveOrganization(dataJson, resolvedTenantId);
            case "person" -> savePerson(dataJson, resolvedTenantId);
            case "account" -> saveAccount(dataJson, resolvedTenantId);
            case "depot" -> saveDepot(dataJson, resolvedTenantId);
            case "partner" -> savePartner(dataJson, resolvedTenantId, null);
            case "supplier" -> savePartner(dataJson, resolvedTenantId, "供应商");
            case "customer" -> savePartner(dataJson, resolvedTenantId, "客户");
            case "member" -> savePartner(dataJson, resolvedTenantId, "会员");
            case "unit" -> saveUnit(dataJson, resolvedTenantId);
            case "inoutitem" -> saveInOutItem(dataJson, resolvedTenantId);
            default -> new ToolActionResult("保存基础资料", "不支持的实体类型", null, null);
        };
    }

    public ToolActionResult updateBaseDataEnabled(String entityType, Long id, Boolean enabled, Long tenantId) {
        String type = normalizeBaseEntityType(entityType);
        int value = toFlag(enabled, true);
        int rows = switch (type) {
            case "role" -> systemManagementMapper.updateRoleEnabled(id, value, tenantId(tenantId));
            case "person" -> systemManagementMapper.updatePersonEnabled(id, value, tenantId(tenantId));
            case "account" -> systemManagementMapper.updateAccountEnabled(id, value, tenantId(tenantId));
            case "depot" -> systemManagementMapper.updateDepotEnabled(id, value, tenantId(tenantId));
            case "partner", "supplier", "customer", "member" ->
                    systemManagementMapper.updatePartnerEnabled(id, value, tenantId(tenantId));
            case "unit" -> systemManagementMapper.updateUnitEnabled(id, value, tenantId(tenantId));
            case "inoutitem" -> systemManagementMapper.updateInOutItemEnabled(id, value, tenantId(tenantId));
            default -> 0;
        };
        return new ToolActionResult("更新启用状态", rows > 0 ? "更新成功" : "未找到可更新记录", id, null);
    }

    private ToolActionResult saveRole(String dataJson, Long tenantId) {
        ErpRoleInsertParam param = parseObject(dataJson, ErpRoleInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        systemManagementMapper.insertRole(param);
        return new ToolActionResult("保存角色", "角色创建成功", param.getId(), null);
    }

    private ToolActionResult saveOrganization(String dataJson, Long tenantId) {
        ErpOrganizationInsertParam param = parseObject(dataJson, ErpOrganizationInsertParam.class);
        param.setTenantId(tenantId);
        param.setCreateTime(LocalDateTime.now());
        param.setUpdateTime(LocalDateTime.now());
        systemManagementMapper.insertOrganization(param);
        return new ToolActionResult("保存机构", "机构创建成功", param.getId(), null);
    }

    private ToolActionResult savePerson(String dataJson, Long tenantId) {
        ErpPersonInsertParam param = parseObject(dataJson, ErpPersonInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        systemManagementMapper.insertPerson(param);
        return new ToolActionResult("保存经手人", "经手人创建成功", param.getId(), null);
    }

    private ToolActionResult saveAccount(String dataJson, Long tenantId) {
        ErpAccountInsertParam param = parseObject(dataJson, ErpAccountInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        param.setCurrentAmount(param.getCurrentAmount() == null ? defaultDecimal(param.getInitialAmount()) : param.getCurrentAmount());
        systemManagementMapper.insertAccount(param);
        return new ToolActionResult("保存账户", "账户创建成功", param.getId(), null);
    }

    private ToolActionResult saveDepot(String dataJson, Long tenantId) {
        ErpDepotInsertParam param = parseObject(dataJson, ErpDepotInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        param.setIsDefault(param.getIsDefault() == null ? 0 : param.getIsDefault());
        systemManagementMapper.insertDepot(param);
        return new ToolActionResult("保存仓库", "仓库创建成功", param.getId(), null);
    }

    private ToolActionResult savePartner(String dataJson, Long tenantId, String fixedType) {
        ErpPartnerInsertParam param = parseObject(dataJson, ErpPartnerInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        param.setCreator(param.getCreator() == null ? DEFAULT_CREATOR_ID : param.getCreator());
        if (StringUtils.hasText(fixedType)) {
            param.setType(fixedType);
        } else {
            param.setType(normalizePartnerType(param.getType()));
        }
        systemManagementMapper.insertPartner(param);
        return new ToolActionResult("保存往来单位", "往来单位创建成功", param.getId(), null);
    }

    private ToolActionResult saveUnit(String dataJson, Long tenantId) {
        ErpUnitInsertParam param = parseObject(dataJson, ErpUnitInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        systemManagementMapper.insertUnit(param);
        return new ToolActionResult("保存单位", "单位创建成功", param.getId(), null);
    }

    private ToolActionResult saveInOutItem(String dataJson, Long tenantId) {
        ErpInOutItemInsertParam param = parseObject(dataJson, ErpInOutItemInsertParam.class);
        param.setTenantId(tenantId);
        param.setEnabled(param.getEnabled() == null ? 1 : param.getEnabled());
        param.setType(normalizeInOutType(param.getType()));
        systemManagementMapper.insertInOutItem(param);
        return new ToolActionResult("保存收支项目", "收支项目创建成功", param.getId(), null);
    }

    private <T> T parseObject(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式不正确: " + e.getMessage(), e);
        }
    }

    private String toBracketRoleValue(String roleIds) {
        return List.of(roleIds.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(id -> "[" + id + "]")
                .reduce("", String::concat);
    }

    private String normalizeBaseEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        return switch (entityType.trim().toLowerCase(Locale.ROOT)) {
            case "角色" -> "role";
            case "机构" -> "organization";
            case "经手人" -> "person";
            case "账户", "结算账户" -> "account";
            case "仓库" -> "depot";
            case "往来单位" -> "partner";
            case "供应商" -> "supplier";
            case "客户" -> "customer";
            case "会员" -> "member";
            case "单位", "计量单位" -> "unit";
            case "收支项目" -> "inoutitem";
            case "in_out_item" -> "inoutitem";
            default -> entityType.trim().toLowerCase(Locale.ROOT);
        };
    }

    private String normalizePartnerType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "supplier", "供应商" -> "供应商";
            case "customer", "客户" -> "客户";
            case "member", "会员" -> "会员";
            default -> type.trim();
        };
    }

    private String normalizeInOutType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "income", "收入" -> "收入";
            case "expense", "支出" -> "支出";
            default -> type.trim();
        };
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long tenantId(Long tenantId) {
        // 显式传参优先，其次读取当前请求租户，最后回退到默认租户。
        return TenantContextHolder.resolveTenantId(tenantId, DEFAULT_TENANT_ID);
    }

    private int limit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
    }

    private int toFlag(Boolean value, boolean defaultValue) {
        boolean resolved = value == null ? defaultValue : value;
        return resolved ? 1 : 0;
    }
}
