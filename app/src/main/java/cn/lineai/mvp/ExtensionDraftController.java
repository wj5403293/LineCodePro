package cn.lineai.mvp;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.data.repository.ExtensionStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.GlobTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class ExtensionDraftController {
    private final ModelStore modelRepository;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ToolSettingsStore toolSettingsRepository;
    private final ExtensionStore extensionRepository;
    private Context context;

    ExtensionDraftController(
            ModelStore modelRepository,
            ModelClient modelClient,
            ToolRegistry toolRegistry,
            ToolSettingsStore toolSettingsRepository,
            ExtensionStore extensionRepository
    ) {
        this.modelRepository = modelRepository;
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
        this.toolSettingsRepository = toolSettingsRepository;
        this.extensionRepository = extensionRepository;
    }

    void setContext(Context context) {
        this.context = context;
    }

    private String string(int resId, String fallback) {
        return context != null ? context.getString(resId) : fallback;
    }

    ExtensionAgentConfig generateAgentDraft(String description) throws Exception {
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            throw new IllegalStateException("缺少模型，请先在设置中添加并选择一个模型。");
        }
        String request = safe(description).trim();
        if (request.length() == 0) {
            throw new IllegalArgumentException("请先描述你想创建的 Agent。");
        }
        ArrayList<BaseTool> tools = getAvailableTools();
        JSONArray toolList = new JSONArray();
        HashSet<String> allowedTools = new HashSet<>();
        for (BaseTool tool : tools) {
            allowedTools.add(tool.getName());
            toolList.put(new JSONObject()
                    .put("name", tool.getName())
                    .put("category", tool.getCategory().name().toLowerCase(Locale.ROOT))
                    .put("description", tool.getDescription()));
        }
        JSONArray mcpList = agentMcpOptionsJson();
        HashSet<String> allowedMcps = agentMcpOptionIds();
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new SystemModelMessage("你是 LineCode 的自定义 Agent 配置生成器。\n"
                + "根据用户描述生成一个 Agent 配置草稿，只输出 JSON 对象，不要 Markdown，不要解释。\n"
                + "JSON 字段必须是：name, slug, prompt, trigger, toolNames, mcpIds。\n"
                + "name 使用简短中文名；slug 使用小写英文、数字、- 或 _，必须以小写字母开头。\n"
                + "prompt 要写成可直接作为 Agent 系统提示词使用的中文说明，包含角色、任务边界、工作流程、输出要求和安全约束。\n"
                + "trigger 用中文描述何时适合触发这个 Agent。\n"
                + "toolNames 只能从可用工具 name 中选择；mcpIds 只能从可用 MCP id 中选择；不确定时优先选择 file_read 和 glob。"));
        messages.add(new UserModelMessage(new JSONObject()
                .put("userNeed", request)
                .put("availableTools", toolList)
                .put("availableMcp", mcpList)
                .toString(2)));
        ModelCompletionResponse response = modelClient.complete(selectedModel, messages);
        JSONObject draft = extractJsonObject(response.getText());
        String name = draft.optString("name").trim();
        String slug = normalizeAgentSlug(draft.optString("slug", name));
        String prompt = draft.optString("prompt").trim();
        String trigger = draft.optString("trigger").trim();
        if (name.length() == 0 || slug.length() == 0 || prompt.length() == 0) {
            throw new IllegalStateException(string(R.string.extension_draft_missing_fields, "AI returned config missing name, slug, or prompt."));
        }
        List<String> selectedTools = filteredStringArray(draft.optJSONArray("toolNames"), allowedTools);
        if (selectedTools.isEmpty()) {
            selectedTools = defaultAgentTools(allowedTools);
        }
        List<String> selectedMcps = filteredStringArray(draft.optJSONArray("mcpIds"), allowedMcps);
        return new ExtensionAgentConfig("", true, name, slug, prompt, trigger, selectedTools, selectedMcps, 0, 0);
    }

    ArrayList<BaseTool> getAvailableTools() {
        toolRegistry.reloadExtensions();
        ArrayList<BaseTool> tools = new ArrayList<>();
        for (BaseTool tool : toolRegistry.getAll()) {
            if (tool != null && !ToolRegistry.isExtensionToolName(tool.getName())) {
                tools.add(tool);
            }
        }
        return tools;
    }

    private JSONArray agentMcpOptionsJson() throws Exception {
        JSONArray array = new JSONArray();
        for (McpToolConfig config : toolSettingsRepository.getConfigs()) {
            JSONArray tools = new JSONArray();
            for (String tool : config.getTools()) {
                tools.put(tool);
            }
            array.put(new JSONObject()
                    .put("id", "builtin:" + config.getId())
                    .put("name", config.getName())
                    .put("description", tools.toString()));
        }
        for (ExtensionMcpConfig mcp : extensionRepository.getMcpExtensions()) {
            if (!mcp.isEnabled()) {
                continue;
            }
            JSONArray tools = new JSONArray();
            for (McpToolSummary tool : mcp.getTools()) {
                if (tool.isEnabled()) {
                    tools.put(tool.getName());
                }
            }
            array.put(new JSONObject()
                    .put("id", "custom:" + mcp.getId())
                    .put("name", mcp.getName())
                    .put("description", tools.toString()));
        }
        return array;
    }

    private HashSet<String> agentMcpOptionIds() {
        HashSet<String> ids = new HashSet<>();
        for (McpToolConfig config : toolSettingsRepository.getConfigs()) {
            ids.add("builtin:" + config.getId());
        }
        for (ExtensionMcpConfig mcp : extensionRepository.getMcpExtensions()) {
            if (mcp.isEnabled()) {
                ids.add("custom:" + mcp.getId());
            }
        }
        return ids;
    }

    private JSONObject extractJsonObject(String text) throws Exception {
        String source = safe(text);
        int fenceStart = source.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = source.indexOf('\n', fenceStart);
            int fenceEnd = contentStart < 0 ? -1 : source.indexOf("```", contentStart + 1);
            if (contentStart >= 0 && fenceEnd > contentStart) {
                source = source.substring(contentStart + 1, fenceEnd);
            }
        }
        int start = source.indexOf('{');
        int end = source.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException(string(R.string.extension_draft_invalid_json, "AI did not return valid JSON."));
        }
        return new JSONObject(source.substring(start, end + 1));
    }

    private List<String> filteredStringArray(JSONArray array, Set<String> allowed) {
        if (array == null || allowed == null || allowed.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (allowed.contains(value) && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> defaultAgentTools(Set<String> allowed) {
        ArrayList<String> values = new ArrayList<>();
        if (allowed.contains(FileReadTool.NAME)) {
            values.add(FileReadTool.NAME);
        }
        if (allowed.contains(GlobTool.NAME)) {
            values.add(GlobTool.NAME);
        }
        if (values.isEmpty() && !allowed.isEmpty()) {
            values.add(allowed.iterator().next());
        }
        return values;
    }

    private String normalizeAgentSlug(String value) {
        String raw = safe(value).trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < raw.length() && builder.length() < 48; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
                lastDash = false;
            } else if (ch == '-') {
                if (!lastDash && builder.length() > 0) {
                    builder.append(ch);
                    lastDash = true;
                }
            } else if (!lastDash && builder.length() > 0) {
                builder.append('-');
                lastDash = true;
            }
        }
        String clean = builder.toString();
        while (clean.endsWith("-") || clean.endsWith("_")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.length() == 0) {
            clean = "custom-agent";
        }
        char first = clean.charAt(0);
        if (first < 'a' || first > 'z') {
            clean = "agent-" + clean;
        }
        return clean;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
