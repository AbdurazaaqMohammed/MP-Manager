package io.github.abdurazaaqmohammed.util.io;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards for filesystem paths that must not be modified without extra care.
 */
public final class PathGuards {

    private PathGuards() {
    }

    // Directories that should never be deleted (absolute block)
    private static final Set<String> BLOCKED_DELETE_PATHS = new HashSet<>(Arrays.asList(
            "/", "/system", "/system_ext", "/vendor", "/product", "/odm", "/oem",
            "/boot", "/recovery", "/sbin", "/proc", "/sys", "/dev",
            "/cache", "/tmp", "/mnt", "/storage",
            "/data", "/data/data", "/data/user", "/data/user_de",
            "/data/system", "/data/app", "/data/dalvik-cache",
            "/sdcard"
    ));

    public static boolean isPathBlocked(String path) {
        if (path == null) return true;
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return BLOCKED_DELETE_PATHS.contains(normalized);
    }

    // Key directories: deletion is allowed but requires extra confirmation
    private static final Set<String> KEY_DIRECTORIES = new HashSet<>(Arrays.asList(
            "/data/data", "/data/user", "/data/user_de",
            "/data/system", "/data/system/packages.xml",
            "/system/build.prop", "/system/etc/hosts"
    ));

    public static boolean isPathInKeyDirectory(String path) {
        if (path == null) return false;
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        for (String key : KEY_DIRECTORIES) {
            if (normalized.equals(key) || normalized.startsWith(key + "/")) {
                return true;
            }
        }
        return false;
    }

    private static final String[] ROOT_ONLY_PREFIXES = {
            "/data", "/metadata", "/linkerconfig", "/bootstrap-apex",
            "/apex", "/config", "/cust", "/acct", "/dev",
            "/data_mirror", "/debug_ramdisk", "/mi_ext", "/root",
            "/mnt", "/mnt/media_rw"
    };

    public static boolean isRootOnlyPath(String path) {
        if (path == null) return false;
        String normalized = path.endsWith("/") && path.length() > 1
                ? path.substring(0, path.length() - 1) : path;
        for (String prefix : ROOT_ONLY_PREFIXES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + "/")) return true;
        }
        return false;
    }
}
