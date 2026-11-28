package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure (JVM) validation coverage for the LocalThemes frontend: key
 * namespace, draft -> store snapshot building (allowlists, name/launcher
 * bounds, scope normalization, image retention), staged-icon commit
 * filtering, magic-byte sniffing and the preset token sets. Real commits
 * (Context-bound) are covered by LocalThemesIntegrationTest.
 */
public class LocalThemesTest {

    private static LocalThemes.Draft draft() {
        LocalThemes.Draft draft = LocalThemes.newDraft("Test theme", true);
        draft.accent = 0xFF6750A4;
        draft.scrim = 0x66000000;
        return draft;
    }

    // ---------- key namespace ----------

    @Test
    public void newKey_isHex_andKeyOf_roundTrips() {
        String key = LocalThemes.newKey();
        assertTrue(key.matches("[0-9a-f]{32}"));
        String id = LocalThemes.idOf(key);
        assertEquals("local." + key, id);
        assertEquals(key, LocalThemes.keyOf(id));
        assertNull(LocalThemes.keyOf("com.example.provider"));
        assertNull(LocalThemes.keyOf(null));
        assertNull(LocalThemes.keyOf("local.short"));
        assertNull(LocalThemes.keyOf("local.NOTHEX"));
        // The namespace matches the backend's own guard exactly.
        assertTrue(ThemePacks.isLocalPackage(id));
        assertEquals(id, ThemePacks.localPackage(key));
    }

    // ---------- buildStored: allowlists and bounds ----------

    @Test
    public void buildStored_dropsUnknownTokens_andKeepsCanonical() {
        LocalThemes.Draft draft = draft();
        draft.colors.put("colorPrimary", 0xFF6750A4);
        draft.colors.put("COLOR_PRIMARY", 0xFF112233); // case-insensitive canonical
        draft.colors.put("not_a_token", 0xFF000000);
        draft.colors.put("colorPrimary_variant_of_evil", 0xFF000001);
        draft.editorSyntax.put("comment", 0xFF6A9955);
        draft.editorSyntax.put("string.quoted", 0xFFCC7832); // alias -> string
        draft.editorSyntax.put("banana", 0xFF000000);
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertNotNull(stored.colors.get("colorPrimary"));
        // The case variant canonicalizes to the same entry (last wins).
        assertEquals(Integer.valueOf(0xFF112233),
                stored.colors.get("colorPrimary"));
        assertFalse(stored.colors.containsKey("not_a_token"));
        assertFalse(stored.colors.containsKey("colorPrimary_variant_of_evil"));
        assertNotNull(stored.editorSyntax.get("comment"));
        // The alias canonicalized to the host category.
        assertTrue(stored.editorSyntax.containsKey("string"));
        assertFalse(stored.editorSyntax.containsKey("banana"));
    }

    @Test
    public void buildStored_sanitizesName_andBounds() {
        LocalThemes.Draft draft = draft();
        draft.name = "  My\u0001theme  ";
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals("Mytheme", stored.name);

        draft.name = "";
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertEquals("My theme", stored.name);

        draft.name = new String(new char[500]).replace('\0', 'a');
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertEquals(100, stored.name.length());
    }

    @Test
    public void buildStored_boundsLauncherIcon() {
        LocalThemes.Draft draft = draft();
        draft.launcherId = "crimson";
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals("crimson", stored.launcherIcon);

        draft.launcherId = new String(new char[65]).replace('\0', 'x');
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertNull(stored.launcherIcon);

        draft.launcherId = "crim\u0007son";
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertNull(stored.launcherIcon);
    }

    @Test
    public void buildStored_normalizesScope_andMirrorsScrim() {
        LocalThemes.Draft draft = draft();
        draft.wallpaperScope = "bogus";
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertNull(stored.wallpaperScope); // files collapses to null (default)
        assertNotNull(stored.colors.get("wallpaper_scrim")); // scrim mirrored

        draft.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_ALL;
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertEquals(ThemePackContracts.WALLPAPER_SCOPE_ALL, stored.wallpaperScope);

        draft.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_NONE;
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertEquals(ThemePackContracts.WALLPAPER_SCOPE_NONE, stored.wallpaperScope);
        assertNull(stored.wallpaperName); // scope none drops the wallpaper
    }

    @Test
    public void buildStored_localSnapshotsCarryNoSigner_andSchemaLatest() {
        LocalThemes.Draft draft = draft();
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertNull(stored.signer);
        assertEquals(ThemePackStore.SCHEMA_LATEST, stored.schema.intValue());
        assertEquals(1L, stored.revision.longValue());

        ThemePackStore.StoredSnapshot previous = new ThemePackStore.StoredSnapshot();
        previous.revision = 7L;
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), previous);
        assertEquals(8L, stored.revision.longValue());
    }

    @Test
    public void buildStored_darkFlagAndIdPersist() {
        LocalThemes.Draft draft = draft();
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals(Boolean.TRUE, stored.dark);
        assertEquals(0xFF6750A4, stored.accent.intValue());
        assertEquals(0x66000000, stored.scrim.intValue());
    }

    // ---------- image retention ----------

    @Test
    public void buildStored_wallpaperRetentionRules() {
        LocalThemes.Draft draft = draft();
        draft.retainedWallpaper = "wallpaper-123";
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals("wallpaper-123", stored.wallpaperName); // retained

        draft.stagedWallpaper = new File("staged-1"); // replacement wins
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertNull(stored.wallpaperName); // commit assigns a fresh name

        draft.stagedWallpaper = null;
        draft.wallpaperCleared = true; // cleared beats retained
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertNull(stored.wallpaperName);
    }

    @Test
    public void buildStored_wallpaperRetentionRejectsForeignNames() {
        LocalThemes.Draft draft = draft();
        draft.retainedWallpaper = "../escape/wallpaper-1";
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertNull(stored.wallpaperName);
    }

    @Test
    public void buildStored_iconRetentionRules() {
        LocalThemes.Draft draft = draft();
        draft.retainedIcons.put("folder", "icon-folder-42");
        draft.retainedIcons.put("file", "icon-file-43");
        draft.retainedIcons.put("banana", "icon-banana-1"); // non-contract key
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals("icon-folder-42", stored.iconNames.get("folder"));
        assertEquals("icon-file-43", stored.iconNames.get("file"));
        assertFalse(stored.iconNames.containsKey("banana"));

        draft.iconsCleared.put("folder", Boolean.TRUE);
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertFalse(stored.iconNames.containsKey("folder"));
        assertTrue(stored.iconNames.containsKey("file"));
    }

    @Test
    public void stagedIconsForCommit_filtersClearedAndUnknownKeys() {
        LocalThemes.Draft draft = draft();
        File staged = new File("staged-2");
        draft.stagedIcons.put("folder", staged);
        draft.stagedIcons.put("banana", new File("staged-3"));
        draft.iconsCleared.put("folder", Boolean.TRUE);
        Map<String, File> out = LocalThemes.stagedIconsForCommit(draft);
        assertFalse(out.containsKey("folder")); // cleared staged icon dropped
        assertFalse(out.containsKey("banana"));
        assertTrue(out.isEmpty());

        // Fresh draft: every contract kind and variant is committable.
        LocalThemes.Draft fresh = draft();
        for (String kind : ThemePackContracts.ICON_KINDS) {
            fresh.stagedIcons.put(kind, staged);
            fresh.stagedIcons.put(kind + "_light", staged);
            fresh.stagedIcons.put(kind + "_dark", staged);
        }
        fresh.iconsCleared.put("folder", Boolean.TRUE); // per-key clear
        out = LocalThemes.stagedIconsForCommit(fresh);
        for (String kind : ThemePackContracts.ICON_KINDS) {
            assertTrue(kind, out.containsKey(kind + "_light"));
            assertTrue(kind, out.containsKey(kind + "_dark"));
        }
        assertFalse("the per-key clear still applies to the base key",
                out.containsKey("folder"));
        assertEquals(ThemePackContracts.ICON_KINDS.length * 3 - 1, out.size());
    }

    // ---------- image slots cover the whole contract ----------

    @Test
    public void imageSlots_coverEveryContractIconKind_andVariants() {
        for (String kind : ThemePackContracts.ICON_KINDS) {
            LocalThemes.ImageSlot base = LocalThemes.ImageSlot.iconSlot(kind);
            assertNotNull("missing slot for kind " + kind, base);
            assertEquals(kind, base.iconKey);
            assertTrue(LocalThemes.ImageSlot.isBaseIconKey(kind));
            assertTrue(base.maxBytes <= ThemePackContracts.MAX_ICON_BYTES);

            // Variants resolve to their BASE slot (shared caps); the exact
            // variant key is preserved by the key-based staging/clearing API.
            LocalThemes.ImageSlot light = LocalThemes.ImageSlot.iconSlot(kind + "_light");
            LocalThemes.ImageSlot dark = LocalThemes.ImageSlot.iconSlot(kind + "_dark");
            assertNotNull("missing light slot for " + kind, light);
            assertNotNull("missing dark slot for " + kind, dark);
            assertEquals("variant must resolve to the base slot", kind, light.iconKey);
            assertEquals(kind, dark.iconKey);
            assertEquals(base.maxBytes, light.maxBytes);
            assertEquals(base.maxDimension, light.maxDimension);
            assertSame(base, light);
        }
        // The enum constants agree with the contract one-to-one.
        int iconConstants = 0;
        for (LocalThemes.ImageSlot slot : LocalThemes.ImageSlot.values()) {
            if (slot.iconKey != null) iconConstants++;
        }
        assertEquals(ThemePackContracts.ICON_KINDS.length, iconConstants);
        assertFalse(LocalThemes.ImageSlot.isBaseIconKey("folder_light"));
        assertFalse(LocalThemes.ImageSlot.isBaseIconKey("banana"));
        assertNull(LocalThemes.ImageSlot.iconSlot("banana"));
        assertNull(LocalThemes.ImageSlot.iconSlot("folder_middle"));
        assertNull(LocalThemes.ImageSlot.iconSlot("wallpaper"));
        assertNull(LocalThemes.ImageSlot.iconSlot(null));
    }

    @Test
    public void stageAndClear_trackExactKeys_baseAndVariants() {
        LocalThemes.Draft draft = draft();
        File baseFile = new File("staged-folder");
        File lightFile = new File("staged-folder-light");
        File darkFile = new File("staged-folder-dark");

        // Key-exact bookkeeping: the base key and both variants live side by
        // side in the draft (backend validation is integration-covered).
        draft.stagedIcons.put("folder", baseFile);
        draft.stagedIcons.put("folder_light", lightFile);
        draft.stagedIcons.put("folder_dark", darkFile);
        assertTrue(draft.hasIcon("folder"));
        assertTrue(draft.hasIcon("folder_light"));
        assertTrue(draft.hasIcon("folder_dark"));
        assertEquals(baseFile, draft.stagedIconFile("folder"));
        assertEquals(lightFile, draft.stagedIconFile("folder_light"));
        assertEquals(darkFile, draft.stagedIconFile("folder_dark"));
        assertTrue(draft.isDirty());

        // Clearing the base key leaves both variants untouched.
        LocalThemes.clearImage(draft, LocalThemes.ImageSlot.FOLDER_ICON);
        assertFalse(draft.hasIcon("folder"));
        assertTrue(draft.hasIcon("folder_light"));
        assertTrue(draft.hasIcon("folder_dark"));

        // Clearing one variant leaves the base and the sibling variant.
        LocalThemes.clearIcon(draft, "folder_light");
        assertFalse(draft.hasIcon("folder_light"));
        assertTrue(draft.hasIcon("folder_dark"));
        assertNull(draft.stagedIconFile("folder_light"));
        assertNull(draft.retainedIconName("folder_light"));
        assertEquals(darkFile, draft.stagedIconFile("folder_dark"));

        // Unknown keys are rejected by the key-based API.
        LocalThemes.clearIcon(draft, "banana");
        LocalThemes.clearIcon(draft, null);
        assertFalse(draft.iconsCleared.containsKey("banana"));
        assertTrue(draft.iconsCleared.containsKey("folder"));
        assertTrue(draft.iconsCleared.containsKey("folder_light"));
        assertFalse("the sibling variant was never cleared",
                draft.iconsCleared.containsKey("folder_dark"));
    }

    @Test
    public void buildStored_iconRetentionRules_perTypeAndVariants() {
        LocalThemes.Draft draft = draft();
        draft.retainedIcons.put("apk", "icon-apk-7");
        draft.retainedIcons.put("parent_folder", "icon-parent_folder-8");
        draft.retainedIcons.put("folder_light", "icon-folder_light-9");
        draft.retainedIcons.put("folder_dark", "icon-folder_dark-10");
        draft.retainedIcons.put("certificate", "icon-certificate-11");
        draft.retainedIcons.put("banana", "icon-banana-1"); // non-contract key
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals("icon-apk-7", stored.iconNames.get("apk"));
        assertEquals("icon-parent_folder-8", stored.iconNames.get("parent_folder"));
        assertEquals("icon-folder_light-9", stored.iconNames.get("folder_light"));
        assertEquals("icon-folder_dark-10", stored.iconNames.get("folder_dark"));
        assertEquals("icon-certificate-11", stored.iconNames.get("certificate"));
        assertFalse(stored.iconNames.containsKey("banana"));

        // Per-key clearing only touches its own key.
        draft.iconsCleared.put("folder_light", Boolean.TRUE);
        draft.stagedIcons.put("certificate", new File("staged-cert")); // staged wins
        stored = LocalThemes.buildStored(draft, LocalThemes.newKey(), null);
        assertFalse(stored.iconNames.containsKey("folder_light"));
        assertFalse(stored.iconNames.containsKey("certificate")); // staged, commit assigns
        assertTrue(stored.iconNames.containsKey("folder_dark"));
        assertTrue(stored.iconNames.containsKey("apk"));
    }

    // ---------- draft image slots ----------

    @Test
    public void draftImageSlotHelpers_trackState() {
        LocalThemes.Draft draft = draft();
        assertFalse(draft.hasWallpaper());
        assertFalse(draft.hasIcon("folder"));

        draft.retainedWallpaper = "wallpaper-9";
        draft.retainedIcons.put("folder", "icon-folder-9");
        assertTrue(draft.hasWallpaper());
        assertTrue(draft.hasIcon("folder"));
        assertTrue(draft.hasIcon(LocalThemes.ImageSlot.FOLDER_ICON));

        LocalThemes.clearImage(draft, LocalThemes.ImageSlot.FOLDER_ICON);
        assertFalse(draft.hasIcon("folder"));
        assertTrue(draft.hasWallpaper());

        LocalThemes.clearImage(draft, LocalThemes.ImageSlot.WALLPAPER);
        assertFalse(draft.hasWallpaper());
        assertTrue(draft.isDirty());

        // Preview surfaces never leak staged files after clear.
        assertNull(draft.stagedIconFile("folder"));
        assertNull(draft.retainedIconName("folder"));
        assertNull(draft.retainedWallpaperName());
    }

    // ---------- exact-key icon staging (variant preserving) ----------

    @Test
    public void applyStagedIcon_preservesExactVariantKey() {
        LocalThemes.Draft draft = draft();
        File stagedVariant = new File("staged-java-dark");
        LocalThemes.applyStagedIcon(draft, "java_dark", stagedVariant);
        assertSame(stagedVariant, draft.stagedIcons.get("java_dark"));
        assertTrue(draft.hasIcon("java_dark"));
        assertFalse(draft.hasIcon("java")); // base slot untouched
        assertNotNull(draft.key);

        // Re-staging the same key replaces it (and only it).
        File replacement = new File("staged-java-dark-2");
        LocalThemes.applyStagedIcon(draft, "java_dark", replacement);
        assertSame(replacement, draft.stagedIcons.get("java_dark"));
        assertEquals(1, draft.stagedIcons.size());

        // Clear-then-stage un-tombstones the key.
        LocalThemes.clearIcon(draft, "java_dark");
        assertFalse(draft.hasIcon("java_dark"));
        LocalThemes.applyStagedIcon(draft, "java_dark", stagedVariant);
        assertTrue(draft.hasIcon("java_dark"));
        assertFalse(draft.iconsCleared.containsKey("java_dark"));

        // Unknown keys never land in the draft.
        LocalThemes.applyStagedIcon(draft, "banana", new File("staged-x"));
        assertFalse(draft.stagedIcons.containsKey("banana"));
        LocalThemes.applyStagedIcon(draft, null, new File("staged-y"));
        assertNull(draft.stagedIconFile(null));
    }

    @Test
    public void clearIcon_tombstonesExactVariantKeyOnly() {
        LocalThemes.Draft draft = draft();
        draft.retainedIcons.put("java", "icon-java-1");
        draft.retainedIcons.put("java_dark", "icon-java_dark-2");
        draft.retainedIcons.put("js_light", "icon-js_light-3");
        draft.stagedIcons.put("xml", new File("staged-xml"));

        LocalThemes.clearIcon(draft, "java_dark");
        assertFalse(draft.hasIcon("java_dark"));
        assertNull(draft.retainedIconName("java_dark"));
        assertTrue(draft.iconsCleared.containsKey("java_dark"));
        // Base kind and other variants are untouched.
        assertTrue(draft.hasIcon("java"));
        assertEquals("icon-java-1", draft.retainedIconName("java"));
        assertTrue(draft.hasIcon("js_light"));
        assertTrue(draft.hasIcon("xml"));
        assertNotNull(draft.stagedIconFile("xml"));

        // Staged entries are dropped too, and the draft is dirty.
        LocalThemes.clearIcon(draft, "xml");
        assertFalse(draft.hasIcon("xml"));
        assertNull(draft.stagedIconFile("xml"));
        assertTrue(draft.isDirty());

        // Unknown keys are ignored, not tombstoned.
        LocalThemes.clearIcon(draft, "banana");
        assertFalse(draft.iconsCleared.containsKey("banana"));
        LocalThemes.clearIcon(draft, null);
    }

    @Test
    public void buildStored_keepsCodeIconVariantKeys() {
        LocalThemes.Draft draft = draft();
        draft.retainedIcons.put("java_dark", "icon-java_dark-11");
        draft.retainedIcons.put("js_light", "icon-js_light-12");
        draft.retainedIcons.put("python", "icon-python-13");
        draft.retainedIcons.put("banana", "icon-banana-14");
        ThemePackStore.StoredSnapshot stored = LocalThemes.buildStored(
                draft, LocalThemes.newKey(), null);
        assertEquals("icon-java_dark-11", stored.iconNames.get("java_dark"));
        assertEquals("icon-js_light-12", stored.iconNames.get("js_light"));
        assertEquals("icon-python-13", stored.iconNames.get("python"));
        assertFalse(stored.iconNames.containsKey("banana"));

        // Staged variant icons pass the commit filter under their exact key.
        File staged = new File("staged-kotlin-dark");
        draft.stagedIcons.put("kotlin_dark", staged);
        Map<String, File> out = LocalThemes.stagedIconsForCommit(draft);
        assertSame(staged, out.get("kotlin_dark"));
        assertFalse(out.containsKey("banana"));
    }

    @Test
    public void stageIconStream_rejectsUnknownKeys_andLeavesDraftUntouched() {
        LocalThemes.Draft draft = draft();
        assertFalse(LocalThemes.stageIconStream(null, draft, "java", null));
        assertFalse(LocalThemes.stageIconStream(null, draft, null, null));
        // Unknown keys are rejected by the allowlist guard before staging.
        assertFalse(LocalThemes.stageIconStream(null, draft, "java_extra",
                new java.io.ByteArrayInputStream(new byte[0])));
        assertFalse(LocalThemes.stageIconStream(null, draft, "wallpaper",
                new java.io.ByteArrayInputStream(new byte[0])));
        // No partial state: key assignment only ever happens on success.
        assertNull(draft.key);
        assertTrue(draft.stagedIcons.isEmpty());
    }

    @Test
    public void decodeEmbeddedImages_acceptsCodeIconVariants_only() {
        JsonObject images = new JsonObject();
        images.addProperty("java_dark", base64(png(20)));
        images.addProperty("js_light", base64(png(21)));
        images.addProperty("smali", base64(png(22)));
        images.addProperty("kotlin_dark", base64(jpeg(23)));
        images.addProperty("banana", base64(png(24)));
        images.addProperty("java_extra", "not-even-base64!");
        LocalThemes.EmbeddedImages decoded = LocalThemes.decodeEmbeddedImages(images);
        assertNull(decoded.error);
        assertTrue(decoded.payloads.containsKey("java_dark"));
        assertTrue(decoded.payloads.containsKey("js_light"));
        assertTrue(decoded.payloads.containsKey("smali"));
        assertTrue(decoded.payloads.containsKey("kotlin_dark"));
        assertFalse(decoded.payloads.containsKey("banana"));
        assertFalse(decoded.payloads.containsKey("java_extra"));
        assertEquals(4, decoded.payloads.size());
    }

    // ---------- presets ----------

    @Test
    public void presets_useCanonicalHostTokens_only() {
        for (boolean dark : new boolean[]{false, true}) {
            Map<String, Integer> colors = LocalThemes.presetColors(dark);
            assertFalse(colors.isEmpty());
            for (String token : colors.keySet()) {
                assertNotNull("preset token not allowlisted: " + token,
                        ThemePackParser.canonicalColorToken(token));
            }
        }
    }

    @Test
    public void draftFromSnapshot_seedsFromPack() {
        LocalThemes.Draft draft = LocalThemes.draftFromSnapshot(null, null);
        // No active pack in a bare JVM call: falls back to a preset draft.
        assertFalse(draft.colors.isEmpty());
        assertNull(draft.key);
    }

    // ---------- magic-byte sniffing ----------

    @Test
    public void imageMagic_acceptsPngJpeg_only() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 0};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] gif = {'G', 'I', 'F', '8'};
        byte[] short1 = {1};
        assertTrue(LocalThemes.hasSupportedImageMagic(png));
        assertTrue(LocalThemes.hasSupportedImageMagic(jpeg));
        assertFalse(LocalThemes.hasSupportedImageMagic(gif));
        assertFalse(LocalThemes.hasSupportedImageMagic(short1));
        assertFalse(LocalThemes.hasSupportedImageMagic(null));
    }

    // ---------- export document ----------

    @Test
    public void exportDocument_shapeIsStable() throws Exception {
        LocalThemes.LocalTheme theme = new LocalThemes.LocalTheme(
                "local.abc123", "Exported", Boolean.TRUE, 0xFF6750A4,
                null, single("colorPrimary", 0xFF6750A4),
                single("comment", 0xFF6A9955),
                ThemePackContracts.WALLPAPER_SCOPE_FILES,
                null, null, java.util.Collections.emptyMap(),
                "violet", 3L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(LocalThemes.exportTheme(null, theme, out));
        String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"format\":1"));
        assertTrue(json.contains("\"type\":\"mp-manager-theme\""));
        assertTrue(json.contains("\"name\":\"Exported\""));
        assertTrue(json.contains("\"dark\":true"));
        assertTrue(json.contains("\"wallpaperScope\":\"files\""));
        assertTrue(json.contains("\"launcherId\":\"violet\""));
        assertTrue(json.contains("\"colorPrimary\":\"#FF6750A4\""));
        assertTrue(json.contains("\"editorSyntax\""));
        assertTrue(json.contains("\"images\""));
    }

    @Test
    public void exportDocument_embedsEveryIconKeyIncludingVariants() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("mtheme").toFile();
        Map<String, File> iconFiles = new LinkedHashMap<>();
        for (String key : new String[]{"folder", "parent_folder", "apk",
                "folder_light", "apk_dark", "certificate"}) {
            File png = new File(dir, "icon-" + key + "-1");
            writeTinyPng(png);
            iconFiles.put(key, png);
        }
        // A non-contract key must never leak into the document.
        iconFiles.put("banana", new File(dir, "icon-banana-1"));
        LocalThemes.LocalTheme theme = new LocalThemes.LocalTheme(
                "local.abc123", "IconExport", Boolean.FALSE, null,
                null, single("colorPrimary", 0xFF6750A4),
                new LinkedHashMap<>(),
                ThemePackContracts.WALLPAPER_SCOPE_FILES,
                null, null, iconFiles, null, 3L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(LocalThemes.exportTheme(null, theme, out));
        String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        JsonObject images = gsonImages(json);
        assertTrue(images.has("folder"));
        assertTrue(images.has("parent_folder"));
        assertTrue(images.has("apk"));
        assertTrue(images.has("folder_light"));
        assertTrue(images.has("apk_dark"));
        assertTrue(images.has("certificate"));
        assertFalse("non-contract keys must not be exported", images.has("banana"));
        assertEquals(6, images.size());
        // Every embedded payload decodes to the same tiny PNG.
        for (Map.Entry<String, JsonElement> e : images.entrySet()) {
            byte[] decoded = java.util.Base64.getDecoder()
                    .decode(e.getValue().getAsString());
            assertTrue(LocalThemes.hasSupportedImageMagic(decoded));
        }
        for (File f : iconFiles.values()) f.delete();
        dir.delete();
    }

    // ---------- pure embedded image validation ----------

    @Test
    public void embeddedImages_acceptAllowlistedKeysAndDropUnknown() {
        JsonObject images = new JsonObject();
        images.addProperty("folder", base64(png(10)));
        images.addProperty("parent_folder", base64(png(11)));
        images.addProperty("apk", base64(jpeg(12)));
        images.addProperty("apk_light", base64(png(13)));
        images.addProperty("folder_dark", base64(png(14)));
        images.addProperty("banana", base64(png(15))); // unknown -> dropped
        images.addProperty("wallpaper", base64(png(16)));
        images.addProperty("preview", base64(png(17)));
        LocalThemes.EmbeddedImages result = LocalThemes.decodeEmbeddedImages(images);
        assertNull(result.error);
        assertEquals(7, result.payloads.size()); // banana dropped, rest kept
        assertTrue(result.payloads.containsKey("apk_light"));
        assertTrue(result.payloads.containsKey("folder_dark"));
        assertTrue(result.payloads.containsKey("wallpaper"));
        assertTrue(result.payloads.containsKey("preview"));
        assertFalse(result.payloads.containsKey("banana"));
    }

    @Test
    public void embeddedImages_rejectBadPayloads() {
        // Bad magic.
        assertEmbeddedRejected(singleImage("folder", base64("GIF89a")));
        // Truncated base64.
        assertEmbeddedRejected(singleImage("folder", "not base64!!"));
        // Empty payload.
        assertEmbeddedRejected(singleImage("folder", ""));
        // Non-string (number/boolean/object) values.
        JsonObject numeric = new JsonObject();
        numeric.addProperty("folder", 12345);
        assertEmbeddedRejected(numeric);
        JsonObject bool = new JsonObject();
        bool.addProperty("folder", true);
        assertEmbeddedRejected(bool);
        JsonObject object = new JsonObject();
        object.add("folder", new JsonObject());
        assertEmbeddedRejected(object);
        // Icon over the per-slot cap (icon cap is 256KB).
        assertEmbeddedRejected(singleImage("folder",
                base64(oversized((int) (ThemePackContracts.MAX_ICON_BYTES + 1)))));
        // Wallpaper may be big; icons may not. 1MB with PNG magic is a valid
        // wallpaper payload but far over the icon cap.
        LocalThemes.EmbeddedImages big = LocalThemes.decodeEmbeddedImages(
                singleImage("wallpaper", base64(oversized(1024 * 1024))));
        assertNull("1MB wallpaper must pass", big.error);
        // Too many images: wallpaper + preview + 3 per kind.
        JsonObject tooMany = new JsonObject();
        for (int i = 0; i <= LocalThemes.MAX_EMBEDDED_IMAGES; i++) {
            tooMany.addProperty("kind" + i, base64(png(20)));
        }
        assertEmbeddedRejected(tooMany);
    }

    @Test
    public void embeddedImages_enforceTotalIconByteBudget() {
        // Enough allowlisted keys to exceed the budget: base kinds first,
        // then _light/_dark variants (75 total).
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String kind : ThemePackContracts.ICON_KINDS) {
            keys.add(kind);
            keys.add(kind + "_light");
            keys.add(kind + "_dark");
        }
        // 34 x 250KB valid-PNG-magic payloads = 8.5MB > the 8MB icon budget.
        JsonObject images = new JsonObject();
        for (int i = 0; i < 34; i++) {
            images.addProperty(keys.get(i), base64(oversized(250 * 1024)));
        }
        assertEmbeddedRejected(images);

        // Just under the budget passes: 32 x 250KB = 8.0MB (32 x 256000 =
        // 8192000 < 8388608).
        JsonObject fits = new JsonObject();
        for (int i = 0; i < 32; i++) {
            fits.addProperty(keys.get(i), base64(oversized(250 * 1024)));
        }
        LocalThemes.EmbeddedImages ok = LocalThemes.decodeEmbeddedImages(fits);
        assertNull("32 x 250KB icons must fit the total budget", ok.error);
        assertEquals(32, ok.payloads.size());
    }

    @Test
    public void documentCaps_stayAboveWorstLegalPayload() {
        // Worst legal document: 8MB wallpaper + 8MB total icons + 256KB
        // preview, all base64-inflated, plus JSON skeleton headroom.
        long inflated = (ThemePackContracts.MAX_WALLPAPER_BYTES
                + ThemePackContracts.MAX_ICONS_TOTAL_BYTES
                + ThemePackContracts.MAX_PREVIEW_BYTES) / 3 * 4;
        assertTrue(LocalThemes.MAX_DOCUMENT_BYTES >= inflated + 64 * 1024);
        // The cap itself stays bounded.
        assertTrue(LocalThemes.MAX_DOCUMENT_BYTES <= 64L * 1024 * 1024);
        // Backward compatibility: legacy four-key documents fit easily.
        assertTrue(LocalThemes.MAX_EMBEDDED_IMAGES >= 4);
        assertEquals(2 + 3 * ThemePackContracts.ICON_KINDS.length,
                LocalThemes.MAX_EMBEDDED_IMAGES);
    }

    // ---------- persist-level commit inputs ----------

    @Test
    public void stagedWallpaperForCommit_dropsWhenScopeNone() {
        LocalThemes.Draft draft = draft();
        File staged = new File("staged-wallpaper");
        draft.stagedWallpaper = staged;

        // Scope "none": buildStored leaves wallpaperName null; the commit
        // input must not re-introduce the wallpaper through a staged file.
        draft.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_NONE;
        assertNull(LocalThemes.stagedWallpaperForCommit(draft));

        // Scope "all" and the default keep the staged file.
        draft.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_ALL;
        assertEquals(staged, LocalThemes.stagedWallpaperForCommit(draft));
        draft.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_FILES;
        assertEquals(staged, LocalThemes.stagedWallpaperForCommit(draft));
        // Unknown scope normalizes to "files" exactly like buildStored.
        draft.wallpaperScope = "bogus";
        assertEquals(staged, LocalThemes.stagedWallpaperForCommit(draft));

        // Nothing staged: always null.
        draft.stagedWallpaper = null;
        draft.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_ALL;
        assertNull(LocalThemes.stagedWallpaperForCommit(draft));
        assertNull(LocalThemes.stagedWallpaperForCommit(null));
    }

    // ---------- abandoned staged-file janitor ----------

    @Test
    public void sweepAbandonedStaged_removesOnlyOldStagedFilesInLocalDirs()
            throws Exception {
        File filesDir = java.nio.file.Files.createTempDirectory("sweep").toFile();
        File root = new File(filesDir, "theme_packs");
        File local = new File(root, "local.0123456789abcdef");
        File foreign = new File(root, "com.example.provider");
        assertTrue(local.mkdirs());
        assertTrue(foreign.mkdirs());

        File oldStaged = new File(local, "staged-1000");
        File newStaged = new File(local, "staged-2000");
        File oldWallpaper = new File(local, "wallpaper-1000");
        File foreignStaged = new File(foreign, "staged-1000");
        writeTinyPng(oldStaged);
        writeTinyPng(newStaged);
        writeTinyPng(oldWallpaper);
        writeTinyPng(foreignStaged);
        long ancient = System.currentTimeMillis() - 2 * LocalThemes.STAGED_MAX_AGE_MS;
        assertTrue(oldStaged.setLastModified(ancient));
        assertTrue(oldWallpaper.setLastModified(ancient));
        assertTrue(foreignStaged.setLastModified(ancient));

        LocalThemes.sweepAbandonedStaged(filesDir);

        assertFalse("old staged file in a local dir is swept", oldStaged.exists());
        assertTrue("fresh staged file stays", newStaged.exists());
        assertTrue("stored versioned assets are never touched", oldWallpaper.exists());
        assertTrue("non-local pack dirs are never touched", foreignStaged.exists());

        // Missing root must be a no-op, never a throw.
        LocalThemes.sweepAbandonedStaged(
                new File(filesDir, "does-not-exist").getParentFile());

        deleteRecursively(root);
        filesDir.delete();
    }

    @Test
    public void sweepAbandonedStaged_ignoresDirsAndNullRoot() throws Exception {
        File filesDir = java.nio.file.Files.createTempDirectory("sweep2").toFile();
        File root = new File(filesDir, "theme_packs");
        File local = new File(root, "local.aaaaaaaa");
        assertTrue(local.mkdirs());
        // A DIRECTORY named like a staged file is never deleted.
        File stagedDir = new File(local, "staged-3000");
        assertTrue(stagedDir.mkdirs());
        File marker = new File(stagedDir, "keep");
        writeTinyPng(marker);
        assertTrue(marker.setLastModified(
                System.currentTimeMillis() - 2 * LocalThemes.STAGED_MAX_AGE_MS));

        LocalThemes.sweepAbandonedStaged(filesDir);
        assertTrue("directories are never deleted", stagedDir.isDirectory());
        assertTrue(marker.exists());
        // Null/missing roots are no-ops, never throws (File(null, ...) is
        // caught by the janitor).
        LocalThemes.sweepAbandonedStaged(null);
        assertTrue("null root must not disturb anything", stagedDir.isDirectory());
        deleteRecursively(root);
        filesDir.delete();
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    // ---------- embedded helpers ----------

    private static JsonObject singleImage(String key, String base64) {
        JsonObject images = new JsonObject();
        images.addProperty(key, base64);
        return images;
    }

    private static void assertEmbeddedRejected(JsonObject images) {
        LocalThemes.EmbeddedImages result = LocalThemes.decodeEmbeddedImages(images);
        assertNotNull("expected rejection", result.error);
        assertTrue(result.payloads.isEmpty());
    }

    /** PNG-magic bytes of the given size (payload content is irrelevant to
     *  the pure validator; real decode revalidation happens at staging). */
    private static byte[] oversized(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        return bytes;
    }

    private static byte[] png(int seed) {
        byte[] bytes = oversized(32 + (seed % 8));
        return bytes;
    }

    private static byte[] jpeg(int seed) {
        byte[] bytes = new byte[32 + (seed % 8)];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        return bytes;
    }

    private static String base64(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private static String base64(String raw) {
        return base64(raw.getBytes(StandardCharsets.US_ASCII));
    }

    /** Writes a minimal 1x1 PNG (real signature) to a file. */
    private static void writeTinyPng(File file) throws Exception {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write(header);
            // IHDR chunk minimal fake body: validators here only check magic.
            out.write(new byte[64]);
        }
    }

    private static JsonObject gsonImages(String json) {
        JsonElement root = com.google.gson.JsonParser.parseString(json);
        return root.getAsJsonObject().getAsJsonObject("images");
    }

    private static Map<String, Integer> single(String key, int value) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
