#define _GNU_SOURCE
#include <jni.h>
#include <dlfcn.h>
#include <link.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>

extern char **environ;

struct base_search {
    const char *needle;
    uintptr_t base;
};

static int find_base(struct dl_phdr_info *info, size_t size, void *data) {
    struct base_search *search = (struct base_search *) data;
    const char *name = info->dlpi_name;
    if (name == NULL || name[0] == 0 || info->dlpi_addr == 0) return 0;
    /* Compare the exact file name: this library is called libmp_aapt2_jni.so,
     * so a substring match would select the wrapper instead of the binary. */
    const char *slash = strrchr(name, '/');
    const char *base_name = slash == NULL ? name : slash + 1;
    if (strcmp(base_name, search->needle) != 0) return 0;
    search->base = (uintptr_t) info->dlpi_addr;
    return 1;
}

/*
 * Runs the AAPT2 command line in a forked child:
 *  - the downloaded binary is a PIE executable with DF_1_PIE cleared, so the
 *    dynamic linker accepts dlopen() on it;
 *  - main() is called directly (the offset is supplied by the Java side);
 *  - the child exits after main returns, so aapt2's exit()/abort() cannot
 *    affect the app process.
 */
JNIEXPORT jint JNICALL
Java_io_github_abdurazaaqmohammed_util_archive_Aapt2Jni_runNative(
        JNIEnv *env, jclass clazz, jstring jlib, jobjectArray jargs,
        jstring jlog, jstring jcwd, jlong offset) {
    const char *lib = (*env)->GetStringUTFChars(env, jlib, NULL);
    const char *log = (*env)->GetStringUTFChars(env, jlog, NULL);
    const char *cwd = (*env)->GetStringUTFChars(env, jcwd, NULL);
    jsize count = (*env)->GetArrayLength(env, jargs);
    char **argv = calloc((size_t) count + 1, sizeof(char *));
    if (argv == NULL) return -110;
    for (jsize i = 0; i < count; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, jargs, i);
        argv[i] = (char *) (*env)->GetStringUTFChars(env, item, NULL);
    }
    argv[count] = NULL;

    void *handle = dlopen(lib, RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        FILE *out = fopen(log, "w");
        if (out != NULL) {
            fprintf(out, "dlopen failed: %s", dlerror());
            fclose(out);
        }
        return -100;
    }
    const char *slash = strrchr(lib, '/');
    struct base_search search = {slash ? slash + 1 : lib, 0};
    dl_iterate_phdr(find_base, &search);
    if (search.base == 0) return -101;

    pid_t pid = fork();
    if (pid == 0) {
        if (cwd != NULL) chdir(cwd);
        setenv("TMPDIR", cwd == NULL ? "/data/local/tmp" : cwd, 1);
        int lfd = open(log, O_WRONLY | O_CREAT | O_TRUNC, 0600);
        if (lfd >= 0) {
            dup2(lfd, 1);
            dup2(lfd, 2);
            close(lfd);
        }
        int nfd = open("/dev/null", O_RDONLY);
        if (nfd >= 0) {
            dup2(nfd, 0);
            close(nfd);
        }
        int (*main_fn)(int, char **, char **) =
                (int (*)(int, char **, char **)) (search.base + (uintptr_t) offset);
        int rc = main_fn((int) count, argv, environ);
        fflush(NULL);
        _exit(rc);
    }

    int status = 0;
    if (pid > 0) {
        while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
        }
    }
    int rc;
    if (pid <= 0) {
        rc = -102;
    } else if (WIFEXITED(status)) {
        rc = WEXITSTATUS(status);
    } else {
        rc = 128 + WTERMSIG(status);
    }
    for (jsize i = 0; i < count; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, jargs, i);
        (*env)->ReleaseStringUTFChars(env, item, argv[i]);
        (*env)->DeleteLocalRef(env, item);
    }
    free(argv);
    (*env)->ReleaseStringUTFChars(env, jlib, lib);
    (*env)->ReleaseStringUTFChars(env, jlog, log);
    (*env)->ReleaseStringUTFChars(env, jcwd, cwd);
    return rc;
}
