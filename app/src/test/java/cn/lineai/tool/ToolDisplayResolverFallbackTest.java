package cn.lineai.tool;

import org.junit.Assert;
import org.junit.Test;

public final class ToolDisplayResolverFallbackTest {

    @Test
    public void builtInAgentUsesAgentCategory() {
        Assert.assertEquals(ToolDisplayCategory.AGENT, ToolDisplayResolver.fallbackDisplayCategory("agent"));
    }

    @Test
    public void agentPipelineUsesPipelineCategory() {
        Assert.assertEquals(
                ToolDisplayCategory.AGENT_PIPELINE,
                ToolDisplayResolver.fallbackDisplayCategory("agent_pipeline"));
    }

    @Test
    public void customAgentPrefixUsesAgentCategory() {
        Assert.assertEquals(
                ToolDisplayCategory.AGENT,
                ToolDisplayResolver.fallbackDisplayCategory("agentx_reviewer"));
    }
}
