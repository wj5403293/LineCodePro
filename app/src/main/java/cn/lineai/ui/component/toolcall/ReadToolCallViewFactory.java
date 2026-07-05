package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class ReadToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallReadView(context);
    }
}
