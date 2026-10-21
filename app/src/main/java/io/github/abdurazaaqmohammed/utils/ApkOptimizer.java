package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.MPManager.R;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ApkOptimizer {

    public static File optimize(Context context, File apk, boolean deleteFiles, SharedPreferences settings, APKLogger logger) throws Exception {
        return optimize(context, apk, deleteFiles, settings, false, logger);
    }

    public static File optimize(Context context, File apk, boolean deleteFiles, SharedPreferences settings, boolean ultraCompress, APKLogger logger) throws Exception {
        String fileName = apk.getName();
        String filePath = apk.getPath();
        File tempFolder = new File(context.getCacheDir(), System.currentTimeMillis() + '_' + fileName);
        File optFile = FileUtils.getUnusedFile(filePath.replace(".apk", "_opt.apk"));
        try (ZipFile zf = new ZipFile(apk); ZipFile opt = new ZipFile(optFile)) {
            zf.extractAll(tempFolder.getPath());
            ZipParameters zp = new ZipParameters();
            zp.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
            zp.setCompressionMethod(CompressionMethod.STORE);
            String amS = "AndroidManifest.xml";
            File am = new File(tempFolder, amS);
            logger.logMessage(context.getString(R.string.adding, amS));
            opt.addFile(am, zp);
            am.delete();
            String rssS = "resources.arsc";
            File rss = new File(tempFolder, rssS);
            if (rss.exists()) {
                logger.logMessage(context.getString(R.string.adding, rssS));
                opt.addFile(rss, zp);
                rss.delete();
            }
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            zipParameters.setCompressionLevel(
                    ultraCompress ? CompressionLevel.ULTRA : CompressionLevel.MAXIMUM);
            Set<String> filesToDelete;
            ZipParameters zpF = new ZipParameters();
            if (deleteFiles && (filesToDelete = settings.getStringSet("filesToDelete", null)) != null)
                zpF.setExcludeFileFilter(file2 -> {
                    String path = file2.getPath();
                    for (String fd : filesToDelete) if (path.endsWith(fd) || path.matches(fd)) return true;
                    return false;
                });
            List<File> lf = net.lingala.zip4j.util.FileUtils.getFilesInDirectoryRecursive(tempFolder, zpF);
            for (File f : lf) {
                if (f.isDirectory()) continue;
                String relativePath = f.getPath().replace(tempFolder.getPath() + File.separatorChar, "");
                if (relativePath.equals(amS) || relativePath.equals(rssS)) continue;
                if (isSignatureFile(relativePath) || relativePath.equals("META-INF/MANIFEST.MF")) continue;
                logger.logMessage(context.getString(R.string.adding, relativePath));
                ZipParameters params = new ZipParameters(zipParameters);
                if (mustStore(relativePath)) {
                    params.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
                    params.setCompressionMethod(CompressionMethod.STORE);
                }
                params.setFileNameInZip(relativePath);
                opt.addFile(f, params);
            }
        } finally {
            deleteRecursive(tempFolder);
        }
        ApkZipAlignUtil.ensureInstallable(optFile);
        return optFile;
    }

    private static boolean mustStore(String relativePath) {
        return ApkZipAlignUtil.mustStore(relativePath)
                || (relativePath.startsWith("lib/") && relativePath.endsWith(".so"));
    }

    private static boolean isSignatureFile(String relativePath) {
        if (!relativePath.startsWith("META-INF/")) return false;
        String name = relativePath.substring("META-INF/".length());
        return name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") || name.endsWith(".SF");
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] kids = file.listFiles();
        if (kids != null) for (File kid : kids) deleteRecursive(kid);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
