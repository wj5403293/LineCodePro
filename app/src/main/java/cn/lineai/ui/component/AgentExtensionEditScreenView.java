package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.McpToolSummary;
import cn.lineai.tool.BaseTool;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class AgentExtensionEditScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        ExtensionAgentConfig onGenerateDraft(String description) throws Exception;

        void onSave(ExtensionAgentConfig config);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final ExtensionAgentConfig editingAgent;
    private final List<BaseTool> availableTools;
    private final List<McpToolConfig> builtInMcps;
    private final List<ExtensionMcpConfig> customMcps;
    private final HashSet<String> selectedTools = new HashSet<>();
    private final HashSet<String> selectedMcps = new HashSet<>();

    private FormTextFieldView nameField;
    private FormTextFieldView slugField;
    private FormTextFieldView promptField;
    private FormTextFieldView triggerField;
    private SettingsSectionView toolsSection;
    private SettingsSectionView mcpSection;

    public AgentExtensionEditScreenView(
            Context context,
            ExtensionAgentConfig editingAgent,
            List<BaseTool> availableTools,
            List<McpToolConfig> builtInMcps,
            List<ExtensionMcpConfig> customMcps,
            Listener listener
    ) {
        super(context, editingAgent == null ? "添加 Agent" : "修改 Agent", listener::onBack, saveAction(context));
        this.listener = listener;
        this.editingAgent = editingAgent;
        this.availableTools = availableTools == null ? new ArrayList<>() : availableTools;
        this.builtInMcps = builtInMcps == null ? new ArrayList<>() : builtInMcps;
        this.customMcps = customMcps == null ? new ArrayList<>() : customMcps;
        LinearLayout content = getContent();

        SettingsSectionView quick = new SettingsSectionView(context, "快速创建");
        quick.addRow(new ActionRowView(context, IconButtonView.SPARKLES, "让 AI 写",
                "直接描述所需 Agent，自动填写名称、提示词、触发条件和权限。",
                false, true, this::showAiWriterDialog), false);
        content.addView(quick, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        nameField = new FormTextFieldView(context, "名字", "", "例如：测试修复 Agent", null, false, false);
        slugField = new FormTextFieldView(context, "英文标识", "", "test-fixer",
                "用于触发和识别自定义 Agent，保存时会规范成小写英文标识。", false, false);
        addForm(content, "基础信息", nameField, slugField);

        promptField = new FormTextFieldView(context, "提示词", "", "描述这个 Agent 的角色、边界、输出格式和验收方式", null, true, false);
        triggerField = new FormTextFieldView(context, "触发条件", "", "例如：用户要求修复测试、分析性能、重构组件时触发", null, true, false);
        addForm(content, "行为定义", promptField, triggerField);

        toolsSection = new SettingsSectionView(context, "可使用的工具");
        content.addView(toolsSection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mcpSection = new SettingsSectionView(context, "可使用的 MCP");
        content.addView(mcpSection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        applyConfig(editingAgent);
        renderToolRows();
        renderMcpRows();
        getRightAction().setOnClickListener(v -> save());
    }

    private void applyConfig(ExtensionAgentConfig config) {
        selectedTools.clear();
        selectedMcps.clear();
        if (config == null) {
            if (hasTool("file_read")) {
                selectedTools.add("file_read");
            }
            if (hasTool("glob")) {
                selectedTools.add("glob");
            }
            return;
        }
        nameField.getInput().setText(config.getName());
        slugField.getInput().setText(config.getSlug());
        promptField.getInput().setText(config.getPrompt());
        triggerField.getInput().setText(config.getTrigger());
        selectedTools.addAll(config.getToolNames());
        selectedMcps.addAll(config.getMcpIds());
    }

    private boolean hasTool(String name) {
        for (BaseTool tool : availableTools) {
            if (tool.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void renderToolRows() {
        toolsSection.removeAllRows();
        toolsSection.setTitle("可使用的工具 · 已选 " + selectedTools.size());
        if (availableTools.isEmpty()) {
            toolsSection.addRow(empty("暂无可选工具。"), false);
            return;
        }
        for (int i = 0; i < availableTools.size(); i++) {
            BaseTool tool = availableTools.get(i);
            boolean active = selectedTools.contains(tool.getName());
            toolsSection.addRow(new OptionRowView(getContext(), IconButtonView.SETTINGS, tool.getName(),
                    tool.getCategory().name().toLowerCase(Locale.ROOT) + " · " + tool.getDescription(),
                    active, () -> {
                        toggle(selectedTools, tool.getName());
                        renderToolRows();
                    }), i < availableTools.size() - 1);
        }
    }

    private void renderMcpRows() {
        mcpSection.removeAllRows();
        mcpSection.setTitle("可使用的 MCP · 已选 " + selectedMcps.size());
        ArrayList<McpOption> options = mcpOptions();
        if (options.isEmpty()) {
            mcpSection.addRow(empty("暂无 MCP 可选。"), false);
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            McpOption option = options.get(i);
            boolean active = selectedMcps.contains(option.id);
            mcpSection.addRow(new OptionRowView(getContext(), IconButtonView.MCP, option.label, option.desc,
                    active, () -> {
                        toggle(selectedMcps, option.id);
                        renderMcpRows();
                    }), i < options.size() - 1);
        }
    }

    private ArrayList<McpOption> mcpOptions() {
        ArrayList<McpOption> options = new ArrayList<>();
        for (McpToolConfig config : builtInMcps) {
            options.add(new McpOption("builtin:" + config.getId(), config.getName(), join(config.getTools())));
        }
        for (ExtensionMcpConfig mcp : customMcps) {
            if (!mcp.isEnabled()) {
                continue;
            }
            int enabled = 0;
            for (McpToolSummary tool : mcp.getTools()) {
                if (tool.isEnabled()) {
                    enabled++;
                }
            }
            options.add(new McpOption("custom:" + mcp.getId(), mcp.getName(),
                    enabled + "/" + mcp.getTools().size() + " tools · " + mcp.getUrl()));
        }
        return options;
    }

    private void showAiWriterDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        panel.addView(LineTheme.textMedium(getContext(), "让 AI 写 Agent", LineTheme.FONT_LG, LineTheme.TEXT));
        TextView desc = LineTheme.text(getContext(), "描述目标后会自动填入当前表单。", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        panel.addView(desc, top());
        FormTextFieldView input = new FormTextFieldView(getContext(), "需求描述", "",
                "例如：专门分析 Android 原生 View 性能问题，先读相关 View 和 Presenter，再给修复方案。",
                null, true, false);
        panel.addView(input, top());
        panel.addView(actionButton("生成并填写", () -> generateDraft(dialog, input)), top());
        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(insetDialogWidth(), LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private int insetDialogWidth() {
        int width = getResources().getDisplayMetrics().widthPixels - LineTheme.dp(getContext(), 32);
        return Math.max(LineTheme.dp(getContext(), 280), width);
    }

    private void generateDraft(Dialog dialog, FormTextFieldView input) {
        String description = value(input);
        if (description.length() == 0) {
            Toast.makeText(getContext(), "请先描述 Agent 需求", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "正在生成 Agent...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ExtensionAgentConfig draft = listener.onGenerateDraft(description);
                mainHandler.post(() -> {
                    applyConfig(draft);
                    renderToolRows();
                    renderMcpRows();
                    dialog.dismiss();
                    Toast.makeText(getContext(), "已生成并填写", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "linecode-agent-ai-writer").start();
    }

    private void save() {
        String name = value(nameField);
        String slug = normalizeSlug(value(slugField).length() == 0 ? name : value(slugField));
        String prompt = value(promptField);
        String trigger = value(triggerField);
        if (name.length() == 0 || slug.length() == 0 || prompt.length() == 0) {
            Toast.makeText(getContext(), "请填写名字、英文标识和提示词", Toast.LENGTH_SHORT).show();
            return;
        }
        listener.onSave(new ExtensionAgentConfig(
                editingAgent == null ? "" : editingAgent.getId(),
                editingAgent == null || editingAgent.isEnabled(),
                name,
                slug,
                prompt,
                trigger,
                new ArrayList<>(selectedTools),
                new ArrayList<>(selectedMcps),
                editingAgent == null ? 0 : editingAgent.getCreatedAt(),
                editingAgent == null ? 0 : editingAgent.getUpdatedAt()
        ));
    }

    private TextView actionButton(String title, Runnable action) {
        TextView view = LineTheme.textMedium(getContext(), title, LineTheme.FONT_MD, LineTheme.ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 8));
        LineTheme.padding(view, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView empty(String text) {
        TextView view = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        LineTheme.padding(view, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return view;
    }

    private void toggle(HashSet<String> set, String value) {
        if (set.contains(value)) {
            set.remove(value);
        } else {
            set.add(value);
        }
    }

    private String value(FormTextFieldView field) {
        return field == null ? "" : field.getInput().getText().toString().trim();
    }

    private static TextView saveAction(Context context) {
        TextView save = LineTheme.textMedium(context, "保存", LineTheme.FONT_MD, LineTheme.ACCENT);
        save.setGravity(Gravity.CENTER);
        return save;
    }

    private void addForm(LinearLayout content, String title, android.view.View first, android.view.View second) {
        Context context = content.getContext();
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(group, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        group.addView(first, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        secondParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        group.addView(second, secondParams);

        SectionHeaderView header = new SectionHeaderView(context, title);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        headerParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(header, headerParams);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        groupParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        groupParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        content.addView(group, groupParams);
    }

    private LinearLayout.LayoutParams top() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        return params;
    }

    private String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(value);
            }
        }
        return builder.length() == 0 ? "无工具" : builder.toString();
    }

    private String normalizeSlug(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < raw.length() && builder.length() < 48; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
                lastDash = false;
            } else if (!lastDash && builder.length() > 0) {
                builder.append('-');
                lastDash = true;
            }
        }
        String clean = builder.toString();
        while (clean.endsWith("-") || clean.endsWith("_")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.length() == 0) {
            return "";
        }
        char first = clean.charAt(0);
        return first >= 'a' && first <= 'z' ? clean : "agent-" + clean;
    }

    private static final class McpOption {
        final String id;
        final String label;
        final String desc;

        McpOption(String id, String label, String desc) {
            this.id = id == null ? "" : id;
            this.label = label == null ? "" : label;
            this.desc = desc == null ? "" : desc;
        }
    }
}
