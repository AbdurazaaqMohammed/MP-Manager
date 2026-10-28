package io.github.abdurazaaqmohammed.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;

public class FileUtils {

    /**
     * Called from the copying thread as work advances, so a progress dialog can
     * show the current item, its endpoints and a live byte rate.
     * Both methods are cheap and must never block.
     */
    public interface TransferListener {
        /** A new item is about to be written. {@code destination} may be null. */
        void onFile(File source, File destination);

        /** {@code count} more bytes were written. */
        void onBytes(long count);
    }

    /** File/byte totals for a set of roots, used to size a progress bar up front. */
    public static final class Measure {
        public int files;
        public long bytes;
        /** Directories walked, so a delete can count entries rather than files. */
        public int dirs;

        public Measure() {
        }

        public Measure(int files, long bytes) {
            this.files = files;
            this.bytes = bytes;
        }

        /** Every entry the walk will visit, which is what a delete removes. */
        public int entries() {
            return files + dirs;
        }
    }

    /**
     * A total plus the same numbers broken down per input path.
     *
     * <p>The breakdown is what lets an elevated batch copy report a running
     * counter: the shell script cannot measure anything itself while copying,
     * so each item's share is measured once up front and the copy step only has
     * to add those known shares together.
     */
    public static final class MeasureSet {
        public final Measure total;
        public final List<Measure> each;

        public MeasureSet(Measure total, List<Measure> each) {
            this.total = total;
            this.each = each;
        }
    }

    public static final String[] IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".ico", ".tiff", ".tif", ".heic", ".heif"};
    public static final String[] FONT_EXTS = {".ttf", ".otf", ".woff", ".woff2"};
    public static final String[] VIDEO_EXTS = {".mp4", ".mkv", ".webm", ".avi", ".3gp", ".mov", ".ts", ".m4v", ".flv", ".wmv"};
    public static final String[] AUDIO_EXTS = {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".opus"};
    public static final String[] ARCHIVE_EXTS = {".zip", ".rar", ".7z", ".tar", ".gz", ".bz2"};
    public static final String[] TEXT_EXTS = {".txt", ".log", ".xml", ".json", ".html", ".css", ".js", ".java", ".kt", ".md", ".smali", ".pro", ".gradle", ".properties"};
    public static final String[] HTML_EXTS = {".html", ".htm", ".xhtml"};

    public static boolean areFilesDifferent(File[] files1, File[] files2) throws IOException {
        if (files1 == null || files2 == null)
            return files1 != files2;
        if (files1.length != files2.length - 1)
            return true;
        for (int i = 0; i < files1.length; i++) {
            if (!files1[i].exists() || !files2[i + 1].exists() || files1[i].length() != files2[i + 1].length()) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchExt(String ext, String[] extensions) {
        for (String e : extensions) if (e.equals(ext)) return true;
        return false;
    }

    public static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTS) if (lower.endsWith(ext)) return true;
        return false;
    }
    public static boolean doesNotHaveStoragePerm(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? !Environment.isExternalStorageManager() : Build.VERSION.SDK_INT > 22 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED;
    }

    public static boolean isAxml(InputStream inputStream) throws IOException {
        if (inputStream == null) return false;
        boolean markSupported = inputStream.markSupported();
        InputStream is = markSupported ? inputStream
                : new BufferedInputStream(inputStream);
        is.mark(8);
        byte[] magic = new byte[8];
        int read = 0;
        try {
            while (read < 8) {
                int n = is.read(magic, read, 8 - read);
                if (n == -1) break;
                read += n;
            }
        } finally {
            try { is.reset(); } catch (IOException ignored) { }
        }
        if (read < 2) return false;
        // Binary Android XML magic: chunk type RES_XML_TYPE (0x0003) + header size 0x0008 (LE)
        // bytes: 03 00 08 00
        if (read >= 4 && (magic[0] & 0xFF) == 0x03 && (magic[1] & 0xFF) == 0x00
                && (magic[2] & 0xFF) == 0x08 && (magic[3] & 0xFF) == 0x00) {
            return true;
        }
        // Plain-text XML (even without <?xml header) starts with optional BOM/whitespace then '<'.
        // Binary garbage decoded as text could also contain '<', so magic check above is authoritative.
        int i = 0;
        // Skip UTF-8 BOM
        if (read >= 3 && (magic[0] & 0xFF) == 0xEF && (magic[1] & 0xFF) == 0xBB && (magic[2] & 0xFF) == 0xBF) i = 3;
        // Skip UTF-16 BOMs
        else if (read >= 2 && (((magic[0] & 0xFF) == 0xFF && (magic[1] & 0xFF) == 0xFE)
                || ((magic[0] & 0xFF) == 0xFE && (magic[1] & 0xFF) == 0xFF))) i = 2;
        while (i < read && (magic[i] == ' ' || magic[i] == '\t' || magic[i] == '\r' || magic[i] == '\n')) i++;
        if (i >= read) return false;
        // NUL byte in the first bytes => binary, but not AXML magic => not AXML
        for (int k = 0; k < read; k++) if (magic[k] == 0) return false;
        return false;
    }

    public static boolean isAxml(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream is = getInputStream(file)) {
            return isAxml(is);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAbx(InputStream inputStream) throws IOException {
        if (inputStream == null) return false;
        boolean markSupported = inputStream.markSupported();
        InputStream is = markSupported ? inputStream
                : new BufferedInputStream(inputStream);
        is.mark(8);
        byte[] magic = new byte[4];
        int read = 0;
        try {
            while (read < 4) {
                int n = is.read(magic, read, 4 - read);
                if (n == -1) break;
                read += n;
            }
        } finally {
            try { is.reset(); } catch (IOException ignored) { }
        }
        return read == 4 && (magic[0] & 0xFF) == 0x41 && (magic[1] & 0xFF) == 0x42
                && (magic[2] & 0xFF) == 0x58 && (magic[3] & 0xFF) == 0x00;
    }

    public static boolean isAbx(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream is = getInputStream(file)) {
            return isAbx(is);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean looksLikeAxmlText(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.charAt(0) != '<') return false;
        return t.contains("xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                || t.contains("xmlns:android='http://schemas.android.com/apk/res/android'")
                || t.contains("<manifest")
                || t.contains("<resources");
    }
     public static File copyFileFromAssetsAndGetFile(String fileName, Context context) throws IOException {
        File destinationFile = new File(context.getFilesDir(), fileName);
        if(!destinationFile.exists()) try(InputStream is = context.getAssets().open(fileName)) {
            copyFile(is, destinationFile);
        }
        return destinationFile;
    }
    
    public static File getDebugKeystore(Context context) throws IOException {
        File destinationFile = new File(context.getFilesDir(), "debug.keystore");
        if (!destinationFile.exists() || !isDebugKeystoreValid(destinationFile)) {
            try (InputStream is = context.getAssets().open("debug.keystore")) {
                copyFile(is, destinationFile);
            }
        }
        return destinationFile;
    }

    public static boolean isDebugKeystoreValid(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream is = new FileInputStream(file)) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(is, "android".toCharArray());
            return ks.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static File getUnusedFile(File file) {
        int i = 0;
        while(file.exists()) {
            i++;
            String fileName = file.getName();
            String extension = FilenameUtils.getExtension(fileName);
            file = new File(file.getParentFile(), fileName.replace('.' + extension, "").replaceFirst("_\\d+$", "") + '_' + i + '.' + extension);
        }
        return file;
    }

    public static File getUnusedFile(String file) {
        return getUnusedFile(new File(file));
    }

    public static void copyFolder(File src, File dest) throws IOException {
        copyFolder(src, dest, null);
    }

    /**
     * Recursive copy that reports every file it starts and every chunk it writes,
     * so a single walk can drive both the item counter and the byte rate.
     */
    public static void copyFolder(File src, File dest, TransferListener listener) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dest.mkdir();
            }
            String[] children = src.list();
            if (children == null) return;
            for (String child : children) {
                File srcChild = new File(src, child);
                File dstChild = new File(dest, child);
                copyFolder(srcChild, dstChild, listener);
            }
        } else {
            // dest might be the parent dir OR the exact target file (when called
            // from the recursive case above it is always the exact target file).
            File target = dest.isDirectory() ? new File(dest, src.getName()) : dest;
            copyFile(src, target, listener);
        }
    }

    public static OutputStream getOutputStream(String filepath) throws IOException {
        return getOutputStream(new File(filepath));
    }

    public static OutputStream getOutputStream(File file) throws IOException {
        return LegacyUtils.supportsFileChannel ?
        Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                : new FileOutputStream(file);
    }

    /**
     * Human-readable diagnostics for a failed create/rename/delete on
     * {@code target}: existence/writability of the target and its parent, plus
     * a hint when the path sits on a removable (secondary) storage volume,
     * which Android only lets apps write through SAF or an elevated backend.
     */
    public static String describeWriteProblem(Context context, File target) {
        StringBuilder sb = new StringBuilder("Path: ").append(target.getAbsolutePath());
        try {
            sb.append("\nExists: ").append(target.exists());
            File parent = target.getParentFile();
            if (parent == null) {
                sb.append("\nParent: (none)");
            } else {
                sb.append("\nParent exists: ").append(parent.exists());
                sb.append("\nParent writable: ").append(parent.canWrite());
            }
            sb.append("\nElevated access available: ").append(AccessManager.fileOpsOn(context));
        } catch (Exception ignored) {
        }
        if (isOnSecondaryVolume(target)) {
            sb.append("\nThis is a removable storage volume (SD card). Android restricts writes there; try root/Shizuku access, or on Android 10 the system only allows SAF access to it.");
        }
        return sb.toString();
    }

    /** True for paths like /storage/0000-0000/... (removable/secondary volumes). */
    public static boolean isOnSecondaryVolume(File f) {
        try {
            String p = f.getAbsolutePath();
            if (!p.startsWith("/storage/")) return false;
            String rest = p.substring("/storage/".length());
            int slash = rest.indexOf('/');
            String vol = slash == -1 ? rest : rest.substring(0, slash);
            return vol.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");
        } catch (Exception e) {
            return false;
        }
    }

    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        copyFile(sourceFile, destinationFile, null);
    }

    /**
     * Copies {@code sourceFile} to {@code destinationFile}.
     *
     * <p>With a listener the transfer is chunked at {@value #TRANSFER_CHUNK} so
     * the caller gets a byte counter that actually moves during a large file;
     * without one the original single-shot path is kept untouched.
     */
    public static void copyFile(File sourceFile, File destinationFile, TransferListener listener) throws IOException {
        if (listener != null) listener.onFile(sourceFile, destinationFile);
        // FileChannel.transferTo delegates to sendfile(2) on Linux — the kernel
        // copies data without any user-space buffer, making this the fastest
        // possible file copy on Android internal and shared storage.
        if (LegacyUtils.supportsFileChannel) {
            try (FileChannel src = FileChannel.open(sourceFile.toPath(), StandardOpenOption.READ);
                 FileChannel dst = FileChannel.open(destinationFile.toPath(),
                         StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {
                long size = src.size();
                long transferred = 0;
                while (transferred < size) {
                    long want = listener == null ? size - transferred
                            : Math.min(size - transferred, TRANSFER_CHUNK);
                    long written = src.transferTo(transferred, want, dst);
                    if (written <= 0) break;
                    transferred += written;
                    if (listener != null) listener.onBytes(written);
                }
                return;
            }
        }
        try (InputStream is = getInputStream(sourceFile);
             OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os, listener);
        }
    }

    public static boolean backupOnce(File original) {
        try {
            if (original == null || !original.isFile()) return false;
            File bak = new File(original.getPath() + ".bak");
            if (bak.exists()) return true;
            copyFile(original, bak);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void copyFile(File in, OutputStream os) throws IOException {
        try(InputStream is = getInputStream(in)) {
            copyFile(is, os);
        }
    }

    public static void copyFile(InputStream is, File destinationFile) throws IOException {
        try (OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
        }
    }

    /** I/O buffer size: 64 KB gives good throughput on FUSE / shared storage. */
    private static final int IO_BUFFER_SIZE = 65536;

    /** Granularity of the reported transfer progress: 1 MB keeps the UI smooth. */
    private static final long TRANSFER_CHUNK = 1024L * 1024L;

    public static void copyFile(InputStream is, OutputStream os) throws IOException {
        if (LegacyUtils.supportsWriteExternalStorage) {
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
        } else android.os.FileUtils.copy(is, os);
    }

    /** Streaming copy that reports every chunk it writes. */
    public static void copyFile(InputStream is, OutputStream os, TransferListener listener) throws IOException {
        byte[] buffer = new byte[IO_BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
            if (listener != null) listener.onBytes(length);
        }
    }

    public static InputStream getInputStream(File file) throws IOException {
        if (file instanceof ShizukuFile) {
            file = ShizukuFileOps.materialize(null, file);
        }
        return LegacyUtils.supportsFileChannel ?
                Files.newInputStream(file.toPath(), StandardOpenOption.READ)
                : new FileInputStream(file);
    }

    public static InputStream getInputStream(String filePath) throws IOException {
        return getInputStream(new File(filePath));
    }

    public static File getUnusedFile(File appFolder, String name) {
        return getUnusedFile(new File(appFolder, name));
    }

    /**
     * Counts the files a copy, move, delete or compress will touch, so a
     * progress dialog can show a real total instead of a spinner.
     *
     * <p>The walk happens before any work starts, so it stops at {@code cap}
     * entries; without that a huge tree would cost more than the operation it
     * is measuring. The result is always at least one so the progress bar has a
     * usable maximum.
     */
    /**
     * One walk that yields both the file count and the byte size of the work, so
     * the progress dialog can show a real total and a remaining-bytes figure
     * without paying for two traversals of the same tree.
     *
     * <p>The count matches {@link #countFilesForProgress(Collection, int)} exactly
     * (a directory with children counts as 0, an empty one as 1) and stops at
     * {@code cap} entries. The result always reports at least one file so a
     * progress bar has a usable maximum.
     */
    public static Measure measure(Collection<File> roots, int cap) {
        Measure acc = new Measure();
        if (roots == null) {
            acc.files = 1;
            return acc;
        }
        for (File root : roots) {
            if (acc.files >= cap) break;
            measureInto(root, cap, acc);
        }
        if (acc.files < 1) acc.files = 1;
        return acc;
    }

    public static Measure measure(File root, int cap) {
        Measure acc = new Measure();
        measureInto(root, cap, acc);
        if (acc.files < 1) acc.files = 1;
        return acc;
    }

    private static void measureInto(File root, int cap, Measure acc) {
        if (root == null || acc.files >= cap) return;
        File[] children = root.listFiles();
        if (children == null || children.length == 0) {
            acc.files += 1;
            acc.bytes += safeLength(root);
            if (root.isDirectory()) acc.dirs += 1;
            return;
        }
        if (root.isDirectory()) acc.dirs += 1;
        for (File child : children) {
            if (acc.files >= cap) break;
            if (child.isDirectory()) {
                measureInto(child, cap, acc);
            } else {
                acc.files += 1;
                acc.bytes += safeLength(child);
            }
        }
    }

    /**
     * Deletes {@code root} recursively, reporting every entry as it goes so a
     * progress dialog gets a counter that actually moves.
     *
     * <p>{@code Util.deleteDir} removes a whole tree in one opaque call, which is
     * why a delete used to jump from 0% to 100%: the caller had nothing to
     * report in between. Here each unlink is its own step.
     *
     * <p>Symlinks are removed, never followed, so a link pointing outside the
     * tree cannot take its target with it.
     *
     * @return true when nothing of the tree is left behind
     */
    public static boolean deleteTree(File root, TransferListener listener) {
        if (root == null) return true;
        if (listener != null) listener.onFile(root, null);

        // One lstat decides link/dir/file, so a large tree does not pay three
        // stat syscalls per entry just to be measured.
        int mode = lstatMode(root);
        boolean link = mode >= 0 && android.system.OsConstants.S_ISLNK(mode);
        boolean isDir = !link && (mode >= 0 ? android.system.OsConstants.S_ISDIR(mode) : root.isDirectory());
        boolean isFile = !link && (mode >= 0 ? android.system.OsConstants.S_ISREG(mode) : root.isFile());

        if (isDir) {
            File[] kids;
            try {
                kids = root.listFiles();
            } catch (Exception e) {
                kids = null;
            }
            if (kids != null) {
                for (File kid : kids) deleteTree(kid, listener);
            }
        }
        long size = isFile ? safeLength(root) : 0L;
        boolean gone = root.delete();
        if (gone && size > 0 && listener != null) listener.onBytes(size);
        return gone || !root.exists();
    }

    /** {@code st_mode} from lstat, or -1 when it cannot be read. */
    private static int lstatMode(File f) {
        try {
            return android.system.Os.lstat(f.getAbsolutePath()).st_mode;
        } catch (Exception e) {
            return -1;
        }
    }

    /** True for a symbolic link; {@code File.isDirectory()} would follow it. */
    public static boolean isSymlink(File f) {
        if (f == null) return false;
        return android.system.OsConstants.S_ISLNK(lstatMode(f));
    }

    private static long safeLength(File f) {
        try {
            return f.isFile() ? f.length() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public static int countFilesForProgress(Collection<File> roots, int cap) {
        int total = 0;
        if (roots == null) return 1;
        for (File root : roots) {
            if (total >= cap) break;
            total += countFilesForProgress(root, cap - total);
        }
        return Math.max(1, total);
    }

    public static int countFilesForProgress(File root, int cap) {
        if (root == null) return 0;
        if (cap <= 0) return 0;
        File[] children = root.listFiles();
        if (children == null || children.length == 0) return 1;
        int total = 0;
        for (File child : children) {
            if (total >= cap) break;
            total += child.isDirectory() ? countFilesForProgress(child, cap - total) : 1;
        }
        return total;
    }
}