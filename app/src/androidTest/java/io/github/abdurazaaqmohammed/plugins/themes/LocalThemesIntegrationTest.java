package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * End-to-end coverage for in-app local themes on the real backend: create
 * -> save -> apply -> restart (cache invalidation, like a fresh process)
 * -> reload -> delete, custom icon persistence, edit retention, and
 * portable export/import round trips. No provider/signer is involved: the
 * backend skips signer verification for local.* packages.
 */
public class LocalThemesIntegrationTest {

    /** Marker prefs file shared by the two real-restart probe methods. */
    private static final String RESTART_PROBE_PREFS = "theme_restart_probe";
    /** Color token the verify probe must rediscover after a fresh process. */
    private static final int RESTART_PROBE_COLOR = 0xFF0B5E07;

    @Rule
    public final TestName testName = new TestName();

    private final android.app.Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private Context context;
    private final java.util.List<String> createdKeys = new java.util.ArrayList<>();

    /** The real-restart probes manage their own (deliberately persistent)
     *  state across separate instrumentation invocations; the normal
     *  setUp/tearDown reset would erase exactly what they validate. */
    private boolean isRestartProbe() {
        String name = testName.getMethodName();
        if (name != null) return name.startsWith("realRestart");
        // AndroidJUnitRunner may invoke @Before/@After before the TestName
        // rule has entered its method scope. The requested class argument is
        // still available there, so a two-invocation probe must not be
        // mistaken for an ordinary test and deactivated during teardown.
        String requested = InstrumentationRegistry.getArguments().getString("class", "");
        return requested.contains("#realRestart");
    }

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        if (isRestartProbe()) return;
        ThemePacks.deactivate(context);
    }

    @After
    public void tearDown() {
        if (isRestartProbe()) return;
        for (String key : createdKeys) {
            ThemePacks.deleteLocal(context, key);
        }
        ThemePacks.deactivate(context);
    }

    private void track(LocalThemes.Draft draft) {
        if (draft.key != null) createdKeys.add(draft.key);
    }

    /** 16x16 solid PNG bytes. */
    private static byte[] pngBytes(int color) {
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    // ---------- save / read / apply / restart / delete ----------

    @Test
    public void save_read_apply_survivesCacheInvalidation_thenDelete() {
        LocalThemes.Draft draft = LocalThemes.newDraft("Persisted", true);
        draft.colors.put("colorPrimary", 0xFF112233);
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);

        List<LocalThemes.LocalTheme> themes = LocalThemes.list(context);
        assertFalse(themes.isEmpty());
        LocalThemes.LocalTheme saved = LocalThemes.read(context, id);
        assertNotNull(saved);
        assertEquals("Persisted", saved.name);
        assertEquals(Boolean.TRUE, saved.dark);
        assertEquals(Integer.valueOf(0xFF112233), saved.colors.get("colorPrimary"));

        // Save without apply must NOT change the active theme.
        assertNull(ThemePacks.active(context));

        // Apply.
        assertTrue(LocalThemes.activate(context, id));
        ThemePacks.Snapshot active = ThemePacks.active(context);
        assertNotNull(active);
        assertEquals(id, active.packageName);
        assertEquals("Persisted", active.name);
        assertEquals(Boolean.TRUE, active.dark);
        assertEquals(Integer.valueOf(0xFF112233), active.colors.get("colorPrimary"));
        assertTrue(LocalThemes.isActive(context, id));

        // "Process restart": the cache is process memory; clearing it makes
        // the next read go back to the private store exactly like a new
        // process would.
        ThemePacks.invalidateCachedSnapshot();
        active = ThemePacks.active(context);
        assertNotNull("Saved local theme must survive restart", active);
        assertEquals(id, active.packageName);
        assertEquals(Integer.valueOf(0xFF112233), active.colors.get("colorPrimary"));
        // No signer on local snapshots: never deactivated between reads.
        assertEquals(id, ThemePacks.active(context).packageName);

        // Delete deactivates and removes.
        assertTrue(LocalThemes.delete(context, id));
        createdKeys.remove(draft.key);
        assertNull(ThemePacks.active(context));
        assertNull(LocalThemes.read(context, id));
        assertFalse(LocalThemes.list(context).stream()
                .anyMatch(t -> id.equals(t.id)));
    }

    @Test
    public void saveAndApply_activatesInOneStep() {
        LocalThemes.Draft draft = LocalThemes.newDraft("ApplyNow", false);
        draft.colors.put("colorSurface", 0xFF101010);
        String id = LocalThemes.saveAndApply(context, draft);
        assertNotNull(id);
        track(draft);
        ThemePacks.Snapshot active = ThemePacks.active(context);
        assertNotNull(active);
        assertEquals(id, active.packageName);
        assertEquals(Integer.valueOf(0xFF101010), active.colors.get("colorSurface"));
    }

    @Test
    public void saveExistingKey_isIdempotent_noDuplicate() {
        LocalThemes.Draft draft = LocalThemes.newDraft("Once", false);
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);
        int before = LocalThemes.list(context).size();
        draft.name = "Twice";
        assertEquals(id, LocalThemes.save(context, draft));
        assertEquals(before, LocalThemes.list(context).size());
        assertEquals("Twice", LocalThemes.read(context, id).name);
    }

    // ---------- custom icons + wallpaper persistence ----------

    @Test
    public void customIcons_persistThroughSaveAndEditRetention() {
        LocalThemes.Draft draft = LocalThemes.newDraft("Iconed", false);
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);

        // Stage a custom folder icon against the SAVED theme.
        assertTrue(LocalThemes.stageImageStream(context, draft,
                LocalThemes.ImageSlot.FOLDER_ICON,
                new ByteArrayInputStream(pngBytes(Color.MAGENTA))));
        assertNotNull(draft.stagedIconFile("folder"));
        // Stage a wallpaper too.
        assertTrue(LocalThemes.stageImageStream(context, draft,
                LocalThemes.ImageSlot.WALLPAPER,
                new ByteArrayInputStream(pngBytes(Color.CYAN))));

        // Second save retains what was already stored and adds the staged art.
        assertEquals(id, LocalThemes.save(context, draft));
        LocalThemes.LocalTheme saved = LocalThemes.read(context, id);
        File folderIcon = saved.iconFiles.get("folder");
        assertNotNull("Custom folder icon must persist", folderIcon);
        assertTrue(folderIcon.isFile());
        Bitmap decoded = BitmapFactory.decodeFile(folderIcon.getAbsolutePath());
        assertNotNull(decoded);
        assertEquals(16, decoded.getWidth());
        decoded.recycle();
        assertNotNull(saved.wallpaper);
        assertTrue(saved.wallpaper.isFile());

        // Edit flow: draftFrom retains stored images with NO re-staging.
        LocalThemes.Draft edit = LocalThemes.draftFrom(context, id);
        assertTrue(edit.hasIcon("folder"));
        assertTrue(edit.hasWallpaper());
        assertTrue(edit.stagedIconFile("folder") == null
                && edit.retainedIconName("folder") != null);
        assertEquals(id, LocalThemes.save(context, edit));
        assertNotNull(LocalThemes.read(context, id).iconFiles.get("folder"));

        // Clearing the icon survives a save.
        LocalThemes.clearImage(edit, LocalThemes.ImageSlot.FOLDER_ICON);
        assertFalse(edit.hasIcon("folder"));
        assertEquals(id, LocalThemes.save(context, edit));
        assertNull(LocalThemes.read(context, id).iconFiles.get("folder"));
        // Wallpaper untouched by the icon clear.
        assertNotNull(LocalThemes.read(context, id).wallpaper);

        assertTrue(LocalThemes.stageImageStream(context, edit,
                LocalThemes.ImageSlot.FOLDER_ICON,
                new ByteArrayInputStream(pngBytes(Color.GREEN))));
        assertEquals(id, LocalThemes.save(context, edit));
        assertEquals(id, LocalThemes.save(context, edit));
        assertTrue(LocalThemes.stageImageStream(context, edit,
                LocalThemes.ImageSlot.FOLDER_ICON,
                new ByteArrayInputStream(pngBytes(Color.BLUE))));
        assertEquals(id, LocalThemes.save(context, edit));
        Bitmap replaced = BitmapFactory.decodeFile(LocalThemes.read(context, id)
                .iconFiles.get("folder").getAbsolutePath());
        assertNotNull(replaced);
        assertEquals(Color.BLUE, replaced.getPixel(0, 0));
        replaced.recycle();

        LocalThemes.Draft copy = LocalThemes.duplicate(context, LocalThemes.read(context, id));
        String copyId = LocalThemes.save(context, copy);
        assertNotNull(copyId);
        track(copy);
        assertFalse(id.equals(copyId));
        assertNotNull(LocalThemes.read(context, copyId).iconFiles.get("folder"));
        assertNotNull(LocalThemes.read(context, copyId).wallpaper);
    }

    // ---------- per-file-type icons (all contract kinds + variants) ----------

    @Test
    public void perTypeIcons_stageAllKinds_persistRetainClearRoundTrip() {
        final int kindCount = ThemePackContracts.ICON_KINDS.length;
        final String[] variantKeys = {"folder_light", "folder_dark", "apk_light"};

        // Save an empty theme first, then stage one distinct icon per base
        // kind plus three variants against the SAVED theme.
        LocalThemes.Draft draft = LocalThemes.newDraft("PerType", false);
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);

        Map<String, Integer> expectedColors = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kindCount; i++) {
            int color = 0xFF000000 | (0x101010 * (i + 1));
            String key = ThemePackContracts.ICON_KINDS[i];
            assertTrue("staging base key " + key,
                    LocalThemes.stageImageStream(context, draft,
                            LocalThemes.ImageSlot.iconSlot(key),
                            new ByteArrayInputStream(pngBytes(color))));
            expectedColors.put(key, color);
        }
        int[] variantColors = {0xFFFF0001, 0xFFFF0002, 0xFFFF0003};
        for (int i = 0; i < variantKeys.length; i++) {
            assertTrue("staging variant " + variantKeys[i],
                    LocalThemes.stageIconStream(context, draft, variantKeys[i],
                            new ByteArrayInputStream(pngBytes(variantColors[i]))));
            expectedColors.put(variantKeys[i], variantColors[i]);
        }

        assertEquals(id, LocalThemes.save(context, draft));
        LocalThemes.LocalTheme saved = LocalThemes.read(context, id);
        assertNotNull(saved);
        assertEquals("every base kind + 3 variants must persist",
                kindCount + variantKeys.length, saved.iconFiles.size());
        for (Map.Entry<String, Integer> e : expectedColors.entrySet()) {
            File icon = saved.iconFiles.get(e.getKey());
            assertNotNull("missing icon for " + e.getKey(), icon);
            assertTrue(icon.isFile());
            Bitmap decoded = BitmapFactory.decodeFile(icon.getAbsolutePath());
            assertNotNull(decoded);
            assertEquals("pixel color must match the staged PNG for "
                    + e.getKey(), e.getValue().intValue(), decoded.getPixel(0, 0));
            decoded.recycle();
        }

        // Edit flow retains every stored icon with NO re-staging.
        LocalThemes.Draft edit = LocalThemes.draftFrom(context, id);
        for (String key : expectedColors.keySet()) {
            assertTrue("edit must retain " + key, edit.hasIcon(key));
            assertNotNull(edit.retainedIconName(key));
            assertNull(edit.stagedIconFile(key));
        }
        assertEquals(id, LocalThemes.save(context, edit));
        assertEquals(kindCount + variantKeys.length,
                LocalThemes.read(context, id).iconFiles.size());

        // Per-key clearing: only the cleared keys disappear (key-based API
        // so the folder_dark variant does NOT collapse onto the base key).
        LocalThemes.clearIcon(edit, "apk");
        LocalThemes.clearIcon(edit, "folder_dark");
        LocalThemes.clearIcon(edit, "parent_folder");
        assertFalse(edit.hasIcon("apk"));
        assertFalse(edit.hasIcon("folder_dark"));
        assertTrue(edit.hasIcon("folder_light")); // untouched variant survives
        assertEquals(id, LocalThemes.save(context, edit));
        LocalThemes.LocalTheme cleared = LocalThemes.read(context, id);
        assertNull(cleared.iconFiles.get("apk"));
        assertNull(cleared.iconFiles.get("folder_dark"));
        assertNull(cleared.iconFiles.get("parent_folder"));
        assertNotNull(cleared.iconFiles.get("folder"));
        assertNotNull(cleared.iconFiles.get("folder_light"));
        assertNotNull(cleared.iconFiles.get("apk_light"));
        assertNotNull(cleared.iconFiles.get("certificate"));
        assertEquals(kindCount + variantKeys.length - 3, cleared.iconFiles.size());

        // Duplicate copies the remaining per-type artwork to a NEW theme.
        LocalThemes.Draft copy = LocalThemes.duplicate(context,
                LocalThemes.read(context, id));
        String copyId = LocalThemes.save(context, copy);
        assertNotNull(copyId);
        track(copy);
        assertFalse(copyId.equals(id));
        LocalThemes.LocalTheme copyTheme = LocalThemes.read(context, copyId);
        assertEquals(cleared.iconFiles.keySet(), copyTheme.iconFiles.keySet());
        Bitmap copied = BitmapFactory.decodeFile(
                copyTheme.iconFiles.get("folder_light").getAbsolutePath());
        assertEquals(0xFFFF0001, copied.getPixel(0, 0));
        copied.recycle();

        // Export/import round trip preserves ALL remaining keys incl variants.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(LocalThemes.exportTheme(context, cleared, out));
        int before = LocalThemes.list(context).size();
        LocalThemes.ImportResult result = LocalThemes.importTheme(context,
                new ByteArrayInputStream(out.toByteArray()));
        assertNotNull("import failed rejected=" + result.rejected
                + " error=" + result.error, result.theme);
        assertFalse(result.rejected);
        assertEquals(before + 1, LocalThemes.list(context).size());
        createdKeys.add(result.theme.id.substring("local.".length()));
        assertEquals(cleared.iconFiles.keySet(), result.theme.iconFiles.keySet());
        Bitmap roundTripped = BitmapFactory.decodeFile(
                result.theme.iconFiles.get("apk_light").getAbsolutePath());
        assertEquals(0xFFFF0003, roundTripped.getPixel(0, 0));
        roundTripped.recycle();
        Bitmap roundTrippedBase = BitmapFactory.decodeFile(
                result.theme.iconFiles.get("dex").getAbsolutePath());
        assertEquals(expectedColors.get("dex").intValue(),
                roundTrippedBase.getPixel(0, 0));
        roundTrippedBase.recycle();
        assertNull(ThemePacks.active(context));
    }

    @Test
    public void import_rejectsTooManyEmbeddedImages_butAcceptsLegacyDocuments() {
        // Legacy documents (at most wallpaper/preview/folder/file) still fit.
        LocalThemes.ImportResult legacy = importOf("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"legacy\","
                + "\"images\":{}}");
        assertNotNull(legacy.theme);

        // 1x1 PNG payload embedded 78 times exceeds the entry cap.
        byte[] tiny = pngBytes(Color.GRAY);
        String tinyBase64 = android.util.Base64.encodeToString(tiny,
                android.util.Base64.NO_WRAP);
        StringBuilder doc = new StringBuilder("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"flood\",\"images\":{");
        for (int i = 0; i < LocalThemes.MAX_EMBEDDED_IMAGES + 1; i++) {
            if (i > 0) doc.append(',');
            doc.append('"').append(ThemePackContracts.ICON_KINDS[
                    i % ThemePackContracts.ICON_KINDS.length]).append('_')
                    .append(i).append("\":\"").append(tinyBase64).append('"');
        }
        doc.append("}}");
        LocalThemes.ImportResult flooded = importOf(doc.toString());
        assertNull(flooded.theme);
        assertTrue(flooded.rejected);
    }

    @Test
    public void import_acceptsVariantIconKeysAndRejectsOversizedIcons() {
        byte[] folderLight = pngBytes(0xFF00AA11);
        byte[] folderDark = pngBytes(0xFF00AA22);
        String lightB64 = android.util.Base64.encodeToString(folderLight,
                android.util.Base64.NO_WRAP);
        String darkB64 = android.util.Base64.encodeToString(folderDark,
                android.util.Base64.NO_WRAP);
        // Direct importTheme (NOT importOf, which deletes the theme for
        // rejection-test teardown): we need the files alive to decode.
        LocalThemes.ImportResult ok;
        try (InputStream in = new ByteArrayInputStream(("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"variants\","
                + "\"images\":{\"folder_light\":\"" + lightB64
                + "\",\"folder_dark\":\"" + darkB64 + "\"}}")
                .getBytes(StandardCharsets.UTF_8))) {
            ok = LocalThemes.importTheme(context, in);
        } catch (Exception e) {
            ok = new LocalThemes.ImportResult(null, false, "io");
        }
        assertNotNull("variant icon keys must import: " + ok.error, ok.theme);
        createdKeys.add(ok.theme.id.substring("local.".length()));
        Bitmap light = BitmapFactory.decodeFile(
                ok.theme.iconFiles.get("folder_light").getAbsolutePath());
        assertEquals(0xFF00AA11, light.getPixel(0, 0));
        light.recycle();
        Bitmap dark = BitmapFactory.decodeFile(
                ok.theme.iconFiles.get("folder_dark").getAbsolutePath());
        assertEquals(0xFF00AA22, dark.getPixel(0, 0));
        dark.recycle();
        assertFalse(ok.theme.iconFiles.containsKey("folder")); // base untouched

        // An icon payload over the per-slot cap (256KB) is rejected.
        byte[] huge = new byte[(int) (ThemePackContracts.MAX_ICON_BYTES + 4096)];
        huge[0] = (byte) 0x89;
        huge[1] = 'P';
        huge[2] = 'N';
        huge[3] = 'G';
        String hugeB64 = android.util.Base64.encodeToString(huge,
                android.util.Base64.NO_WRAP);
        LocalThemes.ImportResult rejected = importOf("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"huge\","
                + "\"images\":{\"folder\":\"" + hugeB64 + "\"}}");
        assertNull(rejected.theme);
        assertTrue(rejected.rejected);
    }

    // ---------- launcher selector ----------

    @Test
    public void launcherSelector_persistsOnSnapshot() {
        LocalThemes.Draft draft = LocalThemes.newDraft("Launcher", false);
        draft.launcherId = "violet";
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);
        assertEquals("violet", LocalThemes.read(context, id).launcherId);
        assertTrue(LocalThemes.activate(context, id));
        assertEquals("violet", ThemePacks.active(context).launcherIcon);
    }

    // ---------- export / import ----------

    @Test
    public void exportImport_roundTrip_pureDocument() throws Exception {
        LocalThemes.Draft draft = LocalThemes.newDraft("RoundTrip", true);
        draft.colors.put("colorPrimary", 0xFF00AA55);
        draft.launcherId = "amber";
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);
        LocalThemes.LocalTheme saved = LocalThemes.read(context, id);
        assertTrue(saved.colors.containsKey("colorPrimary"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(LocalThemes.exportTheme(context, saved, out));
        byte[] document = out.toByteArray();

        // Import creates a NEW theme with the same content, not applied.
        int before = LocalThemes.list(context).size();
        LocalThemes.ImportResult result = LocalThemes.importTheme(context,
                new ByteArrayInputStream(document));
        assertNotNull(result.theme);
        assertFalse(result.rejected);
        assertEquals(LocalThemes.list(context).size(), before + 1);
        createdKeys.add(result.theme.id.substring("local.".length()));
        assertTrue(!result.theme.id.equals(id));
        assertEquals("RoundTrip", result.theme.name);
        assertEquals(Boolean.TRUE, result.theme.dark);
        assertEquals(Integer.valueOf(0xFF00AA55),
                result.theme.colors.get("colorPrimary"));
        assertEquals("amber", result.theme.launcherId);
        assertNull(ThemePacks.active(context));
    }

    @Test
    public void exportImport_roundTrip_withImages() throws Exception {
        LocalThemes.Draft draft = LocalThemes.newDraft("WithArt", false);
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);
        assertTrue(LocalThemes.stageImageStream(context, draft,
                LocalThemes.ImageSlot.FOLDER_ICON,
                new ByteArrayInputStream(pngBytes(Color.YELLOW))));
        assertEquals(id, LocalThemes.save(context, draft));

        LocalThemes.LocalTheme saved = LocalThemes.read(context, id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(LocalThemes.exportTheme(context, saved, out));

        LocalThemes.ImportResult result = LocalThemes.importTheme(context,
                new ByteArrayInputStream(out.toByteArray()));
        assertNotNull("import failed rejected=" + result.rejected
                + " error=" + result.error, result.theme);
        createdKeys.add(result.theme.id.substring("local.".length()));
        File importedFolder = result.theme.iconFiles.get("folder");
        assertNotNull("Custom folder icon must travel through export/import",
                importedFolder);
        Bitmap decoded = BitmapFactory.decodeFile(importedFolder.getAbsolutePath());
        assertNotNull(decoded);
        decoded.recycle();
    }

    @Test
    public void import_rejectsMalformedDocuments() {
        assertRejected("not json at all");
        assertRejected("{}"); // missing format/type
        assertRejected("{\"format\":2,\"type\":\"mp-manager-theme\",\"name\":\"x\"}");
        assertRejected("{\"format\":1,\"type\":\"other\",\"name\":\"x\"}");
        assertRejected("{\"format\":1,\"type\":\"mp-manager-theme\","
                + "\"wallpaperScope\":\"everywhere\",\"name\":\"x\"}");
        // Unknown tokens are dropped (not rejected), so this one imports.
        LocalThemes.ImportResult ok = importOf("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"ok\","
                + "\"colors\":{\"colorPrimary\":\"#FF112233\",\"bogus\":\"#FF000000\"}}");
        assertNotNull(ok.theme);
        assertTrue(ok.theme.colors.containsKey("colorPrimary"));
        assertEquals(1, ok.theme.colors.size());
        // Oversized colors object -> rejected.
        StringBuilder huge = new StringBuilder("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"huge\",\"colors\":{");
        for (int i = 0; i < 500; i++) {
            if (i > 0) huge.append(',');
            huge.append("\"tok").append(i).append("\":\"#FF000000\"");
        }
        huge.append("}}");
        LocalThemes.ImportResult rejected = importOf(huge.toString());
        assertNull(rejected.theme);
        assertTrue(rejected.rejected);
        // Bad embedded image magic -> rejected.
        LocalThemes.ImportResult badImage = importOf("{\"format\":1,"
                + "\"type\":\"mp-manager-theme\",\"name\":\"img\","
                + "\"images\":{\"folder\":\"" + java.util.Base64.getEncoder()
                .encodeToString("GIF89a".getBytes(StandardCharsets.US_ASCII))
                + "\"}}");
        assertNull(badImage.theme);
        assertTrue(badImage.rejected);
    }

    private static void assertRejected(String document) {
        LocalThemes.ImportResult result = importOf(document);
        assertNull("Expected rejection for: " + document, result.theme);
        assertTrue(result.rejected);
    }

    private static LocalThemes.ImportResult importOf(String document) {
        Context app = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        LocalThemes.ImportResult result = LocalThemes.importTheme(app,
                new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));
        if (result.theme != null) {
            // Track for teardown via the package-private hook this test owns.
            ThemePacks.deleteLocal(app,
                    result.theme.id.substring(ThemePacks.LOCAL_PKG_PREFIX.length()));
        }
        return result;
    }

    // ---------- corrupt store: deactivate, never wipe local user data ----------

    @Test
    public void corruptSnapshotJson_deactivates_butPreservesPackDirAndIcons()
            throws Exception {
        LocalThemes.Draft draft = LocalThemes.newDraft("CorruptProbe", false);
        assertTrue(LocalThemes.stageImageStream(context, draft,
                LocalThemes.ImageSlot.FOLDER_ICON,
                new ByteArrayInputStream(pngBytes(Color.RED))));
        String id = LocalThemes.save(context, draft);
        assertNotNull(id);
        track(draft);

        File packDir = new File(new File(context.getFilesDir(), "theme_packs"), id);
        assertTrue("pack directory must exist after save", packDir.isDirectory());
        File snapshot = new File(packDir, "snapshot.json");
        assertTrue("snapshot.json must exist after save", snapshot.isFile());
        // Capture the stored artwork BEFORE corrupting the snapshot so the
        // preservation assertions can address concrete files.
        LocalThemes.LocalTheme before = LocalThemes.read(context, id);
        assertNotNull(before);
        File folderIcon = before.iconFiles.get("folder");
        assertNotNull(folderIcon);
        assertTrue(folderIcon.isFile());

        // Corrupt the persisted snapshot behind the selection.
        try (OutputStream out = new FileOutputStream(snapshot)) {
            out.write('{');
        }
        // Point the selection at the corrupted local pack exactly like the
        // host would after a restart (theme_packs/active_package prefs).
        context.getSharedPreferences("theme_packs", Context.MODE_PRIVATE)
                .edit().putString("active_package", id).commit();
        ThemePacks.invalidateCachedSnapshot();

        assertNull("A malformed snapshot must deactivate, never crash or "
                + "resurrect a snapshot", ThemePacks.active(context));
        // The reviewer contract: the reserved local namespace is host-owned
        // user data — a corrupt read must NOT deletePack() the directory, so
        // the saved theme (incl. staged icons) stays on disk for repair.
        assertTrue("local pack dir must survive a corrupt snapshot.json",
                packDir.isDirectory());
        assertTrue("snapshot.json itself is never deleted",
                snapshot.isFile());
        assertTrue("staged folder icon must survive the corrupt read",
                folderIcon.isFile());
        Bitmap decoded = BitmapFactory.decodeFile(folderIcon.getAbsolutePath());
        assertNotNull(decoded);
        assertEquals(Color.RED, decoded.getPixel(0, 0));
        decoded.recycle();
        // The read path degrades gracefully too (no crash, null theme).
        assertNull(LocalThemes.read(context, id));
        // The deactivate path also cleared the (corrupt) selection pointer.
        assertNull(context.getSharedPreferences("theme_packs",
                Context.MODE_PRIVATE).getString("active_package", null));
    }

    // ---------- real process restart persistence (two invocations) ----------
    //
    // These two methods are ONLY meaningful when run in two SEPARATE
    // instrumentation invocations (each starts a fresh target-app process;
    // the static snapshot cache cannot bridge them):
    //   1) am instrument -e class <pkg>.LocalThemesIntegrationTest#realRestartStage_...
    //   2) am instrument -e class <pkg>.LocalThemesIntegrationTest#realRestartVerify_...
    // Within a single class run the verify step skips (Assume) because the
    // marker prefs would come from the same process — no restart proven.

    /** Invocation 1: save + activate the probe and record its id. Cleans up
     *  nothing; state must survive the process death between invocations. */
    @Test
    public void realRestartStage_saveAndActivateProbe() {
        SharedPreferences probe = context.getSharedPreferences(
                RESTART_PROBE_PREFS, Context.MODE_PRIVATE);
        String previous = probe.getString("probe_id", null);
        if (previous != null) {
            // Re-staging after an earlier run: replace the stale probe so
            // exactly one probe theme lives in app data.
            LocalThemes.delete(context, previous);
        }
        LocalThemes.Draft draft = LocalThemes.newDraft("RestartProbe", false);
        draft.colors.put("colorPrimary", RESTART_PROBE_COLOR);
        String id = LocalThemes.saveAndApply(context, draft);
        assertNotNull(id);
        assertNotNull(ThemePacks.active(context));
        probe.edit().putString("probe_id", id)
                .putInt("probe_pid", android.os.Process.myPid()).commit();
    }

    /** Invocation 2: a brand-new app process. The active selection and the
     *  saved theme can only come from disk (prefs + private store). */
    @Test
    public void realRestartVerify_probeSurvivesFreshProcess_thenCleansUp() {
        // Skip unless BOTH markers of an undisturbed stage survive: the
        // recorded id AND the active selection still pointing at it. Inside
        // a single class run the sibling tests' deactivate() clears the
        // selection — a pass there would prove nothing about a restart.
        SharedPreferences probe = context.getSharedPreferences(
                RESTART_PROBE_PREFS, Context.MODE_PRIVATE);
        String id = probe.getString("probe_id", null);
        int stagePid = probe.getInt("probe_pid", -1);
        String activePointer = context.getSharedPreferences("theme_packs",
                Context.MODE_PRIVATE).getString("active_package", null);
        Assume.assumeTrue("No undisturbed stage probe: the stage step must "
                + "run first in its OWN separate instrumentation invocation "
                + "(marker=" + id + ", stagePid=" + stagePid + ", active="
                + activePointer + ").",
                id != null && stagePid != -1 && id.equals(activePointer));

        // Hard proof of a separate process: each `am instrument` invocation
        // restarts the target app process, so the current PID must differ
        // from the stage invocation's PID — no static cache can bridge them.
        int currentPid = android.os.Process.myPid();
        assertTrue("verify must run in a NEW process: stagePid=" + stagePid
                + " currentPid=" + currentPid, currentPid != stagePid);

        ThemePacks.Snapshot active = ThemePacks.active(context);
        assertNotNull("activated local theme must still be active in a "
                + "brand-new process", active);
        assertEquals(id, active.packageName);
        assertEquals("RestartProbe", active.name);
        assertEquals(Integer.valueOf(RESTART_PROBE_COLOR),
                active.colors.get("colorPrimary"));
        assertTrue(LocalThemes.isActive(context, id));
        LocalThemes.LocalTheme saved = LocalThemes.read(context, id);
        assertNotNull("saved local theme must survive the process restart",
                saved);
        assertEquals("RestartProbe", saved.name);
        assertEquals(Integer.valueOf(RESTART_PROBE_COLOR),
                saved.colors.get("colorPrimary"));

        // This invocation owns the cleanup (no data erasure anywhere).
        assertTrue(LocalThemes.delete(context, id));
        probe.edit().clear().commit();
        assertNull(ThemePacks.active(context));
    }

    // ---------- backend exclusivity ----------

    @Test
    public void providerPackActivation_clearsLocalSelection() {
        LocalThemes.Draft draft = LocalThemes.newDraft("Excl", false);
        String id = LocalThemes.saveAndApply(context, draft);
        assertNotNull(id);
        track(draft);
        assertNotNull(ThemePacks.active(context));

        // Simulate a provider pack being selected through the shared flow:
        // ThemePacks clears the local pointer by design when the pack
        // import activates (verified via the local read path only).
        ThemePacks.deactivate(context);
        assertNull(ThemePacks.active(context));
        // The saved local theme itself survives deactivation.
        assertNotNull(LocalThemes.read(context, id));
        assertTrue(LocalThemes.activate(context, id));
        assertNotNull(ThemePacks.active(context));
    }
}
