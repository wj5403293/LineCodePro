package cn.lineai.ui.util;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Slash 命令的元数据与解析工具。定义在 composer 中通过输入 {@code /} 触发的命令
 * 集合，以及在 send 时把整段输入文本解析为 {@link Parsed} 的纯逻辑入口。
 *
 * <p>本类不依赖任何 Android 视图，便于在 JUnit 单元测试中覆盖。</p>
 */
public final class SlashCommandCatalog {

    public enum Kind {
        MODE,
        MODEL
    }

    /**
     * 主命令定义。{@link #token} 是用户输入的 {@code /xxx} 前缀（含斜杠），
     * {@link #description} 为 popup 中展示的副标题，{@link #kind} 决定该命令
     * 在 send 时如何分发。
     */
    public static final class Definition {
        public final String token;
        public final String description;
        public final Kind kind;

        Definition(String token, String description, Kind kind) {
            this.token = token;
            this.description = description;
            this.kind = kind;
        }
    }

    /**
     * 解析结果。{@link #kind == MODE} 时 {@link #mode} 有值；{@link #kind == MODEL}
     * 时 {@link #modelId} 必有值，{@link #reasoningEffort} 可为空。
     */
    public static final class Parsed {
        public final Kind kind;
        public final String mode;
        public final String modelId;
        public final String reasoningEffort;

        Parsed(Kind kind, String mode, String modelId, String reasoningEffort) {
            this.kind = kind;
            this.mode = mode;
            this.modelId = modelId;
            this.reasoningEffort = reasoningEffort;
        }
    }

    public static final List<Definition> MAIN_COMMANDS;
    public static final List<String> REASONING_LEVELS;

    static {
        List<Definition> main = new ArrayList<>();
        main.add(new Definition("/chat", "Switch to chat mode (read-only Q&A).", Kind.MODE));
        main.add(new Definition("/plan", "Switch to plan mode (read-only planning).", Kind.MODE));
        main.add(new Definition("/agent", "Switch to agent mode (default execution).", Kind.MODE));
        main.add(new Definition("/control", "Switch to control mode (phone automation).", Kind.MODE));
        main.add(new Definition("/model", "Switch model, optional reasoning level.", Kind.MODEL));
        MAIN_COMMANDS = Collections.unmodifiableList(main);
        REASONING_LEVELS = Collections.unmodifiableList(Arrays.asList(
                AiBehaviorSettings.REASONING_OFF,
                AiBehaviorSettings.REASONING_LOW,
                AiBehaviorSettings.REASONING_MEDIUM,
                AiBehaviorSettings.REASONING_HIGH,
                AiBehaviorSettings.REASONING_MAX
        ));
    }

    private SlashCommandCatalog() {
    }

    /**
     * 按 query（不含前导 {@code /}）前缀过滤主命令。query 为空或全空白时返回全列表。
     * 匹配大小写不敏感。
     */
    public static List<Definition> filterMain(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.length() == 0) {
            return MAIN_COMMANDS;
        }
        List<Definition> matches = new ArrayList<>();
        for (Definition def : MAIN_COMMANDS) {
            if (def.token.substring(1).toLowerCase().startsWith(needle)) {
                matches.add(def);
            }
        }
        return matches;
    }

    /**
     * 解析 send 时输入的整段文本。识别以下两种形式：
     * <ul>
     *     <li>{@code /chat} / {@code /plan} / {@code /agent} / {@code /control} → MODE</li>
     *     <li>{@code /model {id} [level]} → MODEL；id 必填；level 必须是
     *         {@link #REASONING_LEVELS} 之一，否则忽略但 id 仍生效</li>
     * </ul>
     * 未命中返回 null，调用方应继续按普通消息处理。
     */
    public static Parsed parse(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.length() == 0 || trimmed.charAt(0) != '/') {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        String head = tokens[0].toLowerCase();
        switch (head) {
            case "/chat":
                return new Parsed(Kind.MODE, ChatMode.CHAT, null, null);
            case "/plan":
                return new Parsed(Kind.MODE, ChatMode.PLAN, null, null);
            case "/agent":
                return new Parsed(Kind.MODE, ChatMode.AGENT, null, null);
            case "/control":
                return new Parsed(Kind.MODE, ChatMode.CONTROL, null, null);
            case "/model":
                if (tokens.length < 2 || tokens[1].length() == 0) {
                    return null;
                }
                String modelId = tokens[1];
                String effort = null;
                if (tokens.length >= 3) {
                    String candidate = tokens[2].toLowerCase();
                    if (REASONING_LEVELS.contains(candidate)) {
                        effort = candidate;
                    }
                }
                return new Parsed(Kind.MODEL, null, modelId, effort);
            default:
                return null;
        }
    }

    /**
     * 给定模型 id 集合与 query，过滤出 id 前缀匹配的模型。query 为空返回全列表。
     * 匹配大小写不敏感。{@code modelIds} 为 null 时返回空列表。
     */
    public static List<String> filterModelIds(List<String> modelIds, String query) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyList();
        }
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.length() == 0) {
            return new ArrayList<>(modelIds);
        }
        List<String> matches = new ArrayList<>();
        for (String id : modelIds) {
            if (id != null && id.toLowerCase().startsWith(needle)) {
                matches.add(id);
            }
        }
        return matches;
    }

    /**
     * 思考等级前缀匹配（大小写不敏感）。{@code query} 为空时返回全部等级。
     */
    public static List<String> filterReasoningLevels(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.length() == 0) {
            return REASONING_LEVELS;
        }
        List<String> matches = new ArrayList<>();
        for (String level : REASONING_LEVELS) {
            if (level.toLowerCase().startsWith(needle)) {
                matches.add(level);
            }
        }
        return matches;
    }
}
