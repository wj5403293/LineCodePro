package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import cn.lineai.ui.theme.LineTheme;

public final class UserMessageView extends LinearLayout {
    private final LinearLayout quoteBlockView;
    private final TextView quoteTextView;
    private final TextView contentText;
    private final LinearLayout attachmentList;
    private final MessageActionBarView actionBar;
    private String lastContent = "";
    private ChatMessage currentMessage;
    private MessageActionListener actionListener;

    public UserMessageView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        LineTheme.padding(this, LineTheme.LG, 0, LineTheme.LG, 6);

        // Quote block (shown when message has quoted content)
        quoteBlockView = new LinearLayout(context);
        quoteBlockView.setOrientation(HORIZONTAL);
        quoteBlockView.setVisibility(GONE);
        quoteBlockView.setBackground(LineTheme.rounded(context, 0xFF2A2A3A, 8));
        LineTheme.padding(quoteBlockView, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);
        View quoteBar = new View(context);
        quoteBar.setBackgroundColor(LineTheme.ACCENT);
        quoteBlockView.addView(quoteBar, new LayoutParams(LineTheme.dp(context, 3), LayoutParams.MATCH_PARENT));
        quoteTextView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.ITALIC);
        quoteTextView.setMaxLines(3);
        quoteTextView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams qtp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        qtp.leftMargin = LineTheme.dp(context, LineTheme.SM);
        quoteBlockView.addView(quoteTextView, qtp);
        int horizontalPaddingPx = LineTheme.dp(context, LineTheme.LG) * 2;
        int availableWidth = context.getResources().getDisplayMetrics().widthPixels - horizontalPaddingPx;
        quoteBlockView.setMinimumWidth(0);
        LinearLayout.LayoutParams qbp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        qbp.bottomMargin = LineTheme.dp(context, LineTheme.XS);
        addView(quoteBlockView, qbp);

        contentText = LineTheme.text(context, "", 16, LineTheme.TEXT_ON_COLOR, Typeface.NORMAL);
        // Do NOT set textIsSelectable here - it steals touch from sibling buttons
        contentText.setLineSpacing(LineTheme.dp(context, 2), 1.0f);
        contentText.setBackground(LineTheme.userBubble(context));
        LineTheme.padding(contentText, LineTheme.MD, 5, LineTheme.MD, 5);
        contentText.setMaxWidth((int) (availableWidth * 0.80f));
        addView(contentText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        attachmentList = new LinearLayout(context);
        attachmentList.setOrientation(VERTICAL);
        attachmentList.setGravity(Gravity.END);
        LinearLayout.LayoutParams attachmentParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        attachmentParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        addView(attachmentList, attachmentParams);

        actionBar = new MessageActionBarView(context, MessageActionBarView.ALIGN_RIGHT, true);
        actionBar.setListener(new MessageActionBarView.Listener() {
            @Override
            public void onCopy() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onCopyMessage(currentMessage);
                }
            }

            @Override
            public void onQuote() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onQuoteMessage(currentMessage);
                }
            }

            @Override
            public void onShare() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onShareMessage(currentMessage);
                }
            }

            @Override
            public void onSelect() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onSelectMessage(currentMessage, UserMessageView.this);
                }
            }

            @Override
            public void onMultiSelect() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onMultiSelectMessage(currentMessage);
                }
            }

            @Override
            public void onRecall() {
                if (actionListener != null && currentMessage != null) {
                    actionListener.onRecallMessage(currentMessage);
                }
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 22));
        actionParams.topMargin = LineTheme.dp(context, 3);
        addView(actionBar, actionParams);
    }

    public void setMessageActionListener(MessageActionListener listener) {
        actionListener = listener;
    }

    public void bind(ChatMessage message) {
        currentMessage = message;
        String content = visibleUserContent(message);
        if (!lastContent.equals(content)) {
            // Parse quote block: "> quoted\n> lines\n\nactual message"
            String quotePart = null;
            String messagePart = content;
            if (content.startsWith("> ")) {
                int doubleNewline = content.indexOf("\n\n");
                if (doubleNewline > 0) {
                    quotePart = content.substring(0, doubleNewline).replace("\n> ", "\n").substring(2);
                    messagePart = content.substring(doubleNewline + 2);
                } else {
                    quotePart = content.replace("\n> ", "\n").substring(2);
                    messagePart = "";
                }
            }
            if (quotePart != null && quotePart.length() > 0) {
                quoteTextView.setText(quotePart);
                quoteBlockView.setVisibility(VISIBLE);
            } else {
                quoteBlockView.setVisibility(GONE);
            }
            contentText.setText(messagePart);
            lastContent = content;
        }
        contentText.setVisibility(contentText.getText().length() == 0 ? GONE : VISIBLE);
        renderAttachments(message);
    }

    private String visibleUserContent(ChatMessage message) {
        if (message == null) {
            return "";
        }
        String content = message.getContent();
        if (getContext().getString(R.string.message_user_attached_files).equals(content.trim()) && message.hasAttachments()) {
            return "";
        }
        return content;
    }

    private void renderAttachments(ChatMessage message) {
        attachmentList.removeAllViews();
        if (message == null || !message.hasAttachments()) {
            attachmentList.setVisibility(GONE);
            return;
        }
        attachmentList.setVisibility(VISIBLE);
        for (InputAttachment attachment : message.getAttachments()) {
            attachmentList.addView(attachmentChip(attachment));
        }
    }

    private TextView attachmentChip(InputAttachment attachment) {
        TextView chip = LineTheme.textMedium(getContext(), attachment.getName(), LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        chip.setMaxWidth(LineTheme.dp(getContext(), 220));
        chip.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(chip, LineTheme.SM, 4, LineTheme.SM, 4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
        chip.setLayoutParams(params);
        return chip;
    }
}
