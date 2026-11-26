package io.github.abdurazaaqmohammed.feature.apk;

import android.content.res.Configuration;
import android.util.Log;

import io.github.abdurazaaqmohammed.MPManager.BuildConfig;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded cache of APK-derived info rows shown in the APK info dialog.
 *
 * <p>Only data derived from the APK file itself is cached. Live state (installed
 * package comparison, icon Drawables, Views) is deliberately excluded and must
 * be refreshed per dialog open. Cache identity is canonical path + size +
 * last-modified + locale so rebuilt/overwritten APKs and locale changes do not
 * show stale data.
 */
public final class ApkInfoCache {

    public static final String PERF_TAG = "ApkInfoPerf";

    /** APK-derived values, safe to keep across dialog openings. */
    public static final class Snapshot {
        public String packageName = "";
        public String label = "";
        public String versionName = "";
        public long versionCode;
        public int targetSdk;
        public int minSdk = -1;
        public int entryCount;
        public String signatureLabel = "";
        public String certSha256 = "";
        public boolean signatureAnalyzed;
        public boolean protectedApk;
        public String protectionSummary;
        public boolean protectionAnalyzed;
        public boolean hasIl2Cpp;
        public boolean hasNativeLibs;
        public boolean isFlutter;
        public boolean nativeAnalyzed;
        /** True only when every extractor completed successfully. */
        public boolean complete;
        public long storedAt;
    }

    private static final int MAX_ENTRIES = 24;
    private static final long MAX_AGE_MS = 10L * 60L * 1000L;

    private static final LinkedHashMap<String, Snapshot> MAP =
            new LinkedHashMap<String, Snapshot>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private ApkInfoCache() {
    }

    /** Identity of a file right now; null when it cannot be determined. */
    public static String keyOf(File apk, Configuration config) {
        if (apk == null) return null;
        try {
            Locale locale = config != null && config.locale != null
                    ? config.locale : Locale.getDefault();
            return apk.getCanonicalPath() + ':' + apk.length() + ':' + apk.lastModified()
                    + ':' + locale.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Public convenience used by the loader; includes the current locale. */
    public static String keyOf(File apk) {
        return keyOf(apk, null);
    }

    public static synchronized Snapshot get(File apk, String key) {
        if (key == null) return null;
        Snapshot s = MAP.get(key);
        if (s == null) {
            if (BuildConfig.DEBUG) Log.d(PERF_TAG, "cache MISS " + apk.getName());
            return null;
        }
        if (System.currentTimeMillis() - s.storedAt > MAX_AGE_MS) {
            MAP.remove(key);
            return null;
        }
        if (BuildConfig.DEBUG) Log.d(PERF_TAG, "cache HIT " + apk.getName());
        return s;
    }

    /**
     * Stores a snapshot only if the file identity has not changed since the key
     * was captured. Incomplete results are never cached.
     */
    public static synchronized void put(File apk, String key, Snapshot snapshot) {
        if (apk == null || key == null || snapshot == null || !snapshot.complete) return;
        if (!key.equals(keyOf(apk))) return;
        snapshot.storedAt = System.currentTimeMillis();
        MAP.put(key, snapshot);
        if (BuildConfig.DEBUG) Log.d(PERF_TAG, "cache PUT " + apk.getName());
    }

    static String identityForTest(File apk) {
        return keyOf(apk);
    }

    static synchronized int sizeForTest() {
        return MAP.size();
    }

    static synchronized void clearForTest() {
        MAP.clear();
    }
}
