package cn.lineai.mvp;

import android.os.Handler;
import android.os.Looper;

public final class MainThreadDispatcher {
    private final Handler handler;
    private final boolean dispatchInline;

    public MainThreadDispatcher() {
        this(new Handler(Looper.getMainLooper()));
    }

    MainThreadDispatcher(Handler handler) {
        this(handler, false);
    }

    MainThreadDispatcher(Handler handler, boolean dispatchInline) {
        this.handler = handler;
        this.dispatchInline = dispatchInline;
    }

    public void post(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (dispatchInline) {
            runnable.run();
            return;
        }
        handler.post(runnable);
    }

    public void postDelayed(Runnable runnable, long delayMillis) {
        if (runnable == null) {
            return;
        }
        if (dispatchInline) {
            runnable.run();
            return;
        }
        handler.postDelayed(runnable, Math.max(0L, delayMillis));
    }

    public void dispatch(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (dispatchInline) {
            runnable.run();
            return;
        }
        if (isMainThread()) {
            runnable.run();
        } else {
            post(runnable);
        }
    }

    public boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
