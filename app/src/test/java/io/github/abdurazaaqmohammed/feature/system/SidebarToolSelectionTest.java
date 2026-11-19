package io.github.abdurazaaqmohammed.feature.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit added-set, ordering, hide-key and render-state rules behind the sidebar's hub tools. */
public class SidebarToolSelectionTest {

    @Test
    public void addedSetCanonicalizesLegacyAliasesAndKeepsPrefixedIds() {
        Set<String> raw = new HashSet<>(Arrays.asList(
                "wifimanager", "mcpserver", "packtool:ocr.scan", "custom:mytool", "", null, "  "));
        Set<String> added = SidebarToolSelection.sanitizeAdded(raw);
        assertTrue(added.contains("wifi"));
        assertTrue(added.contains("mcp_server"));
        assertTrue(added.contains("packtool:ocr.scan"));
        assertTrue(added.contains("custom:mytool"));
        assertEquals(4, added.size());
    }

    @Test
    public void addedSetEmptyAndNullInputsYieldEmptyResult() {
        assertTrue(SidebarToolSelection.sanitizeAdded(null).isEmpty());
        assertTrue(SidebarToolSelection.sanitizeAdded(new HashSet<String>()).isEmpty());
    }

    @Test
    public void ensureAddedIsIdempotentCanonicalAndNeverRemoves() {
        Set<String> added = new HashSet<>();
        assertTrue(SidebarToolSelection.ensureAdded(added, "wifimanager"));
        assertEquals(1, added.size());
        assertTrue(added.contains("wifi"));
        assertFalse(SidebarToolSelection.ensureAdded(added, "wifi"));
        assertEquals(1, added.size());
        assertTrue(SidebarToolSelection.ensureAdded(added, "packtool:ocr.scan"));
        assertEquals(2, added.size());
    }

    @Test
    public void ensureAddedIgnoresBlankAndNullIds() {
        Set<String> added = new HashSet<>();
        assertFalse(SidebarToolSelection.ensureAdded(added, null));
        assertFalse(SidebarToolSelection.ensureAdded(added, "   "));
        assertFalse(SidebarToolSelection.ensureAdded(null, "wifi"));
        assertTrue(added.isEmpty());
    }

    @Test
    public void membershipIsExplicitOnlyForHubOnlyRegistryPackAndCustomRows() {
        assertTrue(SidebarToolSelection.membershipIsExplicit("wifi", true, false));
        assertTrue(SidebarToolSelection.membershipIsExplicit("screen_ocr", true, false));
        assertFalse(SidebarToolSelection.membershipIsExplicit("extract", true, true));
        assertTrue(SidebarToolSelection.membershipIsExplicit("packtool:ocr.scan", false, false));
        assertTrue(SidebarToolSelection.membershipIsExplicit("custom:mytool", false, false));
        assertFalse(SidebarToolSelection.membershipIsExplicit("ext.action", false, false));
        assertFalse(SidebarToolSelection.membershipIsExplicit(null, false, false));
    }

    /** Add/Remove state transitions, mirroring the hub menu against organize hides. */
    @Test
    public void defaultToolTransitionsFollowHideKeyOnly() {
        // shipped default: visible until organized away, restore keeps nothing else
        assertTrue(SidebarToolSelection.isRendered(false, false, true, false));
        assertFalse(SidebarToolSelection.isRendered(false, false, true, true));
    }

    @Test
    public void hubOnlyToolTransitionsNeedAddedMembershipAndRespectHides() {
        // not added: absent even unhidden
        assertFalse(SidebarToolSelection.isRendered(true, false, false, false));
        // added and visible
        assertTrue(SidebarToolSelection.isRendered(true, true, false, false));
        // added but hidden in organize mode: absent, so the hub must offer Add (restore)
        assertFalse(SidebarToolSelection.isRendered(true, true, false, true));
        // restore: membership kept, hide key cleared
        assertTrue(SidebarToolSelection.isRendered(true, true, false, false));
    }

    @Test
    public void packRowRendersAutomaticallyWithPluginsSettingAndExplicitAddsSurviveItOff() {
        // visible via the show-plugins setting alone
        assertTrue(SidebarToolSelection.isRendered(true, false, true, false));
        // removed (hidden) while shown automatically
        assertFalse(SidebarToolSelection.isRendered(true, false, true, true));
        // explicit add keeps the row when the plugins setting is off
        assertTrue(SidebarToolSelection.isRendered(true, true, false, false));
        // explicit add still loses to an organize hide
        assertFalse(SidebarToolSelection.isRendered(true, true, false, true));
    }

    @Test
    public void customRowRendersOnlyOnceAdded() {
        assertFalse(SidebarToolSelection.isRendered(true, false, false, false));
        assertTrue(SidebarToolSelection.isRendered(true, true, false, false));
        assertFalse(SidebarToolSelection.isRendered(true, true, false, true));
    }

    @Test
    public void extensionRowRendersUnlessHidden() {
        assertTrue(SidebarToolSelection.isRendered(false, false, true, false));
        assertFalse(SidebarToolSelection.isRendered(false, false, true, true));
    }

    @Test
    public void mergeOrderSavedOrderWinsThenDefaultsThenAdded() {
        List<String> saved = new ArrayList<>(Arrays.asList("documents", "wifimanager", "extract"));
        List<String> defaults = Arrays.asList("extract", "documents", "recycle_bin");
        Set<String> added = new HashSet<>(Arrays.asList("packtool:ocr.scan", "screen_ocr"));
        List<String> merged = SidebarToolSelection.mergeOrder(saved, defaults, added);
        assertEquals(Arrays.asList("documents", "wifi", "extract", "recycle_bin",
                "packtool:ocr.scan", "screen_ocr"), merged);
    }

    @Test
    public void mergeOrderDropsBlanksAndDuplicates() {
        List<String> merged = SidebarToolSelection.mergeOrder(
                Arrays.asList("extract", null, "  ", "extract"),
                Arrays.asList("extract", "documents"),
                new HashSet<>(Arrays.asList("extract", "documents")));
        assertEquals(Arrays.asList("extract", "documents"), merged);
    }

    @Test
    public void mergeOrderHandlesNullInputs() {
        assertEquals(Arrays.asList("extract"),
                SidebarToolSelection.mergeOrder(null, Arrays.asList("extract"), null));
    }

    @Test
    public void hiddenKeysCanonicalizeToolPrefixOnly() {
        Set<String> raw = new HashSet<>(Arrays.asList(
                "TOOL\u0001wifimanager", "TOOL\u0001frida", "settings", "PIN\u0001saf:xyz"));
        Set<String> canonical = SidebarToolSelection.canonicalizeHidden(raw);
        assertTrue(canonical.contains("TOOL\u0001wifi"));
        assertTrue(canonical.contains("TOOL\u0001frida_console"));
        assertTrue(canonical.contains("settings"));
        assertTrue(canonical.contains("PIN\u0001saf:xyz"));
        assertEquals(4, canonical.size());
    }

    @Test
    public void hideKeysMatchAfterAliasCanonicalization() {
        Set<String> canonical = SidebarToolSelection.canonicalizeHidden(new HashSet<>(
                Arrays.asList(SidebarToolSelection.hiddenKey("wifimanager"))));
        assertTrue(canonical.contains(SidebarToolSelection.hiddenKey("wifi")));
    }

    @Test
    public void hiddenKeyFormatMatchesAdapterEntryKey() {
        assertEquals("TOOL\u0001screen_ocr", SidebarToolSelection.hiddenKey("screen_ocr"));
        assertEquals("TOOL\u0001packtool:ocr.scan",
                SidebarToolSelection.hiddenKey("packtool:ocr.scan"));
    }

    @Test
    public void prefAndPrefixKeysAreStable() {
        assertEquals("sidebar_added_tools", SidebarToolSelection.PREF_ADDED_TOOLS);
        assertEquals("custom:", SidebarToolSelection.CUSTOM_TOOL_PREFIX);
        assertEquals("packtool:", SidebarToolSelection.PACK_TOOL_PREFIX);
        assertEquals(SidebarToolSelection.PACK_TOOL_PREFIX, "packtool:");
    }
}
