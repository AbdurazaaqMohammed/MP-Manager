package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.utils.deepopt.RepackUtil;

import java.io.File;
import java.util.Set;

/**
 * Fast repack of an APK: strip signatures, drop the junk-file set, enforce installer
 * alignment - a raw single-pass copy (no extraction, no re-deflation). Deep optimization
 * owns recompression; this path finishes in seconds.
 */
public class ApkOptimizer {

    public static File optimize(Context context, File apk, boolean deleteFiles,
                                SharedPreferences settings, APKLogger logger) throws Exception {
        Set<String> filesToDelete = deleteFiles ? settings.getStringSet("filesToDelete", null) : null;
        File optFile = FileUtils.getUnusedFile(apk.getPath().replace(".apk", "_opt.apk"));
        RepackUtil.repack(apk, optFile, filesToDelete, logger);
        return optFile;
    }
}
