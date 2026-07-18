package cn.lineai.mvp;

public interface ScreenView {
    void showScreen(String screenId);

    default void showScreen(String screenId, boolean forward) {
        showScreen(screenId);
    }

    default void showScreen(String screenId, boolean forward, boolean animate) {
        showScreen(screenId, forward);
    }

    void showChatScreen();

    void openExternalUrl(String url);

    void recreateForTheme(String screenId);

    default void evictScreen(String screenId) {
        invalidateScreen(screenId);
    }

    void invalidateScreen(String screenId);
}
