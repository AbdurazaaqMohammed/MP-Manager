package io.github.abdurazaaqmohammed.ui.editor.symbols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView;
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataCodec;
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SharedPreferencesSymbolGroupRepository;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup;
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.GroupIndicatorStyle;
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SharedPreferencesSymbolDrawerSettingsRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.editor.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.editor.UnifiedEditorFragment;
import io.github.rosemoe.sora.widget.CodeEditor;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import modder.hub.dexeditor.views.TextActionWindow;

/**
 * Focused integration tests for the V2 symbol-drawer integration on the real
 * shipped Sora editor (sora-editor 0.23.6) and the real V2 library module.
 * Covers the app-owned bridge ({@link SoraSymbolActionHost}), the one-way V1
 * migration ({@link SymbolDrawerMigration}), and the actual drawer callbacks
 * wired by {@link UnifiedEditorFragment} down to real symbol-cell taps.
 *
 * Follows the EditorRegressionTest fixture style: real activities launched
 * from the instrumentation with temp files, no mocks. Every editor touch goes
 * through the main thread because Sora is main-thread only.
 *
 * Runs only against the isolated ".editorregression" debug package
 * (-PeditorRegressionBuild) so it can never replace or disturb the main
 * installed app.
 *
 * Deliberately omitted: physical IME interaction (flaky across emulator
 * keyboards). The IME-geometry contract the fragment guarantees is pinned via
 * the drawer translation assertion in the lifecycle test instead.
 */
public class SymbolDrawerIntegrationTest {
    private static final String LEGACY_PREFS = "advanced_symbol_prefs";
    private static final String LEGACY_DATA_KEY = "symbol_json_data";
    private static final String V2_GROUPS_PREFS = "symbol_drawer_v2";
    private static final String V2_SETTINGS_PREFS = "symbol_drawer_v2_settings";
    /** settings.longPressTimeoutMs defaults to 420ms; wait past it. */
    private static final long LONG_PRESS_WAIT_MS = 700;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private final List<Activity> activities = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private final List<int[]> customIds = new ArrayList<>();
    private final List<String[]> customArgs = new ArrayList<>();
    private String savedLegacyBottomBar;
    private String savedMenuOrder;
    private File currentFile;
    private final AtomicReference<Activity> resumed = new AtomicReference<>();
    private final Object resumeLock = new Object();
    private final Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) {
            synchronized (resumeLock) { resumed.set(activity); resumeLock.notifyAll(); }
        }
        @Override public void onActivityCreated(Activity activity, Bundle state) { }
        @Override public void onActivityStarted(Activity activity) { }
        @Override public void onActivityPaused(Activity activity) { }
        @Override public void onActivityStopped(Activity activity) { }
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        @Override public void onActivityDestroyed(Activity activity) { }
    };

    /** Records every custom (mpManager) action the host forwards. */
    private final SoraSymbolActionHost.CustomAction recorder = (id, text, secondText, options) -> {
        customIds.add(new int[]{id});
        customArgs.add(new String[]{text, secondText, options});
        return true;
    };

    @Before
    public void prepare() throws Exception {
        context = instrumentation.getTargetContext();
        assertTrue("Must run against the isolated -PeditorRegressionBuild package; refusing to "
                        + "touch the main installed app: " + context.getPackageName(),
                context.getPackageName().endsWith(".editorregression"));
        Application application = (Application) context.getApplicationContext();
        // The existing rooted AVD may have an interactive su manager; editor
        // fixtures must not block startup on its grant dialog.
        PreferenceManager.getDefaultSharedPreferences(application).edit()
                .putString("working_mode", "non_root")
                .putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false)
                .putBoolean("root_file_ops", false).commit();
        application.registerActivityLifecycleCallbacks(callbacks);
        // Deterministic symbol-drawer stores: keep only what each test seeds.
        // The regression package is disposable, so stale drawer state is dropped
        // instead of restored.
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(V2_GROUPS_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(V2_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        SharedPreferences bottomBar = PreferenceManager.getDefaultSharedPreferences(context);
        savedLegacyBottomBar = bottomBar.getString("pref_bottom_bar_buttons", null);
        bottomBar.edit().remove("pref_bottom_bar_buttons")
                .remove("bottom_bar_migrated_to_symbol_input").commit();
        // editor_prefs persists across runs on the device, so a stale floating
        // menu order (paste/cut disabled by an earlier session) would leak into
        // every TextActionWindow constructed for the editor fixtures. Each test
        // starts from the regenerated default; the pathological menus are
        // seeded explicitly by the dedicated tolerance test below.
        SharedPreferences editorPrefs = context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE);
        savedMenuOrder = editorPrefs.getString("menu_order", null);
        editorPrefs.edit().remove("menu_order").commit();
        customIds.clear();
        customArgs.clear();
    }

    @After
    public void cleanup() {
        instrumentation.runOnMainSync(() -> {
            for (int i = activities.size() - 1; i >= 0; i--) activities.get(i).finish();
        });
        ((Application) context.getApplicationContext()).unregisterActivityLifecycleCallbacks(callbacks);
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(V2_GROUPS_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(V2_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.remove("bottom_bar_migrated_to_symbol_input");
        if (savedLegacyBottomBar != null) editor.putString("pref_bottom_bar_buttons", savedLegacyBottomBar);
        else editor.remove("pref_bottom_bar_buttons");
        editor.commit();
        SharedPreferences.Editor editorPrefs = context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE).edit();
        if (savedMenuOrder != null) editorPrefs.putString("menu_order", savedMenuOrder);
        else editorPrefs.remove("menu_order");
        editorPrefs.commit();
        for (File file : files) file.delete();
    }

    // ------------------------------------------------------------------
    // Legacy V1 migration: extended f/g/h/i fields, one-time, non-destructive
    // ------------------------------------------------------------------

    @Test
    public void legacyMigrationImportsExtendedFieldsAndSettingsOneTimeNonDestructive() throws Exception {
        // V1 compact wire shape: group {n,d}; item {a,b,c,d,e} plus the MP
        // Manager extension fields f (short secondText), g (long secondText),
        // h (short options), i (long options), where a/d=101 carry the
        // mpManager find/replace custom action.
        String legacyJson = "[{\"n\":\"Greek\",\"d\":["
                + "{\"a\":0,\"b\":\"alpha\",\"c\":\"alpha()\"},"
                + "{\"a\":0,\"b\":\"wrap\",\"c\":\"<b>\",\"d\":0,\"e\":\"</b>\",\"f\":\"s2\",\"g\":\"l2\",\"h\":\"o1\",\"i\":\"o2\"},"
                + "{\"a\":101,\"b\":\"FR\",\"c\":\"needle\",\"f\":\"repl\",\"h\":\"ra\",\"d\":101,\"e\":\"lneedle\",\"g\":\"lrepl\",\"i\":\"lra\"}"
                + "]}]";
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        legacy.edit().putString(LEGACY_DATA_KEY, legacyJson)
                .putInt("symbol_per_row", 7)
                .putInt("symbol_collapsed_rows", 3)
                .putInt("symbol_text_size_sp", 20)
                .putInt("symbol_indicator_style", 2) // V1 "hidden" style
                .putInt("symbol_max_label_chars", 0)
                .putBoolean("symbol_remember_expanded", true)
                .putBoolean("symbol_last_expanded", true)
                .commit();

        SymbolDrawerMigration.migrate(context);

        SharedPreferencesSymbolGroupRepository repository = repository();
        List<SymbolGroup> groups = repository.loadGroups();
        assertEquals("One legacy bundle becomes one V2 group", 1, groups.size());
        assertEquals("Greek", groups.get(0).getTitle());
        List<SymbolEntry> entries = groups.get(0).getSymbols();
        assertEquals(3, entries.size());

        SymbolEntry alpha = entries.get(0);
        assertTrue("Plain V1 text item stays an InsertText action",
                alpha.getShortAction() instanceof SymbolAction.InsertText);
        assertEquals("alpha()", ((SymbolAction.InsertText) alpha.getShortAction()).getTemplate());
        assertNull("Item without d has no long action", alpha.getLongAction());
        assertEquals(Integer.valueOf(0), alpha.getLegacyShortActionId());

        SymbolEntry wrap = entries.get(1);
        assertTrue(wrap.getShortAction() instanceof SymbolAction.InsertText);
        assertEquals("<b>", ((SymbolAction.InsertText) wrap.getShortAction()).getTemplate());
        assertTrue(wrap.getLongAction() instanceof SymbolAction.InsertText);
        assertEquals("</b>", ((SymbolAction.InsertText) wrap.getLongAction()).getTemplate());

        SymbolEntry fr = entries.get(2);
        assertTrue("MP Manager 101 must migrate to the legacy.action.101 extension route",
                fr.getShortAction() instanceof SymbolAction.Extension);
        SymbolAction.Extension shortAction = (SymbolAction.Extension) fr.getShortAction();
        assertEquals("legacy.action.101", shortAction.getExtensionId());
        JsonObject shortPayload = JsonParser.parseString(shortAction.getPayload()).getAsJsonObject();
        assertTrue(shortPayload.get("mpManager").getAsBoolean());
        assertEquals("needle", shortPayload.get("text").getAsString());
        assertEquals("repl", shortPayload.get("secondText").getAsString());
        assertEquals("ra", shortPayload.get("options").getAsString());
        assertTrue(fr.getLongAction() instanceof SymbolAction.Extension);
        SymbolAction.Extension longAction = (SymbolAction.Extension) fr.getLongAction();
        assertEquals("legacy.action.101", longAction.getExtensionId());
        JsonObject longPayload = JsonParser.parseString(longAction.getPayload()).getAsJsonObject();
        assertEquals("lneedle", longPayload.get("text").getAsString());
        assertEquals("lrepl", longPayload.get("secondText").getAsString());
        assertEquals("lra", longPayload.get("options").getAsString());

        // Settings migration branch (runs only when no durable settings exist).
        SharedPreferencesSymbolDrawerSettingsRepository settingsRepository = settingsRepository();
        var settings = settingsRepository.load().getSettings();
        assertEquals("symbol_per_row migrates into columns", 7, settings.getColumns());
        assertEquals("symbol_collapsed_rows migrates", 3, settings.getCollapsedRows());
        assertEquals("symbol_text_size_sp migrates", 20, settings.getSymbolLabelTextSizeSp());
        assertEquals("V1 indicator style 2 is 'hidden' (options: standard/simple/hidden/"
                        + "top_line/block) and maps to the text-tabs style",
                GroupIndicatorStyle.TEXT_TABS, settings.getIndicatorStyle());
        assertEquals("The hidden V1 indicator collapses the indicator bar height",
                0, settings.getIndicatorHeightDp());
        assertTrue("symbol_max_label_chars == 0 means full labels",
                settings.getShowFullSymbolLabels());
        assertTrue(settings.getRememberExpandedState());
        SharedPreferences v2Settings = context.getSharedPreferences(V2_SETTINGS_PREFS, Context.MODE_PRIVATE);
        assertTrue("symbol_last_expanded lands in the remembered-expansion slot",
                v2Settings.getBoolean("remembered_expanded_state", false));
        assertTrue(repository.isLegacyMigrationComplete());

        // Non-destructive: the legacy store must be byte-identical afterwards.
        assertEquals("Legacy JSON must stay untouched (one-way migration)",
                legacyJson, legacy.getString(LEGACY_DATA_KEY, null));
        assertEquals(7, legacy.getInt("symbol_per_row", -1));

        // One-time: a later edit of the legacy store is never re-imported and
        // stored settings are not overwritten again.
        String editedLegacyJson = "[{\"n\":\"Beta\",\"d\":[{\"a\":0,\"b\":\"beta\",\"c\":\"beta()\"}]}]";
        legacy.edit().putString(LEGACY_DATA_KEY, editedLegacyJson)
                .putInt("symbol_per_row", 20).commit();
        SymbolDrawerMigration.migrate(context);
        assertEquals("Second migration must not import the edited legacy bundle",
                1, repository.loadGroups().size());
        assertEquals("Greek", repository.loadGroups().get(0).getTitle());
        assertEquals("Legacy JSON still untouched after the second run",
                editedLegacyJson, legacy.getString(LEGACY_DATA_KEY, null));
        assertEquals("Stored settings survive the second migration",
                7, settingsRepository.load().getSettings().getColumns());
    }

    @Test
    public void migrationWithExistingV2GroupsMarksCompleteWithoutImporting() throws Exception {
        // Seed V2 directly through the strict V1 codec so the store is
        // non-empty before the migration runs.
        String seededJson = "[{\"n\":\"Seeded\",\"d\":[{\"a\":0,\"b\":\"seed\",\"c\":\"seed()\"}]}]";
        repository().saveGroups(LegacySymbolDataCodec.INSTANCE.decode(seededJson));
        String legacyJson = "[{\"n\":\"Legacy\",\"d\":[{\"a\":0,\"b\":\"legacy\",\"c\":\"legacy()\"}]}]";
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit()
                .putString(LEGACY_DATA_KEY, legacyJson).commit();

        SymbolDrawerMigration.migrate(context);

        List<SymbolGroup> groups = repository().loadGroups();
        assertEquals("Existing V2 data blocks the legacy import", 1, groups.size());
        assertEquals("Seeded", groups.get(0).getTitle());
        assertTrue("Migration is marked complete so later launches skip it",
                repository().isLegacyMigrationComplete());
        assertEquals("Legacy JSON untouched even when its import was skipped",
                legacyJson, context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                        .getString(LEGACY_DATA_KEY, null));
    }

    // ------------------------------------------------------------------
    // SoraSymbolActionHost against the real shipped Sora editor
    // ------------------------------------------------------------------

    @Test
    public void macroInsertionExpandsMarkersAndRestoresSelection() throws Exception {
        TextEditorActivity activity = openEditor("hello world");
        UnifiedEditorFragment fragment = loaded(activity, "hello world");
        CodeEditor editor = fragment.getEditor();
        SoraSymbolActionHost host = newHost(editor);

        // Collapsed caret: $S/$E delimit the caret region of the expansion.
        runOnMain(() -> editor.setText(""));
        cursorAt(editor, 0, 0);
        assertTrue(hostAct(() -> host.insertText("a$Sb$Ec")));
        assertEquals("abc", text(editor));
        assertEquals("Caret region placed between the $S and $E markers",
                1, leftIndex(editor));
        assertEquals(2, rightIndex(editor));

        // Selection: $T substitutes the selected text and the selection is
        // restored onto the substituted region inside the expansion. A non-pair
        // marker (@) keeps Sora's symbol-pair auto-surround out of the picture.
        // In "hello @world@" the substituted region is indices 7..12.
        runOnMain(() -> editor.setText("hello world"));
        runOnMain(() -> editor.setSelectionRegion(0, 6, 0, 11));
        assertTrue(hostAct(() -> host.insertText("@$S$T$E@")));
        assertEquals("hello @world@", text(editor));
        assertEquals("Selection start restored onto the $T region", 7, leftIndex(editor));
        assertEquals("Selection end restored onto the $T region", 12, rightIndex(editor));

        // replaceSelection is a literal replacement by contract: no macro pass.
        runOnMain(() -> editor.setText(""));
        cursorAt(editor, 0, 0);
        assertTrue(hostAct(() -> host.replaceSelection("x$$y")));
        assertEquals("x$$y", text(editor));

        // insertText expands $$ into a literal dollar sign.
        runOnMain(() -> editor.setText(""));
        cursorAt(editor, 0, 0);
        assertTrue(hostAct(() -> host.insertText("x$$y")));
        assertEquals("x$y", text(editor));
        assertEquals("No $S/$E markers leaves the caret at the expansion end",
                3, leftIndex(editor));
        assertEquals(3, rightIndex(editor));

        // Surround keeps the original selection between prefix and suffix.
        runOnMain(() -> editor.setText("mid"));
        runOnMain(() -> editor.setSelectionRegion(0, 0, 0, 3));
        assertTrue(hostAct(() -> host.surroundSelection("[", "]")));
        assertEquals("[mid]", text(editor));
        assertEquals(1, leftIndex(editor));
        assertEquals(4, rightIndex(editor));

        // Uppercase transform via the host selection API.
        runOnMain(() -> editor.setText("word"));
        runOnMain(() -> editor.setSelectionRegion(0, 0, 0, 4));
        assertTrue(hostAct(() -> host.transformSelection(SelectionTransform.UPPERCASE)));
        assertEquals("WORD", text(editor));
        assertEquals(0, leftIndex(editor));
        assertEquals(4, rightIndex(editor));

        // The drawer applies exactly the requested text, without Sora auto-pairing.
        runOnMain(() -> editor.setText("word"));
        runOnMain(() -> editor.setSelectionRegion(0, 0, 0, 4));
        assertTrue(hostAct(() -> host.insertText("(")));
        assertEquals("(", text(editor));
        assertEquals(1, rightIndex(editor));
    }

    @Test
    public void macroLineWordIndentContextExpansions() throws Exception {
        TextEditorActivity activity = openEditor("    alpha beta");
        UnifiedEditorFragment fragment = loaded(activity, "    alpha beta");
        CodeEditor editor = fragment.getEditor();
        SoraSymbolActionHost host = newHost(editor);

        // Caret inside "beta": $W is the word at the caret, $L the whole line
        // and $I the line's leading indent; with no $S/$E markers the caret
        // rests after the expansion.
        cursorAt(editor, 0, 11);
        assertTrue(hostAct(() -> host.insertText("<$W|$L|$I>")));
        assertEquals("    alpha b<beta|    alpha beta|    >eta", text(editor));
        assertEquals(37, leftIndex(editor));
        assertEquals(37, rightIndex(editor));
    }

    @Test
    public void unicodeDeletionTreatsSurrogatePairsAsOneCharacter() throws Exception {
        TextEditorActivity activity = openEditor("u");
        UnifiedEditorFragment fragment = loaded(activity, "u");
        CodeEditor editor = fragment.getEditor();
        SoraSymbolActionHost host = newHost(editor);
        String emoji = new String(Character.toChars(0x1F600)); // astral plane

        // Forward deletion across a supplementary character removes both units.
        runOnMain(() -> editor.setText("a" + emoji + "b"));
        cursorAt(editor, 0, 1);
        assertTrue("Forward delete must remove the whole surrogate pair",
                hostAct(() -> host.delete(DeleteKind.FORWARD)));
        assertEquals("ab", text(editor));

        // Backward deletion across the pair also removes both units.
        runOnMain(() -> editor.setText("a" + emoji + "b"));
        cursorAt(editor, 0, 3);
        assertTrue("Backward delete must remove the whole surrogate pair",
                hostAct(() -> host.delete(DeleteKind.BACKWARD)));
        assertEquals("ab", text(editor));

        // BMP characters still delete exactly one code unit.
        runOnMain(() -> editor.setText("a" + emoji + "b"));
        cursorAt(editor, 0, 1);
        assertTrue(hostAct(() -> host.delete(DeleteKind.BACKWARD)));
        assertEquals(emoji + "b", text(editor));

        // Boundaries: nothing to delete returns false without changes.
        runOnMain(() -> editor.setText("a" + emoji));
        cursorAt(editor, 0, 0);
        assertFalse("Backward delete at document start must be refused",
                hostAct(() -> host.delete(DeleteKind.BACKWARD)));
        cursorAt(editor, 0, 3);
        assertFalse("Forward delete at document end must be refused",
                hostAct(() -> host.delete(DeleteKind.FORWARD)));
        assertEquals("a" + emoji, text(editor));

        // CURRENT_LINE delegates to the line.delete command; Sora keeps one
        // line separator when a middle line is removed.
        runOnMain(() -> editor.setText("alpha\nbeta\ngamma"));
        cursorAt(editor, 1, 2);
        assertTrue(hostAct(() -> host.delete(DeleteKind.CURRENT_LINE)));
        assertEquals("alpha\ngamma", text(editor));

        // TO_LINE_START / TO_LINE_END delete to the caret line boundaries.
        runOnMain(() -> editor.setText("keepHead keepTail"));
        cursorAt(editor, 0, 8);
        assertTrue(hostAct(() -> host.delete(DeleteKind.TO_LINE_START)));
        assertEquals(" keepTail", text(editor));
        runOnMain(() -> editor.setText("keepHead keepTail"));
        cursorAt(editor, 0, 8);
        assertTrue(hostAct(() -> host.delete(DeleteKind.TO_LINE_END)));
        assertEquals("keepHead", text(editor));
    }

    @Test
    public void readOnlyRefusesAllMutationsIncludingUndoRedoAndCustom() throws Exception {
        TextEditorActivity activity = openEditor("keep");
        UnifiedEditorFragment fragment = loaded(activity, "keep");
        CodeEditor editor = fragment.getEditor();
        SoraSymbolActionHost host = newHost(editor);

        cursorAt(editor, 0, 4); // deterministic caret: the file-load position is not guaranteed
        assertTrue(hostAct(() -> host.insertText("x")));
        assertEquals("keepx", text(editor));
        final boolean[] undoable = new boolean[1];
        runOnMain(() -> undoable[0] = editor.canUndo());
        assertTrue("History must exist before the read-only phase", undoable[0]);

        runOnMain(() -> editor.setEditable(false));

        // Every mutating host entry point must refuse without touching the text.
        assertFalse(hostAct(() -> host.insertText("y")));
        assertFalse(hostAct(() -> host.replaceSelection("y")));
        assertFalse(hostAct(() -> host.surroundSelection("[", "]")));
        for (DeleteKind kind : DeleteKind.values()) {
            assertFalse("Read-only must refuse DeleteKind." + kind,
                    hostAct(() -> host.delete(kind)));
        }
        for (SelectionTransform transform : SelectionTransform.values()) {
            assertFalse("Read-only must refuse transform " + transform,
                    hostAct(() -> host.transformSelection(transform)));
        }
        assertFalse(hostAct(() -> host.performClipboard(ClipboardOperation.CUT)));
        assertFalse(hostAct(() -> host.performClipboard(ClipboardOperation.PASTE)));
        String[] mutatingCommands = {"undo", "history.undo", "redo", "history.redo",
                "insert_payload", "line.delete", "line.cut", "line.clear", "line.duplicate",
                "line.toggleComment", "line.replace", "line.uppercase", "line.indent",
                "selection.delete", "selection.uppercase", "cursor.deleteWordBackward",
                "document.trimTrailingWhitespace", "unknown.command"};
        for (String command : mutatingCommands) {
            assertFalse("Read-only must refuse editor command '" + command + "'",
                    hostAct(() -> host.executeEditorCommand(command, "payload")));
        }
        // Custom mpManager actions mutate the document, so 101/102 are refused
        // while 100 (search) is a navigation aid and stays allowed.
        String mpPayload = "{\"mpManager\":true,\"text\":\"needle\",\"secondText\":\"repl\",\"options\":\"ra\"}";
        assertFalse(hostAct(() -> host.executeExtension("legacy.action.101", mpPayload)));
        assertFalse(hostAct(() -> host.executeExtension("legacy.action.102", mpPayload)));
        assertEquals("No custom action may execute while read-only", 0, customIds.size());

        assertEquals("Refusals must not alter the document", "keepx", text(editor));

        // Allowed read-only operations stay available (the refusal is specific
        // to mutation, not a blanket lockout).
        assertTrue(hostAct(() -> host.performClipboard(ClipboardOperation.COPY)));
        assertTrue("line.copy is allowed read-only",
                hostAct(() -> host.executeEditorCommand("line.copy", null)));
        assertTrue(hostAct(() -> host.executeEditorCommand("select_all", null)));
        final boolean[] selected = new boolean[1];
        runOnMain(() -> selected[0] = editor.getCursor().isSelected());
        assertTrue("select_all must still work read-only", selected[0]);
        assertTrue(hostAct(() -> host.executeEditorCommand("selection.selectAll", null)));
        assertTrue(hostAct(() -> host.moveCursor(CursorTarget.LEFT)));
        assertTrue("Registry cursor navigation stays allowed",
                hostAct(() -> host.executeEditorCommand("cursor.lineStart", null)));
        assertTrue("Search (100) stays allowed read-only",
                hostAct(() -> host.executeExtension("legacy.action.100", null)));
        assertEquals(1, customIds.size());
        assertEquals(100, customIds.get(0)[0]);

        // Undo history was intact but never consumed by the refusals.
        runOnMain(() -> editor.setEditable(true));
        assertTrue("Undo must work again once editing is re-enabled",
                hostAct(() -> host.executeEditorCommand("undo", null)));
        assertEquals("Undo after re-enabling restores the pre-insert document",
                "keep", text(editor));
    }

    @Test
    public void lineCopyAndCutSemanticsUseTheCurrentLine() throws Exception {
        TextEditorActivity activity = openEditor("alpha\nbeta\ngamma");
        UnifiedEditorFragment fragment = loaded(activity, "alpha\nbeta\ngamma");
        CodeEditor editor = fragment.getEditor();
        SoraSymbolActionHost host = newHost(editor);

        runOnMain(() -> editor.setSelectionRegion(1, 2, 1, 2));
        setClipboard("sentinel");
        assertTrue("line.copy copies the caret line",
                hostAct(() -> host.executeEditorCommand("line.copy", null)));
        assertEquals("Clipboard must hold the caret line text", "beta", clipboardText());
        assertEquals("line.copy must not modify the document",
                "alpha\nbeta\ngamma", text(editor));

        assertTrue("line.cut copies and removes the caret line",
                hostAct(() -> host.executeEditorCommand("line.cut", null)));
        assertEquals("Clipboard holds the removed line", "beta", clipboardText());
        assertEquals("alpha\ngamma", text(editor));
        assertEquals("Caret rests where the line was removed", 6, leftIndex(editor));

        // Plain clipboard copy/cut follow the selection.
        runOnMain(() -> editor.setSelectionRegion(0, 0, 0, 3));
        setClipboard("sentinel");
        assertTrue(hostAct(() -> host.performClipboard(ClipboardOperation.COPY)));
        assertEquals("alp", clipboardText());
        assertEquals("alpha\ngamma", text(editor));
        assertTrue(hostAct(() -> host.performClipboard(ClipboardOperation.CUT)));
        assertEquals("alp", clipboardText());
        assertEquals("Cut removes exactly the selected range: alpha minus alp leaves ha",
                "ha\ngamma", text(editor));
    }

    @Test
    public void textActionWindowToleratesMissingAndDisabledPasteAndCutButtons() throws Exception {
        // The floating menu persists in editor_prefs and may omit or disable the
        // paste/cut buttons (Edit floating menus screen). TextActionWindow only
        // creates buttons for non-disabled ids, so its mutators must tolerate
        // the absent views instead of crashing (guarded updatePasteButtonState
        // and updateCutButtonVisibility).
        String missingButtons = "[{\"id\":\"panel_btn_select_all\",\"title\":\"Select All\",\"disabled\":false},"
                + "{\"id\":\"panel_btn_copy\",\"title\":\"Copy\",\"disabled\":false}]";
        context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE).edit()
                .putString("menu_order", missingButtons).commit();
        TextEditorActivity activity = openEditor("clip");
        UnifiedEditorFragment fragment = loaded(activity, "clip");
        CodeEditor editor = fragment.getEditor();
        TextActionWindow window = awaitActionWindow(fragment);

        Map<?, ?> buttons = (Map<?, ?>) field(window, "buttonMap");
        assertNotNull(buttons.get("panel_btn_copy"));
        assertNull("Menu without paste must simply omit the paste button",
                buttons.get("panel_btn_paste"));
        assertNull("Menu without cut must simply omit the cut button",
                buttons.get("panel_btn_cut"));

        // Selection changes drive the mutators through the real event pipeline;
        // the direct reflective call pins the same guarantee deterministically.
        runOnMain(() -> editor.setSelectionRegion(0, 0, 0, 4));
        instrumentation.waitForIdleSync();
        invokeUpdateButtonStates(window);

        // Case B: buttons present but disabled are omitted exactly like absent ones.
        runOnMain(activity::finish);
        String disabledButtons = "[{\"id\":\"panel_btn_select_all\",\"title\":\"Select All\",\"disabled\":false},"
                + "{\"id\":\"panel_btn_copy\",\"title\":\"Copy\",\"disabled\":false},"
                + "{\"id\":\"panel_btn_paste\",\"title\":\"Paste\",\"disabled\":true},"
                + "{\"id\":\"panel_btn_cut\",\"title\":\"Cut\",\"disabled\":true}]";
        context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE).edit()
                .putString("menu_order", disabledButtons).commit();
        TextEditorActivity second = openEditor("clip");
        UnifiedEditorFragment secondFragment = loaded(second, "clip");
        TextActionWindow secondWindow = awaitActionWindow(secondFragment);
        Map<?, ?> secondButtons = (Map<?, ?>) field(secondWindow, "buttonMap");
        assertNull("Disabled paste must be omitted from the action window",
                secondButtons.get("panel_btn_paste"));
        assertNull("Disabled cut must be omitted from the action window",
                secondButtons.get("panel_btn_cut"));
        assertNotNull(secondButtons.get("panel_btn_copy"));
        invokeUpdateButtonStates(secondWindow);
    }

    // ------------------------------------------------------------------
    // Real drawer UI: cells, short/long callbacks, manager/settings screens
    // ------------------------------------------------------------------

    @Test
    public void drawerCellsDispatchShortLongLineAndManagerActionsThroughRealCallbacks() throws Exception {
        String drawerJson = "[{\"n\":\"Bar\",\"d\":["
                + "{\"a\":0,\"b\":\"A1\",\"c\":\"s1\",\"d\":0,\"e\":\"l1\"},"
                + "{\"a\":0,\"b\":\"A2\",\"c\":\"s2\",\"d\":0,\"e\":\"l2\"},"
                + "{\"a\":33,\"b\":\"CP\"},"
                + "{\"a\":34,\"b\":\"CX\"},"
                + "{\"a\":22,\"b\":\"GEAR\"}"
                + "]}]";
        repository().saveGroups(LegacySymbolDataCodec.INSTANCE.decode(drawerJson));

        TextEditorActivity activity = openEditor("seed");
        UnifiedEditorFragment fragment = loaded(activity, "seed");
        CodeEditor editor = fragment.getEditor();
        SymbolDrawerView bar = symbolBar(fragment);

        // Short tap dispatches the short action through the fragment callback.
        cursorAt(editor, 0, 0);
        tap(awaitCell(bar, "A1"));
        assertEquals("Short tap inserts the short text via the real host",
                "s1seed", text(editor));

        // Real touch-stream long press dispatches the long action; the adapter
        // clears the pressed state before dispatch so the TextView's ACTION_UP
        // must not also fire the short action.
        longPress(awaitCell(bar, "A2"));
        assertEquals("Long tap inserts the long text via the real host",
                "s1l2seed", text(editor));
        assertFalse("Long press must dispatch ONLY the long action; if 's2' leaked in "
                        + "the no-double-dispatch guard is broken: " + text(editor),
                text(editor).contains("s2"));

        // line.copy (33) and line.cut (34) through real cells.
        cursorAt(editor, 0, 0);
        setClipboard("sentinel");
        tap(awaitCell(bar, "CP"));
        assertEquals("s1l2seed", clipboardText());
        assertEquals("s1l2seed", text(editor));
        tap(awaitCell(bar, "CX"));
        assertEquals("s1l2seed", clipboardText());
        assertEquals("Single-line document is emptied by the line cut", "", text(editor));

        // OpenManager (22) through a real cell opens the V2 manager activity
        // via the fragment's registered callback.
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                SymbolDrawerActivity.class.getName(), null, false);
        tap(findGearCell(bar));
        SymbolDrawerActivity manager = waitForMonitor(monitor);
        assertEquals("Manager activity title",
                context.getString(R.string.edpref_bottom_bar), manager.getTitle());
        assertFalse("Manager mode must not carry the settings extra",
                manager.getIntent().getBooleanExtra(SymbolDrawerActivity.EXTRA_SETTINGS, false));
        assertNotNull("Manager screen lists the seeded V2 group",
                awaitTextView(manager, "Bar"));
    }

    @Test
    public void v2ManagerAndSettingsScreensOpenViaDrawerCallbacks() throws Exception {
        String drawerJson = "[{\"n\":\"Bar\",\"d\":[{\"a\":0,\"b\":\"A1\",\"c\":\"s1\"}]}]";
        repository().saveGroups(LegacySymbolDataCodec.INSTANCE.decode(drawerJson));

        TextEditorActivity activity = openEditor("seed");
        UnifiedEditorFragment fragment = loaded(activity, "seed");
        SymbolDrawerView bar = symbolBar(fragment);

        Function0<Unit> openManager = bar.getOnOpenManagerListener();
        assertNotNull("Fragment must register the manager callback on the drawer", openManager);
        Instrumentation.ActivityMonitor managerMonitor = instrumentation.addMonitor(
                SymbolDrawerActivity.class.getName(), null, false);
        runOnMain(openManager::invoke);
        SymbolDrawerActivity manager = waitForMonitor(managerMonitor);
        assertEquals(context.getString(R.string.edpref_bottom_bar), manager.getTitle());
        assertFalse(manager.getIntent().getBooleanExtra(SymbolDrawerActivity.EXTRA_SETTINGS, false));
        assertNotNull("Manager screen lists the seeded group",
                awaitTextView(manager, "Bar"));

        Function0<Unit> openSettings = bar.getOnOpenSettingsListener();
        assertNotNull("Fragment must register the settings callback on the drawer", openSettings);
        Instrumentation.ActivityMonitor settingsMonitor = instrumentation.addMonitor(
                SymbolDrawerActivity.class.getName(), null, false);
        runOnMain(openSettings::invoke);
        SymbolDrawerActivity settings = waitForMonitor(settingsMonitor);
        assertTrue("Settings route must carry the settings extra",
                settings.getIntent().getBooleanExtra(SymbolDrawerActivity.EXTRA_SETTINGS, false));
        assertEquals(context.getString(R.string.editor_settings), settings.getTitle());
        assertTrue("Settings screen renders setting rows",
                countTextViews(settings.getWindow().getDecorView()) > 0);
    }

    @Test
    public void drawerReloadsGroupsAfterLifecycleResumeAndKeepsImeGeometry() throws Exception {
        String drawerJson = "[{\"n\":\"Bar\",\"d\":[{\"a\":0,\"b\":\"A1\",\"c\":\"s1\"}]}]";
        repository().saveGroups(LegacySymbolDataCodec.INSTANCE.decode(drawerJson));

        TextEditorActivity activity = openEditor("seed");
        UnifiedEditorFragment fragment = loaded(activity, "seed");
        CodeEditor editor = fragment.getEditor();
        SymbolDrawerView bar = symbolBar(fragment);
        assertNotNull(awaitCell(bar, "A1"));

        // Groups edited while the activity is in the background must be visible
        // after resume (fragment.onResume -> loadBottomBarFunctions ->
        // reloadGroups/onHostResume, backed by the repository observer).
        String updatedJson = "[{\"n\":\"Bar\",\"d\":["
                + "{\"a\":0,\"b\":\"A1\",\"c\":\"s1\"},"
                + "{\"a\":0,\"b\":\"B1\",\"c\":\"s2\"}"
                + "]}]";
        repository().saveGroups(LegacySymbolDataCodec.INSTANCE.decode(updatedJson));
        shell("input keyevent KEYCODE_HOME");
        Intent relaunch = new Intent(context, TextEditorActivity.class)
                .putExtra("path", currentFile.getPath())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // startActivitySync cannot deliver an intent to an already-running
        // singleTop instance (it waits for a creation barrier that never
        // completes), so start on the main thread and await the real resume
        // callback; the assertion still requires the same instance.
        resumed.set(null);
        runOnMain(() -> context.startActivity(relaunch));
        awaitResume(activity);
        assertNotNull("Reloaded group must expose the new symbol after resume",
                awaitCell(bar, "B1"));
        assertNotNull(awaitCell(bar, "A1"));

        // The fragment pins the drawer's IME geometry: the bar itself must not
        // be double-offset behind the keyboard.
        final float[] translation = new float[1];
        runOnMain(() -> translation[0] = bar.getTranslationY());
        assertEquals("Symbol bar must not carry a manual IME offset",
                0f, translation[0], 0.001f);
        assertEquals("Document untouched by the lifecycle trip", "seed", text(editor));
    }

    // ------------------------------------------------------------------
    // Fixture helpers (EditorRegressionTest style)
    // ------------------------------------------------------------------

    private TextEditorActivity openEditor(String content) throws Exception {
        File file = File.createTempFile("symbol-regression-", ".txt", context.getCacheDir());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        files.add(file);
        currentFile = file;
        TextEditorActivity activity = (TextEditorActivity) instrumentation.startActivitySync(
                new Intent(context, TextEditorActivity.class)
                        .putExtra("path", file.getPath())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertNotNull(activity);
        activities.add(activity);
        return activity;
    }

    private UnifiedEditorFragment loaded(TextEditorActivity activity, String expected) throws Exception {
        UnifiedEditorFragment fragment = (UnifiedEditorFragment) field(activity, "editorFragment");
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<String> text = new AtomicReference<>();
            runOnMain(() -> text.set(fragment.getCode()));
            if (expected.equals(text.get())) return fragment;
            Thread.sleep(20);
        }
        fail("Text did not finish loading");
        return fragment;
    }

    private SymbolDrawerView symbolBar(UnifiedEditorFragment fragment) throws Exception {
        SymbolDrawerView bar = (SymbolDrawerView) field(fragment, "symbolInputView");
        assertNotNull("UnifiedEditorFragment must own the V2 SymbolDrawerView", bar);
        return bar;
    }

    private TextActionWindow awaitActionWindow(UnifiedEditorFragment fragment) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<TextActionWindow> ref = new AtomicReference<>();
            runOnMain(() -> {
                try {
                    Object candidate = field(fragment, "currentActionWindow");
                    if (candidate instanceof TextActionWindow) ref.set((TextActionWindow) candidate);
                } catch (Exception ignored) {
                }
            });
            if (ref.get() != null) return ref.get();
            Thread.sleep(20);
        }
        fail("TextActionWindow was never constructed");
        return null;
    }

    /** Runs the private mutator batch on the main thread, surfacing any crash. */
    private void invokeUpdateButtonStates(TextActionWindow window) throws Exception {
        java.lang.reflect.Method update = TextActionWindow.class.getDeclaredMethod("updateButtonStates");
        update.setAccessible(true);
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMain(() -> {
            try {
                update.invoke(window);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) throw new AssertionError("updateButtonStates crashed", error.get());
    }

    /** Waits for the lifecycle callback to report this exact instance resumed. */
    private void awaitResume(Activity activity) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        synchronized (resumeLock) {
            while (resumed.get() != activity && System.currentTimeMillis() < deadline) {
                resumeLock.wait(1000);
            }
        }
        assertTrue("Activity did not resume (or was recreated instead of resumed)",
                resumed.get() == activity);
    }

    private SoraSymbolActionHost newHost(CodeEditor editor) {
        return new SoraSymbolActionHost(editor, "//", () -> { }, recorder);
    }

    private SharedPreferencesSymbolGroupRepository repository() {
        return new SharedPreferencesSymbolGroupRepository(context, V2_GROUPS_PREFS);
    }

    private SharedPreferencesSymbolDrawerSettingsRepository settingsRepository() {
        return new SharedPreferencesSymbolDrawerSettingsRepository(context, V2_SETTINGS_PREFS,
                context.getSharedPreferences(V2_SETTINGS_PREFS, Context.MODE_PRIVATE));
    }

    /** Sora is main-thread only; run the host call there and collect the result. */
    private boolean hostAct(Supplier<Boolean> call) {
        AtomicBoolean result = new AtomicBoolean();
        runOnMain(() -> result.set(call.get()));
        return result.get();
    }

    private void runOnMain(Runnable action) {
        instrumentation.runOnMainSync(action);
    }

    private void cursorAt(CodeEditor editor, int line, int column) {
        runOnMain(() -> editor.setSelectionRegion(line, column, line, column));
    }

    private String text(CodeEditor editor) {
        AtomicReference<String> ref = new AtomicReference<>();
        runOnMain(() -> ref.set(editor.getText().toString()));
        return ref.get();
    }

    private int leftIndex(CodeEditor editor) {
        AtomicReference<Integer> ref = new AtomicReference<>();
        runOnMain(() -> ref.set(editor.getCursor().getLeft()));
        return ref.get();
    }

    private int rightIndex(CodeEditor editor) {
        AtomicReference<Integer> ref = new AtomicReference<>();
        runOnMain(() -> ref.set(editor.getCursor().getRight()));
        return ref.get();
    }

    private void setClipboard(String content) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("text", content));
    }

    private String clipboardText() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence text = clip.getItemAt(0).getText();
        return text == null ? null : text.toString();
    }

    private TextView awaitCell(SymbolDrawerView bar, String label) throws Exception {
        AtomicReference<TextView> ref = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10000;
        while (ref.get() == null && System.currentTimeMillis() < deadline) {
            runOnMain(() -> ref.set(findText(bar, label)));
            if (ref.get() == null) Thread.sleep(30);
        }
        assertNotNull("Symbol cell '" + label + "' never appeared in the drawer", ref.get());
        return ref.get();
    }

    private TextView findGearCell(SymbolDrawerView bar) throws Exception {
        AtomicReference<TextView> ref = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10000;
        while (ref.get() == null && System.currentTimeMillis() < deadline) {
            runOnMain(() -> ref.set(findGearInPager(bar)));
            if (ref.get() == null) Thread.sleep(30);
        }
        assertNotNull("Manager (OpenManager) cell never appeared in the drawer", ref.get());
        return ref.get();
    }

    private TextView awaitTextView(Activity activity, String label) throws Exception {
        AtomicReference<TextView> ref = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10000;
        while (ref.get() == null && System.currentTimeMillis() < deadline) {
            runOnMain(() -> ref.set(findText(activity.getWindow().getDecorView(), label)));
            if (ref.get() == null) Thread.sleep(30);
        }
        return ref.get();
    }

    private static TextView findText(View root, String label) {
        if (root instanceof TextView) {
            TextView view = (TextView) root;
            if (label.contentEquals(view.getText())) return view;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * The OpenManager cell renders an empty label plus a content description
     * inside the pager's RecyclerView. Scoping to RecyclerViews keeps the
     * indicator bar (which also hosts a settings affordance) out of the match.
     */
    private static TextView findGearInPager(View root) {
        if (root instanceof RecyclerView) return findEmptyTextWithDescription(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findGearInPager(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findEmptyTextWithDescription(View root) {
        if (root instanceof TextView) {
            TextView view = (TextView) root;
            if (view.getText().length() == 0 && view.getContentDescription() != null) return view;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findEmptyTextWithDescription(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int countTextViews(View root) {
        if (root instanceof TextView) return 1;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            int count = 0;
            for (int i = 0; i < group.getChildCount(); i++) count += countTextViews(group.getChildAt(i));
            return count;
        }
        return 0;
    }

    private void tap(TextView cell) {
        runOnMain(cell::performClick);
    }

    /**
     * Real touch stream so the drawer's own timing-based long-press detector
     * (SymbolCellTouchListener) fires; performLongClick() would bypass it.
     */
    private void longPress(TextView cell) throws Exception {
        long downTime = SystemClock.uptimeMillis();
        float x = cell.getWidth() / 2f;
        float y = cell.getHeight() / 2f;
        runOnMain(() -> cell.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)));
        Thread.sleep(LONG_PRESS_WAIT_MS);
        long upTime = SystemClock.uptimeMillis();
        runOnMain(() -> cell.dispatchTouchEvent(
                MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)));
        Thread.sleep(100);
    }

    private SymbolDrawerActivity waitForMonitor(Instrumentation.ActivityMonitor monitor) throws Exception {
        Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, 15000);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Expected activity never resumed", activity);
        activities.add(activity);
        return (SymbolDrawerActivity) activity;
    }

    private Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void shell(String command) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[4096];
            while (stream.read(buffer) != -1) { /* wait for the shell command */ }
        }
        SystemClock.sleep(300);
    }
}
