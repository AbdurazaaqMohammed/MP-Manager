package io.github.abdurazaaqmohammed.utils;

import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class EditorIoTest {
    @Test public void viewportSupportsFilesBeyondRecyclerViewIntegerRange() {
        HexViewport viewport = new HexViewport();
        long size = 40L * 1024 * 1024 * 1024 + 3;
        viewport.resize(size);
        assertEquals(HexViewport.MAX_ROWS, viewport.rowCount());
        assertTrue(viewport.ensureVisible(size - 1));
        int position = viewport.position(size - 1);
        assertTrue(position >= 0 && position < viewport.rowCount());
        assertEquals((size - 1) / 8 * 8, viewport.offset(position));
        assertFalse(viewport.hasNextWindow());
        assertTrue(viewport.shift(-HexViewport.MAX_ROWS / 2));
        assertTrue(viewport.hasNextWindow());
        viewport.resize(17);
        assertEquals(0, viewport.firstRow());
        assertEquals(3, viewport.rowCount());
        viewport.resize(0);
        assertEquals(0, viewport.rowCount());
    }
    private long search(byte[] data, byte[] pattern, long start, boolean forward) {
        return BytePatternSearch.find((from, to) -> Arrays.copyOfRange(data, (int) from, (int) to),
                data.length, pattern, start, forward, () -> false);
    }

    @Test public void boundarySpanningPatternsWorkInBothDirections() {
        byte[] data = new byte[200000];
        byte[] needle = {1, 2, 1, 2, 1, 3};
        System.arraycopy(needle, 0, data, 65534, needle.length);
        System.arraycopy(needle, 0, data, 131070, needle.length);
        assertEquals(65534, search(data, needle, 0, true));
        assertEquals(131070, search(data, needle, 65535, true));
        assertEquals(131070, search(data, needle, data.length - 1, false));
        assertEquals(65534, search(data, needle, 131069, false));
        assertEquals(-1, search(data, needle, 65533, false));
    }

    @Test public void matchesNaiveSearchOnRandomInputs() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 500; iteration++) {
            byte[] data = new byte[100 + random.nextInt(500)];
            random.nextBytes(data);
            int length = 1 + random.nextInt(15);
            int occurrence = random.nextInt(data.length - length + 1);
            byte[] needle = Arrays.copyOfRange(data, occurrence, occurrence + length);
            int start = random.nextInt(data.length);
            for (boolean forward : new boolean[]{true, false}) {
                long expected = -1;
                for (int p = forward ? start : Math.min(start, data.length - needle.length);
                     forward ? p + needle.length <= data.length : p >= 0; p += forward ? 1 : -1) {
                    if (Arrays.equals(needle, Arrays.copyOfRange(data, p, p + needle.length))) { expected = p; break; }
                }
                assertEquals(expected, search(data, needle, start, forward));
            }
        }
    }

    @Test public void handlesPatternsLargerThanReadBufferAndOverlaps() {
        byte[] needle = new byte[70000];
        Arrays.fill(needle, (byte) 1);
        needle[needle.length - 1] = 2;
        byte[] data = new byte[160000];
        Arrays.fill(data, (byte) 1);
        data[140000] = 2;
        assertEquals(70001, search(data, needle, 0, true));
        assertEquals(70001, search(data, needle, 150000, false));
    }

    @Test public void cancellationAndTruncationDoNotReportMatches() {
        AtomicInteger reads = new AtomicInteger();
        assertEquals(-1, BytePatternSearch.find((from, to) -> { reads.incrementAndGet(); return new byte[(int) (to - from)]; },
                1000000000L, new byte[]{1}, 0, true, () -> reads.get() >= 2));
        assertEquals(2, reads.get());
        assertEquals(-1, BytePatternSearch.find((from, to) -> new byte[0], 100, new byte[]{0}, 0, true, () -> false));
    }

    @Test public void largeFileSearchUsesLongOffsetsAndBulkReads() {
        long offset = (1L << 31) + 65534;
        byte[] needle = {9, 8, 7, 6};
        AtomicInteger reads = new AtomicInteger();
        BytePatternSearch.Source source = (from, to) -> {
            reads.incrementAndGet();
            byte[] data = new byte[(int) (to - from)];
            for (int i = 0; i < needle.length; i++) if (offset + i >= from && offset + i < to) data[(int) (offset + i - from)] = needle[i];
            return data;
        };
        assertEquals(offset, BytePatternSearch.find(source, offset + 100000, needle, 1L << 31, true, () -> false));
        assertEquals(2, reads.get());
        assertEquals(offset, BytePatternSearch.find(source, offset + 100000, needle, offset + 100, false, () -> false));
    }

    @Test public void pageCacheReadsBoundariesEofAndInvalidationWithoutChangingSeekPointer() throws Exception {
        File file = File.createTempFile("hex-pages", ".bin");
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(70000);
            raf.seek(65535); raf.write(new byte[]{(byte) 255, 42});
            raf.seek(123);
            HexPageCache cache = new HexPageCache(raf.getChannel());
            assertEquals(255, cache.read(65535));
            assertEquals(42, cache.read(65536));
            assertEquals(-1, cache.read(70000));
            assertEquals(-1, cache.read(-1));
            assertEquals(123, raf.getFilePointer());
            raf.seek(65536); raf.write(7);
            assertEquals(42, cache.read(65536));
            cache.clear();
            assertEquals(7, cache.read(65536));
        } finally { file.delete(); }
        assertEquals("0080FF10", HexPageCache.hex(new byte[]{0, (byte) 128, (byte) 255, 16}));
    }
}
