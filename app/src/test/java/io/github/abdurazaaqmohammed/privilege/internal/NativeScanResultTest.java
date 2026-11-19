package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.privilege.internal.NativeScanResult;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeScanResultTest {
    @Test public void exactCountsIncludeDirectoriesAnd64BitBytes() {
        NativeScanResult result = new NativeScanResult(2);
        result.accept("ITEM_RESULT idx=0 files=6000 dirs=3 bytes=4294967297 hidden=2 errors=0");
        result.accept("ITEM_RESULT idx=1 files=0 dirs=1 bytes=0 hidden=0 errors=0");
        result.accept("SCAN_RESULT files=6000 dirs=4 bytes=4294967297 hidden=2 errors=0 ms=1");
        assertTrue(result.valid());
        assertEquals(6004, result.total.entries());
        assertEquals(4294967297L, result.total.bytes());
        assertEquals(1, result.items.get(1).folders());
        assertEquals(0, result.items.get(1).files());
    }

    @Test public void missingMalformedOrMismatchedTotalsAreRejected() {
        NativeScanResult result = new NativeScanResult(1);
        result.accept("SCAN_RESULT files=0 dirs=0 bytes=0 hidden=0 errors=0");
        assertFalse(result.valid());
        result.accept("ITEM_RESULT idx=0 files=1 dirs=0 bytes=10 hidden=0 errors=0");
        assertFalse(result.valid());
        result.accept("SCAN_RESULT files=1 dirs=0 bytes=10 hidden=0 errors=0");
        assertTrue(result.valid());
        result.accept("ITEM_RESULT idx=0 files=-1 dirs=0 bytes=10 hidden=0 errors=0");
        assertFalse(result.valid());
    }

    @Test public void unreadableEntriesAreExplicitlyPartial() {
        NativeScanResult result = new NativeScanResult(1);
        result.accept("ITEM_RESULT idx=0 files=0 dirs=1 bytes=0 hidden=0 errors=1");
        result.accept("SCAN_RESULT files=0 dirs=1 bytes=0 hidden=0 errors=1");
        assertTrue(result.valid());
        assertFalse(result.total.complete());
    }
}
