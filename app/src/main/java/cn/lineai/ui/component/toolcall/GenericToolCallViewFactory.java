package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.tool.ToolDisplayCategory;

public final class GenericToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.GENERIC;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallGenericView(context, context.getString(R.string.tool_call_block_mcp));
    }
}
