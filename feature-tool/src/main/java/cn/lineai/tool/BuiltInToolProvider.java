package cn.lineai.tool;

import android.content.Context;
import cn.lineai.ipc.IpcProviderManager;

/**
 * Factory contract for built-in tools that require constructor dependencies
 * (Android Context, IpcProviderManager, etc). ToolRegistry iterates over the
 * registered providers and instantiates tools through {@link #create}, so new
 * built-ins no longer need to be wired in {@link ToolRegistry}'s constructor.
 */
public interface BuiltInToolProvider {
    /**
     * Build the tool instance. {@code context} is the application context
     * passed to {@link ToolRegistry}, which may be {@code null} in unit tests
     * that do not exercise Android. {@code ipcProviderManager} is the
     * terminal-provider bridge, which may also be {@code null}.
     */
    BaseTool create(Context context, IpcProviderManager ipcProviderManager);
}
