#define _GNU_SOURCE
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

// Convert /storage/emulated/0 to /data/media/0 for high-performance direct ext4 I/O
static void resolve_fast_path(const char *in, char *out, size_t out_size) {
    if (strncmp(in, "/storage/emulated/", 18) == 0) {
        snprintf(out, out_size, "/data/media/%s", in + 18);
    } else if (strcmp(in, "/storage/emulated") == 0) {
        snprintf(out, out_size, "/data/media");
    } else {
        snprintf(out, out_size, "%s", in);
    }
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
        resolve_fast_path(paths[i], fast_path, sizeof(fast_path));
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
} CopyContext;

static void report_copy_progress(CopyContext *ctx, const char *cur_name, bool force) {
    uint64_t now = current_time_ms();
    if (!force && (now - ctx->last_report_ms < 60)) {
        return;
    }
    ctx->last_report_ms = now;
    printf("PROGRESS %" PRIu64 " %" PRIu64 " %s\n", ctx->done_files, ctx->done_bytes, cur_name ? cur_name : "");
    fflush(stdout);
}

static int copy_single_file(const char *src, const char *dst, struct stat *st, CopyContext *ctx) {
    if (g_cancelled) return -1;
    int sfd = open(src, O_RDONLY);
    if (sfd < 0) return -1;

    mode_t mode = st->st_mode & 07777;
    int dfd = open(dst, O_WRONLY | O_CREAT | O_TRUNC, mode | 0644);
    if (dfd < 0) {
        close(sfd);
        return -1;
    }

    posix_fadvise(sfd, 0, 0, POSIX_FADV_SEQUENTIAL);

    const char *bname = strrchr(dst, '/');
    bname = bname ? bname + 1 : dst;

    ssize_t n;
    while ((n = read(sfd, g_io_buffer, sizeof(g_io_buffer))) > 0) {
        if (g_cancelled) break;
        ssize_t written = 0;
        while (written < n) {
            ssize_t w = write(dfd, g_io_buffer + written, n - written);
            if (w < 0) {
                if (errno == EINTR) continue;
                break;
            }
            written += w;
        }
        if (written < n) break;
        ctx->done_bytes += (uint64_t)n;
        report_copy_progress(ctx, bname, false);
    }

    close(sfd);
    close(dfd);

    if (g_cancelled) {
        unlink(dst);
        return -1;
    }

    struct utimbuf times;
    times.actime = st->st_atime;
    times.modtime = st->st_mtime;
    utime(dst, &times);
    chmod(dst, mode);

    ctx->done_files++;
    report_copy_progress(ctx, bname, false);
    return 0;
}

static int copy_recursive(const char *src, const char *dst, CopyContext *ctx) {
    if (g_cancelled) return -1;
    struct stat st;
    if (lstat(src, &st) != 0) return -1;

    if (S_ISDIR(st.st_mode)) {
        if (mkdir(dst, (st.st_mode & 07777) | 0755) != 0) {
            if (errno != EEXIST) return -1;
        }
        DIR *dir = opendir(src);
        if (!dir) return -1;
        struct dirent *entry;
        char sub_src[4096];
        char sub_dst[4096];
        while ((entry = readdir(dir)) != NULL) {
            if (g_cancelled) break;
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
            snprintf(sub_src, sizeof(sub_src), "%s/%s", src, entry->d_name);
            snprintf(sub_dst, sizeof(sub_dst), "%s/%s", dst, entry->d_name);
            copy_recursive(sub_src, sub_dst, ctx);
        }
        closedir(dir);

        struct utimbuf times;
        times.actime = st.st_atime;
        times.modtime = st.st_mtime;
        utime(dst, &times);
        chmod(dst, st.st_mode & 07777);
    } else if (S_ISREG(st.st_mode)) {
        copy_single_file(src, dst, &st, ctx);
    } else if (S_ISLNK(st.st_mode)) {
        char link_target[4096];
        ssize_t len = readlink(src, link_target, sizeof(link_target) - 1);
        if (len > 0) {
            link_target[len] = '\0';
            unlink(dst);
            symlink(link_target, dst);
        }
        ctx->done_files++;
    }
    return 0;
}

static void mkdir_p(const char *dir_path) {
    char tmp[4096];
    snprintf(tmp, sizeof(tmp), "%s", dir_path);
    size_t len = strlen(tmp);
    if (len == 0) return;
    if (tmp[len - 1] == '/') tmp[len - 1] = '\0';
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            mkdir(tmp, 0755);
            *p = '/';
        }
    }
    mkdir(tmp, 0755);
}

static int do_copy(const char *src, const char *dst) {
    char fast_src[4096];
    char fast_dst[4096];
    resolve_fast_path(src, fast_src, sizeof(fast_src));
    resolve_fast_path(dst, fast_dst, sizeof(fast_dst));

    // Ensure destination parent directory exists
    char dst_parent[4096];
    snprintf(dst_parent, sizeof(dst_parent), "%s", fast_dst);
    char *slash = strrchr(dst_parent, '/');
    if (slash && slash != dst_parent) {
        *slash = '\0';
        mkdir_p(dst_parent);
    }

    CopyContext ctx = {0, 0, 0};
    uint64_t t0 = current_time_ms();
    report_copy_progress(&ctx, src, true);
    copy_recursive(fast_src, fast_dst, &ctx);
    report_copy_progress(&ctx, "", true);
    uint64_t elapsed = current_time_ms() - t0;

    if (g_cancelled) {
        printf("CANCELLED done_files=%" PRIu64 " done_bytes=%" PRIu64 " ms=%" PRIu64 "\n",
               ctx.done_files, ctx.done_bytes, elapsed);
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
    resolve_fast_path(path, fast_path, sizeof(fast_path));

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
