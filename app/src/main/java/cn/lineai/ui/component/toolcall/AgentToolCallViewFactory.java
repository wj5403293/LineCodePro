package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class AgentToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.AGENT;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallAgentView(context);
    }
}
