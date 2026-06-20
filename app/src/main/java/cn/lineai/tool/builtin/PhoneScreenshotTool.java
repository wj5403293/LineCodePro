package cn.lineai.tool.builtin;

import android.content.Context;
import android.graphics.Bitmap;
import cn.lineai.R;
import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneScreenshotTool extends BaseTool {
    private final Context context;

    public PhoneScreenshotTool(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "phone_screenshot";
    }

    @Override
    public String getDescription() {
        return context == null ? "Capture the current screen and return the saved image path." : context.getString(R.string.phone_tool_screenshot_description);
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject())
                .put("required", new JSONArray());
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (this.context == null) {
            return error("Screenshot tool is missing app context");
        }
        LineCodeAccessibilityService service = PhoneControlToolSupport.service(this.context);
        if (service == null) {
            return PhoneControlToolSupport.unavailable(this, this.context);
        }
        Bitmap bitmap = service.takeScreenshot();
        if (bitmap == null) {
            return error(this.context.getString(R.string.phone_tool_screenshot_failed));
        }
        File dir = PhoneScreenshotCache.directory(this.context);
        if (!dir.exists() && !dir.mkdirs()) {
            bitmap.recycle();
            return error(this.context.getString(R.string.phone_tool_screenshot_cache_dir_failed));
        }
        File file = new File(dir, String.format(Locale.ROOT, "screenshot-%d.png", System.currentTimeMillis()));
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return error(this.context.getString(R.string.phone_tool_screenshot_save_failed));
            }
            return ok(file.getAbsolutePath());
        } catch (Exception e) {
            return error(this.context.getString(R.string.phone_tool_screenshot_save_failed_with_reason, e.getMessage()));
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
            bitmap.recycle();
        }
    }
}
