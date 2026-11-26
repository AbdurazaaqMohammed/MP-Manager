package io.github.abdurazaaqmohammed.feature.apk;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Pure unit checks for APK info cache identity and publication rules. */
public class ApkInfoCacheTest {

    @Test
    public void keyIncludesFileIdentity() throws Exception {
        File f = File.createTempFile("apk-info-cache", ".apk");
        f.deleteOnExit();
        String before = ApkInfoCache.identityForTest(f);
        assertNotNullKey(before);
        assertTrue(before.contains(String.valueOf(f.length())));
        assertTrue(f.setLastModified(1_600_000_000_000L));
        assertNotEquals(before, ApkInfoCache.identityForTest(f));
    }

    @Test
    public void distinctPathsHaveDistinctKeys() throws Exception {
        File a = File.createTempFile("apk-info-a", ".apk");
        File b = File.createTempFile("apk-info-b", ".apk");
        a.deleteOnExit();
        b.deleteOnExit();
        assertNotEquals(ApkInfoCache.identityForTest(a), ApkInfoCache.identityForTest(b));
    }

    @Test
    public void incompleteSnapshotsAreNotCached() throws Exception {
        File f = File.createTempFile("apk-info-cache-put", ".apk");
        f.deleteOnExit();
        ApkInfoCache.clearForTest();
        ApkInfoCache.Snapshot s = new ApkInfoCache.Snapshot();
        s.packageName = "com.example";
        ApkInfoCache.put(f, ApkInfoCache.keyOf(f), s);
        assertEquals(0, ApkInfoCache.sizeForTest());
    }

    @Test
    public void staleKeyCannotPublishOverReplacedFile() throws Exception {
        File f = File.createTempFile("apk-info-replace", ".apk");
        f.deleteOnExit();
        ApkInfoCache.clearForTest();
        String oldKey = ApkInfoCache.keyOf(f);
        assertTrue(java.nio.file.Files.size(f.toPath()) >= 0);
        try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
            out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        }
        assertTrue(f.setLastModified(1_700_000_000_000L));
        ApkInfoCache.Snapshot s = new ApkInfoCache.Snapshot();
        s.complete = true;
        s.packageName = "com.old";
        ApkInfoCache.put(f, oldKey, s);
        assertEquals(0, ApkInfoCache.sizeForTest());
        assertNull(ApkInfoCache.get(f, oldKey));
    }

    @Test
    public void completeSnapshotRoundTrips() throws Exception {
        File f = File.createTempFile("apk-info-roundtrip", ".apk");
        f.deleteOnExit();
        ApkInfoCache.clearForTest();
        String key = ApkInfoCache.keyOf(f);
        ApkInfoCache.Snapshot s = new ApkInfoCache.Snapshot();
        s.complete = true;
        s.packageName = "com.example.app";
        s.label = "Example";
        ApkInfoCache.put(f, key, s);
        assertEquals(1, ApkInfoCache.sizeForTest());
        assertSame(s, ApkInfoCache.get(f, key));
        assertFalse("com.other".equals(s.packageName));
    }

    private static void assertNotNullKey(String key) {
        assertTrue(key != null && !key.isEmpty());
    }
}
