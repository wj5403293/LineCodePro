package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;

public interface ToolCallViewFactory {
    ToolDisplayCategory category();
    ToolCallCardView createView(Context context);
}
