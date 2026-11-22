package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ingestion coverage for the private store: snapshot persistence
 * round-trips, staged unique files with size caps, atomic commit with
 * versioned image names and rollback, deletion, and the Snapshot label
 * locale resolution.
 */
public class ThemePackIngestionTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File filesDir;

    @Before
    public void setUp() throws Exception {
        filesDir = folder.newFolder("files");
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static ThemePackStore.StoredSnapshot snapshot() {
        ThemePackStore.StoredSnapshot s = new ThemePackStore.StoredSnapshot();
        s.id = "example_night";
        s.name = "Example Night";
        s.dark = Boolean.TRUE;
        s.accent = 0xFF6750A4;
        s.scrim = 0x66000000;
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("colorPrimary", 0xFF6750A4);
        colors.put("wallpaper_scrim", 0x66000000);
        colors.put("folder_icon_bg", 0x33FFFFFF);
        s.colors = colors;
        Map<String, Map<String, String>> strings = new LinkedHashMap<>();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("en-US", "Files");
        files.put("zh-CN", "\u6587\u4ef6");
        strings.put("tab.files", files);
        s.strings = strings;
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("comment", 0xFF6A9955);
        syntax.put("keyword", 0xFFCC7832);
        s.editorSyntax = syntax;
        s.wallpaperScope = "files";
        return s;
    }

    /** Writes raw JSON directly so pre-schema (v1-era) files are simulated. */
    private static void writeRawSnapshot(File filesDir, String pkg, String json)
            throws IOException {
        File file = ThemePackStore.snapshotFile(filesDir, pkg);
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdirs failed");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------- snapshot persistence ----------

    @Test
    public void snapshot_roundTrips() throws IOException {
        ThemePackStore.StoredSnapshot in = snapshot();
        in.schema = ThemePackStore.SCHEMA_V2;
        in.revision = 7L;
        in.wallpaperName = "wallpaper-123456789";
        ThemePackStore.writeSnapshot(filesDir, "com.example.themes", in);
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(
                filesDir, "com.example.themes");
        assertNotNull(out);
        assertEquals("example_night", out.id);
        assertEquals("Example Night", out.name);
        assertEquals(Boolean.TRUE, out.dark);
        assertEquals(0xFF6750A4, out.accent.intValue());
        assertEquals(0x66000000, out.scrim.intValue());
        assertEquals(3, out.colors.size());
        assertEquals("Files", out.strings.get("tab.files").get("en-US"));
        assertEquals("\u6587\u4ef6", out.strings.get("tab.files").get("zh-CN"));
        assertEquals("wallpaper-123456789", out.wallpaperName);
        assertNull(out.previewName);
        // v2 fields
        assertEquals(ThemePackStore.SCHEMA_V2, out.schema.intValue());
        assertEquals(7L, out.revision.longValue());
        assertEquals(2, out.editorSyntax.size());
        assertEquals(0xFFCC7832, out.editorSyntax.get("keyword").intValue());
        assertEquals("files", out.wallpaperScope);
    }

    @Test
    public void snapshot_missingOrNullFields_roundTrip() throws IOException {
        ThemePackStore.StoredSnapshot in = new ThemePackStore.StoredSnapshot();
        in.id = "bare";
        ThemePackStore.writeSnapshot(filesDir, "com.example.themes", in);
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(
                filesDir, "com.example.themes");
        assertNotNull(out);
        assertEquals("bare", out.id);
        assertNull(out.name);
        assertNull(out.dark);
        assertNull(out.accent);
        assertNull(out.scrim);
        assertTrue(out.colors == null || out.colors.isEmpty());
        assertNull(out.wallpaperName);
        assertNull(out.previewName);
    }

    @Test
    public void snapshot_missingOrCorrupt_returnsNull() throws IOException {
        assertNull(ThemePackStore.readSnapshot(filesDir, "com.example.themes"));
        ThemePackStore.StoredSnapshot in = snapshot();
        ThemePackStore.writeSnapshot(filesDir, "com.example.themes", in);
        File file = ThemePackStore.snapshotFile(filesDir, "com.example.themes");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("{corrupt".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertNull(ThemePackStore.readSnapshot(filesDir, "com.example.themes"));
    }

    // ---------- schema/revision back-compat ----------

    @Test
    public void snapshot_preSchemaFile_normalizesToSchema1() throws IOException {
        // Gson omits null fields, so a null schema serializes to a v1-era
        // file with no "schema" key at all.
        ThemePackStore.StoredSnapshot in = snapshot();
        in.schema = null;
        in.revision = null;
        ThemePackStore.writeSnapshot(filesDir, "com.example.themes", in);
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(
                filesDir, "com.example.themes");
        assertNotNull(out);
        assertEquals(ThemePackStore.SCHEMA_V1, out.schema.intValue());
        assertEquals(0L, out.revision.longValue());
    }

    @Test
    public void snapshot_schemaZeroOrNegative_treatedAs1() throws IOException {
        writeRawSnapshot(filesDir, "com.example.themes",
                "{\"id\":\"old\",\"schema\":0,\"revision\":3}");
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(
                filesDir, "com.example.themes");
        assertNotNull(out);
        assertEquals("old", out.id);
        assertEquals(ThemePackStore.SCHEMA_V1, out.schema.intValue());
        assertEquals(3L, out.revision.longValue());
    }

    @Test
    public void snapshot_unknownScopeField_forcedBackToFiles() throws IOException {
        writeRawSnapshot(filesDir, "com.example.themes",
                "{\"id\":\"old\",\"schema\":2,\"wallpaperScope\":\"bogus\"}");
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(
                filesDir, "com.example.themes");
        assertNotNull(out);
        assertEquals("files", out.wallpaperScope);
    }

    @Test
    public void snapshot_nonNumericRevision_normalizesToZero() throws IOException {
        // A hand-edited file with a malformed revision must not break reads.
        writeRawSnapshot(filesDir, "com.example.themes",
                "{\"id\":\"old\",\"revision\":-5}");
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(
                filesDir, "com.example.themes");
        assertNotNull(out);
        assertEquals(0L, out.revision.longValue());
    }

    @Test
    public void snapshot_revisionRoundTripsThroughCommit() throws IOException {
        String pkg = "com.example.themes";
        ThemePackStore.StoredSnapshot in = snapshot();
        in.revision = 41L;
        in.schema = ThemePackStore.SCHEMA_V2;
        assertTrue(ThemePackStore.commit(filesDir, pkg, in, null, null));
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(filesDir, pkg);
        assertNotNull(out);
        assertEquals(41L, out.revision.longValue());
        assertEquals(ThemePackStore.SCHEMA_V2, out.schema.intValue());
        assertEquals("files", out.wallpaperScope);
        assertEquals(2, out.editorSyntax.size());
    }

    // ---------- path safety ----------

    @Test
    public void packDir_rejectsUnsafeProviderNames() {
        String[] bad = {"", null, "..", "../evil", "a/b", ".hidden", "pkg.", "pkg..", "a b"};
        for (String name : bad) {
            try {
                ThemePackStore.packDir(filesDir, name);
                fail("provider package '" + name + "' must be rejected");
            } catch (IllegalArgumentException expected) {
            }
        }
        assertEquals(new File(filesDir, "theme_packs/com.example.themes").getPath(),
                ThemePackStore.packDir(filesDir, "com.example.themes").getPath());
    }

    @Test
    public void imageReferences_rejectNonGeneratedNames() {
        assertNull(ThemePackStore.wallpaperFile(filesDir, "com.example.themes", null));
        assertNull(ThemePackStore.wallpaperFile(filesDir, "com.example.themes", "../evil"));
        assertNull(ThemePackStore.wallpaperFile(filesDir, "com.example.themes", "snapshot.json"));
        assertNull(ThemePackStore.wallpaperFile(filesDir, "com.example.themes", "wallpaper-abc"));
        assertNull(ThemePackStore.previewFile(filesDir, "com.example.themes", "wallpaper-123"));
        assertNotNull(ThemePackStore.wallpaperFile(filesDir, "com.example.themes", "wallpaper-123"));
    }

    // ---------- staging ----------

    @Test
    public void stageFile_uniqueFilesWithinLimit() {
        File packDir = ThemePackStore.packDir(filesDir, "com.example.themes");
        File first = ThemePackStore.stageFile(bytes("wallpaper-bytes"), packDir, 1024);
        File second = ThemePackStore.stageFile(bytes("wallpaper-bytes"), packDir, 1024);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.isFile());
        assertFalse(first.equals(second));
        assertEquals("wallpaper-bytes", read(first));
        // no leftovers besides the two staged files
        assertEquals(2, packDir.listFiles().length);
    }

    @Test
    public void stageFile_overLimit_leavesNothing() {
        File packDir = ThemePackStore.packDir(filesDir, "com.example.themes");
        assertNull(ThemePackStore.stageFile(bytes("0123456789"), packDir, 8));
        assertEquals(0, packDir.listFiles().length);
    }

    // ---------- commit ----------

    @Test
    public void commit_stagedFilesConsume_andNoBaksRemain() {
        String pkg = "com.example.themes";
        File packDir = ThemePackStore.packDir(filesDir, pkg);
        ThemePackStore.StoredSnapshot in = snapshot();
        File stagedWallpaper = ThemePackStore.stageFile(bytes("PNGDATA"), packDir, 1024);
        File stagedPreview = ThemePackStore.stageFile(bytes("JPEGDATA"), packDir, 1024);
        assertTrue(ThemePackStore.commit(filesDir, pkg, in, stagedWallpaper, stagedPreview));
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(filesDir, pkg);
        assertNotNull(out);
        assertTrue(out.wallpaperName.startsWith("wallpaper-"));
        assertTrue(out.previewName.startsWith("preview-"));
        assertEquals("PNGDATA", read(ThemePackStore.wallpaperFile(filesDir, pkg, out.wallpaperName)));
        assertEquals("JPEGDATA", read(ThemePackStore.previewFile(filesDir, pkg, out.previewName)));
        assertFalse(stagedWallpaper.exists());
        assertFalse(stagedPreview.exists());
        // snapshot.json + wallpaper-* + preview-*; no .bak/.tmp leftovers
        assertEquals(3, packDir.listFiles().length);
    }

    @Test
    public void commit_withoutImages_removesPreviousOnes() {
        String pkg = "com.example.themes";
        File packDir = ThemePackStore.packDir(filesDir, pkg);
        File staged = ThemePackStore.stageFile(bytes("OLD-PNG"), packDir, 1024);
        assertTrue(ThemePackStore.commit(filesDir, pkg, snapshot(), staged, null));
        assertNotNull(ThemePackStore.readSnapshot(filesDir, pkg).wallpaperName);

        // second import carries no wallpaper: previous one must be dropped
        ThemePackStore.StoredSnapshot bare = new ThemePackStore.StoredSnapshot();
        bare.id = "bare";
        assertTrue(ThemePackStore.commit(filesDir, pkg, bare, null, null));
        assertEquals(1, packDir.listFiles().length); // snapshot.json only
        assertNull(ThemePackStore.readSnapshot(filesDir, pkg).wallpaperName);
    }

    @Test
    public void commit_failure_restoresPreviousSnapshotAndImages() throws Exception {
        String pkg = "com.example.themes";
        File packDir = ThemePackStore.packDir(filesDir, pkg);
        File staged = ThemePackStore.stageFile(bytes("OLD-PNG"), packDir, 1024);
        ThemePackStore.StoredSnapshot first = snapshot();
        assertTrue(ThemePackStore.commit(filesDir, pkg, first, staged, null));
        String oldWallpaperName = first.wallpaperName;

        // Force a snapshot persistence failure: replace snapshot.json with a
        // non-empty directory so the rename cannot go through.
        File snap = ThemePackStore.snapshotFile(filesDir, pkg);
        File inside = new File(snap, "blocker");
        assertTrue(snap.delete());
        assertTrue(snap.mkdirs());
        try (FileOutputStream fos = new FileOutputStream(inside)) {
            fos.write(1);
        }

        File stagedNew = ThemePackStore.stageFile(bytes("NEW-PNG"), packDir, 1024);
        ThemePackStore.StoredSnapshot replacement = snapshot();
        replacement.name = "Replacement";
        assertFalse(ThemePackStore.commit(filesDir, pkg, replacement, stagedNew, null));
        // previous wallpaper file (referenced by the restored snapshot) survived
        assertEquals("OLD-PNG", read(ThemePackStore.wallpaperFile(filesDir, pkg, oldWallpaperName)));
        assertFalse(new File(packDir, "snapshot.json.bak").exists());
        // the failed import's staged image left no versioned file behind
        assertEquals(2, packDir.listFiles().length); // snapshot.json dir + old wallpaper
    }

    // ---------- retained images (no-op refresh) ----------

    @Test
    public void commit_retainedName_reusesStoredImage() throws Exception {
        String pkg = "com.example.themes";
        File packDir = ThemePackStore.packDir(filesDir, pkg);
        File staged = ThemePackStore.stageFile(bytes("PNGDATA"), packDir, 1024);
        ThemePackStore.StoredSnapshot first = snapshot();
        assertTrue(ThemePackStore.commit(filesDir, pkg, first, staged, null));
        String retainedName = first.wallpaperName;
        File retainedFile = ThemePackStore.wallpaperFile(filesDir, pkg, retainedName);
        assertTrue(retainedFile.isFile());

        // Metadata-only change with the image name pre-set (bytes already
        // verified by the caller): the stored file must survive untouched.
        ThemePackStore.StoredSnapshot changed = snapshot();
        changed.name = "Renamed";
        changed.wallpaperName = retainedName;
        assertTrue(ThemePackStore.commit(filesDir, pkg, changed, null, null));
        ThemePackStore.StoredSnapshot out = ThemePackStore.readSnapshot(filesDir, pkg);
        assertNotNull(out);
        assertEquals("Renamed", out.name);
        assertEquals(retainedName, out.wallpaperName);
        assertEquals("PNGDATA", read(retainedFile));
        // snapshot.json + the retained wallpaper; no versioned leftovers
        assertEquals(2, packDir.listFiles().length);
    }

    @Test
    public void commit_retainedName_failure_keepsStoredImage() throws Exception {
        String pkg = "com.example.themes";
        File packDir = ThemePackStore.packDir(filesDir, pkg);
        File staged = ThemePackStore.stageFile(bytes("PNGDATA"), packDir, 1024);
        ThemePackStore.StoredSnapshot first = snapshot();
        assertTrue(ThemePackStore.commit(filesDir, pkg, first, staged, null));
        String retainedName = first.wallpaperName;
        File retainedFile = ThemePackStore.wallpaperFile(filesDir, pkg, retainedName);

        // Force a snapshot persistence failure; the retained image must not
        // be deleted by the rollback (the restored snapshot references it).
        File snap = ThemePackStore.snapshotFile(filesDir, pkg);
        File inside = new File(snap, "blocker");
        assertTrue(snap.delete());
        assertTrue(snap.mkdirs());
        try (FileOutputStream fos = new FileOutputStream(inside)) {
            fos.write(1);
        }

        ThemePackStore.StoredSnapshot replacement = snapshot();
        replacement.name = "Replacement";
        replacement.wallpaperName = retainedName;
        assertFalse(ThemePackStore.commit(filesDir, pkg, replacement, null, null));
        // the retained (pre-existing) image survived the rollback
        assertEquals("PNGDATA", read(retainedFile));
    }

    @Test
    public void deletePack_removesEverything() throws Exception {
        String pkg = "com.example.themes";
        File packDir = ThemePackStore.packDir(filesDir, pkg);
        File staged = ThemePackStore.stageFile(bytes("PNG"), packDir, 1024);
        assertTrue(ThemePackStore.commit(filesDir, pkg, snapshot(), staged, null));
        assertTrue(new File(packDir, "staged-leftover").createNewFile());
        ThemePackStore.deletePack(filesDir, pkg);
        assertFalse(packDir.exists());
    }

    // ---------- label resolution ----------

    @Test
    public void label_resolvesLocaleCascade() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        Map<String, Map<String, String>> strings = new LinkedHashMap<>();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("en-US", "Files");
        files.put("zh-CN", "\u6587\u4ef6");
        files.put("fr", "Fichiers");
        strings.put("tab.files", files);
        Map<String, String> others = new LinkedHashMap<>();
        others.put("de", "Sonstiges");
        strings.put("tab.other", others);

        ThemePacks.Snapshot snap = new ThemePacks.Snapshot("com.example.themes",
                "Example", Boolean.TRUE, 0xFF6750A4, 0x66000000, colors, strings,
                null, null, null, null);
        // exact BCP47 tag
        assertEquals("Files", snap.label("tab.files", Locale.US));
        assertEquals("\u6587\u4ef6", snap.label("tab.files", Locale.SIMPLIFIED_CHINESE));
        // language-only request matches a region entry
        assertEquals("\u6587\u4ef6", snap.label("tab.files", Locale.forLanguageTag("zh")));
        assertEquals("Fichiers", snap.label("tab.files", Locale.forLanguageTag("fr-FR")));
        // no matching language: English, then any entry
        assertEquals("Sonstiges", snap.label("tab.other", Locale.forLanguageTag("fr-FR")));
        // case/separator-insensitive token lookup
        assertEquals("Files", snap.label("Tab_Files", Locale.US));
        // unknown or non-allowlisted tokens -> null
        assertNull(snap.label("tab.files2", Locale.US));
        assertNull(snap.label("unknown.key", Locale.US));
        assertNull(snap.label(null, Locale.US));
    }

    // ---------- Snapshot v2 fields + sameState ----------

    private static ThemePacks.Snapshot buildSnapshot(Map<String, Integer> colors,
                                                     Map<String, Integer> syntax,
                                                     String scope, File wallpaper) {
        return new ThemePacks.Snapshot("com.example.themes", "Example",
                Boolean.TRUE, 0xFF6750A4, 0x66000000, colors,
                new LinkedHashMap<>(), wallpaper, null, syntax, scope);
    }

    @Test
    public void snapshot_v2Defaults() {
        ThemePacks.Snapshot snap = buildSnapshot(new LinkedHashMap<>(), null, null, null);
        // null editorSyntax becomes an empty unmodifiable map; null scope -> files
        assertNotNull(snap.editorSyntax);
        assertTrue(snap.editorSyntax.isEmpty());
        assertEquals("files", snap.wallpaperScope);
        try {
            snap.editorSyntax.put("comment", 1);
            fail("editorSyntax must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void snapshot_sameState_ignoresNothingVisible() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("colorPrimary", 0xFF6750A4);
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("comment", 0xFF6A9955);
        ThemePacks.Snapshot a = buildSnapshot(colors, syntax, "all", null);
        ThemePacks.Snapshot b = buildSnapshot(new LinkedHashMap<>(colors),
                new LinkedHashMap<>(syntax), "all", null);
        assertTrue(a.sameState(a));
        assertTrue(a.sameState(b));
        assertFalse(a.sameState(null));

        // any visible change flips it
        Map<String, Integer> otherColors = new LinkedHashMap<>(colors);
        otherColors.put("colorPrimary", 0xFF111111);
        assertFalse(a.sameState(buildSnapshot(otherColors, syntax, "all", null)));
        Map<String, Integer> otherSyntax = new LinkedHashMap<>(syntax);
        otherSyntax.put("comment", 0xFF111111);
        assertFalse(a.sameState(buildSnapshot(colors, otherSyntax, "all", null)));
        assertFalse(a.sameState(buildSnapshot(colors, syntax, "files", null)));
        assertFalse(a.sameState(buildSnapshot(colors, null, "all", null)));
        assertFalse(a.sameState(buildSnapshot(colors, syntax, "all",
                new File("whatever-wallpaper"))));
        ThemePacks.Snapshot differentPack = new ThemePacks.Snapshot(
                "com.other.provider", "Example", Boolean.TRUE, 0xFF6750A4,
                0x66000000, colors, new LinkedHashMap<>(), null, null, syntax, "all");
        assertFalse(a.sameState(differentPack));
    }

    private static String read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
