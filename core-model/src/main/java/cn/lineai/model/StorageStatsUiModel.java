package cn.lineai.model;

/**
 * UI layer representation of storage statistics, decoupled from StorageStatsRepository.
 */
public final class StorageStatsUiModel {
    public long totalSize = 0;
    public int totalCount = 0;
    public long diffCacheSize = 0;
    public int diffCacheCount = 0;
    public long chatSize = 0;
    public int chatCount = 0;
    public long configSize = 0;
    public int configCount = 0;
    public long homeSize = 0;
    public int homeCount = 0;

    public StorageStatsUiModel() {
    }

    public String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        if (bytes < 1024 * 1024 * 1024) {
            long mb = bytes / (1024 * 1024);
            return mb + " MB";
        }
        long gb = bytes / (1024 * 1024 * 1024);
        return gb + " GB";
    }

    public String formatTotalSize() {
        return formatSize(totalSize);
    }

    public String formatDiffCacheSize() {
        return formatSize(diffCacheSize);
    }

    public String formatChatSize() {
        return formatSize(chatSize);
    }

    public String formatConfigSize() {
        return formatSize(configSize);
    }

    public String formatHomeSize() {
        return formatSize(homeSize);
    }
}
