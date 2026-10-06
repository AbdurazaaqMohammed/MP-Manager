package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.reandroid.apk.APKLogger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DexStringDecryptor.Options;
import io.github.abdurazaaqmohammed.utils.DexStringDecryptor.Outcome;

/**
 * MT's "DEX string decryption": run the static decrypter over every dex entry of an APK and
 * swap each recovered call site ({@code const-string cipher} → decrypt → {@code move-result})
 * for its plaintext const-string, dropping the now-dead invoke.
 *
 * <p>The whole APK is indexed first so a decrypt method living in a different dex entry can
 * still be resolved while any entry is being rewritten.
 */
public final class DexDecryptInjector {

    private DexDecryptInjector() {
    }

    public static File inject(Context context, File inputApk, Options opts,
                              APKLogger logger) throws Exception {
        File workDir = createTempDir(context, "ds");
        try {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(inputApk, Opcodes.getDefault());

            Map<String, ClassDef> global = new HashMap<>();
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
                        container.getEntry(entryName);
                if (entry == null) continue;
                for (ClassDef cls : entry.getDexFile().getClasses()) {
                    global.put(cls.getType(), cls);
                }
            }

            Map<String, File> replacements = new LinkedHashMap<>();
            int totalDecrypted = 0;
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
                        container.getEntry(entryName);
                if (entry == null) continue;
                DexBackedDexFile dex = entry.getDexFile();

                if (logger != null) {
                    logger.logMessage(context.getString(R.string.logger_patching, entryName));
                }
                int api = FastDexPatch.detectDexApi(inputApk, entryName);
                Outcome outcome = DexStringDecryptor.decryptDex(dex, global, api, opts);
                totalDecrypted += outcome.decrypted;
                if (outcome.decrypted == 0 || outcome.dexBytes == null) continue;

                File outDex = new File(workDir, entryName + ".ds");
                try (FileOutputStream fos = new FileOutputStream(outDex)) {
                    fos.write(outcome.dexBytes);
                }
                replacements.put(entryName, outDex);
            }

            if (totalDecrypted == 0) {
                throw new IOException(context.getString(R.string.dex_decrypt_none));
            }
            if (logger != null) {
                logger.logMessage(context.getString(R.string.dex_decrypt_count, totalDecrypted));
            }

            File outputFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                    FilenameUtils.getBaseName(inputApk.getName()) + "_ds.apk"));
            ApkZipAlignUtil.rebuildApk(inputApk, outputFile, replacements, null, null, null);
            if (logger != null) {
                logger.logMessage(context.getString(R.string.logger_saved_to,
                        outputFile.getName()));
            }
            return outputFile;
        } finally {
            deleteDirectory(workDir);
        }
    }

    private static File createTempDir(Context context, String prefix) {
        File dir = new File(context.getCacheDir(), prefix + "_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteDirectory(file);
                else //noinspection ResultOfMethodCallIgnored
                    file.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }
}
