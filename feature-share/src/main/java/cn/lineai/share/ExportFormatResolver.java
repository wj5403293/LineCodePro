package cn.lineai.share;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExportFormatResolver {
    private final List<ExportFormat> formats = new ArrayList<>();

    public ExportFormatResolver() {
        register(new cn.lineai.share.format.ClipboardFormat());
        register(new cn.lineai.share.format.PlainTextFormat());
        register(new cn.lineai.share.format.MarkdownFormat());
        register(new cn.lineai.share.format.PdfFormat());
        register(new cn.lineai.share.format.ChatImageFormat());
    }

    public void register(ExportFormat format) {
        if (format != null) {
            formats.add(format);
        }
    }

    public List<ExportFormat> getAllFormats() {
        return Collections.unmodifiableList(formats);
    }

    public String[] getDisplayNames() {
        String[] names = new String[formats.size()];
        for (int i = 0; i < formats.size(); i++) {
            names[i] = formats.get(i).displayName();
        }
        return names;
    }

    public ExportFormat get(int index) {
        return formats.get(index);
    }

    public int size() {
        return formats.size();
    }
}
