package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.R;

public final class ExperimentalSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public ExperimentalSettingsScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_experimental_title), listener::onBack, null);
        LinearLayout content = getContent();

        SettingsSectionView chat = new SettingsSectionView(context, context.getString(R.string.screen_experimental_section_chat));
        chat.addRow(new SwitchRowView(context, IconButtonView.MESSAGE_SQUARE_TEXT, context.getString(R.string.screen_experimental_resume_label), context.getString(R.string.screen_experimental_resume_desc), false), false);
        content.addView(chat, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView system = new SettingsSectionView(context, context.getString(R.string.screen_experimental_section_compat));
        system.addRow(new SwitchRowView(context, IconButtonView.SMARTPHONE, context.getString(R.string.screen_experimental_keyboard_label), context.getString(R.string.screen_experimental_keyboard_desc), false), false);
        content.addView(system, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView render = new SettingsSectionView(context, context.getString(R.string.screen_experimental_section_render));
        render.addRow(new SwitchRowView(context, IconButtonView.SQUARE_FUNCTION, context.getString(R.string.screen_experimental_math_label), context.getString(R.string.screen_experimental_math_desc), false), false);
        content.addView(render, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
