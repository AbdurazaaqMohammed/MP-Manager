package io.github.abdurazaaqmohammed.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.abdurazaaqmohammed.feature.apk.ApkInfoDialogs;
import io.github.abdurazaaqmohammed.feature.apk.ApkMoreMenuOrder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure JVM coverage for the hook-integration menu plumbing that needs no
 * Android framework: the file/APK default menu orders stay well-formed (the
 * persisted reorder/hide flows append third-party ids to these arrays, so a
 * duplicate or missing constant would silently corrupt user arrangements),
 * and plugin-supplied result text is clamped before any UI display.
 */
public class HookMenuOrderTest {

    private static List<String> asList(String[] order) {
        List<String> out = new ArrayList<>();
        if (order != null) {
            for (String id : order) out.add(id);
        }
        return out;
    }

    @Test
    public void fileMenuDefaultOrderHasNoDuplicates() {
        List<String> order = asList(FileMenuOrder.DEFAULT_ORDER);
        Set<String> seen = new HashSet<>(order);
        assertEquals("duplicate id in FileMenuOrder.DEFAULT_ORDER",
                order.size(), seen.size());
        assertTrue("DEFAULT_ORDER empty", !order.isEmpty());
        // The core constants the file menu dispatch switches on must exist.
        String[] mustContain = {FileMenuOrder.COPY, FileMenuOrder.MOVE,
                FileMenuOrder.RENAME, FileMenuOrder.DELETE, FileMenuOrder.SHARE,
                FileMenuOrder.OPEN_WITH, FileMenuOrder.PROPERTIES};
        for (String id : mustContain) {
            assertTrue("missing core id " + id, seen.contains(id));
        }
    }

    @Test
    public void apkMoreMenuDefaultOrderHasNoDuplicates() {
        List<String> order = asList(ApkMoreMenuOrder.DEFAULT_ORDER);
        Set<String> seen = new HashSet<>(order);
        assertEquals("duplicate id in ApkMoreMenuOrder.DEFAULT_ORDER",
                order.size(), seen.size());
        assertTrue("DEFAULT_ORDER empty", !order.isEmpty());
        String[] mustContain = {ApkMoreMenuOrder.SIGN, ApkMoreMenuOrder.OPT,
                ApkMoreMenuOrder.DECOMPILE, ApkMoreMenuOrder.CLONE};
        for (String id : mustContain) {
            assertTrue("missing core id " + id, seen.contains(id));
        }
    }

    @Test
    public void pluginResultMessagesClampTo512Chars() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 600; i++) big.append('x');
        String clamped = MainFilesArrayAdapter.boundedPluginMessage(big.toString());
        assertEquals(MainFilesArrayAdapter.MAX_PLUGIN_MESSAGE_CHARS, clamped.length());
        assertEquals(512, MainFilesArrayAdapter.MAX_PLUGIN_MESSAGE_CHARS);
    }

    @Test
    public void shortAndEmptyPluginMessagesPassThrough() {
        assertEquals("Done", MainFilesArrayAdapter.boundedPluginMessage("Done"));
        assertEquals("", MainFilesArrayAdapter.boundedPluginMessage(null));
        assertEquals("", MainFilesArrayAdapter.boundedPluginMessage(""));
        StringBuilder exactly = new StringBuilder();
        for (int i = 0; i < MainFilesArrayAdapter.MAX_PLUGIN_MESSAGE_CHARS; i++) {
            exactly.append('y');
        }
        assertEquals(MainFilesArrayAdapter.MAX_PLUGIN_MESSAGE_CHARS,
                MainFilesArrayAdapter.boundedPluginMessage(exactly.toString()).length());
    }

    @Test
    public void apkDialogClampMatchesFileMenuClamp() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1000; i++) big.append('z');
        assertEquals(MainFilesArrayAdapter.MAX_PLUGIN_MESSAGE_CHARS,
                ApkInfoDialogs.boundedPluginMessage(big.toString()).length());
        assertEquals(MainFilesArrayAdapter.MAX_PLUGIN_MESSAGE_CHARS,
                ApkInfoDialogs.MAX_PLUGIN_MESSAGE_CHARS);
    }
}
