package io.github.abdurazaaqmohammed.data.prefs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MinimizedEditorsTest {

    @Before
    @After
    public void resetRegistry() {
        MinimizedEditors.clearForTest();
    }

    @Test
    public void retainsParkedEntriesUntilOneHourExpiryAndDiscardsOnExpiration() {
        AtomicInteger dexDiscards = new AtomicInteger();
        AtomicInteger textDiscards = new AtomicInteger();
        long t0 = 1_000_000L;

        MinimizedEditors.addForTest(10, "Dex Editor+ – classes.dex", dexDiscards::incrementAndGet, t0);
        MinimizedEditors.addForTest(10, "Text Editor – AndroidManifest.xml", textDiscards::incrementAndGet, t0 + 10_000L);

        // Switching away and returning after 59 minutes must keep both parked items alive.
        List<MinimizedEditors.Item> beforeExpiry =
                MinimizedEditors.getItemsForTest(t0 + TimeUnit.MINUTES.toMillis(59));
        assertEquals(2, beforeExpiry.size());
        assertEquals("Text Editor – AndroidManifest.xml", beforeExpiry.get(0).title);
        assertEquals(0, dexDiscards.get());
        assertEquals(0, textDiscards.get());

        // Exactly at 1 hour from the first entry's timestamp, only the expired one is purged.
        List<MinimizedEditors.Item> partialExpiry =
                MinimizedEditors.getItemsForTest(t0 + MinimizedEditors.EXPIRY_MS);
        assertEquals(1, partialExpiry.size());
        assertEquals("Text Editor – AndroidManifest.xml", partialExpiry.get(0).title);
        assertEquals(1, dexDiscards.get());
        assertEquals(0, textDiscards.get());

        // After the second entry hits 1 hour, it is expired and discarded too.
        List<MinimizedEditors.Item> allExpired =
                MinimizedEditors.getItemsForTest(t0 + 10_000L + MinimizedEditors.EXPIRY_MS);
        assertTrue(allExpired.isEmpty());
        assertEquals(1, textDiscards.get());
    }

    @Test
    public void closeItemRemovesSingleEntryWithoutAffectingOthersAndCloseAllClearsRest() {
        AtomicInteger firstDiscards = new AtomicInteger();
        AtomicInteger secondDiscards = new AtomicInteger();
        AtomicInteger thirdDiscards = new AtomicInteger();
        long t0 = 5_000_000L;

        MinimizedEditors.Item first = MinimizedEditors.addForTest(42, "First", firstDiscards::incrementAndGet, t0);
        MinimizedEditors.Item second = MinimizedEditors.addForTest(42, "Second", secondDiscards::incrementAndGet, t0 + 100);
        MinimizedEditors.Item third = MinimizedEditors.addForTest(42, "Third", thirdDiscards::incrementAndGet, t0 + 200);

        MinimizedEditors.closeItem(second);
        assertEquals(1, secondDiscards.get());
        assertEquals(0, firstDiscards.get());
        assertEquals(0, thirdDiscards.get());

        List<MinimizedEditors.Item> remaining = MinimizedEditors.getItemsForTest(t0 + 500);
        assertEquals(2, remaining.size());
        assertTrue(remaining.contains(first));
        assertTrue(remaining.contains(third));

        MinimizedEditors.closeAll();
        assertEquals(1, firstDiscards.get());
        assertEquals(1, thirdDiscards.get());
        assertTrue(MinimizedEditors.getItemsForTest(t0 + 500).isEmpty());
    }

    @Test
    public void formatTitleHandlesMissingFileOrParentCleanly() {
        assertEquals("classes.dex - base.apk", MinimizedEditors.formatTitle("classes.dex", "base.apk"));
        assertEquals("classes.dex", MinimizedEditors.formatTitle("classes.dex", null));
        assertEquals("base.apk", MinimizedEditors.formatTitle("", "base.apk"));
        assertEquals("", MinimizedEditors.formatTitle("", null));
    }
}
