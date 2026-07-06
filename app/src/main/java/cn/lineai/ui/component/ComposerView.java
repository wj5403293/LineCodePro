package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMode;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.InputSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.mvp.QuoteController;
import cn.lineai.ui.theme.LineTheme;
import cn.lineai.ui.util.SlashCommandCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComposerView extends LinearLayout implements QuoteController.QuotePreview {
    public interface Listener {
        void onSend(String text, List<InputAttachment> attachments);

        void onAttachClick();

        void onModeChanged(String mode);

        void onStop();

        void onModelQuickSwitch(String modelId);

        void onModelManageClick();

        void onAiReasoningEffortChanged(String effort);
    }

    /**
     * 关闭引用预览时的回调，便于 QuoteController 清理自身状态。
     */
    public interface QuoteDismissListener {
        void onQuoteDismissed();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearLayout quotePreviewLayout;
    private TextView quotePreviewText;
    private IconButtonView quoteCloseButton;
    private QuoteDismissListener quoteDismissListener;
    private LinearLayout modelSelectorButton;
    private TextView modelText;
    private IconButtonView modelChevron;
    private TextView contextText;
    private LinearLayout modeSelectorButton;
    private TextView modeSelectorText;
    private IconButtonView modeSelectorChevron;
    private HorizontalScrollView attachmentScroll;
    private LinearLayout attachmentList;
    private IconButtonView attachButton;
    private EditText input;
    private IconButtonView sendButton;
    private final ArrayList<InputAttachment> attachments = new ArrayList<>();
    private PopupWindow modePopup;
    private PopupWindow modelPopup;
    private SlashCommandPopup slashPopup;
    private String lastSlashSignature = null;
    private boolean streaming;
    private String chatMode = ChatMode.DEFAULT;
    private String enterKeyBehavior = InputSettings.ENTER_SEND;
    private String selectedModelId = "";
    private List<ModelConfig> availableModels = Collections.emptyList();
    private Listener listener;

    public ComposerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setWillNotDraw(false);
        LineTheme.padding(this, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.LG);

        buildQuotePreview();

        attachmentScroll = new HorizontalScrollView(context);
        attachmentScroll.setHorizontalScrollBarEnabled(false);
        attachmentScroll.setVisibility(GONE);
        attachmentList = new LinearLayout(context);
        attachmentList.setOrientation(HORIZONTAL);
        attachmentScroll.addView(attachmentList, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams attachmentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        attachmentParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(attachmentScroll, attachmentParams);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setMinimumHeight(LineTheme.dp(context, 148));
        panel.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 22, LineTheme.BORDER));
        addView(panel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(metaRow, LineTheme.LG, 0, LineTheme.LG, 0);
        panel.addView(metaRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34)));

        modelSelectorButton = new LinearLayout(context);
        modelSelectorButton.setOrientation(HORIZONTAL);
        modelSelectorButton.setGravity(Gravity.CENTER_VERTICAL);
        modelSelectorButton.setClickable(true);
        modelSelectorButton.setFocusable(true);
        modelSelectorButton.setOnClickListener(v -> showModelPopup(modelSelectorButton));
        modelSelectorButton.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 14));
        LineTheme.padding(modelSelectorButton, LineTheme.SM, 0, LineTheme.SM, 0);

        modelText = LineTheme.textMedium(context, "", LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY);
        modelText.setSingleLine(true);
        modelText.setMaxWidth(LineTheme.dp(context, 180));
        modelText.setEllipsize(TextUtils.TruncateAt.END);
        modelSelectorButton.addView(modelText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        modelChevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        modelChevron.setIconColor(LineTheme.TEXT_SECONDARY);
        modelChevron.setIconSizeDp(16, 12);
        modelChevron.setClickable(false);
        LinearLayout.LayoutParams modelChevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16));
        modelChevronParams.leftMargin = LineTheme.dp(context, 2);
        modelSelectorButton.addView(modelChevron, modelChevronParams);

        metaRow.addView(modelSelectorButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        android.view.View metaSpacer = new android.view.View(context);
        metaRow.addView(metaSpacer, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        contextText = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams contextParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        metaRow.addView(contextText, contextParams);

        android.view.View divider = new android.view.View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(HORIZONTAL);
        inputRow.setGravity(Gravity.TOP);
        LineTheme.padding(inputRow, LineTheme.SM, LineTheme.SM, LineTheme.SM, 0);
        panel.addView(inputRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        attachButton = new IconButtonView(context, IconButtonView.PLUS);
        attachButton.setIconColor(LineTheme.TEXT_SECONDARY);
        attachButton.setIconSizeDp(40, 22);
        attachButton.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 20));
        attachButton.setOnClickListener(v -> {
            if (!streaming && listener != null) {
                listener.onAttachClick();
            }
        });

        input = new EditText(context);
        input.setTextColor(LineTheme.TEXT);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setHint(context.getString(R.string.composer_hint_default));
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(6);
        input.setMinHeight(LineTheme.dp(context, 68));
        input.setMaxHeight(LineTheme.dp(context, 152));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setIncludeFontPadding(false);
        input.setPadding(LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (!InputSettings.ENTER_SEND.equals(enterKeyBehavior)) {
                return false;
            }
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                return submitCurrentInput();
            }
            if (isPlainEnterDown(event)) {
                return submitCurrentInput();
            }
            return false;
        });
        input.setOnKeyListener((view, keyCode, event) -> {
            if (!InputSettings.ENTER_SEND.equals(enterKeyBehavior)) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER && isPlainEnterDown(event)) {
                return submitCurrentInput();
            }
            return false;
        });
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                if (modePopup != null && modePopup.isShowing()) {
                    modePopup.dismiss();
                }
                if (modelPopup != null && modelPopup.isShowing()) {
                    modelPopup.dismiss();
                }
            } else {
                dismissSlashPopup();
            }
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(input, inputParams);

        sendButton = new IconButtonView(context, IconButtonView.ARROW_UP);
        sendButton.setIconSizeDp(40, 22);
        sendButton.setOnClickListener(v -> {
            if (streaming) {
                if (listener != null) {
                    listener.onStop();
                }
                return;
            }
            submitCurrentInput();
        });

        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setOrientation(HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(modeRow, LineTheme.SM, 0, LineTheme.SM, LineTheme.SM);
        panel.addView(modeRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams attachParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        attachParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(attachButton, attachParams);

        modeSelectorButton = new LinearLayout(context);
        modeSelectorButton.setOrientation(HORIZONTAL);
        modeSelectorButton.setGravity(Gravity.CENTER_VERTICAL);
        modeSelectorButton.setClickable(true);
        modeSelectorButton.setFocusable(true);
        modeSelectorButton.setOnClickListener(v -> showModePopup(modeSelectorButton));
        LineTheme.padding(modeSelectorButton, LineTheme.SM, 0, LineTheme.XS, 0);
        modeSelectorText = LineTheme.textMedium(context, modeLabel(chatMode), LineTheme.FONT_XS, LineTheme.TEXT);
        modeSelectorText.setGravity(Gravity.CENTER_VERTICAL);
        modeSelectorText.setSingleLine(true);
        modeSelectorButton.addView(modeSelectorText, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        modeSelectorChevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        modeSelectorChevron.setIconColor(LineTheme.TEXT_SECONDARY);
        modeSelectorChevron.setIconSizeDp(18, 13);
        modeSelectorChevron.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        chevronParams.leftMargin = LineTheme.dp(context, 1);
        modeSelectorButton.addView(modeSelectorChevron, chevronParams);
        modeRow.addView(modeSelectorButton, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34)));

        View spacer = new View(context);
        modeRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 40));
        sendParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        modeRow.addView(sendButton, sendParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton();
                updateSlashPopup();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        slashPopup = new SlashCommandPopup(context);
        updateSendButton();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setQuoteDismissListener(QuoteDismissListener listener) {
        this.quoteDismissListener = listener;
    }

    private void buildQuotePreview() {
        Context context = getContext();
        quotePreviewLayout = new LinearLayout(context);
        quotePreviewLayout.setOrientation(HORIZONTAL);
        quotePreviewLayout.setGravity(Gravity.CENTER_VERTICAL);
        quotePreviewLayout.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(quotePreviewLayout, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        View quoteBar = new View(context);
        quoteBar.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 2));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 3), LayoutParams.MATCH_PARENT);
        barParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        quotePreviewLayout.addView(quoteBar, barParams);

        quotePreviewText = LineTheme.text(context, "", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        quotePreviewText.setMaxLines(2);
        quotePreviewText.setEllipsize(TextUtils.TruncateAt.END);
        quotePreviewLayout.addView(quotePreviewText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        quoteCloseButton = new IconButtonView(context, IconButtonView.CLOSE);
        quoteCloseButton.setIconColor(LineTheme.TEXT_TERTIARY);
        quoteCloseButton.setIconSizeDp(28, 16);
        quoteCloseButton.setOnClickListener(v -> {
            hideQuote();
            if (quoteDismissListener != null) {
                quoteDismissListener.onQuoteDismissed();
            }
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 28), LineTheme.dp(context, 28));
        closeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        quotePreviewLayout.addView(quoteCloseButton, closeParams);

        quotePreviewLayout.setVisibility(GONE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        addView(quotePreviewLayout, params);
    }

    @Override
    public void showQuote(String previewText) {
        if (quotePreviewLayout == null) {
            return;
        }
        quotePreviewText.setText(previewText == null ? "" : previewText);
        quotePreviewLayout.setVisibility(VISIBLE);
    }

    @Override
    public void hideQuote() {
        if (quotePreviewLayout == null) {
            return;
        }
        quotePreviewLayout.setVisibility(GONE);
    }

    public void setDraft(String text) {
        setDraft(text, Collections.emptyList());
    }

    public void setDraft(String text, List<InputAttachment> nextAttachments) {
        String value = text == null ? "" : text;
        input.setText(value);
        attachments.clear();
        if (nextAttachments != null) {
            attachments.addAll(nextAttachments);
        }
        renderAttachments();
        input.setSelection(input.getText().length());
        input.requestFocus();
        updateSendButton();
    }

    public List<InputAttachment> getAttachments() {
        return Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public List<String> selectedAttachmentPaths(String source) {
        String normalizedSource = InputAttachment.SOURCE_SSH.equals(source)
                ? InputAttachment.SOURCE_SSH
                : InputAttachment.SOURCE_LOCAL;
        ArrayList<String> paths = new ArrayList<>();
        for (InputAttachment attachment : attachments) {
            if (attachment.getSource().equals(normalizedSource)) {
                paths.add(attachment.getPath());
            }
        }
        return paths;
    }

    public void toggleAttachment(InputAttachment attachment) {
        if (attachment == null || attachment.getPath().length() == 0) {
            return;
        }
        for (int i = 0; i < attachments.size(); i++) {
            InputAttachment existing = attachments.get(i);
            if (existing.matches(attachment.getPath(), attachment.getSource())) {
                attachments.remove(i);
                renderAttachments();
                updateSendButton();
                return;
            }
        }
        attachments.add(attachment);
        renderAttachments();
        updateSendButton();
    }

    public void render(ChatUiState state) {
        streaming = state.isStreaming();
        modelText.setText(state.getModelLabel());
        selectedModelId = state.getSelectedModelId();
        availableModels = state.getAvailableModels();
        contextText.setText(state.getContextLabel());
        contextText.setTextColor(state.getContextPercent() >= 80 ? LineTheme.WARNING : LineTheme.TEXT_TERTIARY);
        chatMode = state.getChatMode();
        updateEnterKeyBehavior(state.getEnterKeyBehavior());
        if (streaming && modePopup != null) {
            modePopup.dismiss();
        }
        if (streaming && modelPopup != null) {
            modelPopup.dismiss();
        }
        if (streaming) {
            dismissSlashPopup();
        } else {
            updateSlashPopup();
        }
        input.setEnabled(!streaming);
        attachButton.setEnabled(!streaming);
        attachButton.setAlpha(streaming ? 0.62f : 1f);
        input.setHint(state.hasConfiguredModel()
                ? getContext().getString(R.string.composer_hint_default)
                : getContext().getString(R.string.composer_hint_no_model));
        updateModeButtons();
        updateModelSelector();
        updateSendButton();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        borderPaint.setColor(LineTheme.BORDER);
        borderPaint.setStrokeWidth(1f);
        canvas.drawLine(0, 0, getWidth(), 0, borderPaint);
    }

    private void updateSendButton() {
        boolean hasContent = canSend();
        if (streaming) {
            sendButton.setIconType(IconButtonView.STOP);
            sendButton.setIconColor(LineTheme.TEXT_ON_COLOR);
            sendButton.setIconSizeDp(40, 18);
            sendButton.setBackground(LineTheme.rounded(getContext(), LineTheme.DANGER, 20));
        } else {
            sendButton.setIconType(IconButtonView.ARROW_UP);
            sendButton.setIconColor(hasContent ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
            sendButton.setIconSizeDp(40, 22);
            sendButton.setBackground(LineTheme.rounded(getContext(), hasContent ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 20));
        }
        sendButton.setEnabled(streaming || hasContent);
        sendButton.setAlpha(sendButton.isEnabled() ? 1f : 0.72f);
    }

    private boolean canSend() {
        return input.getText().toString().trim().length() > 0 || !attachments.isEmpty();
    }

    private boolean submitCurrentInput() {
        if (streaming || !canSend()) {
            return true;
        }
        String text = input.getText().toString();
        SlashCommandCatalog.Parsed parsed = SlashCommandCatalog.parse(text);
        if (parsed != null) {
            if (listener == null) {
                input.setText("");
                clearAttachments();
                dismissSlashPopup();
                return true;
            }
            if (parsed.kind == SlashCommandCatalog.Kind.MODE) {
                listener.onModeChanged(parsed.mode);
            } else if (parsed.kind == SlashCommandCatalog.Kind.MODEL) {
                listener.onModelQuickSwitch(parsed.modelId);
                if (parsed.reasoningEffort != null) {
                    listener.onAiReasoningEffortChanged(parsed.reasoningEffort);
                }
            }
            input.setText("");
            clearAttachments();
            dismissSlashPopup();
            return true;
        }
        if (listener != null) {
            listener.onSend(text, getAttachments());
        }
        input.setText("");
        clearAttachments();
        return true;
    }

    private boolean isPlainEnterDown(KeyEvent event) {
        return event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && !event.isShiftPressed();
    }

    private void updateEnterKeyBehavior(String behavior) {
        enterKeyBehavior = InputSettings.normalizeEnterKeyBehavior(behavior);
        input.setImeOptions(InputSettings.ENTER_SEND.equals(enterKeyBehavior)
                ? EditorInfo.IME_ACTION_SEND
                : EditorInfo.IME_ACTION_NONE);
    }

    private void clearAttachments() {
        attachments.clear();
        renderAttachments();
        updateSendButton();
    }

    private void renderAttachments() {
        attachmentList.removeAllViews();
        if (attachments.isEmpty()) {
            attachmentScroll.setVisibility(GONE);
            return;
        }
        attachmentScroll.setVisibility(VISIBLE);
        for (InputAttachment attachment : attachments) {
            attachmentList.addView(attachmentChip(attachment));
        }
    }

    private View attachmentChip(InputAttachment attachment) {
        Context context = getContext();
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 17, LineTheme.BORDER));
        LineTheme.padding(chip, LineTheme.MD, 0, LineTheme.SM, 0);

        TextView name = LineTheme.textMedium(context, attachment.getName(), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setMaxWidth(LineTheme.dp(context, 170));
        chip.addView(name, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        IconButtonView remove = new IconButtonView(context, IconButtonView.CLOSE);
        remove.setContentDescription(getContext().getString(R.string.composer_attachment_remove_desc));
        remove.setIconColor(LineTheme.TEXT_TERTIARY);
        remove.setIconSizeDp(18, 12);
        remove.setOnClickListener(v -> {
            attachments.remove(attachment);
            renderAttachments();
            updateSendButton();
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
        removeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        chip.addView(remove, removeParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34));
        params.rightMargin = LineTheme.dp(context, LineTheme.SM);
        chip.setLayoutParams(params);
        return chip;
    }

    private void updateSlashPopup() {
        if (slashPopup == null) {
            return;
        }
        if (streaming) {
            dismissSlashPopup();
            return;
        }
        String text = input.getText() == null ? "" : input.getText().toString();
        SlashState state = resolveSlashState(text);
        if (state == null) {
            dismissSlashPopup();
            return;
        }
        String signature = state.signature + "|" + chatMode;
        if (signature.equals(lastSlashSignature) && slashPopup.isShowing()) {
            return;
        }
        lastSlashSignature = signature;
        List<SlashCommandPopup.Row> rows = buildSlashRows(state);
        if (rows.isEmpty()) {
            dismissSlashPopup();
            return;
        }
        slashPopup.setSelectedIndex(state.selectedIndex);
        slashPopup.show(state.title, rows);
        slashPopup.showAtAnchor(this);
    }

    private void dismissSlashPopup() {
        if (slashPopup != null) {
            slashPopup.dismiss();
        }
        lastSlashSignature = null;
    }

    /**
     * 把当前输入文本解析为 slash popup 状态。返回 null 表示不应展示 popup。
     * 三种状态：主命令（MAIN）、模型 id（MODEL_ID）、思考等级（REASONING）。
     */
    private SlashState resolveSlashState(String text) {
        if (text == null || text.length() == 0) {
            return null;
        }
        if (text.charAt(0) != '/') {
            return null;
        }
        String[] tokens = text.split("\\s+", -1);
        String head = tokens[0].toLowerCase();
        if ("/model".equals(head)) {
            if (tokens.length < 2 || tokens[1].length() == 0) {
                return modelIdState("");
            }
            String modelId = tokens[1];
            if (!containsModelId(modelId)) {
                return mainState("");
            }
            String reasonQuery = tokens.length >= 3 ? tokens[2] : "";
            return reasoningState(modelId, reasonQuery);
        }
        return mainState(head.substring(1));
    }

    private boolean containsModelId(String id) {
        for (ModelConfig model : availableModels) {
            if (model != null && model.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private SlashState mainState(String query) {
        List<SlashCommandCatalog.Definition> defs = SlashCommandCatalog.filterMain(query);
        int selected = -1;
        for (int i = 0; i < defs.size(); i++) {
            SlashCommandCatalog.Definition def = defs.get(i);
            if (def.kind == SlashCommandCatalog.Kind.MODE && def.token.substring(1).equalsIgnoreCase(chatMode)) {
                selected = i;
                break;
            }
        }
        return new SlashState(SlashState.Kind.MAIN,
                getContext().getString(R.string.slash_command_main_title),
                defs, query, selected, null,
                Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    private SlashState modelIdState(String query) {
        List<String> ids = new ArrayList<>();
        for (ModelConfig model : availableModels) {
            if (model != null) {
                ids.add(model.getId());
            }
        }
        List<String> filtered = SlashCommandCatalog.filterModelIds(ids, query);
        int selected = -1;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).equals(selectedModelId)) {
                selected = i;
                break;
            }
        }
        return new SlashState(SlashState.Kind.MODEL_ID,
                getContext().getString(R.string.slash_command_model_title),
                Collections.<SlashCommandCatalog.Definition>emptyList(), query, selected, null,
                filtered, Collections.<String>emptyList());
    }

    private SlashState reasoningState(String modelId, String query) {
        List<String> levels = SlashCommandCatalog.filterReasoningLevels(query);
        return new SlashState(SlashState.Kind.REASONING,
                getContext().getString(R.string.slash_command_reasoning_title, modelId),
                Collections.<SlashCommandCatalog.Definition>emptyList(), query, -1, modelId,
                Collections.<String>emptyList(), levels);
    }

    private List<SlashCommandPopup.Row> buildSlashRows(SlashState state) {
        List<SlashCommandPopup.Row> rows = new ArrayList<>();
        if (state.kind == SlashState.Kind.MAIN) {
            for (SlashCommandCatalog.Definition def : state.definitions) {
                String label = def.token;
                String description = mainDescription(def.token);
                Runnable action = () -> applyMainSelection(def);
                rows.add(new SlashCommandPopup.Row(label, description, action));
            }
        } else if (state.kind == SlashState.Kind.MODEL_ID) {
            for (String id : state.modelIds) {
                String label = id;
                String description = modelDescription(id);
                Runnable action = () -> applyModelIdSelection(id);
                rows.add(new SlashCommandPopup.Row(label, description, action));
            }
        } else {
            for (String level : state.levels) {
                String description = reasoningDescription(level);
                Runnable action = () -> applyReasoningSelection(state.modelId, level);
                rows.add(new SlashCommandPopup.Row(level, description, action));
            }
        }
        return rows;
    }

    private String modelDescription(String id) {
        for (ModelConfig model : availableModels) {
            if (model != null && model.getId().equals(id)) {
                String name = model.getName();
                if (name.length() > 0 && !name.equals(id)) {
                    return name;
                }
                return model.getProviderLabel();
            }
        }
        return "";
    }

    private String mainDescription(String token) {
        Context ctx = getContext();
        if ("/chat".equals(token)) {
            return ctx.getString(R.string.slash_command_chat_desc);
        }
        if ("/plan".equals(token)) {
            return ctx.getString(R.string.slash_command_plan_desc);
        }
        if ("/agent".equals(token)) {
            return ctx.getString(R.string.slash_command_agent_desc);
        }
        if ("/control".equals(token)) {
            return ctx.getString(R.string.slash_command_control_desc);
        }
        if ("/model".equals(token)) {
            return ctx.getString(R.string.slash_command_model_desc);
        }
        return "";
    }

    private String reasoningDescription(String level) {
        Context ctx = getContext();
        if (AiBehaviorSettings.REASONING_OFF.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_off_desc);
        }
        if (AiBehaviorSettings.REASONING_LOW.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_low_desc);
        }
        if (AiBehaviorSettings.REASONING_MEDIUM.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_medium_desc);
        }
        if (AiBehaviorSettings.REASONING_HIGH.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_high_desc);
        }
        if (AiBehaviorSettings.REASONING_MAX.equals(level)) {
            return ctx.getString(R.string.slash_command_reasoning_max_desc);
        }
        return level;
    }

    private void applyMainSelection(SlashCommandCatalog.Definition def) {
        String replacement;
        if (def.kind == SlashCommandCatalog.Kind.MODEL) {
            replacement = "/model ";
        } else {
            replacement = def.token + " ";
        }
        input.setText(replacement);
        input.setSelection(replacement.length());
        lastSlashSignature = null;
    }

    private void applyModelIdSelection(String modelId) {
        String replacement = "/model " + modelId + " ";
        input.setText(replacement);
        input.setSelection(replacement.length());
        lastSlashSignature = null;
    }

    private void applyReasoningSelection(String modelId, String level) {
        String replacement = "/model " + modelId + " " + level;
        input.setText(replacement);
        input.setSelection(replacement.length());
        lastSlashSignature = null;
    }

    private static final class SlashState {
        enum Kind { MAIN, MODEL_ID, REASONING }
        final Kind kind;
        final String title;
        final List<SlashCommandCatalog.Definition> definitions;
        final List<String> modelIds;
        final List<String> levels;
        final String query;
        final int selectedIndex;
        final String modelId;
        final String signature;

        SlashState(Kind kind, String title,
                   List<SlashCommandCatalog.Definition> definitions,
                   String query, int selectedIndex, String modelId,
                   List<String> modelIds, List<String> levels) {
            this.kind = kind;
            this.title = title;
            this.definitions = definitions == null ? Collections.<SlashCommandCatalog.Definition>emptyList() : definitions;
            this.modelIds = modelIds == null ? Collections.<String>emptyList() : modelIds;
            this.levels = levels == null ? Collections.<String>emptyList() : levels;
            this.query = query == null ? "" : query;
            this.selectedIndex = selectedIndex;
            this.modelId = modelId == null ? "" : modelId;
            this.signature = kind.name() + ":" + this.query + ":" + this.modelId + ":"
                    + this.definitions.size() + ":" + this.modelIds.size() + ":" + this.levels.size();
        }
    }

    private void updateModeButtons() {
        modeSelectorText.setText(modeLabel(chatMode));
        modeSelectorText.setTextColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT);
        modeSelectorChevron.setIconColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT_SECONDARY);
        modeSelectorButton.setBackground(LineTheme.rounded(getContext(), LineTheme.INPUT_BG, 17));
        modeSelectorButton.setEnabled(!streaming);
        modeSelectorButton.setAlpha(streaming ? 0.62f : 1f);
    }

    private void updateModelSelector() {
        modelSelectorButton.setEnabled(!streaming);
        modelSelectorButton.setAlpha(streaming ? 0.62f : 1f);
        modelChevron.setIconColor(streaming ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT_SECONDARY);
    }

    private void showModelPopup(View anchor) {
        if (streaming) return;
        dismissSlashPopup();
        input.clearFocus();
        if (modelPopup != null && modelPopup.isShowing()) { modelPopup.dismiss(); return; }
        Context ctx = getContext();
        int popupWidth = LineTheme.dp(ctx, 240);
        int rowHeight = LineTheme.dp(ctx, 38);
        int separatorHeight = LineTheme.dp(ctx, 1);
        int manageRowHeight = LineTheme.dp(ctx, 36);
        int modelCount = availableModels.size();
        int visibleRows = Math.min(modelCount, 8);
        int popupHeight = rowHeight * visibleRows + separatorHeight + manageRowHeight + LineTheme.dp(ctx, 6);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(ctx, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        if (modelCount > 8) {
            android.widget.ScrollView sw = new android.widget.ScrollView(ctx);
            LinearLayout sc = new LinearLayout(ctx);
            sc.setOrientation(VERTICAL);
            for (int i = 0; i < modelCount; i++) {
                ModelConfig m = availableModels.get(i);
                sc.addView(modelOptionRow(ctx, m, m.getId().equals(selectedModelId)),
                        new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
            }
            sw.addView(sc, new android.widget.ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            content.addView(sw, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight * visibleRows));
        } else {
            for (int i = 0; i < modelCount; i++) {
                ModelConfig m = availableModels.get(i);
                content.addView(modelOptionRow(ctx, m, m.getId().equals(selectedModelId)),
                        new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
            }
        }
        View divider = new View(ctx);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        content.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, separatorHeight));
        TextView manageItem = LineTheme.textMedium(ctx, "\u2699 \u7ba1\u7406\u6a21\u578b...", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY);
        manageItem.setGravity(Gravity.CENTER_VERTICAL);
        manageItem.setSingleLine(true);
        manageItem.setPadding(LineTheme.dp(ctx, LineTheme.MD), 0, LineTheme.dp(ctx, LineTheme.MD), 0);
        manageItem.setClickable(true);
        manageItem.setOnClickListener(v -> {
            if (modelPopup != null) modelPopup.dismiss();
            post(() -> {
                if (listener != null) listener.onModelManageClick();
            });
        });
        content.addView(manageItem, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, manageRowHeight));
        modelPopup = new PopupWindow(content, popupWidth, popupHeight, true);
        modelPopup.setOutsideTouchable(true);
        modelPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int centeredX = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int popupX = Math.max(LineTheme.dp(ctx, LineTheme.SM), Math.min(centeredX, screenWidth - popupWidth - LineTheme.dp(ctx, LineTheme.SM)));
        modelPopup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, Math.max(0, location[1] - popupHeight - LineTheme.dp(ctx, 8)));
    }

    private LinearLayout modelOptionRow(Context ctx, ModelConfig model, boolean selected) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LineTheme.dp(ctx, LineTheme.MD), 0, LineTheme.dp(ctx, LineTheme.MD), 0);
        row.setBackground(LineTheme.rounded(ctx, selected ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 11));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (modelPopup != null) modelPopup.dismiss();
            final String mid = model.getId();
            post(() -> {
                if (!mid.equals(selectedModelId) && listener != null) {
                    listener.onModelQuickSwitch(mid);
                }
            });
        });
        View dot = new View(ctx);
        dot.setBackground(LineTheme.rounded(ctx, selected ? LineTheme.TEXT_ON_COLOR : LineTheme.BORDER, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(LineTheme.dp(ctx, 7), LineTheme.dp(ctx, 7));
        dotParams.rightMargin = LineTheme.dp(ctx, LineTheme.SM);
        row.addView(dot, dotParams);
        String displayName = model.getName().length() > 0 ? model.getName() : model.getModelId();
        TextView name = LineTheme.textMedium(ctx, displayName, LineTheme.FONT_SM, selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView provider = LineTheme.text(ctx, model.getProviderLabel(), LineTheme.FONT_XS, selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pp.leftMargin = LineTheme.dp(ctx, LineTheme.SM);
        row.addView(provider, pp);
        return row;
    }

    private void showModePopup(View anchor) {
        if (streaming) {
            return;
        }
        dismissSlashPopup();
        if (modePopup != null && modePopup.isShowing()) {
            modePopup.dismiss();
            return;
        }
        Context context = getContext();
        int popupWidth = LineTheme.dp(context, 112);
        int rowHeight = LineTheme.dp(context, 38);
        int popupHeight = rowHeight * 4 + LineTheme.dp(context, 6);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 3, 3, 3, 3);
        content.addView(modeOption(context, "Chat", ChatMode.CHAT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Plan", ChatMode.PLAN), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "Agent", ChatMode.AGENT), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        content.addView(modeOption(context, "\u63a7\u5236", ChatMode.CONTROL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        modePopup = new PopupWindow(content, popupWidth, popupHeight, true);
        modePopup.setOutsideTouchable(true);
        modePopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int centeredX = location[0] + (anchor.getWidth() - popupWidth) / 2;
        int popupX = Math.max(LineTheme.dp(context, LineTheme.SM),
                Math.min(centeredX, screenWidth - popupWidth - LineTheme.dp(context, LineTheme.SM)));
        modePopup.showAtLocation(this, Gravity.NO_GRAVITY, popupX, Math.max(0, location[1] - popupHeight - LineTheme.dp(context, 8)));
    }

    private TextView modeOption(Context context, String label, String mode) {
        boolean selected = mode.equals(chatMode);
        TextView item = LineTheme.textMedium(context, label, LineTheme.FONT_SM,
                selected ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setPadding(LineTheme.dp(context, LineTheme.MD), 0, LineTheme.dp(context, LineTheme.MD), 0);
        item.setBackground(LineTheme.rounded(context, selected ? LineTheme.ACCENT : android.graphics.Color.TRANSPARENT, 11));
        item.setClickable(true);
        item.setOnClickListener(v -> {
            if (modePopup != null) {
                modePopup.dismiss();
            }
            if (!mode.equals(chatMode) && listener != null) {
                listener.onModeChanged(mode);
            }
        });
        return item;
    }

    private String modeLabel(String mode) {
        if (ChatMode.CHAT.equals(mode)) {
            return "Chat";
        }
        if (ChatMode.PLAN.equals(mode)) {
            return "Plan";
        }
        if (ChatMode.CONTROL.equals(mode)) {
            return "\u63a7\u5236";
        }
        return "Agent";
    }
}
