package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the Shizuku file-operation policy. Access is intentionally unrestricted for absolute
 * paths (the kernel and SELinux are the real gate), so what matters here is that the destructive
 * guards still hold. Mirrors {@code ShizukuFileService.allowed}/{@code topLevel()}.
 */
public class ShizukuManagerPathTest {

    @Test
    public void allowsEveryAbsolutePath() {
        assertTrue(ShizukuManager.isAllowed("/"));
        assertTrue(ShizukuManager.isAllowed("/system"));
        assertTrue(ShizukuManager.isAllowed("/system/build.prop"));
        assertTrue(ShizukuManager.isAllowed("/vendor"));
        assertTrue(ShizukuManager.isAllowed("/product"));
        assertTrue(ShizukuManager.isAllowed("/odm"));
        assertTrue(ShizukuManager.isAllowed("/data/local/tmp"));
        assertTrue(ShizukuManager.isAllowed("/storage/emulated/0/Download"));
        assertTrue(ShizukuManager.isAllowed("/storage/1A2B-3C4D"));
        assertTrue(ShizukuManager.isAllowed("/proc"));
        assertTrue(ShizukuManager.isAllowed("/sys/devices"));
    }

    @Test
    public void rejectsRelativeAndNullPaths() {
        assertFalse(ShizukuManager.isAllowed(null));
        assertFalse(ShizukuManager.isAllowed(""));
        assertFalse(ShizukuManager.isAllowed("system"));
        assertFalse(ShizukuManager.isAllowed("./system"));
    }

    /** Path traversal must collapse inside the real root instead of escaping it. */
    @Test
    public void normalizesTraversalRatherThanRejecting() {
        assertTrue(ShizukuManager.isAllowed("/system/../data"));
        assertTrue(ShizukuManager.isAllowed("/../.."));
    }

    /**
     * Widening access must not widen the destructive-action guard: writing to, deleting or
     * renaming the Android roots and the /storage containers stays refused, contents stay free.
     */
    @Test
    public void keepsProtectedTopLevelGuard() {
        assertTrue(ShizukuManager.isTopLevel("/storage/emulated/0/Android/data"));
        assertTrue(ShizukuManager.isTopLevel("/storage/emulated/0/Android/obb"));
        assertTrue(ShizukuManager.isTopLevel("/storage/emulated/0/Android/media"));
        assertTrue(ShizukuManager.isTopLevel("/sdcard/Android/data"));
        assertTrue(ShizukuManager.isTopLevel("/storage"));
        assertTrue(ShizukuManager.isTopLevel("/storage/emulated"));
        assertTrue(ShizukuManager.isTopLevel("/storage/self/primary"));
    }

    @Test
    public void doesNotProtectTopLevelDescendants() {
        assertFalse(ShizukuManager.isTopLevel("/storage/emulated/0/Android/data/com.foo"));
        assertFalse(ShizukuManager.isTopLevel("/storage/emulated/0/Android/data/com.foo/files/a.txt"));
        assertFalse(ShizukuManager.isTopLevel("/storage/emulated/0/Download"));
        assertFalse(ShizukuManager.isTopLevel("/storage/emulated/0"));
    }

    /** OTA staging mounts are brick-risk, so they stay out of delete/rename in both modes. */
    @Test
    public void blocksOtaStagingMounts() {
        assertTrue(RootManager.isPathBlocked("/mnt/installer"));
        assertTrue(RootManager.isPathBlocked("/mnt/androidwritable"));
        assertTrue(RootManager.isPathBlocked("/mnt/installer/"));
        assertTrue(RootManager.isPathBlocked("/mnt"));
    }

    @Test
    public void keepsOtherCriticalPathsBlocked() {
        assertTrue(RootManager.isPathBlocked("/"));
        assertTrue(RootManager.isPathBlocked("/system"));
        assertTrue(RootManager.isPathBlocked("/data"));
        assertTrue(RootManager.isPathBlocked("/vendor"));
        assertTrue(RootManager.isPathBlocked("/storage"));
        assertTrue(RootManager.isPathBlocked(null));
    }
}