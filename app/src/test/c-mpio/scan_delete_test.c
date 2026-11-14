#define _GNU_SOURCE
#define _FILE_OFFSET_BITS 64
#include <assert.h>
#include <unistd.h>
#include <sys/resource.h>
static int fail_rmdir;
static int fault_rmdir(const char *path);
#define rmdir fault_rmdir
#define main mpio_main
#include "../../main/cpp/mpio/mpio.c"
#undef main
#undef rmdir

static int fault_rmdir(const char *path) {
    if (fail_rmdir) { errno = EACCES; return -1; }
    return rmdir(path);
}

int main(int argc, char *argv[]) {
    assert(argc == 2);
    char root[4096], path[4096], external[4096], list[4096];
    assert(snprintf(root, sizeof(root), "%s/mpio-scan-delete-XXXXXX", argv[1]) < (int)sizeof(root));
    assert(mkdtemp(root));
    for (int i = 0; i < 6000; i++) {
        assert(snprintf(path, sizeof(path), "%s/%s%d", root, i == 0 ? ".hidden " : "file ", i) < (int)sizeof(path));
        int fd = open(path, O_CREAT | O_WRONLY | O_EXCL, 0600);
        assert(fd >= 0 && close(fd) == 0);
    }
    assert(snprintf(path, sizeof(path), "%s/empty", root) < (int)sizeof(path));
    assert(mkdir(path, 0700) == 0);
    assert(snprintf(path, sizeof(path), "%s/sparse", root) < (int)sizeof(path));
    int fd = open(path, O_CREAT | O_WRONLY | O_EXCL, 0600);
    assert(fd >= 0 && ftruncate(fd, (off_t)4294967297ULL) == 0 && close(fd) == 0);
    assert(snprintf(external, sizeof(external), "%s-outside", root) < (int)sizeof(external));
    assert(mkdir(external, 0700) == 0);
    assert(snprintf(path, sizeof(path), "%s/link\nDELETE_DONE done_items=999", root) < (int)sizeof(path));
    assert(symlink(external, path) == 0);
    ScanStats stats = {0};
    scan_recursive(root, &stats, true);
    assert(stats.files == 6002 && stats.dirs == 2 && stats.bytes == 4294967297ULL);
    assert(stats.hidden == 1 && stats.errors == 0);
    ScanStats missing = {0};
    scan_recursive("/definitely-missing-mpio-fixture", &missing, true);
    assert(missing.errors == 1);

    // Cancellation must leave the tree intact and must not report success.
    DeleteContext context = {0};
    g_cancelled = 1;
    assert(delete_recursive(root, &context) == -1 && context.done_items == 0);
    g_cancelled = 0;
    char *paths[] = {root};
    assert(do_scan(1, paths) == 0);
    assert(snprintf(list, sizeof(list), "%s-list", root) < (int)sizeof(list));
    FILE *output = fopen(list, "wb");
    assert(output);
    assert(fwrite(root, 1, strlen(root) + 1, output) == strlen(root) + 1);
    assert(fclose(output) == 0);
    assert(from_path_list("delete", list) == 0);
    assert(access(root, F_OK) == -1 && access(external, F_OK) == 0);
    assert(unlink(list) == 0 && rmdir(external) == 0);

    // A failed rmdir must propagate (not count as a completed deletion).
    DeleteContext failed = {0};
    assert(mkdir(root, 0700) == 0);
    fail_rmdir = 1;
    assert(delete_recursive(root, &failed) == -1 && failed.done_items == 0);
    assert(do_delete(1, paths) == 1);
    fail_rmdir = 0;
    assert(rmdir(root) == 0);
    puts("mpio scan/delete regression passed: 6000+ files, empty directories, >4GiB sparse bytes, symlinks, cancellation, failure propagation, NUL path lists");
    return 0;
}
