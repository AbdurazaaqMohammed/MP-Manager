package io.github.abdurazaaqmohammed.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small LRU of original-file pages. Positional reads do not disturb RAF's seek pointer. */
public final class HexPageCache {
    private static final int PAGE = 64 * 1024;
    private final FileChannel channel;
    private final Map<Long, byte[]> pages = new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, byte[]> entry) { return size() > 8; }
    };
    public HexPageCache(FileChannel channel) { this.channel = channel; }
    public synchronized int read(long offset) throws IOException {
        if (offset < 0) return -1;
        long base = offset - offset % PAGE;
        byte[] bytes = pages.get(base);
        if (bytes == null) {
            long remaining = channel.size() - base;
            if (remaining <= 0) return -1;
            bytes = new byte[(int) Math.min(PAGE, remaining)];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                int n = channel.read(buffer, base + buffer.position());
                if (n <= 0) throw new IOException("Unexpected end of file");
            }
            pages.put(base, bytes);
        }
        int index = (int) (offset - base);
        return index < bytes.length ? bytes[index] & 255 : -1;
    }
    public synchronized void clear() { pages.clear(); }
    public static String hex(byte[] bytes) {
        char[] digits = "0123456789ABCDEF".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }
}
