package io.github.abdurazaaqmohammed.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class FileUtils {
    public static final String[] IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".ico", ".tiff", ".tif", ".heic", ".heif"};
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
        try (InputStreamReader isr = new InputStreamReader(inputStream); BufferedReader abr = new BufferedReader(isr)) {
            return !abr.readLine().startsWith("<?xml version=");
        }
    }
     public static File copyFileFromAssetsAndGetFile(String fileName, Context context) throws IOException {
        File destinationFile = new File(context.getFilesDir(), fileName);
        if(!destinationFile.exists()) try(InputStream is = context.getAssets().open(fileName)) {
            copyFile(is, destinationFile);
        }
        return destinationFile;
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
        Files.newOutputStream(file.toPath(), java.nio.file.StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                : new FileOutputStream(file);
    }

    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        try (InputStream is = getInputStream(sourceFile);
             OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
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
}