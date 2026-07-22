package cn.lineai.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public class UrlPolicy {
    private static final UrlPolicy DEFAULT = new UrlPolicy();

    private volatile boolean relaxedHttpEnabled = false;
    private final Set<String> cleartextHosts;
    private final ArrayList<Predicate<String>> privateNetworkPredicates;

    public UrlPolicy() {
        cleartextHosts = new HashSet<>();
        privateNetworkPredicates = new ArrayList<>();
        registerDefaultCleartextHosts();
        registerDefaultPrivateNetworkPredicates();
    }

    private void registerDefaultCleartextHosts() {
        addCleartextHost("localhost");
        addCleartextHost("127.0.0.1");
        addCleartextHost("10.0.2.2");
        addCleartextHost("::1");
    }

    private void registerDefaultPrivateNetworkPredicates() {
        addPrivateNetworkPredicate(host -> host.startsWith("192.168."));
        addPrivateNetworkPredicate(host -> host.startsWith("10."));
        addPrivateNetworkPredicate(host -> host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*"));
    }

    public void addCleartextHost(String host) {
        if (host != null && host.length() > 0) {
            cleartextHosts.add(host.toLowerCase(Locale.ROOT));
        }
    }

    public void addPrivateNetworkPredicate(Predicate<String> predicate) {
        if (predicate != null) {
            privateNetworkPredicates.add(predicate);
        }
    }

    String checkNormalizeHttpOrHttpsUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.length() == 0) {
            return "";
        }
        URI uri = parse(value);
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        String scheme = lower(uri.getScheme());
        return "https".equals(scheme) || "http".equals(scheme) ? value : "";
    }

    public String checkNormalizeHttpOrLocalCleartextUrl(String rawUrl) {
        String value = checkNormalizeHttpOrHttpsUrl(rawUrl);
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

    public String checkRequireHttpOrLocalCleartextUrl(String rawUrl, String label) {
        String normalized = checkNormalizeHttpOrHttpsUrl(rawUrl);
        String name = label == null || label.trim().length() == 0 ? "URL" : label.trim();
        if (normalized.length() == 0) {
            throw new IllegalArgumentException(name + " must start with http:// or https://.");
        }
        URI uri = parse(normalized);
        if (uri != null && "http".equals(lower(uri.getScheme())) && !relaxedHttpEnabled && !isAllowedCleartextHttpHost(uri.getHost())) {
            throw new IllegalArgumentException(name + " using HTTP cleartext is only allowed for localhost, 127.0.0.1, or 10.0.2.2.");
        }
        return normalized;
    }

    public boolean checkIsAllowedCleartextHttpUrl(String rawUrl) {
        String value = checkNormalizeHttpOrHttpsUrl(rawUrl);
        if (value.length() == 0) {
            return false;
        }
        URI uri = parse(value);
        return uri != null
                && "http".equals(lower(uri.getScheme()))
                && (relaxedHttpEnabled || isAllowedCleartextHttpHost(uri.getHost()));
    }

    boolean isAllowedCleartextHttpHost(String host) {
        if (host == null || host.length() == 0) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (cleartextHosts.contains(h)) {
            return true;
        }
        return isPrivateNetwork(h);
    }

    boolean isPrivateNetwork(String host) {
        for (Predicate<String> predicate : privateNetworkPredicates) {
            if (predicate.test(host)) {
                return true;
            }
        }
        return false;
    }

    private URI parse(String value) {
        try {
            return new URI(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    // ---- Static convenience API ----

    public static void setRelaxedHttpEnabled(boolean enabled) {
        DEFAULT.relaxedHttpEnabled = enabled;
    }

    public static boolean isRelaxedHttpEnabled() {
        return DEFAULT.relaxedHttpEnabled;
    }

    public static String normalizeHttpOrHttpsUrl(String rawUrl) {
        return DEFAULT.checkNormalizeHttpOrHttpsUrl(rawUrl);
    }

    public static String normalizeHttpOrLocalCleartextUrl(String rawUrl) {
        return DEFAULT.checkNormalizeHttpOrLocalCleartextUrl(rawUrl);
    }

    public static String requireHttpOrLocalCleartextUrl(String rawUrl, String label) {
        return DEFAULT.checkRequireHttpOrLocalCleartextUrl(rawUrl, label);
    }

    public static boolean isAllowedCleartextHttpUrl(String rawUrl) {
        return DEFAULT.checkIsAllowedCleartextHttpUrl(rawUrl);
    }

    public static UrlPolicy getDefault() {
        return DEFAULT;
    }
}
