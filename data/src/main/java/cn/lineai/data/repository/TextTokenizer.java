package cn.lineai.data.repository;

import cn.lineai.model.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * 关键词抽取与 CJK 双字分词工具。纯算法实现，不依赖数据库。
 */
final class TextTokenizer {
    private static final String[] STOP_WORDS = new String[] {
            "the", "and", "for", "with", "this", "that", "from", "have", "into", "your", "you", "are", "how",
            "一个", "这个", "那个", "怎么", "如何", "什么", "以及", "或者", "可以", "需要", "进行", "使用"
    };

    private TextTokenizer() {
    }

    static List<String> extractKeywords(String input) {
        ArrayList<String> keywords = new ArrayList<>();
        String value = safeStatic(input).toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isLatinTokenChar(ch)) {
                latin.append(ch);
            } else {
                addKeyword(keywords, latin.toString());
                latin.setLength(0);
            }
            if (isCjk(ch)) {
                cjk.append(ch);
            } else {
                addCjkKeywords(keywords, cjk.toString());
                cjk.setLength(0);
            }
        }
        addKeyword(keywords, latin.toString());
        addCjkKeywords(keywords, cjk.toString());
        return keywords;
    }

    static HashMap<String, Integer> termFrequency(List<String> keywords) {
        HashMap<String, Integer> counts = new HashMap<>();
        for (String keyword : keywords) {
            Integer count = counts.get(keyword);
            counts.put(keyword, count == null ? 1 : count + 1);
        }
        return counts;
    }

    static String safeStatic(String value) {
        return Strings.nullToEmpty(value);
    }

    private static boolean isLatinTokenChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '_'
                || ch == '#'
                || ch == '.'
                || ch == '-';
    }

    private static boolean isCjk(char ch) {
        return ch >= '\u4e00' && ch <= '\u9fff';
    }

    private static void addCjkKeywords(ArrayList<String> keywords, String chunk) {
        String value = safeStatic(chunk).trim();
        if (value.length() < 2) {
            return;
        }
        if (value.length() <= 4) {
            addKeyword(keywords, value);
            return;
        }
        for (int i = 0; i < value.length() - 1; i++) {
            addKeyword(keywords, value.substring(i, i + 2));
        }
    }

    private static void addKeyword(ArrayList<String> keywords, String term) {
        String value = trimToken(safeStatic(term).trim());
        if (value.length() < 2) {
            return;
        }
        if (value.length() > 32) {
            value = value.substring(0, 32);
        }
        if (!isStopWord(value)) {
            keywords.add(value);
        }
    }

    private static String trimToken(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isTokenTrimChar(value.charAt(start))) {
            start++;
        }
        while (end > start && isTokenTrimChar(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isTokenTrimChar(char ch) {
        return ch == '.' || ch == '-' || ch == '_' || ch == '#';
    }

    private static boolean isStopWord(String term) {
        for (String stopWord : STOP_WORDS) {
            if (stopWord.equals(term)) {
                return true;
            }
        }
        return false;
    }
}
