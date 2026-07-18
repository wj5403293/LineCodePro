package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;
import cn.lineai.tool.builtin.AgentPipelineTool;
import cn.lineai.tool.builtin.AgentTool;

public final class ToolCallBlockView extends LinearLayout {
    private final ToolCallViewFactoryRegistry registry;
    private String lastSignature = "";
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;

    public ToolCallBlockView(Context context) {
        this(context, ToolCallViewFactoryRegistry.getDefault());
    }

    public ToolCallBlockView(Context context, ToolCallViewFactoryRegistry registry) {
        super(context);
        this.registry = registry;
        setOrientation(VERTICAL);
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        String signature = signature(toolCall, result);
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        String name = toolCall == null ? "" : toolCall.getName();
        ToolDisplayCategory category = resolveDisplayCategory(name);
        ToolCallCardView childView = registry.createView(getContext(), category);
        if (childView != null) {
            removeAllViews();
            childView.setToolReviewListener(toolReviewListener);
            childView.setProjectPath(projectPath);
            addView((View) childView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            childView.bind(toolCall, result);
        }
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
        if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallCardView) {
            ((ToolCallCardView) getChildAt(0)).setToolReviewListener(listener);
        }
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
        if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallCardView) {
            ((ToolCallCardView) getChildAt(0)).setProjectPath(this.projectPath);
        }
    }

    private ToolDisplayCategory resolveDisplayCategory(String name) {
        if (AgentTool.NAME.equals(name)) {
            return ToolDisplayCategory.AGENT;
        }
        if (AgentPipelineTool.NAME.equals(name)) {
            return ToolDisplayCategory.AGENT_PIPELINE;
        }
        return ToolCallUtils.getDisplayCategory(name);
    }

    private String signature(ToolCall toolCall, ToolResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(projectPath).append('|');
        if (toolCall != null) {
            builder.append(toolCall.getId()).append('|')
                    .append(toolCall.getName()).append('|')
                    .append(toolCall.getArguments());
        }
        builder.append('|');
        if (result != null) {
            builder.append(result.getToolCallId()).append('|')
                    .append(result.getToolName()).append('|')
                    .append(result.getContent()).append('|')
                    .append(result.isError()).append('|')
                    .append(result.getDiffId()).append('|')
                    .append(result.getReviewState()).append('|')
                    .append(result.getReviewMessage());
        }
        return builder.toString();
    }
}
