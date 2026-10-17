package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.InputStream;
import java.util.zip.CRC32;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

/**
 * Backups for entries replaced inside an archive.
 *
 * <p>Two things are preserved when a file inside a ZIP/APK is modified: the
 * untouched archive next to the original ({@code archive.bak}, handled by
 * {@link FileUtils#backupOnce}) and the original entry itself, written back as
 * {@code <entry>.bak} inside the archive. Both are skipped when the new content
 * hashes to the same value as what the archive already holds, so saving an
 * unchanged file leaves no litter behind.
 */
public final class ArchiveBackupUtil {

    public static final String BAK_SUFFIX = ".bak";

    private ArchiveBackupUtil() {
    }

    public static String backupEntryName(String entryName) {
        return entryName + BAK_SUFFIX;
    }

    /** CRC32 of a file on disk, or -1 when it cannot be read. */
    public static long crc32Of(File file) {
        if (file == null || !file.isFile()) return -1;
        try (InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(file), 16384)) {
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

    /**
     * Copies the archive's current copy of {@code entryName} to
     * {@code <entryName>.bak} inside the same archive, but only when the
     * replacement really differs from what is already stored.
     *
     * @return true when a backup entry was written
     */
    public static boolean backupEntryIfChanged(ZipFile zip, String entryName, File replacement) {
        if (!isEntryChanged(zip, entryName, replacement)) return false;
        try {
            FileHeader header = zip.getFileHeader(entryName);
            if (header == null || header.isDirectory()) return false;
            ZipParameters params = new ZipParameters();
            params.setFileNameInZip(backupEntryName(entryName));
            // The original is already compressed; storing it as-is keeps the
            // backup cheap and byte-exact.
            params.setCompressionMethod(CompressionMethod.STORE);
            try (InputStream in = zip.getInputStream(header)) {
                zip.addStream(in, params);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}