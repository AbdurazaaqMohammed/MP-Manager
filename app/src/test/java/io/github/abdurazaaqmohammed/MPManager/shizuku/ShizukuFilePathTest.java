package io.github.abdurazaaqmohammed.MPManager.shizuku;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the Shizuku fallback path predicate. Anything absolute is reachable; the kernel (read-only
 * erofs mounts) and SELinux decide the rest, so this must stay permissive rather than drift back
 * into a whitelist. Mirrors {@code ShizukuManager.isAllowed}.
 */
public class ShizukuFilePathTest {

    @Test
    public void acceptsSystemPartitions() {
        assertTrue(ShizukuFile.isShellPath("/"));
        assertTrue(ShizukuFile.isShellPath("/system"));
        assertTrue(ShizukuFile.isShellPath("/system/build.prop"));
        assertTrue(ShizukuFile.isShellPath("/system_ext"));
        assertTrue(ShizukuFile.isShellPath("/vendor"));
        assertTrue(ShizukuFile.isShellPath("/product"));
        assertTrue(ShizukuFile.isShellPath("/odm"));
        assertTrue(ShizukuFile.isShellPath("/apex/com.android.runtime/etc"));
    }

    @Test
    public void acceptsDataPartitions() {
        assertTrue(ShizukuFile.isShellPath("/data"));
        assertTrue(ShizukuFile.isShellPath("/data/local/tmp"));
        assertTrue(ShizukuFile.isShellPath("/data/app/base.apk"));
    }

    @Test
    public void acceptsStorageVariants() {
        assertTrue(ShizukuFile.isShellPath("/storage"));
        assertTrue(ShizukuFile.isShellPath("/storage/emulated"));
        assertTrue(ShizukuFile.isShellPath("/storage/emulated/0"));
        assertTrue(ShizukuFile.isShellPath("/sdcard"));
        assertTrue(ShizukuFile.isShellPath("/storage/1A2B-3C4D"));
        assertTrue(ShizukuFile.isShellPath("/mnt/user/0/emulated/0/Download"));
        assertTrue(ShizukuFile.isShellPath("/mnt/media_rw"));
    }

    @Test
    public void acceptsPseudoFilesystems() {
        assertTrue(ShizukuFile.isShellPath("/proc"));
        assertTrue(ShizukuFile.isShellPath("/proc/self"));
        assertTrue(ShizukuFile.isShellPath("/sys"));
        assertTrue(ShizukuFile.isShellPath("/sys/devices"));
    }

    /** Relative and null paths are rejected so callers cannot be tricked out of the chrooted root. */
    @Test
    public void rejectsRelativeAndNullPaths() {
        assertFalse(ShizukuFile.isShellPath((String) null));
        assertFalse(ShizukuFile.isShellPath(""));
        assertFalse(ShizukuFile.isShellPath("system/build.prop"));
        assertFalse(ShizukuFile.isShellPath("./system"));
        assertFalse(ShizukuFile.isShellPath("../data"));
    }

    @Test
    public void fileOverloadMatchesStringOverload() {
        assertTrue(ShizukuFile.isShellPath(new java.io.File("/system")));
        assertTrue(ShizukuFile.isShellPath(new java.io.File("/storage/emulated/0/Download")));
        assertFalse(ShizukuFile.isShellPath((java.io.File) null));
    }
}