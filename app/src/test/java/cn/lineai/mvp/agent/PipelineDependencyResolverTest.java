package cn.lineai.mvp.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.tool.builtin.AgentTool;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class PipelineDependencyResolverTest {

    @Test
    public void independentAgentsShareOneLevel() throws Exception {
        PipelineDependencyResolver resolver = new PipelineDependencyResolver();
        JSONArray agents = new JSONArray()
                .put(agentJson("a", AgentTool.TYPE_EXPLORE, Collections.emptyList()))
                .put(agentJson("b", AgentTool.TYPE_EXPLORE, Collections.emptyList()));

        ArrayList<ArrayList<PipelineAgent>> levels = resolver.dependencyLevels(resolver.parsePipelineAgents(agents));

        assertEquals(1, levels.size());
        assertEquals(2, levels.get(0).size());
        assertEquals("a", levels.get(0).get(0).getId());
        assertEquals("b", levels.get(0).get(1).getId());
    }

    @Test
    public void dependentAgentsSplitAcrossLevels() throws Exception {
        PipelineDependencyResolver resolver = new PipelineDependencyResolver();
        JSONArray agents = new JSONArray()
                .put(agentJson("a", AgentTool.TYPE_EXPLORE, Collections.emptyList()))
                .put(agentJson("b", AgentTool.TYPE_SUB_CODING, Collections.singletonList("a")));

        ArrayList<ArrayList<PipelineAgent>> levels = resolver.dependencyLevels(resolver.parsePipelineAgents(agents));

        assertEquals(2, levels.size());
        assertEquals(1, levels.get(0).size());
        assertEquals("a", levels.get(0).get(0).getId());
        assertEquals(1, levels.get(1).size());
        assertEquals("b", levels.get(1).get(0).getId());
    }

    @Test
    public void cycleYieldsEmptyLevels() throws Exception {
        PipelineDependencyResolver resolver = new PipelineDependencyResolver();
        JSONArray agents = new JSONArray()
                .put(agentJson("a", AgentTool.TYPE_EXPLORE, Collections.singletonList("b")))
                .put(agentJson("b", AgentTool.TYPE_EXPLORE, Collections.singletonList("a")));

        ArrayList<ArrayList<PipelineAgent>> levels = resolver.dependencyLevels(resolver.parsePipelineAgents(agents));
        assertTrue(levels.isEmpty());
    }

    private static JSONObject agentJson(String id, String type, java.util.List<String> dependsOn) throws Exception {
        JSONObject object = new JSONObject()
                .put("id", id)
                .put("type", type)
                .put("description", id)
                .put("prompt", "do " + id)
                .put("read_scope", new JSONArray().put("src/"))
                .put("write_scope", AgentTool.TYPE_SUB_CODING.equals(type) ? new JSONArray().put("src/" + id) : new JSONArray());
        JSONArray deps = new JSONArray();
        for (String dependency : dependsOn) {
            deps.put(dependency);
        }
        object.put("depends_on", deps);
        return object;
    }
}
