package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

/**
 * 抽象基类，集中管理工具调用卡片的公共样式与辅助视图构建逻辑。
 * 子类继承后无需重复设置方向与背景，并可直接复用路径滚动、状态图标、消息行等通用方法。
 */
public abstract class BaseToolCallView extends LinearLayout {

    public BaseToolCallView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
    }

    /**
     * 构建一个禁用横向滚动条的 HorizontalScrollView，并将 pathView 装入其中。
     * 用于在卡片头部展示可能超长的文件路径。
     */
    protected HorizontalScrollView horizontalPathScroll(TextView pathView) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(pathView, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /**
     * 返回状态视图：running 时显示不确定进度条，完成时显示 CHECK 图标。
     * 子类如需区分成功/失败图标或自定义颜色，可在调用后调整返回的 IconButtonView。
     */
    protected View statusView(boolean running) {
        if (running) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            return bar;
        }
        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.CHECK);
        icon.setIconColor(LineTheme.SUCCESS);
        icon.setIconSizeDp(18, 13);
        icon.setClickable(false);
        return icon;
    }

    /**
     * 向 parent 追加一条消息行：顶部 1px 分割线 + 横向布局（图标 + 文本）。
     * 用于在卡片底部展示错误信息或运行中输出。
     */
    protected void addMessageRow(LinearLayout parent, int iconType, String text, int color) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        parent.addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LineTheme.padding(row, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(color);
        icon.setIconSizeDp(14, 12);
        icon.setClickable(false);
        row.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 14), LineTheme.dp(getContext(), 14)));

        TextView message = LineTheme.text(getContext(), text, LineTheme.FONT_XS, color, Typeface.NORMAL);
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        row.addView(message, params);

        parent.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
