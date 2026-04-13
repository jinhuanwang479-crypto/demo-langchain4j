package com.example.consultant.service;

import com.example.consultant.mapper.AiToolPermissionMapper;
import com.example.consultant.pojo.AiToolDefinition;
import com.example.consultant.pojo.ErpUser;
import com.example.consultant.utils.UserContextUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 生成“当前可直接在 AI 中执行什么操作”的动态提示词片段。
 * 这段内容会注入系统提示词，约束模型在回答末尾的下一步建议中，
 * 只推荐当前租户已注册、已启用且当前用户有权限调用的 AI tools。
 */
@Service
public class AiToolGuidanceService {

    private final AiToolPermissionMapper aiToolPermissionMapper;
    private final RolePermissionService rolePermissionService;

    public AiToolGuidanceService(AiToolPermissionMapper aiToolPermissionMapper,
                                 RolePermissionService rolePermissionService) {
        this.aiToolPermissionMapper = aiToolPermissionMapper;
        this.rolePermissionService = rolePermissionService;
    }

    public String buildAvailableToolGuidance() {
        Long tenantId = UserContextUtil.getTenantId();
        ErpUser currentUser = rolePermissionService.getCurrentUser();

        if (tenantId == null || currentUser == null) {
            return """
                    当前未识别到有效的租户或用户上下文。
                    在回答末尾“接下来您可以”时，不要承诺可直接在 AI 中执行任何具体系统操作；
                    只允许建议用户继续追问解释、查看示例，或明确描述其业务目标。
                    """;
        }

        List<AiToolDefinition> allRegisteredTools =
                aiToolPermissionMapper.findAllRegisteredEnabledByTenantId(tenantId);

        if (CollectionUtils.isEmpty(allRegisteredTools)) {
            return """
                    当前租户在 jsh_ai_tool 中没有可用的 AI tools。
                    在回答末尾“接下来您可以”时，不要建议任何可直接在 AI 中执行的系统操作；
                    只允许建议用户继续提问解释类问题、让系统举例或说明流程。
                    """;
        }

        List<AiToolDefinition> permittedTools = new ArrayList<>();
        for (AiToolDefinition tool : allRegisteredTools) {
            if (rolePermissionService.hasToolPermission(currentUser, tool)) {
                permittedTools.add(tool);
            }
        }

        if (permittedTools.isEmpty()) {
            return """
                    当前用户没有可直接调用的 AI tools 权限。
                    在回答末尾“接下来您可以”时，不要建议任何可直接在 AI 中执行的新增、查询、修改、开单、审核等系统操作；
                    只允许建议解释类、示例类、流程类追问。
                    """;
        }

        Set<String> toolNames = new LinkedHashSet<>();
        for (AiToolDefinition tool : permittedTools) {
            if (tool.getToolName() != null && !tool.getToolName().trim().isEmpty()) {
                toolNames.add(tool.getToolName().trim());
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("以下是当前用户在 AI 对话中可直接调用的系统操作能力清单，仅可从这些能力中延展下一步操作建议：\n");
        for (String toolName : toolNames) {
            builder.append("- ").append(toolName).append("\n");
        }
        builder.append("如果某个建议涉及“直接在 AI 中帮用户执行系统操作”，必须能被上面清单中的某一项明确覆盖；")
                .append("如果不能覆盖，就不要建议该操作，改为建议用户继续追问解释、提供条件，或让你演示。");
        return builder.toString();
    }
}
