package cn.lineai.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LineCodeAccessibilityService extends AccessibilityService {

    private static LineCodeAccessibilityService instance;

    private final Executor screenshotExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    public static LineCodeAccessibilityService getInstance() {
        return instance;
    }

    public boolean click(int x, int y) {
        return dispatchGesture(tapGesture(x, y, 1), null, null);
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        return dispatchGesture(swipeGesture(x1, y1, x2, y2, Math.max(100, durationMs)), null, null);
    }

    public boolean longPress(int x, int y, int durationMs) {
        return dispatchGesture(tapGesture(x, y, Math.max(400, durationMs)), null, null);
    }

    public String viewHierarchy() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "(no active window)";
        }
        StringBuilder builder = new StringBuilder();
        dumpNode(root, builder, 0);
        root.recycle();
        return builder.toString();
    }

    public boolean clickById(String resourceId) {
        if (resourceId == null || resourceId.length() == 0) {
            return false;
        }
        String fullId = resourceId.contains(":/") ? resourceId : (getPackageName() + ":id/" + resourceId);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(fullId);
        boolean clicked = clickFirstClickableNode(nodes);
        root.recycle();
        return clicked;
    }

    public boolean clickByText(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        boolean clicked = clickFirstClickableNode(nodes);
        root.recycle();
        return clicked;
    }

    public boolean clickByCoordinates(int x, int y) {
        return click(x, y);
    }

    private boolean clickFirstClickableNode(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return false;
        }
        boolean clicked = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                clicked = true;
            }
            node.recycle();
        }
        return clicked;
    }

    public Bitmap takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        try {
            Class<?> callbackClass = Class.forName("android.accessibilityservice.AccessibilityService$TakeScreenshotCallback");
            Method takeScreenshotMethod = AccessibilityService.class.getMethod("takeScreenshot", int.class, Executor.class, callbackClass);
            DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display display = displayManager != null ? displayManager.getDisplay(Display.DEFAULT_DISPLAY) : null;
            if (display == null) {
                return null;
            }
            final Bitmap[] result = new Bitmap[1];
            final CountDownLatch latch = new CountDownLatch(1);
            Object callback = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("onSuccess".equals(method.getName()) && args != null && args.length > 0) {
                                Object screenshotResult = args[0];
                                Method getBitmap = screenshotResult.getClass().getMethod("getBitmap");
                                result[0] = (Bitmap) getBitmap.invoke(screenshotResult);
                            }
                            latch.countDown();
                            return null;
                        }
                    }
            );
            takeScreenshotMethod.invoke(this, display.getDisplayId(), screenshotExecutor, callback);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return null;
            }
            return result[0];
        } catch (Exception e) {
            return null;
        }
    }

    private GestureDescription tapGesture(int x, int y, int durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(1, durationMs));
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    private GestureDescription swipeGesture(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, durationMs);
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    private void dumpNode(AccessibilityNodeInfo node, StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(node.getClassName() == null ? "View" : node.getClassName());
        if (node.getViewIdResourceName() != null) {
            builder.append(" id=").append(node.getViewIdResourceName());
        }
        if (node.getText() != null && node.getText().length() > 0) {
            builder.append(" text=").append(node.getText());
        }
        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
            builder.append(" desc=").append(node.getContentDescription());
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        builder.append(" bounds=").append(bounds.toShortString());
        builder.append("\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNode(child, builder, depth + 1);
                child.recycle();
            }
        }
    }
}
