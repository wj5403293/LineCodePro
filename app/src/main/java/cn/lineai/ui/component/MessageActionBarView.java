package cn.lineai.ui.component;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import cn.lineai.R;
import cn.lineai.ui.theme.LineTheme;

public final class MessageActionBarView extends LinearLayout {
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_RIGHT = 1;
    private final IconButtonView copyButton;
    private final IconButtonView recallButton;
    private final IconButtonView quoteButton;
    private final IconButtonView shareButton;
    private final IconButtonView selectButton;
    private final IconButtonView multiSelectButton;

    public MessageActionBarView(Context context, int align, boolean recallEnabled) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(align == ALIGN_RIGHT ? Gravity.END : Gravity.START);
        setMinimumHeight(LineTheme.dp(context, 22));

        copyButton = icon(context, IconButtonView.COPY);
        copyButton.setContentDescription(context.getString(R.string.message_action_copy_desc));
        addView(copyButton, iconParams(context));

        quoteButton = icon(context, IconButtonView.QUOTE);
        quoteButton.setContentDescription("引用");
        addView(quoteButton, iconParams(context));

        shareButton = icon(context, IconButtonView.EXTERNAL_LINK);
        shareButton.setContentDescription("分享");
        addView(shareButton, iconParams(context));

        selectButton = icon(context, IconButtonView.FILE_PEN_LINE);
        selectButton.setContentDescription("选中文字");
        addView(selectButton, iconParams(context));

        multiSelectButton = icon(context, IconButtonView.CIRCLE_CHECK);
        multiSelectButton.setContentDescription("多选导出");
        addView(multiSelectButton, iconParams(context));

        IconButtonView recall = null;
        if (recallEnabled) {
            recall = icon(context, IconButtonView.ROTATE_CCW);
            recall.setContentDescription(context.getString(R.string.message_action_recall_desc));
            addView(recall, iconParams(context));
        }
        recallButton = recall;
    }

    public void setListener(Listener listener) {
        copyButton.setOnClickListener(v -> { if (listener != null) listener.onCopy(); });
        quoteButton.setOnClickListener(v -> { if (listener != null) listener.onQuote(); });
        shareButton.setOnClickListener(v -> { if (listener != null) listener.onShare(); });
        selectButton.setOnClickListener(v -> { if (listener != null) listener.onSelect(); });
        multiSelectButton.setOnClickListener(v -> { if (listener != null) listener.onMultiSelect(); });
        if (recallButton != null) {
            recallButton.setOnClickListener(v -> { if (listener != null) listener.onRecall(); });
        }
    }

    public interface Listener {
        void onCopy();
        void onQuote();
        void onShare();
        void onSelect();
        void onMultiSelect();
        void onRecall();
    }

    private IconButtonView icon(Context context, int type) {
        IconButtonView icon = new IconButtonView(context, type);
        icon.setIconColor(LineTheme.TEXT_TERTIARY);
        icon.setIconPaddingDp(4, 3, 5, 4);
        icon.setClickable(true);
        return icon;
    }

    private LinearLayout.LayoutParams iconParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 22));
        params.rightMargin = LineTheme.dp(context, LineTheme.XS);
        return params;
    }
}
