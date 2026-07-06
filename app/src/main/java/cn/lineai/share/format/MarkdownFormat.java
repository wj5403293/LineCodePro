package cn.lineai.share.format;

import android.content.Context;
import android.net.Uri;
import cn.lineai.model.ChatMessage;
import cn.lineai.share.ChatMessages;
import cn.lineai.share.ExportFormat;
import cn.lineai.share.ExportResult;
import cn.lineai.share.FileRepository;
import cn.lineai.share.ShareFileProvider;
import java.io.File;
import java.util.List;

public final class MarkdownFormat implements ExportFormat {
    @Override
    public String displayName() { return "Markdown 文件(.md)"; }

    @Override
    public ExportResult execute(Context context, List<ChatMessage> messages) {
        String content = ChatMessages.toMarkdown(messages);
        File file = FileRepository.write(context, "chat_export.md", content);
        Uri uri = ShareFileProvider.uriFor(context, file);
        return ExportResult.forFile(file, "text/markdown", uri);
    }
}
