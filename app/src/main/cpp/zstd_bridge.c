/**
 * JNI bridge to the vendored zstd library (app/src/main/cpp/zstd).
 *
 * Two streaming APIs, one per direction. Both hand whole chunks across the
 * boundary and never require a giant contiguous buffer, so multi-hundred-MB
 * APKs compress/decompress in a few 64KB buffers:
 *
 *   nativeCreateCCtx(level)   -> context id (pointer as jlong)
 *   nativeCompress(ctx, src, srcOffset, srcLength, endDirective)
 *                              -> newly allocated byte[] with the bytes the
 *                                 compressor emitted (never null)
 *   nativeEnd(ctx)             -> byte[] with everything left to flush
 *   nativeFreeCCtx(ctx)
 *   nativeCreateDCtx() / nativeFreeDCtx(ctx)
 *   nativeDecompress(ctx, src, srcOffset, srcLength, finalChunk)
 *                              -> byte[] with all decoded output for this
 *                                 chunk (may be empty). Pass finalChunk=true
 *                                 once, at end of input: it flushes whatever
 *                                 is still buffered and requires the frame to
 *                                 complete — a truncated frame or trailing
 *                                 garbage throws IOException.
 *
 * All errors are reported as java.io.IOException with the zstd error name.
 * Memory is allocated with malloc/free inside each call; no JNI globals.
 */
#include <jni.h>
#include <zstd.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>

static void throwIo(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

static void throwOom(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

/* Growable byte sink for one call's output. */
typedef struct {
    unsigned char *data;
    size_t size;
    size_t capacity;
} ByteSink;

static int sinkInit(ByteSink *sink, size_t initialCapacity) {
    sink->data = (unsigned char *) malloc(initialCapacity);
    sink->size = 0;
    sink->capacity = initialCapacity;
    return sink->data != NULL;
}

static int sinkEnsure(ByteSink *sink, size_t extra) {
    if (sink->size + extra <= sink->capacity) return 1;
    size_t newCapacity = sink->capacity * 2;
    while (sink->size + extra > newCapacity) newCapacity *= 2;
    if (newCapacity > (size_t) INT32_MAX) return 0; /* 2 GB cap keeps jbyteArray legal */
    unsigned char *grown = (unsigned char *) realloc(sink->data, newCapacity);
    if (grown == NULL) return 0;
    sink->data = grown;
    sink->capacity = newCapacity;
    return 1;
}

static void sinkAppend(ByteSink *sink, const void *bytes, size_t length) {
    if (length > 0) {
        memcpy(sink->data + sink->size, bytes, length);
        sink->size += length;
    }
}

static void sinkFree(ByteSink *sink) {
    free(sink->data);
    sink->data = NULL;
    sink->size = sink->capacity = 0;
}

static jbyteArray sinkToJava(JNIEnv *env, ByteSink *sink) {
    jbyteArray result = (*env)->NewByteArray(env, (jsize) sink->size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize) sink->size, (const jbyte *) sink->data);
    }
    return result;
}

/* ---------- compressor ---------- */

JNIEXPORT jlong JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeCreateCCtx(JNIEnv *env, jclass cls,
        jint level) {
    (void) cls;
    ZSTD_CCtx *ctx = ZSTD_createCCtx();
    if (ctx == NULL) {
        throwOom(env, "zstd: failed to allocate compression context");
        return 0;
    }
    ZSTD_CCtx_setParameter(ctx, ZSTD_c_compressionLevel, (int) level);
    /* Enable multithreaded compression across available CPU cores if compiled with ZSTD_MULTITHREAD */
#ifdef ZSTD_MULTITHREAD
    int cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    if (cores <= 0) cores = 4;
    ZSTD_CCtx_setParameter(ctx, ZSTD_c_nbWorkers, cores);
#endif
    /* Store the content checksum; the decompressor verifies it for us. */
    ZSTD_CCtx_setParameter(ctx, ZSTD_c_checksumFlag, 1);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeFreeCCtx(JNIEnv *env, jclass cls,
        jlong ctx) {
    (void) env;
    (void) cls;
    if (ctx != 0) {
        ZSTD_freeCCtx((ZSTD_CCtx *) ctx);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeCompress(JNIEnv *env, jclass cls,
        jlong ctx, jbyteArray src, jint srcOffset, jint srcLength, jint endDirective) {
    (void) cls;
    ZSTD_CCtx *cctx = (ZSTD_CCtx *) ctx;
    if (cctx == NULL) {
        throwIo(env, "zstd: bad compression context");
        return NULL;
    }
    if (srcLength < 0 || srcOffset < 0) {
        throwIo(env, "zstd: negative offset or length");
        return NULL;
    }
    jsize arrayLength = (*env)->GetArrayLength(env, src);
    if ((jlong) srcOffset + srcLength > arrayLength) {
        throwIo(env, "zstd: offset + length out of bounds");
        return NULL;
    }

    jbyte *in = (*env)->GetByteArrayElements(env, src, NULL);
    if (in == NULL) {
        return NULL; /* JVM already threw OOM */
    }

    size_t outCapacity = (srcLength > 0) ? ZSTD_compressBound((size_t) srcLength) : 4096;
    void *outBuf = malloc(outCapacity);
    if (outBuf == NULL) {
        (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
        throwOom(env, "zstd: failed to allocate compression output buffer");
        return NULL;
    }

    ZSTD_inBuffer input = { in + srcOffset, (size_t) srcLength, 0 };
    ZSTD_outBuffer output = { outBuf, outCapacity, 0 };
    size_t r = ZSTD_compressStream2(cctx, &output, &input, (ZSTD_EndDirective) endDirective);
    (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);

    if (ZSTD_isError(r)) {
        free(outBuf);
        throwIo(env, ZSTD_getErrorName(r));
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, (jsize) output.pos);
    if (result != NULL && output.pos > 0) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize) output.pos, (const jbyte *) outBuf);
    }
    free(outBuf);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeEnd(JNIEnv *env, jclass cls,
        jlong ctx) {
    (void) cls;
    ZSTD_CCtx *cctx = (ZSTD_CCtx *) ctx;
    if (cctx == NULL) {
        throwIo(env, "zstd: bad compression context");
        return NULL;
    }

    ByteSink sink;
    if (!sinkInit(&sink, 256 * 1024)) {
        throwOom(env, "zstd: failed to allocate end buffer");
        return NULL;
    }

    unsigned char tmp[128 * 1024];
    const ZSTD_inBuffer emptyInput = { "", 0, 0 };
    size_t remaining;
    do {
        ZSTD_outBuffer output = { tmp, sizeof(tmp), 0 };
        ZSTD_inBuffer input = emptyInput;
        remaining = ZSTD_compressStream2(cctx, &output, &input, ZSTD_e_end);
        if (ZSTD_isError(remaining)) {
            sinkFree(&sink);
            throwIo(env, ZSTD_getErrorName(remaining));
            return NULL;
        }
        if (!sinkEnsure(&sink, output.pos)) {
            sinkFree(&sink);
            throwOom(env, "zstd: frame too large to flush");
            return NULL;
        }
        sinkAppend(&sink, tmp, output.pos);
    } while (remaining != 0);

    jbyteArray result = sinkToJava(env, &sink);
    sinkFree(&sink);
    return result;
}

/* ---------- decompressor ---------- */

/* Wrapper around the zstd context that also remembers the return code of
 * the last ZSTD_decompressStream() call, so the caller can tell at
 * end-of-input whether the frame is complete (0) or still pending (>0). */
typedef struct {
    ZSTD_DCtx *z;
    size_t lastR;
} MpDCtx;

JNIEXPORT jlong JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeCreateDCtx(JNIEnv *env, jclass cls) {
    (void) cls;
    MpDCtx *ctx = (MpDCtx *) calloc(1, sizeof(MpDCtx));
    if (ctx == NULL) {
        throwOom(env, "zstd: failed to allocate decompression context");
        return 0;
    }
    ctx->z = ZSTD_createDCtx();
    if (ctx->z == NULL) {
        free(ctx);
        throwOom(env, "zstd: failed to create decompression context");
        return 0;
    }
    ctx->lastR = 0;
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeFreeDCtx(JNIEnv *env, jclass cls,
        jlong ctx) {
    (void) env;
    (void) cls;
    MpDCtx *mctx = (MpDCtx *) ctx;
    if (mctx != NULL) {
        ZSTD_freeDCtx(mctx->z);
        free(mctx);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_io_github_abdurazaaqmohammed_utils_ZstdBridge_nativeDecompress(JNIEnv *env, jclass cls,
        jlong ctx, jbyteArray src, jint srcOffset, jint srcLength, jboolean finalChunk) {
    (void) cls;
    MpDCtx *mctx = (MpDCtx *) ctx;
    if (mctx == NULL || mctx->z == NULL) {
        throwIo(env, "zstd: bad decompression context");
        return NULL;
    }
    ZSTD_DCtx *dctx = mctx->z;
    if (srcLength < 0 || srcOffset < 0) {
        throwIo(env, "zstd: negative offset or length");
        return NULL;
    }
    jsize arrayLength = (*env)->GetArrayLength(env, src);
    if ((jlong) srcOffset + srcLength > arrayLength) {
        throwIo(env, "zstd: offset + length out of bounds");
        return NULL;
    }

    jbyte *in = (*env)->GetByteArrayElements(env, src, NULL);
    if (in == NULL) {
        return NULL; /* JVM already threw OOM */
    }

    /* Decode into a growing sink; one call consumes the whole input. */
    ByteSink sink;
    if (!sinkInit(&sink, 256 * 1024)) {
        (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
        throwOom(env, "zstd: failed to allocate decompression buffer");
        return NULL;
    }

    unsigned char tmp[256 * 1024];
    ZSTD_inBuffer input = { in + srcOffset, (size_t) srcLength, 0 };
    /* Start from the previous call's status: at end of input this tells us
     * whether the frame was already complete (0) or still pending (>0). */
    size_t r = mctx->lastR;
    while (input.pos < input.size) {
        ZSTD_outBuffer output = { tmp, sizeof(tmp), 0 };
        r = ZSTD_decompressStream(dctx, &output, &input);
        if (ZSTD_isError(r)) {
            (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
            sinkFree(&sink);
            throwIo(env, ZSTD_getErrorName(r));
            return NULL;
        }
        if (!sinkEnsure(&sink, output.pos)) {
            (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
            sinkFree(&sink);
            throwOom(env, "zstd: decompressed frame too large");
            return NULL;
        }
        sinkAppend(&sink, tmp, output.pos);
        mctx->lastR = r;
    }

    if (finalChunk) {
        /* End of input: flush whatever the decoder still buffers, and
         * require the current frame to complete. zstd returns 0 exactly
         * when a frame is fully decoded and flushed; when the last chunk
         * completed the frame there is nothing buffered and nothing to do.
         * A truncated frame, or trailing garbage, fails loudly instead of
         * yielding silent partial output. */
        while (r != 0) {
            ZSTD_outBuffer output = { tmp, sizeof(tmp), 0 };
            ZSTD_inBuffer empty = { "", 0, 0 };
            r = ZSTD_decompressStream(dctx, &output, &empty);
            if (ZSTD_isError(r)) {
                (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
                sinkFree(&sink);
                throwIo(env, ZSTD_getErrorName(r));
                return NULL;
            }
            if (!sinkEnsure(&sink, output.pos)) {
                (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
                sinkFree(&sink);
                throwOom(env, "zstd: decompressed frame too large");
                return NULL;
            }
            sinkAppend(&sink, tmp, output.pos);
            if (output.pos == 0 && r != 0) {
                /* wants more input that will never come */
                (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
                sinkFree(&sink);
                throwIo(env, "truncated zstd frame: input ended mid-frame");
                return NULL;
            }
            mctx->lastR = r;
        }
    }

    (*env)->ReleaseByteArrayElements(env, src, in, JNI_ABORT);
    jbyteArray result = sinkToJava(env, &sink);
    sinkFree(&sink);
    return result;
}
