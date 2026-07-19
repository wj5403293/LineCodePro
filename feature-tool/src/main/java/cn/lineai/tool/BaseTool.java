package cn.lineai.tool;

import android.content.Context;
import org.json.JSONObject;

public abstract class BaseTool implements ToolInfo {
    // Icon constants for getActionIcon(), values matching IconButtonView in the app module.
    public static final int ICON_EXPAND = 36;
    public static final int ICON_SEARCH = 65;
    public static final int ICON_GLOBE = 39;
    public static final int ICON_FOLDER_OPEN = 9;
    public static final int ICON_PAINTBRUSH = 48;
    public static final int ICON_SPARKLES = 38;
    public static final int ICON_SMARTPHONE = 57;

    public abstract String getName();

    public abstract String getDescription();

    public abstract ToolCategory getCategory();

    public boolean needsConfirmation() {
        return false;
    }

    public boolean isAllowedInReadonlyMode() {
        return false;
    }

    public String promptSupplement(String executionMode, boolean isSsh) {
        return null;
    }

    public abstract JSONObject getParameters() throws org.json.JSONException;

    public abstract ToolResult execute(JSONObject input, ToolContext context);

    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.GENERIC;
    }

    public String getDisplayLabel(Context context, JSONObject input, String workspacePath) {
        return null;
    }

    public String getActionName(Context context) {
        return null;
    }

    public int getActionIcon() {
        return 0;
    }

    public boolean isConcurrencySafe() {
        return false;
    }

    public boolean shouldRecordDiff() {
        return false;
    }

    public boolean shouldHideOnSuccess() {
        return false;
    }

    protected ToolResult ok(String content) {
        return ToolResult.of("", getName(), content, false);
    }

    protected ToolResult error(String message) {
        return ToolResult.of("", getName(), message, true);
    }

    public final JSONObject toJson() throws org.json.JSONException {
        JSONObject function = new JSONObject();
        function.put("name", getName());
        function.put("description", getDescription());
        function.put("parameters", getParameters());
        JSONObject wrapper = new JSONObject();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }
}
