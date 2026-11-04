package io.github.abdurazaaqmohammed.utils;

/**
 * JNI entry points into the vendored zstd library (libmp_zstd.so). Streaming
 * API: whole chunks cross the boundary, memory stays flat regardless of file
 * size. Callers should use {@link MpZstdInputStream} / {@link MpZstdOutputStream}.
 *
 * The native library is built for every ABI the app ships; if loading ever
 * fails (unexpected device), {@link #isLoaded()} reports it and zstd users
 * fail with a clear message instead of UnsatisfiedLinkError.
 */
final class ZstdBridge {

    private static final boolean LOADED = tryLoad();

    private ZstdBridge() {
    }

    /** ZSTD_e_continue: keep the frame open, emit whole blocks as they become ready. */
    static final int ZSTD_E_CONTINUE = 0;
    /** ZSTD_e_flush: flush everything, but do not close the frame. */
    static final int ZSTD_E_FLUSH = 1;
    /** ZSTD_e_end: finish the frame (used by nativeEnd internally). */
    static final int ZSTD_E_END = 2;

    static boolean isLoaded() {
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

    static native long nativeCreateCCtx(int level);
    static native void nativeFreeCCtx(long ctx);
    static native byte[] nativeCompress(long ctx, byte[] src, int srcOffset, int srcLength, int endDirective);
    static native byte[] nativeEnd(long ctx);

    static native long nativeCreateDCtx();
    static native void nativeFreeDCtx(long ctx);
    /**
     * Decodes the whole chunk. Pass finalChunk=true exactly once, at end of
     * input, to flush and validate the frame (a truncated frame throws).
     */
    static native byte[] nativeDecompress(long ctx, byte[] src, int srcOffset, int srcLength, boolean finalChunk);
}
