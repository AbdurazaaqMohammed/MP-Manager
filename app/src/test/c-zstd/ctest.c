/* Standalone roundtrip check for the vendored zstd (host gcc, no JNI).
 * Usage: ctest [payload-bytes]
 * Prints what ZSTD_decompressStream returns at each step so the
 * end-of-stream semantics can be inspected. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zstd.h>

static unsigned long rngState = 12345;
static unsigned nextRand(void) {
    rngState ^= rngState << 13;
    rngState ^= rngState >> 17;
    rngState ^= rngState << 5;
    return rngState;
}

int main(int argc, char **argv) {
    size_t size = argc > 1 ? (size_t) atoi(argv[1]) : 200 * 1024;
    unsigned char *data = malloc(size);
    for (size_t i = 0; i < size; i++) data[i] = (unsigned char) nextRand();

    ZSTD_CCtx *cctx = ZSTD_createCCtx();
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_compressionLevel, 19);
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_checksumFlag, 1);
    size_t bound = ZSTD_compressBound(size);
    void *packed = malloc(bound);
    ZSTD_inBuffer in = { data, size, 0 };
    ZSTD_outBuffer out = { packed, bound, 0 };
    size_t rc = ZSTD_compressStream2(cctx, &out, &in, ZSTD_e_end);
    printf("compress rc=%zu packed=%zu err=%u\n", rc, out.pos, (unsigned) ZSTD_isError(rc));

    ZSTD_DCtx *dctx = ZSTD_createDCtx();
    unsigned char tmp[256 * 1024];
    ZSTD_inBuffer din = { packed, out.pos, 0 };
    size_t dr = 0;
    int step = 0;
    while (din.pos < din.size) {
        ZSTD_outBuffer dout = { tmp, sizeof(tmp), 0 };
        dr = ZSTD_decompressStream(dctx, &dout, &din);
        printf("step %d: in.pos=%zu/%zu out.pos=%zu rc=%zu err=%u\n",
               ++step, din.pos, din.size, dout.pos, dr, (unsigned) ZSTD_isError(dr));
        if (ZSTD_isError(dr)) return 1;
    }
    printf("input exhausted, last rc=%zu -> starting drain\n", dr);
    for (int i = 0; i < 8; i++) {
        ZSTD_outBuffer dout = { tmp, sizeof(tmp), 0 };
        ZSTD_inBuffer empty = { "", 0, 0 };
        dr = ZSTD_decompressStream(dctx, &dout, &empty);
        printf("drain %d: out.pos=%zu rc=%zu err=%u\n",
               i, dout.pos, dr, (unsigned) ZSTD_isError(dr));
        if (ZSTD_isError(dr)) return 1;
        if (dr == 0) break;
    }
    printf("done\n");
    free(data);
    free(packed);
    ZSTD_freeCCtx(cctx);
    ZSTD_freeDCtx(dctx);
    return 0;
}
