package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

public class ArchiveUtil {

    /** 64 KB: sweet spot between FUSE page size overhead and JVM GC pressure. */
    private static final int BUFFER_SIZE = 65536;

    public static boolean isMiuiOrAndroidBackup(File file) {
        if (file == null || file.isDirectory()) return false;
        try (InputStream is = FileUtils.getInputStream(file)) {
            byte[] buf = new byte[256];
            int n = is.read(buf);
            if (n < 11) return false;
            String header = new String(buf, 0, n, StandardCharsets.UTF_8);
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

    public static void extract(File archive, File destDir, boolean preserveTime, char[] password) throws IOException {
        extract(archive, destDir, preserveTime, password, null);
    }

    /**
     * Extracts an archive, optionally with a password for encrypted zip/7z.
     * A null password extracts normally; encrypted archives then fail with a
     * password exception that callers can use to prompt and retry.
     *
     * The format comes from the file's bytes via {@link ArchiveBrowseUtil},
     * not the file name, so extraction works on archives with missing or
     * wrong extensions too.
     *
     * @param listener receives the entry currently being written and the bytes
     *                 as they land, so a progress dialog can show a live rate
     */
    public static void extract(File archive, File destDir, boolean preserveTime, char[] password,
                               FileUtils.TransferListener listener) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        ArchiveBrowseUtil.ArchiveType type = ArchiveBrowseUtil.detectArchiveType(archive);
        switch (type) {
            case ZIP:
                extractZip(archive, destDir, preserveTime, password, listener);
                return;
            case SEVEN_Z:
                extract7z(archive, destDir, preserveTime, password, listener);
                return;
            case RAR:
                extractRar(archive, destDir, preserveTime, listener);
                return;
            case TAR:
                extractTar(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE), archive,
                        destDir, preserveTime, listener);
                return;
            case GZIP:
            case BZIP2:
            case XZ:
                if (ArchiveBrowseUtil.isTarPayload(archive, type)) {
                    extractTar(ArchiveBrowseUtil.openTar(archive, type), archive, destDir, preserveTime, listener);
                } else {
                    extractSingleCompressed(ArchiveBrowseUtil.openSingleCompressed(archive, type), archive,
                            destDir, ArchiveBrowseUtil.stripCompressorExtension(archive.getName()), listener);
                }
                return;
            default:
                if (isMiuiOrAndroidBackup(archive)) extractBackupArchive(archive, destDir, preserveTime, listener);
                else throw new IOException("Unsupported archive format: " + archive.getName());
        }
    }

    private static void extractBackupArchive(File archive, File destDir, boolean preserveTime,
                                             FileUtils.TransferListener listener) throws IOException {
        InputStream raw = new BufferedInputStream(FileUtils.getInputStream(archive));
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

        InputStream tarStream = isCompressed ? new InflaterInputStream(raw) : raw;
        extractTar(tarStream, archive, destDir, preserveTime, listener);
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

    public static void create(File output, List<File> sources) throws IOException {
        create(output, sources, null);
    }

    /**
     * @param listener receives every file written and the bytes as they land
     */
    public static void create(File output, List<File> sources, FileUtils.TransferListener listener) throws IOException {
        String lower = output.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".7z")) create7z(output, sources, listener);
        else if (lower.endsWith(".tar")) createTar(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE), sources, listener);
        else if (lower.endsWith(".tgz")) createTar(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources, listener);
        else if (lower.endsWith(".tar.gz")) createTar(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources, listener);
        else if (lower.endsWith(".tbz2")) createTar(new BZip2CompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources, listener);
        else if (lower.endsWith(".tar.bz2")) createTar(new BZip2CompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources, listener);
        else if (lower.endsWith(".txz")) createTar(new XZCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources, listener);
        else if (lower.endsWith(".tar.xz")) createTar(new XZCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources, listener);
        else if (lower.endsWith(".gz") && sources.size() == 1) compressSingle(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources.get(0), output, listener);
        else if (lower.endsWith(".bz2") && sources.size() == 1) compressSingle(new BZip2CompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources.get(0), output, listener);
        else if (lower.endsWith(".xz") && sources.size() == 1) compressSingle(new XZCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE)), sources.get(0), output, listener);
        else throw new IOException("Unsupported archive format: " + output.getName());
    }

    private static void extract7z(File archive, File destDir, boolean preserveTime, char[] password,
                                  FileUtils.TransferListener listener) throws IOException {
        // Opened through ArchiveBrowseUtil.openSevenZ: with core library
        // desugaring (NIO) the SevenZFile(File) machinery works below API 26.
        try (SevenZFile sevenZFile = ArchiveBrowseUtil.openSevenZ(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                File out = resolveDestFile(destDir, entry.getName());
                if (entry.isDirectory()) out.mkdirs();
                else {
                    if (listener != null) listener.onFile(archive, out);
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(sevenZFile.getInputStream(entry), os, listener);
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
    private static void extractZip(File archive, File destDir, boolean preserveTime, char[] password,
                                   FileUtils.TransferListener listener) throws IOException {
        try (ZipFile zf = new ZipFile(archive)) {
            if (password != null) zf.setPassword(password);
            // zip4j reports progress through its own monitor; the pump turns that
            // into byte deltas and the entry currently being written.
            ZipProgressPump pump = null;
            if (listener != null) {
                pump = new ZipProgressPump(zf.getProgressMonitor(), listener,
                        path -> listener.onFile(archive, new File(path)));
                pump.start();
            }
            try {
                zf.extractAll(destDir.getAbsolutePath());
            } finally {
                if (pump != null) pump.stop();
            }
        } catch (ZipException e) {
            if (password != null || isPasswordError(e)) throw e;
            ZipCompat.extractAll(archive, destDir, preserveTime);
        }
    }

    /**
     * True when the archive cannot be opened without a password. The format
     * is detected from content: zip checks the central-directory flag; 7z
     * (when only contents are encrypted) is detected by attempting to open it.
     * Other formats return false.
     */
    public static boolean needsPassword(File archive) throws IOException {
        ArchiveBrowseUtil.ArchiveType type = ArchiveBrowseUtil.detectArchiveType(archive);
        if (type == ArchiveBrowseUtil.ArchiveType.ZIP) {
            try (ZipFile zf = new ZipFile(archive)) {
                return zf.isEncrypted();
            } catch (ZipException e) {
                // zip4j cannot walk archives with unknown compression methods;
                // ask the central directory directly instead of failing.
                for (net.lingala.zip4j.model.FileHeader fh : ZipCompat.listHeaders(archive)) {
                    if (fh.isEncrypted()) return true;
                }
                return false;
            }
        }
        if (type == ArchiveBrowseUtil.ArchiveType.SEVEN_Z) {
            try (SevenZFile ignored = ArchiveBrowseUtil.openSevenZ(archive, null)) {
                return false;
            } catch (PasswordRequiredException e) {
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
            if (t instanceof ZipException
                    && ((ZipException) t).getType() == ZipException.Type.WRONG_PASSWORD)
                return true;
            if (t instanceof PasswordRequiredException) return true;
            if (t == t.getCause()) break;
        }
        return false;
    }

    private static void extractRar(File archive, File destDir, boolean preserveTime,
                                   FileUtils.TransferListener listener) throws IOException {
        try (Archive rar = new Archive(archive)) {
            FileHeader fh;
            while ((fh = rar.nextFileHeader()) != null) {
                File out = resolveDestFile(destDir, fh.getFileName());
                if (fh.isDirectory()) out.mkdirs();
                else {
                    if (listener != null) listener.onFile(archive, out);
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        rar.extractFile(fh, os);
                        if (listener != null) listener.onBytes(Math.max(0L, fh.getFullUnpackSize()));
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

    private static void extractTar(InputStream tarInput, File archive, File destDir, boolean preserveTime,
                                   FileUtils.TransferListener listener) throws IOException {
        try (TarArchiveInputStream tais = new TarArchiveInputStream(tarInput)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                File out = resolveDestFile(destDir, entry.getName());
                if (entry.isDirectory()) out.mkdirs();
                else {
                    if (listener != null) listener.onFile(archive, out);
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(tais, os, listener);
                    }
                    if (preserveTime && entry.getLastModifiedDate() != null) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(entry.getLastModifiedDate().getTime());
                    }
                }
            }
        }
    }

    private static void extractSingleCompressed(InputStream compressedInput, File archive, File destDir,
                                                String baseName, FileUtils.TransferListener listener) throws IOException {
        try (InputStream is = compressedInput) {
            File out = new File(destDir, baseName);
            if (listener != null) listener.onFile(archive, out);
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                copy(is, os, listener);
            }
        }
    }

    private static void compressSingle(OutputStream compressedOutput, File source, File output,
                                       FileUtils.TransferListener listener) throws IOException {
        try (OutputStream os = new BufferedOutputStream(compressedOutput)) {
            if (listener != null) listener.onFile(source, output);
            try (InputStream is = new BufferedInputStream(new FileInputStream(source))) {
                copy(is, os, listener);
            }
        }
    }

    private static void create7z(File output, List<File> sources, FileUtils.TransferListener listener) throws IOException {
        try (SevenZOutputFile sevenZOutput = new SevenZOutputFile(output)) {
            sevenZOutput.setContentCompression(SevenZMethod.LZMA2);
            for (File source : sources) addToSevenZ(sevenZOutput, source, source.isDirectory() ? source.getName() + "/" : source.getName(), output, listener);
        }
    }

    private static void addToSevenZ(SevenZOutputFile sevenZOutput, File file, String entryName, File output,
                                    FileUtils.TransferListener listener) throws IOException {
        SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(file, entryName);
        sevenZOutput.putArchiveEntry(entry);
        if (file.isDirectory()) {
            sevenZOutput.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToSevenZ(sevenZOutput, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""), output, listener);
        } else {
            if (listener != null) listener.onFile(file, output);
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copySevenZ(is, sevenZOutput, listener);
            }
            sevenZOutput.closeArchiveEntry();
        }
    }

    private static void copySevenZ(InputStream is, SevenZOutputFile os, FileUtils.TransferListener listener) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
            if (listener != null) listener.onBytes(length);
        }
    }

    private static void createTar(OutputStream tarOutput, List<File> sources, FileUtils.TransferListener listener) throws IOException {
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(tarOutput)) {
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            taos.setAddPaxHeadersForNonAsciiNames(true);
            for (File source : sources) addToTar(taos, source, source.isDirectory() ? source.getName() + "/" : source.getName(), listener);
        }
    }

    private static void addToTar(TarArchiveOutputStream taos, File file, String entryName,
                                 FileUtils.TransferListener listener) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
        taos.putArchiveEntry(entry);
        if (file.isDirectory()) {
            taos.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToTar(taos, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""), listener);
        } else {
            if (listener != null) listener.onFile(file, null);
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copy(is, taos, listener);
            }
            taos.closeArchiveEntry();
        }
    }

    /**
     * Resolves an archive entry name to a path relative to the destination.
     * Path components are processed left to right so a single pass cannot
     * resurrect "..": a crafted name like "....//file" (which the old
     * single-pass regex turned back into "..//file") is kept as the literal
     * name "..../file" instead of normalizing to "../file".
     */
    private static String sanitizeEntryName(String entryName) {
        String cleaned = entryName.replace('\\', '/');
        Deque<String> parts = new ArrayDeque<>();
        for (String part : cleaned.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) parts.pollLast();
            else parts.addLast(part);
        }
        return String.join("/", parts);
    }

    /**
     * Final traversal guard: the sanitized entry must resolve inside destDir.
     * Throws when an entry still tries to escape it.
     */
    private static File resolveDestFile(File destDir, String entryName) throws IOException {
        File out = new File(destDir, sanitizeEntryName(entryName));
        String canonicalDest = destDir.getCanonicalPath();
        String canonicalOut = out.getCanonicalPath();
        if (!canonicalOut.equals(canonicalDest) && !canonicalOut.startsWith(canonicalDest + File.separator)) {
            throw new IOException("Blocked archive entry escaping the destination: " + entryName);
        }
        return out;
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        copy(is, os, null);
    }

    private static void copy(InputStream is, OutputStream os, FileUtils.TransferListener listener) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
            if (listener != null) listener.onBytes(length);
        }
    }
}
