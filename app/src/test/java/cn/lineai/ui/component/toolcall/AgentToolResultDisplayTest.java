package cn.lineai.ui.component.toolcall;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class AgentToolResultDisplayTest {

    @Test
    public void displayOutputPrefersStructuredOutputNotRawJson() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_progress", true)
                .put("status", "error")
                .put("output", "任务已中断")
                .put("model_content", "上次生成已中断。")
                .put("tool_calls", new JSONArray());

        String raw = progress.toString();
        Assert.assertNotNull(AgentToolResultDisplay.progressPayload(raw));
        Assert.assertEquals("任务已中断", AgentToolResultDisplay.displayOutput(raw));
        Assert.assertFalse(AgentToolResultDisplay.displayOutput(raw).contains("linecode_agent_progress"));
    }

    @Test
    public void displayOutputFallsBackToModelContentWhenOutputEmpty() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_progress", true)
                .put("status", "error")
                .put("output", "")
                .put("model_content", "Agent terminated");

        Assert.assertEquals("Agent terminated", AgentToolResultDisplay.displayOutput(progress.toString()));
    }

    @Test
    public void displayOutputHidesUnknownJsonBlob() {
        String blob = "{\"foo\":1,\"bar\":\"baz\"}";
        Assert.assertEquals("", AgentToolResultDisplay.displayOutput(blob));
        Assert.assertNull(AgentToolResultDisplay.progressPayload(blob));
    }

    @Test
    public void plainTextOutputPassthrough() {
        Assert.assertEquals("hello agent", AgentToolResultDisplay.displayOutput("hello agent"));
    }

    @Test
    public void toolCallCountFromProgress() throws Exception {
        JSONObject progress = new JSONObject()
                .put("linecode_agent_progress", true)
                .put("tool_call_count", 3)
                .put("tool_calls", new JSONArray()
                        .put(new JSONObject().put("id", "a").put("name", "file_read")));
        Assert.assertEquals(3, AgentToolResultDisplay.toolCallCount(progress.toString()));
    }
}
