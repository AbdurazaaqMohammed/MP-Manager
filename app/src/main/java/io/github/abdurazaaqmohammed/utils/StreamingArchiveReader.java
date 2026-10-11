package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.domain.files.ArchiveEntryInfo;

/**
 * Streaming archive reader: reads only the directory listing (headers) without
 * extracting the whole archive to disk. Individual entries are extracted
 * on-demand when the user taps a file.
 *
 * Supports: tar, tar.gz/tgz, tar.bz2/tbz2, tar.xz/txz, rar, 7z
 */
public class StreamingArchiveReader {

    private static final int BUFFER = 8192;

    /** List entries at the given path inside the archive (no extraction). */
    public static List<ArchiveEntryInfo> listEntries(File archive, String path) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("Archive does not exist: " + archive);
        }
        // FIX: SELALU baca dari root — dapet SEMUA entry
        // Filter path di UI (showStreamingEntries)
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return listTar(archive, "", new GzipCompressorInputStream(new FileInputStream(archive), true), "tar.gz");
        } else if (lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")) {
            return listTar(archive, "", new BZip2CompressorInputStream(new FileInputStream(archive), true), "tar.bz2");
        } else if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            return listTar(archive, "", new XZCompressorInputStream(new FileInputStream(archive), true), "tar.xz");
        } else if (lower.endsWith(".tar")) {
            return listTar(archive, "", new FileInputStream(archive), "tar");
        } else if (lower.endsWith(".rar")) {
            return listRar(archive, "");
        } else if (lower.endsWith(".7z")) {
            return list7z(archive, "");
        }
        throw new IOException("Unsupported archive: " + archive.getName());
    }

    // ===== TAR (all variants) — return SEMUA entry =====
    private static List<ArchiveEntryInfo> listTar(File archive, String path, InputStream base, String type) throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();
        try (TarArchiveInputStream tais = new TarArchiveInputStream(base)) {
            TarArchiveEntry entry;
            while (true) {
                try {
                    entry = tais.getNextTarEntry();
                } catch (java.io.EOFException e) {
                    break;
                }
                if (entry == null) break;
                String entryPath = entry.getName().replace('\\', '/');
                if (entryPath.isEmpty()) continue;
                
                // Return SEMUA entry (file + folder)
                String name = entryPath.substring(entryPath.lastIndexOf('/') + 1);
                if (name.isEmpty()) continue;
                
                entries.add(new ArchiveEntryInfo(
                        name, entryPath,
                        entry.getSize(),
                        entry.getModTime() != null ? entry.getModTime().getTime() : 0L,
                        entry.isDirectory(), archive, type));
            }
        } catch (java.io.EOFException e) {
        } catch (IOException e) {
            if (entries.isEmpty()) throw e;
        }
        return entries;
    }

    // ===== RAR — return SEMUA entry =====
    private static List<ArchiveEntryInfo> listRar(File archive, String path) throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();
        try (Archive rar = new Archive(archive)) {
            FileHeader fh;
            while ((fh = rar.nextFileHeader()) != null) {
                String entryPath = fh.getFileName().replace('\\', '/');
                if (entryPath.isEmpty()) continue;
                String name = entryPath.substring(entryPath.lastIndexOf('/') + 1);
                if (name.isEmpty()) continue;
                entries.add(new ArchiveEntryInfo(
                        name, entryPath,
                        fh.getFullUnpackSize(),
                        fh.getMTime() != null ? fh.getMTime().getTime() : 0L,
                        fh.isDirectory(), archive, "rar"));
            }
        } catch (Exception e) {
            throw new IOException("Failed to read RAR: " + e.getMessage(), e);
        }
        return entries;
    }

    // ===== 7Z — return SEMUA entry =====
    private static List<ArchiveEntryInfo> list7z(File archive, String path) throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();
        try (SevenZFile zf = new SevenZFile(archive)) {
            SevenZArchiveEntry entry;
            while ((entry = zf.getNextEntry()) != null) {
                String entryPath = entry.getName().replace('\\', '/');
                if (entryPath.isEmpty()) continue;
                String name = entryPath.substring(entryPath.lastIndexOf('/') + 1);
                if (name.isEmpty()) continue;
                entries.add(new ArchiveEntryInfo(
                        name, entryPath,
                        entry.getSize(),
                        entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().getTime() : 0L,
                        entry.isDirectory(), archive, "7z"));
            }
        }
        return entries;
    }

    // ===== Extract 1 entry (on-demand) =====
    public static File extractEntry(ArchiveEntryInfo info, File destDir) throws IOException {
        if (info == null) throw new IOException("Null entry");
        if (info.isDirectory()) throw new IOException("Cannot extract directory");

        File out = new File(destDir, info.getName());
        File parent = out.getParentFile();
        // === FIX: pastikan parent dir ada ===
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IOException("Cannot create parent dir: " + parent);
            }
        }

        File archive = info.getArchiveFile();
        String lower = archive.getName().toLowerCase(Locale.ROOT);

        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            extractTarEntry(info, out, new GzipCompressorInputStream(new FileInputStream(archive), true));
        } else if (lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")) {
            extractTarEntry(info, out, new BZip2CompressorInputStream(new FileInputStream(archive), true));
        } else if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            extractTarEntry(info, out, new XZCompressorInputStream(new FileInputStream(archive), true));
        } else if (lower.endsWith(".tar")) {
            extractTarEntry(info, out, new FileInputStream(archive));
        } else if (lower.endsWith(".rar")) {
            extractRarEntry(info, out);
        } else if (lower.endsWith(".7z")) {
            extract7zEntry(info, out);
        } else {
            throw new IOException("Unsupported archive type");
        }
        return out;
    }

    private static void extractTarEntry(ArchiveEntryInfo info, File out, InputStream base) throws IOException {
        try (TarArchiveInputStream tais = new TarArchiveInputStream(base)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (entry.getName().replace('\\', '/').equals(info.getFullPath())) {
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copyStream(tais, os);
                    }
                    return;
                }
            }
        }
        throw new IOException("Entry not found: " + info.getFullPath());
    }

    private static void extractRarEntry(ArchiveEntryInfo info, File out) throws IOException {
        try (Archive rar = new Archive(info.getArchiveFile())) {
            FileHeader fh;
            while ((fh = rar.nextFileHeader()) != null) {
                if (fh.getFileName().replace('\\', '/').equals(info.getFullPath())) {
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        rar.extractFile(fh, os);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract RAR entry", e);
        }
        throw new IOException("Entry not found: " + info.getFullPath());
    }

    private static void extract7zEntry(ArchiveEntryInfo info, File out) throws IOException {
        try (SevenZFile zf = new SevenZFile(info.getArchiveFile())) {
            SevenZArchiveEntry entry;
            while ((entry = zf.getNextEntry()) != null) {
                if (entry.getName().replace('\\', '/').equals(info.getFullPath())) {
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        byte[] buf = new byte[BUFFER];
                        int n;
                        while ((n = zf.read(buf)) != -1) os.write(buf, 0, n);
                    }
                    return;
                }
            }
        }
        throw new IOException("Entry not found: " + info.getFullPath());
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[BUFFER];
        int n;
        while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
    }

    private static String normalizePrefix(String path) {
        if (path == null || path.isEmpty()) return "";
        String p = path.replace('\\', '/');
        if (!p.endsWith("/")) p += "/";
        return p;
    }
}
