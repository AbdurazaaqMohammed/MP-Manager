package io.github.abdurazaaqmohammed.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decompresses a .zst stream. The whole decompressed content flows while
 * consuming the input, so huge files decode in 64KB chunks of memory.
 * Corrupt input or a checksum mismatch raises IOException.
 */
public final class MpZstdInputStream extends InputStream {

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] EMPTY = new byte[0];

    private final InputStream in;
    private long ctx;
    private byte[] decompressed;
    private int pos;
    private boolean eof;
    private boolean closed;

    public MpZstdInputStream(InputStream in) throws IOException {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (!ZstdBridge.isLoaded()) {
            throw new IOException("Zstandard is not supported on this device (native library missing)");
        }
        this.in = in;
        this.ctx = ZstdBridge.nativeCreateDCtx();
        if (this.ctx == 0) {
            throw new IOException("zstd: failed to create decompression context");
        }
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        if (!fill()) {
            return -1;
        }
        return decompressed[pos++] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("offset=" + off + " len=" + len + " array=" + b.length);
        }
        if (len == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }
        int n = Math.min(len, decompressed.length - pos);
        System.arraycopy(decompressed, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        if (decompressed == null) {
            return 0;
        }
        return Math.max(0, decompressed.length - pos);
    }

    /** Refills the decoded buffer; false at end of stream. May skip chunks that decode to zero bytes. */
    private boolean fill() throws IOException {
        while (decompressed == null || pos >= decompressed.length) {
            if (eof) {
                return false;
            }
            byte[] chunk = readChunk();
            if (chunk == null) {
                // End of underlying input: flush and validate the frame.
                byte[] tail = ZstdBridge.nativeDecompress(ctx, EMPTY, 0, 0, true);
                eof = true;
                if (tail.length > 0) {
                    decompressed = tail;
                    pos = 0;
                    return true;
                }
                if (decompressed == null) {
                    // Zero bytes of input: not a zstd file at all (a real
                    // .zst always carries at least a frame header).
                    throw new IOException("not a zstd stream (empty input)");
                }
                return false;
            }
            decompressed = ZstdBridge.nativeDecompress(ctx, chunk, 0, chunk.length, false);
            pos = 0;
        }
        return true;
    }

    /** Reads up to 64KB; null at EOF; short read at a truncated tail (legal for final frames). */
    private byte[] readChunk() throws IOException {
        byte[] tmp = new byte[CHUNK_SIZE];
        int total = 0;
        while (total < tmp.length) {
            int n = in.read(tmp, total, tmp.length - total);
            if (n < 0) {
                if (total == 0) {
                    return null;
                }
                byte[] exact = new byte[total];
                System.arraycopy(tmp, 0, exact, 0, total);
                return exact;
            }
            total += n;
        }
        return tmp;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            in.close();
        } finally {
            ZstdBridge.nativeFreeDCtx(ctx);
            ctx = 0;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("zstd stream is closed");
        }
    }
}
