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

public final class FileReadTool extends BaseTool {
    public static final String NAME = "file_read";
    private static final long LARGE_FILE_THRESHOLD_BYTES = 50L * 1024L;
    private static final int MAX_DIRECTORY_ITEMS = 400;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "读取文件内容。返回带行号的文件内容；大文件请通过 start_kb/end_kb 分段读取。读取目录时返回目录树。";
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
        String filePath = input.optString("file_path");
        if (filePath.length() > 0) {
            return displayPath(workspacePath, filePath);
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
                        .put("file_path", new JSONObject().put("type", "string").put("description", "文件的绝对或相对路径"))
                        .put("start_kb", new JSONObject().put("type", "number").put("description", "起始位置，单位 KB，默认 0"))
                        .put("end_kb", new JSONObject().put("type", "number").put("description", "结束位置，单位 KB，默认 50，上限 50")))
                .put("required", new org.json.JSONArray().put("file_path"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        try {
            File file = FileToolPathPolicy.resolve(context, input.optString("file_path"));
            if (!file.exists()) {
                return error("文件不存在: " + FileToolPathPolicy.displayPath(context.getHomePath(), file));
            }
            if (file.isDirectory()) {
                StringBuilder builder = new StringBuilder();
                int[] count = new int[] {0};
                appendDirectory(builder, file, "", count);
                String list = builder.length() == 0 ? "(空目录)" : builder.toString().trim();
                return ok("目录 " + FileToolPathPolicy.displayPath(context.getHomePath(), file) + ":\n" + list
                        + "\n\n如需读取文件，请指定具体文件路径。");
            }

            int startKb = Math.max(0, input.optInt("start_kb", 0));
            int endKb = Math.max(startKb + 1, Math.min(50, input.optInt("end_kb", 50)));
            boolean hasKbRange = input.has("start_kb") || input.has("end_kb");

            // For files > 1MB, refuse to read entirely
            if (file.length() > 1024 * 1024) {
                return error("文件 " + FileToolPathPolicy.displayPath(context.getHomePath(), file)
                        + " 大小为 " + (file.length() / 1024) + "KB，超过 1MB 读取限制。\n"
                        + "请使用 start_kb/end_kb 指定更小的范围，例如："
                        + "{\"file_path\":\"" + input.optString("file_path") + "\",\"start_kb\":0,\"end_kb\":50}");
            }

            // No KB range specified and file > 50KB: suggest using KB range
            if (!hasKbRange && file.length() > LARGE_FILE_THRESHOLD_BYTES) {
                return error("文件 " + FileToolPathPolicy.displayPath(context.getHomePath(), file)
                        + " 大小为 " + (file.length() / 1024) + "KB，单次读取超过 50KB。\n"
                        + "请使用 start_kb 和 end_kb 指定读取范围，例如："
                        + "{\"file_path\":\"" + input.optString("file_path") + "\",\"start_kb\":0,\"end_kb\":50}");
            }

            String content = FileIo.readUtf8(file);

            if (!hasKbRange) {
                // Small file, no range specified: return entire content with line numbers
                String numbered = addLineNumbers(content, 1);
                return ok(ToolResult.truncateContent(numbered));
            }

            // KB range specified: extract the relevant portion
            int startChar = Math.min(startKb * 1024, content.length());
            int endChar = Math.min(endKb * 1024, content.length());
            if (startChar >= content.length()) {
                return error("start_kb=" + startKb + " 超出文件大小（文件共 "
                        + (content.length() / 1024) + "KB）");
            }

            // Snap to line boundaries
            if (startChar > 0) {
                int lineStart = content.lastIndexOf('\n', startChar - 1);
                startChar = lineStart >= 0 ? lineStart + 1 : 0;
            }
            if (endChar < content.length()) {
                int lineEnd = content.indexOf('\n', endChar);
                endChar = lineEnd >= 0 ? lineEnd + 1 : content.length();
            }

            // Count line number at startChar
            int startLineNumber = 1;
            for (int i = 0; i < startChar; i++) {
                if (content.charAt(i) == '\n') {
                    startLineNumber++;
                }
            }

            String extracted = content.substring(startChar, endChar);
            StringBuilder result = new StringBuilder();
            result.append(addLineNumbers(extracted, startLineNumber));

            // Add range info
            int totalLines = 1;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '\n') totalLines++;
            }
            if (startChar > 0 || endChar < content.length()) {
                result.append("\n\n... (共 ").append(totalLines).append(" 行，")
                        .append("显示 KB ").append(startKb).append('-').append(endKb)
                        .append(" / 共 ").append(content.length() / 1024).append("KB)");
            }

            return ok(ToolResult.truncateContent(result.toString()));
        } catch (Exception e) {
            return error("读取文件失败: " + e.getMessage());
        }
    }

    private String addLineNumbers(String content, int startLine) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(startLine + i).append('\t').append(lines[i]);
            if (i + 1 < lines.length) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private void appendDirectory(StringBuilder builder, File dir, String parentPath, int[] count) {
        if (count[0] >= MAX_DIRECTORY_ITEMS) {
            return;
        }
        File[] items = dir.listFiles();
        if (items == null) {
            return;
        }
        Arrays.sort(items, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File item : items) {
            if (count[0] >= MAX_DIRECTORY_ITEMS) {
                builder.append("... (目录项过多，已截断)\n");
                return;
            }
            String relative = parentPath.length() == 0 ? item.getName() : parentPath + "/" + item.getName();
            if (item.isDirectory()) {
                builder.append("[DIR]  ").append(relative).append("/\n");
                count[0]++;
                appendDirectory(builder, item, relative, count);
            } else {
                builder.append("[FILE] ").append(relative).append('\n');
                count[0]++;
            }
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
