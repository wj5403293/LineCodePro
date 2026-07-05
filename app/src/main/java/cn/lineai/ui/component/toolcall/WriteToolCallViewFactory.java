package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class WriteToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.WRITE;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallWriteView(context);
    }
}
