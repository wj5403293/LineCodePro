package cn.lineai.tool.builtin;

import cn.lineai.model.TodoItem;
import cn.lineai.state.TodoStateStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TodoUpdateTool extends BaseTool {
    public static final String NAME = "todo_update";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "维护当前会话的 TODO 列表。每次调用以完整列表覆盖旧列表；"
                + "状态会作为 {{TODO_STATE}} 注入到下一轮 system prompt，"
                + "帮助模型按顺序推进并及时更新进度。"
                + "状态取值：pending（未开始）/ in_progress（进行中）/ completed（已完成）。"
                + "同一时刻最多 1 个 in_progress；新任务应放在列表底部；"
                + "任务完成时立即从列表移除或改为 completed，不要保留中间状态。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.TODO;
    }

    @Override
    public JSONObject getParameters() throws JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("items", new JSONObject()
                                .put("type", "array")
                                .put("description", "完整 TODO 列表，会覆盖旧列表。")
                                .put("items", new JSONObject()
                                        .put("type", "object")
                                        .put("properties", new JSONObject()
                                                .put("content", new JSONObject()
                                                        .put("type", "string")
                                                        .put("description", "任务内容，简洁、可验证"))
                                                .put("status", new JSONObject()
                                                        .put("type", "string")
                                                        .put("enum", new JSONArray()
                                                                .put(TodoItem.STATUS_PENDING)
                                                                .put(TodoItem.STATUS_IN_PROGRESS)
                                                                .put(TodoItem.STATUS_COMPLETED))
                                                        .put("description", "任务状态：pending / in_progress / completed"))))))
                .put("required", new JSONArray().put("items"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (input == null) {
            return error("参数不能为空。");
        }
        JSONArray rawArray = input.optJSONArray("items");
        if (rawArray == null) {
            return error("缺少 items 数组。");
        }
        List<TodoItem> parsed = new ArrayList<>();
        for (int i = 0; i < rawArray.length(); i++) {
            JSONObject obj = rawArray.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            TodoItem item = TodoItem.fromJson(obj);
            if (item != null) {
                parsed.add(item);
            }
        }
        TodoStateStore store = context == null ? null : context.getTodoStateStore();
        if (store == null) {
            return error("TODO 状态存储未初始化。");
        }
        store.replace(parsed);
        int total = store.totalCount();
        int done = store.completedCount();
        String summary;
        if (total == 0) {
            summary = "已清空 TODO 列表。";
        } else {
            summary = "TODO 列表已更新，共 " + total + " 项，已完成 " + done + " 项。";
        }
        return ok(summary);
    }
}
