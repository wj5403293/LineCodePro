package cn.lineai.model;

public final class ChatMode {
    public static final String CHAT = "chat";
    public static final String PLAN = "plan";
    public static final String AGENT = "agent";
    public static final String CONTROL = "control";
    public static final String DEFAULT = AGENT;

    /** 与 PromptTemplateRepository 中的 ID 常量对应的模板标识符。 */
    public static final String TEMPLATE_ID_CHAT = "chatModeChat";
    public static final String TEMPLATE_ID_PLAN = "chatModePlan";
    public static final String TEMPLATE_ID_AGENT = "chatModeAgent";
    public static final String TEMPLATE_ID_CONTROL = "chatModeControl";

    private ChatMode() {
    }

    public static String normalize(String mode) {
        if (CHAT.equals(mode)) {
            return CHAT;
        }
        if (PLAN.equals(mode)) {
            return PLAN;
        }
        if (CONTROL.equals(mode)) {
            return CONTROL;
        }
        return AGENT;
    }

    /**
     * 返回给定模式对应的 promptTemplateId，可直接传给 PromptTemplateRepository.getTemplateText()。
     */
    public static String promptTemplateId(String mode) {
        String normalized = normalize(mode);
        if (CHAT.equals(normalized)) {
            return TEMPLATE_ID_CHAT;
        }
        if (PLAN.equals(normalized)) {
            return TEMPLATE_ID_PLAN;
        }
        if (CONTROL.equals(normalized)) {
            return TEMPLATE_ID_CONTROL;
        }
        return TEMPLATE_ID_AGENT;
    }

    public static String promptContext(String mode) {
        String normalized = normalize(mode);
        if (CHAT.equals(normalized)) {
            return "## Current Session Mode\n"
                    + "Current mode: Chat.\n"
                    + "- Chat is a read-only mode, used only for answering questions, explaining code, reading context, searching information, and listing directories.\n"
                    + "- Allowed read-only tools: file_read, glob, list_dir, web_search, web_fetch.\n"
                    + "- Writing, editing, and deleting files are prohibited; executing Shell is prohibited; starting services, running builds, running tests, installing dependencies, modifying permissions, or dispatching Agents are prohibited.\n"
                    + "- If the user asks to implement, fix, migrate, execute commands, or verify results, only state that switching to Agent mode is needed; if the user only wants a plan, suggest switching to Plan mode.";
        }
        if (PLAN.equals(normalized)) {
            return "## Current Session Mode\n"
                    + "Current mode: Plan.\n"
                    + "- Plan is a read-only planning mode. The goal is to understand requirements, read necessary context, form a plan, list risks, and confirm acceptance criteria; do not execute the plan.\n"
                    + "- Allowed read-only tools for gathering information: file_read, glob, list_dir, web_search, web_fetch. When reading, only read the minimum necessary files, do not expand the scope.\n"
                    + "- Calling any state-changing tools is prohibited: file_write, file_edit, file_delete, agent, agent_pipeline, custom Agents, write-type MCP, or tools with unknown side effects.\n"
                    + "- For local workspace targets, executing Shell is prohibited; use file_read, glob, list_dir to read context for local projects.\n"
                    + "- For SSH Shell targets, when local file_read, glob, list_dir are unavailable, shell_execute is allowed to view remote project content, but only side-effect-free project read commands may be executed.\n"
                    + "- SSH Plan allowed shell scope: pwd, ls/find to view directories, cat/sed/head/tail to view files, grep/rg to search text. Commands must be limited to the current project directory, with priority on limiting depth, line count, and result count.\n"
                    + "- SSH Plan prohibits detecting and changing the environment: do not run git status, builds, tests, installs, package managers, starting services, write redirects, tee, touch, mkdir, rm, mv, cp, chmod, chown, kill, ssh/scp/rsync, curl/wget downloads, database commands, or any commands that may change the remote state.\n"
                    + "- Modifying the local workspace, SSH remote, Skills directory, configuration, database, dependencies, permissions, processes, or network services is prohibited; do not create temporary files either.\n"
                    + "- If completing the task requires writing files, running tests, building, installing, starting services, calling Agents, or confirming the real environment state, you must stop at the planning stage and clearly inform the user to switch to Agent mode before executing.\n"
                    + "- The output should be an actionable plan: give the conclusion first, then list steps, involved files, required tools, verification commands, risks, and questions that need user confirmation.";
        }
        if (CONTROL.equals(normalized)) {
            return "## Current Session Mode\n"
                    + "Current mode: Control. Only phone control tools are allowed (phone_screenshot, phone_click, phone_swipe, phone_long_press, phone_view_hierarchy, phone_click_view, phone_global_action). Accessibility permission is required. After taking a screenshot, the returned path can be passed to the image understanding tool for analysis; if an image understanding model is not configured, the tool will prompt the user to configure one. File operations, shell, agent, and other tools are prohibited.\n";
        }
        return "## Current Session Mode\n"
                + "Current mode: Agent.\n"
                + "- Agent is the execution mode. When the user asks to implement, fix, migrate, verify, or execute commands, you can actively read context, call tools, modify files, and verify results.\n"
                + "- Before executing, still keep the scope minimal, prioritize reading relevant files, and state obvious risks; writing, deleting, Shell, and SSH operations must comply with current tool permissions and confirmation mechanisms.\n"
                + "- Under SSH Shell, clearly state the command purpose and working directory, avoid unrelated probing; do not modify task-unrelated files, environments, dependencies, or services.\n"
                + "- After completion, summarize changes, verify results, and note remaining risks; if blocked by permissions, missing configuration, or environment issues, state the specific blocker and next steps.";
    }
}
