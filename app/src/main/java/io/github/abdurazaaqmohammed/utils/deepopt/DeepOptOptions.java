package io.github.abdurazaaqmohammed.utils.deepopt;

/**
 * Pure-Java settings for the optimize pipelines. The Android layer maps
 * SharedPreferences onto this; the desktop test harness constructs it directly.
 */
public class DeepOptOptions {

    /** Strip META-INF signature files and force max deflate on every compressible entry. */
    public boolean repack = true;
    /** Remove the user's junk-file set (filesToDelete). */
    public boolean deleteFiles = true;
    /** Drop unreachable classes from the dexes. */
    public boolean removeClasses = true;
    /** Drop unreachable private methods. */
    public boolean removeMethods = true;
    /** Drop unreachable private fields. */
    public boolean removeFields = true;
    /** Keep debug info (source file names, line numbers, param names) when rewriting dexes. */
    public boolean preserveDebug = true;
    /** Re-parse the output and verify reference integrity; fall back to repack-only on failure. */
    public boolean verifyOutput = true;
    /**
     * Max empty-method cascade passes. The cascade rewrites bytecode (nops dead call
     * sites + dead-code elimination), which is a small size win but the riskiest pass of
     * the pipeline - off by default so deep optimize never touches instruction bodies
     * unless explicitly requested.
     */
    public int maxPasses = 0;
    /** Phase B: shorten resource entry names (opt-in, experimental; skipped when dynamic lookups or native libs exist). */
    public boolean shortenNames = false;
    /**
     * Safe mode (default): when the app resolves resources dynamically, keep every resource
     * whose entry name matches any dex string constant. Aggressive (false) ignores that
     * guard and only keeps statically referenced resources.
     */
    public boolean safeMode = true;

    public static DeepOptOptions defaults() {
        return new DeepOptOptions();
    }
}
