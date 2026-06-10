package cn.lineai.tool;

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
