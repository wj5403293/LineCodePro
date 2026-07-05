package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneLongPressTool extends BaseTool {
    private final Context context;

    public PhoneLongPressTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "phone_long_press";
    }

    @Override
    public String getDescription() {
        return context == null ? "Long-press the specified screen coordinates." : context.getString(R.string.phone_tool_long_press_description);
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.PHONE_CONTROL;
    }

    @Override
    public String getDisplayLabel(Context ctx, JSONObject input, String workspacePath) {
        if (ctx == null) return getName();
        return ctx.getString(R.string.tool_call_phone_summary_point, input.optInt("x"), input.optInt("y"));
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_long_press);
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("x", new JSONObject().put("type", "number").put("description", "屏幕 X 坐标"))
                        .put("y", new JSONObject().put("type", "number").put("description", "屏幕 Y 坐标"))
                        .put("duration_ms", new JSONObject().put("type", "number").put("description", "长按时长（毫秒），默认 800")))
                .put("required", new JSONArray().put("x").put("y"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        LineCodeAccessibilityService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        int x = input.optInt("x");
        int y = input.optInt("y");
        int durationMs = input.optInt("duration_ms", 800);
        boolean success = service.longPress(x, y, durationMs);
        return success ? ok(this.context.getString(R.string.phone_tool_long_press_success, x, y)) : error(this.context.getString(R.string.phone_tool_long_press_failed));
    }
}
