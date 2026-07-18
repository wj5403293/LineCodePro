package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.ChatMode;
import cn.lineai.model.PromptTemplateItem;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PromptTemplateRepository {
    public static final String ID_SYSTEM_PROMPT = "systemPrompt";
    public static final String ID_WORK_DIRECTORY = "workDirectory";
    public static final String ID_TONE_CODING = "toneCoding";
    public static final String ID_TONE_CHAT = "toneChat";
    public static final String ID_LEARNING_CONTEXT = "learningContext";
    public static final String ID_MEMORY_EXTRACTION = "memoryExtraction";
    public static final String ID_SKILL_EXTRACTION = "skillExtraction";
    public static final String ID_CONTEXT_COMPACTION = "contextCompaction";
    public static final String ID_CHAT_MODE_CHAT = "chatModeChat";
    public static final String ID_CHAT_MODE_PLAN = "chatModePlan";
    public static final String ID_CHAT_MODE_AGENT = "chatModeAgent";
    public static final String ID_CHAT_MODE_CONTROL = "chatModeControl";
    public static final String ID_MODEL_IDENTITY = "modelIdentity";
    public static final String ID_TODO_STATE = "todoState";
    public static final String ID_TODO_USAGE = "todoUsage";
    public static final String ID_AGENT_ROLE_EXPLORE_REMOTE = "agentRoleExploreRemote";
    public static final String ID_AGENT_ROLE_CODING_REMOTE = "agentRoleCodingRemote";
    public static final String ID_AGENT_ROLE_EXPLORE_LOCAL = "agentRoleExploreLocal";
    public static final String ID_AGENT_ROLE_CODING_LOCAL = "agentRoleCodingLocal";
    public static final String ID_AGENT_SYSTEM_PROMPT = "agentSystemPrompt";
    public static final String ID_IMAGE_UNDERSTANDING_TOOL_SYSTEM = "imageUnderstandingToolSystem";
    public static final String ID_CONTEXT_COMPACTION_SUMMARY_PREFIX = "contextCompactionSummaryPrefix";
    public static final String ID_CONTEXT_COMPACTION_RESPONSES_FALLBACK = "contextCompactionResponsesFallback";

    private static final String KEY_PREFIX = "@linecode_prompt_template_";
    private static final List<Definition> DEFINITIONS = buildDefinitions();

    private final Context context;
    private final SettingsRepository settingsRepository;

    public PromptTemplateRepository(Context context) {
        this.context = context.getApplicationContext();
        this.settingsRepository = new SettingsRepository(this.context);
    }

    public synchronized List<PromptTemplateItem> getTemplates() {
        ArrayList<PromptTemplateItem> items = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            String defaultText = defaultText(definition);
            String currentText = settingsRepository.getString(key(definition.id), defaultText);
            items.add(new PromptTemplateItem(
                    definition.id,
                    definition.title,
                    definition.description,
                    definition.sourceLabel,
                    definition.variables,
                    defaultText,
                    currentText,
                    !defaultText.equals(currentText)
            ));
        }
        return items;
    }

    public synchronized String getTemplateText(String id) {
        Definition definition = definitionFor(id);
        String defaultText = defaultText(definition);
        return settingsRepository.getString(key(definition.id), defaultText);
    }

    public synchronized String getDefaultTemplateText(String id) {
        return defaultText(definitionFor(id));
    }

    public synchronized void saveTemplate(String id, String value) {
        definitionFor(id);
        settingsRepository.setString(key(id), value == null ? "" : value);
    }

    public synchronized void resetTemplate(String id) {
        definitionFor(id);
        settingsRepository.remove(key(id));
    }

    public static List<String> templateIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            ids.add(definition.id);
        }
        return Collections.unmodifiableList(ids);
    }

    private Definition definitionFor(String id) {
        String safeId = id == null ? "" : id;
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(safeId)) {
                return definition;
            }
        }
        throw new IllegalArgumentException("未知提示词模板: " + safeId);
    }

    private String key(String id) {
        return KEY_PREFIX + id;
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
            throw new IllegalStateException("无法读取提示词模板: " + path, e);
        }
    }

    private String defaultText(Definition definition) {
        if (definition.assetPath.length() > 0) {
            return readAsset(definition.assetPath);
        }
        return definition.defaultText;
    }

    private static List<Definition> buildDefinitions() {
        ArrayList<Definition> definitions = new ArrayList<>();
        definitions.add(new Definition(
                ID_SYSTEM_PROMPT,
                "System 提示词",
                "主系统提示词，定义 LineCode 的身份、工作方式、工具循环、回答规范，并组合其它上下文模板。",
                "prompts/system-prompt-template.txt",
                "TOOLS_CONTEXT", "TONE_CONTEXT", "CHAT_MODE_CONTEXT", "WORK_DIRECTORY_CONTEXT", "LEARNING_CONTEXT", "MODEL_IDENTITY"
        ));
        definitions.add(new Definition(
                ID_WORK_DIRECTORY,
                "工作目录模板",
                "注入当前工作区、应用私有目录、Skills 目录和 SSH Skills 目录，约束文件操作默认路径。",
                "prompts/work-directory-template.txt",
                "HOME_PATH", "LINECODE_ROOT", "GLOBAL_SKILLS_ROOT", "WORKSPACE_PRIVATE_ROOT", "WORKSPACE_SKILLS_ROOT"
        ));
        definitions.add(new Definition(
                ID_TONE_CODING,
                "编程模式提示词",
                "AI 行为选择“编程模式”时追加的语气和输出风格约束。",
                "prompts/tone-coding-template.txt"
        ));
        definitions.add(new Definition(
                ID_TONE_CHAT,
                "聊天模式提示词",
                "AI 行为选择“聊天模式”时追加的语气和输出风格约束。",
                "prompts/tone-chat-template.txt"
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_CHAT,
                "会话 Chat 模式提示词",
                "顶部会话模式选择 Chat 时注入，只读交流、解释和搜索，不允许修改文件或执行 Shell。",
                "内置模板：ChatMode.CHAT",
                ChatMode.promptContext(ChatMode.CHAT)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_PLAN,
                "会话 Plan 模式提示词",
                "顶部会话模式选择 Plan 时注入，只读规划，允许收集上下文但禁止执行计划。",
                "内置模板：ChatMode.PLAN",
                ChatMode.promptContext(ChatMode.PLAN)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_AGENT,
                "会话 Agent 模式提示词",
                "顶部会话模式选择 Agent 时注入，允许按权限读取、修改、执行和验证任务。",
                "内置模板：ChatMode.AGENT",
                ChatMode.promptContext(ChatMode.AGENT)
        ));
        definitions.add(new Definition(
                ID_CHAT_MODE_CONTROL,
                "会话 Control 模式提示词",
                "顶部会话模式选择 Control 时注入，仅允许手机控制工具，禁止文件操作、Shell 和 Agent。",
                "内置模板：ChatMode.CONTROL",
                ChatMode.promptContext(ChatMode.CONTROL)
        ));
        definitions.add(new Definition(
                ID_LEARNING_CONTEXT,
                "学习模式上下文模板",
                "学习模式开启时，把短期记忆、长期记忆、聊天检索和 Skills 检索结果渲染进 system prompt。",
                "prompts/learning-context-template.txt",
                "WORKING_MEMORY_SECTION", "MEMORY_SECTION", "HISTORY_SECTION", "SKILL_PATHS_SECTION",
                "SKILLS_SECTION", "PRIVATE_BOUNDARY_SECTION"
        ));
        definitions.add(new Definition(
                ID_MEMORY_EXTRACTION,
                "长期记忆提取模板",
                "学习模式保存记忆时使用，指导模型从本轮对话中提取 user/project/environment 记忆。",
                "prompts/memory-extraction-template.txt",
                "PROJECT_ID", "USER_INPUT", "TRANSCRIPT"
        ));
        definitions.add(new Definition(
                ID_SKILL_EXTRACTION,
                "Skills 沉淀模板",
                "学习模式判断是否生成可复用 Skill 时使用，约束返回 JSON 和 Skill 内容格式。",
                "prompts/skill-extraction-template.txt",
                "PROJECT_ID", "USER_INPUT", "TRANSCRIPT"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION,
                "上下文压缩模板",
                "上下文过长时用于总结旧对话，要求模型输出可恢复任务状态的压缩摘要。",
                "prompts/context-compaction-template.txt"
        ));
        definitions.add(new Definition(
                ID_MODEL_IDENTITY,
                "模型身份提示词",
                "把当前模型的 modelId、名称、提供方和协议注入到 system prompt，让模型在回答自身能力相关问题时以模型标识为依据。",
                "prompts/model-identity-template.txt",
                "MODEL_ID", "MODEL_NAME", "MODEL_PROVIDER", "MODEL_PROTOCOL"
        ));
        definitions.add(new Definition(
                ID_TODO_STATE,
                "TODO 状态模板",
                "把当前 TODO 列表注入到 system prompt，引导模型按顺序推进并及时更新状态。",
                "prompts/todo-state-template.txt",
                "TODO_LIST"
        ));
        definitions.add(new Definition(
                ID_TODO_USAGE,
                "TODO 使用引导模板",
                "在当前 TODO 列表为空时注入到 system prompt，推荐 Plan/Agent 模式使用 todo_update 拆分任务，并解释跨轮次持续维护的语义。",
                "prompts/todo-usage-template.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_EXPLORE_REMOTE,
                "Agent 远程探索角色提示词",
                "SSH 远端或终端提供者模式下 explore 类型 Agent 的角色定义和规则约束。",
                "prompts/agent-role-explore-remote.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_CODING_REMOTE,
                "Agent 远程编码角色提示词",
                "SSH 远端或终端提供者模式下 coding 类型 Agent 的角色定义和规则约束。",
                "prompts/agent-role-coding-remote.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_EXPLORE_LOCAL,
                "Agent 本地探索角色提示词",
                "本地模式下 explore 类型 Agent 的角色定义和规则约束。",
                "prompts/agent-role-explore-local.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_ROLE_CODING_LOCAL,
                "Agent 本地编码角色提示词",
                "本地模式下 coding 类型 Agent 的角色定义和规则约束。",
                "prompts/agent-role-coding-local.txt"
        ));
        definitions.add(new Definition(
                ID_AGENT_SYSTEM_PROMPT,
                "Agent 系统提示词模板",
                "Agent 系统提示词的组合模板，将角色、任务、工作区、范围、扩展和工具上下文拼接为完整 system prompt。",
                "prompts/agent-system-prompt-template.txt",
                "ROLE_PROMPT", "TASK_DESCRIPTION", "WORKSPACE_CONTEXT", "SCOPE_CONTEXT", "EXTENSIONS_CONTEXT", "TOOLS_CONTEXT"
        ));
        definitions.add(new Definition(
                ID_IMAGE_UNDERSTANDING_TOOL_SYSTEM,
                "图片理解工具系统提示词",
                "图片理解工具发送给视觉模型的 system prompt，约束模型只返回与图片和提示相关的分析内容。",
                "prompts/image-understanding-tool-system.txt"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION_SUMMARY_PREFIX,
                "上下文压缩摘要前缀",
                "上下文压缩后注入的摘要前缀，包含格式化的摘要内容，指示模型从上一段对话继续。",
                "prompts/context-compaction-summary-prefix.txt",
                "SUMMARY"
        ));
        definitions.add(new Definition(
                ID_CONTEXT_COMPACTION_RESPONSES_FALLBACK,
                "上下文压缩 Responses 回退前缀",
                "使用 OpenAI Responses compact API 压缩后注入的回退前缀，指示模型从上一段对话继续。",
                "prompts/context-compaction-responses-fallback.txt"
        ));
        return Collections.unmodifiableList(definitions);
    }

    private static final class Definition {
        final String id;
        final String title;
        final String description;
        final String assetPath;
        final String sourceLabel;
        final String defaultText;
        final String[] variables;

        Definition(String id, String title, String description, String assetPath, String... variables) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.assetPath = assetPath;
            this.sourceLabel = assetPath;
            this.defaultText = "";
            this.variables = variables == null ? new String[0] : variables;
        }

        Definition(String id, String title, String description, String source, String defaultText) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.assetPath = "";
            this.sourceLabel = source;
            this.defaultText = defaultText == null ? "" : defaultText;
            this.variables = new String[0];
        }
    }
}
