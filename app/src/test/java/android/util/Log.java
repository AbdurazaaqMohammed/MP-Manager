package android.util;

/** Minimal JVM stub for unit tests; mirrors android.util.Log signatures used in tests. */
public final class Log {
    private Log() {}
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int e(String tag, String msg) { return 0; }
}
