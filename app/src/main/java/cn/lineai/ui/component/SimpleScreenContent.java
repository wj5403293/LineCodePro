package cn.lineai.ui.component;

import android.content.Context;
import cn.lineai.R;

/**
 * Static lookup tables that map a settings "screen id" to the localized title, subtitle, and
 * list of placeholder rows used by the fallback {@link SimpleSettingsScreenView}.
 *
 * <p>These mappings were originally inlined in {@code MainChatView.simpleScreen(...)}; they
 * are kept here so the chat view does not have to carry the full string-id switch.</p>
 */
public final class SimpleScreenContent {

    private SimpleScreenContent() {
    }

    /** Localized title for the given {@code screenId}; falls back to "Project" copy. */
    public static String title(Context context, String screenId) {
        if (context == null) {
            return "";
        }
        if ("llm".equals(screenId)) return context.getString(R.string.screen_llm_title);
        if ("promptTemplates".equals(screenId)) return context.getString(R.string.screen_prompt_templates_title);
        if ("input".equals(screenId)) return context.getString(R.string.screen_input_title);
        if ("mcp".equals(screenId)) return context.getString(R.string.screen_mcp_title);
        if ("toolSettings".equals(screenId)) return context.getString(R.string.screen_tools_title);
        if ("theme".equals(screenId)) return context.getString(R.string.fallback_screen_theme_title);
        if ("output".equals(screenId)) return context.getString(R.string.fallback_screen_output_title);
        if ("experimental".equals(screenId)) return context.getString(R.string.screen_experimental_title);
        if ("storage".equals(screenId)) return context.getString(R.string.screen_storage_title);
        if ("memory".equals(screenId)) return context.getString(R.string.screen_memory_title);
        if ("data".equals(screenId)) return context.getString(R.string.fallback_screen_data_title);
        if ("keepAlive".equals(screenId)) return context.getString(R.string.fallback_screen_keep_alive_title_alt);
        if ("sshSettings".equals(screenId)) return context.getString(R.string.screen_ssh_title);
        if ("termuxIntegration".equals(screenId)) return context.getString(R.string.screen_termux_title);
        if ("about".equals(screenId)) return context.getString(R.string.fallback_screen_about_title_alt);
        if ("modelAddOptions".equals(screenId)) return context.getString(R.string.screen_model_add_options_title);
        if ("licenses".equals(screenId)) return context.getString(R.string.fallback_screen_licenses_title);
        if ("tutorial".equals(screenId)) return context.getString(R.string.fallback_screen_tutorial_title_alt);
        if (screenId != null && screenId.startsWith("extension:")) return context.getString(R.string.fallback_screen_extension_title);
        return context.getString(R.string.header_project_default);
    }

    /** Localized subtitle for the given {@code screenId}; falls back to a generic blurb. */
    public static String subtitle(Context context, String screenId) {
        if (context == null) {
            return "";
        }
        if ("about".equals(screenId)) return context.getString(R.string.fallback_screen_about_subtitle);
        if ("modelAddOptions".equals(screenId)) return context.getString(R.string.fallback_screen_model_add_options_subtitle);
        if (screenId != null && screenId.startsWith("extension:")) return context.getString(R.string.fallback_screen_extension_subtitle);
        return context.getString(R.string.fallback_screen_default_subtitle);
    }

    /** Localized placeholder rows for the given {@code screenId}. */
    public static String[] rows(Context context, String screenId) {
        if (context == null) {
            return new String[] {""};
        }
        if ("llm".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_llm_tone),
                context.getString(R.string.fallback_row_llm_thinking),
                context.getString(R.string.fallback_row_llm_keep_reasoning),
                context.getString(R.string.fallback_row_llm_prompts)
        };
        if ("input".equals(screenId)) return new String[] {context.getString(R.string.fallback_row_input_enter)};
        if ("mcp".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_mcp_execution),
                context.getString(R.string.fallback_row_mcp_local),
                context.getString(R.string.fallback_row_mcp_ssh),
                context.getString(R.string.fallback_row_mcp_confirm)
        };
        if ("toolSettings".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_tools_image_understanding),
                context.getString(R.string.fallback_row_tools_web_search),
                context.getString(R.string.fallback_row_tools_model_select),
                context.getString(R.string.fallback_row_tools_search_api)
        };
        if ("theme".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_theme_dark),
                context.getString(R.string.fallback_row_theme_light),
                context.getString(R.string.fallback_row_theme_coffee),
                context.getString(R.string.fallback_row_theme_high_contrast)
        };
        if ("output".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_output_code_wrap),
                context.getString(R.string.fallback_row_output_open_mode),
                context.getString(R.string.fallback_row_output_browser_js),
                context.getString(R.string.fallback_row_output_markdown_preview)
        };
        if ("experimental".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_experimental_keyboard),
                context.getString(R.string.fallback_row_experimental_rendering)
        };
        if ("storage".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_storage_chat),
                context.getString(R.string.fallback_row_storage_config),
                context.getString(R.string.fallback_row_storage_diff_cache),
                context.getString(R.string.fallback_row_storage_workspace)
        };
        if ("memory".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_memory_long_term),
                context.getString(R.string.fallback_row_memory_project),
                context.getString(R.string.fallback_row_memory_short_term),
                context.getString(R.string.fallback_row_memory_index)
        };
        if ("data".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_data_full_export),
                context.getString(R.string.fallback_row_data_linecode_import),
                context.getString(R.string.fallback_row_data_archive)
        };
        if ("keepAlive".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_keep_alive_wake_lock),
                context.getString(R.string.fallback_row_keep_alive_foreground),
                context.getString(R.string.fallback_row_keep_alive_fake_music),
                context.getString(R.string.fallback_row_keep_alive_battery_whitelist)
        };
        if ("sshSettings".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_ssh_host),
                context.getString(R.string.fallback_row_ssh_port),
                context.getString(R.string.fallback_row_ssh_username),
                context.getString(R.string.fallback_row_ssh_private_key),
                context.getString(R.string.fallback_row_ssh_test)
        };
        if ("termuxIntegration".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_termux_grant),
                context.getString(R.string.fallback_row_termux_run_command_perm),
                context.getString(R.string.fallback_row_termux_auto_ssh)
        };
        if ("about".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_about_version),
                context.getString(R.string.screen_about_open_source_licenses)
        };
        if ("modelAddOptions".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_model_add_custom_api),
                context.getString(R.string.fallback_row_model_add_local_gguf),
                context.getString(R.string.fallback_row_model_add_openai_provider),
                context.getString(R.string.fallback_row_model_add_codex_preset)
        };
        if ("tutorial".equals(screenId)) return new String[] {
                context.getString(R.string.fallback_row_tutorial_beginner),
                context.getString(R.string.fallback_row_tutorial_pro),
                context.getString(R.string.fallback_row_tutorial_tools)
        };
        if (screenId != null && screenId.startsWith("extension:")) return new String[] {
                context.getString(R.string.fallback_row_extension_add),
                context.getString(R.string.fallback_row_extension_installed),
                context.getString(R.string.fallback_row_extension_enabled),
                context.getString(R.string.fallback_row_extension_manage)
        };
        return new String[] {context.getString(R.string.fallback_row_default), context.getString(R.string.fallback_row_default_subtitle)};
    }
}
