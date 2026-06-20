package cn.lineai.tool;

import android.content.Context;
import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.tool.builtin.ImageGenerationTool;
import cn.lineai.tool.builtin.ImageUnderstandingTool;
import cn.lineai.tool.builtin.ListDirectoryTool;
import cn.lineai.tool.builtin.PhoneClickTool;
import cn.lineai.tool.builtin.PhoneClickViewTool;
import cn.lineai.tool.builtin.PhoneLongPressTool;
import cn.lineai.tool.builtin.PhoneScreenshotTool;
import cn.lineai.tool.builtin.PhoneSwipeTool;
import cn.lineai.tool.builtin.PhoneViewHierarchyTool;
import cn.lineai.tool.builtin.ShellExecuteTool;
import cn.lineai.tool.builtin.TodoUpdateTool;
import cn.lineai.tool.builtin.WebFetchTool;
import cn.lineai.tool.builtin.WebSearchTool;
import java.util.ArrayList;
import java.util.List;

/**
 * Static catalogue of built-in tool factories. New tools append an entry
 * to {@link #defaults()} — no edits to {@link ToolRegistry} are required.
 */
public final class BuiltInToolProviders {
    private BuiltInToolProviders() {
    }

    public static List<BuiltInToolProvider> defaults() {
        List<BuiltInToolProvider> list = new ArrayList<>();
        // Stateless tools with no Android dependencies.
        list.add((context, ipc) -> new FileReadTool());
        list.add((context, ipc) -> new FileWriteTool());
        list.add((context, ipc) -> new FileEditTool());
        list.add((context, ipc) -> new FileDeleteTool());
        list.add((context, ipc) -> new GlobTool());
        list.add((context, ipc) -> new ListDirectoryTool());
        list.add((context, ipc) -> new HttpServerTool());
        list.add((context, ipc) -> new AgentTool());
        list.add((context, ipc) -> new AgentPipelineTool());
        list.add((context, ipc) -> new TodoUpdateTool());
        list.add((context, ipc) -> new WebFetchTool());
        // Phone control tools need the accessibility service.
        list.add((context, ipc) -> new PhoneScreenshotTool(context));
        list.add((context, ipc) -> new PhoneClickTool());
        list.add((context, ipc) -> new PhoneSwipeTool());
        list.add((context, ipc) -> new PhoneLongPressTool());
        list.add((context, ipc) -> new PhoneViewHierarchyTool());
        list.add((context, ipc) -> new PhoneClickViewTool());
        // Tools that need the application context.
        list.add((context, ipc) -> new ShellExecuteTool(context, ipc));
        list.add((context, ipc) -> new ImageUnderstandingTool(context, ipc));
        list.add((context, ipc) -> new ImageGenerationTool(context));
        // WebSearchTool needs its own config repository.
        list.add((context, ipc) -> new WebSearchTool(
                context == null ? null : new WebSearchConfigRepository(context)));
        return list;
    }
}
