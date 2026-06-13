package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.data.repository.StorageStatsRepository;
import cn.lineai.ui.theme.LineTheme;

public final class StorageManagementScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onClearDiffCache();
        void onClearChatHistory();
    }

    private final Context context;
    private final StorageStatsRepository repository;
    private final Handler handler;
    private TextView totalSizeView;
    private TextView diffSizeView;
    private TextView diffCountView;
    private TextView chatSizeView;
    private TextView chatCountView;
    private TextView configSizeView;
    private TextView configCountView;
    private TextView homeSizeView;
    private TextView homeCountView;

    public StorageManagementScreenView(Context context, Listener listener) {
        super(context, "存储管理", listener::onBack, refreshButton(context, listener));
        this.context = context;
        this.repository = new StorageStatsRepository(context);
        this.handler = new Handler(Looper.getMainLooper());
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(VERTICAL);
        summary.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(summary, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        TextView label = LineTheme.textMedium(context, "已统计使用量", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY);
        summary.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        totalSizeView = LineTheme.text(context, "计算中...", LineTheme.FONT_XXL, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        summary.addView(totalSizeView, valueParams);
        TextView time = LineTheme.text(context, "实时统计应用数据占用", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        summary.addView(time, timeParams);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        summaryParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(summary, summaryParams);

        LinearLayout diffRow = createStorageRow(IconButtonView.GIT_COMPARE, "Diff 缓存", "工具调用生成的补丁和比较结果");
        diffSizeView = (TextView) ((LinearLayout) diffRow.getChildAt(2)).getChildAt(0);
        diffCountView = (TextView) ((LinearLayout) diffRow.getChildAt(2)).getChildAt(1);
        content.addView(diffRow, createRowParams());

        LinearLayout chatRow = createStorageRow(IconButtonView.MESSAGE_SQUARE, "聊天记录", "对话、消息和索引摘要");
        chatSizeView = (TextView) ((LinearLayout) chatRow.getChildAt(2)).getChildAt(0);
        chatCountView = (TextView) ((LinearLayout) chatRow.getChildAt(2)).getChildAt(1);
        content.addView(chatRow, createRowParams());

        LinearLayout configRow = createStorageRow(IconButtonView.SETTINGS, "配置文件", "模型、主题、MCP 和系统设置");
        configSizeView = (TextView) ((LinearLayout) configRow.getChildAt(2)).getChildAt(0);
        configCountView = (TextView) ((LinearLayout) configRow.getChildAt(2)).getChildAt(1);
        content.addView(configRow, createRowParams());

        LinearLayout homeRow = createStorageRow(IconButtonView.FOLDER, "Home 目录", "项目文件、Skills 和扩展数据");
        homeSizeView = (TextView) ((LinearLayout) homeRow.getChildAt(2)).getChildAt(0);
        homeCountView = (TextView) ((LinearLayout) homeRow.getChildAt(2)).getChildAt(1);
        content.addView(homeRow, createRowParams());

        loadStats();
    }

    private static View refreshButton(Context context, Listener listener) {
        RefreshCwButtonView button = new RefreshCwButtonView(context, 18);
        button.setOnClickListener(v -> listener.onBack());
        return button;
    }

    private LinearLayout createStorageRow(int iconType, String title, String desc) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(row, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);

        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 19));
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(38, 19);
        icon.setClickable(false);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38), Gravity.CENTER));
        row.addView(iconWrap, new LinearLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(text, textParams);
        text.addView(LineTheme.text(context, title, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView descView = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        text.addView(descView, descParams);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(VERTICAL);
        meta.setGravity(Gravity.END);
        TextView sizeView = LineTheme.text(context, "-", LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
        meta.addView(sizeView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView countView = LineTheme.text(context, "-", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        countParams.topMargin = LineTheme.dp(context, 2);
        meta.addView(countView, countParams);
        row.addView(meta, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        return row;
    }

    private LinearLayout.LayoutParams createRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        return params;
    }

    private void loadStats() {
        new Thread(() -> {
            StorageStatsRepository.StorageStats stats = repository.getStats();
            handler.post(() -> updateViews(stats));
        }).start();
    }

    private void updateViews(StorageStatsRepository.StorageStats stats) {
        totalSizeView.setText(stats.formatTotalSize());
        diffSizeView.setText(stats.formatDiffCacheSize());
        diffCountView.setText(stats.diffCacheCount + " 项");
        chatSizeView.setText(stats.formatChatSize());
        chatCountView.setText(stats.chatCount + " 项");
        configSizeView.setText(stats.formatConfigSize());
        configCountView.setText(stats.configCount + " 项");
        homeSizeView.setText(stats.formatHomeSize());
        homeCountView.setText(stats.homeCount + " 项");
    }

    public void refresh() {
        loadStats();
    }
}