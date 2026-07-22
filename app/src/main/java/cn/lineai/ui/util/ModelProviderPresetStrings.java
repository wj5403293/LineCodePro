package cn.lineai.ui.util;

import android.content.Context;
import cn.lineai.R;
import java.util.HashMap;
import java.util.Map;

public final class ModelProviderPresetStrings {

    private static final class Entry {
        final int labelResId;
        final int descResId;
        final int hintResId;

        Entry(int labelResId, int descResId, int hintResId) {
            this.labelResId = labelResId;
            this.descResId = descResId;
            this.hintResId = hintResId;
        }
    }

    private static final Map<String, Entry> REGISTRY = new HashMap<>();

    static {
        register("custom",
                R.string.model_provider_preset_custom_label,
                R.string.model_provider_preset_custom_desc,
                R.string.model_provider_preset_custom_hint);
        register("deepseek",
                R.string.model_provider_preset_deepseek_label,
                R.string.model_provider_preset_deepseek_desc,
                R.string.model_provider_preset_deepseek_hint);
        register("glm",
                R.string.model_provider_preset_glm_label,
                R.string.model_provider_preset_glm_desc,
                R.string.model_provider_preset_glm_hint);
        register("mimo",
                R.string.model_provider_preset_mimo_label,
                R.string.model_provider_preset_mimo_desc,
                R.string.model_provider_preset_mimo_hint);
        register("mimo-token-plan",
                R.string.model_provider_preset_mimo_token_plan_label,
                R.string.model_provider_preset_mimo_token_plan_desc,
                R.string.model_provider_preset_mimo_token_plan_hint);
        register("kimi",
                R.string.model_provider_preset_kimi_label,
                R.string.model_provider_preset_kimi_desc,
                R.string.model_provider_preset_kimi_hint);
        register("qwen",
                R.string.model_provider_preset_qwen_label,
                R.string.model_provider_preset_qwen_desc,
                R.string.model_provider_preset_qwen_hint);
        register("openai",
                R.string.model_provider_preset_openai_label,
                R.string.model_provider_preset_openai_desc,
                R.string.model_provider_preset_openai_hint);
        register("claude",
                R.string.model_provider_preset_claude_label,
                R.string.model_provider_preset_claude_desc,
                R.string.model_provider_preset_claude_hint);
        register("gemini",
                R.string.model_provider_preset_gemini_label,
                R.string.model_provider_preset_gemini_desc,
                R.string.model_provider_preset_gemini_hint);
        register("openrouter",
                R.string.model_provider_preset_openrouter_label,
                R.string.model_provider_preset_openrouter_desc,
                R.string.model_provider_preset_openrouter_hint);
        register("codex",
                R.string.model_provider_preset_codex_label,
                R.string.model_provider_preset_codex_desc,
                R.string.model_provider_preset_codex_hint);
    }

    private ModelProviderPresetStrings() {
    }

    public static void register(String id, int labelResId, int descResId, int hintResId) {
        REGISTRY.put(id, new Entry(labelResId, descResId, hintResId));
    }

    public static String getLabel(Context context, String id) {
        Entry entry = REGISTRY.get(id);
        return entry != null ? context.getString(entry.labelResId) : (id != null ? id : "");
    }

    public static String getDesc(Context context, String id) {
        Entry entry = REGISTRY.get(id);
        return entry != null ? context.getString(entry.descResId) : "";
    }

    public static String getHint(Context context, String id) {
        Entry entry = REGISTRY.get(id);
        return entry != null ? context.getString(entry.hintResId) : "";
    }
}
