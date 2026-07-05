package cn.lineai.tool;

/**
 * 工具分类枚举，同时承载基于工具名称的类型判断逻辑，
 * 供 UI 层与工具层共用，避免判断逻辑散落在各处。
 */
public enum ToolCategory {
    READ("read"),
    GENERATE("generate"),
    WRITE("write"),
    SYSTEM("system");

    private final String value;

    ToolCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Deprecated
    public static boolean isReadType(String name) {
        return "file_read".equals(name) || "glob".equals(name) || "list_dir".equals(name)
                || "web_search".equals(name) || "web_fetch".equals(name)
                || "image_understanding".equals(name)
                || "image_generation".equals(name);
    }

    @Deprecated
    public static boolean isWriteType(String name) {
        return "file_write".equals(name) || "file_edit".equals(name);
    }

    @Deprecated
    public static boolean isDeleteType(String name) {
        return "file_delete".equals(name);
    }

    @Deprecated
    public static boolean isHttpType(String name) {
        return "http_server".equals(name);
    }

    @Deprecated
    public static boolean isShellType(String name) {
        return "shell_execute".equals(name);
    }

    @Deprecated
    public static boolean isAgentType(String name) {
        return "agent".equals(name);
    }

    @Deprecated
    public static boolean isAgentPipelineType(String name) {
        return "agent_pipeline".equals(name);
    }

    @Deprecated
    public static boolean isTodoType(String name) {
        return "todo_update".equals(name);
    }

    @Deprecated
    public static boolean isImageGenerationType(String name) {
        return "image_generation".equals(name);
    }

    @Deprecated
    public static boolean isPhoneControlType(String name) {
        return name != null && name.startsWith("phone_");
    }

    @Deprecated
    public static boolean isCustomMcpType(String name) {
        return name != null && name.startsWith("mcpx_");
    }

    @Deprecated
    public static boolean isCustomAgentType(String name) {
        return name != null && name.startsWith("agentx_");
    }
}
