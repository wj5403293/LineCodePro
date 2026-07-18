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

/**
 * Phone control tool: click at screen coordinates.
 *
 * <p>Module split barrier: this tool depends on the tool framework
 * ({@link BaseTool}, {@link ToolCategory}, {@link ToolContext}, {@link ToolResult},
 * {@link ToolDisplayCategory}) and {@link LineCodeAccessibilityService}, all of which
 * reside in the :app module. To extract into :feature-phone, the tool framework
 * interfaces must first be extracted into a shared :tool-framework module, and
 * {@link ToolRegistry} registration must support external module contributions
 * (e.g. via ServiceLoader or a registry callback). No direct dependency on
 * {@code cn.lineai.ui.*} classes.
 */
public final class PhoneClickTool extends BaseTool {
    public static final String NAME = "phone_click";
    private final Context context;

    public PhoneClickTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return context == null ? "Tap the specified screen coordinates." : context.getString(R.string.phone_tool_click_description);
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
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_click);
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("x", new JSONObject().put("type", "number").put("description", "屏幕 X 坐标"))
                        .put("y", new JSONObject().put("type", "number").put("description", "屏幕 Y 坐标")))
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
        boolean success = service.click(x, y);
        return success ? ok(this.context.getString(R.string.phone_tool_click_success, x, y)) : error(this.context.getString(R.string.phone_tool_click_failed));
    }
}
