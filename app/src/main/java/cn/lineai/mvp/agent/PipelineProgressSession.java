package cn.lineai.mvp.agent;

import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PipelineProgressSession {
    private static final String AGENT_TERMINATED_MESSAGE = "Agent 已终止。";

    private final ToolContext parentContext;
    private final ArrayList<PipelineAgent> agents;
    private final LinkedHashMap<String, PipelineAgentState> stateById = new LinkedHashMap<>();
    private String status = "running";
    private boolean error;

    public PipelineProgressSession(ToolContext parentContext, ArrayList<PipelineAgent> agents) {
        this.parentContext = parentContext;
        this.agents = agents == null ? new ArrayList<>() : agents;
        for (PipelineAgent agent : this.agents) {
            stateById.put(agent.getId(), new PipelineAgentState(agent));
        }
    }

    public void beginAgent(PipelineAgent agent) {
        PipelineAgentState state = stateById.get(agent.getId());
        if (state != null) {
            state.setStatus("running");
        }
        publish(false);
    }

    public void updateAgent(PipelineAgent agent, String agentProgressPayload, boolean agentError) {
        PipelineAgentState state = stateById.get(agent.getId());
        if (state == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject(agentProgressPayload);
            state.setStatus(object.optString("status", state.getStatus()));
            state.setOutput(object.optString("output", state.getOutput()));
            state.setThinking(object.optString("thinking", state.getThinking()));
            state.setToolCallCount(object.optInt("tool_call_count", state.getToolCallCount()));
            JSONArray toolCalls = object.optJSONArray("tool_calls");
            if (toolCalls != null) {
                state.setToolCalls(new JSONArray(toolCalls.toString()));
            }
            state.setError(agentError || object.optBoolean("error", false) || "error".equals(state.getStatus()));
        } catch (Exception ignored) {
            state.setOutput(agentProgressPayload == null ? state.getOutput() : agentProgressPayload);
            state.setError(agentError);
        }
        error = error || state.isError();
        publish(error);
    }

    public void finishAgent(PipelineAgent agent, AgentRunResult result) {
        PipelineAgentState state = stateById.get(agent.getId());
        if (state != null) {
            state.setStatus(result.isError() ? "error" : "done");
            state.setOutput(result.getOutput());
            state.setToolCallCount(result.getToolCallCount());
            state.setError(result.isError());
        }
        error = error || result.isError();
        publish(error);
    }

    public void terminate() {
        status = "error";
        error = true;
        for (PipelineAgentState state : stateById.values()) {
            if ("running".equals(state.getStatus()) || "waiting".equals(state.getStatus())) {
                state.setStatus("error");
                state.setError(true);
                state.setOutput(AGENT_TERMINATED_MESSAGE);
            }
        }
        publish(true);
    }

    public void publish(boolean nextError) {
        if (parentContext == null || parentContext.getToolCallId().length() == 0) {
            return;
        }
        parentContext.reportToolProgress("agent_pipeline", payload(), nextError);
    }

    private String payload() {
        try {
            JSONObject object = new JSONObject();
            object.put("linecode_agent_pipeline_progress", true);
            object.put("kind", "agent_pipeline");
            object.put("status", status);
            object.put("total", agents.size());
            object.put("completed", countStatus("done"));
            object.put("running", countStatus("running"));
            object.put("failed", countFailed());
            JSONArray array = new JSONArray();
            for (PipelineAgentState state : stateById.values()) {
                array.put(state.toJson());
            }
            object.put("agents", array);
            return object.toString();
        } catch (Exception e) {
            return "Agent 流水线运行中。";
        }
    }

    private int countStatus(String value) {
        int count = 0;
        for (PipelineAgentState state : stateById.values()) {
            if (value.equals(state.getStatus())) {
                count++;
            }
        }
        return count;
    }

    private int countFailed() {
        int count = 0;
        for (PipelineAgentState state : stateById.values()) {
            if (state.isError() || "error".equals(state.getStatus())) {
                count++;
            }
        }
        return count;
    }
}