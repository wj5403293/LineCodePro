package cn.lineai.data.repository;

import cn.lineai.model.McpToolConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolPromptRenderer {

    static String renderToolPrompt(
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        return renderToolPrompt(ToolSettingsStore.EXECUTION_LOCAL, configs, enabled, toolByName, nativeToolProtocol);
    }

    static String renderToolPrompt(
            String executionMode,
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        if (enabled.isEmpty()) {
            return "## 可用工具\n当前没有可用工具。未配置模型工具、工具组被关闭或当前权限模式禁用了所有工具。";
        }
        String mode = ToolSettingsRepository.normalizeExecutionMode(executionMode);
        if (ToolSettingsStore.EXECUTION_SSH.equals(mode)) {
            return renderRemoteToolPrompt(mode, configs, enabled, toolByName, nativeToolProtocol);
        }
        if (ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(mode)) {
            return renderRemoteToolPrompt(mode, configs, enabled, toolByName, nativeToolProtocol);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 可用工具\n以下工具列表由当前 MCP 设置、权限模式、执行目标和已注册工具动态生成。未列出的工具不可用，工具执行必须遵守当前权限模式。\n\n");
        List<McpToolConfig> promptConfigs = configs == null ? new ArrayList<>() : configs;
        HashSet<String> renderedTools = new HashSet<>();
        for (McpToolConfig config : promptConfigs) {
            ArrayList<String> tools = new ArrayList<>();
            for (String tool : config.getTools()) {
                if (enabled.contains(tool)) {
                    tools.add(tool);
                    renderedTools.add(tool);
                }
            }
            if (tools.isEmpty()) {
                continue;
            }
            builder.append("### ").append(config.getName()).append('\n');
            for (String toolName : tools) {
                BaseTool tool = toolByName == null ? null : toolByName.get(toolName);
                builder.append("  - ").append(toolName);
                if (tool != null) {
                    builder.append(" [").append(categoryLabel(tool.getCategory()));
                    if (tool.requiresConfirmation()) {
                        builder.append(", 需要确认");
                    }
                    builder.append("]：").append(tool.getDescription()).append('\n');
                    try {
                        builder.append("    参数: ").append(tool.getParameters().toString()).append('\n');
                    } catch (Exception ignored) {
                        builder.append("    参数: {}\n");
                    }
                } else {
                    builder.append('\n');
                }
            }
            builder.append('\n');
        }
        appendExtensionTools(builder, enabled, renderedTools, toolByName);
        if (nativeToolProtocol) {
            builder.append("工具调用由当前模型协议的原生 tools/function calling 机制提供。需要读取、写入、搜索、生成图片或列目录时，必须使用原生工具调用，不要把工具调用 JSON、XML、<tool_calls> 或 Markdown 代码块输出到正文。")
                    .append("每次工具返回后必须继续分析结果；如果任务还没完成，继续调用合适工具执行下一步。");
        } else {
            builder.append("工具调用格式已锁定：需要调用工具时，只能输出 <tool_calls><tool_call name=\"工具名\"><argument name=\"参数名\">参数值</argument></tool_calls>。")
                    .append("不要输出 OpenAI tool_calls JSON、Markdown 代码块或自然语言包装。每次工具返回后必须继续分析结果；如果任务还没完成，继续调用合适工具执行下一步。");
        }
        return builder.toString().trim();
    }

    private static String renderRemoteToolPrompt(
            String executionMode,
            List<McpToolConfig> configs,
            Set<String> enabled,
            Map<String, BaseTool> toolByName,
            boolean nativeToolProtocol
    ) {
        boolean isSsh = ToolSettingsStore.EXECUTION_SSH.equals(executionMode);
        StringBuilder builder = new StringBuilder();
        builder.append("## 可用工具\n");
        if (isSsh) {
            builder.append("当前执行目标是 SSH Shell。本地文件读写、文件搜索和 HTTP 服务器已禁用。\n")
                    .append("Agent、Agent Pipeline、任务清单仍可用，子 Agent 只能通过 shell_execute 在 SSH 环境内完成文件操作。\n")
                    .append("图片理解会通过 SFTP 读取 SSH 工作区图片；网页搜索、图片生成和应用侧自定义 HTTP MCP 可用时仍会作为工具提供。\n")
                    .append("不要引用应用私有 home 工作目录；如果系统提示提供了 SSH 项目目录，必须在该目录内操作。\n")
                    .append("如需读取、写入、列目录或搜索文件，请通过 shell 命令在 SSH 环境内完成。\n\n");
        } else {
            builder.append("当前执行目标是终端提供者（Terminal Provider）。本地文件读写、文件搜索和 HTTP 服务器已禁用。\n")
                    .append("Agent、Agent Pipeline、任务清单仍可用，子 Agent 只能通过 shell_execute 在终端提供者环境内完成文件操作。\n")
                    .append("图片理解会通过 IPC 读取终端提供者环境的图片；网页搜索、图片生成和应用侧自定义 HTTP MCP 可用时仍会作为工具提供。\n")
                    .append("不要引用应用私有 home 工作目录；如果系统提示提供了终端提供者工作目录，必须在该目录内操作。\n")
                    .append("如需读取、写入、列目录或搜索文件，请通过 shell 命令在终端提供者环境内完成。\n\n");
        }
        List<McpToolConfig> promptConfigs = configs == null ? new ArrayList<>() : configs;
        HashSet<String> renderedTools = new HashSet<>();
        for (McpToolConfig config : promptConfigs) {
            ArrayList<String> tools = new ArrayList<>();
            for (String tool : config.getTools()) {
                if (enabled.contains(tool)) {
                    tools.add(tool);
                    renderedTools.add(tool);
                }
            }
            if (tools.isEmpty()) {
                continue;
            }
            builder.append("### ").append(config.getName()).append('\n');
            for (String toolName : tools) {
                BaseTool tool = toolByName == null ? null : toolByName.get(toolName);
                builder.append("  - ").append(toolName);
                if (tool != null) {
                    builder.append(" [").append(categoryLabel(tool.getCategory()));
                    if (tool.requiresConfirmation()) {
                        builder.append(", 需要确认");
                    }
                    builder.append("]：").append(tool.getDescription()).append('\n');
                    try {
                        builder.append("    参数: ").append(tool.getParameters().toString()).append('\n');
                    } catch (Exception ignored) {
                        builder.append("    参数: {}\n");
                    }
                } else {
                    builder.append('\n');
                }
            }
            if ("shell".equals(config.getId())) {
                if (isSsh) {
                    builder.append("shell_execute 默认在当前工作区目录执行；如需临时切换目录，再显式设置 cwd。\n");
                } else {
                    builder.append("shell_execute 通过终端提供者 IPC 执行；默认在当前工作区目录执行；如需临时切换目录，再显式设置 cwd。\n");
                }
            } else if ("web_search".equals(config.getId())) {
                if (isSsh) {
                    builder.append("web_search 和 web_fetch 由应用侧网络配置执行，不依赖 SSH 主机环境。\n");
                } else {
                    builder.append("web_search 和 web_fetch 由应用侧网络配置执行，不依赖终端提供者环境。\n");
                }
            } else if ("image_understanding".equals(config.getId())) {
                if (isSsh) {
                    builder.append("image_understanding 通过 SFTP 读取 SSH 工作区图片，再由应用侧视觉模型配置执行。\n");
                } else {
                    builder.append("image_understanding 通过 IPC 读取终端提供者环境图片，再由应用侧视觉模型配置执行。\n");
                }
            } else if ("image_generation".equals(config.getId())) {
                builder.append("image_generation 由应用侧生图模型配置执行，不依赖").append(isSsh ? "SSH 主机环境" : "终端提供者环境").append("；结果会以内联 Markdown 图片返回。\n");
            }
            builder.append('\n');
        }
        appendExtensionTools(builder, enabled, renderedTools, toolByName);
        builder.append("每次工具返回后必须继续分析输出；如果任务还没完成，继续调用合适工具执行下一步。")
                .append("不要因为刚执行过一次或两次 shell 命令就结束；只有确认任务完成、受阻或需要用户决定时才回复用户。\n");
        if (nativeToolProtocol) {
            builder.append("工具调用由当前模型协议的原生 tools/function calling 机制提供。不要把工具调用 JSON、XML、<tool_calls> 或 Markdown 代码块输出到正文。");
        } else {
            builder.append("工具调用格式已锁定：需要调用工具时，只能输出 <tool_calls><tool_call name=\"工具名\"><argument name=\"参数名\">参数值</argument></tool_calls>。")
                    .append("不要输出 OpenAI tool_calls JSON、Markdown 代码块或自然语言包装。");
        }
        return builder.toString().trim();
    }

    private static void appendExtensionTools(
            StringBuilder builder,
            Set<String> enabled,
            Set<String> renderedTools,
            Map<String, BaseTool> toolByName
    ) {
        ArrayList<String> extensionTools = new ArrayList<>();
        for (String toolName : enabled) {
            if (!renderedTools.contains(toolName) && ToolRegistry.isExtensionToolName(toolName)) {
                extensionTools.add(toolName);
            }
        }
        java.util.Collections.sort(extensionTools);
        if (extensionTools.isEmpty()) {
            return;
        }
        builder.append("### 扩展\n");
        for (String toolName : extensionTools) {
            BaseTool tool = toolByName == null ? null : toolByName.get(toolName);
            builder.append("  - ").append(toolName);
            if (tool != null) {
                builder.append(" [").append(categoryLabel(tool.getCategory())).append("]：")
                        .append(tool.getDescription()).append('\n');
                try {
                    builder.append("    参数: ").append(tool.getParameters().toString()).append('\n');
                } catch (Exception ignored) {
                    builder.append("    参数: {}\n");
                }
            } else {
                builder.append('\n');
            }
        }
        builder.append('\n');
    }

    static String categoryLabel(ToolCategory category) {
        if (category == ToolCategory.GENERATE) {
            return "generate";
        }
        if (category == ToolCategory.WRITE) {
            return "write";
        }
        if (category == ToolCategory.SYSTEM) {
            return "system";
        }
        return "read";
    }
}
