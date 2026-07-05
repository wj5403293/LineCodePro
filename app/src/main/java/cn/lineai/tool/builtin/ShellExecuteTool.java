package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.ipc.terminal.TerminalShellCallback;
import cn.lineai.ipc.terminal.TerminalShellResult;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ShellExecuteTool extends BaseTool {
    private final SshService sshService;
    private final ToolSettingsStore settingsRepository;
    private final IpcProviderManager ipcProviderManager;

    public ShellExecuteTool(Context context) {
        this(context, null);
    }

    public ShellExecuteTool(Context context, IpcProviderManager ipcProviderManager) {
        sshService = context == null ? null : new SshService(context);
        settingsRepository = context == null ? null : new ToolSettingsRepository(context);
        this.ipcProviderManager = ipcProviderManager;
    }

    @Override
    public String getName() {
        return "shell_execute";
    }

    @Override
    public String getDescription() {
        return "通过当前执行目标执行 shell 命令：SSH 模式走 SSH，终端提供者模式走 IPC。命令会在执行前请求用户确认。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.SHELL;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("command", new JSONObject()
                                .put("type", "string")
                                .put("description", "要执行的 shell 命令"))
                        .put("cwd", new JSONObject()
                                .put("type", "string")
                                .put("description", "可选工作目录，会通过 cd 后执行命令"))
                        .put("timeoutMs", new JSONObject()
                                .put("type", "number")
                                .put("description", "可选超时时间，单位毫秒，默认 30000，最大 300000")))
                .put("required", new JSONArray().put("command"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String inputCommand = input.optString("command", "");
        String inputCwd = input.optString("cwd", "");
        if (inputCommand.trim().length() == 0) {
            return error("命令不能为空");
        }
        int timeoutMs = Math.max(1000, Math.min(input.optInt("timeoutMs", 30000), 300000));
        String cwd = inputCwd.trim().length() > 0
                ? inputCwd.trim()
                : context == null ? "" : context.getHomePath().trim();
        if (isTerminalProviderMode()) {
            return executeViaTerminalProvider(inputCommand, cwd, timeoutMs, context);
        }
        return executeViaSsh(inputCommand, cwd, timeoutMs, context);
    }

    private boolean isTerminalProviderMode() {
        return settingsRepository != null
                && ToolSettingsRepository.EXECUTION_TERMINAL_PROVIDER.equals(settingsRepository.getExecutionMode());
    }

    private ToolResult executeViaTerminalProvider(String command, String cwd, long timeoutMs, ToolContext context) {
        if (ipcProviderManager == null) {
            return error("终端提供者管理器未初始化。");
        }
        TerminalIpcProvider provider = ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL) instanceof TerminalIpcProvider
                ? (TerminalIpcProvider) ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL)
                : null;
        if (provider == null) {
            return error("没有已绑定的终端提供者。请在设置中添加并启用终端提供者。");
        }
        if (!provider.isBound()) {
            return error("终端提供者服务未绑定。");
        }
        if (context != null) {
            context.reportToolProgress(getName(), "", false);
        }
        StringBuilder streamedOutput = new StringBuilder();
        try {
            TerminalShellResult result = provider.executeShell(command, cwd, timeoutMs, new TerminalShellCallback() {
                @Override
                public void onOutput(String content) {
                    synchronized (streamedOutput) {
                        streamedOutput.setLength(0);
                        streamedOutput.append(content == null ? "" : content);
                    }
                    if (context != null) {
                        context.reportToolProgress(getName(), content, false);
                    }
                }

                @Override
                public void onError(String error) {
                }

                @Override
                public void onComplete(int exitCode) {
                }
            });
            String output = streamedOutput.toString().trim();
            if (!result.isSuccess()) {
                String message = "命令执行失败，退出码: " + result.getExitCode();
                return error(output.length() == 0 ? message : output + "\n" + message);
            }
            return ok(output.length() == 0 ? "命令执行完成，无输出" : output);
        } catch (Exception e) {
            restoreInterrupt(e);
            String existing;
            synchronized (streamedOutput) {
                existing = streamedOutput.toString().trim();
            }
            String message = "命令执行失败: " + describeException(e);
            return error(existing.length() == 0 ? message : existing + "\n" + message);
        }
    }

    private ToolResult executeViaSsh(String inputCommand, String cwd, long timeoutMs, ToolContext context) {
        if (sshService == null) {
            return error("当前环境未初始化 SSH 服务。");
        }
        String command = cwd.length() > 0
                ? "cd " + shellQuote(cwd) + " && " + inputCommand
                : inputCommand;
        StringBuilder streamedOutput = new StringBuilder();
        if (context != null) {
            context.reportToolProgress(getName(), "", false);
        }
        try {
            String output = sshService.executeCommand(command, (int) timeoutMs, null, streamed -> {
                synchronized (streamedOutput) {
                    streamedOutput.setLength(0);
                    streamedOutput.append(streamed == null ? "" : streamed);
                }
                if (context != null) {
                    context.reportToolProgress(getName(), streamed, false);
                }
            });
            return ok(output.length() == 0 ? "命令执行完成，无输出" : output);
        } catch (Exception e) {
            restoreInterrupt(e);
            String existing;
            synchronized (streamedOutput) {
                existing = streamedOutput.toString().trim();
            }
            String message = "命令执行失败: " + describeException(e);
            return error(existing.length() == 0 ? message : existing + "\n" + message);
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void restoreInterrupt(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describeException(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message.trim();
        }
        String name = error.getClass().getSimpleName();
        return name.length() == 0 ? "未知错误" : name;
    }
}
