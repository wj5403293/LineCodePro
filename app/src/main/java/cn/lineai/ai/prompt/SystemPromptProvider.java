package cn.lineai.ai.prompt;

import android.content.Context;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.workspace.WorkspacePaths;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public final class SystemPromptProvider {
    private static final String TEMPLATE_PATH = "prompts/system-prompt-template.txt";
    private static final String WORK_DIRECTORY_TEMPLATE_PATH = "prompts/work-directory-template.txt";
    private static final String TONE_CODING_TEMPLATE_PATH = "prompts/tone-coding-template.txt";
    private static final String TONE_CHAT_TEMPLATE_PATH = "prompts/tone-chat-template.txt";

    private final Context context;
    private final WorkspacePaths workspacePaths;
    private StringTemplate cachedTemplate;
    private StringTemplate cachedWorkDirectoryTemplate;
    private StringTemplate cachedCodingToneTemplate;
    private StringTemplate cachedChatToneTemplate;

    public SystemPromptProvider(Context context) {
        this.context = context.getApplicationContext();
        this.workspacePaths = new WorkspacePaths(this.context);
    }

    public String build(String homePath) {
        return build(homePath, AiBehaviorSettings.TONE_CODING);
    }

    public String build(String homePath, String toneMode) {
        return build(homePath, toneMode, "");
    }

    public String build(String homePath, String toneMode, String learningContext) {
        return build(homePath, toneMode, learningContext, "");
    }

    public String build(String homePath, String toneMode, String learningContext, String toolsContext) {
        HashMap<String, String> values = new HashMap<>();
        values.put("TONE_CONTEXT", toneContext(toneMode));
        values.put("WORK_DIRECTORY_CONTEXT", workDirectoryContext(homePath));
        values.put("LEARNING_CONTEXT", learningContext == null ? "" : learningContext.trim());
        values.put("TOOLS_CONTEXT", toolsContext == null ? "" : toolsContext.trim());
        return template().render(values);
    }

    private StringTemplate template() {
        if (cachedTemplate == null) {
            cachedTemplate = new StringTemplate(readAsset(TEMPLATE_PATH));
        }
        return cachedTemplate;
    }

    private String workDirectoryContext(String homePath) {
        if (homePath == null || homePath.trim().length() == 0) {
            return "";
        }
        HashMap<String, String> values = new HashMap<>();
        values.put("HOME_PATH", homePath.trim());
        values.put("LINECODE_ROOT", workspacePaths.getLinecodeRoot().getAbsolutePath());
        values.put("GLOBAL_SKILLS_ROOT", workspacePaths.getSkillsRoot().getAbsolutePath());
        values.put("WORKSPACE_PRIVATE_ROOT", WorkspacePaths.join(homePath.trim(), ".linecode"));
        values.put("WORKSPACE_SKILLS_ROOT", WorkspacePaths.join(homePath.trim(), ".linecode/skills"));
        if (cachedWorkDirectoryTemplate == null) {
            cachedWorkDirectoryTemplate = new StringTemplate(readAsset(WORK_DIRECTORY_TEMPLATE_PATH));
        }
        return cachedWorkDirectoryTemplate.render(values);
    }

    private String toneContext(String toneMode) {
        if (AiBehaviorSettings.TONE_CHAT.equals(toneMode)) {
            if (cachedChatToneTemplate == null) {
                cachedChatToneTemplate = new StringTemplate(readAsset(TONE_CHAT_TEMPLATE_PATH));
            }
            return cachedChatToneTemplate.render(new HashMap<>());
        }
        if (cachedCodingToneTemplate == null) {
            cachedCodingToneTemplate = new StringTemplate(readAsset(TONE_CODING_TEMPLATE_PATH));
        }
        return cachedCodingToneTemplate.render(new HashMap<>());
    }

    private String readAsset(String path) {
        try {
            InputStream input = context.getAssets().open(path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalStateException("无法读取系统提示词模板: " + path, e);
        }
    }
}
