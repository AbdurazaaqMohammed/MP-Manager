package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;

/**
 * Reads an archive's entry list into the flat row model the zip pane already
 * uses, so 7z/rar/tar* can browse exactly like zip.
 *
 * <p>Listings for non-zip formats are cached by path+size+mtime: a tar has no
 * central directory, so the first listing costs a full scan and every format
 * here is read-only while browsing, which makes the entry rows safe to reuse
 * across navigation.
 */
public final class ArchiveLister {

    private static final int CACHE_LIMIT = 6;
    private static final Map<String, List<ZipEntryInfo>> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, List<ZipEntryInfo>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<ZipEntryInfo>> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    private ArchiveLister() {
    }

    /** Whether a tap on this name should open the archive pane. */
    public static boolean isBrowsableName(String fileName) {
        if (fileName == null) return false;
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".zip") || n.endsWith(".7z") || n.endsWith(".rar")
                || n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz")
                || n.endsWith(".tar.bz2") || n.endsWith(".tbz2")
                || n.endsWith(".tar.xz") || n.endsWith(".txz");
    }

    public static boolean isZipName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /** Browsable formats that are not zip: these list off the main thread. */
    public static boolean isNonZipBrowsableName(String fileName) {
        return isBrowsableName(fileName) && !isZipName(fileName);
    }

    /**
     * Lists every entry of the archive, flat, with full paths.
     *
     * @param password null when the archive is not password protected
     */
    public static List<ZipEntryInfo> list(File archive, char[] password) throws IOException {
        String n = archive.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".zip")) return listZip(archive);
        if (n.endsWith(".7z")) return list7z(archive, password);
        if (n.endsWith(".rar")) return listRar(archive, password);
        if (n.endsWith(".tar")) return listTar(archive, new FileInputStream(archive));
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz"))
            return listTar(archive, new GzipCompressorInputStream(new FileInputStream(archive), true));
        if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2"))
            return listTar(archive, new BZip2CompressorInputStream(new FileInputStream(archive), true));
        if (n.endsWith(".tar.xz") || n.endsWith(".txz"))
            return listTar(archive, new XZCompressorInputStream(new FileInputStream(archive), true));
        throw new IOException("Unsupported archive format: " + archive.getName());
    }

    /** Cached {@link #list} for non-zip formats; zip lists fast enough each time. */
    public static List<ZipEntryInfo> listCached(File archive, char[] password) throws IOException {
        if (isZipName(archive.getName())) return list(archive, password);
        String key = archive.getAbsolutePath() + "|" + archive.length() + "|" + archive.lastModified();
        List<ZipEntryInfo> hit = CACHE.get(key);
        if (hit != null) return hit;
        List<ZipEntryInfo> fresh = list(archive, password);
        CACHE.put(key, fresh);
        return fresh;
    }

    /**
     * Lists with password resolution: cached result, then the remembered and
     * stored passwords, then prompting until one works.
     *
     * @return null when the user cancelled the password prompt
     */
    public static List<ZipEntryInfo> listResolved(Context context, File archive) throws IOException {
        try {
            List<ZipEntryInfo> hit = CACHE.get(cacheKey(archive));
            if (hit != null) return hit;
        } catch (Exception ignored) {
        }
        IOException last = null;
        List<String> candidates = new ArrayList<>();
        String memo = ZipPassword.remembered(archive);
        if (memo != null) candidates.add(memo);
        candidates.add(null);
        candidates.addAll(ArchivePasswordStore.list(context));
        for (String pw : candidates) {
            try {
                List<ZipEntryInfo> list = listCached(archive, pw == null ? null : pw.toCharArray());
                if (pw != null) ZipPassword.remember(archive, pw);
                return list;
            } catch (IOException e) {
                last = e;
            }
        }
        if (!PasswordedArchive.isEncryptedCandidate(archive)) {
            if (last != null) throw last;
            return listCached(archive, null);
        }
        while (true) {
            String pw = ZipPassword.prompt(context);
            if (pw == null || pw.isEmpty()) return null;
            try {
                List<ZipEntryInfo> list = listCached(archive, pw.toCharArray());
                ZipPassword.remember(archive, pw);
                return list;
            } catch (IOException e) {
                last = e;
                postWrongPassword(context);
            }
        }
    }

    /** One pane listing: the rows plus the synthetic ".." parent row. */
    public static final class Listing {
        public final List<ZipEntryInfo> entries;
        public final ZipEntryInfo parent;

        Listing(List<ZipEntryInfo> entries, ZipEntryInfo parent) {
            this.entries = entries;
            this.parent = parent;
        }
    }

    /**
     * Turns a flat entry list into the direct-children view of one folder:
     * a ".." row, files directly inside, and one synthetic row per subfolder.
     */
    public static Listing buildPaneListing(List<ZipEntryInfo> flat, File archive, String path,
                                           Predicate<ZipEntryInfo> notHidden) {
        List<ZipEntryInfo> entries = new ArrayList<>();
        ZipEntryInfo parent = null;
        java.util.HashSet<String> seenDirs = new java.util.HashSet<>();

        String parentPath = path == null ? "" : path;
        if (!parentPath.isEmpty() && !parentPath.endsWith("/")) parentPath += "/";
        if (parentPath.isEmpty()) {
            entries.add(new ZipEntryInfo("..", null, true, 0L, 0L, archive));
        } else {
            String parentDir = new File(path).getParent();
            if (parentDir == null) parentDir = "";
            String parentFull = parentDir.isEmpty() ? "" : parentDir.replaceAll("/+$", "") + "/";
            parent = new ZipEntryInfo("..", parentFull, true, 0L, 0L, archive);
            entries.add(parent);
        }

        for (ZipEntryInfo info : flat) {
            String entryPath = info.getFullPath();
            if (entryPath == null) continue;
            if (!entryPath.startsWith(parentPath) || entryPath.equals(parentPath)) continue;
            String rest = entryPath.substring(parentPath.length());
            int nextSlash = rest.indexOf('/');
            if (nextSlash == -1) {
                if (notHidden.test(info)) entries.add(info);
            } else {
                String childDirName = rest.substring(0, nextSlash + 1);
                String childFullPath = parentPath + childDirName;
                if (seenDirs.add(childFullPath)) {
                    // The row for a real directory entry carries its own
                    // timestamp; deeper entries only prove the folder exists.
                    long mtime = rest.equals(childDirName) ? info.getLastModified() : 0L;
                    ZipEntryInfo dirInfo = new ZipEntryInfo(
                            childDirName.substring(0, childDirName.length() - 1),
                            childFullPath, true, 0L, mtime, archive);
                    if (notHidden.test(dirInfo)) entries.add(dirInfo);
                }
            }
        }
        return new Listing(entries, parent);
    }

    private static String cacheKey(File archive) {
        return archive.getAbsolutePath() + "|" + archive.length() + "|" + archive.lastModified();
    }

    private static void postWrongPassword(Context context) {
        ZipPassword.toastWrongPassword(context);
    }

    private static List<ZipEntryInfo> listZip(File archive) throws IOException {
        List<ZipEntryInfo> out = new ArrayList<>();
        try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(archive)) {
            for (net.lingala.zip4j.model.FileHeader fh : zf.getFileHeaders()) {
                out.add(fromZipHeader(fh, archive));
            }
        }
        return out;
    }

    private static ZipEntryInfo fromZipHeader(net.lingala.zip4j.model.FileHeader fh, File archive) {
        String entryName = fh.getFileName().replace('\\', '/');
        boolean isDir = fh.isDirectory() || entryName.endsWith("/");
        String cleaned = isDir ? entryName.replaceAll("/+$", "") : entryName;
        String name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/') + 1);
        long size = fh.getUncompressedSize() < 0 ? 0 : fh.getUncompressedSize();
        long mtime = fh.getLastModifiedTimeEpoch() >= 0 ? fh.getLastModifiedTimeEpoch() : 0L;
        return new ZipEntryInfo(name, entryName, isDir, isDir ? 0L : size, mtime, archive);
    }

    private static List<ZipEntryInfo> list7z(File archive, char[] password) throws IOException {
        List<ZipEntryInfo> out = new ArrayList<>();
        try (SevenZFile sevenZFile = password == null
                ? new SevenZFile(archive) : new SevenZFile(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                out.add(from7z(entry, archive));
            }
        }
        return out;
    }

    private static ZipEntryInfo from7z(SevenZArchiveEntry entry, File archive) {
        String entryName = entry.getName().replace('\\', '/');
        boolean isDir = entry.isDirectory() || entryName.endsWith("/");
        String cleaned = isDir ? entryName.replaceAll("/+$", "") : entryName;
        String name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/') + 1);
        long size = entry.getSize() < 0 ? 0 : entry.getSize();
        long mtime = entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().getTime() : 0L;
        return new ZipEntryInfo(name, entryName, isDir, isDir ? 0L : size, mtime, archive);
    }

    private static List<ZipEntryInfo> listRar(File archive, char[] password) throws IOException {
        List<ZipEntryInfo> out = new ArrayList<>();
        try (Archive rar = password == null
                ? new Archive(archive) : new Archive(archive, new String(password))) {
            for (FileHeader fh : rar.getFileHeaders()) {
                out.add(fromRar(fh, archive));
            }
        } catch (com.github.junrar.exception.RarException e) {
            throw new IOException("Failed to read RAR archive", e);
        }
        return out;
    }

    private static ZipEntryInfo fromRar(FileHeader fh, File archive) {
        String entryName = fh.getFileName().replace('\\', '/');
        boolean isDir = fh.isDirectory() || entryName.endsWith("/");
        String cleaned = isDir ? entryName.replaceAll("/+$", "") : entryName;
        String name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/') + 1);
        long size = fh.getUnpSize() < 0 ? 0 : fh.getUnpSize();
        long mtime = fh.getMTime() != null ? fh.getMTime().getTime() : 0L;
        return new ZipEntryInfo(name, entryName, isDir, isDir ? 0L : size, mtime, archive);
    }

    private static List<ZipEntryInfo> listTar(File archive, InputStream tarInput) throws IOException {
        List<ZipEntryInfo> out = new ArrayList<>();
        try (TarArchiveInputStream tais = new TarArchiveInputStream(tarInput)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                boolean isDir = entry.isDirectory() || entryName.endsWith("/");
                String cleaned = isDir ? entryName.replaceAll("/+$", "") : entryName;
                String name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/') + 1);
                long mtime = entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().getTime() : 0L;
                out.add(new ZipEntryInfo(name, entryName, isDir, isDir ? 0L : Math.max(0, entry.getSize()),
                        mtime, archive));
            }
        }
        return out;
    }
}
