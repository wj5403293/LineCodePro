package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.R;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneSwipeTool extends BaseTool {
    private final Context context;

    public PhoneSwipeTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "phone_swipe";
    }

    @Override
    public String getDescription() {
        return context == null ? "Swipe between screen coordinates." : context.getString(R.string.phone_tool_swipe_description);
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("x1", new JSONObject().put("type", "number").put("description", "起点 X 坐标"))
                        .put("y1", new JSONObject().put("type", "number").put("description", "起点 Y 坐标"))
                        .put("x2", new JSONObject().put("type", "number").put("description", "终点 X 坐标"))
                        .put("y2", new JSONObject().put("type", "number").put("description", "终点 Y 坐标"))
                        .put("duration_ms", new JSONObject().put("type", "number").put("description", "滑动时长（毫秒），默认 300")))
                .put("required", new JSONArray().put("x1").put("y1").put("x2").put("y2"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        LineCodeAccessibilityService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        int x1 = input.optInt("x1");
        int y1 = input.optInt("y1");
        int x2 = input.optInt("x2");
        int y2 = input.optInt("y2");
        int durationMs = input.optInt("duration_ms", 300);
        boolean success = service.swipe(x1, y1, x2, y2, durationMs);
        return success ? ok(this.context.getString(R.string.phone_tool_swipe_success, x1, y1, x2, y2)) : error(this.context.getString(R.string.phone_tool_swipe_failed));
    }
}
