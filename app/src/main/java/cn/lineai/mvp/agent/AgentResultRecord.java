package cn.lineai.mvp.agent;

public final class AgentResultRecord {
    public static final int PREVIEW_MAX_CHARS = 240;

    private final String agentId;
    private final String toolCallId;
    private final String toolName;
    private final String status;
    private final String type;
    private final String description;
    private final String preview;
    private final String fullOutput;
    private final String thinking;
    private final String progressJson;
    private final int toolCallCount;
    private final boolean error;
    private final boolean async;
    private final int generationId;
    private final long updatedAtMs;

    public AgentResultRecord(
            String agentId,
            String toolCallId,
            String toolName,
            String status,
            String type,
            String description,
            String preview,
            String fullOutput,
            String thinking,
            String progressJson,
            int toolCallCount,
            boolean error,
            boolean async,
            int generationId,
            long updatedAtMs
    ) {
        this.agentId = agentId == null ? "" : agentId;
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.toolName = toolName == null ? "" : toolName;
        this.status = status == null || status.length() == 0 ? "running" : status;
        this.type = type == null ? "" : type;
        this.description = description == null ? "" : description;
        this.preview = clampPreview(preview);
        this.fullOutput = fullOutput == null ? "" : fullOutput;
        this.thinking = thinking == null ? "" : thinking;
        this.progressJson = progressJson == null ? "" : progressJson;
        this.toolCallCount = Math.max(0, toolCallCount);
        this.error = error;
        this.async = async;
        this.generationId = generationId;
        this.updatedAtMs = updatedAtMs <= 0L ? System.currentTimeMillis() : updatedAtMs;
    }

    public static AgentResultRecord running(
            String agentId,
            String toolCallId,
            String toolName,
            String type,
            String description,
            boolean async,
            int generationId
    ) {
        return new AgentResultRecord(
                agentId,
                toolCallId,
                toolName,
                "running",
                type,
                description,
                "",
                "",
                "",
                "",
                0,
                false,
                async,
                generationId,
                System.currentTimeMillis()
        );
    }

    public AgentResultRecord withPreview(String nextPreview) {
        return copy(
                status, type, description, nextPreview, fullOutput, thinking, progressJson,
                toolCallCount, error, async, generationId, System.currentTimeMillis());
    }

    public AgentResultRecord withStatus(String nextStatus, boolean nextError, String nextPreview) {
        return copy(
                nextStatus, type, description, nextPreview, fullOutput, thinking, progressJson,
                toolCallCount, nextError, async, generationId, System.currentTimeMillis());
    }

    public AgentResultRecord withFullOutput(
            String nextFullOutput,
            String nextThinking,
            String nextProgressJson,
            int nextToolCallCount,
            boolean nextError
    ) {
        String nextStatus = nextError ? "error" : "done";
        String nextPreview = previewFrom(nextFullOutput);
        return copy(
                nextStatus, type, description, nextPreview, nextFullOutput, nextThinking, nextProgressJson,
                nextToolCallCount, nextError, async, generationId, System.currentTimeMillis());
    }

    private AgentResultRecord copy(
            String nextStatus,
            String nextType,
            String nextDescription,
            String nextPreview,
            String nextFullOutput,
            String nextThinking,
            String nextProgressJson,
            int nextToolCallCount,
            boolean nextError,
            boolean nextAsync,
            int nextGenerationId,
            long nextUpdatedAtMs
    ) {
        return new AgentResultRecord(
                agentId,
                toolCallId,
                toolName,
                nextStatus,
                nextType,
                nextDescription,
                nextPreview,
                nextFullOutput,
                nextThinking,
                nextProgressJson,
                nextToolCallCount,
                nextError,
                nextAsync,
                nextGenerationId,
                nextUpdatedAtMs
        );
    }

    public String getAgentId() {
        return agentId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
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

    public String getThinking() {
        return thinking;
    }

    public String getProgressJson() {
        return progressJson;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public boolean isError() {
        return error;
    }

    public boolean isAsync() {
        return async;
    }

    public int getGenerationId() {
        return generationId;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public static String clampPreview(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= PREVIEW_MAX_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_MAX_CHARS);
    }

    public static String previewFrom(String fullOutput) {
        if (fullOutput == null || fullOutput.length() == 0) {
            return "";
        }
        return clampPreview(fullOutput);
    }
}
