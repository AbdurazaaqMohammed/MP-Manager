package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.antik.AntikEnv;
import com.antik.DexPatcher.DexPatcher;
import com.antik.manifest.manifestP;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.WriteProgress;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Integrates RePairip (com.antik): removes Pairip (Google Play integrity/加固) protection from
 * APKs and split bundles (.apks/.xapk) by patching the manifest (drops split requirements,
 * LicenseActivity/LicenseContentProvider, CHECK_LICENSE) and neutralizing
 * StartupLauncher/SignatureCheck/LicenseClient classes in the dex.
 */
public class PairipRemoverUtil {

    public static File removePairip(Context context, File input) throws Exception {
        AntikEnv.assets = context.getAssets();
        AntikEnv.cacheDir = context.getCacheDir();

        ApkModule module;
        File tempDir = null;
        boolean bundle = !input.getName().toLowerCase(Locale.US).endsWith(".apk");

        if (bundle) {
            tempDir = new File(context.getCacheDir(), "pairip_merge_" + System.currentTimeMillis());
            //noinspection ResultOfMethodCallIgnored
            tempDir.mkdirs();
            extractApks(input, tempDir);
            ApkBundle apkBundle = new ApkBundle();
            apkBundle.loadApkDirectory(tempDir);
            module = apkBundle.mergeModules();
        } else {
            module = ApkModule.loadApkFile(input);
        }

        manifestP.patch(module);
        DexPatcher.patch(module);

        File outFile = FileUtils.getUnusedFile(new File(input.getParentFile(),
                FilenameUtils.getBaseName(input.getName()) + "_pairip.apk"));
        module.writeApk(outFile, (path, method, length) -> {});

        if (tempDir != null) deleteRecursive(tempDir);
        return outFile;
    }

    private static void extractApks(File bundleFile, File destDir) throws Exception {
        try (ZipFile zip = new ZipFile(bundleFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".apk")) continue;
                File outFile = new File(destDir, new File(entry.getName()).getName());
                try (InputStream is = zip.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                }
            }
        }
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursive(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
