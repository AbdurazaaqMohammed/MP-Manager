/**
 * @file jni_entry.c
 * @brief JNI entry point and background dump orchestration.
 *
 * This module is the bridge between the Android Java/Kotlin app and the
 * native IL2CPP dumper.  It performs three major tasks:
 *
 *   1. On library load (constructor attribute), spawn a detached thread that
 *      waits for `libil2cpp.so` to be mapped, resolves the IL2CPP API, and
 *      writes a `dump.cs` listing of all managed types.
 *   2. Expose a JNI call (`nativeStart`) that can be invoked from managed
 *      code to trigger the same dump on demand.
 *   3. Install a temporary `SIGSEGV` / `SIGBUS` handler on the dump thread
 *      to protect the host application from crashes during the memory scan.
 *
 * @section security Safe crash handling
 * The dump traverses IL2CPP internal data structures; if those structures are
 * corrupt or not yet initialised the process would normally crash.  We use
 * `sigsetjmp`/`siglongjmp` to gracefully abort the dump instead of killing
 * the whole application.
 */

#define _GNU_SOURCE
#define _POSIX_C_SOURCE 200809L

#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <setjmp.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <dlfcn.h>
#include <link.h>
#include <elf.h>
#include <limits.h>

#include "log.h"
#include "il2cpp_api.h"
#include "il2cpp_dump.h"
#include "elf_sym_find.h"

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

/*
 * JNI naming helpers.
 *
 * pkg  : lower‑case native package segment  (e.g. com_il2cppapp).
 * cls  : Java class name           (Il2CppDumper).
 * fn   : native method name.
 *
 * The convention is Java_<pkg>_<cls>_<fn>.
 */
#define JAVA_PKG_NATIVE  com_il2cppapp_Il2CppDumper
#define _JNI_STR(pkg, cls, fn)  Java_ ## pkg ## _ ## cls ## _ ## fn
#define JNI_EXPORT(pkg, fn)     _JNI_STR(pkg, Il2CppDumper, fn)

/** Saved JVM pointer, obtained in JNI_OnLoad, used for JNIEnv retrieval. */
static JavaVM *g_jvm = NULL;

/* -------------------------------------------------------------------------
 * Crash‑safe dump execution
 * ------------------------------------------------------------------------- */
#define DUMP_SIGSTACK_SIZE 16384

/** Alternative signal stack for the crash handler. */
static uint8_t              g_sigstack[DUMP_SIGSTACK_SIZE];
/** Jump buffer for longjmp back to dump entry. */
static sigjmp_buf           g_crash_jmp;
/** Guard to prevent re‑entry into the crash handler. */
static volatile sig_atomic_t g_in_dump  = 0;
/** Saved previous SIGSEGV/SIGBUS actions (restored after dump). */
static struct sigaction     g_old_segv;
static struct sigaction     g_old_bus;

/**
 * @brief Signal handler installed during the dump.
 *
 * If the crash occurs while we are inside the dump (g_in_dump == 1),
 * we perform a siglongjmp back to the safe point in dump_thread().
 * Otherwise we forward the signal to the previously installed handler
 * or set it to default behaviour to avoid interfering with normal app
 * crashes.
 *
 * @param sig   signal number (SIGSEGV or SIGBUS).
 * @param si    additional signal info (unused).
 * @param ctx   CPU context (unused).
 */
static void crash_signal_handler(int sig, siginfo_t *si, void *ctx) {
    (void)si; (void)ctx;

    if (g_in_dump) {
        g_in_dump = 0;
        siglongjmp(g_crash_jmp, sig);   /* abort dump, return error */
    }

    /* Forward signal to original handler or restore default action. */
    struct sigaction *old = (sig == SIGSEGV) ? &g_old_segv : &g_old_bus;
    if (old->sa_flags & SA_SIGINFO) {
        old->sa_sigaction(sig, si, ctx);
    } else if (old->sa_handler != SIG_IGN && old->sa_handler != SIG_DFL) {
        old->sa_handler(sig);
    } else {
        signal(sig, SIG_DFL);
        raise(sig);
    }
}

/**
 * @brief Register the crash handler on an alternate stack.
 *
 * The alternate stack is necessary because a stack overflow could also
 * trigger SIGSEGV; with a dedicated stack the handler can still run.
 */
static void install_crash_handler(void) {
    /* Configure alternate signal stack */
    stack_t ss;
    ss.ss_sp    = g_sigstack;
    ss.ss_size  = sizeof(g_sigstack);
    ss.ss_flags = 0;
    sigaltstack(&ss, NULL);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_signal_handler;
    sa.sa_flags     = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);

    sigaction(SIGSEGV, &sa, &g_old_segv);
    sigaction(SIGBUS,  &sa, &g_old_bus);
}

/**
 * @brief Restore original signal handlers and disable alternate stack.
 */
static void uninstall_crash_handler(void) {
    sigaction(SIGSEGV, &g_old_segv, NULL);
    sigaction(SIGBUS,  &g_old_bus,  NULL);

    /* Disable alternate stack */
    stack_t ss;
    ss.ss_flags = SS_DISABLE;
    ss.ss_sp    = g_sigstack;
    ss.ss_size  = sizeof(g_sigstack);
    sigaltstack(&ss, NULL);
}

/* -------------------------------------------------------------------------
 * Helper: mkdir -p
 * ------------------------------------------------------------------------- */
/**
 * @brief Create a directory tree (like `mkdir -p`).
 *
 * @param path Full path to create.
 */
static void mkdir_p(const char *path) {
    char   tmp[PATH_MAX];
    size_t len = strnlen(path, sizeof(tmp) - 1);
    if (!len || len >= sizeof(tmp)) return;
    memcpy(tmp, path, len);
    tmp[len] = '\0';
    char *p;
    for (p = tmp + 1; *p; ++p) {
        if (*p == '/') { *p = '\0'; mkdir(tmp, 0755); *p = '/'; }
    }
    mkdir(tmp, 0755);
}

/* -------------------------------------------------------------------------
 * Process / app identification
 * ------------------------------------------------------------------------- */
/**
 * @brief Read the process name from /proc/self/cmdline.
 *
 * The cmdline file contains the executable path, often with a trailing
 * colon (for Android apps: `com.example.app:`).  We extract the part
 * before the first colon / NUL as the package name.
 *
 * @param buf Output buffer (at least sz bytes).
 * @param sz  Size of output buffer.
 */
static void read_package_name(char *buf, size_t sz) {
    buf[0] = '\0';
    FILE *f = fopen("/proc/self/cmdline", "rb");
    if (!f) { snprintf(buf, sz, "unknown"); return; }
    size_t n = fread(buf, 1, sz - 1, f);
    fclose(f);
    if (!n) { snprintf(buf, sz, "unknown"); return; }
    buf[n] = '\0';
    /* cmdline may contain NULLs; stop at first NUL or colon. */
    size_t i;
    for (i = 0; i < n && buf[i]; ++i) {}
    buf[i] = '\0';
    char *colon = strchr(buf, ':');
    if (colon) *colon = '\0';
    if (!buf[0]) snprintf(buf, sz, "unknown");
    LOGI("Package name: %s", buf);
}

/* -------------------------------------------------------------------------
 * Obtain data directory via JNI (Context.getFilesDir())
 * ------------------------------------------------------------------------- */
/**
 * @brief Retrieve the app's internal files directory using JNI.
 *
 * This is the preferred output location because it does not require
 * external storage permissions (Android 10+).  It mimics:
 *
 *   ActivityThread.currentApplication().getFilesDir().getAbsolutePath()
 *
 * The call may block until the Application object is created (up to
 * 30 seconds). Any pending JNI exceptions are explicitly cleared
 * after each call to avoid spurious failures in certain container
 * environments.
 *
 * @param out_buf Buffer to receive the path.
 * @param out_sz  Size of out_buf.
 * @return 1 on success, 0 on failure.
 */
static int get_files_dir_jni(char *out_buf, size_t out_sz) {
    if (!g_jvm) { LOGW("get_files_dir_jni: g_jvm NULL"); return 0; }

    JNIEnv *env      = NULL;
    int     attached = 0;
    int     result   = 0;

    jint rc = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            LOGW("get_files_dir_jni: AttachCurrentThread failed");
            return 0;
        }
        attached = 1;
    } else if (rc != JNI_OK) {
        LOGW("get_files_dir_jni: GetEnv failed %d", (int)rc);
        return 0;
    }

    /*
     * Build JNI call chain:
     *   ActivityThread.currentApplication()  -> Application
     *   .getFilesDir()                       -> java.io.File
     *   .getAbsolutePath()                   -> String
     */
    jclass   cls_at   = NULL;
    jmethodID mid_ca  = NULL;
    jobject  app      = NULL;
    jclass   cls_ctx  = NULL;
    jmethodID mid_gfd = NULL;
    jobject  file     = NULL;
    jclass   cls_file = NULL;
    jmethodID mid_gap = NULL;
    jstring  path_str = NULL;
    const char *chars = NULL;

#define CLR_EXC() do { if ((*env)->ExceptionCheck(env)) \
                           (*env)->ExceptionClear(env); } while(0)

    cls_at = (*env)->FindClass(env, "android/app/ActivityThread");
    if (!cls_at) { CLR_EXC(); goto jni_done; }
    /* Avoid spurious failures in some
container configurations. */
    CLR_EXC();

    mid_ca = (*env)->GetStaticMethodID(env, cls_at,
                 "currentApplication", "()Landroid/app/Application;");
    if (!mid_ca) { CLR_EXC(); goto jni_done; }

    /* Wait for Application object (it may not be available immediately) */
    {
        int retry;
        for (retry = 0; retry < 30; ++retry) {
            app = (*env)->CallStaticObjectMethod(env, cls_at, mid_ca);
            CLR_EXC();
            if (app) break;
            if (retry % 5 == 0)
                LOGI("Waiting for Application object (%d/30)...", retry);
            sleep(1);
        }
    }
    if (!app) { LOGW("currentApplication() returned null"); goto jni_done; }

    cls_ctx = (*env)->FindClass(env, "android/content/Context");
    if (!cls_ctx) { CLR_EXC(); goto jni_done; }

    mid_gfd = (*env)->GetMethodID(env, cls_ctx,
                  "getFilesDir", "()Ljava/io/File;");
    if (!mid_gfd) { CLR_EXC(); goto jni_done; }

    file = (*env)->CallObjectMethod(env, app, mid_gfd);
    CLR_EXC();
    if (!file) { LOGW("getFilesDir() returned null"); goto jni_done; }

    cls_file = (*env)->FindClass(env, "java/io/File");
    if (!cls_file) { CLR_EXC(); goto jni_done; }

    mid_gap = (*env)->GetMethodID(env, cls_file,
                  "getAbsolutePath", "()Ljava/lang/String;");
    if (!mid_gap) { CLR_EXC(); goto jni_done; }

    path_str = (jstring)(*env)->CallObjectMethod(env, file, mid_gap);
    CLR_EXC();
    if (!path_str) goto jni_done;

    chars = (*env)->GetStringUTFChars(env, path_str, NULL);
    if (chars && chars[0]) {
        strncpy(out_buf, chars, out_sz - 1);
        out_buf[out_sz - 1] = '\0';
        result = 1;
        LOGI("getFilesDir() = %s", out_buf);
    }
    if (chars) (*env)->ReleaseStringUTFChars(env, path_str, chars);

jni_done:
    /* Clean up local references */
    if (path_str) (*env)->DeleteLocalRef(env, path_str);
    if (cls_file) (*env)->DeleteLocalRef(env, cls_file);
    if (file)     (*env)->DeleteLocalRef(env, file);
    if (cls_ctx)  (*env)->DeleteLocalRef(env, cls_ctx);
    if (app)      (*env)->DeleteLocalRef(env, app);
    if (cls_at)   (*env)->DeleteLocalRef(env, cls_at);

    if (attached) (*g_jvm)->DetachCurrentThread(g_jvm);
    return result;

#undef CLR_EXC
}

/* -------------------------------------------------------------------------
 * IL2CPP library discovery – three‑tier strategy
 * ------------------------------------------------------------------------- */

/**
 * @brief Quick ELF magic check at a given address.
 *
 * @param addr potential base address of an ELF image.
 * @return 1 if the ELF magic (\x7fELF) is present, 0 otherwise.
 */
static int is_elf_at(uintptr_t addr) {
    if (!addr) return 0;
    const unsigned char *m = (const unsigned char *)addr;
    return (m[0] == 0x7f && m[1] == 'E' && m[2] == 'L' && m[3] == 'F');
}

/**
 * @brief Tier A – scan /proc/self/maps for libil2cpp.so.
 *
 * Looks for readable entries whose path ends with "libil2cpp.so",
 * selects the one with the lowest start address, and verifies the
 * ELF signature.
 *
 * @param out_path  buffer for the full path of the library.
 * @param psz       size of out_path.
 * @param out_base  receives the detected base address.
 * @return 1 on success, 0 on failure.
 */
static int find_il2cpp_by_maps(char *out_path, size_t psz, uintptr_t *out_base) {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;

    char      line[1024];
    uintptr_t best  = (uintptr_t)-1;
    char      bpath[PATH_MAX] = {0};

    while (fgets(line, sizeof(line), maps)) {
        if (!strstr(line, "libil2cpp")) continue;

        unsigned long s = 0, e = 0, off = 0, ino = 0;
        char perms[8] = {0}, dev[16] = {0}, path[PATH_MAX] = {0};

        int r = sscanf(line, "%lx-%lx %7s %lx %15s %lu %4095s",
                       &s, &e, perms, &off, dev, &ino, path);

        if (r < 7 || !path[0] || path[0] == '[') continue;
        if (!strstr(path, "libil2cpp.so"))        continue;
        if (perms[0] != 'r')                      continue;

        /* Track the lowest start address = ELF header location */
        if ((uintptr_t)s < best) {
            best = (uintptr_t)s;
            strncpy(bpath, path, sizeof(bpath) - 1);
            bpath[sizeof(bpath) - 1] = '\0';
        }
    }
    fclose(maps);

    if (best == (uintptr_t)-1 || !bpath[0]) return 0;
    if (!is_elf_at(best)) {
        LOGW("maps_scan: no ELF magic at 0x%lx (%s)", (unsigned long)best, bpath);
        return 0;
    }

    *out_base = best;
    strncpy(out_path, bpath, psz - 1);
    out_path[psz - 1] = '\0';
    LOGI("Tier A found: base=0x%lx path=%s", (unsigned long)best, bpath);
    return 1;
}

/* ------------------------------------------------------------------
 * Tier B: dl_iterate_phdr
 * ------------------------------------------------------------------ */
typedef struct { char path[PATH_MAX]; uintptr_t base; int found; } PhdrCtx;

static int phdr_cb(struct dl_phdr_info *info, size_t size, void *arg) {
    (void)size;
    PhdrCtx *ctx = (PhdrCtx *)arg;
    if (!info || !info->dlpi_name) return 0;
    if (!strstr(info->dlpi_name, "libil2cpp.so")) return 0;

    uintptr_t base = (uintptr_t)info->dlpi_addr;

    /* dlpi_addr is load bias; for p_vaddr==0 SOs it equals the base address.
     * For non-zero p_vaddr, add the minimum PT_LOAD vaddr to get the header. */
    if (!is_elf_at(base)) {
        uintptr_t min_vaddr = (uintptr_t)-1;
        ElfW(Half) i;
        for (i = 0; i < info->dlpi_phnum; ++i) {
            if (info->dlpi_phdr[i].p_type == PT_LOAD) {
                uintptr_t v = (uintptr_t)info->dlpi_phdr[i].p_vaddr;
                if (v < min_vaddr) min_vaddr = v;
            }
        }
        if (min_vaddr != (uintptr_t)-1)
            base = (uintptr_t)info->dlpi_addr + min_vaddr;
        if (!is_elf_at(base)) return 0;
    }

    ctx->base = base;
    strncpy(ctx->path, info->dlpi_name, sizeof(ctx->path) - 1);
    ctx->path[sizeof(ctx->path) - 1] = '\0';
    ctx->found = 1;
    return 1; /* stop iteration */
}

/**
 * @brief Locate libil2cpp.so via dl_iterate_phdr.
 *
 * Iterates over all loaded shared objects and finds the one whose
 * name contains "libil2cpp.so".  It computes the runtime base by
 * adding the smallest PT_LOAD vaddr to the dlpi_addr reported by
 * the linker (as some containers mangle the reported base).
 *
 * @param out_path  buffer for the library path.
 * @param psz       size of out_path.
 * @param out_base  receives the computed base address.
 * @return 1 on success, 0 on failure.
 */
static int find_il2cpp_by_phdr(char *out_path, size_t psz, uintptr_t *out_base) {
    PhdrCtx ctx;
    memset(&ctx, 0, sizeof(ctx));
    dl_iterate_phdr(phdr_cb, &ctx);
    if (!ctx.found) return 0;
    *out_base = ctx.base;
    strncpy(out_path, ctx.path, psz - 1);
    out_path[psz - 1] = '\0';
    LOGI("Tier B found: base=0x%lx path=%s", (unsigned long)ctx.base, ctx.path);
    return 1;
}

/**
 * @brief Tier C – brute‑force scan of all readable, offset‑0 mappings.
 *
 * Opens /proc/self/mem, scans every readable region with file offset 0,
 * checks for the ELF magic, and then resolves the `il2cpp_domain_get`
 * symbol to confirm that the region is indeed libil2cpp.so.  This is
 * the final fallback and can be slow.
 *
 * @param out_path  buffer for the library path (or a synthetic name).
 * @param psz       size of out_path.
 * @param out_base  receives the detected base address.
 * @return 1 on success, 0 on failure.
 */
static int find_il2cpp_by_brute(char *out_path, size_t psz, uintptr_t *out_base) {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;

    /* Open /proc/self/mem for safe pread()-based magic checks.
     * pread() returns -1 on unreadable/XOM pages — never faults. */
    int memfd = open("/proc/self/mem", O_RDONLY);

    char line[1024];
    int  found = 0;

    while (fgets(line, sizeof(line), maps) && !found) {
        unsigned long s = 0, e = 0, off = 0, ino = 0;
        char perms[8] = {0}, dev[16] = {0}, path[PATH_MAX] = {0};

        int r = sscanf(line, "%lx-%lx %7s %lx %15s %lu %4095s",
                       &s, &e, perms, &off, dev, &ino, path);

        if (r < 6)           continue;
        if (perms[0] != 'r') continue;
        if (off != 0)        continue;
        if (path[0] == '[')  continue;
        if (strstr(path, "/dev/")  ||
            strstr(path, "/proc/") ||
            strstr(path, "/sys/"))  continue;

        uintptr_t base = (uintptr_t)s;

        /* Safe ELF magic check via pread() — no fault on XOM pages */
        int magic_ok = 0;
        if (memfd >= 0) {
            unsigned char magic[4] = {0};
            ssize_t n = pread(memfd, magic, 4, (off_t)base);
            magic_ok  = (n == 4 &&
                         magic[0] == 0x7f && magic[1] == 'E' &&
                         magic[2] == 'L'  && magic[3] == 'F');
        } else {
            /* Fallback: direct read protected by thread crash handler */
            magic_ok = is_elf_at(base);
        }
        if (!magic_ok) continue;

        /* Confirm: probe il2cpp_domain_get via in-memory ELF parse.
         * Protected by thread-level crash handler if a bad page is hit. */
        uintptr_t sym = elf_sym_find(base, "il2cpp_domain_get");
        if (!sym) continue;

        *out_base = base;
        if (r >= 7 && path[0])
            strncpy(out_path, path, psz - 1);
        else
            snprintf(out_path, psz, "<anon@0x%lx>", (unsigned long)base);
        out_path[psz - 1] = '\0';
        LOGI("Tier C found: base=0x%lx path=%s", (unsigned long)base, out_path);
        found = 1;
    }

    if (memfd >= 0) close(memfd);
    fclose(maps);
    return found;
}

/**
 * @brief Dispatch: try all three discovery tiers in order.
 *
 *   Tier A – /proc/self/maps with ELF check.
 *   Tier B – dl_iterate_phdr.
 *   Tier C – brute‑force scan.
 *
 * The first successful tier sets `out_path` and `out_base`.
 *
 * @param out_path buffer for the library path.
 * @param path_sz  size of out_path.
 * @param out_base receives the base address.
 * @return 1 if any tier succeeded, 0 otherwise.
 */
static int find_il2cpp_in_maps(char *out_path, size_t path_sz,
                                uintptr_t *out_base) {
    *out_base   = 0;
    out_path[0] = '\0';

    if (find_il2cpp_by_maps(out_path, path_sz, out_base))  return 1;
    LOGW("Tier A failed; trying dl_iterate_phdr...");

    if (find_il2cpp_by_phdr(out_path, path_sz, out_base))  return 1;
    LOGW("Tier B failed; trying brute ELF scan (slow)...");

    if (find_il2cpp_by_brute(out_path, path_sz, out_base)) return 1;

    return 0;
}

 /* -------------------------------------------------------------------------
 * Background dump thread
 * ------------------------------------------------------------------------- */
/**
 * @brief Main dump thread routine.
 *
 * Steps (all performed under crash‑handler protection):
 *   1. Install SIGSEGV/SIGBUS handler and enter safe region.
 *   2. Wait for libil2cpp.so to appear in memory (up to 60 s).
 *   3. Identify the app package.
 *   4. Determine an output directory:
 *        a. JNI‑acquired files dir (preferred, no permissions needed).
 *        b. /data/data/<pkg>/files/il2cpp_dump (fallback).
 *        c. /sdcard/il2cpp_dump (last resort).
 *   5. dlopen libil2cpp.so / RTLD_DEFAULT and resolve the IL2CPP API.
 *   6. Wait for domain, assemblies, and attach a thread.
 *   7. Invoke the dump (il2cpp_do_dump).
 *   8. Detach IL2CPP thread, uninstall crash handler, and exit.
 *
 * Any crash during steps 2‑7 causes a graceful abort
 * (log message, cleanup, thread exits).
 *
 * @param arg Unused.
 * @return Always NULL.
 */
static void *dump_thread(void *arg) {
    (void)arg;
#ifdef SYS_gettid
    LOGI("dump_thread tid=%ld", (long)syscall(SYS_gettid));
#endif

    /* Install crash handler and enter safe region. */
    install_crash_handler();
    g_in_dump = 1;
    int crash_sig = sigsetjmp(g_crash_jmp, 1);
    if (crash_sig != 0) {
        /* Crash occurred during dump; cleanly abort. */
        LOGE("dump_thread caught signal %d during setup/discovery; "
             "aborting safely.", crash_sig);
        g_in_dump = 0;
        uninstall_crash_handler();
        return NULL;
    }
    
    char      il2cpp_path[PATH_MAX] = {0};
    uintptr_t il2cpp_base = 0;
    int       r;

    /* Wait until libil2cpp.so is mapped.  Some games load it lazily. */
    {
        int found = 0;
        for (r = 0; r < 60 && !found; ++r) {
            found = find_il2cpp_in_maps(il2cpp_path, sizeof(il2cpp_path),
                                        &il2cpp_base);
            if (!found) {
                if (r % 10 == 0)
                    LOGI("Waiting for libil2cpp.so... (%d/60)", r);
                sleep(1);
            }
        }
        if (!found) {
            LOGE("libil2cpp.so not found after 60 s. Not an il2cpp game?");
            return NULL;
        }
    }

    /* Identify the app package (for output path construction). */
    char pkg[256] = {0};
    read_package_name(pkg, sizeof(pkg));

    char output_dir[PATH_MAX] = {0};

    /* Strategy 1: JNI getFilesDir(). Preferred: the app's own private dir is
     * always writable, and the DocumentsProvider MP-Manager injects exposes it
     * (root "data"), so a file manager can read the dump with no root. */
    {
        char files_dir[PATH_MAX] = {0};
        if (get_files_dir_jni(files_dir, sizeof(files_dir))) {
            snprintf(output_dir, sizeof(output_dir),
                     "%s/il2cpp_dump", files_dir);
            mkdir(output_dir, 0755);   /* best effort */
            LOGI("Output dir (JNI): %s", output_dir);
        }
    }

    /* Strategy 2: /data/data/<pkg>/files/il2cpp_dump, for when the JNI call
     * could not resolve the Context (Application still initialising). */
    if (!output_dir[0] && pkg[0] && strcmp(pkg, "unknown") != 0) {
        snprintf(output_dir, sizeof(output_dir),
                 "/data/data/%s/files/il2cpp_dump", pkg);
        mkdir_p(output_dir);
        LOGI("Output dir (cmdline): %s", output_dir);
    }

    /* Strategy 3: scoped external files dir of the host app (Android/data).
     * Only a fallback: it is writable by the app but on Android 11+ nothing
     * else can read it, and a plain /sdcard write usually fails too. */
    if (!output_dir[0] && pkg[0] && strcmp(pkg, "unknown") != 0) {
        snprintf(output_dir, sizeof(output_dir),
                 "/storage/emulated/0/Android/data/%s/files/il2cpp_dump", pkg);
        mkdir_p(output_dir);
        char probe[PATH_MAX];
        snprintf(probe, sizeof(probe), "%s/.wprobe", output_dir);
        int pfd = open(probe, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (pfd >= 0) {
            close(pfd);
            unlink(probe);
            LOGI("Output dir (external): %s", output_dir);
        } else {
            LOGW("External dir not writable: %s errno=%d (%s)",
                 output_dir, errno, strerror(errno));
            output_dir[0] = '\0';
        }
    }

    /* Strategy 4: Fallback to /sdcard. */
    if (!output_dir[0]) {
        snprintf(output_dir, sizeof(output_dir), "/sdcard/il2cpp_dump");
        mkdir_p(output_dir);
        LOGW("Output dir (sdcard fallback): %s", output_dir);
    }

    /* Obtain a handle to resolve IL2CPP API functions.
     * Try: already loaded libil2cpp.so, then explicit dlopen, then global. */
    void *handle = dlopen(il2cpp_path, RTLD_NOLOAD | RTLD_NOW);
    if (!handle) handle = dlopen("libil2cpp.so", RTLD_NOLOAD | RTLD_NOW);
    if (!handle) {
        void *probe = dlsym(RTLD_DEFAULT, "il2cpp_domain_get");
        if (probe) handle = RTLD_DEFAULT;
    }
    LOGI("dlopen handle: %p  (%s)",
         handle, handle ? "dlsym path" : "ELF resolver path");

    /* Initialise function pointer table and compute base address. */
    if (!il2cpp_api_init_ex(handle, il2cpp_base)) {
        LOGE("il2cpp_api_init_ex failed. Aborting.");
        return NULL;
    }

    /* Wait for IL2CPP domain to become available (runtime may still be
       initialising). */
    LOGI("Waiting for il2cpp domain...");
    sleep(1);   // ← THIS. Give il2cpp_init() time to run.
    for (r = 0; r < 60; ++r) {
        if (il2cpp_domain_get && il2cpp_domain_get()) break;
        sleep(1);
    }
    if (!il2cpp_domain_get || !il2cpp_domain_get()) {
        LOGW("Domain not ready after 60 s; attempting dump anyway.");
    } else {
        LOGI("Domain ready.");
    }
    sleep(1); /* extra settling time */

    /* Attach the current thread to the IL2CPP domain (necessary for some
       API calls that require a managed thread context). */
    Il2CppThread *il2cpp_thread = NULL;
    if (il2cpp_domain_get && il2cpp_thread_attach) {
        Il2CppDomain *dom = il2cpp_domain_get();
        if (dom) {
            il2cpp_thread = il2cpp_thread_attach(dom);
            if (il2cpp_thread) LOGI("Thread attached to il2cpp domain.");
        }
    }

    /* Wait for at least one assembly to be loaded (indicates the runtime
       has finished loading the main application code). */
    LOGI("Waiting for assemblies...");
    {
        size_t asm_count = 0;
        for (r = 0; r < 30; ++r) {
            if (il2cpp_domain_get && il2cpp_domain_get_assemblies) {
                Il2CppDomain *dom = il2cpp_domain_get();
                if (dom) {
                    const Il2CppAssembly **list =
                        il2cpp_domain_get_assemblies(dom, &asm_count);
                    if (list && asm_count > 0) {
                        LOGI("Assemblies ready: %zu loaded.", asm_count);
                        break;
                    }
                }
            }
            if (r % 5 == 0)
                LOGI("Assemblies not ready yet (%d/30)...", r);
            sleep(1);
        }
        if (!asm_count)
            LOGW("Assembly count still 0 after 30 s; proceeding anyway.");
    }

    /* Ensure output directory exists. */
    mkdir(output_dir, 0755);
    char output_file[PATH_MAX];
    snprintf(output_file, sizeof(output_file), "%s/dump.cs", output_dir);

    /* Normal execution path. */
    int ok = il2cpp_do_dump(output_file, output_dir);
    if (ok) { LOGI("SUCCESS: %s", output_file); }
    else    { LOGE("FAILED:  %s", output_file); }

    /* Detach from IL2CPP domain if we attached earlier. */
    if (il2cpp_thread && il2cpp_thread_detach) {
        il2cpp_thread_detach(il2cpp_thread);
    }

    g_in_dump = 0;
    uninstall_crash_handler();
    return NULL;
}

/* -------------------------------------------------------------------------
 * Thread spawn helper
 * ------------------------------------------------------------------------- */
/**
 * @brief Create a detached thread that will execute the dump.
 *
 * The detached attribute means we never need to join it; the system
 * reclaims its resources automatically when it exits.
 */
static void spawn_dump_thread(void) {
    pthread_t      tid;
    pthread_attr_t attr;

    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

    int rc = pthread_create(&tid, &attr, dump_thread, NULL);
    if (rc) { LOGE("pthread_create failed: %d (%s)", rc, strerror(rc)); }
    else    { LOGI("Dump thread spawned."); }

    pthread_attr_destroy(&attr);
}

/* -------------------------------------------------------------------------
 * Library constructor / JNI entry points
 * ------------------------------------------------------------------------- */
/**
 * @brief Constructor executed when the shared library is loaded.
 *
 * Immediately spawns the background dump thread.  The thread will wait
 * for the IL2CPP runtime to become ready before performing the dump.
 */
__attribute__((constructor))
static void il2cpp_dumper_constructor(void) {
    LOGI("il2cpp_dumper: constructor entry");
    spawn_dump_thread();
}

/**
 * @brief Standard JNI_OnLoad entry point.
 *
 * Saves the JavaVM pointer for later JNI operations (e.g. getFilesDir)
 * and returns the supported JNI version.
 *
 * @param vm        JavaVM pointer.
 * @param reserved  Unused.
 * @return JNI_VERSION_1_6.
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_jvm = vm;
    LOGI("JNI_OnLoad: JavaVM saved.");
    return JNI_VERSION_1_6;
}

/**
 * @brief JNI‑exported function `nativeStart` for manual triggering.
 *
 * Can be called from Java/Kotlin code:
 *   `com.il2cppapp.Il2CppDumper.nativeStart(String outDir)`
 *
 * Currently the `outDir` parameter is unused; the thread uses the
 * auto‑discovery logic for the output path.
 *
 * @param env      JNI environment.
 * @param cls      Calling class.
 * @param out_dir  (unused) Proposed output directory.
 */
JNIEXPORT void JNICALL JNI_EXPORT(JAVA_PKG_NATIVE, nativeStart)(
        JNIEnv *env, jclass cls, jstring out_dir) {
    (void)env; (void)cls; (void)out_dir;
    LOGI("nativeStart() -> spawn_dump_thread");
    spawn_dump_thread();
}