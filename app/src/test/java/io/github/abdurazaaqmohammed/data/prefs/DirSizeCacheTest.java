package io.github.abdurazaaqmohammed.data.prefs;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class DirSizeCacheTest {

    @Test
    public void tieredTtlGivesStaticSystemPartitionsLongerLifetime() {
        DirSizeCache.clearForTest();
        try {
            long sysTtl = DirSizeCache.maxAgeForPath("/system/app");
            long vendorTtl = DirSizeCache.maxAgeForPath("/vendor/lib64");
            long dataTtl = DirSizeCache.maxAgeForPath("/data/local/tmp");
            long appDataTtl = DirSizeCache.maxAgeForPath("/storage/emulated/0/Android/data");

            assertTrue(sysTtl > dataTtl);
            assertEquals(sysTtl, vendorTtl);
            assertEquals(dataTtl, appDataTtl);
        } finally {
            DirSizeCache.clearForTest();
        }
    }

    @Test
    public void recordBatchCooldownAndInFlightLifecycleWorkCleanly() {
        DirSizeCache.clearForTest();
        try {
            String path = "/storage/emulated/0/Android/obb/com.test.game";
            assertFalse(DirSizeCache.isInCooldown(path));
            assertTrue(DirSizeCache.tryClaimScan(path));
            assertFalse(DirSizeCache.tryClaimScan(path));
            assertTrue(DirSizeCache.isInFlight(path));

            DirSizeCache.recordCooldown(path, DirSizeCache.COOLDOWN_TIMEOUT_MS);
            assertFalse(DirSizeCache.isInFlight(path));
            assertTrue(DirSizeCache.isInCooldown(path));

            Map<String, Long> batch = new HashMap<>();
            batch.put(path, 4096L);
            DirSizeCache.recordBatch(batch);

            assertFalse(DirSizeCache.isInCooldown(path));
            assertFalse(DirSizeCache.isInFlight(path));
            assertEquals(Long.valueOf(4096L), DirSizeCache.peek(new File(path)));

            DirSizeCache.invalidatePrefix(new File("/storage/emulated/0/Android/obb"));
            assertNull(DirSizeCache.peek(new File(path)));
        } finally {
            DirSizeCache.clearForTest();
        }
    }

    @Test
    public void releaseScansFreesRemainingPaths() {
        DirSizeCache.clearForTest();
        try {
            assertTrue(DirSizeCache.tryClaimScan("/a"));
            assertTrue(DirSizeCache.tryClaimScan("/b"));
            DirSizeCache.releaseScans(Arrays.asList("/a", "/b"));
            assertFalse(DirSizeCache.isInFlight("/a"));
            assertFalse(DirSizeCache.isInFlight("/b"));
        } finally {
            DirSizeCache.clearForTest();
        }
    }

    @Test
    public void legacyV1ZeroEntriesPurgedWhileV2VerifiedZeroEntriesRetained() {
        DirSizeCache.clearForTest();
        try {
            File unreadableDir = new File("/nonexistent-dir-test-001") {
                @Override
                public boolean canRead() {
                    return false;
                }

                @Override
                public long lastModified() {
                    return 0L;
                }
            };
            long now = System.currentTimeMillis();
            DirSizeCache.recordWithVersionForTest(unreadableDir.getAbsolutePath(), 0L, now, 1);
            assertNull(DirSizeCache.peek(unreadableDir));

            DirSizeCache.recordBatch(Collections.singletonMap(unreadableDir.getAbsolutePath(), 0L));
            assertEquals(Long.valueOf(0L), DirSizeCache.peek(unreadableDir));
        } finally {
            DirSizeCache.clearForTest();
        }
    }
}
