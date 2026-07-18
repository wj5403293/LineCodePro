package cn.lineai.tool;

public final class ToolArgs {
    private ToolArgs() {
    }

    public static void requireNonEmpty(String value, String paramName) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }
}
