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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;

/**
 * Reads single entries out of 7z, rar and tar archives with the same
 * remembered/stored/prompted password loop the pane listing uses. Zip keeps
 * using the zip4j code paths already in place; whole-archive extraction stays
 * in ArchiveUtil.
 */
public final class ArchiveEntryIO {

    /** The requested path is not in the archive; never retried with another password. */
    public static final class NoSuchEntryException extends IOException {
        public NoSuchEntryException(String path) {
            super("Entry not found: " + path);
        }
    }

    private ArchiveEntryIO() {
    }

    /** True for the formats staged here; zip keeps using the zip4j code paths. */
    public static boolean handles(File archive) {
        return archive != null && ArchiveLister.isNonZipBrowsableName(archive.getName());
    }

    /** Stages one file to {@code outFile}. Returns false when the password prompt was cancelled. */
    public static boolean stage(Context context, ZipEntryInfo entry, File outFile) throws IOException {
        final File archive = entry.getZipFile();
        final String want = wantPath(entry);
        if (entry.isDirectory()) throw new NoSuchEntryException(want);
        final long mtime = entry.getLastModified();
        return withPasswords(context, archive,
                password -> stageFile(archive, want, mtime, outFile, password));
    }

    /**
     * Extracts one entry into {@code destDir}: a file lands at destDir/name,
     * a directory becomes destDir/name/<everything under the prefix>, exactly
     * like the zip4j path in FileOperationsHelper.extractZipEntry.
     *
     * @return false when the password prompt was cancelled
     */
    public static boolean extract(Context context, ZipEntryInfo entry, File destDir) throws IOException {
        final File archive = entry.getZipFile();
        String want = wantPath(entry);
        if (entry.isDirectory()) {
            if (!want.endsWith("/")) want += "/";
            final String prefix = want;
            final String dirName = entry.getName();
            return withPasswords(context, archive,
                    password -> extractTree(archive, prefix, dirName, destDir, password));
        }
        final String fileWant = want;
        final long mtime = entry.getLastModified();
        return withPasswords(context, archive,
                password -> stageFile(archive, fileWant, mtime, new File(destDir, entry.getName()), password));
    }

    private static String wantPath(ZipEntryInfo entry) {
        String path = entry.getFullPath();
        if (path == null) throw new NoSuchEntryException(entry.getName());
        return path.replace('\\', '/');
    }

    private interface Op {
        void run(char[] password) throws IOException;
    }

    /**
     * Tries the remembered, plain and stored passwords, then prompts until one
     * works. Every attempt probes the password by reading the first file entry
     * to the end before the operation writes anything, so a wrong guess never
     * leaves partial output behind; tar carries no passwords and skips the probe.
     *
     * @return false when the password prompt was cancelled
     */
    private static boolean withPasswords(Context context, File archive, Op op) throws IOException {
        List<String> candidates = new ArrayList<>();
        String memo = ZipPassword.remembered(archive);
        if (memo != null) candidates.add(memo);
        candidates.add(null);
        candidates.addAll(ArchivePasswordStore.list(context));
        IOException last = null;
        for (String pw : candidates) {
            char[] password = pw == null ? null : pw.toCharArray();
            try {
                probe(archive, password);
                op.run(password);
                if (pw != null) ZipPassword.remember(archive, pw);
                return true;
            } catch (NoSuchEntryException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            }
        }
        if (!PasswordedArchive.isEncryptedCandidate(archive)) {
            throw last != null ? last : new IOException("Cannot read " + archive.getName());
        }
        while (true) {
            String pw = ZipPassword.prompt(context);
            if (pw == null || pw.isEmpty()) return false;
            try {
                char[] password = pw.toCharArray();
                probe(archive, password);
                op.run(password);
                ZipPassword.remember(archive, pw);
                return true;
            } catch (NoSuchEntryException e) {
                throw e;
            } catch (IOException e) {
                ZipPassword.toastWrongPassword(context);
            }
        }
    }

    /**
     * Verifies the password before anything is written: open the archive and
     * read the first file entry to the end. Header-encrypted 7z and rar fail on
     * the open itself; content-only encryption fails while reading. A wrong
     * guess surfaces as IOException, which sends the caller to the next
     * candidate or back to the prompt.
     */
    private static void probe(File archive, char[] password) throws IOException {
        String n = archive.getName().toLowerCase(Locale.ROOT);
        if (isTarName(n)) return;
        if (n.endsWith(".7z")) {
            try (SevenZFile f = open7z(archive, password)) {
                SevenZArchiveEntry e;
                while ((e = f.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    try (InputStream is = f.getInputStream(e)) {
                        byte[] buf = new byte[8192];
                        while (is.read(buf) != -1) {
                        }
                    }
                    return;
                }
                return;
            }
        }
        if (n.endsWith(".rar")) {
            try (Archive r = openRar(archive, password)) {
                for (FileHeader fh : r.getFileHeaders()) {
                    if (fh.isDirectory()) continue;
                    r.extractFile(fh, DISCARD);
                    return;
                }
                return;
            } catch (com.github.junrar.exception.RarException e) {
                throw new IOException("Cannot read RAR archive", e);
            }
        }
    }

    private static void stageFile(File archive, String want, long mtime, File outFile, char[] password)
            throws IOException {
        String n = archive.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".7z")) {
            try (SevenZFile f = open7z(archive, password)) {
                SevenZArchiveEntry e;
                while ((e = f.getNextEntry()) != null) {
                    if (!want.equals(e.getName().replace('\\', '/'))) continue;
                    if (e.isDirectory()) throw new NoSuchEntryException(want);
                    copyOut(f.getInputStream(e), outFile);
                    applyTime(outFile, mtime > 0 ? mtime : dateMillis(e.getLastModifiedDate()));
                    return;
                }
                throw new NoSuchEntryException(want);
            }
        }
        if (n.endsWith(".rar")) {
            try (Archive r = openRar(archive, password)) {
                for (FileHeader fh : r.getFileHeaders()) {
                    if (!want.equals(fh.getFileName().replace('\\', '/'))) continue;
                    if (fh.isDirectory()) throw new NoSuchEntryException(want);
                    try (OutputStream os = openOut(outFile)) {
                        r.extractFile(fh, os);
                    }
                    if (fh.getMTime() != null) {
                        applyTime(outFile, mtime > 0 ? mtime : fh.getMTime().getTime());
                    }
                    return;
                }
                throw new NoSuchEntryException(want);
            } catch (com.github.junrar.exception.RarException e) {
                throw new IOException("Failed to read RAR entry", e);
            }
        }
        try (TarArchiveInputStream tais = new TarArchiveInputStream(openTar(archive))) {
            TarArchiveEntry e;
            while ((e = tais.getNextTarEntry()) != null) {
                if (!want.equals(e.getName().replace('\\', '/'))) continue;
                if (e.isDirectory()) throw new NoSuchEntryException(want);
                copyOut(tais, outFile);
                applyTime(outFile, mtime > 0 ? mtime : dateMillis(e.getLastModifiedDate()));
                return;
            }
            throw new NoSuchEntryException(want);
        }
    }

    private static void extractTree(File archive, String prefix, String dirName, File destDir, char[] password)
            throws IOException {
        String n = archive.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".7z")) {
            try (SevenZFile f = open7z(archive, password)) {
                SevenZArchiveEntry e;
                while ((e = f.getNextEntry()) != null) {
                    String name = e.getName().replace('\\', '/');
                    if (!underPrefix(name, prefix)) continue;
                    File out = treeTarget(destDir, dirName, prefix, name);
                    if (e.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.mkdirs();
                        continue;
                    }
                    copyOut(f.getInputStream(e), out);
                    applyTime(out, dateMillis(e.getLastModifiedDate()));
                }
            }
            return;
        }
        if (n.endsWith(".rar")) {
            try (Archive r = openRar(archive, password)) {
                for (FileHeader fh : r.getFileHeaders()) {
                    String name = fh.getFileName().replace('\\', '/');
                    if (!underPrefix(name, prefix)) continue;
                    File out = treeTarget(destDir, dirName, prefix, name);
                    if (fh.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.mkdirs();
                        continue;
                    }
                    try (OutputStream os = openOut(out)) {
                        r.extractFile(fh, os);
                    }
                    if (fh.getMTime() != null) applyTime(out, fh.getMTime().getTime());
                }
            } catch (com.github.junrar.exception.RarException e) {
                throw new IOException("Failed to read RAR entry", e);
            }
            return;
        }
        try (TarArchiveInputStream tais = new TarArchiveInputStream(openTar(archive))) {
            TarArchiveEntry e;
            while ((e = tais.getNextTarEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (!underPrefix(name, prefix)) continue;
                File out = treeTarget(destDir, dirName, prefix, name);
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                    continue;
                }
                copyOut(tais, out);
                applyTime(out, dateMillis(e.getLastModifiedDate()));
            }
        }
    }

    private static boolean underPrefix(String name, String prefix) {
        if (name.startsWith(prefix)) return true;
        return name.equals(prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix);
    }

    /** destDir/<entry name>/<part under the prefix>, same layout as the zip4j path. */
    private static File treeTarget(File destDir, String dirName, String prefix, String name) {
        String rel = name.startsWith(prefix) ? name.substring(prefix.length()) : "";
        return new File(destDir, ArchiveUtil.sanitizeEntryName(dirName + "/" + rel));
    }

    private static boolean isTarName(String lower) {
        return lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz");
    }

    private static SevenZFile open7z(File archive, char[] password) throws IOException {
        return password == null ? new SevenZFile(archive) : new SevenZFile(archive, password);
    }

    private static Archive openRar(File archive, char[] password) throws IOException {
        try {
            return password == null ? new Archive(archive) : new Archive(archive, new String(password));
        } catch (com.github.junrar.exception.RarException e) {
            throw new IOException("Cannot read RAR archive", e);
        }
    }

    private static InputStream openTar(File archive) throws IOException {
        String n = archive.getName().toLowerCase(Locale.ROOT);
        InputStream raw = new FileInputStream(archive);
        try {
            if (n.endsWith(".tar")) return raw;
            if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) return new GzipCompressorInputStream(raw, true);
            if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2")) return new BZip2CompressorInputStream(raw, true);
            if (n.endsWith(".tar.xz") || n.endsWith(".txz")) return new XZCompressorInputStream(raw, true);
        } catch (IOException e) {
            raw.close();
            throw e;
        }
        raw.close();
        throw new IOException("Unsupported archive format: " + archive.getName());
    }

    private static OutputStream openOut(File outFile) throws IOException {
        File parent = outFile.getParentFile();
        if (parent != null) parent.mkdirs();
        return new BufferedOutputStream(new FileOutputStream(outFile));
    }

    private static void copyOut(InputStream is, File outFile) throws IOException {
        try (InputStream in = is; OutputStream os = openOut(outFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    private static void applyTime(File file, long epoch) {
        if (epoch > 0) {
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(epoch);
        }
    }

    private static long dateMillis(Date when) {
        return when == null ? 0L : when.getTime();
    }

    private static final OutputStream DISCARD = new OutputStream() {
        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    };
}
