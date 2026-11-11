package io.github.abdurazaaqmohammed.utils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Compresses to a standard .zst frame (readable by any zstd tool), with a
 * content checksum and level 19 — noticeably smaller output, still fast.
 * Buffers 64KB internally, so memory stays flat on huge files.
 *
 * Closing closes the underlying stream, matching the app's other compressor
 * streams (gzip/bz2/xz).
 */
public final class MpZstdOutputStream extends OutputStream {

    /** Level 3: default fast zstd compression with high throughput; matches modern backup utilities. */
    public static final int COMPRESSION_LEVEL = 3;
    private static final int BUFFER_SIZE = 256 * 1024;

    private final OutputStream out;
    private long ctx;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int length;
    private boolean closed;

    public MpZstdOutputStream(OutputStream out) throws IOException {
        this(out, COMPRESSION_LEVEL);
    }

    public MpZstdOutputStream(OutputStream out, int level) throws IOException {
        if (!ZstdBridge.isLoaded()) {
            throw new IOException("Zstandard is not supported on this device (native library missing)");
        }
        this.out = out;
        this.ctx = ZstdBridge.nativeCreateCCtx(level);
        if (this.ctx == 0) {
            throw new IOException("zstd: failed to create compression context");
        }
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        buffer[length++] = (byte) b;
        if (length == buffer.length) {
            flushBuffer();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("offset=" + off + " len=" + len + " array=" + b.length);
        }
        int remaining = len;
        int pos = off;
        while (remaining > 0) {
            int take = Math.min(buffer.length - length, remaining);
            System.arraycopy(b, pos, buffer, length, take);
            length += take;
            pos += take;
            remaining -= take;
            if (length == buffer.length) {
                flushBuffer();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        flushBuffer();
        out.flush();
    }

    private void flushBuffer() throws IOException {
        if (length == 0) {
            return;
        }
        byte[] compressed = ZstdBridge.nativeCompress(ctx, buffer, 0, length, ZstdBridge.ZSTD_E_CONTINUE);
        if (compressed.length > 0) {
            out.write(compressed);
        }
        length = 0;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException toThrow = null;
        try {
            flushBuffer();
            byte[] tail = ZstdBridge.nativeEnd(ctx);
            if (tail.length > 0) {
                out.write(tail);
            }
        } catch (IOException | RuntimeException e) {
            toThrow = e instanceof IOException ? (IOException) e : new IOException(e);
        } finally {
            ZstdBridge.nativeFreeCCtx(ctx);
            ctx = 0;
            try {
                out.close();
            } catch (IOException e) {
                if (toThrow == null) {
                    toThrow = e;
                } else {
                    toThrow.addSuppressed(e);
                }
            }
        }
        if (toThrow != null) {
            throw toThrow;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("zstd stream is closed");
        }
    }
}
