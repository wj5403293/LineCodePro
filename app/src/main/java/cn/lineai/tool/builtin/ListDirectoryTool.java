package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

public final class ListDirectoryTool extends BaseTool {
    @Override
    public String getName() {
        return "list_dir";
    }

    @Override
    public String getDescription() {
        return "列出目录下的直接文件和文件夹。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public String getDisplayLabel(Context ctx, JSONObject input, String workspacePath) {
        String path = input.optString("path");
        if (path.length() > 0) {
            return displayPath(workspacePath, path);
        }
        return null;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "目录的绝对或相对路径，可选，默认为 home 目录")));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            File dir = FileToolPathPolicy.resolve(context, input.optString("path"));
            if (!dir.exists()) {
                return error("目录不存在: " + input.optString("path", "."));
            }
            if (!dir.isDirectory()) {
                return error("路径不是目录: " + input.optString("path", "."));
            }
            File[] items = dir.listFiles();
            if (items == null || items.length == 0) {
                return ok("目录 " + FileToolPathPolicy.displayPath(context.getHomePath(), dir) + ":\n(空目录)");
            }
            Arrays.sort(items, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            StringBuilder builder = new StringBuilder();
            builder.append("目录 ").append(FileToolPathPolicy.displayPath(context.getHomePath(), dir)).append(":\n");
            for (File item : items) {
                builder.append(item.isDirectory() ? "[DIR]  " : "[FILE] ")
                        .append(item.getName())
                        .append(item.isDirectory() ? "/" : "")
                        .append('\n');
            }
            return ok(builder.toString().trim());
        } catch (Exception e) {
            return error("列目录失败: " + e.getMessage());
        }
    }

    /** 将绝对路径转换为相对于工作区的展示路径。 */
    private static String displayPath(String workspacePath, String path) {
        if (path == null || path.trim().length() == 0) return "";
        String value = path.trim().replace('\\', '/');
        if (value.startsWith("file://")) {
            value = value.substring("file://".length());
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.length() == 0) return "";
        if (!value.startsWith("/")) {
            while (value.startsWith("./")) {
                value = value.substring(2);
            }
            return value;
        }
        String root = workspacePath == null ? "" : workspacePath.trim().replace('\\', '/');
        while (root.length() > 1 && root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.length() == 0 || !root.startsWith("/")) return value;
        if (value.equals(root)) return ".";
        String prefix = root + "/";
        if (value.startsWith(prefix)) {
            String relative = value.substring(prefix.length());
            while (relative.startsWith("./")) {
                relative = relative.substring(2);
            }
            return relative;
        }
        return value;
    }
}
