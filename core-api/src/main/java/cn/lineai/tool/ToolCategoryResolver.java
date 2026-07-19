package cn.lineai.tool;

/**
 * Resolves the display category for a tool by name.
 * Decouples data layer from UI layer's ToolCallUtils.
 */
public interface ToolCategoryResolver {
    ToolDisplayCategory resolve(String toolName);
}
