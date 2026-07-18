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
 * Module split barrier: depends on tool framework (BaseTool, ToolCategory, etc.)
 * and LineCodeAccessibilityService in :app. See PhoneClickTool for full barrier notes.
 * No direct dependency on cn.lineai.ui.* classes.
 */
public final class PhoneGlobalActionTool extends BaseTool {
    public static final String NAME = "phone_global_action";
    private final Context context;

    public PhoneGlobalActionTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return NAME;
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
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.PHONE_CONTROL;
    }

    @Override
    public String getDisplayLabel(Context ctx, JSONObject input, String workspacePath) {
        if (ctx == null) return getName();
        String action = input.optString("action");
        if (action.length() == 0) {
            return ctx.getString(R.string.tool_call_phone_summary_global_action);
        }
        return globalActionLabel(ctx, action);
    }

    @Override
    public String getActionName(Context ctx) {
        return ctx == null ? getName() : ctx.getString(R.string.tool_call_phone_action_global_action);
    }

    private static String globalActionLabel(Context ctx, String action) {
        if ("back".equals(action)) return ctx.getString(R.string.tool_call_phone_global_back);
        if ("home".equals(action)) return ctx.getString(R.string.tool_call_phone_global_home);
        if ("exit_app".equals(action)) return ctx.getString(R.string.tool_call_phone_global_exit_app);
        if ("recents".equals(action)) return ctx.getString(R.string.tool_call_phone_global_recents);
        if ("notifications".equals(action)) return ctx.getString(R.string.tool_call_phone_global_notifications);
        if ("quick_settings".equals(action)) return ctx.getString(R.string.tool_call_phone_global_quick_settings);
        if ("power_dialog".equals(action)) return ctx.getString(R.string.tool_call_phone_global_power_dialog);
        if ("lock_screen".equals(action)) return ctx.getString(R.string.tool_call_phone_global_lock_screen);
        return ctx.getString(R.string.tool_call_phone_global_unknown, action);
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
