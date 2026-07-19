package cn.lineai.ai;

/**
 * Provides prompt templates.
 * Decouples feature-model from data layer's PromptTemplateRepository.
 */
public interface PromptTemplateProvider {
    String getTemplate(String templateName);
    String getCustomSystemPrompt();
    String getCustomReasoningEffort();
}
