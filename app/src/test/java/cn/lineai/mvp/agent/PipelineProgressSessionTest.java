package cn.lineai.mvp.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.tool.ToolContext;
import cn.lineai.tool.builtin.AgentTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;

public final class PipelineProgressSessionTest {

    @Test
    public void concurrentBeginUpdateFinishKeepsConsistentPayload() throws Exception {
        ArrayList<PipelineAgent> agents = new ArrayList<>();
        agents.add(agent("a"));
        agents.add(agent("b"));
        agents.add(agent("c"));

        AtomicInteger publishCount = new AtomicInteger();
        PipelineProgressSession session = new PipelineProgressSession(
                ToolContext.builder().toolCallId("pipeline-1").build(),
                agents,
                (id, name, payload, error) -> {
                    try {
                        publishCount.incrementAndGet();
                        assertEquals("pipeline-1", id);
                        JSONObject object = new JSONObject(payload);
                        assertEquals(3, object.getInt("total"));
                        assertTrue(object.getInt("running") + object.getInt("completed") + object.getInt("failed") <= 3);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        try {
            for (PipelineAgent agent : agents) {
                executor.execute(() -> {
                    try {
                        start.await(2, TimeUnit.SECONDS);
                        session.beginAgent(agent);
                        session.updateAgent(agent, new JSONObject()
                                .put("status", "running")
                                .put("output", agent.getId() + "-out")
                                .put("tool_call_count", 1)
                                .toString(), false);
                        session.finishAgent(agent, new AgentRunResult(agent.getId() + "-done", 1, false));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        JSONObject payload = new JSONObject(session.payload());
        assertEquals(3, payload.getInt("completed"));
        assertEquals(0, payload.getInt("running"));
        assertEquals(0, payload.getInt("failed"));
        assertFalse(payload.getBoolean("error"));
        assertTrue(publishCount.get() >= 3);
    }

    private static PipelineAgent agent(String id) {
        return new PipelineAgent(
                id,
                AgentTool.TYPE_EXPLORE,
                id,
                "prompt-" + id,
                new ArrayList<>(Collections.singletonList("src/")),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
