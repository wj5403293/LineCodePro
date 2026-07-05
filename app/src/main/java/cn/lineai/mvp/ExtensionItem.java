package cn.lineai.mvp;

/**
 * 已安装扩展条目的统一数据载体，供 ExtensionDetailScreenView 统一渲染。
 */
public final class ExtensionItem {

    private final String id;
    private final String name;
    private final String description;
    private final boolean enabled;

    public ExtensionItem(String id, String name, String description, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
