package cn.lineai.model;

/**
 * UI layer representation of an installed extension item, decoupled from
 * ExtensionItem in the mvp package.
 */
public final class ExtensionItemUiModel {
    private final String id;
    private final String name;
    private final String description;
    private final boolean enabled;

    public ExtensionItemUiModel(String id, String name, String description, boolean enabled) {
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
