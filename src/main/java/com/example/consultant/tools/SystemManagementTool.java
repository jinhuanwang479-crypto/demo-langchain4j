package com.example.consultant.tools;

import com.example.consultant.pojo.*;
import com.example.consultant.service.SystemManagementService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SystemManagementTool {

    private final SystemManagementService systemManagementService;

    public SystemManagementTool(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @Tool("查询系统功能菜单")
    public List<ErpFunction> listFunctions(@P("关键字，可选") String keyword) {
        return systemManagementService.listFunctions(keyword);
    }

    @Tool("查询系统用户")
    public List<ErpUser> searchUsers(@P("姓名、登录名或手机号关键字，可选") String keyword,
                                     @P("返回条数，可选") Integer limit) {
        return systemManagementService.searchUsers(keyword, null, limit);
    }

    @Tool("创建系统用户")
    public ToolActionResult createUser(@P("用户姓名") String username,
                                       @P("登录名") String loginName,
                                       @P("手机号，可选") String phone,
                                       @P("邮箱，可选") String email,
                                       @P("岗位，可选") String position,
                                       @P("部门，可选") String department,
                                       @P("角色ID列表，多个用逗号分隔，可选") String roleIds) {
        return systemManagementService.createUser(username, loginName, phone, email, position, department, roleIds, null);
    }

    @Tool("更新用户状态")
    public ToolActionResult updateUserStatus(@P("用户ID") Long userId,
                                             @P("状态值，0表示正常，1表示禁用") Integer status) {
        return systemManagementService.updateUserStatus(userId, status, null);
    }

    @Tool("查询角色")
    public List<ErpRole> searchRoles(@P("关键字，可选") String keyword) {
        return systemManagementService.searchRoles(keyword, null);
    }

    @Tool("查询机构")
    public List<ErpOrganization> searchOrganizations(@P("关键字，可选") String keyword) {
        return systemManagementService.searchOrganizations(keyword, null);
    }

    @Tool("查询经手人")
    public List<ErpPerson> searchHandlers(@P("关键字，可选") String keyword) {
        return systemManagementService.searchHandlers(keyword, null);
    }

    @Tool("查询结算账户")
    public List<ErpAccount> searchAccounts(@P("关键字，可选") String keyword) {
        return systemManagementService.searchAccounts(keyword, null);
    }

    @Tool("查询仓库")
    public List<ErpDepot> searchDepots(@P("关键字，可选") String keyword) {
        return systemManagementService.searchDepots(keyword, null);
    }

    @Tool("查询供应商客户会员")
    public List<ErpPartner> searchPartners(@P("关键字，可选") String keyword,
                                           @P("类型，可选，例如供应商、客户、会员") String type,
                                           @P("返回条数，可选") Integer limit) {
        return systemManagementService.searchPartners(keyword, type, null, limit);
    }

    @Tool("查询计量单位，用于新增商品前解析unitId，并从basicUnit或副单位中选择实际单位")
    public List<ErpUnit> searchUnits(@P("关键字，可选") String keyword) {
        return systemManagementService.searchUnits(keyword, null);
    }

    @Tool("查询收支项目")
    public List<ErpInOutItem> searchInOutItems(@P("关键字，可选") String keyword,
                                               @P("类型，可选") String type) {
        return systemManagementService.searchInOutItems(keyword, type, null);
    }

    @Tool("查询系统参数配置")
    public ErpSystemConfig getSystemConfig() {
        return systemManagementService.getSystemConfig(null);
    }

    @Tool("查询平台参数配置")
    public List<ErpPlatformConfig> listPlatformConfig() {
        return systemManagementService.listPlatformConfig();
    }

    @Tool("查询系统消息")
    public List<ErpMessageNotice> listMessages(@P("用户ID，可选") Long userId,
                                               @P("消息状态，可选") String status) {
        return systemManagementService.listMessages(userId, status, null);
    }

    @Tool("创建系统消息")
    public ToolActionResult createMessage(@P("消息标题") String title,
                                          @P("消息内容") String content,
                                          @P("接收用户ID") Long userId,
                                          @P("消息类型，可选") String type) {
        return systemManagementService.createMessage(title, content, userId, type, null);
    }

    @Tool("查询操作日志")
    public List<ErpLogRecord> listLogs(@P("关键字，可选") String keyword,
                                       @P("用户ID，可选") Long userId,
                                       @P("返回条数，可选") Integer limit) {
        return systemManagementService.listLogs(keyword, userId, null, limit);
    }

    @Tool("保存基础资料")
    public ToolActionResult saveBaseData(@P("实体类型，例如角色、机构、经手人、账户、仓库、往来单位、计量单位、收支项目") String entityType,
                                         @P("JSON对象数据") String dataJson) {
        return systemManagementService.saveBaseData(entityType, dataJson, null);
    }

    @Tool("更新基础资料启用状态")
    public ToolActionResult updateBaseDataEnabled(@P("实体类型") String entityType,
                                                  @P("记录ID") Long id,
                                                  @P("是否启用") Boolean enabled) {
        return systemManagementService.updateBaseDataEnabled(entityType, id, enabled, null);
    }
}
