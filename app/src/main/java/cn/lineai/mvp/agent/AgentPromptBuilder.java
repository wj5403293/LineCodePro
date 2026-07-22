package cn.lineai.mvp.agent;

import cn.lineai.ai.prompt.StringTemplate;
import cn.lineai.ai.protocol.ModelProtocolFactory;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ModelConfig;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.builtin.AgentTool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class AgentPromptBuilder {
    private static final String MODE_LOCAL = "local";
    private static final String MODE_REMOTE = "remote";

    private static final Map<String, String> ROLE_TEMPLATE_MAP = new HashMap<>();
    static {
        ROLE_TEMPLATE_MAP.put(AgentTool.TYPE_EXPLORE + ":" + MODE_REMOTE, PromptTemplateRepository.ID_AGENT_ROLE_EXPLORE_REMOTE);
        ROLE_TEMPLATE_MAP.put(AgentTool.TYPE_SUB_CODING + ":" + MODE_REMOTE, PromptTemplateRepository.ID_AGENT_ROLE_CODING_REMOTE);
        ROLE_TEMPLATE_MAP.put(AgentTool.TYPE_EXPLORE + ":" + MODE_LOCAL, PromptTemplateRepository.ID_AGENT_ROLE_EXPLORE_LOCAL);
        ROLE_TEMPLATE_MAP.put(AgentTool.TYPE_SUB_CODING + ":" + MODE_LOCAL, PromptTemplateRepository.ID_AGENT_ROLE_CODING_LOCAL);
    }

    private final ExtensionRepository extensionRepository;
    private final ToolSettingsStore toolSettingsRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ModelProtocolFactory modelProtocolFactory = new ModelProtocolFactory();
    private android.content.Context context;

    public AgentPromptBuilder(
            ExtensionRepository extensionRepository,
            ToolSettingsStore toolSettingsRepository,
            PromptTemplateRepository promptTemplateRepository
    ) {
        this.extensionRepository = extensionRepository;
        this.toolSettingsRepository = toolSettingsRepository;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    public void setContext(android.content.Context context) {
        this.context = context;
    }

    private String string(int resId, String fallback) {
        return context != null ? context.getString(resId) : fallback;
    }

    public static void registerRoleTemplate(String type, String mode, String templateId) {
        ROLE_TEMPLATE_MAP.put(type + ":" + mode, templateId);
    }

    public String agentSystemPrompt(
            String type,
            String description,
            List<String> readScope,
            List<String> writeScope,
            String homePath,
            ModelConfig selectedModel,
            List<ToolInfo> agentTools,
            AgentExecutionController.Host host
    ) {
        host.syncModePermission();
        boolean remoteMode = host != null && (host.isSshExecutionMode() || host.isTerminalProviderExecutionMode());
        HashMap<String, String> values = new HashMap<>();
        values.put("ROLE_PROMPT", agentRolePrompt(type, remoteMode));
        values.put("TASK_DESCRIPTION", description);
        values.put("WORKSPACE_CONTEXT", agentWorkspacePrompt(homePath, host, remoteMode));
        values.put("SCOPE_CONTEXT", agentScopePrompt(type, readScope, writeScope, remoteMode));
        values.put("EXTENSIONS_CONTEXT", extensionRepository.buildExtensionPrompt(homePath));
        values.put("TOOLS_CONTEXT", toolSettingsRepository.buildToolPrompt(new ArrayList<>(agentTools), modelProtocolFactory.create(selectedModel.getProtocolType()).supportsNativeTools(selectedModel)));
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_AGENT_SYSTEM_PROMPT)).render(values);
    }

    public String agentRolePrompt(String type) {
        return agentRolePrompt(type, false);
    }

    public String agentRolePrompt(String type, boolean remoteMode) {
        String mode = remoteMode ? MODE_REMOTE : MODE_LOCAL;
        String templateId = ROLE_TEMPLATE_MAP.get(type + ":" + mode);
        if (templateId == null) {
            templateId = ROLE_TEMPLATE_MAP.get(AgentTool.TYPE_SUB_CODING + ":" + mode);
        }
        if (promptTemplateRepository != null && templateId != null) {
            return promptTemplateRepository.getTemplateText(templateId);
        }
        return fallbackRolePrompt(type, remoteMode);
    }

    public String agentWorkspacePrompt(String homePath, AgentExecutionController.Host host) {
        return agentWorkspacePrompt(homePath, host, host != null && (host.isSshExecutionMode() || host.isTerminalProviderExecutionMode()));
    }

    public String agentWorkspacePrompt(String homePath, AgentExecutionController.Host host, boolean remoteMode) {
        String path = homePath == null || homePath.trim().length() == 0 ? promptHomePath(host) : homePath.trim();
        String modeHint = remoteMode
                ? "\n当前是 SSH 远端或终端提供者模式：所有命令作用于远端主机上的工作区，文件路径按远端约定解析。"
                : "\n所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 API key、token、密码等敏感数据。";
        return "当前工作区: " + path + modeHint;
    }

    public String agentScopePrompt(String type, List<String> readScope, List<String> writeScope) {
        return agentScopePrompt(type, readScope, writeScope, false);
    }

    public String agentScopePrompt(String type, List<String> readScope, List<String> writeScope, boolean remoteMode) {
        StringBuilder builder = new StringBuilder();
        builder.append("## Agent 范围\n");
        builder.append("read_scope: ").append(scopeSummary(readScope)).append('\n');
        builder.append("write_scope: ").append(scopeSummary(writeScope)).append('\n');
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            builder.append("这是 explore Agent，write_scope 必须视为无效，禁止任何写入。");
        } else if (writeScope == null || writeScope.isEmpty()) {
            builder.append("没有授权写入范围。禁止写入文件；如果任务需要修改文件，直接说明需要主模型重新分配 write_scope。");
        } else {
            builder.append("只能写入 write_scope 覆盖的路径。不要修改其它文件，不要把多个 Agent 的职责混到同一个文件里。");
        }
        if (remoteMode) {
            builder.append("\n注意：所有路径都是远端主机上的路径；写入/读取都要通过 shell_execute 调用 sed/awk/python heredoc/cat 等命令，不要尝试调用本地 file 类工具。");
        }
        return builder.toString();
    }

    public String scopeSummary(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "未声明";
        }
        StringBuilder builder = new StringBuilder();
        for (String scope : scopes) {
            if (scope == null || scope.trim().length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(scope.trim());
        }
        return builder.length() == 0 ? "未声明" : builder.toString();
    }

    public ArrayList<String> scopeList(org.json.JSONArray array) {
        ArrayList<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    public HashSet<String> scopeSet(org.json.JSONArray array) {
        HashSet<String> values = new HashSet<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    public ArrayList<String> dependencyList(org.json.JSONArray array) {
        ArrayList<String> dependencies = new ArrayList<>();
        if (array == null) {
            return dependencies;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (value.length() > 0) {
                dependencies.add(value);
            }
        }
        return dependencies;
    }

    private String fallbackRolePrompt(String type, boolean remoteMode) {
        if (remoteMode) {
            return AgentTool.TYPE_EXPLORE.equals(type)
                    ? string(cn.lineai.R.string.agent_fallback_role_explore_remote,
                    "You are an exploration Agent in a remote Shell environment. You can use shell_execute to execute read-only commands.\nRules:\n- Only read code, do not make any modifications.\n- Prefer read-only tools to search and read key files; you can use shell_execute to execute read-only commands.")
                    : string(cn.lineai.R.string.agent_fallback_role_coding_remote,
                    "You are a coding Agent in a remote Shell environment (the current workspace is on an SSH remote or terminal provider container; local file_read / file_write / file_edit / glob / list_dir may not be available).\nRules:\n- Most work must be done via shell_execute: read with cat/ls/grep, write with sed/awk/python heredoc or tee, verify with cat.\n- Only modify files or directories listed in write_scope; when there is no write_scope, writing to files is prohibited.");
        } else {
            return AgentTool.TYPE_EXPLORE.equals(type)
                    ? string(cn.lineai.R.string.agent_fallback_role_explore_local,
                    "You are a code exploration Agent. Your task is to quickly locate and analyze code.\nRules:\n- Only read code, do not make any modifications, and do not call any write tools.\n- Prefer read-only tools to search and read key files; you can use shell_execute to execute read-only commands.\n- Provide concise and accurate answers.")
                    : string(cn.lineai.R.string.agent_fallback_role_coding_local,
                    "You are a coding Agent. Your task is to complete well-scoped coding subtasks.\nRules:\n- Only modify files or directories listed in write_scope; when there is no write_scope, writing to files is prohibited.\n- Prefer file_read / file_write / file_edit / glob / list_dir to complete work; only use shell_execute when file tools truly cannot meet the need.");
        }
    }

    private String promptHomePath(AgentExecutionController.Host host) {
        if ("ssh".equals(host.projectSource()) && host.projectPath().length() == 0) {
            return "~";
        }
        return host.projectPath();
    }
}
