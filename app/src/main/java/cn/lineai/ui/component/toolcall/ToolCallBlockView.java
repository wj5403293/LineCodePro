package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ToolResult;

public final class ToolCallBlockView extends LinearLayout {
    private static final ToolCallViewFactoryRegistry REGISTRY = createRegistry();

    private String lastSignature = "";
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;

    public ToolCallBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        String signature = signature(toolCall, result);
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        String name = toolCall == null ? "" : toolCall.getName();
        ToolDisplayCategory category = ToolCallUtils.getDisplayCategory(name);
        ToolCallCardView childView = REGISTRY.createView(getContext(), category);
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

    private static ToolCallViewFactoryRegistry createRegistry() {
        ToolCallViewFactoryRegistry registry = new ToolCallViewFactoryRegistry();
        registry.register(new ShellToolCallViewFactory());
        registry.register(new TodoToolCallViewFactory());
        registry.register(new AgentToolCallViewFactory());
        registry.register(new AgentPipelineToolCallViewFactory());
        registry.register(new ReadToolCallViewFactory());
        registry.register(new ImageGenerationToolCallViewFactory());
        registry.register(new PhoneControlToolCallViewFactory());
        registry.register(new WriteToolCallViewFactory());
        registry.register(new DeleteToolCallViewFactory());
        registry.register(new HttpToolCallViewFactory());
        registry.register(new GenericToolCallViewFactory());
        return registry;
    }
}
