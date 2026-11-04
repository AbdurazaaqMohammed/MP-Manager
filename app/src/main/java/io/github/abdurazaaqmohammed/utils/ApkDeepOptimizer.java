package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.utils.deepopt.DeepOptOptions;
import io.github.abdurazaaqmohammed.utils.deepopt.DeepOptimizer;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Set;

public class ApkDeepOptimizer {

    public static File optimize(Context context, File inputApk, APKLogger logger) throws Exception {
        return optimize(context, inputApk, null, null, logger);
    }

    public static File optimize(Context context, File inputApk, Set<String> filesToDelete,
                                SharedPreferences settings, APKLogger logger) throws Exception {
        DeepOptOptions options = fromSettings(settings);
        File outFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                FilenameUtils.getBaseName(inputApk.getName()) + "_deep.apk"));
        return DeepOptimizer.optimize(inputApk, outFile, filesToDelete, options, logger);
    }

    public static DeepOptOptions fromSettings(SharedPreferences settings) {
        DeepOptOptions options = new DeepOptOptions();
        if (settings == null) return options;
        options.preserveDebug = settings.getBoolean("deep_opt_preserve_debug", true);
        options.maxPasses = Math.max(0, settings.getInt("deep_opt_max_passes", 5));
        options.verifyOutput = settings.getBoolean("deep_opt_verify_output", true);
        options.removeClasses = settings.getBoolean("deep_opt_remove_classes", true);
        options.removeMethods = settings.getBoolean("deep_opt_remove_methods", true);
        options.removeFields = settings.getBoolean("deep_opt_remove_fields", true);
        options.shortenNames = settings.getBoolean("deep_opt_phase_b", false);
        options.safeMode = settings.getBoolean("deep_opt_safe_mode", true);
        return options;
    }
}
