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

public final class PhoneGlobalActionTool extends BaseTool {
    private final Context context;

    public PhoneGlobalActionTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "phone_global_action";
    }

    @Override
    public String getDescription() {
        return context == null ? "Run a system global action." : context.getString(R.string.phone_tool_global_action_description);
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
                        .put("action", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray()
                                        .put("back")
                                        .put("home")
                                        .put("exit_app")
                                        .put("recents")
                                        .put("notifications")
                                        .put("quick_settings")
                                        .put("power_dialog")
                                        .put("lock_screen"))
                                .put("description", context == null ? "System action to run" : context.getString(R.string.phone_tool_global_action_param_desc))))
                .put("required", new JSONArray().put("action"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext toolContext) {
        LineCodeAccessibilityService service = PhoneControlToolSupport.service(context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, context);
        }
        String action = input.optString("action").trim();
        if (action.length() == 0) {
            return error(context.getString(R.string.phone_tool_global_action_missing));
        }
        boolean success = service.performPhoneAction(action);
        return success ? ok(context.getString(R.string.phone_tool_global_action_success, action)) : error(context.getString(R.string.phone_tool_global_action_failed, action));
    }
}
