package cn.lineai.tool;

/**
 * Session-scoped agent result snapshot for parent-model fetch ({@code agent_output}).
 */
public final class StoredAgentResult {
    private final String agentId;
    private final String status;
    private final String type;
    private final String description;
    private final String preview;
    private final String fullOutput;
    private final boolean error;
    private final boolean async;
    private final int toolCallCount;

    public StoredAgentResult(
            String agentId,
            String status,
            String type,
            String description,
            String preview,
            String fullOutput,
            boolean error,
            boolean async,
            int toolCallCount
    ) {
        this.agentId = agentId == null ? "" : agentId;
        this.status = status == null ? "" : status;
        this.type = type == null ? "" : type;
        this.description = description == null ? "" : description;
        this.preview = preview == null ? "" : preview;
        this.fullOutput = fullOutput == null ? "" : fullOutput;
        this.error = error;
        this.async = async;
        this.toolCallCount = Math.max(0, toolCallCount);
    }

    public String getAgentId() {
        return agentId;
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getPreview() {
        return preview;
    }

    public String getFullOutput() {
        return fullOutput;
    }

    public boolean isError() {
        return error;
    }

    public boolean isAsync() {
        return async;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public boolean isRunning() {
        return "running".equals(status) || "pending".equals(status) || "waiting_unlock".equals(status);
    }
}
