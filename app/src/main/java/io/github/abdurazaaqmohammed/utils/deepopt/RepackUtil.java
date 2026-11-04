package io.github.abdurazaaqmohammed.utils.deepopt;

import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fast repack: single-pass raw copy through {@link ApkZipAlignUtil#rebuildApk} that drops
 * signature files and the junk-file set while enforcing installer-required STORED entries
 * and alignment. No extraction, no re-deflation (deep optimize owns compression), so it
 * runs in seconds even on huge APKs.
 */
public final class RepackUtil {

    private static final Pattern JUNK_SAFE = Pattern.compile(
            "^(?!res/|assets/|lib/|META-INF/services/|r/)(?!.*\\.dex$).*$");

    private RepackUtil() {
    }

    public static void repack(File inputApk, File outFile, Set<String> filesToDelete,
                              APKLogger logger) throws IOException {
        Set<String> skip = computeSkip(inputApk, filesToDelete);
        if (logger != null && !skip.isEmpty())
            logger.logMessage("Repack: dropping " + skip.size() + " entries");
        ApkZipAlignUtil.rebuildApk(inputApk, outFile, null, null, null, skip);
    }

    private static Set<String> computeSkip(File inputApk, Set<String> filesToDelete) throws IOException {
        Set<String> skip = new HashSet<>();
        try (ZipFile zip = new ZipFile(inputApk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (isSignatureFile(name) || isJunk(name, filesToDelete)) skip.add(name);
            }
        }
        return skip;
    }

    static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/")) return false;
        String entry = name.substring("META-INF/".length());
        return entry.endsWith(".SF") || entry.endsWith(".RSA") || entry.endsWith(".DSA")
                || entry.endsWith(".EC") || entry.equalsIgnoreCase("MANIFEST.MF");
    }

    static boolean isJunk(String name, Set<String> filesToDelete) {
        if (filesToDelete == null || filesToDelete.isEmpty()) return false;
        // Load-bearing paths are never junk, whatever the user's list says.
        if (name.startsWith("res/") || name.startsWith("assets/") || name.startsWith("lib/")
                || name.startsWith("META-INF/services/") || name.startsWith("r/")
                || name.endsWith(".dex") || name.equals("AndroidManifest.xml")
                || name.equals("resources.arsc")) return false;
        for (String fd : filesToDelete) {
            if (name.endsWith(fd) || name.matches(fd)) return true;
        }
        return false;
    }
}
