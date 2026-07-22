package cn.lineai.tool;

import android.graphics.Bitmap;

/**
 * Abstraction over the accessibility service used by phone control tools.
 * Implemented by {@code LineCodeAccessibilityService} in the :app module;
 * consumed by phone tools in :feature-tool so they do not depend on :app.
 */
public interface PhoneControlService {
    boolean click(int x, int y);

    boolean swipe(int x1, int y1, int x2, int y2, int durationMs);

    boolean longPress(int x, int y, int durationMs);

    String viewHierarchy();

    boolean clickById(String resourceId);

    boolean clickByText(String text);

    boolean clickByCoordinates(int x, int y);

    boolean performPhoneAction(String action);

    Bitmap takeScreenshot();
}
