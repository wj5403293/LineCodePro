package cn.lineai.ai;

/**
 * Provides skill-related prompts.
 * Decouples data layer from AI layer's SkillPromptBuilder.
 */
public interface SkillPromptProvider {
    String buildExtensionPrompt(String skillName, String skillContent, String workDirectory);
}
