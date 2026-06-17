package cn.lineai.ui.component;

import android.content.Context;
import android.view.View;
import cn.lineai.mvp.MainUiController;
import cn.lineai.ui.MainChatView;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes a screen id to the {@link ScreenFactory} that builds it.
 *
 * <p>Lookup first tries an exact match against the registered screen ids, then falls
 * back to asking each factory via {@link ScreenFactory#matches(String)} so that
 * prefix-style factories (for example {@code "extension:"}) can claim dynamic ids.
 */
public final class ScreenRegistry {
    private final Map<String, ScreenFactory> factories = new LinkedHashMap<>();

    public void register(String id, ScreenFactory factory) {
        if (id == null || factory == null) {
            return;
        }
        factories.put(id, factory);
    }

    public void register(ScreenFactory factory) {
        register(factory.screenId(), factory);
    }

    /**
     * Creates the screen for the requested id. Returns null when no factory matches,
     * letting the caller fall back to its own default handling.
     */
    public View createScreen(String id, MainChatView view, MainUiController controller, Context context) {
        if (id != null) {
            ScreenFactory exact = factories.get(id);
            if (exact != null) {
                return exact.createScreen(view, controller, context);
            }
        }
        for (ScreenFactory factory : factories.values()) {
            if (factory.matches(id)) {
                return factory.createScreen(view, controller, context);
            }
        }
        return null;
    }
}
