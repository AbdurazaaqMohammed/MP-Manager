#define _GNU_SOURCE
#define _FILE_OFFSET_BITS 64
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

enum Fault { NONE, SHORT_WRITE, READ_EINTR, WRITE_EINTR, READ_ERROR, WRITE_ERROR,
             ZERO_WRITE, EARLY_EOF, SYNC_ERROR, CLOSE_ERROR, CANCEL_READ };
static enum Fault fault;
static int read_calls, write_calls, close_calls;
static ssize_t fault_read(int fd, void *buffer, size_t size);
static ssize_t fault_write(int fd, const void *buffer, size_t size);
static int fault_fsync(int fd);
static int fault_close(int fd);

#define read fault_read
#define write fault_write
#define fsync fault_fsync
#define close fault_close
#define main mpio_main
#include "../../main/cpp/mpio/mpio.c"
#undef main
#undef close
#undef fsync
#undef write
#undef read

static ssize_t fault_read(int fd, void *buffer, size_t size) {
    int call = ++read_calls;
    if (fault == READ_EINTR && call == 1) { errno = EINTR; return -1; }
    if (fault == READ_ERROR && call == 2) { errno = EIO; return -1; }
    if (fault == EARLY_EOF && call == 2) return 0;
    if (fault == CANCEL_READ && call == 2) g_cancelled = 1;
    return read(fd, buffer, size);
}

static ssize_t fault_write(int fd, const void *buffer, size_t size) {
    int call = ++write_calls;
    if (fault == WRITE_EINTR && call == 1) { errno = EINTR; return -1; }
    if (fault == WRITE_ERROR && call == 2) { errno = ENOSPC; return -1; }
    if (fault == ZERO_WRITE && call == 2) return 0;
    if (fault == SHORT_WRITE && size > 997) size = 997;
    return write(fd, buffer, size);
}

static int fault_fsync(int fd) {
    if (fault == SYNC_ERROR) { errno = EIO; return -1; }
    return fsync(fd);
}

static int fault_close(int fd) {
    int result = close(fd);
    if (fault == CLOSE_ERROR && ++close_calls == 1) { errno = EIO; return -1; }
    return result;
}

static void reset(enum Fault next) {
    fault = next;
    read_calls = write_calls = close_calls = 0;
    g_cancelled = 0;
}

static void same_contents(const char *src, const char *dst) {
    int a = open(src, O_RDONLY), b = open(dst, O_RDONLY);
    assert(a >= 0 && b >= 0);
    char ab[8192], bb[8192];
    ssize_t count;
    do {
        count = read(a, ab, sizeof(ab));
        assert(count >= 0 && read(b, bb, sizeof(bb)) == count);
        assert(memcmp(ab, bb, (size_t)count) == 0);
    } while (count > 0);
    assert(close(a) == 0 && close(b) == 0);
}

int main(int argc, char *argv[]) {
    assert(argc == 2);
    char directory[4096], src[4096], dst[4096], child[4096], nested[4096];
    assert(snprintf(directory, sizeof(directory), "%s/mpio-test-XXXXXX", argv[1]) < (int)sizeof(directory));
    assert(mkdtemp(directory));
    assert(snprintf(src, sizeof(src), "%s/source", directory) < (int)sizeof(src));
    assert(snprintf(dst, sizeof(dst), "%s/destination", directory) < (int)sizeof(dst));
    int fd = open(src, O_WRONLY | O_CREAT | O_EXCL, 0600);
    assert(fd >= 0);
    char data[600123];
    for (size_t i = 0; i < sizeof(data); i++) data[i] = (char)(i * 31 + i / 997);
    assert(write(fd, data, sizeof(data)) == (ssize_t)sizeof(data));
    assert(close(fd) == 0);

    enum Fault successful[] = {NONE, SHORT_WRITE, READ_EINTR, WRITE_EINTR};
    for (size_t i = 0; i < sizeof(successful) / sizeof(successful[0]); i++) {
        reset(successful[i]);
        struct stat st;
        CopyContext ctx = {0};
        assert(stat(src, &st) == 0);
        assert(copy_single_file(src, dst, &st, &ctx) == 0);
        assert(ctx.done_files == 1 && ctx.done_bytes == sizeof(data));
        same_contents(src, dst);
        assert(unlink(dst) == 0);
    }

    enum Fault failures[] = {READ_ERROR, WRITE_ERROR, ZERO_WRITE, EARLY_EOF, SYNC_ERROR, CLOSE_ERROR, CANCEL_READ};
    for (size_t i = 0; i < sizeof(failures) / sizeof(failures[0]); i++) {
        reset(failures[i]);
        struct stat st;
        CopyContext ctx = {0};
        assert(stat(src, &st) == 0);
        assert(copy_single_file(src, dst, &st, &ctx) == -1);
        assert(ctx.done_files == 0);
        assert(access(dst, F_OK) == -1 && errno == ENOENT);
        assert(stat(src, &st) == 0 && st.st_size == (off_t)sizeof(data));
    }

    reset(NONE);
    assert(do_copy(src, src) == 1); // Must not truncate an alias of the source.
    struct stat st;
    assert(stat(src, &st) == 0 && st.st_size == (off_t)sizeof(data));
    assert(link(src, dst) == 0);
    assert(do_copy(src, dst) == 1);
    same_contents(src, dst);
    assert(unlink(dst) == 0);

    assert(snprintf(child, sizeof(child), "%s/tree", directory) < (int)sizeof(child));
    assert(mkdir(child, 0700) == 0);
    assert(snprintf(nested, sizeof(nested), "%s/tree/file", directory) < (int)sizeof(nested));
    assert(link(src, nested) == 0);
    reset(WRITE_ERROR);
    assert(do_copy(child, dst) == 1); // Failure must travel through the directory walk.
    char copied_child[4096];
    assert(snprintf(copied_child, sizeof(copied_child), "%s/file", dst) < (int)sizeof(copied_child));
    assert(access(copied_child, F_OK) == -1);
    assert(rmdir(dst) == 0);
    reset(NONE);
    assert(do_copy(child, nested) == 1); // Directory into itself.
    assert(unlink(nested) == 0 && rmdir(child) == 0);

    assert(symlink("missing-target", dst) == 0);
    assert(do_copy(dst, child) == 0); // Dangling symlinks are copied, not followed.
    char target[32];
    assert(readlink(child, target, sizeof(target)) == (ssize_t)strlen("missing-target"));
    assert(unlink(child) == 0 && unlink(dst) == 0);
    assert(do_copy(dst, child) == 1); // Missing source cannot report COPY_DONE.
    assert(unlink(src) == 0 && rmdir(directory) == 0);
    puts("mpio native regression tests passed (read/write/sync/close failures, cancellation, aliases, recursion)");
    return 0;
}
