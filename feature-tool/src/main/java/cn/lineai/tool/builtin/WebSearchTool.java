package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.data.repository.WebSearchConfigRepository;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import java.util.List;
import org.json.JSONObject;

public final class WebSearchTool extends BaseTool {
    public static final String NAME = "web_search";
    private final WebSearchConfigRepository configRepository;
    private final WebSearchService webSearchService = new WebSearchService();

    public WebSearchTool() {
        this(null);
    }

    public WebSearchTool(WebSearchConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String promptSupplement(String executionMode, boolean isSsh) {
        if (isSsh) {
            return "web_search 和 web_fetch 由应用侧网络配置执行，不依赖 SSH 主机环境。";
        }
        return "web_search 和 web_fetch 由应用侧网络配置执行，不依赖终端提供者环境。";
    }

    @Override
    public String getDescription() {
        return "搜索互联网信息。需要用户先在 MCP 工具设置中配置搜索 API、模型/搜索源和密钥。适合查询最新事实、文档、新闻和网页资料。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_action_search);
    }

    @Override
    public int getActionIcon() {
        return ICON_SEARCH;
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("query", new JSONObject().put("type", "string").put("description", "搜索关键词或问题"))
                        .put("limit", new JSONObject().put("type", "number").put("description", "返回结果数量，1-10，默认 5")))
                .put("required", new org.json.JSONArray().put("query"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        String query = input.optString("query").trim();
        if (query.length() == 0) {
            return error(context.getString(R.string.tool_web_search_query_empty));
        }
        int limit = input.optInt("limit", 5);
        try {
            List<SearchResultItem> results = webSearchService.search(config(), query, limit);
            if (results.isEmpty()) {
                return ok(context.getString(R.string.tool_web_search_no_results, query));
            }
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                SearchResultItem item = results.get(i);
                if (i > 0) {
                    content.append("\n\n");
                }
                content.append(i + 1).append(". ").append(item.title).append('\n')
                        .append("URL: ").append(item.url);
                if (item.publishedDate.length() > 0) {
                    content.append('\n').append("Date: ").append(item.publishedDate);
                }
                if (item.snippet.length() > 0) {
                    content.append('\n').append("Snippet: ").append(item.snippet);
                }
            }
            return ok(content.toString());
        } catch (Exception e) {
            return error(context.getString(R.string.tool_web_search_failed, e.getMessage()));
        }
    }

    private WebSearchConfig config() {
        return configRepository == null ? WebSearchConfig.defaultConfig() : configRepository.get();
    }
}
