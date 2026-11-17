package io.github.abdurazaaqmohammed.feature.files;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Locale;

import io.github.abdurazaaqmohammed.util.archive.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.util.archive.ArchiveUtil;
import io.github.abdurazaaqmohammed.util.io.FileUtils;

/** Distinguishes appended editor backups from standalone .bak archive formats. */
final class BackupOpenPolicy {
    private BackupOpenPolicy() {}

    static boolean isBackupName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".bak");
    }

    static String originalName(String name) {
        return isBackupName(name) ? name.substring(0, name.length() - 4) : name;
    }

    static boolean isNativeBackup(File readable, String name) {
        if (ArchiveUtil.isMiuiOrAndroidBackup(readable)) return true;
        // A ZIP/APK's appended .bak is a backup, not a standalone container.
        // Unknown suffixes (e.g. com.example.app.bak) still allow native archive browsing.
        return !hasOriginalFormat(originalName(name)) && ArchiveBrowseUtil.isBrowsable(readable);
    }

    static boolean hasOriginalFormat(String name) {
        String ext = "." + FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT);
        if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS) || FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)
                || FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)
                || FileUtils.matchExt(ext, FileUtils.FONT_EXTS) || FileUtils.matchExt(ext, FileUtils.HTML_EXTS)) return true;
        if (ArchiveUtil.isSupportedArchive(name)) return true;
        return switch (ext) {
            case ".apk", ".apks", ".xapk", ".apkm", ".aspk", ".dex", ".arsc", ".xml", ".axml",
                    ".pdf", ".db", ".db3", ".sqlite", ".sqlite3", ".img", ".iso", ".bin",
                    ".payload", ".sh", ".mtcr", ".dexdiff", ".bak" -> true;
            default -> false;
        };
    }
}
