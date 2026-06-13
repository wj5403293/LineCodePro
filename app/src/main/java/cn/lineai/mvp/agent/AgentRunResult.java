package cn.lineai.mvp.agent;

public final class AgentRunResult {
    private final String output;
    private final int toolCallCount;
    private final boolean error;

    public AgentRunResult(String output, int toolCallCount, boolean error) {
        this.output = output == null ? "" : output;
        this.toolCallCount = toolCallCount;
        this.error = error;
    }

    public String getOutput() {
        return output;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public boolean isError() {
        return error;
    }
}