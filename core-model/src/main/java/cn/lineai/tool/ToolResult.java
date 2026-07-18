package cn.lineai.tool;

import cn.lineai.model.Strings;

public final class ToolResult {
    private final String toolCallId;
    private final String toolName;
    private final String content;
    private final boolean error;
    private final String diffId;
    private final String reviewState;
    private final String reviewMessage;

    @Deprecated
    public ToolResult(String toolCallId, String toolName, String content, boolean error) {
        this(toolCallId, toolName, content, error, "", "", "");
    }

    @Deprecated
    public ToolResult(
            String toolCallId,
            String toolName,
            String content,
            boolean error,
            String diffId,
            String reviewState,
            String reviewMessage
    ) {
        this.toolCallId = Strings.nullToEmpty(toolCallId);
        this.toolName = Strings.nullToEmpty(toolName);
        this.content = Strings.nullToEmpty(content);
        this.error = error;
        this.diffId = Strings.nullToEmpty(diffId);
        this.reviewState = Strings.nullToEmpty(reviewState);
        this.reviewMessage = Strings.nullToEmpty(reviewMessage);
    }

    public static ToolResult success(String output) {
        return new ToolResult("", "", output, false, "", "", "");
    }

    public static ToolResult error(String error) {
        return new ToolResult("", "", error, true, "", "", "");
    }

    public static ToolResult withReview(String output, String toolCallId, String toolName,
                                         String diffId, String reviewState, String reviewMessage) {
        return new ToolResult(toolCallId, toolName, output, false, diffId, reviewState, reviewMessage);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getContent() {
        return content;
    }

    public boolean isError() {
        return error;
    }

    public String getDiffId() {
        return diffId;
    }

    public String getReviewState() {
        return reviewState;
    }

    public String getReviewMessage() {
        return reviewMessage;
    }

    public ToolResult withCall(String nextToolCallId, String nextToolName) {
        return new ToolResult(nextToolCallId, nextToolName, content, error, diffId, reviewState, reviewMessage);
    }

    public ToolResult withDiffId(String nextDiffId) {
        return new ToolResult(toolCallId, toolName, content, error, nextDiffId, reviewState, reviewMessage);
    }

    public ToolResult withReview(String nextReviewState, String nextReviewMessage) {
        return new ToolResult(toolCallId, toolName, content, error, diffId, nextReviewState, nextReviewMessage);
    }
}
