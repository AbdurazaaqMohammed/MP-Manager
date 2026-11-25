package io.github.abdurazaaqmohammed.feature.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.abdurazaaqmohammed.ui.common.theme.ThemePackEditorColors;

public class StructuredCodeFormatterAndConfigsTest {

    private static File resolveAssetsDir() {
        File direct = new File("app/src/main/assets");
        if (direct.isDirectory()) return direct;
        File moduleRelative = new File("src/main/assets");
        if (moduleRelative.isDirectory()) return moduleRelative;
        throw new IllegalStateException("Cannot locate app/src/main/assets from " + new File(".").getAbsolutePath());
    }

    @Test
    public void formatJsonPreservesKeyOrderEscapesAndEmptyContainers() {
        String raw = "{\"z\":\"{a:[1,2]}\",\"b\":{\"empty\":{},\"arr\":[],\"nested\":[1,2,3]},\"n\":1.00}\n";
        String expected = ""
                + "{\n"
                + "    \"z\": \"{a:[1,2]}\",\n"
                + "    \"b\": {\n"
                + "        \"empty\": {},\n"
                + "        \"arr\": [],\n"
                + "        \"nested\": [\n"
                + "            1,\n"
                + "            2,\n"
                + "            3\n"
                + "        ]\n"
                + "    },\n"
                + "    \"n\": 1.00\n"
                + "}\n";

        String formatted = StructuredCodeFormatter.formatJson(raw);
        assertEquals(expected, formatted);
        assertEquals(expected, StructuredCodeFormatter.formatJson(formatted));
    }

    @Test
    public void formatJsonLeavesBrokenInputAndWhitespaceUnchanged() {
        assertEquals("{\"unclosed\": [1, 2", StructuredCodeFormatter.formatJson("{\"unclosed\": [1, 2"));
        assertEquals("   ", StructuredCodeFormatter.formatJson("   "));
        assertEquals("", StructuredCodeFormatter.formatJson(""));
    }

    @Test
    public void deriveIndentGuideColorProducesVisibleActiveAndPassiveShades() {
        int darkGuide = ThemePackEditorColors.deriveIndentGuideColor(0xFF000000, 0xFFE0E0E0, false);
        int darkActive = ThemePackEditorColors.deriveIndentGuideColor(0xFF000000, 0xFFE0E0E0, true);
        assertNotEquals(0, darkGuide);
        assertNotEquals(0xFF000000, darkGuide);
        assertTrue((darkActive & 0xFF) > (darkGuide & 0xFF));

        int lightGuide = ThemePackEditorColors.deriveIndentGuideColor(0xFFFFFFFF, 0xFF000000, false);
        int lightActive = ThemePackEditorColors.deriveIndentGuideColor(0xFFFFFFFF, 0xFF000000, true);
        assertNotEquals(0xFFFFFFFF, lightGuide);
        assertTrue((lightActive & 0xFF) < (lightGuide & 0xFF));
    }

    @Test
    public void allLanguagesHaveLoadableLanguageConfigurationsWithFoldingAndIndentRules() throws Exception {
        File assets = resolveAssetsDir();
        String languagesJson = Files.readString(new File(assets, "languages.json").toPath());
        JsonObject root = JsonParser.parseString(languagesJson).getAsJsonObject();
        JsonArray langs = root.getAsJsonArray("languages");
        assertEquals(14, langs.size());

        for (JsonElement elem : langs) {
            JsonObject lang = elem.getAsJsonObject();
            String name = lang.get("name").getAsString();
            assertTrue("Missing languageConfiguration for " + name, lang.has("languageConfiguration"));
            String configPath = lang.get("languageConfiguration").getAsString();
            File configFile = new File(assets, configPath);
            assertTrue("Missing config file for " + name + ": " + configFile, configFile.isFile());

            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                LanguageConfiguration cfg = LanguageConfiguration.load(reader);
                assertNotNull("TM4E failed to load config for " + name, cfg);
                assertNotNull("Missing folding rules for " + name, cfg.getFolding());
                assertNotNull("Missing folding start marker for " + name, cfg.getFolding().markersStart);
                assertNotNull("Missing folding end marker for " + name, cfg.getFolding().markersEnd);
                assertNotNull("Missing indentationRules for " + name, cfg.getIndentationRules());
                assertNotNull("Missing increaseIndentPattern for " + name, cfg.getIndentationRules().increaseIndentPattern);
                assertNotNull("Missing decreaseIndentPattern for " + name, cfg.getIndentationRules().decreaseIndentPattern);
            }
        }
    }

    @Test
    public void builtinThemesIncludeNonEmptyIndentGuideColors() throws Exception {
        File assets = resolveAssetsDir();
        for (String theme : new String[]{"themes/dark.json", "themes/light.json"}) {
            String raw = Files.readString(new File(assets, theme).toPath());
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject colors = obj.getAsJsonObject("colors");
            assertNotNull("Missing colors block in " + theme, colors);
            assertFalse(colors.get("editorIndentGuide.background").getAsString().isEmpty());
            assertFalse(colors.get("editorIndentGuide.activeBackground").getAsString().isEmpty());
        }
    }
}
