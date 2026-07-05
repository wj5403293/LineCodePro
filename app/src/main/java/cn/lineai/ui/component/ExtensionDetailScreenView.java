package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.mvp.ExtensionKindDescriptor;
import cn.lineai.mvp.ExtensionKindRegistry;
import cn.lineai.mvp.ExtensionItem;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.SkillRecord;
import cn.lineai.ui.MainChatView;
import cn.lineai.ui.theme.LineTheme;
import java.util.List;

public final class ExtensionDetailScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onAddAgent();

        void onEditAgent(String id);

        void onAddMcp();

        void onEditMcp(String id);

        void onCreateSkill(String location, String name, String description, String content);

        void onInstallSkill(String location, String sourcePath, String name);

        void onInstallSkillFromUri(String location, String uri, String displayName);

        void onEnabledChanged(String kind, String id, boolean enabled);

        void onDelete(String kind, String id);
    }

    private final String kind;
    private final ExtensionOverviewState state;
    private final Listener listener;
    private final ExtensionKindDescriptor descriptor;

    public ExtensionDetailScreenView(Context context, String kind, ExtensionOverviewState state, Listener listener) {
        super(context, titleFor(context, kind), listener::onBack, addButton(context, kind, listener));
        this.kind = kind == null ? "" : kind;
        this.state = state == null ? new ExtensionOverviewState(null, null, null) : state;
        this.listener = listener;
        this.descriptor = ExtensionKindRegistry.getInstance().get(this.kind);
        getRightAction().setOnClickListener(v -> handleAdd());
        LinearLayout content = getContent();
        LineTheme.padding(content, 0, 0, 0, 100);

        String sectionTitle = descriptor != null ? descriptor.sectionTitle(context) : context.getString(R.string.screen_extension_detail_section_install_other);
        int sectionIcon = descriptor != null ? descriptor.iconType() : IconButtonView.PACKAGE;
        String sectionInlineTitle = descriptor != null ? descriptor.inlineTitle(context) : context.getString(R.string.screen_extension_detail_inline_title_linecode);
        String sectionInlineDesc = descriptor != null ? descriptor.inlineDesc(context) : context.getString(R.string.screen_extension_detail_inline_desc_linecode);

        SettingsSectionView add = new SettingsSectionView(context, sectionTitle);
        add.addRow(new ActionRowView(context, sectionIcon, sectionInlineTitle, sectionInlineDesc, false, true, this::handleAdd), false);
        content.addView(add, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView installed = new SettingsSectionView(context, context.getString(R.string.screen_extension_detail_section_installed));
        renderInstalled(installed);
        content.addView(installed, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void handleAdd() {
        if (descriptor != null) {
            switch (descriptor.addActionType()) {
                case ExtensionKindDescriptor.ADD_ACTION_AGENT:
                    listener.onAddAgent();
                    return;
                case ExtensionKindDescriptor.ADD_ACTION_MCP:
                    listener.onAddMcp();
                    return;
                case ExtensionKindDescriptor.ADD_ACTION_SKILL:
                    showSkillActions();
                    return;
                default:
                    break;
            }
        }
        Toast.makeText(getContext(), getContext().getString(R.string.screen_extension_detail_empty_linecode), Toast.LENGTH_SHORT).show();
    }

    private void renderInstalled(SettingsSectionView installed) {
        if (descriptor == null) {
            installed.addRow(empty(getContext().getString(R.string.screen_extension_detail_empty_linecode)), false);
            return;
        }
        List<ExtensionItem> items = descriptor.getInstalledItems(state);
        if (items.isEmpty()) {
            installed.addRow(empty(descriptor.emptyMessage(getContext())), false);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            ExtensionItem item = items.get(i);
            installed.addRow(extensionRow(descriptor.kind(), item.getId(), descriptor.iconType(), item.getName(),
                    item.getDescription(), item.isEnabled()), i < items.size() - 1);
        }
    }

    private LinearLayout extensionRow(String rowKind, String id, int iconType, String title, String desc, boolean enabled) {
        SwitchRowView row = new SwitchRowView(getContext(), iconType, title, desc, enabled,
                (button, checked) -> listener.onEnabledChanged(rowKind, id, checked));
        row.setOnLongClickListener(v -> {
            confirmDelete(rowKind, id, title);
            return true;
        });
        return row;
    }

    private TextView empty(String text) {
        TextView view = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        LineTheme.padding(view, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return view;
    }

    private void showSkillActions() {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_sheet_title_skills));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_install_zip), getContext().getString(R.string.screen_extension_detail_install_zip_desc), () -> {
            dialog.dismiss();
            chooseSkillDocument();
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_create_skill), getContext().getString(R.string.screen_extension_detail_create_skill_desc), () -> {
            dialog.dismiss();
            showCreateSkillDialog();
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_install_path), getContext().getString(R.string.screen_extension_detail_install_path_desc), () -> {
            dialog.dismiss();
            showInstallSkillDialog();
        });
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void chooseSkillDocument() {
        Context context = getContext();
        if (!(context instanceof MainChatView.WorkspaceHost)) {
            showInstallSkillDialog();
            return;
        }
        ((MainChatView.WorkspaceHost) context).openDocumentPicker("*/*", new String[] {"skill.zip"}, new MainChatView.DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                String lower = (displayName == null ? "" : displayName).toLowerCase(java.util.Locale.ROOT);
                if (!lower.endsWith(".zip") && !lower.endsWith(".md")) {
                    Toast.makeText(getContext(), getContext().getString(R.string.screen_extension_detail_pick_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                showInstallTargetDialog(uri, displayName);
            }

            @Override
            public void onDocumentPickCancelled() {
            }
        });
    }

    private void showInstallTargetDialog(String uri, String displayName) {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_sheet_title_install_target));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_target_project), getContext().getString(R.string.screen_extension_detail_target_project_desc), () -> {
            dialog.dismiss();
            listener.onInstallSkillFromUri(SkillRecord.LOCATION_PROJECT, uri, displayName);
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_target_global), getContext().getString(R.string.screen_extension_detail_target_global_desc), () -> {
            dialog.dismiss();
            listener.onInstallSkillFromUri(SkillRecord.LOCATION_APP, uri, displayName);
        });
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void showCreateSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel(getContext().getString(R.string.screen_extension_detail_create_skill));
        FormTextFieldView name = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_name), "", "android-native-view", null, false, false);
        FormTextFieldView desc = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_desc), "", getContext().getString(R.string.screen_extension_detail_hint_skill_desc), null, false, false);
        FormTextFieldView body = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_content), "", getContext().getString(R.string.screen_extension_detail_hint_content), null, true, false);
        RadioGroup scope = locationGroup();
        panel.addView(name);
        panel.addView(desc, top());
        panel.addView(body, top());
        panel.addView(scope, top());
        panel.addView(actionButton(getContext().getString(R.string.common_create), () -> {
            listener.onCreateSkill(checkedLocation(scope), value(name), value(desc), value(body));
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private void showInstallSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel(getContext().getString(R.string.screen_extension_detail_dialog_install_skill));
        String downloadExample = Environment.getExternalStorageDirectory().getPath() + "/Download/skill.zip";
        FormTextFieldView path = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_source_path), "", downloadExample, getContext().getString(R.string.screen_extension_detail_helper_source_path), false, false);
        FormTextFieldView name = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_name_optional), "", getContext().getString(R.string.screen_extension_detail_hint_optional_name), null, false, false);
        RadioGroup scope = locationGroup();
        panel.addView(path);
        panel.addView(name, top());
        panel.addView(scope, top());
        panel.addView(actionButton(getContext().getString(R.string.common_install), () -> {
            listener.onInstallSkill(checkedLocation(scope), value(path), value(name));
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private RadioGroup locationGroup() {
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton project = locationButton(getContext().getString(R.string.screen_extension_detail_position_project), SkillRecord.LOCATION_PROJECT);
        RadioButton app = locationButton(getContext().getString(R.string.screen_extension_detail_position_global), SkillRecord.LOCATION_APP);
        group.addView(project, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        group.addView(app, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        project.setChecked(true);
        return group;
    }

    private RadioButton locationButton(String label, String location) {
        RadioButton button = new RadioButton(getContext());
        button.setId(android.view.View.generateViewId());
        button.setText(label);
        button.setTag(location);
        button.setTextColor(LineTheme.TEXT);
        button.setTextSize(LineTheme.FONT_SM);
        return button;
    }

    private String checkedLocation(RadioGroup group) {
        android.view.View checked = group.findViewById(group.getCheckedRadioButtonId());
        Object tag = checked == null ? null : checked.getTag();
        return tag == null ? SkillRecord.LOCATION_PROJECT : String.valueOf(tag);
    }

    private TextView actionButton(String title, Runnable action) {
        TextView view = LineTheme.textMedium(getContext(), title, LineTheme.FONT_MD, LineTheme.ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 8));
        LineTheme.padding(view, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void confirmDelete(String rowKind, String id, String title) {
        ExtensionKindDescriptor d = ExtensionKindRegistry.getInstance().get(rowKind);
        if (d != null && d.hasModifyAction()) {
            Dialog dialog = createBottomDialog();
            LinearLayout panel = createBottomPanel();
            addHandle(panel);
            addSheetTitle(panel, title);
            addDivider(panel);
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_modify), getContext().getString(R.string.screen_extension_detail_modify_desc), () -> {
                dialog.dismiss();
                if (d.addActionType() == ExtensionKindDescriptor.ADD_ACTION_AGENT) {
                    listener.onEditAgent(id);
                } else {
                    listener.onEditMcp(id);
                }
            });
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_delete), getContext().getString(R.string.screen_extension_detail_delete_desc), () -> {
                dialog.dismiss();
                confirmDeleteOnly(rowKind, id, title);
            }, LineTheme.DANGER);
            addBottomInset(panel);
            showBottomDialog(dialog, panel);
            return;
        }
        confirmDeleteOnly(rowKind, id, title);
    }

    private void confirmDeleteOnly(String rowKind, String id, String title) {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_delete_title));
        TextView desc = LineTheme.text(getContext(), getContext().getString(R.string.screen_extension_detail_delete_confirm, title), LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.common_cancel), "", dialog::dismiss);
        addActionRow(panel, getContext().getString(R.string.common_delete), getContext().getString(R.string.screen_extension_detail_delete_confirm_desc), () -> {
            dialog.dismiss();
            listener.onDelete(rowKind, id);
        }, LineTheme.DANGER);
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private Dialog createDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        panel.addView(LineTheme.textMedium(getContext(), title, LineTheme.FONT_LG, LineTheme.TEXT));
        return panel;
    }

    private void showPanel(Dialog dialog, LinearLayout panel) {
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawableResource(android.R.color.transparent);
                shown.setLayout(insetDialogWidth(), LayoutParams.WRAP_CONTENT);
            }
        });
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private int insetDialogWidth() {
        int width = getResources().getDisplayMetrics().widthPixels - LineTheme.dp(getContext(), 32);
        return Math.max(LineTheme.dp(getContext(), 280), width);
    }

    private Dialog createBottomDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private LinearLayout createBottomPanel() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        return panel;
    }

    private void showBottomDialog(Dialog dialog, LinearLayout panel) {
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
    }

    private void addHandle(LinearLayout panel) {
        View handle = new View(panel.getContext());
        handle.setBackground(LineTheme.rounded(panel.getContext(), LineTheme.TEXT_TERTIARY, 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(panel.getContext(), 36), LineTheme.dp(panel.getContext(), 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = LineTheme.dp(panel.getContext(), LineTheme.SM);
        params.bottomMargin = LineTheme.dp(panel.getContext(), LineTheme.XS);
        panel.addView(handle, params);
    }

    private void addSheetTitle(LinearLayout panel, String title) {
        TextView titleView = LineTheme.text(panel.getContext(), title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(titleView, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addDivider(LinearLayout panel) {
        View divider = new View(panel.getContext());
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
    }

    private void addActionRow(LinearLayout panel, String label, String desc, Runnable action) {
        addActionRow(panel, label, desc, action, LineTheme.TEXT);
    }

    private void addActionRow(LinearLayout panel, String label, String desc, Runnable action, int labelColor) {
        Context context = panel.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(LineTheme.text(context, label, LineTheme.FONT_MD, labelColor, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (desc != null && desc.length() > 0) {
            TextView descView = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            labels.addView(descView, descParams);
        }
        panel.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addBottomInset(LinearLayout panel) {
        panel.addView(new View(panel.getContext()), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(panel.getContext(), 34)));
    }

    private LinearLayout.LayoutParams top() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        return params;
    }

    private String value(FormTextFieldView field) {
        return field == null ? "" : field.getInput().getText().toString().trim();
    }

    private static IconButtonView addButton(Context context, String kind, Listener listener) {
        IconButtonView button = new IconButtonView(context, IconButtonView.PLUS);
        button.setIconColor(LineTheme.ACCENT);
        button.setIconSizeDp(36, 19);
        button.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 18));
        ExtensionKindDescriptor d = ExtensionKindRegistry.getInstance().get(kind);
        button.setOnClickListener(v -> {
            if (d == null) return;
            switch (d.addActionType()) {
                case ExtensionKindDescriptor.ADD_ACTION_AGENT:
                    listener.onAddAgent();
                    break;
                case ExtensionKindDescriptor.ADD_ACTION_MCP:
                    listener.onAddMcp();
                    break;
                case ExtensionKindDescriptor.ADD_ACTION_SKILL:
                    Toast.makeText(context, context.getString(R.string.screen_extension_detail_add_button_toast), Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        });
        return button;
    }

    private static String titleFor(Context context, String kind) {
        ExtensionKindDescriptor d = ExtensionKindRegistry.getInstance().get(kind);
        return d != null ? d.title(context) : context.getString(R.string.screen_extensions_section_linecode);
    }
}
