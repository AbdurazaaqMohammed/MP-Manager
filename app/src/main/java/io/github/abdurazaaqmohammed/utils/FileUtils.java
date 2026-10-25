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
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.Collection;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;

public class FileUtils {
    public static final String[] IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".ico", ".tiff", ".tif", ".heic", ".heif"};
    public static final String[] FONT_EXTS = {".ttf", ".otf", ".woff", ".woff2"};
    public static final String[] VIDEO_EXTS = {".mp4", ".mkv", ".webm", ".avi", ".3gp", ".mov", ".ts", ".m4v", ".flv", ".wmv"};
    public static final String[] AUDIO_EXTS = {".mp3", ".wav", ".flac", ".ogg", ".m4a", ".aac", ".wma", ".opus"};
    public static final String[] ARCHIVE_EXTS = {".zip", ".rar", ".7z", ".tar", ".gz", ".bz2"};
    public static final String[] TEXT_EXTS = {".txt", ".log", ".xml", ".json", ".html", ".css", ".js", ".java", ".kt", ".md", ".smali", ".pro", ".gradle", ".properties"};

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
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdir();
            }

            String[] files = src.list();
            if (files == null) return;

            for (String file : files) {
                File f = new File(file);
                copyFolder(new File(src, file), f.isDirectory() ? new File(dest, file) : (dest));
            }
        } else {
            File copy = new File(dest, src.getName());
            copyFile(src, copy);
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
        try (InputStream is = getInputStream(sourceFile);
             OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
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

    public static void copyFile(InputStream is, OutputStream os) throws IOException {
        if(LegacyUtils.supportsWriteExternalStorage) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
        } else android.os.FileUtils.copy(is, os);
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