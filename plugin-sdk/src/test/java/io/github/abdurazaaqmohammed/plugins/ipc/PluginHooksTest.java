package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure JUnit4 tests for the hook/event helper allowlists and parsing. */
public class PluginHooksTest {

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }

    @Test
    public void isActionHook_acceptsStableHookIdsOnly() {
        for (String hookId : PluginHooks.knownHooks()) {
            assertTrue(hookId, PluginHooks.isActionHook(hookId));
        }
        // The intent action string is NOT a hook id.
        assertFalse(PluginHooks.isActionHook(PluginContracts.ACTION_HOOK));
        assertFalse(PluginHooks.isActionHook("my.own.hook"));
        assertFalse(PluginHooks.isActionHook("Toolbar.Actions"));
        assertFalse(PluginHooks.isActionHook(" toolbar.actions "));
        assertFalse(PluginHooks.isActionHook(null));
    }

    @Test
    public void isEvent_acceptsStableEventIdsOnly() {
        for (String eventId : PluginHooks.knownEvents()) {
            assertTrue(eventId, PluginHooks.isEvent(eventId));
        }
        // The intent action string is NOT an event id.
        assertFalse(PluginHooks.isEvent(PluginContracts.ACTION_EVENT));
        assertFalse(PluginHooks.isEvent("my.own.event"));
        assertFalse(PluginHooks.isEvent("Theme.Changed"));
        assertFalse(PluginHooks.isEvent(null));
    }

    @Test
    public void actionHooks_containsTheDeclaredStableHooksInOrder() {
        String[] hooks = PluginHooks.actionHooks();
        assertTrue(hooks.length >= 6);
        assertArrayEquals(hooks, PluginHooks.knownHooks().toArray(new String[0]));
        // The six contract hooks must all be present, in this relative order.
        List<String> expected = Arrays.asList(
                PluginContracts.HOOK_SIDEBAR_FOOTER,
                PluginContracts.HOOK_TOOLBAR_ACTIONS,
                PluginContracts.HOOK_FILE_MENU_EXTRA,
                PluginContracts.HOOK_ELF_DIALOG_EXTRA,
                PluginContracts.HOOK_APK_DIALOG_MORE,
                PluginContracts.HOOK_EDITOR_ACTIONS);
        int index = -1;
        for (String id : expected) {
            int next = PluginHooks.knownHooks().indexOf(id);
            assertTrue(id + " missing/unordered", next > index);
            index = next;
        }
    }

    @Test
    public void eventHooks_containsTheDeclaredStableEventsInOrder() {
        String[] events = PluginHooks.eventHooks();
        assertTrue(events.length >= 3);
        assertArrayEquals(events, PluginHooks.knownEvents().toArray(new String[0]));
        List<String> expected = Arrays.asList(
                PluginContracts.EVENT_APP_LAUNCH,
                PluginContracts.EVENT_FILE_OPENED,
                PluginContracts.EVENT_THEME_CHANGED);
        int index = -1;
        for (String id : expected) {
            int next = PluginHooks.knownEvents().indexOf(id);
            assertTrue(id + " missing/unordered", next > index);
            index = next;
        }
    }

    @Test
    public void actionHooksAndEventHooksAreDefensiveCopies() {
        String[] hooks = PluginHooks.actionHooks();
        hooks[0] = "tampered";
        assertFalse("tampered", PluginHooks.isActionHook(hooks[0]));
        assertTrue(PluginHooks.isActionHook(PluginHooks.actionHooks()[0]));
    }

    @Test
    public void parse_keepsKnownHooksAndEventsInDeclaredOrder() {
        assertEquals(
                Arrays.asList("toolbar.actions", "theme.changed", "file.opened"),
                PluginHooks.parse(" toolbar.actions , bogus , theme.changed,"
                        + "other.hook,file.opened,toolbar.actions"));
    }

    @Test
    public void parse_ignoresUnknownAndEmptyEntries() {
        assertTrue(PluginHooks.parse(null).isEmpty());
        assertTrue(PluginHooks.parse("").isEmpty());
        assertTrue(PluginHooks.parse(" , ,, ").isEmpty());
        assertTrue(PluginHooks.parse("not.a.hook").isEmpty());
        assertTrue(PluginHooks.parse(PluginContracts.ACTION_HOOK).isEmpty());
    }

    @Test
    public void parse_dropsOverlongTokens() {
        String overlong = repeat("a", PluginRequests.MAX_ID_CHARS + 1);
        assertEquals(Collections.singletonList("toolbar.actions"),
                PluginHooks.parse(overlong + ",toolbar.actions"));
        String atLimit = repeat("s", PluginRequests.MAX_ID_CHARS);
        // A 128-char token is never a known id, but it must not break parsing.
        assertTrue(PluginHooks.parse(atLimit + ",editor.toolbar")
                .equals(Collections.singletonList("editor.toolbar")));
    }

    @Test
    public void parse_rejectsMetaValuesOver2048Chars() {
        StringBuilder huge = new StringBuilder("toolbar.actions,");
        while (huge.length() <= PluginRequests.MAX_META_VALUE_CHARS) {
            huge.append("toolbar.actions,");
        }
        assertTrue(PluginHooks.parse(huge.toString()).isEmpty());

        StringBuilder ok = new StringBuilder();
        while (ok.length() < 1500) {
            ok.append("toolbar.actions,");
        }
        ok.append("editor.toolbar");
        assertEquals(Arrays.asList("toolbar.actions", "editor.toolbar"),
                PluginHooks.parse(ok.toString()));
    }

    @Test
    public void parse_isBounded() {
        StringBuilder declared = new StringBuilder();
        for (String id : PluginHooks.knownHooks()) {
            declared.append(id).append(',');
        }
        for (String id : PluginHooks.knownEvents()) {
            declared.append(id).append(',');
        }
        List<String> parsed = PluginHooks.parse(declared.toString());
        int expected = Math.min(PluginHooks.knownHooks().size() + PluginHooks.knownEvents().size(),
                PluginRequests.MAX_DECLARED_HOOKS);
        assertEquals(expected, parsed.size());
        assertTrue(parsed.size() <= PluginRequests.MAX_DECLARED_HOOKS);
    }

    @Test
    public void entryId_buildsStableQualifiedId() {
        assertEquals("ext:com.example.tpl/template.hooks@toolbar.actions",
                PluginHooks.entryId("com.example.tpl", "template.hooks", null, "toolbar.actions"));
        // Blank plugin id falls back to the class name.
        assertEquals("ext:com.example.tpl/com.example.tpl.HookActivity@filemenu.extra",
                PluginHooks.entryId(" com.example.tpl ", "  ",
                        "  com.example.tpl.HookActivity  ", " filemenu.extra "));
    }

    @Test
    public void entryId_rejectsInvalidPackages() {
        assertNull(PluginHooks.entryId(null, "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId("", "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId("example", "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId("com..example", "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.1example", "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.example/../evil", "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.ex ample", "id", "Cls", "toolbar.actions"));
        assertNull(PluginHooks.entryId(repeat("c", PluginRequests.MAX_ID_CHARS + 1),
                "id", "Cls", "toolbar.actions"));
    }

    @Test
    public void entryId_rejectsSpoofedPluginIds() {
        assertNull(PluginHooks.entryId("com.example", "id/with/slash", null, "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.example", "id@other", null, "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.example", "id\\back", null, "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.example", "id\nnewline", null, "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.example", repeat("i", PluginRequests.MAX_ID_CHARS + 1),
                null, "toolbar.actions"));
        assertNull(PluginHooks.entryId("com.example", null, null, "toolbar.actions"));
    }

    @Test
    public void entryId_requiresAllowlistedHookId() {
        assertNull(PluginHooks.entryId("com.example", "id", "Cls", null));
        assertNull(PluginHooks.entryId("com.example", "id", "Cls", ""));
        assertNull(PluginHooks.entryId("com.example", "id", "Cls", "not.a.hook"));
        assertNull(PluginHooks.entryId("com.example", "id", "Cls", "theme.changed"));
        assertNull(PluginHooks.entryId("com.example", "id", "Cls",
                PluginContracts.ACTION_HOOK));
    }

    @Test
    public void mergeOrder_dedupesAndAppends() {
        assertEquals(Arrays.asList("a", "b", "c"),
                PluginHooks.mergeOrder(Arrays.asList("a", "b", "a"),
                        Arrays.asList("b", "c", null)));
        assertEquals(Collections.singletonList("only"),
                PluginHooks.mergeOrder(Collections.singletonList("only"), null));
        assertTrue(PluginHooks.mergeOrder(null, null).isEmpty());
        try {
            PluginHooks.mergeOrder(Collections.singletonList("a"),
                    Collections.singletonList("b")).add("c");
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
