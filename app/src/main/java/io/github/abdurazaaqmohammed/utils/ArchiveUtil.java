package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import net.lingala.zip4j.ZipFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

public class ArchiveUtil {

    private static final int BUFFER_SIZE = 8192;

    public static boolean isMiuiOrAndroidBackup(File file) {
        if (file == null || !file.exists() || file.isDirectory()) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[256];
            int n = fis.read(buf);
            if (n < 16) return false;
            String header = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
            return header.startsWith("MIUI BACKUP") || header.startsWith("ANDROID BACKUP");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isSupportedArchive(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".tar")
                || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz")
                || lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz");
    }

    public static String[] getSupportedCreateExts() {
        return new String[] { ".zip", ".7z", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".gz", ".bz2", ".xz" };
    }

    public static void extract(File archive, File destDir) throws IOException {
        extract(archive, destDir, false);
    }

    public static void extract(File archive, File destDir, boolean preserveTime) throws IOException {
        extract(archive, destDir, preserveTime, null);
    }

    /**
     * Extracts an archive, optionally with a password for encrypted zip/7z.
     * A null password extracts normally; encrypted archives then fail with a
     * password exception that callers can use to prompt and retry.
     */
    public static void extract(File archive, File destDir, boolean preserveTime, char[] password) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) extractZip(archive, destDir, preserveTime, password);
        else if (lower.endsWith(".7z")) extract7z(archive, destDir, preserveTime, password);
        else if (lower.endsWith(".rar")) extractRar(archive, destDir, preserveTime);
        else if (lower.endsWith(".tar")) extractTar(new FileInputStream(archive), destDir, preserveTime);
        else if (lower.endsWith(".tgz")) extractTar(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tar.gz")) extractTar(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tbz2")) extractTar(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tar.bz2")) extractTar(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".txz")) extractTar(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tar.xz")) extractTar(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".gz")) extractSingleCompressed(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".gz");
        else if (lower.endsWith(".bz2")) extractSingleCompressed(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".bz2");
        else if (lower.endsWith(".xz")) extractSingleCompressed(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".xz");
        else if (isMiuiOrAndroidBackup(archive)) extractBackupArchive(archive, destDir, preserveTime);
        else throw new IOException("Unsupported archive format: " + archive.getName());
    }

    private static void extractBackupArchive(File archive, File destDir, boolean preserveTime) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(archive));
        // Consume text header lines until tar payload starts
        int linesToSkip = 0;
        is.mark(1024);
        byte[] probe = new byte[256];
        int read = is.read(probe);
        is.reset();
        String headStr = new String(probe, 0, Math.max(0, read), java.nio.charset.StandardCharsets.UTF_8);
        if (headStr.startsWith("MIUI BACKUP")) {
            linesToSkip = 9;
        } else if (headStr.startsWith("ANDROID BACKUP")) {
            linesToSkip = 4;
        }
        for (int i = 0; i < linesToSkip; i++) {
            while (true) {
                int b = is.read();
                if (b == -1 || b == '\n') break;
            }
        }
        extractTar(is, destDir, preserveTime);
    }

    public static void create(File output, List<File> sources) throws IOException {
        String lower = output.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".7z")) create7z(output, sources);
        else if (lower.endsWith(".tar")) createTar(new FileOutputStream(output), sources);
        else if (lower.endsWith(".tgz")) createTar(new GzipCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tar.gz")) createTar(new GzipCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tbz2")) createTar(new BZip2CompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tar.bz2")) createTar(new BZip2CompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".txz")) createTar(new XZCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tar.xz")) createTar(new XZCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".gz") && sources.size() == 1) compressSingle(new GzipCompressorOutputStream(new FileOutputStream(output)), sources.get(0));
        else if (lower.endsWith(".bz2") && sources.size() == 1) compressSingle(new BZip2CompressorOutputStream(new FileOutputStream(output)), sources.get(0));
        else if (lower.endsWith(".xz") && sources.size() == 1) compressSingle(new XZCompressorOutputStream(new FileOutputStream(output)), sources.get(0));
        else throw new IOException("Unsupported archive format: " + output.getName());
    }

    private static void extract7z(File archive, File destDir, boolean preserveTime, char[] password) throws IOException {
        try (SevenZFile sevenZFile = password == null ? new SevenZFile(archive) : new SevenZFile(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                String name = sanitizeEntryName(entry.getName());
                File out = new File(destDir, name);
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(sevenZFile.getInputStream(entry), os);
                    }
                    if (preserveTime && entry.getLastModifiedDate() != null) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(entry.getLastModifiedDate().getTime());
                    }
                }
            }
        }
    }

    /**
     * Zip extraction via zip4j: extractAll handles nested folders, mtimes and
     * parent dirs, and refuses entries escaping the destination (canonical
     * path check), so no manual traversal sanitizing is needed here.
     *
     * Archives using a compression method zip4j does not implement (XZ,
     * Zstandard, bzip2, LZMA) abort its central-directory read, so the same
     * guarantee is provided by {@link ZipCompat} for those.
     */
    private static void extractZip(File archive, File destDir, boolean preserveTime, char[] password) throws IOException {
        try (ZipFile zf = new ZipFile(archive)) {
            if (password != null) zf.setPassword(password);
            zf.extractAll(destDir.getAbsolutePath());
        } catch (net.lingala.zip4j.exception.ZipException e) {
            if (password != null || isPasswordError(e)) throw e;
            ZipCompat.extractAll(archive, destDir, preserveTime);
        }
    }

    /**
     * True when the archive cannot be opened without a password. Zip checks
     * the central-directory flag; 7z (when only contents are encrypted) is
     * detected by attempting to open it. Other formats return false.
     */
    public static boolean needsPassword(File archive) throws IOException {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            try (ZipFile zf = new ZipFile(archive)) {
                return zf.isEncrypted();
            } catch (net.lingala.zip4j.exception.ZipException e) {
                // zip4j cannot walk archives with unknown compression methods;
                // ask the central directory directly instead of failing.
                for (net.lingala.zip4j.model.FileHeader fh : ZipCompat.listHeaders(archive)) {
                    if (fh.isEncrypted()) return true;
                }
                return false;
            }
        }
        if (lower.endsWith(".7z")) {
            try (SevenZFile ignored = new SevenZFile(archive)) {
                return false;
            } catch (org.apache.commons.compress.PasswordRequiredException e) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the failure is a password problem (missing or wrong), as
     * opposed to a corrupted archive or I/O error, so the caller can re-prompt.
     */
    public static boolean isPasswordError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof net.lingala.zip4j.exception.ZipException
                    && ((net.lingala.zip4j.exception.ZipException) t).getType() == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD)
                return true;
            if (t instanceof org.apache.commons.compress.PasswordRequiredException) return true;
            if (t == t.getCause()) break;
        }
        return false;
    }

    private static void extractRar(File archive, File destDir, boolean preserveTime) throws IOException {
        try (Archive rar = new Archive(archive)) {
            FileHeader fh;
            while ((fh = rar.nextFileHeader()) != null) {
                String name = sanitizeEntryName(fh.getFileName());
                File out = new File(destDir, name);
                if (fh.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        rar.extractFile(fh, os);
                    }
                    if (preserveTime && fh.getMTime() != null) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(fh.getMTime().getTime());
                    }
                }
            }
        } catch (RarException e) {
            throw new IOException("Failed to extract RAR archive", e);
        }
    }

    private static void extractTar(InputStream tarInput, File destDir, boolean preserveTime) throws IOException {
        try (TarArchiveInputStream tais = new TarArchiveInputStream(tarInput)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                String name = sanitizeEntryName(entry.getName());
                File out = new File(destDir, name);
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(tais, os);
                    }
                    if (preserveTime && entry.getLastModifiedDate() != null) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(entry.getLastModifiedDate().getTime());
                    }
                }
            }
        }
    }

    private static void extractSingleCompressed(InputStream compressedInput, File destDir, String archiveName, String ext) throws IOException {
        try (InputStream is = compressedInput) {
            String base = archiveName.substring(0, archiveName.length() - ext.length());
            File out = new File(destDir, base);
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                copy(is, os);
            }
        }
    }

    private static void compressSingle(OutputStream compressedOutput, File source) throws IOException {
        try (OutputStream os = new BufferedOutputStream(compressedOutput)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(source))) {
                copy(is, os);
            }
        }
    }

    private static void create7z(File output, List<File> sources) throws IOException {
        try (SevenZOutputFile sevenZOutput = new SevenZOutputFile(output)) {
            sevenZOutput.setContentCompression(SevenZMethod.LZMA2);
            for (File source : sources) addToSevenZ(sevenZOutput, source, source.isDirectory() ? source.getName() + "/" : source.getName());
        }
    }

    private static void addToSevenZ(SevenZOutputFile sevenZOutput, File file, String entryName) throws IOException {
        SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(file, entryName);
        sevenZOutput.putArchiveEntry(entry);
        if (file.isDirectory()) {
            sevenZOutput.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToSevenZ(sevenZOutput, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""));
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copySevenZ(is, sevenZOutput);
            }
            sevenZOutput.closeArchiveEntry();
        }
    }

    private static void copySevenZ(InputStream is, SevenZOutputFile os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
    }

    private static void createTar(OutputStream tarOutput, List<File> sources) throws IOException {
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(tarOutput)) {
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            taos.setAddPaxHeadersForNonAsciiNames(true);
            for (File source : sources) addToTar(taos, source, source.isDirectory() ? source.getName() + "/" : source.getName());
        }
    }

    private static void addToTar(TarArchiveOutputStream taos, File file, String entryName) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
        taos.putArchiveEntry(entry);
        if (file.isDirectory()) {
            taos.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToTar(taos, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""));
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copy(is, taos);
            }
            taos.closeArchiveEntry();
        }
    }

    private static String sanitizeEntryName(String entryName) {
        String cleaned = entryName.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        cleaned = cleaned.replaceAll("\\.\\./", "");
        return cleaned;
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
    }
}
