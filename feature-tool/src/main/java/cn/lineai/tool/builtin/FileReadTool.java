package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
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
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_action_read);
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
                return error(context.getString(R.string.tool_file_read_not_found, FileToolPathPolicy.displayPath(context.getHomePath(), file)));
            }
            if (file.isDirectory()) {
                StringBuilder builder = new StringBuilder();
                int[] count = new int[] {0};
                appendDirectory(builder, file, "", count, context);
                String list = builder.length() == 0 ? context.getString(R.string.tool_file_read_empty_dir) : builder.toString().trim();
                return ok(context.getString(R.string.tool_file_read_dir_content, FileToolPathPolicy.displayPath(context.getHomePath(), file), list)
                        + context.getString(R.string.tool_file_read_dir_specify_file));
            }

            int startKb = Math.max(0, input.optInt("start_kb", 0));
            int endKb = Math.max(startKb + 1, Math.min(50, input.optInt("end_kb", 50)));
            boolean hasKbRange = input.has("start_kb") || input.has("end_kb");

            // For files > 1MB, refuse to read entirely
            if (file.length() > 1024 * 1024) {
                return error(context.getString(R.string.tool_file_read_exceed_1mb,
                        FileToolPathPolicy.displayPath(context.getHomePath(), file),
                        file.length() / 1024,
                        input.optString("file_path")));
            }

            // No KB range specified and file > 50KB: suggest using KB range
            if (!hasKbRange && file.length() > LARGE_FILE_THRESHOLD_BYTES) {
                return error(context.getString(R.string.tool_file_read_exceed_50kb,
                        FileToolPathPolicy.displayPath(context.getHomePath(), file),
                        file.length() / 1024,
                        input.optString("file_path")));
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
                return error(context.getString(R.string.tool_file_read_start_out_of_range, startKb, content.length() / 1024));
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
                result.append(context.getString(R.string.tool_file_read_range_info, totalLines, startKb, endKb, content.length() / 1024));
            }

            return ok(ToolResult.truncateContent(result.toString()));
        } catch (Exception e) {
            return error(context.getString(R.string.tool_file_read_failed, e.getMessage()));
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

    private void appendDirectory(StringBuilder builder, File dir, String parentPath, int[] count, ToolContext context) {
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
                builder.append(context.getString(R.string.tool_file_read_dir_truncated));
                return;
            }
            String relative = parentPath.length() == 0 ? item.getName() : parentPath + "/" + item.getName();
            if (item.isDirectory()) {
                builder.append("[DIR]  ").append(relative).append("/\n");
                count[0]++;
                appendDirectory(builder, item, relative, count, context);
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
