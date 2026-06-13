package cn.lineai.mvp.agent;

import org.json.JSONArray;
import org.json.JSONObject;

public final class PipelineAgentState {
    private final String id;
    private final String type;
    private final String description;
    private String status = "waiting";
    private String output = "";
    private String thinking = "";
    private int toolCallCount;
    private boolean error;
    private JSONArray toolCalls = new JSONArray();

    public PipelineAgentState(PipelineAgent agent) {
        this.id = agent == null ? "" : agent.getId();
        this.type = agent == null ? "" : agent.getType();
        this.description = agent == null ? "" : agent.getDescription();
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getThinking() {
        return thinking;
    }

    public void setThinking(String thinking) {
        this.thinking = thinking;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public JSONArray getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(JSONArray toolCalls) {
        this.toolCalls = toolCalls;
    }

    public JSONObject toJson() throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("type", type)
                .put("description", description)
                .put("status", status)
                .put("output", output)
                .put("thinking", thinking)
                .put("tool_call_count", toolCallCount)
                .put("error", error)
                .put("tool_calls", toolCalls);
    }
}