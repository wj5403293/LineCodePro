package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolResult;

final class PhoneControlToolSupport {
    private PhoneControlToolSupport() {
    }

    static LineCodeAccessibilityService service(Context context) {
        return LineCodeAccessibilityService.getReadyInstance(context);
    }

    static ToolResult unavailable(BaseTool tool, Context context) {
        if (context != null && LineCodeAccessibilityService.isServiceEnabled(context)) {
            return toolError(tool, context.getString(R.string.phone_tool_accessibility_not_ready));
        }
        if (context != null) {
            return toolError(tool, context.getString(R.string.phone_tool_accessibility_disabled));
        }
        return toolError(tool, "Accessibility service is not enabled.");
    }

    private static ToolResult toolError(BaseTool tool, String message) {
        return new ToolResult("", tool.getName(), message, true);
    }
}
