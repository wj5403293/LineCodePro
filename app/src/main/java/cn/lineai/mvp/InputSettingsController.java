package cn.lineai.mvp;

import cn.lineai.model.InputSettings;
import cn.lineai.model.PromptTemplateItem;
import java.util.List;

public interface InputSettingsController {
    InputSettings getInputSettings();

    void onEnterKeyBehaviorChanged(String behavior);

    List<PromptTemplateItem> getPromptTemplates();

    void onPromptTemplateSaved(String id, String value);

    void onPromptTemplateReset(String id);
}
