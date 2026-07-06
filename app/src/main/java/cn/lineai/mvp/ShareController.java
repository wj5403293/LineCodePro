package cn.lineai.mvp;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportFormatResolver;
import cn.lineai.share.ExportResult;
import cn.lineai.share.ShareHelper;
import cn.lineai.share.format.ClipboardFormat;
import java.util.List;

public final class ShareController {

    public interface OnShareListener {
        void onShareFile(ExportResult result);
        void onCopyToClipboard(String text);
        void onShareText(String text);
    }

    private final ExportFormatResolver resolver;
    private OnShareListener listener;

    public ShareController(ExportFormatResolver resolver) {
        this.resolver = resolver;
    }

    public void setListener(OnShareListener listener) {
        this.listener = listener;
    }

    public void showFormatPicker(Context context, List<ChatMessage> selected) {
        String[] names = resolver.getDisplayNames();
        new AlertDialog.Builder(context)
                .setTitle("导出格式")
                .setItems(names, (dialog, which) -> {
                    ExportFormat format = resolver.get(which);
                    ExportResult result = format.execute(context, selected);
                    if (result != null) {
                        handleResult(context, result, format);
                    }
                })
                .show();
    }

    private void handleResult(Context context, ExportResult result, ExportFormat format) {
        switch (result.getAction()) {
            case ExportResult.ACTION_SHARE_FILE:
                ShareHelper.shareFile(context, result.getFile(), result.getMimeType());
                if (listener != null) listener.onShareFile(result);
                break;
            case ExportResult.ACTION_CLIPBOARD:
                String text = result.getContent();
                if (format instanceof ClipboardFormat) {
                    ClipboardFormat clipFormat = (ClipboardFormat) format;
                    if (clipFormat.shouldWarn(text)) {
                        Toast.makeText(context, "内容较长，仅部分可能被复制", Toast.LENGTH_SHORT).show();
                    }
                }
                ShareHelper.copy(context, text);
                if (listener != null) listener.onCopyToClipboard(text);
                break;
            case ExportResult.ACTION_SHARE_TEXT:
                ShareHelper.shareText(context, result.getContent());
                if (listener != null) listener.onShareText(result.getContent());
                break;
        }
    }
}
