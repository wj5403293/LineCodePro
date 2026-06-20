package cn.lineai.mvp.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.builtin.AgentTool;
import java.util.Collections;
import java.util.HashSet;
import org.json.JSONObject;
import org.junit.Test;

public final class AgentExecutionControllerTest {

    @Test
    public void subCodingAllowsShellExecute() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        BaseTool shell = new FakeTool("shell_execute", ToolCategory.SYSTEM);

        assertTrue(controller.isAgentToolAllowed(
                shell,
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void localExploreRejectsShellExecute() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        BaseTool shell = new FakeTool("shell_execute", ToolCategory.SYSTEM);

        assertFalse(controller.isAgentToolAllowed(
                shell,
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingStillRejectsAgentAndPipelineAndDelete() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("agent", ToolCategory.SYSTEM),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("agent_pipeline", ToolCategory.SYSTEM),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("file_delete", ToolCategory.WRITE),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingKeepsReadWriteAndHttpServer() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_read", ToolCategory.READ),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_write", ToolCategory.WRITE),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_edit", ToolCategory.WRITE),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("http_server", ToolCategory.SYSTEM),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingRejectsCustomToolNotInWhitelist() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        HashSet<String> customToolNames = new HashSet<>();
        customToolNames.add("agentx_demo");

        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("web_search", ToolCategory.READ),
                AgentTool.TYPE_SUB_CODING,
                customToolNames,
                Collections.emptySet()));
    }

    @Test
    public void subCodingAllowsCustomToolInWhitelist() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        HashSet<String> customToolNames = new HashSet<>();
        customToolNames.add("agentx_demo");

        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("agentx_demo", ToolCategory.READ),
                AgentTool.TYPE_SUB_CODING,
                customToolNames,
                Collections.emptySet()));
    }

    @Test
    public void localExploreAllowsReadToolsOnly() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_read", ToolCategory.READ),
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("file_write", ToolCategory.WRITE),
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("shell_execute", ToolCategory.SYSTEM),
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingRolePromptMentionsShellExecuteAndWriteScope() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        String prompt = controller.agentRolePrompt(AgentTool.TYPE_SUB_CODING);

        assertTrue(prompt.contains("shell_execute"));
        assertTrue(prompt.contains("write_scope"));
    }

    @Test
    public void exploreRolePromptRemainsReadOnly() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null);

        String prompt = controller.agentRolePrompt(AgentTool.TYPE_EXPLORE);

        assertTrue(prompt.contains("shell_execute"));
        assertTrue(prompt.contains("只读命令"));
        assertTrue(prompt.contains("只读取代码"));
    }

    private static final class FakeTool extends BaseTool {
        private final String name;
        private final ToolCategory category;

        FakeTool(String name, ToolCategory category) {
            this.name = name;
            this.category = category;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "fake " + name;
        }

        @Override
        public ToolCategory getCategory() {
            return category;
        }

        @Override
        public JSONObject getParameters() {
            return new JSONObject();
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            return new ToolResult("", name, "", false);
        }
    }
}
