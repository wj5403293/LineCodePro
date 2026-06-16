package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.ui.theme.LineTheme;

public final class PluginPageScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public PluginPageScreenView(Context context, String title, Listener listener) {
        super(context, title == null ? context.getString(R.string.screen_plugin_page_default_title) : title, listener::onBack, null);
        LinearLayout content = getContent();
        content.setGravity(Gravity.CENTER);
        LineTheme.padding(content, LineTheme.XL, LineTheme.XL, LineTheme.XL, LineTheme.XL);
        TextView status = LineTheme.text(context, context.getString(R.string.screen_plugin_page_opening), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        content.addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView desc = LineTheme.text(context, context.getString(R.string.screen_plugin_page_desc), LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(desc, descParams);
    }
}
