package cn.lineai.tool.builtin;

import cn.lineai.service.LineCodeAccessibilityService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneClickViewTool extends BaseTool {
    @Override
    public String getName() {
        return "phone_click_view";
    }

    @Override
    public String getDescription() {
        return "点击当前窗口中的 View。支持按 resource id、显示文本或屏幕坐标定位。需要无障碍服务已开启。";
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
                        .put("resource_id", new JSONObject().put("type", "string").put("description", "View 的 resource id，可传完整 id 或简写 name"))
                        .put("text", new JSONObject().put("type", "string").put("description", "View 上显示的文本"))
                        .put("x", new JSONObject().put("type", "integer").put("description", "屏幕坐标 x"))
                        .put("y", new JSONObject().put("type", "integer").put("description", "屏幕坐标 y")))
                .put("required", new JSONArray());
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        LineCodeAccessibilityService service = LineCodeAccessibilityService.getInstance();
        if (service == null) {
            return error("无障碍服务未开启");
        }
        String resourceId = input.optString("resource_id");
        String text = input.optString("text");
        int x = input.optInt("x", Integer.MIN_VALUE);
        int y = input.optInt("y", Integer.MIN_VALUE);
        if (resourceId.length() > 0) {
            boolean success = service.clickById(resourceId);
            return success ? ok("点击 View 成功: " + resourceId) : error("点击 View 失败: " + resourceId);
        }
        if (text.length() > 0) {
            boolean success = service.clickByText(text);
            return success ? ok("点击 View 成功: " + text) : error("点击 View 失败: " + text);
        }
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
            boolean success = service.clickByCoordinates(x, y);
            return success ? ok("点击坐标成功: (" + x + ", " + y + ")") : error("点击坐标失败: (" + x + ", " + y + ")");
        }
        return error("缺少定位参数，请提供 resource_id、text 或 x,y 坐标");
    }
}
