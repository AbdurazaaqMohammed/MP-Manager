package io.github.abdurazaaqmohammed.utils;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Format-generic read access to every archive type the panes can browse.
 *
 * The format is detected from the file's bytes (magic numbers, and a tar
 * header heuristic for the magic-less tar), never from the file name, so
 * "Open with → Archive viewer" works on any real archive even when its
 * extension is missing or wrong. Zip keeps flowing through {@link ZipCompat};
 * 7z, RAR, the tar family and the single-file compressors (gz/bz2/xz) are read
 * through commons-compress and junrar. A gz/bz2/xz whose payload is a tar is
 * browsed as that tar, so mislabeled tar.gz archives still list real names.
 * Entry names are normalized to zip conventions everywhere: forward slashes
 * and a trailing slash on directories.
 */
public final class ArchiveBrowseUtil {

    /** Archive container kinds detectable from file content. */
    public enum ArchiveType {
        ZIP, SEVEN_Z, RAR, TAR, GZIP, BZIP2, XZ, BACKUP,
        /** Not a recognizable archive (or one this build cannot list). */
        NONE
    }

    /** One raw archive entry; {@code path} ends with '/' when {@code isDirectory}. */
    public record RawEntry(String path, boolean isDirectory, long size, long lastModified) {
    }

    private static final int BUFFER_SIZE = 8192;
    private static final int TAR_BLOCK = 512;

    private ArchiveBrowseUtil() {
    }

    /**
     * Detects the archive container kind from the file's leading bytes. Never
     * throws; {@link ArchiveType#NONE} means "not a readable archive".
     */
    public static ArchiveType detectArchiveType(File file) {
        if (file == null || !file.isFile()) return ArchiveType.NONE;
        byte[] head = new byte[TAR_BLOCK];
        int n;
        try (InputStream in = FileUtils.getInputStream(file)) {
            n = readFully(in, head);
        } catch (Exception e) {
            return ArchiveType.NONE;
        }
        byte[] magic = head;
        if (n >= 4 && magic[0] == 'P' && magic[1] == 'K'
                && (magic[2] == 3 || magic[2] == 5 || magic[2] == 7)
                && (magic[3] == 4 || magic[3] == 6 || magic[3] == 8)) return ArchiveType.ZIP;
        if (n >= 6 && magic[0] == 0x37 && magic[1] == 0x7A && magic[2] == (byte) 0xBC
                && magic[3] == (byte) 0xAF && magic[4] == 0x27 && magic[5] == 0x1C)
            return ArchiveType.SEVEN_Z;
        if (n >= 6 && magic[0] == 'R' && magic[1] == 'a' && magic[2] == 'r'
                && magic[3] == '!' && magic[4] == 0x1A && magic[5] == 0x07) return ArchiveType.RAR;
        if (n >= 2 && magic[0] == 0x1F && magic[1] == (byte) 0x8B) return ArchiveType.GZIP;
        if (n >= 3 && magic[0] == 'B' && magic[1] == 'Z' && magic[2] == 'h') return ArchiveType.BZIP2;
        if (n >= 6 && magic[0] == (byte) 0xFD && magic[1] == 0x37 && magic[2] == 0x7A
                && magic[3] == 'X' && magic[4] == 'Z' && magic[5] == 0x00) return ArchiveType.XZ;
        return looksLikeTar(head, n) ? ArchiveType.TAR : (n >= 14 && (new String(head, 0, Math.min(n, 64), StandardCharsets.US_ASCII).startsWith("MIUI BACKUP") || new String(head, 0, Math.min(n, 64), StandardCharsets.US_ASCII).startsWith("ANDROID BACKUP")) ? ArchiveType.BACKUP : ArchiveType.NONE);
    }

    /** True when the archive content can be listed in a pane. */
    public static boolean isBrowsable(File file) {
        return detectArchiveType(file) != ArchiveType.NONE;
    }

    /**
     * True when the file is a zip-family container whose entries can be edited
     * in place (zip4j) — zips, APKs and every other PK-prefixed container.
     */
    public static boolean isZipLike(File archive) {
        return detectArchiveType(archive) == ArchiveType.ZIP;
    }

    /** True when the leading bytes look like a tar archive block. */
    private static boolean looksLikeTar(byte[] head, int len) {
        if (len < TAR_BLOCK) return false;
        // POSIX ("ustar\0" + "00") and GNU ("ustar ", " \0") both spell ustar
        // at offset 257; v7 tars have no magic, so fall back to the checksum.
        if (head[257] == 'u' && head[258] == 's' && head[259] == 't'
                && head[260] == 'a' && head[261] == 'r') return true;
        if (head[0] == 0) return false;
        try {
            String chk = new String(head, 148, 8, StandardCharsets.ISO_8859_1).trim();
            if (chk.isEmpty() || !isOctal(chk)) return false;
            long stored = Long.parseLong(chk, 8);
            long sum = 0;
            for (int i = 0; i < TAR_BLOCK; i++)
                sum += (i >= 148 && i < 156) ? 0x20 : (head[i] & 0xFF);
            return sum == stored;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isOctal(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '7') return false;
        }
        return true;
    }

    /** Every entry in the archive, directories marked by a trailing '/'. */
    public static List<RawEntry> listEntries(File archive) throws IOException {
        ArchiveType type = detectArchiveType(archive);
        switch (type) {
            case ZIP: {
                List<RawEntry> out = new ArrayList<>();
                for (net.lingala.zip4j.model.FileHeader fh : ZipCompat.listHeaders(archive)) {
                    boolean dir = fh.isDirectory();
                    out.add(new RawEntry(normalizeDir(dir, fh.getFileName()), dir,
                            Math.max(0, fh.getUncompressedSize()),
                            Math.max(0, fh.getLastModifiedTime())));
                }
                return out;
            }
            case SEVEN_Z: {
                try (SevenZFile sz = openSevenZ(archive, null)) {
                    List<RawEntry> out = new ArrayList<>();
                    for (SevenZArchiveEntry e : sz.getEntries()) {
                        String name = e.getName().replace('\\', '/');
                        if (name.isEmpty()) continue;
                        long mtime = e.getLastModifiedDate() != null ? e.getLastModifiedDate().getTime() : 0;
                        out.add(new RawEntry(normalizeDir(e.isDirectory(), name), e.isDirectory(),
                                Math.max(0, e.getSize()), mtime));
                    }
                    return out;
                }
            }
            case RAR:
                return listRar(archive);
            case TAR:
            case BACKUP:
                return listTar(archive, type);
            case GZIP:
            case BZIP2:
            case XZ: {
                // A compressor stream wrapping a tar is browsed as that tar, so
                // mislabeled .tar.gz archives list the tar's real entry names.
                if (isTarPayload(archive, type)) return listTar(archive, type);
                return listSingleCompressed(archive, type);
            }
            default:
                throw new IOException("Unsupported archive format: " + archive.getName());
        }
    }

    /**
     * Opens one entry for reading. The returned stream owns whatever archive
     * handle backs it and closes it when closed.
     */
    public static InputStream openEntry(File archive, String entryPath) throws IOException {
        ArchiveType type = detectArchiveType(archive);
        String want = stripSlash(normalize(entryPath));
        switch (type) {
            case ZIP:
                return ZipCompat.openEntry(archive, entryPath);
            case SEVEN_Z: {
                SevenZFile sz = openSevenZ(archive, null);
                try {
                    for (SevenZArchiveEntry e : sz.getEntries()) {
                        if (!stripSlash(normalize(e.getName())).equals(want)) continue;
                        if (e.isDirectory()) break;
                        return closeWith(sz, sz.getInputStream(e));
                    }
                } catch (IOException | RuntimeException e) {
                    closeQuietly(sz);
                    throw e;
                }
                closeQuietly(sz);
                throw new IOException("Entry not found: " + entryPath);
            }
            case RAR: {
                // junrar only decodes into an OutputStream, so stage the entry in a
                // temp file and stream from that; the file deletes itself on close.
                File tmp = File.createTempFile("mp_rar_entry_", ".tmp");
                try {
                    extractRarEntry(archive, want, tmp);
                    return new DeletingFileInputStream(tmp);
                } catch (IOException | RuntimeException e) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    throw e;
                }
            }
            case TAR:
            case BACKUP:
                return openTarEntry(archive, type, want, entryPath);
            case GZIP:
            case BZIP2:
            case XZ:
                if (isTarPayload(archive, type)) return openTarEntry(archive, type, want, entryPath);
                return openSingleCompressed(archive, type);
            default:
                throw new IOException("Unsupported archive format: " + archive.getName());
        }
    }

    /** Extracts one entry into {@code outFile}, restoring its stored mtime. */
    public static void extractEntry(File archive, String entryPath, File outFile) throws IOException {
        ArchiveType type = detectArchiveType(archive);
        if (type == ArchiveType.ZIP) {
            ZipCompat.extractEntry(archive, entryPath, outFile);
            return;
        }
        String want = stripSlash(normalize(entryPath));
        File parent = outFile.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        switch (type) {
            case SEVEN_Z: {
                try (SevenZFile sz = openSevenZ(archive, null)) {
                    for (SevenZArchiveEntry e : sz.getEntries()) {
                        if (!stripSlash(normalize(e.getName())).equals(want)) continue;
                        if (e.isDirectory()) {
                            //noinspection ResultOfMethodCallIgnored
                            outFile.mkdirs();
                            return;
                        }
                        try (InputStream in = sz.getInputStream(e); OutputStream os = new FileOutputStream(outFile)) {
                            copy(in, os);
                        }
                        if (e.getLastModifiedDate() != null) outFile.setLastModified(e.getLastModifiedDate().getTime());
                        return;
                    }
                }
                throw new IOException("Entry not found: " + entryPath);
            }
            case RAR:
                extractRarEntry(archive, want, outFile);
                return;
            case TAR:
            case BACKUP: {
                if (extractTarEntry(archive, type, want, outFile)) return;
                break;
            }
            case GZIP:
            case BZIP2:
            case XZ: {
                if (isTarPayload(archive, type)) {
                    if (extractTarEntry(archive, type, want, outFile)) return;
                    break;
                }
                try (InputStream in = openSingleCompressed(archive, type); OutputStream os = new FileOutputStream(outFile)) {
                    copy(in, os);
                }
                return;
            }
            default:
                throw new IOException("Unsupported archive format: " + archive.getName());
        }
        throw new IOException("Entry not found: " + entryPath);
    }

    /** CRC-32 of one entry's uncompressed bytes, or -1 when it cannot be read. */
    public static long entryCrc32(File archive, String entryPath) {
        try (InputStream in = openEntry(archive, entryPath)) {
            CRC32 crc = new CRC32();
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) crc.update(buf, 0, n);
            return crc.getValue();
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * True when the payload inside a gz/bz2/xz starts with a tar header, i.e.
     * the file is a compressed tar regardless of its name.
     */
    public static boolean isTarPayload(File archive, ArchiveType wrapper) {
        try (InputStream in = openSingleCompressed(archive, wrapper)) {
            byte[] head = new byte[TAR_BLOCK];
            return readFully(in, head) == TAR_BLOCK && looksLikeTar(head, TAR_BLOCK);
        } catch (Exception e) {
            return false;
        }
    }

    private static List<RawEntry> listTar(File archive, ArchiveType wrapper) throws IOException {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(openTar(archive, wrapper))) {
            List<RawEntry> out = new ArrayList<>();
            TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.isEmpty()) continue;
                // "tar cf x.tar ." stores a "./" root marker; normalize()
                // turns it into "/" which is not a real child of the root.
                if (stripSlash(name).isEmpty()) continue;
                long mtime = e.getLastModifiedDate() != null ? e.getLastModifiedDate().getTime() : 0;
                out.add(new RawEntry(normalizeDir(e.isDirectory(), name), e.isDirectory(),
                        Math.max(0, e.getSize()), mtime));
            }
            return out;
        }
    }

    /** Scans a (possibly compressed) tar for one entry and copies it out. */
    private static boolean extractTarEntry(File archive, ArchiveType wrapper, String want, File outFile) throws IOException {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(openTar(archive, wrapper))) {
            TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                if (!stripSlash(normalize(e.getName())).equals(want)) continue;
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    outFile.mkdirs();
                    return true;
                }
                try (OutputStream os = new FileOutputStream(outFile)) {
                    copy(tar, os);
                }
                if (e.getLastModifiedDate() != null) outFile.setLastModified(e.getLastModifiedDate().getTime());
                return true;
            }
        }
        return false;
    }

    /** Positions a (possibly compressed) tar on one entry and returns it open. */
    private static InputStream openTarEntry(File archive, ArchiveType wrapper, String want, String originalPath) throws IOException {
        TarArchiveInputStream tar = new TarArchiveInputStream(openTar(archive, wrapper));
        try {
            TarArchiveEntry e;
            while ((e = tar.getNextTarEntry()) != null) {
                if (!stripSlash(normalize(e.getName())).equals(want)) continue;
                if (e.isDirectory()) break;
                return tar;
            }
        } catch (IOException | RuntimeException e) {
            closeQuietly(tar);
            throw e;
        }
        closeQuietly(tar);
        throw new IOException("Entry not found: " + originalPath);
    }

    /**
     * Opens the (optionally decompressed) byte stream of a tar archive. The
     * caller wraps the result in {@link TarArchiveInputStream} itself.
     */
    public static InputStream openTar(File archive, ArchiveType wrapper) throws IOException {
        if (wrapper == ArchiveType.BACKUP) {
            InputStream raw = new BufferedInputStream(new FileInputStream(archive));
            try {
                String line = readAsciiLine(raw);
                if (line == null) {
                    raw.close();
                    throw new IOException("Empty backup archive: " + archive.getName());
                }
                line = line.trim();
                if (line.startsWith("MIUI BACKUP")) {
                    boolean foundAndroidBackup = false;
                    for (int i = 0; i < 20; i++) {
                        line = readAsciiLine(raw);
                        if (line == null) break;
                        if (line.trim().startsWith("ANDROID BACKUP")) {
                            foundAndroidBackup = true;
                            break;
                        }
                    }
                    if (!foundAndroidBackup) {
                        raw.close();
                        throw new IOException("Invalid MIUI backup format: missing ANDROID BACKUP section");
                    }
                } else if (!line.startsWith("ANDROID BACKUP")) {
                    raw.close();
                    throw new IOException("Not an Android or MIUI backup: " + line);
                }
                String versionStr = readAsciiLine(raw);
                String compressionStr = readAsciiLine(raw);
                String encryptionStr = readAsciiLine(raw);
                if (encryptionStr != null && !"none".equalsIgnoreCase(encryptionStr.trim())) {
                    raw.close();
                    throw new IOException("Encrypted backups (" + encryptionStr + ") are not supported");
                }
                boolean isCompressed = compressionStr != null &&
                        !"0".equals(compressionStr.trim()) &&
                        !"none".equalsIgnoreCase(compressionStr.trim());
                return isCompressed ? new java.util.zip.InflaterInputStream(raw) : raw;
            } catch (IOException e) {
                closeQuietly(raw);
                throw e;
            }
        }
        InputStream raw = new FileInputStream(archive);
        try {
            if (wrapper == ArchiveType.GZIP) return new GzipCompressorInputStream(raw, true);
            if (wrapper == ArchiveType.BZIP2) return new BZip2CompressorInputStream(raw, true);
            if (wrapper == ArchiveType.XZ) return new XZCompressorInputStream(raw, true);
            return raw;
        } catch (IOException e) {
            closeQuietly(raw);
            throw e;
        }
    }

    private static String readAsciiLine(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return sb.length() == 0 && b == -1 ? null : sb.toString();
    }

    private static List<RawEntry> listRar(File archive) throws IOException {
        try (Archive rar = new Archive(archive)) {
            List<RawEntry> out = new ArrayList<>();
            for (FileHeader fh : rar.getFileHeaders()) {
                String name = fh.getFileName().replace('\\', '/');
                if (name.isEmpty()) continue;
                long mtime = fh.getMTime() != null ? fh.getMTime().getTime() : 0;
                out.add(new RawEntry(normalizeDir(fh.isDirectory(), name), fh.isDirectory(),
                        Math.max(0, fh.getUnpSize()), mtime));
            }
            return out;
        } catch (RarException e) {
            throw new IOException("Failed to read RAR archive: " + archive.getName(), e);
        }
    }

    private static void extractRarEntry(File archive, String want, File outFile) throws IOException {
        try (Archive rar = new Archive(archive)) {
            for (FileHeader fh : rar.getFileHeaders()) {
                if (!stripSlash(normalize(fh.getFileName())).equals(want)) continue;
                if (fh.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    outFile.mkdirs();
                    return;
                }
                try (OutputStream os = new FileOutputStream(outFile)) {
                    rar.extractFile(fh, os);
                }
                if (fh.getMTime() != null) outFile.setLastModified(fh.getMTime().getTime());
                return;
            }
        } catch (RarException e) {
            throw new IOException("Failed to read RAR archive: " + archive.getName(), e);
        }
        throw new IOException("Entry not found: " + want);
    }

    /**
     * A bare gz/bz2/xz holds exactly one unnamed payload. gzip stores the
     * original file name in its header, so that is used when present; the
     * archive's own name minus a known compressor extension is the fallback.
     */
    private static List<RawEntry> listSingleCompressed(File archive, ArchiveType type) throws IOException {
        String name = null;
        if (type == ArchiveType.GZIP) name = gzipStoredFileName(archive);
        if (name == null) name = stripCompressorExtension(archive.getName());
        long size = 0;
        long mtime = 0;
        if (type == ArchiveType.GZIP) {
            long len = archive.length();
            if (len >= 18) {
                try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
                    byte[] head = new byte[8];
                    raf.readFully(head);
                    mtime = u4le(head, 4);
                    byte[] tail = new byte[4];
                    raf.seek(len - 4);
                    raf.readFully(tail);
                    size = u4le(tail, 0);
                }
            }
        }
        List<RawEntry> out = new ArrayList<>(1);
        out.add(new RawEntry(name, false, size, mtime));
        return out;
    }

    /** Opens the decompressed payload stream of a bare gz/bz2/xz file. */
    public static InputStream openSingleCompressed(File archive, ArchiveType type) throws IOException {
        InputStream raw = new FileInputStream(archive);
        try {
            if (type == ArchiveType.GZIP) return new GzipCompressorInputStream(raw, true);
            if (type == ArchiveType.BZIP2) return new BZip2CompressorInputStream(raw, true);
            return new XZCompressorInputStream(raw, true);
        } catch (IOException e) {
            closeQuietly(raw);
            throw e;
        }
    }

    /** Original file name stored in a gzip header (FNAME field), or null. */
    private static String gzipStoredFileName(File archive) {
        try (InputStream in = FileUtils.getInputStream(archive)) {
            byte[] head = new byte[10];
            if (readFully(in, head) < 10) return null;
            int flags = head[3] & 0xFF;
            if ((flags & 0x08) == 0) return null;
            int skip = 0;
            if ((flags & 0x04) != 0) { // FEXTRA: two little-endian length bytes
                byte[] xlen = new byte[2];
                if (readFully(in, xlen) < 2) return null;
                skip = (xlen[0] & 0xFF) | ((xlen[1] & 0xFF) << 8);
            }
            for (int i = 0; i < skip; i++) if (in.read() < 0) return null;
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) > 0) sb.append((char) b);
            if (sb.length() == 0) return null;
            String name = sb.toString().replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).trim();
            return name.isEmpty() ? null : name;
        } catch (Exception e) {
            return null;
        }
    }

    /** archive.tar.gz → archive.tar; archive.bin → archive.bin (unchanged). */
    public static String stripCompressorExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".txz", ".gz", ".bz2", ".xz"}) {
            if (lower.endsWith(ext) && name.length() > ext.length())
                return name.substring(0, name.length() - ext.length());
        }
        return name;
    }

    /**
     * Opens a 7z archive. With core library desugaring (NIO) enabled, the
     * {@code SevenZFile(File)} machinery — including its {@code File.toPath()}
     * call and the java.nio.file classes in static initializers — works below
     * API 26. Do not pass a platform {@link java.nio.channels.FileChannel}:
     * under the desugar layer that breaks 7z iteration under ART.
     */
    public static SevenZFile openSevenZ(File archive, char[] password) throws IOException {
        return password == null ? new SevenZFile(archive) : new SevenZFile(archive, password);
    }

    private static String normalize(String path) {
        String out = path == null ? "" : path.replace('\\', '/').trim();
        while (out.startsWith("./")) out = out.substring(2);
        return out;
    }

    private static String stripSlash(String path) {
        String out = normalize(path);
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String normalizeDir(boolean isDirectory, String name) {
        String out = normalize(name);
        if (isDirectory) {
            if (!out.endsWith("/")) out += "/";
        } else {
            out = stripSlash(out);
        }
        return out;
    }

    private static long u4le(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
    }

    private static InputStream closeWith(final Closeable owner, InputStream stream) {
        return new FilterInputStream(stream) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    super.close();
                } finally {
                    closeQuietly(owner);
                }
            }
        };
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    /** Streams a temp file and removes it once fully read. */
    private static final class DeletingFileInputStream extends FilterInputStream {
        private final File file;
        private boolean closed;

        DeletingFileInputStream(File file) throws IOException {
            super(new FileInputStream(file));
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                super.close();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
