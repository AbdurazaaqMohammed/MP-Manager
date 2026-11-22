package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * No-op refresh fast path decisions: pure metadata comparison and bounded
 * local byte comparison. Covers the four required hidden cases —
 * byte-identical content, a one-byte image change, changed metadata, and a
 * missing previous image (which must fail the fast path so the commit
 * repairs the store instead of skipping over the hole).
 */
public class ThemePackContentEqualsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static ThemePackStore.StoredSnapshot stored() {
        ThemePackStore.StoredSnapshot s = new ThemePackStore.StoredSnapshot();
        s.id = "example_night";
        s.name = "Example Night";
        s.dark = Boolean.TRUE;
        s.accent = 0xFF6750A4;
        s.scrim = 0x66000000;
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("colorPrimary", 0xFF6750A4);
        colors.put("wallpaper_scrim", 0x66000000);
        s.colors = colors;
        Map<String, Map<String, String>> strings = new LinkedHashMap<>();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("en-US", "Files");
        strings.put("tab.files", files);
        s.strings = strings;
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("comment", 0xFF6A9955);
        s.editorSyntax = syntax;
        s.wallpaperScope = "files";
        s.revision = 3L;
        s.schema = ThemePackStore.SCHEMA_V2;
        s.signer = "abc";
        s.wallpaperName = "wallpaper-123";
        return s;
    }

    private static ThemePackStore.StoredSnapshot copy(ThemePackStore.StoredSnapshot in) {
        ThemePackStore.StoredSnapshot out = new ThemePackStore.StoredSnapshot();
        out.signer = in.signer;
        out.id = in.id;
        out.name = in.name;
        out.dark = in.dark;
        out.accent = in.accent;
        out.scrim = in.scrim;
        out.colors = in.colors == null ? null : new LinkedHashMap<>(in.colors);
        if (in.strings != null) {
            out.strings = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> e : in.strings.entrySet()) {
                out.strings.put(e.getKey(), e.getValue() == null
                        ? null : new LinkedHashMap<>(e.getValue()));
            }
        }
        out.wallpaperName = in.wallpaperName;
        out.previewName = in.previewName;
        out.editorSyntax = in.editorSyntax == null
                ? null : new LinkedHashMap<>(in.editorSyntax);
        out.wallpaperScope = in.wallpaperScope;
        out.revision = in.revision;
        out.schema = in.schema;
        return out;
    }

    private File writeFile(String name, byte[] data) throws Exception {
        File file = folder.newFile(name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return file;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ---------- byte-identical: fast path may apply ----------

    @Test
    public void identicalMetadata_true() {
        assertTrue(ThemePackContentEquals.sameMetadata(stored(), stored()));
        assertTrue(ThemePackContentEquals.sameMetadata(stored(), copy(stored())));
    }

    @Test
    public void normalizationEquivalences_doNotFakeChanges() {
        ThemePackStore.StoredSnapshot a = stored();
        ThemePackStore.StoredSnapshot b = copy(stored());

        // null vs empty collections
        b.colors = new LinkedHashMap<>();
        b.strings = null;
        b.editorSyntax = null;
        ThemePackStore.StoredSnapshot empty = stored();
        empty.colors = null;
        empty.strings = new LinkedHashMap<>();
        empty.editorSyntax = new LinkedHashMap<>();
        assertTrue(ThemePackContentEquals.sameMetadata(empty, b));

        // scope: null, "files" and unknown spellings all mean "files"
        ThemePackStore.StoredSnapshot scopeNull = stored();
        scopeNull.wallpaperScope = null;
        assertTrue(ThemePackContentEquals.sameMetadata(a, scopeNull));
        // (unknown scopes never reach the store, but a hand-edited one must
        // not fake a change either)
        ThemePackStore.StoredSnapshot scopeBogus = stored();
        scopeBogus.wallpaperScope = "bogus";
        assertTrue(ThemePackContentEquals.sameMetadata(a, scopeBogus));

        // alias spelling of a syntax scope maps onto the same category
        ThemePackStore.StoredSnapshot aliasSyntax = stored();
        aliasSyntax.editorSyntax = new LinkedHashMap<>();
        aliasSyntax.editorSyntax.put("punctuation.definition.comment", 0xFF6A9955);
        assertTrue(ThemePackContentEquals.sameMetadata(a, aliasSyntax));

        // case/separator-insensitive color token spelling
        ThemePackStore.StoredSnapshot caseColor = stored();
        caseColor.colors = new LinkedHashMap<>();
        caseColor.colors.put("Color_Primary", 0xFF6750A4);
        caseColor.colors.put("WALLPAPER_SCRIM", 0x66000000);
        assertTrue(ThemePackContentEquals.sameMetadata(a, caseColor));

        // null vs empty name
        ThemePackStore.StoredSnapshot emptyName = stored();
        emptyName.name = "";
        ThemePackStore.StoredSnapshot nullName = stored();
        nullName.name = null;
        assertTrue(ThemePackContentEquals.sameMetadata(emptyName, nullName));
    }

    @Test
    public void storeBookkeeping_isNotAVisibleChange() {
        ThemePackStore.StoredSnapshot a = stored();
        ThemePackStore.StoredSnapshot b = copy(stored());
        b.revision = 99L;
        b.schema = ThemePackStore.SCHEMA_V1;
        b.signer = "different-signer";
        b.wallpaperName = "wallpaper-999"; // image names never count
        assertTrue(ThemePackContentEquals.sameMetadata(a, b));
    }

    @Test
    public void sameBytes_identicalFiles_true() throws Exception {
        File a = writeFile("a.bin", bytes("same-content-123"));
        File b = writeFile("b.bin", bytes("same-content-123"));
        assertTrue(ThemePackContentEquals.sameBytes(a, b));
        // larger than one 8 KiB chunk
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000; i++) big.append("0123456789ABCDEF");
        File c = writeFile("c.bin", bytes(big.toString()));
        File d = writeFile("d.bin", bytes(big.toString()));
        assertTrue(ThemePackContentEquals.sameBytes(c, d));
    }

    // ---------- one-byte change: fast path must not apply ----------

    @Test
    public void sameBytes_oneByteDifference_false() throws Exception {
        File a = writeFile("one.bin", bytes("same-content-123"));
        File b = writeFile("two.bin", bytes("same-content-124")); // last char
        assertFalse(ThemePackContentEquals.sameBytes(a, b));
    }

    @Test
    public void sameBytes_lengthDifference_false() throws Exception {
        File a = writeFile("len1.bin", bytes("same-content-123"));
        File b = writeFile("len2.bin", bytes("same-content-12"));
        assertFalse(ThemePackContentEquals.sameBytes(a, b));
    }

    @Test
    public void sameBytes_differentAfterFirstChunk_false() throws Exception {
        StringBuilder base = new StringBuilder();
        for (int i = 0; i < 3000; i++) base.append("0123456789ABCDEF");
        String big = base.toString();
        String changed = big.substring(0, big.length() - 1) + "G"; // last byte
        File a = writeFile("big1.bin", bytes(big));
        File b = writeFile("big2.bin", bytes(changed));
        assertFalse(ThemePackContentEquals.sameBytes(a, b));
    }

    // ---------- metadata changed: fast path must not apply ----------

    @Test
    public void changedMetadataFields_false() {
        ThemePackStore.StoredSnapshot b;
        String[] changedFields = {"id", "name", "dark", "accent", "scrim",
                "colors", "strings", "editorSyntax", "wallpaperScope"};
        for (String field : changedFields) {
            b = copy(stored());
            switch (field) {
                case "id": b.id = "renamed_id"; break;
                case "name": b.name = "Renamed"; break;
                case "dark": b.dark = Boolean.FALSE; break;
                case "accent": b.accent = 0xFF111111; break;
                case "scrim": b.scrim = 0x88000000; break;
                case "colors":
                    b.colors = new LinkedHashMap<>();
                    b.colors.put("colorPrimary", 0xFF111111);
                    break;
                case "strings":
                    b.strings = new LinkedHashMap<>();
                    Map<String, String> files = new LinkedHashMap<>();
                    files.put("en-US", "FilesX");
                    b.strings.put("tab.files", files);
                    break;
                case "editorSyntax":
                    b.editorSyntax = new LinkedHashMap<>();
                    b.editorSyntax.put("comment", 0xFF111111);
                    break;
                case "wallpaperScope": b.wallpaperScope = "all"; break;
                default: throw new AssertionError(field);
            }
            assertFalse("field " + field + " must count as a change",
                    ThemePackContentEquals.sameMetadata(stored(), b));
        }
    }

    @Test
    public void nullArguments_false() {
        assertFalse(ThemePackContentEquals.sameMetadata(null, stored()));
        assertFalse(ThemePackContentEquals.sameMetadata(stored(), null));
        assertFalse(ThemePackContentEquals.sameBytes(null, null));
    }

    // ---------- old/missing images: fast path must fail ----------

    @Test
    public void sameBytes_missingPreviousImage_false() throws Exception {
        File staged = writeFile("staged.bin", bytes("PNGDATA"));
        File missing = new File(folder.getRoot(), "wallpaper-gone");
        assertFalse(ThemePackContentEquals.sameBytes(staged, missing));
        assertFalse(ThemePackContentEquals.sameBytes(missing, staged));
    }

    @Test
    public void sameBytes_missingStagedImage_false() throws Exception {
        File previous = writeFile("previous.bin", bytes("PNGDATA"));
        File missing = new File(folder.getRoot(), "staged-gone");
        assertFalse(ThemePackContentEquals.sameBytes(missing, previous));
    }
}
