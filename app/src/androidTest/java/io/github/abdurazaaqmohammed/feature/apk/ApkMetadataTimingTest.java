package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device-side validation for the staged APK info loader on the isolated
 * editorregression package. Verifies cold load publishes basic fields first,
 * that every heavy stage finishes, that results come from cache on a second
 * open, and that rewriting the file triggers a fresh load with correct identity.
 * Also records per-field timing via logcat tag {@code ApkInfoPerf}.
 *
 * <pre>
 * ./gradlew :app:connectedFullDebugAndroidTest -PeditorRegressionBuild \
 *   -Pandroid.testInstrumentationRunnerArguments.class=\
 * io.github.abdurazaaqmohammed.feature.apk.ApkMetadataTimingTest
 * </pre>
 */
public class ApkMetadataTimingTest {
    private static final String TAG = "ApkMetadataTiming";

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private File dir;
    private File fixture;

    @Before public void prepare() throws Exception {
        context = instrumentation.getTargetContext();
        dir = new File(context.getCacheDir(), "apk-metadata-timing");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        fixture = new File(dir, "fixture.apk");
        copyAsset("aapt2-fixture.apk", fixture);
        ApkInfoCache.clearForTest();
    }

    @After public void cleanup() {
        ApkInfoCache.clearForTest();
        deleteRecursively(dir);
    }

    private void copyAsset(String name, File out) throws Exception {
        try (InputStream in = instrumentation.getContext().getAssets().open(name);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Records callback order and monotonic latency per callback; latches count down on background threads. */
    private static final class Recording implements ApkInfoLoader.Listener {
        // basic + entries + native + signature + protection = 5 callbacks per
        // successful load. basic arrives first; entries/native/signature/
        // protection may publish in any order after it.
        static final int EXPECTED_CALLBACKS = 5;
        final CountDownLatch callbacks = new CountDownLatch(EXPECTED_CALLBACKS);
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        /** Monotonic latency (ns since load start) for each stage, in callback order. */
        final List<Long> stageNanos = Collections.synchronizedList(new ArrayList<>());
        volatile ApkInfoLoader.Basic basic;
        volatile int entryCount = -1;
        volatile String signature;
        volatile Boolean protectedApk;
        volatile String nativeFlags;
        volatile boolean invalid;
        volatile long firstCallbackNanos = -1;
        volatile long lastCallbackNanos = -1;
        long startNanos;

        void setStartNanos(long startNanos) {
            this.startNanos = startNanos;
        }

        private void mark(String name) {
            long since = System.nanoTime() - startNanos;
            if (firstCallbackNanos < 0) firstCallbackNanos = since;
            lastCallbackNanos = since;
            events.add(name);
            stageNanos.add(since);
        }

        @Override public void onBasic(ApkInfoLoader.Basic b) {
            basic = b;
            mark("basic(fromCache=" + b.fromCache + ")");
            callbacks.countDown();
        }
        @Override public void onEntries(int count) {
            entryCount = count;
            mark("entries=" + count);
            callbacks.countDown();
        }
        @Override public void onSignature(String label, boolean analyzed, String fp, boolean clickable) {
            signature = label + "/" + analyzed + "/" + fp;
            mark("signature=" + label);
            callbacks.countDown();
        }
        @Override public void onProtection(boolean p, String summary) {
            protectedApk = p;
            mark("protection=" + p);
            callbacks.countDown();
        }
        @Override public void onNative(boolean il2cpp, boolean nativeLibs, boolean flutter) {
            nativeFlags = il2cpp + "/" + nativeLibs + "/" + flutter;
            mark("native=" + nativeFlags);
            callbacks.countDown();
        }
        @Override public void onInvalid() {
            invalid = true;
            mark("invalid");
            while (callbacks.getCount() > 0) callbacks.countDown();
        }

        boolean await(long seconds) throws InterruptedException {
            return callbacks.await(seconds, TimeUnit.SECONDS);
        }
    }

    private Recording loadOnce(File apk, String mode) throws Exception {
        Recording r = new Recording();
        long t0 = System.nanoTime();
        r.setStartNanos(t0);
        ApkInfoLoader loader = new ApkInfoLoader(context, apk, r);
        loader.start();
        assertTrue("callbacks did not finish in 90s: " + r.events, r.await(90));
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;
        StringBuilder stages = new StringBuilder();
        synchronized (r.events) {
            for (int i = 0; i < r.events.size(); i++) {
                if (i > 0) stages.append(" | ");
                stages.append(r.events.get(i)).append('@')
                        .append(r.stageNanos.get(i) / 1_000_000L).append("ms");
            }
        }
        Log.w(TAG, String.format(
                "load %s [%s] size=%dKB wall=%dms firstCallbackLatency=%dms callbackSpan=%dms stages=%s",
                apk.getName(), mode, apk.length() / 1024, wallMs,
                r.firstCallbackNanos / 1_000_000L,
                (r.lastCallbackNanos - r.firstCallbackNanos) / 1_000_000L,
                stages));
        return r;
    }

    private void waitForCached(File apk) throws InterruptedException {
        // Cache commit happens after the final heavy callback; wait briefly.
        // Use the same key the loader computed so the lookup matches.
        String key = ApkInfoCache.keyOf(apk);
        for (int i = 0; i < 40; i++) {
            if (key != null && ApkInfoCache.get(apk, key) != null) return;
            Thread.sleep(50);
        }
    }

    @Test public void coldWarmAndReplacedFileStages() throws Exception {
        // No pre-read of the fixture. The file was just copied, so OS page cache
        // is already warm; we only distinguish METADATA-CACHE EMPTY vs HIT.

        // METADATA-CACHE EMPTY: first staged load of this fixture.
        Recording cold = loadOnce(fixture, "small/metadata-cache-empty");
        assertTrue("cold should not be invalid", !cold.invalid);
        assertNotNull("basic callback", cold.basic);
        assertEquals("basic must arrive first",
                "basic(fromCache=false)", cold.events.get(0));
        assertNotNull("signature callback", cold.signature);
        assertNotNull("protection callback", cold.protectedApk);
        assertNotNull("native callback", cold.nativeFlags);
        assertTrue("entries callback must publish a count", cold.entryCount > 0);
        assertTrue("cold basic should supply a label",
                cold.basic.label != null && !cold.basic.label.isEmpty());

        // Cache commit occurs after the final callback.
        waitForCached(fixture);
        assertNotNull("cold result should be cached",
                ApkInfoCache.get(fixture, ApkInfoCache.keyOf(fixture)));

        // METADATA-CACHE HIT: unchanged file, second open.
        Recording warm = loadOnce(fixture, "small/metadata-cache-hit");
        assertEquals("warm basic must be from cache",
                "basic(fromCache=true)", warm.events.get(0));
        assertEquals("cache must preserve identity", cold.basic.packageName, warm.basic.packageName);
        assertEquals("cache must preserve label", cold.basic.label, warm.basic.label);
        assertEquals("cache must preserve entry count", cold.entryCount, warm.entryCount);
        // Icon Drawables are deliberately NOT cached; each open must load a
        // fresh instance so the cached path still renders correctly.
        assertNotNull("warm basic must still carry label", warm.basic.label);

        // Valid replacement: identical APK bytes but a new mtime (cache key
        // changes), so the loader must reload from disk and produce the same
        // package identity. Do NOT append bytes after EOCD: Android's ZIP
        // parser requires the end-of-central-directory record at EOF.
        assertTrue(fixture.setLastModified(System.currentTimeMillis() + 10_000L));
        // METADATA-CACHE EMPTY with new key: must reload from disk.
        Recording replaced = loadOnce(fixture, "small/metadata-cache-empty/replaced");
        assertEquals("replaced file must reload from disk",
                "basic(fromCache=false)", replaced.events.get(0));
        assertEquals("identity must survive replacement",
                cold.basic.packageName, replaced.basic.packageName);
        assertEquals("entry count must survive replacement", cold.entryCount, replaced.entryCount);
        assertNotNull("replaced signature callback", replaced.signature);
        assertNotNull("replaced native callback", replaced.nativeFlags);

        // Baseline primitive cost runs LAST so it cannot warm any timed load.
        baselineArchiveInfo(fixture);
    }

    /**
     * Realistic large-fixture measurement using the app's own installed APK:
     * METADATA-CACHE EMPTY then HIT. The APK is already on the file system, so
     * numbers reflect the expensive signature/native/entry extractors rather
     * than a first read of the file.
     */
    @Test public void largeOwnApkColdAndWarm() throws Exception {
        File own = new File(context.getApplicationInfo().sourceDir);
        assertTrue("own APK should exist: " + own, own.isFile());
        ApkInfoCache.clearForTest();

        Recording cold = loadOnce(own, "own/metadata-cache-empty");
        assertTrue("own APK should parse", !cold.invalid);
        assertNotNull("basic callback", cold.basic);
        assertEquals("basic must arrive first",
                "basic(fromCache=false)", cold.events.get(0));
        assertNotNull("signature callback", cold.signature);
        assertNotNull("native callback", cold.nativeFlags);
        assertTrue("own APK entries callback must publish a count", cold.entryCount > 0);

        waitForCached(own);
        assertNotNull("own APK cold result should be cached",
                ApkInfoCache.get(own, ApkInfoCache.keyOf(own)));

        Recording warm = loadOnce(own, "own/metadata-cache-hit");
        assertEquals("warm basic must be from cache",
                "basic(fromCache=true)", warm.events.get(0));
        assertEquals("cache must preserve identity", cold.basic.packageName, warm.basic.packageName);
        assertEquals("cache must preserve entry count", cold.entryCount, warm.entryCount);

        // Baseline primitive cost runs LAST so it cannot warm any timed load.
        baselineArchiveInfo(own);
    }

    @Test public void cancelBeforeStartSuppressesCallbacks() throws Exception {
        Recording r = new Recording();
        ApkInfoLoader loader = new ApkInfoLoader(context, fixture, r);
        r.setStartNanos(System.nanoTime());
        loader.cancel();
        loader.start();
        Thread.sleep(500);
        assertEquals("cancelled loader must not publish", 0, r.events.size());
    }

    /**
     * Controlled sequential baseline reproducing the pre-staging extraction
     * order on a fresh copy of the app's own APK, for comparison against the
     * staged {@link #largeOwnApkColdAndWarm()} numbers.
     *
     * <p>Uses a NEW file path on every run: {@code ApkSignatureInfo.analyze()}
     * keeps a static path:size:mtime cache that {@code ApkInfoCache.clearForTest()}
     * does NOT clear, so a reused path would contaminate signature timing with
     * a cache hit. The fresh copy is byte-identical to the installed APK (same
     * APK contents) and is copied immediately before the timed chain, so the
     * page cache is warm for both this baseline and the staged run. Numbers
     * therefore reflect extractor cost on a warm filesystem, NOT cold disk I/O.
     *
     * <p>Limitations: the sequential chain runs on a single instrumentation
     * thread while the staged loader uses a bounded background pool with three
     * heavy extractors in parallel; wall-clock differences conflate extractor
     * cost with concurrency. {@code getPackageArchiveInfo} in the old chain
     * used GET_ACTIVITIES (vs flags 0 in staging); the old native path made
     * three separate ZIP header passes (il2cpp, native libs, flutter) plus a
     * zip4j entry-count pass, whereas staging uses one {@code ZipFile} pass.
     * Old-chain signature verification is also invoked directly here, so its
     * static cache is cold only for this fresh path.
     */
    @Test public void sequentialBaselineOnFreshOwnApkCopy() throws Exception {
        File own = new File(context.getApplicationInfo().sourceDir);
        assertTrue("own APK should exist: " + own, own.isFile());
        // Fresh path guarantees ApkSignatureInfo.analyze() cache miss; byte
        // contents stay identical to the installed APK used by the staged run.
        File baseline = File.createTempFile("apk-metadata-baseline", ".apk", dir);
        copyFile(own, baseline);
        ApkInfoCache.clearForTest();
        assertNull("fresh path must have no signature cache entry",
                io.github.abdurazaaqmohammed.feature.apk.ApkSignatureInfo.getCached(baseline));

        // Old chain, in original sequential order, each stage timed separately.
        long chainStart = System.nanoTime();
        long t = System.nanoTime();
        PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(
                baseline.getPath(), PackageManager.GET_ACTIVITIES);
        long parseMs = elapsedMs(t);
        assertNotNull("old-chain package parse", pi);

        t = System.nanoTime();
        boolean il2cpp = io.github.abdurazaaqmohammed.feature.apk.Il2CppDumperUtil.hasIl2Cpp(baseline);
        long il2cppMs = elapsedMs(t);
        t = System.nanoTime();
        boolean nativeLibs = io.github.abdurazaaqmohammed.feature.apk.NativeLibPatcher.hasNativeLibs(baseline);
        long nativeMs = elapsedMs(t);
        t = System.nanoTime();
        boolean flutter = io.github.abdurazaaqmohammed.feature.apk.flutter.FlutterDetector.isFlutterApk(baseline);
        long flutterMs = elapsedMs(t);

        t = System.nanoTime();
        io.github.abdurazaaqmohammed.feature.apk.ApkSignatureInfo sig =
                io.github.abdurazaaqmohammed.feature.apk.ApkSignatureInfo.analyze(baseline);
        long signatureMs = elapsedMs(t);
        assertNotNull("old-chain signature analysis", sig);

        t = System.nanoTime();
        io.github.abdurazaaqmohammed.feature.apk.ApkProtectionDetector.Result prot =
                io.github.abdurazaaqmohammed.feature.apk.ApkProtectionDetector.detect(baseline);
        long protectionMs = elapsedMs(t);
        assertNotNull("old-chain protection detection", prot);

        t = System.nanoTime();
        int entries = ApkInfoUtil.getEntryCount(baseline);
        long entriesMs = elapsedMs(t);
        long chainMs = (System.nanoTime() - chainStart) / 1_000_000L;

        Log.w(TAG, String.format(
                "baseline SEQUENTIAL fresh-copy %s size=%dKB chainWall=%dms | " +
                        "parse(GET_ACTIVITIES)=%dms | il2cpp=%b/%dms | nativeLibs=%b/%dms | " +
                        "flutter=%b/%dms | signature=%s/%dms | protection=%b/%dms | entries=%d/%dms | " +
                        "nativeTotal=%dms | filesystem=warm-copy (page cache warm, not cold disk)",
                baseline.getName(), baseline.length() / 1024, chainMs,
                parseMs, il2cpp, il2cppMs, nativeLibs, nativeMs, flutter, flutterMs,
                sig.getStatusLabel(), signatureMs, prot.isProtected, protectionMs,
                entries, entriesMs, il2cppMs + nativeMs + flutterMs));
        assertTrue("baseline should find entries", entries > 0);
    }

    private void copyFile(File from, File to) throws Exception {
        try (InputStream in = new java.io.FileInputStream(from);
             OutputStream os = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** Timed framework primitive baseline; called AFTER all staged loads. */
    private void baselineArchiveInfo(File apk) {
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(
                    apk.getPath(), PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            Log.w(TAG, String.format("baseline getPackageArchiveInfo[%d] %s size=%dKB %dms pkg=%s",
                    i, apk.getName(), apk.length() / 1024, ms, pi == null ? "null" : pi.packageName));
            assertNotNull(pi);
        }
    }

    @Test public void missingAndCorruptFilesReportInvalid() throws Exception {
        File missing = new File(dir, "missing.apk");
        Recording r = new Recording();
        r.setStartNanos(System.nanoTime());
        new ApkInfoLoader(context, missing, r).start();
        assertTrue("invalid should be reported quickly: " + r.events, r.await(10));
        assertTrue("expected invalid event for missing file: " + r.events, r.invalid);
        logInvalid("missing", r);

        File corrupt = new File(dir, "corrupt.apk");
        try (FileOutputStream os = new FileOutputStream(corrupt)) {
            os.write(new byte[4096]);
        }
        Recording bad = new Recording();
        bad.setStartNanos(System.nanoTime());
        new ApkInfoLoader(context, corrupt, bad).start();
        assertTrue("corrupt should be reported quickly: " + bad.events, bad.await(10));
        assertTrue("expected invalid event for corrupt file: " + bad.events, bad.invalid);
        logInvalid("corrupt", bad);
    }

    private static void logInvalid(String kind, Recording r) {
        StringBuilder stages = new StringBuilder();
        synchronized (r.events) {
            for (int i = 0; i < r.events.size(); i++) {
                if (i > 0) stages.append(" | ");
                stages.append(r.events.get(i)).append('@')
                        .append(r.stageNanos.get(i) / 1_000_000L).append("ms");
            }
        }
        Log.w(TAG, String.format("invalid %s firstCallbackLatency=%dms stages=%s",
                kind, r.firstCallbackNanos / 1_000_000L, stages));
    }
}
