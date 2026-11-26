package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import io.github.abdurazaaqmohammed.MPManager.BuildConfig;
import io.github.abdurazaaqmohammed.feature.apk.flutter.FlutterDetector;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.util.archive.ZipCompat;

import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Background metadata extraction for the APK info dialog.
 *
 * <p>Package/version/SDK/label/icon are published as soon as the package parser
 * finishes. The archive scan (entry count + native/il2cpp/flutter checks) then
 * publishes its own rows from one central-directory read, while signature
 * verification and packer detection run independently on a shared bounded pool.
 * No ZIP I/O ever runs on the UI thread.
 */
public final class ApkInfoLoader {

    public interface Listener {
        /** Package-derived rows; published before any archive I/O. */
        void onBasic(Basic basic);
        /** Entry count, from the single combined archive scan. */
        void onEntries(int entryCount);
        void onSignature(String label, boolean analyzed, String certSha256, boolean clickable);
        void onProtection(boolean protectedApk, String summary);
        void onNative(boolean il2cpp, boolean nativeLibs, boolean flutter);
        void onInvalid();
    }

    /** Cheap first-pass data; parsed values only, no Views/context retained. */
    public static final class Basic {
        public String label = "";
        public String versionName = "";
        public long versionCode;
        public String packageName = "";
        public int targetSdk;
        public int minSdk = -1;
        public Drawable icon;
        public boolean fromCache;
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "apk-info-loader");
                t.setDaemon(true);
                return t;
            });

    private final Context context;
    private final File apk;
    private final String path;
    /** Identity captured before any work; all publishes/puts re-check this key. */
    private final String cacheKey;
    private final Listener listener;
    private volatile boolean cancelled;

    public ApkInfoLoader(Context context, File apk, Listener listener) {
        this.context = context.getApplicationContext();
        this.apk = apk;
        this.path = apk.getPath();
        this.cacheKey = ApkInfoCache.keyOf(apk);
        this.listener = listener;
    }

    public void cancel() {
        cancelled = true;
    }

    private boolean live() {
        return !cancelled && cacheKey != null && cacheKey.equals(ApkInfoCache.keyOf(apk));
    }

    public void start() {
        POOL.execute(() -> {
            if (!live()) return;
            ApkInfoCache.Snapshot cached = ApkInfoCache.get(apk, cacheKey);
            if (cached != null && cached.complete) {
                publishCached(cached);
                return;
            }
            runCold();
        });
    }

    private void publishCached(ApkInfoCache.Snapshot s) {
        long t = SystemClock.elapsedRealtime();
        if (!live()) return;
        Basic basic = new Basic();
        basic.fromCache = true;
        basic.label = s.label;
        basic.versionName = s.versionName;
        basic.versionCode = s.versionCode;
        basic.packageName = s.packageName;
        basic.targetSdk = s.targetSdk;
        basic.minSdk = s.minSdk;
        // Icons are never cached: always reload from the APK on the background
        // thread so a replaced APK never shows a stale generic placeholder.
        basic.icon = loadIcon();
        if (!live()) return;
        listener.onBasic(basic);
        if (!live()) return;
        listener.onEntries(s.entryCount);
        if (!live()) return;
        listener.onSignature(s.signatureLabel, s.signatureAnalyzed, s.certSha256, s.signatureAnalyzed);
        if (!live()) return;
        listener.onProtection(s.protectedApk, s.protectionSummary);
        if (!live()) return;
        listener.onNative(s.hasIl2Cpp, s.hasNativeLibs, s.isFlutter);
        debug("cached publish", t);
    }

    /** Warm path: parse once to reload an icon that is never cached. */
    private Drawable loadIcon() {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(path, 0);
            if (pi == null || pi.applicationInfo == null) return null;
            return loadIcon(pi.applicationInfo, pm);
        } catch (Exception e) {
            Log.d(ApkInfoCache.PERF_TAG, "icon parse failed: " + e.getMessage());
            return null;
        }
    }

    /** Cold path reuses the already parsed ApplicationInfo — no second parse. */
    private Drawable loadIcon(ApplicationInfo ai, PackageManager pm) {
        try {
            if (TextUtils.isEmpty(ai.sourceDir)) ai.sourceDir = path;
            if (TextUtils.isEmpty(ai.publicSourceDir)) ai.publicSourceDir = path;
            return ai.loadIcon(pm);
        } catch (Exception e) {
            Log.d(ApkInfoCache.PERF_TAG, "icon load failed: " + e.getMessage());
            return null;
        }
    }

    private void runCold() {
        long started = SystemClock.elapsedRealtime();
        Basic basic = new Basic();
        long t = SystemClock.elapsedRealtime();
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo;
        try {
            // Flags 0 is sufficient for label/icon/version/package/SDK rows.
            packageInfo = pm.getPackageArchiveInfo(path, 0);
            if (packageInfo == null || packageInfo.applicationInfo == null) {
                if (live()) listener.onInvalid();
                return;
            }
            debug("package parse", t);
        } catch (Exception e) {
            Log.w(ApkInfoCache.PERF_TAG, "package parse failed", e);
            if (live()) listener.onInvalid();
            return;
        }
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        if (TextUtils.isEmpty(appInfo.sourceDir)) appInfo.sourceDir = path;
        if (TextUtils.isEmpty(appInfo.publicSourceDir)) appInfo.publicSourceDir = path;
        basic.packageName = packageInfo.packageName == null ? "" : packageInfo.packageName;
        basic.versionName = packageInfo.versionName == null ? "" : packageInfo.versionName;
        basic.versionCode = ApkInfoUtil.getVersionCode(packageInfo);
        basic.targetSdk = appInfo.targetSdkVersion;
        basic.minSdk = ApkInfoUtil.getMinSdk(packageInfo);

        t = SystemClock.elapsedRealtime();
        try {
            basic.label = String.valueOf(appInfo.loadLabel(pm));
        } catch (Exception e) {
            Log.d(ApkInfoCache.PERF_TAG, "label load failed: " + e.getMessage());
            basic.label = basic.packageName;
        }
        debug("label", t);
        t = SystemClock.elapsedRealtime();
        basic.icon = loadIcon(appInfo, pm);
        debug("icon", t);
        if (!live()) return;
        // Publish all cheap fields immediately; entry counting must not delay them.
        listener.onBasic(basic);
        debug("cold total (basic)", started);

        ApkInfoCache.Snapshot snapshot = new ApkInfoCache.Snapshot();
        snapshot.packageName = basic.packageName;
        snapshot.label = basic.label;
        snapshot.versionName = basic.versionName;
        snapshot.versionCode = basic.versionCode;
        snapshot.targetSdk = basic.targetSdk;
        snapshot.minSdk = basic.minSdk;
        // No partial snapshot reuse: get() only returns complete snapshots,
        // which are published in full by publishCached() above.

        AtomicInteger remaining = new AtomicInteger(3);
        Runnable commit = () -> {
            if (remaining.decrementAndGet() == 0 && live()) {
                // Only cache when every extractor actually succeeded, so a
                // failed extractor is retried on the next open.
                snapshot.complete = snapshot.signatureAnalyzed
                        && snapshot.protectionAnalyzed && snapshot.nativeAnalyzed;
                ApkInfoCache.put(apk, cacheKey, snapshot);
            }
        };
        // Full verification is intentionally kept: the row's meaning is a
        // verified-scheme list, which a structural-only summary cannot claim.
        submit(() -> {
            long t0 = SystemClock.elapsedRealtime();
            try {
                ApkSignatureInfo sigInfo = ApkSignatureInfo.analyze(apk);
                if (live()) {
                    String label = sigInfo == null ? "Unknown" : sigInfo.getStatusLabel();
                    String fp = "";
                    if (sigInfo != null) {
                        X509Certificate primary = sigInfo.getPrimaryCertificate();
                        if (primary != null) fp = CertUtil.getSha256(primary);
                    }
                    boolean ok = sigInfo != null;
                    snapshot.signatureLabel = label;
                    snapshot.certSha256 = fp;
                    snapshot.signatureAnalyzed = ok;
                    listener.onSignature(label, ok, fp, ok);
                }
                debug("signature", t0);
            } catch (Exception e) {
                Log.w(ApkInfoCache.PERF_TAG, "signature extraction failed", e);
                if (live()) listener.onSignature("Unknown", false, "", false);
            } finally {
                commit.run();
            }
        });
        // Packer/protection scan.
        submit(() -> {
            long t0 = SystemClock.elapsedRealtime();
            try {
                ApkProtectionDetector.Result result = ApkProtectionDetector.detect(apk);
                if (live()) {
                    boolean p = result.isProtected;
                    String summary = p ? result.getSummary() : null;
                    snapshot.protectedApk = p;
                    snapshot.protectionSummary = summary;
                    snapshot.protectionAnalyzed = true;
                    listener.onProtection(p, summary);
                }
                debug("protection", t0);
            } catch (Exception e) {
                Log.w(ApkInfoCache.PERF_TAG, "protection extraction failed", e);
                // Do not mark as successfully analyzed; do not cache a false negative.
                if (live()) listener.onProtection(false, null);
            } finally {
                commit.run();
            }
        });
        // One archive parse for entry count + all native/flutter checks.
        submit(() -> {
            long t0 = SystemClock.elapsedRealtime();
            ArchiveFacts facts = null;
            try {
                try {
                    facts = scanArchive(apk);
                    debug("archive scan", t0);
                } catch (Exception e) {
                    Log.w(ApkInfoCache.PERF_TAG, "archive scan failed", e);
                }
                if (!live()) return;
                int count = facts == null ? 0 : facts.entryCount;
                boolean il2cpp = facts != null && facts.il2cpp;
                boolean libs = facts != null && facts.nativeLibs;
                boolean flutter = facts != null && facts.flutter;
                // Always publish, even on failure, so no row is left "Loading…".
                listener.onEntries(count);
                if (!live()) return;
                listener.onNative(il2cpp, libs, flutter);
                if (facts != null) {
                    snapshot.entryCount = count;
                    snapshot.hasIl2Cpp = il2cpp;
                    snapshot.hasNativeLibs = libs;
                    snapshot.isFlutter = flutter;
                    snapshot.nativeAnalyzed = true;
                }
            } finally {
                commit.run();
            }
        });
    }

    private void submit(Runnable task) {
        POOL.execute(() -> {
            if (live()) task.run();
        });
    }

    private static void debug(String what, long startRealtime) {
        if (BuildConfig.DEBUG) {
            Log.d(ApkInfoCache.PERF_TAG, what + " " + (SystemClock.elapsedRealtime() - startRealtime) + "ms");
        }
    }

    /** Results of a single central-directory pass, using existing detector rules. */
    static final class ArchiveFacts {
        int entryCount;
        boolean il2cpp;
        boolean nativeLibs;
        boolean flutter;
    }

    /**
     * Reads the central directory once and derives every archive-based row.
     *
     * <p>The common APK path uses {@link java.util.zip.ZipFile}, which reads
     * headers only and avoids zip4j's heavier per-header model. Archives that
     * java.util.zip cannot read (split parts) or whose names decode with
     * replacement characters fall back to {@link ZipCompat#listHeaders(File)},
     * preserving the charset/split behaviour of FlutterDetector.
     */
    static ArchiveFacts scanArchive(File apk) throws IOException {
        ArchiveFacts facts = new ArchiveFacts();
        boolean badNames = false;
        boolean flutterPossible = apk.length() >= 1024;
        try (ZipFile zf = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                facts.entryCount++;
                if (name == null) {
                    badNames = true;
                    continue;
                }
                if (name.indexOf('\uFFFD') >= 0) badNames = true;
                boolean directory = e.isDirectory();
                if (!directory && name.startsWith("lib/") && name.endsWith(".so")) {
                    facts.nativeLibs = true;
                    if (name.endsWith("/libil2cpp.so")) facts.il2cpp = true;
                }
                if (flutterPossible
                        && (name.contains(FlutterDetector.LIB_FLUTTER)
                        || name.contains(FlutterDetector.LIB_APP)
                        || name.startsWith(FlutterDetector.FLUTTER_ASSETS_DIR))) {
                    facts.flutter = true;
                }
            }
        } catch (IOException | RuntimeException e) {
            return scanArchiveCompat(apk);
        }
        if (badNames) return scanArchiveCompat(apk);
        return facts;
    }

    /** Fallback for split/legacy-charset archives: one ZipCompat header pass. */
    private static ArchiveFacts scanArchiveCompat(File apk) throws IOException {
        ArchiveFacts facts = new ArchiveFacts();
        List<net.lingala.zip4j.model.FileHeader> headers = ZipCompat.listHeaders(apk);
        facts.entryCount = headers.size();
        for (net.lingala.zip4j.model.FileHeader h : headers) {
            String name = h.getFileName();
            if (name == null) continue;
            boolean directory = h.isDirectory();
            if (!directory && name.startsWith("lib/") && name.endsWith(".so")) {
                facts.nativeLibs = true;
                if (name.endsWith("/libil2cpp.so")) facts.il2cpp = true;
            }
            if (apk.length() >= 1024
                    && (name.contains(FlutterDetector.LIB_FLUTTER)
                    || name.contains(FlutterDetector.LIB_APP)
                    || name.startsWith(FlutterDetector.FLUTTER_ASSETS_DIR))) {
                facts.flutter = true;
            }
        }
        return facts;
    }
}
