package cn.lineai.mvp.agent;

import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.ToolCallTextParser;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ModelConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgentExecutionController {
    private static final int AGENT_MAX_TURNS = 8;
    private static final String AGENT_TERMINATED_MESSAGE = "Agent 已终止。";

    private final ModelClient modelClient;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ToolSettingsRepository toolSettingsRepository;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final ExtensionRepository extensionRepository;

    public interface Host {
        String projectPath();

        String projectSource();

        void syncModePermission();

        void addOrReplaceToolResult(ToolResult result);

        void render();

        void scheduleAgentProgressRender(AgentProgressSession session);

        void postToolProgress(int generationId, ModelCancellationToken cancellationToken, String toolCallId, String toolName, String content, boolean error);
    }

    public AgentExecutionController(
            ModelClient modelClient,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ToolSettingsRepository toolSettingsRepository,
            ToolExecutor toolExecutor,
            ToolRegistry toolRegistry,
            ExtensionRepository extensionRepository
    ) {
        this.modelClient = modelClient;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.toolSettingsRepository = toolSettingsRepository;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.extensionRepository = extensionRepository;
    }

    public ToolResult runAgentTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            Host host
    ) {
        if (selectedModel == null) {
            return new ToolResult("", "agent", "当前没有可用模型，无法运行 Agent。", true);
        }
        String type = AgentTool.normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        ArrayList<String> readScope = scopeList(input.optJSONArray("read_scope"));
        ArrayList<String> writeScope = scopeList(input.optJSONArray("write_scope"));
        Set<String> customToolNames = scopeSet(input.optJSONArray("custom_tool_names"));
        Set<String> customMcpIds = scopeSet(input.optJSONArray("custom_mcp_ids"));
        String homePath = parentContext == null ? host.projectPath() : parentContext.getHomePath();
        AgentProgressSession progress = new AgentProgressSession(
                generationId,
                parentContext == null ? "" : parentContext.getToolCallId(),
                "agent",
                type,
                description
        );
        AgentRunResult result = runAgentLoop(
                type,
                description,
                prompt,
                readScope,
                writeScope,
                homePath,
                selectedModel,
                cancellationToken,
                progress,
                host,
                customToolNames,
                customMcpIds
        );
        StringBuilder builder = new StringBuilder();
        builder.append("Agent 完成: ").append(description).append('\n')
                .append("类型: ").append(type).append('\n')
                .append("工具调用: ").append(result.getToolCallCount()).append('\n')
                .append("输出:\n").append(result.getOutput());
        progress.setTurnResult(result.getOutput(), "");
        progress.setFinished(result.isError() ? "error" : "done", result.isError(), builder.toString().trim());
        host.addOrReplaceToolResult(progress.snapshotResult());
        host.render();
        return new ToolResult("", "agent", progress.snapshotResult().getContent(), result.isError());
    }

    public ToolResult runAgentPipelineTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            Host host
    ) {
        if (selectedModel == null) {
            return new ToolResult("", "agent_pipeline", "当前没有可用模型，无法运行 Agent 流水线。", true);
        }
        ArrayList<PipelineAgent> agents = parsePipelineAgents(input.optJSONArray("agents"));
        if (agents.isEmpty()) {
            return new ToolResult("", "agent_pipeline", "agent_pipeline.agents 不能为空。", true);
        }
        String dependencyError = validatePipelineDependencies(agents);
        if (dependencyError.length() > 0) {
            return new ToolResult("", "agent_pipeline", dependencyError, true);
        }
        ArrayList<ArrayList<PipelineAgent>> levels = dependencyLevels(agents);
        if (levels.isEmpty()) {
            return new ToolResult("", "agent_pipeline", "Agent 流水线存在循环依赖或重复 id，无法执行。", true);
        }

        LinkedHashMap<String, AgentRunResult> results = new LinkedHashMap<>();
        StringBuilder summary = new StringBuilder();
        summary.append("Agent 流水线完成: ").append(agents.size()).append(" 个任务");
        PipelineProgressSession pipelineProgress = new PipelineProgressSession(parentContext, agents);
        pipelineProgress.publish(false);
        boolean hasError = false;
        int totalToolCalls = 0;
        for (ArrayList<PipelineAgent> level : levels) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                pipelineProgress.terminate();
                return new ToolResult("", "agent_pipeline", "Agent 流水线已终止。", true);
            }
            for (PipelineAgent agent : level) {
                String agentPrompt = agent.getPrompt() + dependencyOutputContext(agent, results);
                pipelineProgress.beginAgent(agent);
                AgentProgressSession agentProgress = new AgentProgressSession(
                        generationId,
                        "",
                        "agent",
                        agent.getType(),
                        agent.getDescription(),
                        (payload, progressError) -> pipelineProgress.updateAgent(agent, payload, progressError)
                );
                AgentRunResult result = runAgentLoop(
                        agent.getType(),
                        agent.getDescription(),
                        agentPrompt,
                        agent.getReadScope(),
                        agent.getWriteScope(),
                        parentContext == null ? host.projectPath() : parentContext.getHomePath(),
                        selectedModel,
                        cancellationToken,
                        agentProgress,
                        host,
                        Collections.emptySet(),
                        Collections.emptySet()
                );
                pipelineProgress.finishAgent(agent, result);
                results.put(agent.getId(), result);
                totalToolCalls += result.getToolCallCount();
                hasError = hasError || result.isError();
                summary.append("\n\n## ").append(agent.getId()).append(" · ").append(agent.getDescription())
                        .append('\n').append("类型: ").append(agent.getType())
                        .append('\n').append("状态: ").append(result.isError() ? "error" : "done")
                        .append('\n').append("工具调用: ").append(result.getToolCallCount())
                        .append('\n').append(result.getOutput());
            }
        }
        summary.append("\n\n总工具调用: ").append(totalToolCalls);
        return new ToolResult("", "agent_pipeline", summary.toString().trim(), hasError);
    }

    public AgentRunResult runAgentLoop(
            String type,
            String description,
            String prompt,
            List<String> readScope,
            List<String> writeScope,
            String homePath,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            AgentProgressSession progress,
            Host host,
            Set<String> customToolNames,
            Set<String> customMcpIds
    ) {
        host.syncModePermission();
        ArrayList<BaseTool> agentTools = agentTools(type, host, customToolNames, customMcpIds);
        Set<String> allowedToolNames = toolNames(agentTools);
        ArrayList<ModelMessage> agentMessages = new ArrayList<>();
        agentMessages.add(new SystemModelMessage(agentSystemPrompt(type, description, readScope, writeScope, homePath, selectedModel, agentTools, host)));
        agentMessages.add(new UserModelMessage(prompt));
        int toolCallCount = 0;
        String lastOutput = "";

        for (int turn = 0; turn < AGENT_MAX_TURNS; turn++) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                if (progress != null) {
                    progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
            }
            try {
                if (progress != null) {
                    progress.beginTurn();
                    host.scheduleAgentProgressRender(progress);
                }
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                List<BaseTool> nativeTools = supportsNativeTools(selectedModel) ? agentTools : new ArrayList<>();
                ModelRequestOptions options = new ModelRequestOptions(
                        aiSettings.getReasoningEffort(),
                        aiSettings.isPreserveReasoningEnabled(),
                        nativeTools
                );
                ModelStreamCallback callback = progress == null ? null : new ModelStreamCallback() {
                    @Override
                    public void onTextDelta(String delta) {
                        progress.appendText(delta);
                        host.scheduleAgentProgressRender(progress);
                    }

                    @Override
                    public void onReasoningDelta(String delta) {
                        progress.appendThinking(delta);
                        host.scheduleAgentProgressRender(progress);
                    }
                };
                ModelCompletionResponse response = modelClient.stream(selectedModel, agentMessages, callback, cancellationToken, options);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    if (progress != null) {
                        progress.setTurnResult(AGENT_TERMINATED_MESSAGE, response.getReasoningContent());
                        progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                        host.addOrReplaceToolResult(progress.snapshotResult());
                        host.render();
                    }
                    return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
                }
                ToolCallTextParser.Result parsedTextToolCalls = ToolCallTextParser.parse(response.getText());
                List<ToolCall> calls = mergeToolCalls(response.getToolCalls(), parsedTextToolCalls.getToolCalls());
                String output = parsedTextToolCalls.hasToolMarkup() ? parsedTextToolCalls.getText() : response.getText();
                if (progress != null) {
                    progress.setTurnResult(output, response.getReasoningContent());
                    progress.addToolCalls(calls);
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                if (output.trim().length() > 0) {
                    lastOutput = output;
                }
                if (calls.isEmpty()) {
                    String finalOutput = lastOutput.trim().length() == 0 ? "Agent 没有返回文本。" : lastOutput;
                    return new AgentRunResult(finalOutput, toolCallCount, false);
                }

                agentMessages.add(new AssistantModelMessage(output, response.getReasoningContent(), calls));
                for (ToolCall call : calls) {
                    if (cancellationToken != null && cancellationToken.isCancelled()) {
                        if (progress != null) {
                            progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                            host.addOrReplaceToolResult(progress.snapshotResult());
                            host.render();
                        }
                        return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
                    }
                    ToolResult toolResult = executeAgentToolCall(call, allowedToolNames, type, writeScope, homePath, host);
                    toolCallCount++;
                    if (progress != null) {
                        progress.putToolResult(call, toolResult);
                        host.addOrReplaceToolResult(progress.snapshotResult());
                        host.render();
                    }
                    agentMessages.add(new ToolModelMessage(
                            toolResult.getContent(),
                            toolResult.getToolCallId(),
                            toolResult.getToolName(),
                            toolResult.isError()
                    ));
                }
            } catch (ModelCompletionException e) {
                if (progress != null) {
                    progress.setFinished("error", true, "Agent 模型通信失败：\n" + e.getMessage());
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                return new AgentRunResult("Agent 模型通信失败：\n" + e.getMessage(), toolCallCount, true);
            } catch (Exception e) {
                if (progress != null) {
                    progress.setFinished("error", true, "Agent 执行失败：\n" + e.getMessage());
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                return new AgentRunResult("Agent 执行失败：\n" + e.getMessage(), toolCallCount, true);
            }
        }
        if (progress != null) {
            progress.setFinished("error", true, "Agent 达到最大轮次 " + AGENT_MAX_TURNS + "，最后输出：\n" + lastOutput);
            host.addOrReplaceToolResult(progress.snapshotResult());
            host.render();
        }
        return new AgentRunResult("Agent 达到最大轮次 " + AGENT_MAX_TURNS + "，最后输出：\n" + lastOutput, toolCallCount, true);
    }

    public ArrayList<BaseTool> agentTools(String type, Host host, Set<String> customToolNames, Set<String> customMcpIds) {
        host.syncModePermission();
        ArrayList<BaseTool> tools = new ArrayList<>();
        Set<String> enabled = toolSettingsRepository.getEnabledToolNames();
        Set<String> allowedMcpToolNames = toolRegistry.mcpToolNamesForIds(new ArrayList<>(customMcpIds));
        for (BaseTool tool : toolRegistry.getByNameSet(enabled)) {
            if (tool == null || !isAgentToolAllowed(tool, type, customToolNames, allowedMcpToolNames)) {
                continue;
            }
            tools.add(tool);
        }
        return tools;
    }

    public boolean isAgentToolAllowed(BaseTool tool, String type, Set<String> customToolNames, Set<String> allowedMcpToolNames) {
        String name = tool.getName();
        if ("agent".equals(name) || "agent_pipeline".equals(name)) {
            return false;
        }
        if (!allowedMcpToolNames.isEmpty() && allowedMcpToolNames.contains(name)) {
            return true;
        }
        if (!customToolNames.isEmpty() && !customToolNames.contains(name)) {
            return false;
        }
        if (isRemoteExecutionMode()) {
            return true;
        }
        if ("file_delete".equals(name)) {
            return false;
        }
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return tool.getCategory() == ToolCategory.READ;
        }
        if ("shell_execute".equals(name)) {
            return true;
        }
        return tool.getCategory() == ToolCategory.READ
                || tool.getCategory() == ToolCategory.WRITE
                || "http_server".equals(name);
    }

    private boolean isRemoteExecutionMode() {
        if (toolSettingsRepository == null) {
            return false;
        }
        String executionMode = toolSettingsRepository.getExecutionMode();
        return ToolSettingsRepository.EXECUTION_SSH.equals(executionMode)
                || ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER.equals(executionMode);
    }

    public Set<String> toolNames(List<BaseTool> tools) {
        HashSet<String> names = new HashSet<>();
        if (tools == null) {
            return names;
        }
        for (BaseTool tool : tools) {
            if (tool != null) {
                names.add(tool.getName());
            }
        }
        return names;
    }

    public ToolResult executeAgentToolCall(
            ToolCall call,
            Set<String> allowedToolNames,
            String type,
            List<String> writeScope,
            String homePath,
            Host host
    ) {
        host.syncModePermission();
        if (call == null) {
            return new ToolResult("", "", "Agent 工具调用为空", true);
        }
        if (!allowedToolNames.contains(call.getName())) {
            return new ToolResult(call.getId(), call.getName(), "Agent 不允许调用此工具: " + call.getName(), true);
        }
        ToolContext context = new ToolContext(homePath, extensionRepository.skillWriteRoots(homePath), null, "", null);
        ToolResult scopeError = validateAgentWriteScope(call, type, writeScope, context);
        if (scopeError != null) {
            return scopeError;
        }
        return toolExecutor.execute(call, context);
    }

    public String agentSystemPrompt(
            String type,
            String description,
            List<String> readScope,
            List<String> writeScope,
            String homePath,
            ModelConfig selectedModel,
            List<BaseTool> agentTools,
            Host host
    ) {
        host.syncModePermission();
        return agentRolePrompt(type)
                + "\n\n你的任务: " + description
                + "\n\n" + agentWorkspacePrompt(homePath, host)
                + "\n\n" + agentScopePrompt(type, readScope, writeScope)
                + "\n\n" + extensionRepository.buildExtensionPrompt(homePath)
                + "\n\n" + toolSettingsRepository.buildToolPrompt(agentTools, supportsNativeTools(selectedModel));
    }

    public String agentRolePrompt(String type) {
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return "你是一个代码探索 Agent。你的任务是快速定位和分析代码，回答用户的问题。\n"
                    + "规则：\n"
                    + "- 只读取代码，不做任何修改，不调用任何写入类工具。\n"
                    + "- 优先使用只读工具搜索和读取关键文件；在 SSH Shell 或终端提供者模式下，可以使用 shell_execute 执行 pwd、ls、find、grep、rg、cat、head、tail、git diff、git status 等只读命令。\n"
                    + "- 使用 shell_execute 时禁止执行写入、删除、移动、安装、启动服务、修改权限、重定向写文件或其它有副作用的命令。\n"
                    + "- 给出简洁准确的中文回答，并标注文件路径和必要的行号。";
        }
        return "你是一个编程 Agent。你的任务是完成边界清晰的编程子任务。\n"
                + "规则：\n"
                + "- 只负责用户明确分派给你的任务区域，不要修改无关文件。\n"
                + "- 只能修改 write_scope 中列出的文件或目录；没有 write_scope 时禁止写入文件，只能汇报需要主模型重新分配。\n"
                + "- 使用 shell_execute 时，命令的写入、修改、副作用同样视为对 write_scope 内文件操作；禁止读取、修改、删除或影响 write_scope 外的文件、目录、环境变量、依赖、服务和进程。\n"
                + "- 优先使用 file_read / file_write / file_edit / glob / list_dir 完成工作；只有 file 类工具确实无法满足时（如 SSH 环境、运行构建或验证脚本）才使用 shell_execute。\n"
                + "- 如果发现必须修改 write_scope 外的文件，停止写入并在输出中说明需要扩大或重新分配范围。\n"
                + "- 修改前先读取目标文件，完成后做最小可行验证。\n"
                + "- 如果工具失败，先重新读取和分析，不要盲目重复。\n"
                + "- 用中文总结完成内容、验证结果和剩余风险。";
    }

    public String agentWorkspacePrompt(String homePath, Host host) {
        String path = homePath == null || homePath.trim().length() == 0 ? promptHomePath(host) : homePath.trim();
        return "当前工作区: " + path + "\n"
                + "所有文件路径默认相对此工作区。不要访问未授权路径，不要读取 API key、token、密码等敏感数据。";
    }

    public String agentScopePrompt(String type, List<String> readScope, List<String> writeScope) {
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
        return builder.toString();
    }

    public ToolResult validateAgentWriteScope(
            ToolCall call,
            String type,
            List<String> writeScope,
            ToolContext context
    ) {
        if (call == null || !isFileWriteTool(call.getName())) {
            return null;
        }
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return new ToolResult(call.getId(), call.getName(), "explore Agent 不允许写入文件。", true);
        }
        if (writeScope == null || writeScope.isEmpty()) {
            return new ToolResult(call.getId(), call.getName(),
                    "Agent 未声明 write_scope，禁止写入文件。请让主模型重新分配明确的写入范围。", true);
        }
        try {
            JSONObject arguments = call.getArguments().trim().length() == 0
                    ? new JSONObject()
                    : new JSONObject(call.getArguments());
            String filePath = arguments.optString("file_path").trim();
            if (filePath.length() == 0) {
                return null;
            }
            File target = FileToolPathPolicy.resolve(context, filePath);
            for (String scope : writeScope) {
                if (scope == null || scope.trim().length() == 0) {
                    continue;
                }
                File allowed = FileToolPathPolicy.resolve(context, scope);
                if (isInsidePath(allowed, target)) {
                    return null;
                }
            }
            return new ToolResult(call.getId(), call.getName(),
                    "Agent 写入路径超出 write_scope: " + filePath
                            + "\n允许写入范围: " + scopeSummary(writeScope)
                            + "\n请停止写入并让主模型重新分配。", true);
        } catch (Exception e) {
            return new ToolResult(call.getId(), call.getName(), "Agent 写入范围检查失败: " + e.getMessage(), true);
        }
    }

    public boolean isFileWriteTool(String name) {
        return "file_write".equals(name) || "file_edit".equals(name);
    }

    public boolean isInsidePath(File root, File target) throws java.io.IOException {
        File canonicalRoot = root.getCanonicalFile();
        File canonicalTarget = target.getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String targetPath = canonicalTarget.getPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
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

    public ArrayList<PipelineAgent> parsePipelineAgents(JSONArray array) {
        ArrayList<PipelineAgent> agents = new ArrayList<>();
        if (array == null) {
            return agents;
        }
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                return new ArrayList<>();
            }
            String id = object.optString("id").trim();
            if (id.length() == 0 || ids.contains(id)) {
                return new ArrayList<>();
            }
            ids.add(id);
            agents.add(new PipelineAgent(
                    id,
                    AgentTool.normalizeType(object.optString("type")),
                    object.optString("description").trim(),
                    object.optString("prompt").trim(),
                    scopeList(object.optJSONArray("read_scope")),
                    scopeList(object.optJSONArray("write_scope")),
                    dependencyList(object.optJSONArray("depends_on"))
            ));
        }
        return agents;
    }

    public String validatePipelineDependencies(ArrayList<PipelineAgent> agents) {
        if (agents == null) {
            return "agent_pipeline.agents 不能为空。";
        }
        for (PipelineAgent agent : agents) {
            for (String dependency : agent.getDependencies()) {
                if (agent.getId().equals(dependency)) {
                    return "Agent 不能依赖自身: " + agent.getId();
                }
            }
        }
        return "";
    }

    public ArrayList<ArrayList<PipelineAgent>> dependencyLevels(ArrayList<PipelineAgent> agents) {
        ArrayList<ArrayList<PipelineAgent>> levels = new ArrayList<>();
        HashSet<String> allIds = new HashSet<>();
        for (PipelineAgent agent : agents) {
            allIds.add(agent.getId());
        }
        for (PipelineAgent agent : agents) {
            for (String dependency : agent.getDependencies()) {
                if (!allIds.contains(dependency)) {
                    return new ArrayList<>();
                }
            }
        }

        HashSet<String> completed = new HashSet<>();
        while (completed.size() < agents.size()) {
            ArrayList<PipelineAgent> level = new ArrayList<>();
            for (PipelineAgent agent : agents) {
                if (completed.contains(agent.getId())) {
                    continue;
                }
                if (completed.containsAll(agent.getDependencies())) {
                    level.add(agent);
                }
            }
            if (level.isEmpty()) {
                return new ArrayList<>();
            }
            for (PipelineAgent agent : level) {
                completed.add(agent.getId());
            }
            levels.add(level);
        }
        return levels;
    }

    public String dependencyOutputContext(PipelineAgent agent, LinkedHashMap<String, AgentRunResult> results) {
        if (agent.getDependencies().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n## 上游 Agent 输出\n");
        for (String dependency : agent.getDependencies()) {
            AgentRunResult result = results.get(dependency);
            if (result == null) {
                continue;
            }
            builder.append("\n### ").append(dependency).append('\n').append(result.getOutput()).append('\n');
        }
        builder.append("\n请基于以上结果继续你的任务。");
        return builder.toString();
    }

    public ArrayList<String> scopeList(JSONArray array) {
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

    public HashSet<String> scopeSet(JSONArray array) {
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

    public ArrayList<String> dependencyList(JSONArray array) {
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

    public boolean supportsNativeTools(ModelConfig selectedModel) {
        if (selectedModel == null) {
            return false;
        }
        cn.lineai.model.ModelProtocolType type = selectedModel.getProtocolType();
        if (type == cn.lineai.model.ModelProtocolType.OPENAI_COMPATIBLE) {
            return cn.lineai.ai.protocol.OpenAiCompatibleCapabilities.supportsNativeTools(selectedModel);
        }
        return type == cn.lineai.model.ModelProtocolType.ANTHROPIC_MESSAGES
                || type == cn.lineai.model.ModelProtocolType.CODEX_RESPONSES;
    }

    public List<ToolCall> mergeToolCalls(List<ToolCall> nativeCalls, List<ToolCall> textCalls) {
        ArrayList<ToolCall> merged = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (nativeCalls != null) {
            for (ToolCall call : nativeCalls) {
                if (call == null || call.getName().length() == 0 || seen.contains(call.getId())) {
                    continue;
                }
                merged.add(call);
                seen.add(call.getId());
            }
        }
        if (textCalls != null) {
            for (ToolCall call : textCalls) {
                if (call == null || call.getName().length() == 0 || seen.contains(call.getId())) {
                    continue;
                }
                merged.add(call);
                seen.add(call.getId());
            }
        }
        return merged;
    }

    private String promptHomePath(Host host) {
        if ("ssh".equals(host.projectSource()) && host.projectPath().length() == 0) {
            return "~";
        }
        return host.projectPath();
    }
}
