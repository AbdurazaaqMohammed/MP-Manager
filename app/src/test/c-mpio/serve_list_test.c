#define _GNU_SOURCE
#define _FILE_OFFSET_BITS 64
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define main mpio_main
#include "../../main/cpp/mpio/mpio.c"
#undef main

static int read_token(int fd, char *buf, size_t cap) {
    size_t len = 0;
    for (;;) {
        char ch;
        ssize_t n = read(fd, &ch, 1);
        if (n <= 0) return n == 0 ? 0 : -1;
        if (ch == '\0') {
            buf[len] = '\0';
            return 1;
        }
        assert(len + 1 < cap);
        buf[len++] = ch;
    }
}

static int read_line(int fd, char *buf, size_t cap) {
    size_t len = 0;
    for (;;) {
        char ch;
        ssize_t n = read(fd, &ch, 1);
        if (n <= 0) return -1;
        if (ch == '\n') {
            buf[len] = '\0';
            return 1;
        }
        assert(len + 1 < cap);
        buf[len++] = ch;
    }
}

static void send_cmd(int fd, const char *op, const char *arg) {
    assert(write(fd, op, strlen(op) + 1) == (ssize_t)(strlen(op) + 1));
    if (arg) {
        assert(write(fd, arg, strlen(arg) + 1) == (ssize_t)(strlen(arg) + 1));
    }
}

int main(int argc, char *argv[]) {
    assert(argc == 2);
    char root[4096], empty[4096], sub[4096], reg[4096], weird[4096];
    char link_dir[4096], link_file[4096], broken[4096], loop_a[4096], loop_b[4096];

    assert(snprintf(root, sizeof(root), "%s/mpio-serve-XXXXXX", argv[1]) < (int)sizeof(root));
    assert(mkdtemp(root));
    assert(snprintf(empty, sizeof(empty), "%s/empty_dir", root) < (int)sizeof(empty));
    assert(mkdir(empty, 0750) == 0);
    assert(snprintf(sub, sizeof(sub), "%s/sub_dir", root) < (int)sizeof(sub));
    assert(mkdir(sub, 0755) == 0);
    assert(chmod(sub, 0755) == 0);

    assert(snprintf(reg, sizeof(reg), "%s/data.bin", root) < (int)sizeof(reg));
    int fd = open(reg, O_CREAT | O_WRONLY | O_EXCL, 0640);
    assert(fd >= 0 && write(fd, "hello world", 11) == 11 && close(fd) == 0);
    assert(chmod(reg, 0640) == 0);

    // Filenames containing newlines, tabs, spaces and unit separators must survive NUL framing.
    assert(snprintf(weird, sizeof(weird), "%s/line1\nERR\t99\037weird name", root) < (int)sizeof(weird));
    fd = open(weird, O_CREAT | O_WRONLY | O_EXCL, 0600);
    assert(fd >= 0 && close(fd) == 0);

    assert(snprintf(link_dir, sizeof(link_dir), "%s/link_to_dir", root) < (int)sizeof(link_dir));
    assert(symlink(sub, link_dir) == 0);
    assert(snprintf(link_file, sizeof(link_file), "%s/link_to_file", root) < (int)sizeof(link_file));
    assert(symlink(reg, link_file) == 0);
    assert(snprintf(broken, sizeof(broken), "%s/broken_link", root) < (int)sizeof(broken));
    assert(symlink("/definitely-missing-mpio-target", broken) == 0);
    assert(snprintf(loop_a, sizeof(loop_a), "%s/loop_a", root) < (int)sizeof(loop_a));
    assert(snprintf(loop_b, sizeof(loop_b), "%s/loop_b", root) < (int)sizeof(loop_b));
    assert(symlink(loop_b, loop_a) == 0);
    assert(symlink(loop_a, loop_b) == 0);

    int to_child[2], from_child[2];
    assert(pipe(to_child) == 0 && pipe(from_child) == 0);
    pid_t pid = fork();
    assert(pid >= 0);
    if (pid == 0) {
        assert(dup2(to_child[0], STDIN_FILENO) >= 0);
        assert(dup2(from_child[1], STDOUT_FILENO) >= 0);
        close(to_child[0]);
        close(to_child[1]);
        close(from_child[0]);
        close(from_child[1]);
        char *serve_argv[] = {(char *)"mpio", (char *)"serve", NULL};
        _exit(mpio_main(2, serve_argv));
    }
    close(to_child[0]);
    close(from_child[1]);

    char hdr[128], tok[4096], name[4096];
    assert(read_line(from_child[0], hdr, sizeof(hdr)) == 1);
    assert(strcmp(hdr, "MPIO_SERVE 1") == 0);

    // 1. Empty directory must reply OK immediately (never ERR).
    send_cmd(to_child[1], "L", empty);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
    assert(strcmp(tok, "OK") == 0);

    // 2. Non-existent directory must reply ERR + errno without breaking stream sync.
    send_cmd(to_child[1], "L", "/definitely-missing-mpio-dir");
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
    assert(strcmp(tok, "ERR") == 0);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);

    // 3. Root fixture directory: verify entry types, symlink resolution, broken/loop safety, and weird names.
    send_cmd(to_child[1], "L", root);
    int seen_sub = 0, seen_reg = 0, seen_weird = 0, seen_ldir = 0, seen_lfile = 0, seen_broken = 0, seen_loop = 0;
    for (;;) {
        assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
        if (strcmp(tok, "OK") == 0) break;
        assert(tok[0] == 'E' && tok[1] == ' ');
        int is_dir = -1, is_file = -1, is_link = -1;
        unsigned long long size = 0;
        long long mtime = -1;
        char mode[32] = "";
        assert(sscanf(tok, "E %d %d %d %llu %lld %31s",
                      &is_dir, &is_file, &is_link, &size, &mtime, mode) == 6);
        assert(read_token(from_child[0], name, sizeof(name)) == 1);
        if (strcmp(name, "sub_dir") == 0) {
            assert(is_dir == 1 && is_file == 0 && is_link == 0);
            assert(strcmp(mode, "755") == 0);
            seen_sub++;
        } else if (strcmp(name, "data.bin") == 0) {
            assert(is_dir == 0 && is_file == 1 && is_link == 0 && size == 11);
            assert(strcmp(mode, "640") == 0);
            seen_reg++;
        } else if (strcmp(name, "line1\nERR\t99\037weird name") == 0) {
            assert(is_dir == 0 && is_file == 1 && is_link == 0);
            seen_weird++;
        } else if (strcmp(name, "link_to_dir") == 0) {
            assert(is_dir == 1 && is_file == 0 && is_link == 1);
            seen_ldir++;
        } else if (strcmp(name, "link_to_file") == 0) {
            assert(is_dir == 0 && is_file == 1 && is_link == 1 && size == 11);
            seen_lfile++;
        } else if (strcmp(name, "broken_link") == 0) {
            assert(is_dir == 0 && is_file == 1 && is_link == 1 && size == 0);
            seen_broken++;
        } else if (strcmp(name, "loop_a") == 0) {
            assert(is_dir == 0 && is_file == 1 && is_link == 1);
            seen_loop++;
        }
    }
    assert(seen_sub == 1 && seen_reg == 1 && seen_weird == 1);
    assert(seen_ldir == 1 && seen_lfile == 1 && seen_broken == 1 && seen_loop == 1);

    // 4. Stat queries over the same persistent process.
    send_cmd(to_child[1], "S", link_dir);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
    assert(strncmp(tok, "S 1 1 0 1 ", 10) == 0);

    send_cmd(to_child[1], "S", link_file);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
    assert(strncmp(tok, "S 1 0 1 1 11 ", 13) == 0);

    // 5. Unknown op + clean quit.
    send_cmd(to_child[1], "Z", NULL);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
    assert(strcmp(tok, "ERR") == 0);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);

    send_cmd(to_child[1], "Q", NULL);
    assert(read_token(from_child[0], tok, sizeof(tok)) == 1);
    assert(strcmp(tok, "BYE") == 0);

    close(to_child[1]);
    close(from_child[0]);
    int status = 0;
    assert(waitpid(pid, &status, 0) == pid && WIFEXITED(status) && WEXITSTATUS(status) == 0);

    assert(unlink(loop_b) == 0 && unlink(loop_a) == 0);
    assert(unlink(broken) == 0 && unlink(link_file) == 0 && unlink(link_dir) == 0);
    assert(unlink(weird) == 0 && unlink(reg) == 0);
    assert(rmdir(sub) == 0 && rmdir(empty) == 0 && rmdir(root) == 0);

    puts("mpio serve/list regression passed: empty dir, error recovery, symlinks, ELOOP, weird filenames, stat, persistent daemon");
    return 0;
}
