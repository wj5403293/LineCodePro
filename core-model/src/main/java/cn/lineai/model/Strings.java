package cn.lineai.model;

public final class Strings {
    private Strings() {}

    /** 空值安全处理，等价于 value == null ? "" : value */
    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 判断字符串是否为null或空 */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
