package cn.lineai.tool;

import org.json.JSONObject;

public final class ToolContext {
    public interface AgentRunner {
        ToolResult runAgent(JSONObject input, ToolContext context);

        ToolResult runAgentPipeline(JSONObject input, ToolContext context);
    }

    public interface ProgressListener {
        void onToolProgress(String toolCallId, String toolName, String content, boolean error);
    }

    private final String homePath;
    private final AgentRunner agentRunner;
    private final String toolCallId;
    private final ProgressListener progressListener;

    public ToolContext(String homePath) {
        this(homePath, null, "", null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner) {
        this(homePath, agentRunner, "", null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId) {
        this(homePath, agentRunner, toolCallId, null);
    }

    public ToolContext(String homePath, AgentRunner agentRunner, String toolCallId, ProgressListener progressListener) {
        this.homePath = homePath == null ? "" : homePath;
        this.agentRunner = agentRunner;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.progressListener = progressListener;
    }

    public String getHomePath() {
        return homePath;
    }

    public AgentRunner getAgentRunner() {
        return agentRunner;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public ToolContext withToolCallId(String nextToolCallId) {
        return new ToolContext(homePath, agentRunner, nextToolCallId, progressListener);
    }

    public void reportToolProgress(String toolName, String content, boolean error) {
        if (progressListener != null && toolCallId.length() > 0) {
            progressListener.onToolProgress(toolCallId, toolName, content == null ? "" : content, error);
        }
    }
}
