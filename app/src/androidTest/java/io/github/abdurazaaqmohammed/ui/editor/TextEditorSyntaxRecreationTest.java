package io.github.abdurazaaqmohammed.ui.editor;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.editor.ColorPreviewLanguageWrapper;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Instrumentation regression for the intermittent "XML/AndroidManifest.xml opens
 * without highlighting" bug in UnifiedEditorFragment#applySyntax.
 *
 * Two live editor paths are exercised against a real CodeEditor:
 * <ol>
 *   <li>A cold open of an XML file: the TextMate grammar must be applied and
 *   the editor must actually produce styled spans (more than the single default
 *   span per line that a plain language yields).</li>
 *   <li>The same fragment instance getting its view destroyed and recreated
 *   (detach + attach, the FragmentStateAdapter page-switch pattern): the old
 *   code deduped the re-apply against the previously requested scope and left
 *   the recreated view with no language at all.</li>
 * </ol>
 *
 * The assertions inspect the language actually attached to the editor view, its
 * TextMate delegate, and the resulting spans — a scope-only or wrapper-only
 * check would not catch the fallback-to-plain failure mode.
 */
public class TextEditorSyntaxRecreationTest {

    private static final long TIMEOUT_MS = 15000;

    private TextEditorActivity activity;
    private File xmlFile;
    private String xmlContent;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        xmlFile = new File(context.getCacheDir(), "AndroidManifest_" + System.nanoTime() + ".xml");
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n");
        xml.append("    <application android:label=\"@string/app_name\">\n");
        xml.append("        <activity android:name=\".MainActivity\" />\n");
        xml.append("    </application>\n");
        xml.append("</manifest>\n");
        xmlContent = xml.toString();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(xmlFile), StandardCharsets.UTF_8)) {
            writer.write(xmlContent);
        }
        // Start from a clean session so no restored tab races the assertions.
        sessionFile(xmlFile).delete();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (activity != null && !activity.isFinishing()) activity.finish();
            });
            activity = null;
        }
        if (xmlFile != null) {
            sessionFile(xmlFile).delete();
            //noinspection ResultOfMethodCallIgnored
            xmlFile.delete();
        }
    }

    /** Mirrors TextEditorActivity.sessionFile(): per-source hash in the app cache. */
    private static File sessionFile(File source) {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "text_editor_session_" + Integer.toHexString(source.getAbsolutePath().hashCode()) + ".json");
    }

    private void launchEditor() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                TextEditorActivity.class.getName(), null, false);
        Intent intent = new Intent(context, TextEditorActivity.class);
        intent.putExtra("path", xmlFile.getAbsolutePath());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        activity = (TextEditorActivity) instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MS);
        instrumentation.removeMonitor(monitor);
        assertNotNull("TextEditorActivity did not come up", activity);
    }

    /** {editor, language, textMateDelegate, textLength, maxSpansPerLine} captured on the UI thread. */
    private Object[] snapshot() {
        AtomicReference<Object[]> out = new AtomicReference<>(new Object[]{null, null, null, -1, 0});
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            UnifiedEditorFragment f = findEditorFragment();
            if (f == null || f.getEditor() == null) return;
            CodeEditor editor = f.getEditor();
            Language language = editor.getEditorLanguage();
            Object delegate = null;
            if (language instanceof ColorPreviewLanguageWrapper) {
                try {
                    Field field = ColorPreviewLanguageWrapper.class.getDeclaredField("delegate");
                    field.setAccessible(true);
                    delegate = field.get(language);
                } catch (Exception ignored) {
                }
            }
            int maxSpans = 0;
            int lines = editor.getText().getLineCount();
            for (int line = 0; line < Math.min(lines, 6); line++) {
                try {
                    List<Span> spans = editor.getSpansForLine(line);
                    if (spans != null) maxSpans = Math.max(maxSpans, spans.size());
                } catch (Exception ignored) {
                }
            }
            out.set(new Object[]{editor, language, delegate, editor.getText().length(), maxSpans});
        });
        return out.get();
    }

    private UnifiedEditorFragment findEditorFragment() {
        if (activity == null) return null;
        Fragment f = activity.getSupportFragmentManager().findFragmentById(R.id.editor_container);
        return f instanceof UnifiedEditorFragment ? (UnifiedEditorFragment) f : null;
    }

    private Object[] poll(PollCondition condition) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        Object[] snapshot = snapshot();
        while (!condition.matches(snapshot) && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(150);
            snapshot = snapshot();
        }
        return snapshot;
    }

    private interface PollCondition {
        boolean matches(Object[] snapshot);
    }

    private static boolean highlighted(Object[] snapshot, boolean requireText) {
        if (!(snapshot[1] instanceof ColorPreviewLanguageWrapper)) return false;
        if (!(snapshot[2] instanceof TextMateLanguage)) return false;
        if (requireText && ((Integer) snapshot[3]) <= 0) return false;
        // A plain language emits a single default span per line; TextMate XML
        // styling yields several spans per manifest line (tag, attribute names,
        // values, text).
        return ((Integer) snapshot[4]) > 1;
    }

    private static void assertHighlighted(String stage, Object[] snapshot, boolean requireText) {
        assertTrue(stage + ": editor language is " + snapshot[1]
                        + " (expected a ColorPreviewLanguageWrapper)",
                snapshot[1] instanceof ColorPreviewLanguageWrapper);
        assertTrue(stage + ": wrapper delegate is " + snapshot[2]
                        + " (expected TextMateLanguage)",
                snapshot[2] instanceof TextMateLanguage);
        if (requireText) {
            assertTrue(stage + ": editor text is empty (" + snapshot[3] + ")",
                    ((Integer) snapshot[3]) > 0);
        }
        assertTrue(stage + ": editor produced no styled spans (max per line: "
                        + snapshot[4] + "), syntax highlighting is absent",
                ((Integer) snapshot[4]) > 1);
    }

    @Test
    public void xmlHighlightingAppliesOnColdOpen() throws Exception {
        launchEditor();
        Object[] snapshot = poll(s -> highlighted(s, true));
        assertHighlighted("cold open", snapshot, true);
    }

    @Test
    public void xmlHighlightingReappliesAfterViewRecreationOnSameFragment() throws Exception {
        launchEditor();
        Object[] cold = poll(s -> highlighted(s, true));
        assertHighlighted("cold open", cold, true);
        CodeEditor oldEditor = (CodeEditor) cold[0];

        // Detach and attach as SEPARATE transactions (a same-transaction
        // detach+attach is optimized away by FragmentManager and would not
        // recreate the view). onDestroyView/onCreateView then run on the SAME
        // fragment instance, the editor view (and its language) start over, and
        // the syntax must be re-applied to the new view. The old scope-only
        // dedup skipped this re-apply, leaving XML unhighlighted.
        // Allowing state loss is intentional: this is a pure view-recreation
        // operation and must not depend on whether the activity happens to be
        // past onSaveInstanceState (e.g. covered by a lock screen or another
        // instrumentation session).
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            UnifiedEditorFragment f = findEditorFragment();
            assertNotNull("editor fragment missing before view recreation", f);
            FragmentManager fm = f.getParentFragmentManager();
            fm.beginTransaction().detach(f).commitNowAllowingStateLoss();
            fm.beginTransaction().attach(f).commitNowAllowingStateLoss();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Object[] recreated = poll(s -> s[0] != null);
        assertNotNull("editor view was not recreated after attach", recreated[0]);
        assertNotEquals("attach did not create a fresh editor view",
                oldEditor, recreated[0]);

        // The recreated editor view starts empty (this detach/attach is exactly
        // the FragmentStateAdapter page-switch situation, where the host pushes
        // the content back in). Feed the same XML and require real styled spans:
        // an empty document can only ever have the single default span per line,
        // so the span assertion below is only meaningful once text is present.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            UnifiedEditorFragment f = findEditorFragment();
            assertNotNull(f);
            f.setText(xmlContent);
        });
        Object[] filled = poll(s -> highlighted(s, true));
        assertHighlighted("recreated view rendering", filled, true);
    }
}
