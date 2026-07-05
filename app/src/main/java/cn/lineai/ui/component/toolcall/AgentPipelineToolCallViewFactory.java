package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class AgentPipelineToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.AGENT_PIPELINE;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallAgentPipelineView(context);
    }
}
