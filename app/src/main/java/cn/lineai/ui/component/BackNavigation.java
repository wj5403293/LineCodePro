package cn.lineai.ui.component;

/**
 * Stateless helper that resolves the chat view's back-press behavior in priority order.
 *
 * <p>The chat view stacks five overlays that may swallow a back press before it reaches
 * the underlying activity:</p>
 * <ol>
 *     <li>the full-screen {@code screenHost} (settings, about, model management, ...)</li>
 *     <li>the directory picker sheet</li>
 *     <li>the attachment picker sheet</li>
 *     <li>the generic bottom sheet</li>
 *     <li>the navigation drawer</li>
 * </ol>
 *
 * <p>{@link #handle(BackTarget)} consults each layer top-to-bottom and asks the first
 * visible one to close itself; the call returns {@code true} iff a layer consumed the
 * event.</p>
 */
public final class BackNavigation {

    /**
     * Read/write view of the chat workspace needed by {@link #handle(BackTarget)}.
     *
     * <p>Implemented by {@code MainChatView} — kept as an interface so the helper
     * stays decoupled from the view's own field set.</p>
     */
    public interface BackTarget {
        boolean isScreenVisible();

        boolean isDirectoryPickerVisible();

        boolean isAttachmentPickerVisible();

        boolean isBottomSheetVisible();

        boolean isDrawerVisible();

        void backFromScreen();

        void closeDirectoryPicker();

        void closeAttachmentPicker();

        void closeBottomSheet();

        void closeDrawer();
    }

    private BackNavigation() {
    }

    /**
     * Resolve a back press against the chat workspace.
     *
     * @return {@code true} if an overlay consumed the press; {@code false} if the
     *         underlying activity should handle it (typically to finish).
     */
    public static boolean handle(BackTarget target) {
        if (target == null) {
            return false;
        }
        if (target.isScreenVisible()) {
            target.backFromScreen();
            return true;
        }
        if (target.isDirectoryPickerVisible()) {
            target.closeDirectoryPicker();
            return true;
        }
        if (target.isAttachmentPickerVisible()) {
            target.closeAttachmentPicker();
            return true;
        }
        if (target.isBottomSheetVisible()) {
            target.closeBottomSheet();
            return true;
        }
        if (target.isDrawerVisible()) {
            target.closeDrawer();
            return true;
        }
        return false;
    }
}
