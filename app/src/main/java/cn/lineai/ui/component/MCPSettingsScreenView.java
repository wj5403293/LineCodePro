package cn.lineai.ui.component;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.ui.theme.LineTheme;

public final class MCPSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onExecutionModeChanged(String mode);

        void onToolGroupChanged(String id, boolean enabled);

        void onWebSearchConfigChanged(WebSearchConfig config);

        void onOpenSshSettings();

        void onOpenTermuxIntegration();
    }

    private final Listener listener;
    private final McpSettingsState state;

    public MCPSettingsScreenView(Context context, McpSettingsState state, Listener listener) {
        super(context, "MCP 工具", listener::onBack, null);
        this.listener = listener;
        this.state = state == null ? new McpSettingsState(ToolSettingsRepository.EXECUTION_LOCAL, null) : state;
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        addExecutionTarget(content);
        if (ToolSettingsRepository.EXECUTION_SSH.equals(this.state.getExecutionMode())) {
            addSshConnection(content);
        }
        addWebSearch(content);
        addToolCards(content);
    }

    private void addExecutionTarget(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, "执行目标"));
        LinearLayout segment = new LinearLayout(context);
        segment.setOrientation(HORIZONTAL);
        segment.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(segment, 3, 3, 3, 3);
        addModeButton(segment, "本地工作区", ToolSettingsRepository.EXECUTION_LOCAL);
        addModeButton(segment, "SSH Shell", ToolSettingsRepository.EXECUTION_SSH);
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        segmentParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(segment, segmentParams);
        String executionDesc = ToolSettingsRepository.EXECUTION_LOCAL.equals(state.getExecutionMode())
                ? "本地工作区会在当前工作区路径内执行文件读写、文件搜索、Agent、HTTP 服务器和网页搜索。"
                : "SSH Shell 模式会禁用本地文件读写、文件搜索、Agent 和 HTTP 服务器；网页搜索仍由应用侧网络配置执行。";
        TextView desc = desc(context, executionDesc);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(desc, descParams);
        addCard(content, card);
    }

    private void addSshConnection(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, "SSH 连接"));
        TextView desc = desc(context, "SSH Shell 可以连接远程 Linux 服务器、桌面开发机、NAS，也可以连接手机本机 Termux。Termux 是一个单独选项，不等于只能连接手机。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        LinearLayout ssh = actionButton(context, "SSH 连接设置", IconButtonView.SERVER, true, v -> listener.onOpenSshSettings());
        LinearLayout termux = actionButton(context, "Termux 对接", IconButtonView.SMARTPHONE, false, v -> listener.onOpenTermuxIntegration());
        LinearLayout.LayoutParams sshParams = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f);
        sshParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        actions.addView(ssh, sshParams);
        actions.addView(termux, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(actions, actionsParams);
        addCard(content, card);
    }

    private void addWebSearch(LinearLayout content) {
        Context context = content.getContext();
        WebSearchConfig config = state.getWebSearchConfig();
        String[] selectedProvider = new String[] {config.getProvider()};
        boolean[] suppressChange = new boolean[] {false};

        LinearLayout card = card(context);
        card.addView(title(context, "网页搜索配置"));
        TextView desc = desc(context, "需要先打开“网页搜索”工具，并填写你自己的搜索 API、模型/搜索源和密钥。本地与 SSH 模式共用这组配置。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        FormTextFieldView baseUrlField = new FormTextFieldView(context, "Search API URL", config.getBaseUrl(), "https://api.example.com/search", null, false, false);
        FormTextFieldView apiKeyField = new FormTextFieldView(context, "API Key", config.getApiKey(), "搜索服务密钥", null, false, true);
        FormTextFieldView modelField = new FormTextFieldView(context, "模型 / 搜索源", config.getModel(), "如 basic、advanced、google，可留空", null, false, false);
        FormTextFieldView queryParamField = new FormTextFieldView(context, "查询参数名", config.getQueryParam(), "q", null, false, false);
        FormTextFieldView apiKeyHeaderField = new FormTextFieldView(context, "密钥 Header", config.getApiKeyHeader(), "Authorization，可留空", null, false, false);
        FormTextFieldView apiKeyParamField = new FormTextFieldView(context, "密钥 Query 参数", config.getApiKeyParam(), "api_key，可留空", null, false, false);

        GridLayout providers = new GridLayout(context);
        providers.setColumnCount(3);
        addProviderButton(providers, "Tavily", WebSearchConfig.PROVIDER_TAVILY, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "Brave Search", WebSearchConfig.PROVIDER_BRAVE, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "SerpAPI", WebSearchConfig.PROVIDER_SERPAPI, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "Bing Search", WebSearchConfig.PROVIDER_BING, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "自定义", WebSearchConfig.PROVIDER_CUSTOM, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        providerParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(providers, providerParams);

        TextWatcher watcher = configWatcher(selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        baseUrlField.getInput().addTextChangedListener(watcher);
        apiKeyField.getInput().addTextChangedListener(watcher);
        modelField.getInput().addTextChangedListener(watcher);
        queryParamField.getInput().addTextChangedListener(watcher);
        apiKeyHeaderField.getInput().addTextChangedListener(watcher);
        apiKeyParamField.getInput().addTextChangedListener(watcher);

        card.addView(baseUrlField, formParams(context));
        card.addView(apiKeyField, formParams(context));
        card.addView(modelField, formParams(context));
        if (WebSearchConfig.PROVIDER_CUSTOM.equals(config.getProvider())) {
            card.addView(queryParamField, formParams(context));
            card.addView(apiKeyHeaderField, formParams(context));
            card.addView(apiKeyParamField, formParams(context));
        }
        addCard(content, card);
    }

    private void addProviderButton(
            GridLayout providers,
            String label,
            String provider,
            String[] selectedProvider,
            boolean[] suppressChange,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        Context context = providers.getContext();
        boolean active = provider.equals(selectedProvider[0]);
        TextView button = LineTheme.text(context, label, LineTheme.FONT_XS, active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.roundedStroke(context, active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, active ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setClickable(true);
        button.setOnClickListener(v -> {
            WebSearchConfig defaults = WebSearchConfig.defaultConfig(provider);
            selectedProvider[0] = defaults.getProvider();
            suppressChange[0] = true;
            baseUrlField.getInput().setText(defaults.getBaseUrl());
            apiKeyField.getInput().setText("");
            modelField.getInput().setText(defaults.getModel());
            queryParamField.getInput().setText(defaults.getQueryParam());
            apiKeyHeaderField.getInput().setText(defaults.getApiKeyHeader());
            apiKeyParamField.getInput().setText(defaults.getApiKeyParam());
            suppressChange[0] = false;
            listener.onWebSearchConfigChanged(readWebSearchConfig(selectedProvider[0],
                    baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField));
            if (getParent() != null) {
                listener.onExecutionModeChanged(state.getExecutionMode());
            }
        });
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = LineTheme.dp(context, 34);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM), 0);
        providers.addView(button, params);
    }

    private TextWatcher configWatcher(
            String[] selectedProvider,
            boolean[] suppressChange,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressChange[0]) {
                    return;
                }
                listener.onWebSearchConfigChanged(readWebSearchConfig(selectedProvider[0],
                        baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField));
            }
        };
    }

    private WebSearchConfig readWebSearchConfig(
            String provider,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        return new WebSearchConfig(
                provider,
                baseUrlField.getInput().getText().toString(),
                apiKeyField.getInput().getText().toString(),
                modelField.getInput().getText().toString(),
                queryParamField.getInput().getText().toString(),
                apiKeyHeaderField.getInput().getText().toString(),
                apiKeyParamField.getInput().getText().toString()
        );
    }

    private void addToolCards(LinearLayout content) {
        for (McpToolConfig config : state.getConfigs()) {
            if (!shouldShow(config)) {
                continue;
            }
            addToolCard(content, iconFor(config.getId()), config);
        }
    }

    private boolean shouldShow(McpToolConfig config) {
        if (ToolSettingsRepository.EXECUTION_LOCAL.equals(state.getExecutionMode())) {
            return !"shell".equals(config.getId());
        }
        return "shell".equals(config.getId()) || "web_search".equals(config.getId());
    }

    private int iconFor(String id) {
        if ("http_server".equals(id)) return IconButtonView.SERVER;
        if ("shell".equals(id)) return IconButtonView.TERMINAL;
        if ("web_search".equals(id)) return IconButtonView.SEARCH;
        if ("agent".equals(id)) return IconButtonView.BRAIN;
        return IconButtonView.FILE_CODE;
    }

    private void addToolCard(LinearLayout content, int iconType, McpToolConfig config) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(config.isEnabled() ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        icon.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 18));
        header.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        labelParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        header.addView(labels, labelParams);
        labels.addView(LineTheme.textMedium(context, config.getName(), LineTheme.FONT_MD, LineTheme.TEXT));
        TextView descView = desc(context, config.getDescription());
        LinearLayout.LayoutParams descViewParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descViewParams.topMargin = LineTheme.dp(context, 2);
        labels.addView(descView, descViewParams);

        Switch toggle = new Switch(context);
        toggle.setChecked(config.isEnabled());
        tintSwitch(toggle, (button, checked) -> listener.onToolGroupChanged(config.getId(), checked));
        header.addView(toggle, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(divider, dividerParams);

        FlowLayoutView toolWrap = new FlowLayoutView(context);
        toolWrap.setSpacingDp(LineTheme.SM, LineTheme.SM);
        for (String tool : config.getTools()) {
            TextView badge = LineTheme.text(context, tool, LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            badge.setTypeface(Typeface.MONOSPACE);
            badge.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 4));
            LineTheme.padding(badge, LineTheme.SM, 2, LineTheme.SM, 2);
            toolWrap.addView(badge);
        }
        LinearLayout.LayoutParams toolWrapParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolWrapParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        card.addView(toolWrap, toolWrapParams);
        addCard(content, card);
    }

    private void addModeButton(LinearLayout segment, String label, String mode) {
        Context context = segment.getContext();
        boolean active = mode.equals(state.getExecutionMode());
        TextView button = LineTheme.text(context, label, LineTheme.FONT_SM, active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.rounded(context, active ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 8));
        button.setClickable(true);
        button.setOnClickListener(v -> listener.onExecutionModeChanged(mode));
        segment.addView(button, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    private void tintSwitch(Switch toggle, CompoundButton.OnCheckedChangeListener listener) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT, LineTheme.TEXT_TERTIARY}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {LineTheme.ACCENT_DIM, LineTheme.SURFACE_LIGHT}));
        toggle.setOnCheckedChangeListener(listener);
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(card, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return card;
    }

    private LinearLayout actionButton(Context context, String label, int iconType, boolean primary, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(LineTheme.roundedStroke(context, primary ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, primary ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setOnClickListener(listener);
        LineTheme.padding(button, LineTheme.SM, 0, LineTheme.SM, 0);
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        icon.setIconSizeDp(15, 15);
        icon.setClickable(false);
        button.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 15), LineTheme.dp(context, 15)));
        TextView text = LineTheme.text(context, label, LineTheme.FONT_XS, primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, 6);
        button.addView(text, textParams);
        return button;
    }

    private TextView title(Context context, String text) {
        return LineTheme.text(context, text, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
    }

    private TextView desc(Context context, String text) {
        TextView view = LineTheme.text(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setLineSpacing(LineTheme.dp(context, 3), 1f);
        return view;
    }

    private LinearLayout.LayoutParams formParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.MD);
        return params;
    }

    private void addCard(LinearLayout content, LinearLayout card) {
        Context context = content.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(card, params);
    }
}
