package io.github.abdurazaaqmohammed.util.archive;

import java.io.File;
import java.io.IOException;

/**
 * Runs the on-demand AAPT2 binary through the small bundled
 * {@code libmp_aapt2_jni.so} helper.
 *
 * <p>The downloaded AAPT2 is a PIE executable with {@code DF_1_PIE} cleared so
 * the dynamic linker accepts it. Its {@code main()} is invoked in a forked
 * child, which keeps aapt2's {@code exit()}/global state away from the app
 * process while still loading the code with {@code dlopen()} (Android 10+
 * forbids {@code execve()} of files in app storage, but not {@code dlopen()}).</p>
 *
 * <p>The package/class/method names are part of the JNI symbol; keep them in
 * sync with {@code app/src/main/cpp/aapt2_jni.c}.</p>
 */
public final class Aapt2Jni {
    private static final boolean LOADED = tryLoad();

    private Aapt2Jni() {
    }

    public static boolean isAvailable() {
        return LOADED;
    }

    private static boolean tryLoad() {
        try {
            System.loadLibrary("mp_aapt2_jni");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @param binary     patched AAPT2 executable
     * @param args       full argv, with argv[0] naming the program
     * @param log        file that receives stdout and stderr
     * @param cwd        working directory for the child
     * @param mainOffset offset of {@code main} in the binary (see Aapt2BinaryManager)
     * @return aapt2's exit status, or a negative runner error code
     */
    public static synchronized int run(File binary, String[] args, File log, File cwd,
                                       long mainOffset) throws IOException {
        if (!LOADED) throw new IOException("AAPT2 runner is unavailable in this build");
        if (binary == null || !binary.isFile()) throw new IOException("AAPT2 binary is missing");
        if (args == null || args.length == 0) throw new IllegalArgumentException("Missing AAPT2 arguments");
        if (log == null || cwd == null) throw new IllegalArgumentException("Missing AAPT2 workspace");
        return runNative(binary.getAbsolutePath(), args, log.getAbsolutePath(),
                cwd.getAbsolutePath(), mainOffset);
    }

    private static native int runNative(String binary, String[] args, String log,
                                        String cwd, long mainOffset);
}
