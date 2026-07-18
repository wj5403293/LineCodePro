package cn.lineai.log;

import java.io.File;

public final class ErrorLogEntry {
    private final File file;
    private final String title;
    private final String subtitle;
    private final long timestamp;

    ErrorLogEntry(File file, String title, String subtitle, long timestamp) {
        this.file = file;
        this.title = title;
        this.subtitle = subtitle;
        this.timestamp = timestamp;
    }

    public File getFile() {
        return file;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
