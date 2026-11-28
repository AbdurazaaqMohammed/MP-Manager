package io.github.abdurazaaqmohammed.ui.main;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.data.prefs.UiPrefs;
import io.github.abdurazaaqmohammed.plugins.themes.LocalThemes;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;
import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;

/**
 * Theme-correctness behavior of {@link FileIconLoader} row icons, on device:
 * ordinary content thumbnails still load without a pack (and metadata-only
 * binds never load content); a pack that explicitly configures art for a
 * type ("image") keeps owning the row (no content-load override); and the
 * theme-derived entries in the global icon cache (magic-byte probe path) are
 * re-resolved after any theme state change instead of serving stale epoch/dark
 * artwork, while same-theme rebinds still hit the cache.
 *
 * <p>Fixtures live under a folder the activity never lists (locatePath pins
 * the pane to an empty root), so only the test's own loaders bind them.
 * Non-root mode keeps every path free of backend shells. Render
 * discriminators mirror FileIconArt/FileIconSpec internals: flat =
 * {@link LayerDrawable} whose background GradientDrawable carries the spec
 * tile color; 3D = BitmapDrawable holding the FileIconArt cache bitmap
 * (identical instance for the same kind + theme state); pack override =
 * BitmapDrawable holding exactly {@link FileIconSpec#packBitmap}.
 */
public class FileIconThemeCorrectnessTest {
    private static final long ASYNC_TIMEOUT_MS = 15000;
    /** FileIconSpec.DATABASE flat tile background; FILE's tile is 0xFF424242. */
    private static final int DATABASE_TILE_COLOR = 0xFF00695C;
    /** FileIconSpec.IMAGE flat tile background. */
    private static final int IMAGE_TILE_COLOR = 0xFF6A1B9A;

    private final android.app.Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private Application app;
    private MainActivity main;
    private SharedPreferences preferences;
    private final Map<String, Object> original = new HashMap<>();
    private File rootDir;
    private File fixturesDir;
    private File pngFile;
    private File magicFile;
    private String activePackId;

    @Before public void setUp() throws Exception {
        app = (Application) instrumentation.getTargetContext().getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(app);
        for (String key : new String[]{"working_mode", "su_command", "startup_request_root",
                "root_file_ops", "shizuku_file_ops", UiPrefs.USE_3D_ICONS_KEY}) {
            original.put(key, preferences.getAll().get(key));
        }
        // Non-root mode: no backend shell can interfere and no su prompt can
        // steal the foreground during startup.
        preferences.edit().putString("working_mode", "non_root")
                .putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false).putBoolean("root_file_ops", false)
                .putBoolean("shizuku_file_ops", false)
                .putBoolean(UiPrefs.USE_3D_ICONS_KEY, false).commit();
        // Prevent the app's all-files-access Settings screen from obscuring the
        // activity between tests. This affects only the isolated test package.
        String permissionCommand = Build.VERSION.SDK_INT >= 30
                ? "appops set " + app.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow"
                : "pm grant " + app.getPackageName() + " android.permission.WRITE_EXTERNAL_STORAGE";
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(permissionCommand))) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) != -1) { /* wait for the shell command */ }
        }

        // Normalize the theme state the shared icon set and the row-icon cache
        // are keyed on: no pack active, 3D artwork off.
        ThemePacks.deactivate(app);
        instrumentation.runOnMainSync(() -> ThemeRegistry.reload(app));

        rootDir = new File(app.getCacheDir(), "icon-theme-root-" + System.nanoTime());
        assertTrue(rootDir.mkdirs());
        // The activity lists the empty root only; fixtures below are never
        // bound by the app's own loaders.
        fixturesDir = new File(rootDir, "fixtures");
        assertTrue(fixturesDir.mkdirs());
        pngFile = writePng(new File(fixturesDir, "fixture-image.png"), 0xFF6750A4, 32);
        magicFile = writeSQLiteMagic(new File(fixturesDir, "mystery-" + System.nanoTime() + ".tmp"));

        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        app.startActivity(new Intent(app, MainActivity.class)
                .putExtra("locatePath", rootDir.getAbsolutePath())
                // CLEAR_TASK guarantees a fresh onCreate (see the monitor
                // contract above): a reused singleTask instance would only
                // fire onNewIntent and the monitor would time out.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main = (MainActivity) instrumentation.waitForMonitorWithTimeout(monitor, 120000);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Main activity must finish launching", main);
        instrumentation.waitForIdleSync();
    }

    @After public void tearDown() {
        if (main != null) instrumentation.runOnMainSync(() -> main.finish());
        if (activePackId != null) {
            LocalThemes.delete(app, activePackId);
            activePackId = null;
        }
        ThemePacks.deactivate(app);
        instrumentation.runOnMainSync(() -> ThemeRegistry.reload(app));
        SharedPreferences.Editor restore = preferences.edit();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value == null) restore.remove(entry.getKey());
            else if (value instanceof Boolean) restore.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) restore.putInt(entry.getKey(), (Integer) value);
            else restore.putString(entry.getKey(), (String) value);
        }
        restore.commit();
        deleteRecursive(rootDir);
    }

    // ---------- normal thumbnail behavior ----------

    @Test public void noPack_imageRowsStillReceiveRealThumbnails() throws Exception {
        FileIconLoader loader = newLoader();
        ImageView icon = bindFile(loader, pngFile);
        Drawable thumbnail = awaitIcon(icon, FileIconThemeCorrectnessTest::isBitmapDrawable,
                "image row never received its decoded thumbnail");
        assertNotNull(thumbnail);
        assertTrue("expected a decoded thumbnail, not the static type tile",
                isBitmapDrawable(thumbnail));
    }

    @Test public void metadataResolve_keepsStaticTypeArt_neverLoadsContent() throws Exception {
        FileIconLoader loader = newLoader();
        ImageView[] holder = new ImageView[1];
        instrumentation.runOnMainSync(() -> {
            ImageView icon = new ImageView(main);
            loader.setupMetadataView(pngFile.getName(), false, pngFile.length(),
                    pngFile.lastModified(), false, icon, new TextView(main));
            holder[0] = icon;
        });
        // Grace period long enough that an (incorrectly scheduled) content
        // thumbnail of the small fixture would have landed.
        Thread.sleep(1000);
        Drawable icon = currentDrawable(holder[0]);
        assertTrue("metadata-only resolve must keep the static image tile",
                icon instanceof LayerDrawable);
        assertTrue("static image tile expected (color " + Integer.toHexString(flatTileColor(icon)) + ")",
                flatTileColor(icon) == IMAGE_TILE_COLOR);
    }

    // ---------- explicit pack art wins over content loading ----------

    @Test public void packConfiguredImageArt_wins_overContentThumbnail() throws Exception {
        // Stage explicit custom art for the "image" type in a local pack,
        // then activate it. Pack changes bump the theme epoch, so a loader
        // created afterwards refreshes the shared icons and the pack keys.
        LocalThemes.Draft draft = LocalThemes.newDraft("IconLoaderArt", false);
        File packPng = writePng(new File(fixturesDir, "pack-image.png"), Color.MAGENTA, 16);
        assertTrue("staging the image icon must succeed", LocalThemes.stageIcon(app, draft,
                "image", Uri.fromFile(packPng)));
        activePackId = LocalThemes.saveAndApply(app, draft);
        assertNotNull(activePackId);
        instrumentation.runOnMainSync(() -> ThemeRegistry.reload(app));

        Bitmap[] packHolder = new Bitmap[1];
        instrumentation.runOnMainSync(() -> packHolder[0] = FileIconSpec.packBitmap(main, "image"));
        Bitmap packBitmap = packHolder[0];
        assertNotNull("active pack must expose the staged image art", packBitmap);

        FileIconLoader loader = newLoader();
        ImageView icon = bindFile(loader, pngFile);
        Drawable bound = currentDrawable(icon);
        assertTrue("configured pack art renders as a BitmapDrawable",
                isBitmapDrawable(bound));
        assertSame("row must show the configured pack art", packBitmap,
                ((BitmapDrawable) bound).getBitmap());

        // The real media thumbnail must never take the row over: sample for
        // ~2s, long past the decode time of the tiny fixture.
        for (int i = 0; i < 8; i++) {
            Thread.sleep(250);
            Drawable current = currentDrawable(icon);
            assertTrue("content thumbnail overrode configured pack art",
                    isBitmapDrawable(current));
            assertSame("content thumbnail overrode configured pack art", packBitmap,
                    ((BitmapDrawable) current).getBitmap());
        }
    }

    // ---------- stale theme artifacts in the global icon cache ----------

    @Test public void magicProbeIcons_reresolveAfterThemeStateChange_stillCacheWithinOneTheme() throws Exception {
        // Phase 1: extension-less SQLite content resolves through the
        // magic-byte probe to the flat database tile, which also lands in the
        // global icon cache.
        FileIconLoader loader1 = newLoader();
        ImageView icon1 = bindFile(loader1, magicFile);
        Drawable probedFlat = awaitIcon(icon1,
                d -> flatTileColor(d) == DATABASE_TILE_COLOR,
                "magic probe never resolved the database tile");

        // Phase 2: a theme state change (the 3D toggle here) must invalidate
        // the theme-derived cache entry: a rebind re-resolves and lands on
        // fresh 3D database art, never the stale pre-change artifact.
        UiPrefs.setUse3dIcons(app, true);
        Bitmap[] expectedHolder = new Bitmap[1];
        instrumentation.runOnMainSync(() -> {
            Drawable expected = FileIconSpec.DATABASE.drawable(main);
            assertTrue("3D database art must render as a BitmapDrawable",
                    expected instanceof BitmapDrawable);
            expectedHolder[0] = ((BitmapDrawable) expected).getBitmap();
        });
        Bitmap expectedDb = expectedHolder[0];
        assertNotNull(expectedDb);

        FileIconLoader loader2 = newLoader();
        ImageView icon2 = bindFile(loader2, magicFile);
        Drawable fresh = awaitIcon(icon2,
                d -> isBitmapDrawable(d)
                        && ((BitmapDrawable) d).getBitmap() == expectedDb,
                "stale theme artifact served after theme change (expected fresh 3D database art)");
        assertNotSame("stale flat artifact must not survive the theme change",
                probedFlat, fresh);

        // Phase 3: within the same theme state the cache still serves the
        // fresh entry on rebind (no over-invalidation).
        FileIconLoader loader3 = newLoader();
        ImageView icon3 = bindFile(loader3, magicFile);
        assertSame("same-theme rebind must hit the still-valid cache entry",
                fresh, currentDrawable(icon3));
    }

    // ---------- helpers ----------

    private static boolean isBitmapDrawable(Drawable drawable) {
        return drawable instanceof BitmapDrawable;
    }

    /** Background tile color of a flat spec rendering; 0 for anything else. */
    private static int flatTileColor(Drawable drawable) {
        if (!(drawable instanceof LayerDrawable)) return 0;
        Drawable background = ((LayerDrawable) drawable).getDrawable(0);
        if (!(background instanceof GradientDrawable)) return 0;
        try {
            android.content.res.ColorStateList color =
                    ((GradientDrawable) background).getColor();
            return color == null ? 0 : color.getDefaultColor();
        } catch (Exception e) {
            return 0;
        }
    }

    /** One loader serves one listing; construct it on the UI thread like the adapters do. */
    private FileIconLoader newLoader() {
        FileIconLoader[] holder = new FileIconLoader[1];
        instrumentation.runOnMainSync(() -> holder[0] = new FileIconLoader(main, false));
        return holder[0];
    }

    private ImageView bindFile(FileIconLoader loader, File target) {
        ImageView[] holder = new ImageView[1];
        instrumentation.runOnMainSync(() -> {
            ImageView icon = new ImageView(main);
            loader.setupFileView(target, icon, new TextView(main));
            holder[0] = icon;
        });
        return holder[0];
    }

    private Drawable currentDrawable(ImageView icon) {
        Drawable[] holder = new Drawable[1];
        instrumentation.runOnMainSync(() -> holder[0] = icon.getDrawable());
        return holder[0];
    }

    private interface IconCheck { boolean matches(Drawable drawable); }

    /** Polls the row icon (on the main thread) until {@code check} holds. */
    private Drawable awaitIcon(ImageView icon, IconCheck check, String timeoutMessage)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MS;
        Drawable current = currentDrawable(icon);
        while (!check.matches(current) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            current = currentDrawable(icon);
        }
        assertTrue(timeoutMessage + " (last icon: " + describe(current) + ")",
                check.matches(current));
        return current;
    }

    private static String describe(Drawable drawable) {
        if (drawable == null) return "null";
        String detail = isBitmapDrawable(drawable)
                ? "@bitmap" + Integer.toHexString(System.identityHashCode(
                        ((BitmapDrawable) drawable).getBitmap()))
                : "@color" + Integer.toHexString(flatTileColor(drawable));
        return drawable.getClass().getSimpleName() + detail;
    }

    private static byte[] pngBytes(int color, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private static File writePng(File target, int color, int size) throws Exception {
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(pngBytes(color, size));
        }
        return target;
    }

    /** Extension-less content whose header ("SQLite format 3\\0") resolves through the magic-byte probe to the database art. */
    private static File writeSQLiteMagic(File target) throws Exception {
        byte[] header = "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(header);
            out.write(new byte[32]);
        }
        return target;
    }

    private static void deleteRecursive(File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
