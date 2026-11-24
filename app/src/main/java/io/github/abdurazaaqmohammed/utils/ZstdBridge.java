package io.github.abdurazaaqmohammed.utils;

import io.github.abdurazaaqmohammed.util.archive.MpZstdInputStream;
import io.github.abdurazaaqmohammed.util.archive.MpZstdOutputStream;

/**
 * JNI entry points into the vendored zstd library (libmp_zstd.so). Streaming
 * API: whole chunks cross the boundary, memory stays flat regardless of file
 * size. Callers should use {@link MpZstdInputStream} / {@link MpZstdOutputStream}.
 *
 * <p>The package/class name is part of the native symbols exported by
 * {@code libmp_zstd.so}; do not move or rename it without updating
 * {@code app/src/main/cpp/zstd_bridge.c}.</p>
 */
public final class ZstdBridge {

    private static final boolean LOADED = tryLoad();

    private ZstdBridge() {
    }

    /** ZSTD_e_continue: keep the frame open, emit whole blocks as they become ready. */
    public static final int ZSTD_E_CONTINUE = 0;
    /** ZSTD_e_flush: flush everything, but do not close the frame. */
    public static final int ZSTD_E_FLUSH = 1;
    /** ZSTD_e_end: finish the frame (used by nativeEnd internally). */
    public static final int ZSTD_E_END = 2;

    public static boolean isLoaded() {
        return LOADED;
    }

    private static boolean tryLoad() {
        try {
            System.loadLibrary("mp_zstd");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static native long nativeCreateCCtx(int level);
    public static native void nativeFreeCCtx(long ctx);
    public static native byte[] nativeCompress(long ctx, byte[] src, int srcOffset, int srcLength, int endDirective);
    public static native byte[] nativeEnd(long ctx);

    public static native long nativeCreateDCtx();
    public static native void nativeFreeDCtx(long ctx);
    /**
     * Decodes the whole chunk. Pass finalChunk=true exactly once, at end of
     * input, to flush and validate the frame (a truncated frame throws).
     */
    public static native byte[] nativeDecompress(long ctx, byte[] src, int srcOffset, int srcLength, boolean finalChunk);
}
