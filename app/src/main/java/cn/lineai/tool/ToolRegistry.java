package cn.lineai.tool;

import android.content.Context;
import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.AgentTool;
import cn.lineai.tool.builtin.FileDeleteTool;
import cn.lineai.tool.builtin.FileEditTool;
import cn.lineai.tool.builtin.FileReadTool;
import cn.lineai.tool.builtin.FileWriteTool;
import cn.lineai.tool.builtin.GlobTool;
import cn.lineai.tool.builtin.HttpServerTool;
import cn.lineai.tool.builtin.ListDirectoryTool;
import cn.lineai.tool.builtin.ShellExecuteTool;
import cn.lineai.tool.builtin.WebFetchTool;
import cn.lineai.tool.builtin.WebSearchTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;

public final class ToolRegistry {
    private final Map<String, BaseTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        this(null);
    }

    public ToolRegistry(Context context) {
        register(new FileReadTool());
        register(new FileWriteTool());
        register(new FileEditTool());
        register(new FileDeleteTool());
        register(new GlobTool());
        register(new ListDirectoryTool());
        register(new HttpServerTool());
        register(new AgentTool());
        register(new AgentPipelineTool());
        register(new ShellExecuteTool(context));
        register(new WebSearchTool(context == null ? null : new WebSearchConfigRepository(context)));
        register(new WebFetchTool());
    }

    public void register(BaseTool tool) {
        if (tool != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public BaseTool get(String name) {
        return tools.get(name);
    }

    public List<BaseTool> getAll() {
        return new ArrayList<>(tools.values());
    }

    public List<BaseTool> getByNameSet(Set<String> names) {
        ArrayList<BaseTool> selected = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            return selected;
        }
        for (BaseTool tool : tools.values()) {
            if (names.contains(tool.getName())) {
                selected.add(tool);
            }
        }
        return selected;
    }

    public static JSONArray toJsonArray(Collection<BaseTool> tools) throws org.json.JSONException {
        JSONArray array = new JSONArray();
        if (tools == null) {
            return array;
        }
        for (BaseTool tool : tools) {
            array.put(tool.toJson());
        }
        return array;
    }
}
