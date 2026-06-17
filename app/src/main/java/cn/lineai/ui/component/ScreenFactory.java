package cn.lineai.ui.component;

import android.content.Context;
import android.view.View;
import cn.lineai.mvp.MainUiController;
import cn.lineai.ui.MainChatView;

/**
 * Builds a single settings/utility screen hosted by {@link MainChatView}.
 *
 * <p>Each factory owns one screen identifier returned by {@link #screenId()}. For
 * prefix-style screens (for example {@code "extension:agent-1"}), override
 * {@link #matches(String)} so the registry can route dynamic ids to the right factory.
 */
public interface ScreenFactory {
    View createScreen(MainChatView view, MainUiController controller, Context context);

    String screenId();

    /**
     * Returns true when this factory should handle the requested screen id. The default
     * implementation matches the exact {@link #screenId()}; prefix factories override
     * this to accept any id that starts with their configured prefix.
     */
    default boolean matches(String id) {
        return screenId().equals(id);
    }
}
