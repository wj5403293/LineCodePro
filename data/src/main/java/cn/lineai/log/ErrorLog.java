package cn.lineai.log;

import android.content.Context;

public final class ErrorLog {
    private static volatile ErrorLogRepository repository;
    private static volatile Thread.UncaughtExceptionHandler previousHandler;

    private ErrorLog() {
    }

    public static void init(Context context) {
        if (context != null) {
            repository = new ErrorLogRepository(context.getFilesDir().getAbsolutePath());
            installUncaughtExceptionHandler();
        }
    }

    public static void record(String type, String summary, Throwable throwable, String details) {
        ErrorLogRepository current = repository;
        if (current == null) {
            return;
        }
        current.record(type, summary, throwable, details);
    }

    private static synchronized void installUncaughtExceptionHandler() {
        if (previousHandler != null) {
            return;
        }
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            record("uncaught", throwable == null ? "Uncaught exception" : throwable.getMessage(), throwable,
                    "Thread: " + (thread == null ? "" : thread.getName()));
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
