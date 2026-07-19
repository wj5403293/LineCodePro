package cn.lineai.tool;

/**
 * Renders tool prompts for the tool system prompt.
 * Decouples data layer from AI layer's prompt builder.
 */
public interface ToolPromptRenderer {
    String renderToolPrompt(java.util.List<ToolInfo> enabledTools, boolean includeConfirmationHints);
}
