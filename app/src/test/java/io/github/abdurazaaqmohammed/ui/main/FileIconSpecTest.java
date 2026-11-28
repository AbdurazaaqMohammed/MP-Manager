package io.github.abdurazaaqmohammed.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.abdurazaaqmohammed.plugins.themes.ThemePackContracts;

import org.junit.Test;

import java.util.List;

/** Verifies per-language icon classification and precedence over generic text fallback. */
public class FileIconSpecTest {

    @Test
    public void classifiesCodeExtensionsDistinctly() {
        assertEquals(FileIconSpec.JAVA, FileIconSpec.forPath("/src/Main.java", false));
        assertEquals(FileIconSpec.KOTLIN, FileIconSpec.forPath("/src/App.kt", false));
        assertEquals(FileIconSpec.KOTLIN, FileIconSpec.forPath("/build.gradle.kts", false));
        assertEquals(FileIconSpec.XML, FileIconSpec.forPath("/res/layout/main.xml", false));
        assertEquals(FileIconSpec.XML, FileIconSpec.forPath("/AndroidManifest.axml", false));
        assertEquals(FileIconSpec.XML, FileIconSpec.forPath("/index.html", false));
        assertEquals(FileIconSpec.JSON, FileIconSpec.forPath("/package.json", false));
        assertEquals(FileIconSpec.JSON, FileIconSpec.forPath("/config.yaml", false));
        assertEquals(FileIconSpec.PYTHON, FileIconSpec.forPath("/tool.py", false));
        assertEquals(FileIconSpec.SMALI, FileIconSpec.forPath("/smali/MainActivity.smali", false));
        assertEquals(FileIconSpec.JS, FileIconSpec.forPath("/app.js", false));
        assertEquals(FileIconSpec.JS, FileIconSpec.forPath("/index.tsx", false));
    }

    @Test
    public void keepsSpecificCategoriesAndPlainTextPrecedenceClean() {
        assertEquals(FileIconSpec.SHEET, FileIconSpec.forPath("/export.csv", false));
        assertEquals(FileIconSpec.SCRIPT, FileIconSpec.forPath("/run.sh", false));
        assertEquals(FileIconSpec.TEXT, FileIconSpec.forPath("/notes.txt", false));
        assertEquals(FileIconSpec.TEXT, FileIconSpec.forPath("/README.md", false));
        assertNull(FileIconSpec.codeSpecForExt(".txt"));
    }

    @Test
    public void exposesAllCodeSpecsInPickerList() {
        List<FileIconSpec> all = FileIconSpec.all();
        assertTrue(all.contains(FileIconSpec.JAVA));
        assertTrue(all.contains(FileIconSpec.KOTLIN));
        assertTrue(all.contains(FileIconSpec.XML));
        assertTrue(all.contains(FileIconSpec.JSON));
        assertTrue(all.contains(FileIconSpec.PYTHON));
        assertTrue(all.contains(FileIconSpec.SMALI));
        assertTrue(all.contains(FileIconSpec.JS));
    }

    @Test
    public void codeSpecsUseTheirOwnSemanticIconKeys() {
        assertEquals("java", FileIconSpec.JAVA.iconKey());
        assertEquals("kotlin", FileIconSpec.KOTLIN.iconKey());
        assertEquals("xml", FileIconSpec.XML.iconKey());
        assertEquals("json", FileIconSpec.JSON.iconKey());
        assertEquals("python", FileIconSpec.PYTHON.iconKey());
        assertEquals("smali", FileIconSpec.SMALI.iconKey());
        assertEquals("js", FileIconSpec.JS.iconKey());
        // The generic file spec remains the only intentional "file" key.
        assertEquals("file", FileIconSpec.FILE.iconKey());
        assertEquals("folder", FileIconSpec.FOLDER.iconKey());
    }

    /** Every spec key must be an allowlisted contract icon kind. */
    @Test
    public void allIconKeysAreAllowlistedContractKinds() {
        for (FileIconSpec spec : FileIconSpec.all()) {
            assertTrue("iconKey not allowlisted: " + spec.iconKey(),
                    ThemePackContracts.isIconKey(spec.iconKey()));
        }
    }

    /** Bidirectional contract coverage: each base kind except parent_folder
     *  (overridden through parentDrawable) maps to exactly one spec. */
    @Test
    public void everyContractKindHasExactlyOneSpec() {
        List<FileIconSpec> all = FileIconSpec.all();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FileIconSpec spec : all) {
            assertTrue("duplicate iconKey: " + spec.iconKey(), seen.add(spec.iconKey()));
        }
        for (String kind : ThemePackContracts.ICON_KINDS) {
            if ("parent_folder".equals(kind)) continue;
            assertTrue("contract kind has no spec: " + kind, seen.contains(kind));
        }
        assertEquals(ThemePackContracts.ICON_KINDS.length - 1, seen.size());
    }
}
