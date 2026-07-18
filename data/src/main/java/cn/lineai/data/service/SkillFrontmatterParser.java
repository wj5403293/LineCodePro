package cn.lineai.data.service;

import java.util.Locale;

/**
 * Skill Markdown frontmatter 纯解析工具。
 * 从 SKILL.md 内容中提取 name / description 等 YAML frontmatter 字段与 Markdown 标题。
 */
public final class SkillFrontmatterParser {

    private SkillFrontmatterParser() {
    }

    /**
     * 从 Markdown frontmatter 中提取指定 key 的值。
     */
    public static String frontmatterValue(String content, String key) {
        String[] lines = safe(content).split("\\r?\\n");
        boolean inFrontmatter = lines.length > 0 && "---".equals(lines[0].trim());
        int start = inFrontmatter ? 1 : 0;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (inFrontmatter && "---".equals(line)) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (key.equalsIgnoreCase(line.substring(0, colon).trim())) {
                return unquote(line.substring(colon + 1).trim());
            }
        }
        return "";
    }

    /**
     * 从 Markdown 正文提取第一个一级标题。
     */
    public static String markdownTitle(String content) {
        String[] lines = safe(content).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    /**
     * 从 Markdown 正文提取描述行（frontmatter 之后、标题之后的第一行非空文本）。
     */
    public static String descriptionLine(String content) {
        String[] lines = safe(content).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("---") || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("description:")) {
                return trimmed.substring("description:".length()).trim();
            }
            return trimmed;
        }
        return "";
    }

    private static String unquote(String value) {
        String text = safe(value).trim();
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
