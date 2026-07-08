package cn.lineai.security;

import java.net.URI;
import java.util.Locale;

public final class UrlPolicy {
    private UrlPolicy() {
    }

    /**
     * 当为 true 时,放宽 HTTP 限制:任意 http:// 地址(不限于 localhost 白名单)都将被放行,
     * 由用户在设置中"无视 HTTP 限制"开关控制。默认关闭。
     */
    private static volatile boolean relaxedHttpEnabled = false;

    public static void setRelaxedHttpEnabled(boolean enabled) {
        relaxedHttpEnabled = enabled;
    }

    public static boolean isRelaxedHttpEnabled() {
        return relaxedHttpEnabled;
    }

    public static String normalizeHttpOrHttpsUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.length() == 0) {
            return "";
        }
        URI uri = parse(value);
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        String scheme = lower(uri.getScheme());
        if (relaxedHttpEnabled) {
            return "https".equals(scheme) || "http".equals(scheme) ? value : "";
        }
        return "https".equals(scheme) || "http".equals(scheme) ? value : "";
    }

    public static String normalizeHttpOrLocalCleartextUrl(String rawUrl) {
        String value = normalizeHttpOrHttpsUrl(rawUrl);
        if (value.length() == 0) {
            return "";
        }
        URI uri = parse(value);
        if (uri == null) {
            return "";
        }
        if ("https".equals(lower(uri.getScheme()))) {
            return value;
        }
        return relaxedHttpEnabled || isAllowedCleartextHttpHost(uri.getHost()) ? value : "";
    }

    public static String requireHttpOrLocalCleartextUrl(String rawUrl, String label) {
        String normalized = normalizeHttpOrHttpsUrl(rawUrl);
        String name = label == null || label.trim().length() == 0 ? "URL" : label.trim();
        if (normalized.length() == 0) {
            throw new IllegalArgumentException(name + " 必须以 http:// 或 https:// 开头。");
        }
        URI uri = parse(normalized);
        if (uri != null && "http".equals(lower(uri.getScheme())) && !relaxedHttpEnabled && !isAllowedCleartextHttpHost(uri.getHost())) {
            throw new IllegalArgumentException(name + " 使用 HTTP 明文时仅允许 localhost、127.0.0.1 或 10.0.2.2。");
        }
        return normalized;
    }

    public static boolean isAllowedCleartextHttpUrl(String rawUrl) {
        String value = normalizeHttpOrHttpsUrl(rawUrl);
        if (value.length() == 0) {
            return false;
        }
        URI uri = parse(value);
        return uri != null
                && "http".equals(lower(uri.getScheme()))
                && (relaxedHttpEnabled || isAllowedCleartextHttpHost(uri.getHost()));
    }

    private static boolean isAllowedCleartextHttpHost(String host) {
        if (host == null || host.length() == 0) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || "127.0.0.1".equals(h) || "10.0.2.2".equals(h) || "::1".equals(h)) {
            return true;
        }
        return isPrivateNetwork(h);
    }

    private static boolean isPrivateNetwork(String host) {
        return host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
