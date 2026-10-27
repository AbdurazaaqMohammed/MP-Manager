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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Format-generic read access to every archive type the panes can browse.
 *
 * Zip keeps flowing through {@link ZipCompat}; 7z, RAR, the tar family and the
 * single-file compressors (gz/bz2/xz) are read through commons-compress and
 * junrar, so each of them opens in a pane the way zips always did. Entry names
 * are normalized to zip conventions everywhere: forward slashes and a trailing
 * slash on directories, which is what the pane listing and the '..' logic of
 * {@code MainActivity.readZipFolder} expect.
 */
public final class ArchiveBrowseUtil {

    /** One raw archive entry; {@code path} ends with '/' when {@code isDirectory}. */
    public record RawEntry(String path, boolean isDirectory, long size, long lastModified) {
    }

    private static final int BUFFER_SIZE = 8192;

    private ArchiveBrowseUtil() {
    }

    /**
     * True when the archive is a zip-family container whose entries can be
     * edited in place (zip4j). Plain zip extensions are accepted directly; any
     * other file (.apk bundles and friends) is judged by its PK magic so the
     * archive tools keep working on zip content under other names.
     */
    public static boolean isZipLike(File archive) {
        if (archive == null) return false;
        if (archive.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) return true;
        try (InputStream in = FileUtils.getInputStream(archive)) {
            byte[] magic = new byte[4];
            if (readFully(in, magic) < 4) return false;
            return magic[0] == 'P' && magic[1] == 'K'
                    && (magic[2] == 3 || magic[2] == 5 || magic[2] == 7)
                    && (magic[3] == 4 || magic[3] == 6 || magic[3] == 8);
        } catch (Exception e) {
            return false;
        }
    }

    /** Every entry in the archive, directories marked by a trailing '/'. */
    public static List<RawEntry> listEntries(File archive) throws IOException {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            List<RawEntry> out = new ArrayList<>();
            for (net.lingala.zip4j.model.FileHeader fh : ZipCompat.listHeaders(archive)) {
                boolean dir = fh.isDirectory();
                out.add(new RawEntry(normalizeDir(dir, fh.getFileName()), dir,
                        Math.max(0, fh.getUncompressedSize()),
                        Math.max(0, fh.getLastModifiedTime())));
            }
            return out;
        }
        if (lower.endsWith(".7z")) {
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
        if (lower.endsWith(".rar")) return listRar(archive);
        if (lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(openCompressed(archive, lower))) {
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
        if (lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz"))
            return listSingleCompressed(archive, lower);
        throw new IOException("Unsupported archive format: " + archive.getName());
    }

    /**
     * Opens one entry for reading. The returned stream owns whatever archive
     * handle backs it and closes it when closed.
     */
    public static InputStream openEntry(File archive, String entryPath) throws IOException {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) return ZipCompat.openEntry(archive, entryPath);
        String want = stripSlash(normalize(entryPath));
        if (lower.endsWith(".7z")) {
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
        if (lower.endsWith(".rar")) {
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
        if (lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            TarArchiveInputStream tar = new TarArchiveInputStream(openCompressed(archive, lower));
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
            throw new IOException("Entry not found: " + entryPath);
        }
        if (lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz")) {
            InputStream raw = new FileInputStream(archive);
            try {
                if (lower.endsWith(".gz")) return new GzipCompressorInputStream(raw, true);
                if (lower.endsWith(".bz2")) return new BZip2CompressorInputStream(raw, true);
                return new XZCompressorInputStream(raw, true);
            } catch (IOException e) {
                closeQuietly(raw);
                throw e;
            }
        }
        throw new IOException("Unsupported archive format: " + archive.getName());
    }

    /** Extracts one entry into {@code outFile}, restoring its stored mtime. */
    public static void extractEntry(File archive, String entryPath, File outFile) throws IOException {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            ZipCompat.extractEntry(archive, entryPath, outFile);
            return;
        }
        String want = stripSlash(normalize(entryPath));
        File parent = outFile.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        if (lower.endsWith(".7z")) {
            try (SevenZFile sz = openSevenZ(archive, null)) {
                SevenZArchiveEntry e;
                while ((e = sz.getNextEntry()) != null) {
                    String name = stripSlash(normalize(e.getName()));
                    if (!name.equals(want) || e.isDirectory()) continue;
                    try (InputStream in = sz.getInputStream(e); OutputStream os = new FileOutputStream(outFile)) {
                        copy(in, os);
                    }
                    if (e.getLastModifiedDate() != null) outFile.setLastModified(e.getLastModifiedDate().getTime());
                    return;
                }
            }
            throw new IOException("Entry not found: " + entryPath);
        }
        if (lower.endsWith(".rar")) {
            extractRarEntry(archive, want, outFile);
            return;
        }
        if (lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(openCompressed(archive, lower))) {
                TarArchiveEntry e;
                while ((e = tar.getNextTarEntry()) != null) {
                    if (!stripSlash(normalize(e.getName())).equals(want)) continue;
                    if (e.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        outFile.mkdirs();
                        return;
                    }
                    try (OutputStream os = new FileOutputStream(outFile)) {
                        copy(tar, os);
                    }
                    if (e.getLastModifiedDate() != null) outFile.setLastModified(e.getLastModifiedDate().getTime());
                    return;
                }
            }
            throw new IOException("Entry not found: " + entryPath);
        }
        if (lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz")) {
            try (InputStream in = openEntry(archive, entryPath); OutputStream os = new FileOutputStream(outFile)) {
                copy(in, os);
            }
            return;
        }
        throw new IOException("Unsupported archive format: " + archive.getName());
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
     * Opens a 7z archive together with the file handle backing it. The
     * commons-compress {@code SevenZFile(File)} constructor needs
     * {@code File.toPath()}, which Android only offers from API 26; the
     * channel-based constructor it falls back on here works down to the app's
     * minSdk, at the cost of a second handle that has to be closed by hand.
     */
    /**
     * Opens a 7z archive. {@code SevenZFile(File)} and the password variants
     * are used as-is: with core library desugaring (NIO) enabled, their
     * {@code File.toPath()} calls and the java.nio.file machinery behind them
     * are rewritten to the desugar implementation, which works below API 26.
     * Passing a platform {@link java.nio.channels.FileChannel} from
     * {@code RandomAccessFile.getChannel()} instead is NOT an option — the
     * desugar layer rewrites the channel types and the adapter corrupts
     * sequential 7z entry iteration under ART.
     */
    public static SevenZFile openSevenZ(File archive, char[] password) throws IOException {
        return password == null ? new SevenZFile(archive) : new SevenZFile(archive, password);
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
     * A .gz/.bz2/.xz holds exactly one unnamed payload; it is presented as the
     * archive name minus its extension, matching what extraction produces.
     * gzip exposes the true mtime in its header and the uncompressed size in
     * its ISIZE trailer, so both are read instead of shown as zero.
     */
    private static List<RawEntry> listSingleCompressed(File archive, String lower) throws IOException {
        String ext = lower.endsWith(".gz") ? ".gz" : lower.endsWith(".bz2") ? ".bz2" : ".xz";
        String name = archive.getName().substring(0, archive.getName().length() - ext.length());
        if (name.isEmpty()) name = "content";
        long size = 0;
        long mtime = 0;
        if (lower.endsWith(".gz")) {
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

    private static InputStream openCompressed(File archive, String lower) throws IOException {
        InputStream raw = new FileInputStream(archive);
        try {
            if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return new GzipCompressorInputStream(raw, true);
            if (lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")) return new BZip2CompressorInputStream(raw, true);
            if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) return new XZCompressorInputStream(raw, true);
            return raw;
        } catch (IOException e) {
            closeQuietly(raw);
            throw e;
        }
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
