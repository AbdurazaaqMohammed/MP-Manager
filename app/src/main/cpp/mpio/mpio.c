#define _GNU_SOURCE
#define _FILE_OFFSET_BITS 64
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <inttypes.h>
#include <stdbool.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/time.h>
#include <time.h>
#include <utime.h>
#include <errno.h>
#include <signal.h>

#define BUFFER_SIZE (256 * 1024)

_Static_assert(sizeof(off_t) >= 8, "mpio requires 64-bit file offsets");

static volatile sig_atomic_t g_cancelled = 0;
static char g_io_buffer[BUFFER_SIZE];

static void sig_handler(int sig) {
    (void)sig;
    g_cancelled = 1;
}

static uint64_t current_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)(ts.tv_nsec / 1000000ULL);
}

// Bypass shared storage's FUSE layer for direct filesystem I/O.
static int resolve_fast_path(const char *in, char *out, size_t out_size) {
    // Resolve parent aliases such as /sdcard and /storage/self/primary, but
    // never follow the final symlink (recursive copies preserve symlinks).
    char parent[4096];
    char real_parent[4096];
    char resolved[4096];
    if (strlen(in) >= sizeof(parent)) {
        errno = ENAMETOOLONG;
        return -1;
    }
    const char *slash = strrchr(in, '/');
    const char *name = slash ? slash + 1 : in;
    if (!slash) snprintf(parent, sizeof(parent), ".");
    else if (slash == in) snprintf(parent, sizeof(parent), "/");
    else snprintf(parent, sizeof(parent), "%.*s", (int)(slash - in), in);
    if (realpath(parent, real_parent)) {
        int len = snprintf(resolved, sizeof(resolved), "%s%s%s", real_parent,
                           strcmp(real_parent, "/") == 0 ? "" : "/", name);
        if (len < 0 || (size_t)len >= sizeof(resolved)) {
            errno = ENAMETOOLONG;
            return -1;
        }
        in = resolved;
    }
    int len;
    if (strncmp(in, "/storage/emulated/", 18) == 0) {
        len = snprintf(out, out_size, "/data/media/%s", in + 18);
    } else if (strcmp(in, "/storage/emulated") == 0) {
        len = snprintf(out, out_size, "/data/media");
    } else {
        len = snprintf(out, out_size, "%s", in);
    }
    if (len < 0 || (size_t)len >= out_size) {
        errno = ENAMETOOLONG;
        return -1;
    }
    return 0;
}

typedef struct {
    uint64_t files;
    uint64_t dirs;
    uint64_t bytes;
} ScanStats;

static void scan_recursive(const char *path, ScanStats *stats) {
    if (g_cancelled) return;
    struct stat st;
    if (lstat(path, &st) != 0) return;

    if (S_ISDIR(st.st_mode)) {
        stats->dirs++;
        DIR *dir = opendir(path);
        if (!dir) return;
        struct dirent *entry;
        char subpath[4096];
        while ((entry = readdir(dir)) != NULL) {
            if (g_cancelled) break;
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
            snprintf(subpath, sizeof(subpath), "%s/%s", path, entry->d_name);
            scan_recursive(subpath, stats);
        }
        closedir(dir);
    } else if (S_ISREG(st.st_mode)) {
        stats->files++;
        stats->bytes += (uint64_t)st.st_size;
    } else {
        // Symlink or special
        stats->files++;
    }
}

static int do_scan(int count, char *paths[]) {
    ScanStats total = {0, 0, 0};
    uint64_t t0 = current_time_ms();
    for (int i = 0; i < count; i++) {
        if (g_cancelled) break;
        char fast_path[4096];
        if (resolve_fast_path(paths[i], fast_path, sizeof(fast_path)) != 0) return 1;
        ScanStats item_stats = {0, 0, 0};
        scan_recursive(fast_path, &item_stats);
        total.files += item_stats.files;
        total.dirs += item_stats.dirs;
        total.bytes += item_stats.bytes;
        printf("ITEM_RESULT idx=%d files=%" PRIu64 " dirs=%" PRIu64 " bytes=%" PRIu64 "\n",
               i, item_stats.files, item_stats.dirs, item_stats.bytes);
    }
    uint64_t elapsed = current_time_ms() - t0;
    if (g_cancelled) {
        printf("CANCELLED\n");
        fflush(stdout);
        return 1;
    }
    printf("SCAN_RESULT files=%" PRIu64 " dirs=%" PRIu64 " bytes=%" PRIu64 " ms=%" PRIu64 "\n",
           total.files, total.dirs, total.bytes, elapsed);
    fflush(stdout);
    return 0;
}

typedef struct {
    uint64_t done_files;
    uint64_t done_bytes;
    uint64_t last_report_ms;
    uid_t dst_uid;
    gid_t dst_gid;
    bool is_shared_storage;
} CopyContext;

static void report_copy_progress(CopyContext *ctx, const char *cur_name, bool force) {
    uint64_t now = current_time_ms();
    if (!force && (now - ctx->last_report_ms < 60)) {
        return;
    }
    ctx->last_report_ms = now;
    printf("PROGRESS %" PRIu64 " %" PRIu64 " ", ctx->done_files, ctx->done_bytes);
    // Filenames must not inject extra result/PID lines into the protocol.
    if (cur_name) {
        for (const char *p = cur_name; *p; p++) putchar(*p == '\n' || *p == '\r' ? ' ' : *p);
    }
    putchar('\n');
    fflush(stdout);
}

static int copy_single_file(const char *src, const char *dst, struct stat *st, CopyContext *ctx) {
    if (g_cancelled) {
        errno = ECANCELED;
        return -1;
    }
    int sfd = open(src, O_RDONLY | O_CLOEXEC);
    if (sfd < 0) return -1;
    int dfd = -1;
    bool dst_modified = false;
    uint64_t copied = 0;
    struct stat dst_st;

    // Use the opened source, not potentially stale path attributes.
    if (fstat(sfd, st) != 0) goto fail;
    if (!S_ISREG(st->st_mode) || st->st_size < 0) {
        errno = EINVAL;
        goto fail;
    }

    mode_t mode = st->st_mode & 07777;
    // Do not truncate until we know this is a different regular file. A
    // hardlink or storage alias of the source must never destroy the source.
    dfd = open(dst, O_WRONLY | O_CREAT | O_CLOEXEC, mode | 0644);
    if (dfd < 0) goto fail;
    if (fstat(dfd, &dst_st) != 0) goto fail;
    if (!S_ISREG(dst_st.st_mode)
            || (st->st_dev == dst_st.st_dev && st->st_ino == dst_st.st_ino)) {
        errno = EINVAL;
        goto fail;
    }
    if (ftruncate(dfd, 0) != 0) goto fail;
    dst_modified = true;

    posix_fadvise(sfd, 0, 0, POSIX_FADV_SEQUENTIAL);

    const char *bname = strrchr(dst, '/');
    bname = bname ? bname + 1 : dst;

    for (;;) {
        if (g_cancelled) {
            errno = ECANCELED;
            goto fail;
        }
        ssize_t n = read(sfd, g_io_buffer, sizeof(g_io_buffer));
        if (n < 0) {
            if (errno == EINTR) continue;
            goto fail;
        }
        if (n == 0) break;
        ssize_t written = 0;
        while (written < n) {
            if (g_cancelled) {
                errno = ECANCELED;
                goto fail;
            }
            ssize_t w = write(dfd, g_io_buffer + written, n - written);
            if (w < 0) {
                if (errno == EINTR) continue;
                goto fail;
            }
            if (w == 0) {
                errno = EIO;
                goto fail;
            }
            written += w;
            copied += (uint64_t)w;
            ctx->done_bytes += (uint64_t)w;
        }
        report_copy_progress(ctx, bname, false);
    }

    struct stat final_src;
    if (fstat(sfd, &final_src) != 0 || fstat(dfd, &dst_st) != 0) goto fail;
    if (copied != (uint64_t)st->st_size || final_src.st_size != st->st_size
            || final_src.st_mtim.tv_sec != st->st_mtim.tv_sec
            || final_src.st_mtim.tv_nsec != st->st_mtim.tv_nsec
            || final_src.st_ctim.tv_sec != st->st_ctim.tv_sec
            || final_src.st_ctim.tv_nsec != st->st_ctim.tv_nsec
            || dst_st.st_size != st->st_size) {
        errno = EIO;
        goto fail;
    }

    struct utimbuf times;
    times.actime = st->st_atime;
    times.modtime = st->st_mtime;
    if (utime(dst, &times) != 0) goto fail;

    if (ctx->is_shared_storage) {
        if (chown(dst, ctx->dst_uid, ctx->dst_gid) != 0 || chmod(dst, 0660) != 0) goto fail;
    } else {
        if (chmod(dst, mode) != 0) goto fail;
    }

    // Large writes can fail at writeback/close, long after write() succeeded.
    while (fsync(dfd) != 0) {
        if (errno != EINTR) goto fail;
        if (g_cancelled) {
            errno = ECANCELED;
            goto fail;
        }
    }
    if (g_cancelled) {
        errno = ECANCELED;
        goto fail;
    }
    int close_result = close(dfd);
    dfd = -1; // Linux closes the fd even when close reports an error; never retry.
    if (close_result != 0) goto fail;
    close_result = close(sfd);
    sfd = -1;
    if (close_result != 0) goto fail;

    ctx->done_files++;
    report_copy_progress(ctx, bname, false);
    return 0;

fail: {
        int saved_errno = errno;
        if (sfd >= 0) close(sfd);
        if (dfd >= 0) close(dfd);
        if (dst_modified) unlink(dst);
        fprintf(stderr, "Copy failed from %s to %s: %s\n", src, dst, strerror(saved_errno));
        errno = saved_errno;
        return -1;
    }
}

static int copy_recursive(const char *src, const char *dst, CopyContext *ctx) {
    if (g_cancelled) {
        errno = ECANCELED;
        return -1;
    }
    struct stat st;
    if (lstat(src, &st) != 0) return -1;

    if (S_ISDIR(st.st_mode)) {
        mode_t dir_mode = ctx->is_shared_storage ? 02770 : ((st.st_mode & 07777) | 0755);
        if (mkdir(dst, dir_mode) != 0) {
            if (errno != EEXIST) return -1;
        }
        struct stat dst_st;
        if (stat(dst, &dst_st) != 0) return -1;
        if (!S_ISDIR(dst_st.st_mode)
                || (st.st_dev == dst_st.st_dev && st.st_ino == dst_st.st_ino)) {
            errno = EINVAL;
            return -1;
        }
        if (ctx->is_shared_storage) {
            if (chown(dst, ctx->dst_uid, ctx->dst_gid) != 0 || chmod(dst, 02770) != 0) return -1;
        }

        DIR *dir = opendir(src);
        if (!dir) return -1;
        char sub_src[4096];
        char sub_dst[4096];
        int result = 0;
        for (;;) {
            if (g_cancelled) {
                errno = ECANCELED;
                result = -1;
                break;
            }
            errno = 0;
            struct dirent *entry = readdir(dir);
            if (!entry) {
                if (errno != 0) result = -1;
                break;
            }
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
            int src_len = snprintf(sub_src, sizeof(sub_src), "%s/%s", src, entry->d_name);
            int dst_len = snprintf(sub_dst, sizeof(sub_dst), "%s/%s", dst, entry->d_name);
            if (src_len < 0 || (size_t)src_len >= sizeof(sub_src)
                    || dst_len < 0 || (size_t)dst_len >= sizeof(sub_dst)) {
                errno = ENAMETOOLONG;
                result = -1;
                break;
            }
            if (copy_recursive(sub_src, sub_dst, ctx) != 0) {
                result = -1;
                break;
            }
        }
        int saved_errno = errno;
        if (closedir(dir) != 0 && result == 0) return -1;
        if (result != 0) {
            errno = saved_errno;
            return -1;
        }

        struct utimbuf times;
        times.actime = st.st_atime;
        times.modtime = st.st_mtime;
        if (utime(dst, &times) != 0) return -1;

        if (ctx->is_shared_storage) {
            if (chown(dst, ctx->dst_uid, ctx->dst_gid) != 0 || chmod(dst, 02770) != 0) return -1;
        } else {
            if (chmod(dst, st.st_mode & 07777) != 0) return -1;
        }
    } else if (S_ISREG(st.st_mode)) {
        return copy_single_file(src, dst, &st, ctx);
    } else if (S_ISLNK(st.st_mode)) {
        char link_target[4096];
        ssize_t len = readlink(src, link_target, sizeof(link_target) - 1);
        if (len < 0) return -1;
        if ((size_t)len == sizeof(link_target) - 1) {
            errno = ENAMETOOLONG;
            return -1;
        }
        struct stat dst_st;
        if (lstat(dst, &dst_st) == 0) {
            if (st.st_dev == dst_st.st_dev && st.st_ino == dst_st.st_ino) {
                errno = EINVAL;
                return -1;
            }
        } else if (errno != ENOENT) return -1;
        link_target[len] = '\0';
        if (unlink(dst) != 0 && errno != ENOENT) return -1;
        if (symlink(link_target, dst) != 0) return -1;
        if (ctx->is_shared_storage && lchown(dst, ctx->dst_uid, ctx->dst_gid) != 0) return -1;
        ctx->done_files++;
    } else {
        errno = ENOTSUP;
        return -1;
    }
    return 0;
}

static int mkdir_p(const char *dir_path, uid_t uid, gid_t gid, bool is_shared) {
    char tmp[4096];
    snprintf(tmp, sizeof(tmp), "%s", dir_path);
    size_t len = strlen(tmp);
    if (len == 0) return 0;
    if (tmp[len - 1] == '/') tmp[len - 1] = '\0';
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            int result = mkdir(tmp, is_shared ? 02770 : 0755);
            if (result != 0 && errno != EEXIST) return -1;
            if (result == 0 && is_shared) {
                if (chown(tmp, uid, gid) != 0 || chmod(tmp, 02770) != 0) return -1;
            }
            *p = '/';
        }
    }
    int result = mkdir(tmp, is_shared ? 02770 : 0755);
    if (result != 0 && errno != EEXIST) return -1;
    if (result == 0 && is_shared) {
        if (chown(tmp, uid, gid) != 0 || chmod(tmp, 02770) != 0) return -1;
    }
    return 0;
}

static int do_copy(const char *src, const char *dst) {
    char fast_src[4096];
    char fast_dst[4096];
    CopyContext ctx = {0, 0, 0, 0, 0, false};
    uint64_t t0 = current_time_ms();
    int result = -1;
    printf("MPIO_PID %ld\n", (long)getpid());
    report_copy_progress(&ctx, src, true);
    if (resolve_fast_path(src, fast_src, sizeof(fast_src)) != 0
            || resolve_fast_path(dst, fast_dst, sizeof(fast_dst)) != 0) goto finished;

    // Reject directory copies into themselves (also through symlink aliases).
    char real_src[4096];
    struct stat src_st;
    if (lstat(fast_src, &src_st) != 0) goto finished;
    if (S_ISDIR(src_st.st_mode)) {
        if (!realpath(fast_src, real_src)) goto finished;
        char ancestor[4096];
        char real_ancestor[4096];
        snprintf(ancestor, sizeof(ancestor), "%s", fast_dst);
        while (!realpath(ancestor, real_ancestor)) {
            if (errno != ENOENT) goto finished;
            char *parent_slash = strrchr(ancestor, '/');
            if (!parent_slash) {
                snprintf(ancestor, sizeof(ancestor), ".");
            } else if (parent_slash == ancestor) {
                ancestor[1] = '\0';
            } else {
                *parent_slash = '\0';
            }
        }
        size_t src_len = strlen(real_src);
        if (strcmp(real_src, real_ancestor) == 0
                || (strncmp(real_src, real_ancestor, src_len) == 0
                    && (real_ancestor[src_len] == '/' || strcmp(real_src, "/") == 0))) {
            errno = EINVAL;
            goto finished;
        }
    }

    // Determine target location context (shared storage /data/media/ or /storage/emulated/)
    if (strncmp(fast_dst, "/data/media/", 12) == 0 || strncmp(fast_dst, "/storage/emulated/", 18) == 0) {
        ctx.is_shared_storage = true;
    }

    // Inspect parent directory of destination to inherit correct UID and GID (e.g. 10283:1023)
    char dst_parent[4096];
    snprintf(dst_parent, sizeof(dst_parent), "%s", fast_dst);
    char *slash = strrchr(dst_parent, '/');
    if (slash && slash != dst_parent) {
        *slash = '\0';
        struct stat pst;
        if (stat(dst_parent, &pst) == 0) {
            ctx.dst_uid = pst.st_uid;
            ctx.dst_gid = pst.st_gid;
        } else {
            // Default Android shared storage: media_rw gid = 1023
            ctx.dst_uid = 10283;
            ctx.dst_gid = 1023;
        }
        if (mkdir_p(dst_parent, ctx.dst_uid, ctx.dst_gid, ctx.is_shared_storage) != 0) goto finished;
    }

    result = copy_recursive(fast_src, fast_dst, &ctx);
finished:;
    int saved_errno = errno;
    report_copy_progress(&ctx, "", true);
    uint64_t elapsed = current_time_ms() - t0;

    if (g_cancelled) {
        printf("CANCELLED done_files=%" PRIu64 " done_bytes=%" PRIu64 " ms=%" PRIu64 "\n",
               ctx.done_files, ctx.done_bytes, elapsed);
        fflush(stdout);
        return 1;
    }
    if (result != 0) {
        fprintf(stderr, "Copy failed from %s to %s: %s\n", src, dst, strerror(saved_errno));
        printf("COPY_ERROR errno=%d done_files=%" PRIu64 " done_bytes=%" PRIu64 " ms=%" PRIu64 "\n",
               saved_errno, ctx.done_files, ctx.done_bytes, elapsed);
        fflush(stdout);
        return 1;
    }
    printf("COPY_DONE done_files=%" PRIu64 " done_bytes=%" PRIu64 " ms=%" PRIu64 "\n",
           ctx.done_files, ctx.done_bytes, elapsed);
    fflush(stdout);
    return 0;
}

typedef struct {
    uint64_t done_items;
    uint64_t last_report_ms;
} DeleteContext;

static void report_delete_progress(DeleteContext *ctx, const char *cur_name, bool force) {
    uint64_t now = current_time_ms();
    if (!force && (now - ctx->last_report_ms < 60)) {
        return;
    }
    ctx->last_report_ms = now;
    printf("DEL_PROGRESS %" PRIu64 " %s\n", ctx->done_items, cur_name ? cur_name : "");
    fflush(stdout);
}

static int delete_recursive(const char *path, DeleteContext *ctx) {
    if (g_cancelled) return -1;
    struct stat st;
    if (lstat(path, &st) != 0) return 0;

    if (S_ISDIR(st.st_mode)) {
        DIR *dir = opendir(path);
        if (dir) {
            struct dirent *entry;
            char subpath[4096];
            while ((entry = readdir(dir)) != NULL) {
                if (g_cancelled) break;
                if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
                snprintf(subpath, sizeof(subpath), "%s/%s", path, entry->d_name);
                delete_recursive(subpath, ctx);
            }
            closedir(dir);
        }
        rmdir(path);
        ctx->done_items++;
        report_delete_progress(ctx, path, false);
    } else {
        unlink(path);
        ctx->done_items++;
        report_delete_progress(ctx, path, false);
    }
    return 0;
}

static int do_delete(const char *path) {
    char fast_path[4096];
    if (resolve_fast_path(path, fast_path, sizeof(fast_path)) != 0) return 1;

    DeleteContext ctx = {0, 0};
    uint64_t t0 = current_time_ms();
    delete_recursive(fast_path, &ctx);
    report_delete_progress(&ctx, "", true);
    uint64_t elapsed = current_time_ms() - t0;

    if (g_cancelled) {
        printf("CANCELLED done_items=%" PRIu64 " ms=%" PRIu64 "\n", ctx.done_items, elapsed);
        fflush(stdout);
        return 1;
    }
    printf("DELETE_DONE done_items=%" PRIu64 " ms=%" PRIu64 "\n", ctx.done_items, elapsed);
    fflush(stdout);
    return 0;
}

int main(int argc, char *argv[]) {
    signal(SIGINT, sig_handler);
    signal(SIGTERM, sig_handler);
    signal(SIGPIPE, SIG_IGN);
    signal(SIGXFSZ, SIG_IGN); // Surface EFBIG as a copy error instead of dying mid-file.

    if (argc < 2) {
        fprintf(stderr, "MPIO v1.0 Native I/O Daemon for MP-Manager\n");
        fprintf(stderr, "Usage:\n");
        fprintf(stderr, "  %s scan <path1> [path2 ...]\n", argv[0]);
        fprintf(stderr, "  %s copy <src> <dst>\n", argv[0]);
        fprintf(stderr, "  %s delete <path>\n", argv[0]);
        return 1;
    }

    const char *cmd = argv[1];
    if (strcmp(cmd, "scan") == 0) {
        if (argc < 3) return 1;
        return do_scan(argc - 2, argv + 2);
    } else if (strcmp(cmd, "copy") == 0) {
        if (argc < 4) return 1;
        return do_copy(argv[2], argv[3]);
    } else if (strcmp(cmd, "delete") == 0) {
        if (argc < 3) return 1;
        return do_delete(argv[2]);
    } else {
        fprintf(stderr, "Unknown command: %s\n", cmd);
        return 1;
    }
}
