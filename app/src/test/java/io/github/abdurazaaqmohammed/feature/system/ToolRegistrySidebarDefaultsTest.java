package io.github.abdurazaaqmohammed.feature.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Sidebar default shipping rules: seven hub-only registry tools must not
 * appear by default while every other shipped default stays untouched, and
 * hub-only tools keep their section membership so explicit adds land where
 * they used to live.
 */
public class ToolRegistrySidebarDefaultsTest {

    private static final List<String> HUB_ONLY = Arrays.asList(
            "screen_ocr", "partition_dumper", "app_ops", "set_edit", "wifi", "frida_console", "free_style");

    @Test
    public void sevenToolsAreNoLongerDefaultSidebarEntries() {
        for (String id : HUB_ONLY) {
            assertFalse("expected hub-only: " + id, ToolRegistry.isDefaultSidebarTool(id));
        }
    }

    @Test
    public void hubOnlyToolsKeepTheirSectionMembership() {
        assertEquals("tools", sectionOf("screen_ocr"));
        assertEquals("tools", sectionOf("partition_dumper"));
        assertEquals("tools", sectionOf("app_ops"));
        assertEquals("tools", sectionOf("set_edit"));
        assertEquals("plugins", sectionOf("wifi"));
        assertEquals("plugins", sectionOf("frida_console"));
        assertEquals("plugins", sectionOf("free_style"));
    }

    private static String sectionOf(String id) {
        for (String section : new String[]{"tools", "plugins"}) {
            if (ToolRegistry.isInSidebarSection(id, section)) return section;
        }
        return null;
    }

    @Test
    public void remainingDefaultToolsSectionIsUnchanged() {
        List<String> ids = ToolRegistry.sidebarIds("tools");
        assertEquals(Arrays.asList("tools", "extract", "documents", "recycle_bin",
                "ftp_server", "network_client"), ids);
    }

    @Test
    public void remainingDefaultPluginsSectionIsUnchanged() {
        List<String> ids = ToolRegistry.sidebarIds("plugins");
        assertEquals(Arrays.asList("mcp_server", "command_shell", "logcat", "layout", "color_picker"), ids);
    }

    @Test
    public void nonSidebarRegistryToolsStayOutOfBothSections() {
        for (String id : Arrays.asList("storagemanager", "adb", "jiagu_dumper")) {
            assertFalse(ToolRegistry.isDefaultSidebarTool(id));
            assertFalse(ToolRegistry.isInSidebarSection(id, "tools"));
            assertFalse(ToolRegistry.isInSidebarSection(id, "plugins"));
        }
    }

    @Test
    public void nullSectionBuiltinsReportNoShippedSectionForExplicitAdds() {
        // Once explicitly added from the hub these land in the advanced
        // plugins section; the adapter picks that up from the null section.
        assertNull(ToolRegistry.sidebarSectionOf("storagemanager"));
        assertNull(ToolRegistry.sidebarSectionOf("adb"));
        assertNull(ToolRegistry.sidebarSectionOf("jiagu_dumper"));
        assertEquals("tools", ToolRegistry.sidebarSectionOf("screen_ocr"));
        assertEquals("plugins", ToolRegistry.sidebarSectionOf("wifi"));
        assertNull(ToolRegistry.sidebarSectionOf("unknown_tool"));
    }

    @Test
    public void canonicalAliasesStillResolveForHubOnlyTools() {
        assertEquals("wifi", ToolRegistry.canonicalId("wifimanager"));
        assertEquals("frida_console", ToolRegistry.canonicalId("frida"));
        assertEquals("partition_dumper", ToolRegistry.canonicalId("partitiondumper"));
        assertEquals("app_ops", ToolRegistry.canonicalId("app_ops"));
        assertEquals("set_edit", ToolRegistry.canonicalId("setedit"));
        assertEquals("free_style", ToolRegistry.canonicalId("free_style"));
        assertEquals("screen_ocr", ToolRegistry.canonicalId("screen_ocr"));
    }

    @Test
    public void canonicalAliasesKeepTheirSidebarSection() {
        assertTrue(ToolRegistry.isInSidebarSection("wifimanager", "plugins"));
        assertTrue(ToolRegistry.isInSidebarSection("frida", "plugins"));
        assertTrue(ToolRegistry.isInSidebarSection("partitiondumper", "tools"));
        assertTrue(ToolRegistry.isInSidebarSection("ftp_client", "tools"));
        assertFalse(ToolRegistry.isInSidebarSection("wifimanager", "tools"));
    }

    @Test
    public void isDefaultSidebarToolAcceptsAliases() {
        assertTrue(ToolRegistry.isDefaultSidebarTool("extract"));
        assertTrue(ToolRegistry.isDefaultSidebarTool("ftp_server"));
        assertFalse(ToolRegistry.isDefaultSidebarTool("unknown_tool"));
    }
}
