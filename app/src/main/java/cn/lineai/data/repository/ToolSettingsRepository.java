package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.ChatMode;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolSettingsRepository implements ToolSettingsStore {

    private static final String KEY_MCP_PREFIX = "@linecode_mcp_enabled_";

    private static final Map<String, String> PHONE_CONTROL_TOOL_PERMISSION_MAP;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("phone_screenshot", PhoneControlRepository.PERMISSION_SCREENSHOT);
        map.put("phone_click", PhoneControlRepository.PERMISSION_CLICK);
        map.put("phone_swipe", PhoneControlRepository.PERMISSION_SWIPE);
        map.put("phone_long_press", PhoneControlRepository.PERMISSION_LONG_PRESS);
        map.put("phone_view_hierarchy", PhoneControlRepository.PERMISSION_VIEW_HIERARCHY);
        map.put("phone_click_view", PhoneControlRepository.PERMISSION_VIEW_ACTION);
        map.put("phone_global_action", PhoneControlRepository.PERMISSION_GLOBAL_ACTION);
        PHONE_CONTROL_TOOL_PERMISSION_MAP = java.util.Collections.unmodifiableMap(map);
    }

    private static final Set<String> MODE_LOCAL = java.util.Collections.singleton(EXECUTION_LOCAL);
    private static final Set<String> MODE_REMOTE = java.util.Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(EXECUTION_SSH, EXECUTION_TERMINAL_PROVIDER)));
    private static final Set<String> MODE_ALL = java.util.Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(EXECUTION_LOCAL, EXECUTION_SSH, EXECUTION_TERMINAL_PROVIDER)));

    private static final McpToolConfig[] DEFAULT_CONFIGS = new McpToolConfig[] {
            new McpToolConfig("file_ops", "文件操作", "读取、写入、编辑和删除文件", true,
                    new String[] {"file_read", "file_write", "file_edit", "file_delete", "glob", "list_dir"},
                    MODE_LOCAL),
            new McpToolConfig("http_server", "HTTP 服务器", "启动本地 HTTP 文件服务器", true,
                    new String[] {"http_server"},
                    MODE_LOCAL),
            new McpToolConfig("agent", "Agent", "分派 Agent 处理任务", true,
                    new String[] {"agent", "agent_pipeline"},
                    MODE_ALL),
            new McpToolConfig("phone_control", "手机控制", "通过无障碍服务控制本机操作", true,
                    new String[] {"phone_screenshot", "phone_click", "phone_swipe", "phone_long_press", "phone_view_hierarchy", "phone_click_view", "phone_global_action"},
                    MODE_LOCAL),
            new McpToolConfig("todo", "任务清单", "维护当前会话的 TODO 列表，状态会注入到 system prompt", true,
                    new String[] {"todo_update"},
                    MODE_ALL),
            new McpToolConfig("image_understanding", "图片理解", "读取本地或 SSH 工作区图片并调用已选择的视觉模型理解内容", false,
                    new String[] {"image_understanding"},
                    MODE_ALL),
            new McpToolConfig("image_generation", "图片生成", "调用已选择的生图模型生成图片，并以内联 Markdown 图片返回", false,
                    new String[] {"image_generation"},
                    MODE_ALL),
            new McpToolConfig("shell", "SSH Shell", "通过 SSH 执行 shell 命令", true,
                    new String[] {"shell_execute"},
                    MODE_REMOTE),
            new McpToolConfig("web_search", "网页搜索", "搜索互联网并查看网页内容", false,
                    new String[] {"web_search", "web_fetch"},
                    MODE_ALL)
    };

    private final SettingsRepository settingsRepository;
    private final WebSearchConfigRepository webSearchConfigRepository;
    private final PhoneControlRepository phoneControlRepository;

    public ToolSettingsRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
        webSearchConfigRepository = new WebSearchConfigRepository(context);
        phoneControlRepository = new PhoneControlRepository(context);
    }

    @Override
    public synchronized String getPermissionMode() {
        return normalizePermissionMode(settingsRepository.getString(KEY_PERMISSION_MODE, PERMISSION_AUTO));
    }

    @Override
    public synchronized void setPermissionMode(String mode) {
        settingsRepository.setString(KEY_PERMISSION_MODE, normalizePermissionMode(mode));
    }

    @Override
    public synchronized String getExecutionMode() {
        return normalizeExecutionMode(settingsRepository.getString(KEY_MCP_EXECUTION_MODE, EXECUTION_LOCAL));
    }

    @Override
    public synchronized void setExecutionMode(String mode) {
        settingsRepository.setString(KEY_MCP_EXECUTION_MODE, normalizeExecutionMode(mode));
    }

    @Override
    public synchronized List<McpToolConfig> getConfigs() {
        String executionMode = getExecutionMode();
        ArrayList<McpToolConfig> configs = new ArrayList<>();
        for (McpToolConfig config : DEFAULT_CONFIGS) {
            boolean enabled = getMcpEnabled(executionMode, config);
            configs.add(displayConfigForMode(executionMode, config, enabled));
        }
        return configs;
    }

    @Override
    public synchronized McpSettingsState getMcpSettingsState() {
        return new McpSettingsState(
                getExecutionMode(),
                getConfigs(),
                webSearchConfigRepository.get(),
                getImageUnderstandingModelId(),
                getImageGenerationModelId()
        );
    }

    @Override
    public synchronized WebSearchConfig getWebSearchConfig() {
        return webSearchConfigRepository.get();
    }

    @Override
    public synchronized void setWebSearchConfig(WebSearchConfig config) {
        webSearchConfigRepository.save(config);
    }

    @Override
    public synchronized String getImageUnderstandingModelId() {
        return settingsRepository.getString(KEY_IMAGE_UNDERSTANDING_MODEL_ID, "").trim();
    }

    @Override
    public synchronized void setImageUnderstandingModelId(String modelId) {
        settingsRepository.setString(KEY_IMAGE_UNDERSTANDING_MODEL_ID, modelId == null ? "" : modelId.trim());
    }

    @Override
    public synchronized String getImageGenerationModelId() {
        return settingsRepository.getString(KEY_IMAGE_GENERATION_MODEL_ID, "").trim();
    }

    @Override
    public synchronized void setImageGenerationModelId(String modelId) {
        settingsRepository.setString(KEY_IMAGE_GENERATION_MODEL_ID, modelId == null ? "" : modelId.trim());
    }

    @Override
    public synchronized void setMcpEnabled(String id, boolean enabled) {
        if (id == null || id.length() == 0) {
            return;
        }
        settingsRepository.setBoolean(mcpEnabledKey(getExecutionMode(), id), enabled);
    }

    private boolean getMcpEnabled(String executionMode, McpToolConfig config) {
        Map<String, String> settings = settingsRepository.getLineCodeSettings();
        String key = mcpEnabledKey(executionMode, config.getId());
        if (settings.containsKey(key)) {
            return settingsRepository.getBoolean(key, config.isEnabled());
        }
        String mode = normalizeExecutionMode(executionMode);
        if (EXECUTION_SSH.equals(mode) || EXECUTION_TERMINAL_PROVIDER.equals(mode)) {
            return config.isEnabled();
        }
        return settingsRepository.getBoolean(KEY_MCP_PREFIX + config.getId(), config.isEnabled());
    }

    static String mcpEnabledKey(String executionMode, String id) {
        String mode = normalizeExecutionMode(executionMode);
        if (EXECUTION_SSH.equals(mode) || EXECUTION_TERMINAL_PROVIDER.equals(mode)) {
            return KEY_MCP_PREFIX + mode + "_" + id;
        }
        return KEY_MCP_PREFIX + id;
    }

    static McpToolConfig displayConfigForMode(String executionMode, McpToolConfig config, boolean enabled) {
        if ("shell".equals(config.getId()) && EXECUTION_TERMINAL_PROVIDER.equals(normalizeExecutionMode(executionMode))) {
            return new McpToolConfig(config.getId(), "IPC Shell", "通过终端提供者 IPC 执行 shell 命令", enabled, config.getTools(), config.getSupportedExecutionModes());
        }
        return new McpToolConfig(config.getId(), config.getName(), config.getDescription(), enabled, config.getTools(), config.getSupportedExecutionModes());
    }

    @Override
    public synchronized Set<String> getEnabledToolNames() {
        String executionMode = getExecutionMode();
        String permissionMode = getPermissionMode();
        HashSet<String> enabled = new HashSet<>();
        for (McpToolConfig config : getConfigs()) {
            if (!config.isEnabled()) {
                continue;
            }
            Set<String> modes = config.getSupportedExecutionModes();
            if (modes != null && !modes.contains(executionMode)) {
                continue;
            }
            for (String tool : config.getTools()) {
                ToolCategory category = getToolCategory(tool);
                if (PERMISSION_READONLY.equals(permissionMode)
                        && !isReadonlyToolAllowedForMode(executionMode, tool, category)) {
                    continue;
                }
                enabled.add(tool);
            }
        }
        String chatMode = settingsRepository.getString("@linecode_chat_mode", ChatMode.DEFAULT);
        if (!ChatMode.CONTROL.equals(chatMode)) {
            java.util.Iterator<String> iterator = enabled.iterator();
            while (iterator.hasNext()) {
                String tool = iterator.next();
                if (tool != null && tool.startsWith("phone_")) {
                    iterator.remove();
                }
            }
        } else {
            java.util.Iterator<String> iterator = enabled.iterator();
            while (iterator.hasNext()) {
                String tool = iterator.next();
                String permissionId = PHONE_CONTROL_TOOL_PERMISSION_MAP.get(tool);
                if (permissionId != null && !phoneControlRepository.isPermissionEnabled(permissionId)) {
                    iterator.remove();
                }
            }
        }
        return enabled;
    }

    @Override
    public synchronized Set<String> getEnabledToolNames(Collection<BaseTool> implementedTools) {
        HashSet<String> enabled = new HashSet<>(getEnabledToolNames());
        HashSet<String> implementedNames = new HashSet<>();
        if (implementedTools != null) {
            for (BaseTool tool : implementedTools) {
                if (tool == null || tool.getName().length() == 0) {
                    continue;
                }
                implementedNames.add(tool.getName());
                if (isEnabledExtensionTool(tool.getName(), tool.getCategory())) {
                    enabled.add(tool.getName());
                }
            }
        }
        if (!implementedNames.isEmpty()) {
            enabled.retainAll(implementedNames);
        }
        return enabled;
    }

    @Override
    public synchronized PermissionResult canExecuteTool(String toolName, ToolCategory category) {
        if (toolName == null || toolName.length() == 0) {
            return PermissionResult.denied("工具名为空");
        }
        if (!getEnabledToolNames().contains(toolName)) {
            if (!isEnabledExtensionTool(toolName, category)) {
                return PermissionResult.denied("工具未启用或当前执行目标不可用: " + toolName);
            }
        }
        String executionMode = getExecutionMode();
        if (PERMISSION_READONLY.equals(getPermissionMode())
                && !isReadonlyToolAllowedForMode(executionMode, toolName, category)) {
            return PermissionResult.denied("只读模式下不允许执行 " + toolName + "。请在权限设置中切换到自动或确认模式。");
        }
        return PermissionResult.allowed();
    }

    @Override
    public synchronized boolean needsConfirmation(String toolName) {
        if ("file_delete".equals(toolName) || "shell_execute".equals(toolName)) {
            return true;
        }
        return PERMISSION_CONFIRM.equals(getPermissionMode());
    }

    @Override
    public synchronized String buildToolPrompt(Set<String> implementedToolNames) {
        return buildToolPrompt(implementedToolNames, false);
    }

    @Override
    public synchronized String buildToolPrompt(Set<String> implementedToolNames, boolean nativeToolProtocol) {
        Set<String> enabled = getEnabledToolNames();
        if (implementedToolNames != null) {
            enabled.retainAll(implementedToolNames);
        }
        return renderToolPrompt(getExecutionMode(), getConfigs(), enabled, new LinkedHashMap<>(), nativeToolProtocol);
    }

    @Override
    public synchronized String buildToolPrompt(Collection<BaseTool> implementedTools, boolean nativeToolProtocol) {
        Set<String> enabled = getEnabledToolNames();
        LinkedHashMap<String, BaseTool> toolByName = new LinkedHashMap<>();
        if (implementedTools != null) {
            for (BaseTool tool : implementedTools) {
                if (tool != null && tool.getName().length() > 0) {
                    toolByName.put(tool.getName(), tool);
                    if (isEnabledExtensionTool(tool.getName(), tool.getCategory())) {
                        enabled.add(tool.getName());
                    }
                }
            }
            enabled.retainAll(toolByName.keySet());
        }
        return renderToolPrompt(getExecutionMode(), getConfigs(), enabled, toolByName, nativeToolProtocol);
    }

    static String renderToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        return ToolPromptRenderer.renderToolPrompt(configs, enabled, toolByName, nativeToolProtocol);
    }

    static String renderToolPrompt(
            String executionMode,
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        return ToolPromptRenderer.renderToolPrompt(executionMode, configs, enabled, toolByName, nativeToolProtocol);
    }

    private static String renderSshToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        return ToolPromptRenderer.renderToolPrompt(EXECUTION_SSH, configs, enabled, toolByName, nativeToolProtocol);
    }

    private static String renderTerminalProviderToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        return ToolPromptRenderer.renderToolPrompt(EXECUTION_TERMINAL_PROVIDER, configs, enabled, toolByName, nativeToolProtocol);
    }

    private static String categoryLabel(ToolCategory category) {
        return ToolPromptRenderer.categoryLabel(category);
    }

    private static void appendExtensionTools(
            StringBuilder builder,
            Set<String> enabled,
            Set<String> renderedTools,
            Map<String, BaseTool> toolByName
    ) {
        // delegated to ToolPromptRenderer
    }

    public static String normalizePermissionMode(String mode) {
        if (PERMISSION_READONLY.equals(mode) || "manual".equals(mode)) {
            return PERMISSION_READONLY;
        }
        if (PERMISSION_CONFIRM.equals(mode) || "ask".equals(mode)) {
            return PERMISSION_CONFIRM;
        }
        return PERMISSION_AUTO;
    }

    public static String normalizeExecutionMode(String mode) {
        if (EXECUTION_SSH.equals(mode)) return EXECUTION_SSH;
        if (EXECUTION_TERMINAL_PROVIDER.equals(mode)) return EXECUTION_TERMINAL_PROVIDER;
        return EXECUTION_LOCAL;
    }

    public static ToolCategory getToolCategory(String toolName) {
        ToolDisplayCategory display = cn.lineai.ui.component.toolcall.ToolCallUtils.getDisplayCategory(toolName);
        if (display == ToolDisplayCategory.READ) return ToolCategory.READ;
        if (display == ToolDisplayCategory.WRITE) return ToolCategory.WRITE;
        if (display == ToolDisplayCategory.DELETE) return ToolCategory.WRITE;
        if (display == ToolDisplayCategory.IMAGE_GENERATION) return ToolCategory.GENERATE;
        return ToolCategory.SYSTEM;
    }

    private static boolean isReadonlyAllowed(ToolCategory category) {
        return category == ToolCategory.READ || category == ToolCategory.GENERATE;
    }

    static boolean isReadonlyAlwaysAllowed(String toolName) {
        return "todo_update".equals(toolName);
    }

    static boolean isReadonlyToolAllowedForMode(String executionMode, String toolName, ToolCategory category) {
        if (isReadonlyAllowed(category) || isReadonlyAlwaysAllowed(toolName)) {
            return true;
        }
        if (!EXECUTION_SSH.equals(executionMode) && !EXECUTION_TERMINAL_PROVIDER.equals(executionMode)) {
            return false;
        }
        return "shell_execute".equals(toolName)
                || "agent".equals(toolName)
                || "agent_pipeline".equals(toolName);
    }

    private boolean isEnabledExtensionTool(String toolName, ToolCategory category) {
        if (!ToolRegistry.isExtensionToolName(toolName)) {
            return false;
        }
        String executionMode = getExecutionMode();
        if (EXECUTION_SSH.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (EXECUTION_TERMINAL_PROVIDER.equals(executionMode) && !ToolRegistry.isCustomMcpToolName(toolName)) {
            return false;
        }
        if (!EXECUTION_LOCAL.equals(executionMode) && !EXECUTION_SSH.equals(executionMode) && !EXECUTION_TERMINAL_PROVIDER.equals(executionMode)) {
            return false;
        }
        return !PERMISSION_READONLY.equals(getPermissionMode()) || isReadonlyAllowed(category);
    }
}
