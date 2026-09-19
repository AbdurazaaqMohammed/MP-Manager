package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

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
            zipParameters.setCompressionLevel(CompressionLevel.MAXIMUM);
            Set<String> filesToDelete;
            ZipParameters zpF = new ZipParameters();
            if (deleteFiles && (filesToDelete = settings.getStringSet("filesToDelete", null)) != null)
                zpF.setExcludeFileFilter(file2 -> {
                    String path = file2.getPath();
                    for (String fd : filesToDelete) if (path.endsWith(fd) || path.matches(fd)) return true;
                    return false;
                });
            List<File> lf = net.lingala.zip4j.util.FileUtils.getFilesInDirectoryRecursive(tempFolder, zpF);
            for (File f : lf) if (!f.isDirectory()) {
                String relativePath = f.getPath().replace(tempFolder.getPath() + File.separatorChar, "");
                if (relativePath.equals(amS) || relativePath.equals(rssS)) continue;
                logger.logMessage(context.getString(R.string.adding, relativePath));
                ZipParameters params = new ZipParameters(zipParameters);
                if (relativePath.startsWith("res/") && !relativePath.endsWith(".xml")) {
                    params.setCompressionLevel(CompressionLevel.NO_COMPRESSION);
                    params.setCompressionMethod(CompressionMethod.STORE);
                }
                params.setFileNameInZip(relativePath);
                opt.addFile(f, params);
            }
        }
        return optFile;
    }
}
