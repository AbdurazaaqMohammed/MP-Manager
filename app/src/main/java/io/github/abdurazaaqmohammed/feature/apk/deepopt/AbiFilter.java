package io.github.abdurazaaqmohammed.feature.apk.deepopt;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Architecture & ABI optimizer for APKs (matching MT Manager's architecture pruning feature).
 * Discovers native library ABIs and determines which entries should be pruned when
 * optimizing for a target ABI (e.g. arm64-v8a).
 */
public final class AbiFilter {

    public static final String ABI_ARM64_V8A = "arm64-v8a";
    public static final String ABI_ARMEABI_V7A = "armeabi-v7a";
    public static final String ABI_X86 = "x86";
    public static final String ABI_X86_64 = "x86_64";

    private AbiFilter() {
    }

    /**
     * Discovers all native library architectures present in the given APK.
     */
    public static Set<String> getAvailableAbis(File apk) throws IOException {
        Set<String> abis = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                String abi = extractAbi(name);
                if (abi != null) {
                    abis.add(abi);
                }
            }
        }
        return abis;
    }

    /**
     * Extracts the ABI component from a zip entry name (e.g. "lib/arm64-v8a/libfoo.so" -> "arm64-v8a").
     * Returns null if not a native library path.
     */
    public static String extractAbi(String entryName) {
        if (entryName == null || !entryName.startsWith("lib/")) return null;
        int firstSlash = entryName.indexOf('/');
        int secondSlash = entryName.indexOf('/', firstSlash + 1);
        if (secondSlash > firstSlash) {
            return entryName.substring(firstSlash + 1, secondSlash);
        }
        return null;
    }

    /**
     * Returns true if the given entry is a native library that should be pruned
     * because its ABI is not included in the target allowed ABIs.
     */
    public static boolean shouldPruneAbi(String entryName, Set<String> targetAbis) {
        if (targetAbis == null || targetAbis.isEmpty()) return false;
        String abi = extractAbi(entryName);
        if (abi == null) return false;
        return !targetAbis.contains(abi);
    }
}
