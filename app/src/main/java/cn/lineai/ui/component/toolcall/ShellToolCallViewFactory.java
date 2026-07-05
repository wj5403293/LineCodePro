package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class ShellToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.SHELL;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallShellView(context);
    }
}
