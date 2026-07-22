package cn.lineai.mvp;

/**
 * Decides whether Activity visibility changes should terminate an active generation.
 * <p>
 * Historical bug: {@code MainActivity.onStop}/{@code onStart} always called
 * {@code resetGenerationState()}, which cancelled the LLM stream, auto-rejected
 * pending tool writes, and tore down keep-alive — defeating every keep-alive option.
 * Background visibility is not an explicit user stop; only finishing/destroying the
 * Activity (or the user tapping Stop) should cancel.
 */
public final class ActivityGenerationLifecyclePolicy {
    private ActivityGenerationLifecyclePolicy() {
    }

    /**
     * @param isFinishing true when the Activity is going away permanently
     *                    ({@link android.app.Activity#isFinishing()}).
     * @return whether the presenter should fully stop generation (cancel token,
     * reject pending reviews, stop keep-alive).
     */
    public static boolean shouldStopGenerationOnStop(boolean isFinishing) {
        return isFinishing;
    }

    /**
     * Returning to foreground must never cancel an in-flight generation; orphan
     * streaming flags from process death are cleaned when the conversation is loaded
     * via {@link ConversationResumeSanitizer}.
     */
    public static boolean shouldStopGenerationOnStart() {
        return false;
    }
}
