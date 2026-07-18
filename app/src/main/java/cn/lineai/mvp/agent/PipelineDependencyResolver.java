package cn.lineai.mvp.agent;

import cn.lineai.tool.builtin.AgentTool;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PipelineDependencyResolver {

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

    private static ArrayList<String> scopeList(JSONArray array) {
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

    private static ArrayList<String> dependencyList(JSONArray array) {
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
}
