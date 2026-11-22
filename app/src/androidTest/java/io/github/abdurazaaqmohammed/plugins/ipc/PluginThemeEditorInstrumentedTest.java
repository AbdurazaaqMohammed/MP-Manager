package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;
import io.github.abdurazaaqmohammed.ui.common.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.ui.editor.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.editor.UnifiedEditorFragment;
import io.github.rosemoe.sora.widget.CodeEditor;

/** Live theme changes must not reconstruct unsaved text-editor buffers. */
public class PluginThemeEditorInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private TextEditorActivity activity;
    private String previousTheme;

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        assertTrue("Refusing a non-isolated package", context.getPackageName()
                .endsWith(".editorregression"));
        previousTheme = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(ThemeRegistry.PREF_KEY, null);
        ThemePacks.deactivate(context);
        instrumentation.runOnMainSync(() -> ThemeRegistry.setCurrentId(context, BuiltInThemes.LIGHT_ID));
    }

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(() -> {
                // Bypass editor-owned finish navigation; teardown only this isolated task.
                activity.finishAndRemoveTask();
            });
        }
        ThemePacks.deactivate(context);
        if (previousTheme != null) {
            instrumentation.runOnMainSync(() -> ThemeRegistry.setCurrentId(context, previousTheme));
        } else {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .remove(ThemeRegistry.PREF_KEY).apply();
        }
    }

    @Test
    public void lightToDarkKeepsEditorInstanceTextSelectionAndUndo() throws Exception {
        activity = (TextEditorActivity) instrumentation.startActivitySync(
                new Intent(context, TextEditorActivity.class).setAction(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, "original\nsecond line\n")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CodeEditor editor = awaitEditor();
        AtomicReference<Object> content = new AtomicReference<>();
        String unsaved = "original\nUNSAVED buffer\nsecond line\n";
        instrumentation.runOnMainSync(() -> {
            editor.getText().insert(1, 0, "UNSAVED buffer\n");
            editor.setSelectionRegion(1, 0, 1, 7);
            assertEquals(unsaved, editor.getText().toString());
            assertTrue(editor.canUndo());
            content.set(editor.getText());
            ThemeRegistry.setCurrentId(activity, BuiltInThemes.DARK_ID);
            ThemeRegistry.reload(activity);
        });
        instrumentation.waitForIdleSync();
        SystemClock.sleep(500);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            assertFalse("The live editor activity must not be destroyed", activity.isDestroyed());
            assertSame("A theme change must keep the live Content object", content.get(), editor.getText());
            assertEquals(unsaved, editor.getText().toString());
            assertEquals(1, editor.getCursor().getLeftLine());
            assertEquals(0, editor.getCursor().getLeftColumn());
            assertEquals(1, editor.getCursor().getRightLine());
            assertEquals(7, editor.getCursor().getRightColumn());
            assertTrue("Undo history must survive the theme", editor.canUndo());
        });
    }

    @Test
    public void themeV2ImportStylesExistingBufferWithoutReplacingIt() throws Exception {
        activity = (TextEditorActivity) instrumentation.startActivitySync(
                new Intent(context, TextEditorActivity.class).setAction(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, "Keep this buffer\n")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CodeEditor editor = awaitEditor();
        AtomicReference<Object> content = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> content.set(editor.getText()));
        ThemePacks.Entry sample = null;
        for (ThemePacks.Entry entry : ThemePacks.discover(context)) {
            if ("com.example.mpthemesample".equals(entry.packageName)) sample = entry;
        }
        assertNotNull("Install samples/theme-sample before running this test", sample);
        assertTrue(ThemePacks.importAndSelect(context, sample));
        instrumentation.waitForIdleSync();
        SystemClock.sleep(500);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            assertFalse(activity.isDestroyed());
            assertSame(content.get(), editor.getText());
            ThemePacks.Snapshot pack = ThemePacks.active(activity);
            assertNotNull(pack);
            Integer background = pack.colors.get("editor_background");
            assertNotNull(background);
            assertEquals(background.intValue(), editor.getColorScheme().getColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND));
        });
    }

    @Test
    public void reimportingIdenticalDataDoesNotBumpLiveThemeRevision() {
        ThemePacks.Entry sample = null;
        for (ThemePacks.Entry entry : ThemePacks.discover(context)) {
            if ("com.example.mpthemesample".equals(entry.packageName)) sample = entry;
        }
        assertNotNull(sample);
        assertTrue(ThemePacks.importAndSelect(context, sample));
        instrumentation.waitForIdleSync();
        long revision = ThemePacks.active(context).revision;
        int epoch = ThemeRegistry.currentEpoch();
        String wallpaper = ThemePacks.active(context).wallpaper.getAbsolutePath();
        assertTrue(ThemePacks.importAndSelect(context, sample));
        instrumentation.waitForIdleSync();
        assertEquals(revision, ThemePacks.active(context).revision);
        assertEquals(epoch, ThemeRegistry.currentEpoch());
        assertEquals(wallpaper, ThemePacks.active(context).wallpaper.getAbsolutePath());
    }

    private CodeEditor awaitEditor() throws Exception {
        AtomicReference<CodeEditor> ref = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(io.github.abdurazaaqmohammed.MPManager.R.id.editor_container);
                if (fragment instanceof UnifiedEditorFragment) {
                    CodeEditor editor = ((UnifiedEditorFragment) fragment).getEditor();
                    if (editor != null && editor.getText().length() > 0) ref.set(editor);
                }
            });
            if (ref.get() != null) return ref.get();
            SystemClock.sleep(50);
        }
        throw new AssertionError("Editor buffer never initialized");
    }
}
