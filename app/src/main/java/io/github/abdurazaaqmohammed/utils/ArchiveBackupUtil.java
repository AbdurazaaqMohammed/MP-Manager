package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.CRC32;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

/**
 * Change detection for entries replaced inside an archive.
 *
 * <p>When a file inside a ZIP/APK is modified, the untouched archive is
 * preserved next to the original ({@code archive.bak}, handled by
 * {@link FileUtils#backupOnce}); no per-entry backup is written into the
 * archive itself. The CRC helpers here tell whether a save actually changed
 * the stored entry, so saving an unchanged file leaves no litter behind.
 */
public final class ArchiveBackupUtil {

    private ArchiveBackupUtil() {
    }

    /** CRC32 of a file on disk, or -1 when it cannot be read. */
    public static long crc32Of(File file) {
        if (file == null || !file.isFile()) return -1;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 16384)) {
            CRC32 crc = new CRC32();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) crc.update(buf, 0, n);
            return crc.getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /** CRC32 of an archive entry, recompressed on the fly, or -1 on failure. */
    public static long crc32OfEntry(ZipFile zip, String entryName) {
        if (zip == null || entryName == null) return -1;
        try {
            FileHeader header = zip.getFileHeader(entryName);
            if (header == null || header.isDirectory()) return -1;
            CRC32 crc = new CRC32();
            try (InputStream in = zip.getInputStream(header)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) crc.update(buf, 0, n);
            }
            return crc.getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * True when {@code replacement} differs from what the archive currently
     * stores under {@code entryName}. A missing entry always counts as changed.
     */
    public static boolean isEntryChanged(ZipFile zip, String entryName, File replacement) {
        if (zip == null || entryName == null || replacement == null || !replacement.isFile()) return true;
        long stored = crc32OfEntry(zip, entryName);
        if (stored < 0) return true;
        long incoming = crc32Of(replacement);
        if (incoming < 0) return true;
        return stored != incoming;
    }
}