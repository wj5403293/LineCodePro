package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public final class PhoneControlToolCallViewFactory implements ToolCallViewFactory {
    @Override
    public ToolDisplayCategory category() {
        return ToolDisplayCategory.PHONE_CONTROL;
    }

    @Override
    public ToolCallCardView createView(Context context) {
        return new ToolCallReadView(context);
    }
}
