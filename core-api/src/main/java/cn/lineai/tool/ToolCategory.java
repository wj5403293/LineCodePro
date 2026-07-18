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
}
