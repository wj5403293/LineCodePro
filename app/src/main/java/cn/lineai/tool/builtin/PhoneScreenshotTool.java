package cn.lineai.tool.builtin;

import android.content.Context;
import android.graphics.Bitmap;
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
        return "截取当前屏幕，保存到应用缓存目录并返回图片文件路径。需要无障碍服务已开启。";
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
            return error("截图工具未接入应用上下文");
        }
        LineCodeAccessibilityService service = LineCodeAccessibilityService.getInstance();
        if (service == null) {
            return error("无障碍服务未开启");
        }
        Bitmap bitmap = service.takeScreenshot();
        if (bitmap == null) {
            return error("截图失败");
        }
        File dir = PhoneScreenshotCache.directory(this.context);
        if (!dir.exists() && !dir.mkdirs()) {
            bitmap.recycle();
            return error("截图缓存目录创建失败");
        }
        File file = new File(dir, String.format(Locale.ROOT, "screenshot-%d.png", System.currentTimeMillis()));
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return error("截图保存失败");
            }
            return ok(file.getAbsolutePath());
        } catch (Exception e) {
            return error("截图保存失败: " + e.getMessage());
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
