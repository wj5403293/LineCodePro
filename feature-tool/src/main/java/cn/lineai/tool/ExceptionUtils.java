package cn.lineai.tool;

public final class ExceptionUtils {
    private ExceptionUtils() {}

    public static String describeException(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message.trim();
        }
        String name = error.getClass().getSimpleName();
        return name.length() == 0 ? "未知错误" : name;
    }

    public static void restoreInterrupt(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
