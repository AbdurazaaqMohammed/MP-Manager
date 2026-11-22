package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Contract-string guards: all new extras share the host's namespaced
 * extra prefix and the legacy strings are retained verbatim.
 */
public class PluginContractsTest {

    private static final String EXTRA_PREFIX =
            "io.github.abdurazaaqmohammed.MPManager.extra.";

    @Test
    public void newExtras_shareTheHostNamespace() {
        String[] extras = {
                PluginContracts.EXTRA_CONTRACT_VERSION,
                PluginContracts.EXTRA_HOST_PACKAGE,
                PluginContracts.EXTRA_HOST_HOOKS,
                PluginContracts.EXTRA_HOOK_ID,
                PluginContracts.EXTRA_HOOK_PAYLOAD,
                PluginContracts.EXTRA_THEME_ID,
                PluginContracts.EXTRA_THEME_ACCENT,
                PluginContracts.EXTRA_THEME_DARK,
                PluginContracts.EXTRA_THEME_PALETTE,
                PluginContracts.EXTRA_APPLY_THEME_ID,
        };
        for (String extra : extras) {
            assertTrue(extra, extra.startsWith(EXTRA_PREFIX));
        }
    }

    @Test
    public void hookAndEventActionsAreNamespaced() {
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.HOOK",
                PluginContracts.ACTION_HOOK);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.EVENT",
                PluginContracts.ACTION_EVENT);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.HOOKS",
                PluginContracts.META_HOOKS);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.CONTRACT_VERSION",
                PluginContracts.META_CONTRACT_VERSION);
        assertEquals(1, PluginContracts.CONTRACT_VERSION);
    }

    @Test
    public void stableHookIdsAreExactlyTheContractValues() {
        assertEquals("sidebar.footer", PluginContracts.HOOK_SIDEBAR_FOOTER);
        assertEquals("toolbar.actions", PluginContracts.HOOK_TOOLBAR_ACTIONS);
        assertEquals("filemenu.extra", PluginContracts.HOOK_FILE_MENU_EXTRA);
        assertEquals("dialog.elf.extra", PluginContracts.HOOK_ELF_DIALOG_EXTRA);
        assertEquals("dialog.apk.more", PluginContracts.HOOK_APK_DIALOG_MORE);
        assertEquals("editor.toolbar", PluginContracts.HOOK_EDITOR_ACTIONS);
    }

    @Test
    public void stableEventIdsAreExactlyTheContractValues() {
        assertEquals("app.launch", PluginContracts.EVENT_APP_LAUNCH);
        assertEquals("file.opened", PluginContracts.EVENT_FILE_OPENED);
        assertEquals("theme.changed", PluginContracts.EVENT_THEME_CHANGED);
    }

    @Test
    public void payloadKeysArePrimitiveNames() {
        assertEquals("file_name", PluginContracts.PAYLOAD_KEY_FILE_NAME);
        assertEquals("mime", PluginContracts.PAYLOAD_KEY_MIME);
    }

    @Test
    public void legacyStringsRetained() {
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.SIDEBAR_OPEN",
                PluginContracts.ACTION_SIDEBAR_OPEN);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.SETTING_CONFIG",
                PluginContracts.ACTION_SETTING_CONFIG);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.FILE_MENU",
                PluginContracts.ACTION_FILE_MENU);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.EDITOR_ACTION",
                PluginContracts.ACTION_EDITOR);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.action.APK_ACTION",
                PluginContracts.ACTION_APK);
        assertEquals("io.github.abdurazaaqmohammed.MPManager.PLUGIN_ID",
                PluginContracts.META_PLUGIN_ID);
        assertEquals(EXTRA_PREFIX + "PLUGIN_ID", PluginContracts.EXTRA_PLUGIN_ID);
        assertEquals(EXTRA_PREFIX + "VALUE", PluginContracts.EXTRA_VALUE);
        assertEquals(EXTRA_PREFIX + "MESSAGE", PluginContracts.EXTRA_MESSAGE);
        assertEquals(150 * 1024, PluginContracts.MAX_FULL_TEXT_BYTES);
    }
}
