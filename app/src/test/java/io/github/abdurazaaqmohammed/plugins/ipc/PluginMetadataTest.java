package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.*;

import org.junit.Test;

public class PluginMetadataTest {
    @Test
    public void titleStripsSpoofingControlsAndBoundsLength() {
        assertEquals("A B", PluginMetadata.title("A\u202eB", "fallback"));
        assertEquals("fallback", PluginMetadata.title(" \n ", "fallback"));
        assertEquals(128, PluginMetadata.title("a".repeat(2048), "fallback").length());
    }

    @Test
    public void filePatternsKeepBasicRegexMatching() {
        assertTrue(PluginMetadata.filenameMatches(".*\\.txt", "notes.txt"));
        assertFalse(PluginMetadata.filenameMatches(".*\\.txt", "notes.apk"));
        assertTrue(PluginMetadata.filenameMatches("", "anything"));
        assertFalse(PluginMetadata.filenameMatches("[", "file"));
    }

    @Test(timeout = 1000)
    public void nestedQuantifiersDoNotBacktrackInTheHost() {
        assertFalse(PluginMetadata.filenameMatches("(a+)+$", "a".repeat(500) + "!"));
        assertFalse(PluginMetadata.filenameMatches("a".repeat(257), "a"));
        assertFalse(PluginMetadata.filenameMatches("(?=secret).*", "secret"));
        assertFalse(PluginMetadata.filenameMatches("(a)\\1", "aa"));
    }

    @Test
    public void aSettingCannotWriteHostOrAnotherPluginsPreference() {
        assertEquals("external_setting:com.example.one:theme_plugin_id",
                PluginMetadata.settingKey("com.example.one", "theme_plugin_id"));
        assertNotEquals(PluginMetadata.settingKey("com.example.one", "same"),
                PluginMetadata.settingKey("com.example.two", "same"));
        assertNotEquals("theme_plugin_id", PluginMetadata.settingKey("com.example.one", "theme_plugin_id"));
    }
}
