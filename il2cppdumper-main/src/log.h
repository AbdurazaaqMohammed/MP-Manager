#ifndef IL2CPP_DUMPER_LOG_H
#define IL2CPP_DUMPER_LOG_H

/**
 * @file log.h
 * @brief Platform‑independent logging for the IL2CPP dumper.
 *
 * Provides Android logcat integration when liblog.so is available,
 * falling back to stderr output. Debug logging (LOGD) is only
 * compiled in when IL2CPP_DEBUG is defined.
 */

#include <stdio.h>
#include <stdarg.h>
#include <dlfcn.h>
#include <string.h>

/* Android log.h priority constants (re‑defined to avoid extra dependency). */
#define ANDROID_LOG_DEBUG   3
#define ANDROID_LOG_INFO    4
#define ANDROID_LOG_WARN    5
#define ANDROID_LOG_ERROR   6

#ifndef LOG_TAG
#define LOG_TAG "Il2CppDumper"
#endif


/**
 * @brief Write a message to the system log (Android logcat or stderr).
 *
 * On first call it tries to load `liblog.so` (if already loaded by the
 * process) and resolves `__android_log_print`.  Subsequent calls use
 * the resolved function.  If resolution fails, output goes to stderr.
 *
 * @param level  Android log priority (ANDROID_LOG_*).
 * @param fmt    printf‑style format string.
 * @param ...    format arguments.
 */
static void _il2cpp_log_write(int level, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));

static void _il2cpp_log_write(int level, const char *fmt, ...) {
    typedef int (*log_fn_t)(int, const char *, const char *, ...);
    static log_fn_t s_fn  = NULL;
    static int      s_ok  = 0;     /* initialization flag (0 = not done) */

    if (!s_ok) {
        s_ok = 1;
        /* Try to obtain a handle to the already loaded liblog.so */
        void *h = dlopen("liblog.so", RTLD_NOW | RTLD_NOLOAD);
        if (!h) h = dlopen("liblog.so", RTLD_NOW);  /* fallback: explicit load */
        if (h) {
            void *sym = dlsym(h, "__android_log_print");
            if (sym) __builtin_memcpy(&s_fn, &sym, sizeof(sym));
        }
    }

    /* Format the message into a temporary buffer */
    char buf[2048];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    if (s_fn) {
        s_fn(level, LOG_TAG, "%s", buf);
    } else {
        /* Fallback: write to stderr with tag prefix */
        fprintf(stderr, "[%s] %s\n", LOG_TAG, buf);
    }
}


/* Convenience macros that automatically set the log level.
   They all forward to _il2cpp_log_write. */
#define LOGI(fmt, ...) _il2cpp_log_write(ANDROID_LOG_INFO,  fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) _il2cpp_log_write(ANDROID_LOG_WARN,  fmt, ##__VA_ARGS__)
#define LOGE(fmt, ...) _il2cpp_log_write(ANDROID_LOG_ERROR, fmt, ##__VA_ARGS__)

#ifdef IL2CPP_DEBUG
/* Debug logging includes source file and line number. */
#  define LOGD(fmt, ...) \
       _il2cpp_log_write(ANDROID_LOG_DEBUG, \
           "[%s:%d] " fmt, __FILE__, __LINE__, ##__VA_ARGS__)
#else
#  define LOGD(fmt, ...) ((void)0)   /* suppress in release builds */
#endif

#endif