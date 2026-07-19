package cn.lineai.mvp.agent;

import android.content.Context;
import cn.lineai.R;
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
import cn.lineai.ai.protocol.ModelProtocolFactory;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ExtensionRepository;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.model.ModelConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import cn.lineai.tool.builtin.FileWriteTool;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

public final class AgentExecutionController {
    private static final long AGENT_TOTAL_BUDGET_MS = 30L * 60L * 1000L;
    private static final String AGENT_TERMINATED_MESSAGE = "Agent 已终止。";
    private static final String AGENT_TOOL_LIMIT_MESSAGE = "Agent 已达到主流程总工具调用次数上限。";

    private final ModelClient modelClient;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final ToolSettingsStore toolSettingsRepository;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final ExtensionRepository extensionRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ModelProtocolFactory modelProtocolFactory = new ModelProtocolFactory();
    private final AgentPromptBuilder promptBuilder;
    private final PipelineDependencyResolver dependencyResolver;
    private Context context;
    private ToolReviewAwaiter toolReviewAwaiter;
    private java.util.function.BooleanSupplier bypassPathProtectionSupplier = () -> false;

    public interface Host {
        String projectPath();

        String projectSource();

        void syncModePermission();

        void addOrReplaceToolResult(ToolResult result);

        void render();

        void scheduleAgentProgressRender(AgentProgressSession session);

        void postToolProgress(int generationId, ModelCancellationToken cancellationToken, String toolCallId, String toolName, String content, boolean error);

        void requestAgentToolReview(String displayToolCallId, ToolCall call, ToolResult pendingToolResult);

        void clearAgentToolReview(String displayToolCallId);

        default boolean isSshExecutionMode() {
            return false;
        }

        default boolean isTerminalProviderExecutionMode() {
            return false;
        }
    }

    public interface ToolReviewAwaiter {
        String awaitReview(String displayToolCallId, ToolCall call, ModelCancellationToken cancellationToken) throws InterruptedException;

        default boolean isAutoConfirmed(ToolCall call) {
            return false;
        }
    }

    public AgentExecutionController(
            ModelClient modelClient,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            ToolSettingsStore toolSettingsRepository,
            ToolExecutor toolExecutor,
            ToolRegistry toolRegistry,
            ExtensionRepository extensionRepository,
            PromptTemplateRepository promptTemplateRepository
    ) {
        this.modelClient = modelClient;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.toolSettingsRepository = toolSettingsRepository;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.extensionRepository = extensionRepository;
        this.promptTemplateRepository = promptTemplateRepository;
        this.promptBuilder = new AgentPromptBuilder(extensionRepository, toolSettingsRepository, promptTemplateRepository);
        this.dependencyResolver = new PipelineDependencyResolver();
    }

    public void setToolReviewAwaiter(ToolReviewAwaiter toolReviewAwaiter) {
        this.toolReviewAwaiter = toolReviewAwaiter;
    }

    public void setContext(Context context) {
        this.context = context;
        this.promptBuilder.setContext(context);
    }

    private String string(int resId, String fallback) {
        return context != null ? context.getString(resId) : fallback;
    }

    public void setBypassPathProtectionSupplier(java.util.function.BooleanSupplier supplier) {
        this.bypassPathProtectionSupplier = supplier != null ? supplier : () -> false;
    }

    private boolean isBypassPathProtection() {
        try {
            return bypassPathProtectionSupplier.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    public ToolResult runAgentTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            Host host,
            int usedToolCallCount
    ) {
        if (selectedModel == null) {
            return ToolResult.withReview("", AgentTool.NAME, "当前没有可用模型，无法运行 Agent。", true, "", "", "");
        }
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return ToolResult.withReview("", AgentTool.NAME, AGENT_TERMINATED_MESSAGE, true, "", "", "");
        }
        String type = AgentTool.normalizeType(input.optString("type"));
        String description = input.optString("description").trim();
        String prompt = input.optString("prompt").trim();
        ArrayList<String> readScope = promptBuilder.scopeList(input.optJSONArray("read_scope"));
        ArrayList<String> writeScope = promptBuilder.scopeList(input.optJSONArray("write_scope"));
        Set<String> customToolNames = promptBuilder.scopeSet(input.optJSONArray("custom_tool_names"));
        Set<String> customMcpIds = promptBuilder.scopeSet(input.optJSONArray("custom_mcp_ids"));
        String homePath = parentContext == null ? host.projectPath() : parentContext.getHomePath();
        AgentProgressSession progress = new AgentProgressSession(
                generationId,
                parentContext == null ? "" : parentContext.getToolCallId(),
                AgentTool.NAME,
                type,
                description
        );
        int[] toolCallBudget = new int[]{Math.max(0, usedToolCallCount)};
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
                customMcpIds,
                toolCallBudget
        );
        StringBuilder builder = new StringBuilder();
        builder.append(string(R.string.agent_execution_completed, "Agent completed: ")).append(description).append('\n')
                .append("类型: ").append(type).append('\n')
                .append("工具调用: ").append(result.getToolCallCount()).append('\n')
                .append("输出:\n").append(result.getOutput());
        progress.setTurnResult(result.getOutput(), "");
        progress.setFinished(result.isError() ? "error" : "done", result.isError(), builder.toString().trim());
        host.addOrReplaceToolResult(progress.snapshotResult());
        host.render();
        return ToolResult.withReview("", AgentTool.NAME, progress.snapshotResult().getContent(), result.isError(), "", "", "");
    }

    public ToolResult runAgentPipelineTool(
            JSONObject input,
            ToolContext parentContext,
            ModelConfig selectedModel,
            ModelCancellationToken cancellationToken,
            int generationId,
            Host host,
            int usedToolCallCount
    ) {
        if (selectedModel == null) {
            return ToolResult.withReview("", AgentPipelineTool.NAME, "当前没有可用模型，无法运行 Agent 流水线。", true, "", "", "");
        }
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return ToolResult.withReview("", AgentPipelineTool.NAME, AGENT_TERMINATED_MESSAGE, true, "", "", "");
        }
        ArrayList<PipelineAgent> agents = dependencyResolver.parsePipelineAgents(input.optJSONArray("agents"));
        if (agents.isEmpty()) {
            return ToolResult.withReview("", AgentPipelineTool.NAME, "agent_pipeline.agents 不能为空。", true, "", "", "");
        }
        String dependencyError = dependencyResolver.validatePipelineDependencies(agents);
        if (dependencyError.length() > 0) {
            return ToolResult.withReview("", AgentPipelineTool.NAME, dependencyError, true, "", "", "");
        }
        ArrayList<ArrayList<PipelineAgent>> levels = dependencyResolver.dependencyLevels(agents);
        if (levels.isEmpty()) {
            return ToolResult.withReview("", AgentPipelineTool.NAME, "Agent 流水线存在循环依赖或重复 id，无法执行。", true, "", "", "");
        }

        LinkedHashMap<String, AgentRunResult> results = new LinkedHashMap<>();
        StringBuilder summary = new StringBuilder();
        summary.append(string(R.string.agent_pipeline_completed, "Agent pipeline completed: ")).append(agents.size()).append(" 个任务");
        PipelineProgressSession pipelineProgress = new PipelineProgressSession(
                parentContext,
                agents,
                (id, name, payload, nextError) -> {
                    String reviewState = "running";
                    try {
                        JSONObject object = new JSONObject(payload);
                        if (object.has("status")) {
                            String status = object.optString("status", "running");
                            if (status.length() > 0) {
                                reviewState = status;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    host.addOrReplaceToolResult(ToolResult.withReview(id, name, payload, nextError, "", reviewState, ""));
                    host.render();
                }
        );
        pipelineProgress.publish(false);
        boolean hasError = false;
        int totalToolCalls = 0;
        long pipelineStartedAt = System.currentTimeMillis();
        long pipelineBudgetMs = (long) agents.size() * AGENT_TOTAL_BUDGET_MS;
        int[] toolCallBudget = new int[]{Math.max(0, usedToolCallCount)};
        for (ArrayList<PipelineAgent> level : levels) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                pipelineProgress.terminate();
                pipelineProgress.setFinalSummary("Agent 流水线已终止。");
                pipelineProgress.setStatus("error", true);
                pipelineProgress.publish(true);
                ToolResult finalProgress = pipelineProgressFinalToolResult(parentContext, pipelineProgress, "Agent 流水线已终止。", true);
                host.addOrReplaceToolResult(finalProgress);
                return finalProgress;
            }
            if (System.currentTimeMillis() - pipelineStartedAt > pipelineBudgetMs) {
                String message = "Agent 流水线达到总时长预算 " + (pipelineBudgetMs / 60000L) + " 分钟，已强制结束。";
                pipelineProgress.terminate();
                pipelineProgress.setFinalSummary(message);
                pipelineProgress.setStatus("error", true);
                pipelineProgress.publish(true);
                ToolResult finalProgress = pipelineProgressFinalToolResult(parentContext, pipelineProgress, message, true);
                host.addOrReplaceToolResult(finalProgress);
                return finalProgress;
            }
            for (PipelineAgent agent : level) {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    pipelineProgress.terminate();
                    pipelineProgress.setFinalSummary("Agent 流水线已终止。");
                    pipelineProgress.setStatus("error", true);
                    pipelineProgress.publish(true);
                    ToolResult finalProgress = pipelineProgressFinalToolResult(parentContext, pipelineProgress, "Agent 流水线已终止。", true);
                    host.addOrReplaceToolResult(finalProgress);
                    return finalProgress;
                }
                if (System.currentTimeMillis() - pipelineStartedAt > pipelineBudgetMs) {
                    String message = "Agent 流水线达到总时长预算 " + (pipelineBudgetMs / 60000L) + " 分钟，已强制结束。";
                    pipelineProgress.terminate();
                    pipelineProgress.setFinalSummary(message);
                    pipelineProgress.setStatus("error", true);
                    pipelineProgress.publish(true);
                    ToolResult finalProgress = pipelineProgressFinalToolResult(parentContext, pipelineProgress, message, true);
                    host.addOrReplaceToolResult(finalProgress);
                    return finalProgress;
                }
                String agentPrompt = agent.getPrompt() + dependencyResolver.dependencyOutputContext(agent, results);
                pipelineProgress.beginAgent(agent);
                AgentProgressSession agentProgress = new AgentProgressSession(
                        generationId,
                        "",
                        AgentTool.NAME,
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
                        Collections.emptySet(),
                        toolCallBudget
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
                if (result.isError() && result.getOutput().contains(AGENT_TOOL_LIMIT_MESSAGE)) {
                    String message = "Agent 流水线因工具调用次数达到主流程上限，已提前结束：\n" + result.getOutput();
                    pipelineProgress.setFinalSummary(message);
                    pipelineProgress.setStatus("error", true);
                    pipelineProgress.publish(true);
                    ToolResult finalProgress = pipelineProgressFinalToolResult(parentContext, pipelineProgress, message, true);
                    host.addOrReplaceToolResult(finalProgress);
                    return finalProgress;
                }
            }
        }
        summary.append("\n\n总工具调用: ").append(totalToolCalls);
        String finalSummaryText = summary.toString().trim();
        pipelineProgress.setFinalSummary(finalSummaryText);
        pipelineProgress.setStatus(hasError ? "error" : "done", hasError);
        pipelineProgress.publish(hasError);
        ToolResult finalProgress = pipelineProgressFinalToolResult(parentContext, pipelineProgress, finalSummaryText, hasError);
        host.addOrReplaceToolResult(finalProgress);
        return finalProgress;
    }

    private ToolResult pipelineProgressFinalToolResult(
            ToolContext parentContext,
            PipelineProgressSession pipelineProgress,
            String summary,
            boolean error
    ) {
        String toolCallId = parentContext == null ? "" : parentContext.getToolCallId();
        if (toolCallId.length() == 0) {
            return ToolResult.withReview("", AgentPipelineTool.NAME, summary, error, "", "", "");
        }
        return ToolResult.withReview(
                toolCallId,
                AgentPipelineTool.NAME,
                pipelineProgress.payload(),
                error,
                "",
                error ? "error" : "done",
                ""
        );
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
            Set<String> customMcpIds,
            int[] toolCallBudget
    ) {
        host.syncModePermission();
        ArrayList<BaseTool> agentTools = agentTools(type, host, customToolNames, customMcpIds);
        List<ToolInfo> agentToolInfos = new ArrayList<>(agentTools);
        Set<String> allowedToolNames = toolNames(agentTools);
        ArrayList<ModelMessage> agentMessages = new ArrayList<>();
        agentMessages.add(new SystemModelMessage(promptBuilder.agentSystemPrompt(type, description, readScope, writeScope, homePath, selectedModel, agentToolInfos, host)));
        agentMessages.add(new cn.lineai.ai.message.UserModelMessage(prompt));
        int toolCallCount = 0;
        String lastOutput = "";
        long startedAt = System.currentTimeMillis();
        int toolCallLimit = selectedModel == null ? -1 : selectedModel.getToolCallLimit();

        while (true) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                if (progress != null) {
                    progress.setFinished("error", true, AGENT_TERMINATED_MESSAGE);
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                return new AgentRunResult(AGENT_TERMINATED_MESSAGE, toolCallCount, true);
            }
            if (toolCallLimit > 0 && toolCallBudget[0] >= toolCallLimit) {
                String message = AGENT_TOOL_LIMIT_MESSAGE + "\n" + lastOutput;
                if (progress != null) {
                    progress.setFinished("error", true, message);
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                return new AgentRunResult(message, toolCallCount, true);
            }
            if (System.currentTimeMillis() - startedAt > AGENT_TOTAL_BUDGET_MS) {
                String timeoutMessage = "Agent 达到总时长预算 " + (AGENT_TOTAL_BUDGET_MS / 60000L)
                        + " 分钟，最后输出：\n" + lastOutput;
                if (progress != null) {
                    progress.setFinished("error", true, timeoutMessage);
                    host.addOrReplaceToolResult(progress.snapshotResult());
                    host.render();
                }
                return new AgentRunResult(timeoutMessage, toolCallCount, true);
            }
            try {
                if (progress != null) {
                    progress.beginTurn();
                    host.scheduleAgentProgressRender(progress);
                }
                AiBehaviorSettings aiSettings = aiBehaviorSettingsRepository.get();
                List<ToolInfo> nativeTools = modelProtocolFactory.create(selectedModel.getProtocolType()).supportsNativeTools(selectedModel) ? new ArrayList<>(agentTools) : new ArrayList<>();
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
                    if (toolCallLimit > 0 && toolCallBudget[0] >= toolCallLimit) {
                        String message = AGENT_TOOL_LIMIT_MESSAGE + "\n" + lastOutput;
                        if (progress != null) {
                            progress.setFinished("error", true, message);
                            host.addOrReplaceToolResult(progress.snapshotResult());
                            host.render();
                        }
                        return new AgentRunResult(message, toolCallCount, true);
                    }
                    ToolResult toolResult = executeAgentToolCall(call, allowedToolNames, type, writeScope, homePath, progress, host, cancellationToken);
                    toolCallCount++;
                    toolCallBudget[0]++;
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
        if (getAgentExcludedToolNames().contains(name)) {
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
        if (tool.needsConfirmation() && tool.getCategory() == ToolCategory.WRITE
                && tool.getDisplayCategory() == ToolDisplayCategory.DELETE) {
            return false;
        }
        Set<ToolCategory> allowed = getAgentAllowedCategories(type);
        boolean isRestrictedToRead = allowed.size() == 1 && allowed.contains(ToolCategory.READ);
        if (isRestrictedToRead) {
            return allowed.contains(tool.getCategory());
        }
        if (tool.isAllowedInReadonlyMode()) {
            return true;
        }
        return allowed.contains(tool.getCategory());
    }

    private Set<String> getAgentExcludedToolNames() {
        if (toolSettingsRepository != null) {
            return toolSettingsRepository.getAgentExcludedToolNames();
        }
        Set<String> names = new HashSet<>();
        names.add(AgentTool.NAME);
        names.add(AgentPipelineTool.NAME);
        return names;
    }

    private Set<ToolCategory> getAgentAllowedCategories(String type) {
        if (toolSettingsRepository != null) {
            return toolSettingsRepository.getAgentAllowedCategories(type);
        }
        return defaultAgentAllowedCategories(type);
    }

    private static Set<ToolCategory> defaultAgentAllowedCategories(String type) {
        if (AgentTool.TYPE_EXPLORE.equals(type)) {
            return Collections.singleton(ToolCategory.READ);
        }
        Set<ToolCategory> categories = new HashSet<>();
        categories.add(ToolCategory.READ);
        categories.add(ToolCategory.WRITE);
        return categories;
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
        return executeAgentToolCall(call, allowedToolNames, type, writeScope, homePath, null, host, null);
    }

    public ToolResult executeAgentToolCall(
            ToolCall call,
            Set<String> allowedToolNames,
            String type,
            List<String> writeScope,
            String homePath,
            AgentProgressSession progress,
            Host host,
            ModelCancellationToken cancellationToken
    ) {
        host.syncModePermission();
        if (call == null) {
            return ToolResult.error("Agent 工具调用为空");
        }
        if (!allowedToolNames.contains(call.getName())) {
            return ToolResult.of(call.getId(), call.getName(), "Agent 不允许调用此工具: " + call.getName(), true);
        }
        ToolContext context = ToolContext.builder()
                .homePath(homePath)
                .extraWriteRoots(skillWriteRoots(homePath))
                .toolCallId("")
                .bypassPathProtection(isBypassPathProtection())
                .build();
        ToolResult scopeError = validateAgentWriteScope(call, type, writeScope, context);
        if (scopeError != null) {
            return scopeError;
        }
        if (isSessionAutoConfirmed(call)) {
            return toolExecutor.executeConfirmed(call, context);
        }
        if (requiresToolConfirmation(call)) {
            return executeAgentToolCallWithReview(call, context, progress, host, cancellationToken);
        }
        return toolExecutor.execute(call, context);
    }

    private boolean isSessionAutoConfirmed(ToolCall call) {
        return toolReviewAwaiter != null && toolReviewAwaiter.isAutoConfirmed(call);
    }

    private List<String> skillWriteRoots(String homePath) {
        return extensionRepository == null ? Collections.emptyList() : extensionRepository.skillWriteRoots(homePath);
    }

    private ToolResult executeAgentToolCallWithReview(
            ToolCall call,
            ToolContext context,
            AgentProgressSession progress,
            Host host,
            ModelCancellationToken cancellationToken
    ) {
        if (progress == null || toolReviewAwaiter == null) {
            return toolExecutor.execute(call, context);
        }
        String displayToolCallId = progress.displayToolCallId(call);
        ToolResult pending = ToolResult.withReview(call.getId(), call.getName(), "", false, "", "pending", "");
        progress.putToolResult(call, pending);
        progress.setFinished("pending", false, "");
        host.addOrReplaceToolResult(progress.snapshotResult());
        host.requestAgentToolReview(displayToolCallId, call, pending);
        host.render();
        try {
            String state = toolReviewAwaiter.awaitReview(displayToolCallId, call, cancellationToken);
            progress.setStatus("running", false);
            if ("rejected".equals(state)) {
                ToolResult rejected = ToolResult.withReview(call.getId(), call.getName(), string(R.string.user_rejected_tool, "User rejected this tool."), true, "", "rejected", "");
                progress.putToolResult(call, rejected);
                host.addOrReplaceToolResult(progress.snapshotResult());
                return rejected;
            }
            progress.putToolResult(call, ToolResult.withReview(call.getId(), call.getName(), "", false, "", "accepted", ""));
            host.addOrReplaceToolResult(progress.snapshotResult());
            return toolExecutor.executeConfirmed(call, context).withReview("accepted", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.of(call.getId(), call.getName(), "等待工具确认时被中断。", true);
        } finally {
            host.clearAgentToolReview(displayToolCallId);
        }
    }

    private boolean requiresToolConfirmation(ToolCall call) {
        if (call == null || toolReviewAwaiter == null || toolRegistry == null || toolSettingsRepository == null) {
            return false;
        }
        BaseTool tool = toolRegistry.get(call.getName());
        return tool != null
                && tool.needsConfirmation()
                && !toolReviewAwaiter.isAutoConfirmed(call)
                && toolSettingsRepository.canExecuteTool(tool.getName(), tool.getCategory()).isAllowed()
                && (FileDeleteTool.NAME.equals(tool.getName()) || toolSettingsRepository.needsConfirmation(tool.getName()));
    }

    public String agentRolePrompt(String type) {
        return promptBuilder.agentRolePrompt(type);
    }

    public String agentRolePrompt(String type, boolean remoteMode) {
        return promptBuilder.agentRolePrompt(type, remoteMode);
    }

    public String agentScopePrompt(String type, List<String> readScope, List<String> writeScope) {
        return promptBuilder.agentScopePrompt(type, readScope, writeScope);
    }

    public String agentScopePrompt(String type, List<String> readScope, List<String> writeScope, boolean remoteMode) {
        return promptBuilder.agentScopePrompt(type, readScope, writeScope, remoteMode);
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
            return ToolResult.of(call.getId(), call.getName(), "explore Agent 不允许写入文件。", true);
        }
        if (context != null && context.isBypassPathProtection()) {
            return null;
        }
        if (writeScope == null || writeScope.isEmpty()) {
            return ToolResult.of(call.getId(), call.getName(),
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
            return ToolResult.of(call.getId(), call.getName(),
                    "Agent 写入路径超出 write_scope: " + filePath
                            + "\n允许写入范围: " + promptBuilder.scopeSummary(writeScope)
                            + "\n请停止写入并让主模型重新分配。", true);
        } catch (Exception e) {
            return ToolResult.of(call.getId(), call.getName(), "Agent 写入范围检查失败: " + e.getMessage(), true);
        }
    }

    public boolean isFileWriteTool(String name) {
        BaseTool tool = toolRegistry != null ? toolRegistry.get(name) : null;
        if (tool != null) {
            return tool.getCategory() == ToolCategory.WRITE
                    && (tool.getDisplayCategory() == ToolDisplayCategory.WRITE
                        || tool.getDisplayCategory() == ToolDisplayCategory.DELETE);
        }
        return FileWriteTool.NAME.equals(name) || FileEditTool.NAME.equals(name);
    }

    public boolean isInsidePath(File root, File target) throws java.io.IOException {
        File canonicalRoot = root.getCanonicalFile();
        File canonicalTarget = target.getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String targetPath = canonicalTarget.getPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
    }

    public ArrayList<PipelineAgent> parsePipelineAgents(org.json.JSONArray array) {
        return dependencyResolver.parsePipelineAgents(array);
    }

    public String validatePipelineDependencies(ArrayList<PipelineAgent> agents) {
        return dependencyResolver.validatePipelineDependencies(agents);
    }

    public ArrayList<ArrayList<PipelineAgent>> dependencyLevels(ArrayList<PipelineAgent> agents) {
        return dependencyResolver.dependencyLevels(agents);
    }

    public String dependencyOutputContext(PipelineAgent agent, LinkedHashMap<String, AgentRunResult> results) {
        return dependencyResolver.dependencyOutputContext(agent, results);
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
}
