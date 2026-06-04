package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;

public final class DataSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public DataSettingsScreenView(Context context, Listener listener) {
        super(context, "数据管理", listener::onBack, null);
        LinearLayout content = getContent();

        SettingsSectionView archive = new SettingsSectionView(context, "全量数据");
        archive.addRow(new ActionRowView(context, IconButtonView.ARCHIVE, "导出所有数据", "导出聊天、设置、配置和 home 目录为 .linecode", false, false, null), true);
        archive.addRow(new ActionRowView(context, IconButtonView.UPLOAD, "导入 .linecode", "恢复完整聊天记录、配置和 home 文件", false, false, null), false);
        content.addView(archive, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
