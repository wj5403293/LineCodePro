package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import org.json.JSONObject;

public final class ToolCallReadView extends BaseToolCallView implements ToolCallCardView {
    private String projectPath = "";

    public ToolCallReadView(Context context) {
        super(context);
    }

    @Override
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    @Override
    public void setToolReviewListener(ToolReviewListener listener) {
        // Read view does not use tool review
    }

    @Override
    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        String name = toolCall == null ? "" : toolCall.getName();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        String label = ToolCallUtils.displayInputLabel(getContext(), name, input, projectPath);
        // 简化进度圈逻辑：直接根据结果决定最终状态
        TerminalStatus status = computeTerminalStatus(result);
        boolean running = status == TerminalStatus.RUNNING;
        boolean error = status == TerminalStatus.FAILED;
        boolean unknown = status == TerminalStatus.UNKNOWN;
        boolean complete = !running;
        String actionLabel = actionLabel(name);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(getContext(), 42));
        LineTheme.padding(header, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        int headerIconColor = error ? LineTheme.DANGER
                : running ? LineTheme.ACCENT
                : unknown ? LineTheme.TEXT_TERTIARY
                : LineTheme.TEXT_SECONDARY;
        IconButtonView icon = new IconButtonView(getContext(), iconFor(name));
        icon.setIconColor(headerIconColor);
        icon.setIconSizeDp(26, 13);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 8,
                error ? LineTheme.DANGER_MUTED_2 : LineTheme.CODE_BORDER));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 26), LineTheme.dp(getContext(), 26)));

        LinearLayout textBlock = new LinearLayout(getContext());
        textBlock.setOrientation(VERTICAL);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        textParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(textBlock, textParams);

        TextView action = LineTheme.text(getContext(), actionLabel, 10, error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        textBlock.addView(action, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView path = LineTheme.text(getContext(), label, LineTheme.FONT_SM, error ? LineTheme.DANGER : LineTheme.TEXT, Typeface.NORMAL);
        path.setTypeface(Typeface.MONOSPACE);
        path.setSingleLine(true);
        path.setHorizontallyScrolling(true);
        HorizontalScrollView pathScroll = horizontalPathScroll(path);
        textBlock.addView(pathScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View statusViewInstance = statusView(running);
        if (complete && statusViewInstance instanceof IconButtonView) {
            IconButtonView statusIcon = (IconButtonView) statusViewInstance;
            if (error) {
                statusIcon.setIconType(IconButtonView.CLOSE);
                statusIcon.setIconColor(LineTheme.DANGER);
            } else if (unknown) {
                // 未知情况：使用时钟图标表示等待结果，颜色用 TEXT_TERTIARY
                statusIcon.setIconType(IconButtonView.CLOCK_3);
                statusIcon.setIconColor(LineTheme.TEXT_TERTIARY);
            }
        }
        header.addView(statusViewInstance, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (running && result != null && result.getContent().length() > 0) {
            addMessageRow(this, IconButtonView.LOADER, result.getContent(), LineTheme.TEXT_SECONDARY);
        } else if (error && result.getContent().length() > 0) {
            addMessageRow(this, IconButtonView.CLOSE, result.getContent(), LineTheme.DANGER);
        }
    }

    private String actionLabel(String name) {
        ToolDisplayCategory category = ToolCallUtils.getDisplayCategory(name);
        if (category == ToolDisplayCategory.IMAGE_GENERATION) return getContext().getString(R.string.tool_call_image_generation);
        if (cn.lineai.tool.builtin.ImageUnderstandingTool.NAME.equals(name)) return getContext().getString(R.string.tool_call_image_understanding);
        if (category == ToolDisplayCategory.PHONE_CONTROL) return ToolCallInputParser.phoneControlActionName(getContext(), name);
        if (cn.lineai.tool.builtin.WebSearchTool.NAME.equals(name)) return getContext().getString(R.string.tool_call_action_search);
        if (cn.lineai.tool.builtin.WebFetchTool.NAME.equals(name)) return getContext().getString(R.string.tool_call_action_fetch);
        if (cn.lineai.tool.builtin.GlobTool.NAME.equals(name)) return getContext().getString(R.string.tool_call_action_match);
        if (cn.lineai.tool.builtin.ListDirectoryTool.NAME.equals(name)) return getContext().getString(R.string.tool_call_action_list_dir);
        return getContext().getString(R.string.tool_call_action_read);
    }

    private int iconFor(String name) {
        ToolDisplayCategory category = ToolCallUtils.getDisplayCategory(name);
        if (cn.lineai.tool.builtin.GlobTool.NAME.equals(name) || cn.lineai.tool.builtin.WebSearchTool.NAME.equals(name)) {
            return IconButtonView.SEARCH;
        }
        if (cn.lineai.tool.builtin.WebFetchTool.NAME.equals(name)) {
            return IconButtonView.GLOBE;
        }
        if (cn.lineai.tool.builtin.ListDirectoryTool.NAME.equals(name)) {
            return IconButtonView.FOLDER_OPEN;
        }
        if (cn.lineai.tool.builtin.ImageUnderstandingTool.NAME.equals(name)) {
            return IconButtonView.PAINTBRUSH;
        }
        if (category == ToolDisplayCategory.IMAGE_GENERATION) {
            return IconButtonView.SPARKLES;
        }
        if (category == ToolDisplayCategory.PHONE_CONTROL) {
            return IconButtonView.SMARTPHONE;
        }
        return IconButtonView.EXPAND;
    }
}
