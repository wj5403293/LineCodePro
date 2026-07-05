package cn.lineai.ui.component.toolcall;

import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;

public interface ToolCallCardView {
    void bind(ToolCall call, ToolResult result);
    void setToolReviewListener(ToolReviewListener listener);
    void setProjectPath(String projectPath);
}
