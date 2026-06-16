package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.ui.theme.LineTheme;

public final class TutorialScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public TutorialScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_tutorial_title), listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.MD, LineTheme.LG, 100);

        LinearLayout selector = new LinearLayout(context);
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 16, LineTheme.BORDER_LIGHT));
        addVariant(selector, context.getString(R.string.screen_tutorial_variant_beginner), context.getString(R.string.screen_tutorial_desc_beginner), true);
        addVariant(selector, context.getString(R.string.screen_tutorial_variant_pro), context.getString(R.string.screen_tutorial_pro_desc), false);
        content.addView(selector, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView subtitle = LineTheme.text(context, context.getString(R.string.screen_tutorial_subtitle), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        subtitle.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        subtitleParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(subtitle, subtitleParams);

        addHeading(content, context.getString(R.string.screen_tutorial_section_workspace));
        addParagraph(content, context.getString(R.string.screen_tutorial_workspace_p1));
        addHeading(content, context.getString(R.string.screen_tutorial_section_model));
        addParagraph(content, context.getString(R.string.screen_tutorial_model_p1));
        addHeading(content, context.getString(R.string.screen_tutorial_section_tools));
        addParagraph(content, context.getString(R.string.screen_tutorial_tools_p1));
        addHeading(content, context.getString(R.string.screen_tutorial_section_extensions));
        addParagraph(content, context.getString(R.string.screen_tutorial_extensions_p1));
        addCode(content, context.getString(R.string.screen_tutorial_code));
    }

    private void addVariant(LinearLayout selector, String title, String desc, boolean active) {
        Context context = selector.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(context, 62));
        if (active) row.setBackgroundColor(LineTheme.ACCENT_MUTED);
        LineTheme.padding(row, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        text.addView(LineTheme.text(context, title, LineTheme.FONT_MD, active ? LineTheme.ACCENT : LineTheme.TEXT, Typeface.BOLD));
        TextView sub = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = LineTheme.dp(context, 2);
        text.addView(sub, subParams);
        if (active) {
            IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
            check.setIconColor(LineTheme.ACCENT);
            check.setIconSizeDp(18, 16);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        }
        selector.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addHeading(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView heading = LineTheme.text(context, text, 21, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.XL);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(heading, params);
    }

    private void addParagraph(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView paragraph = LineTheme.text(context, text, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        paragraph.setLineSpacing(LineTheme.dp(context, 4), 1f);
        content.addView(paragraph, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addCode(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView code = LineTheme.text(context, text, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        code.setTypeface(Typeface.MONOSPACE);
        code.setLineSpacing(LineTheme.dp(context, 4), 1f);
        code.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        LineTheme.padding(code, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(code, params);
    }
}
