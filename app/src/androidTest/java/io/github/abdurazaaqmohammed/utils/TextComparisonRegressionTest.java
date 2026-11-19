package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.editor.CompareTextActivity;

import static org.junit.Assert.*;

/** Checks the comparison screen's real filter menu and system-save intent. */
public class TextComparisonRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CompareTextActivity activity;
    private File left;
    private File right;
    private Menu menu;

    @Before public void prepare() throws Exception {
        left = File.createTempFile("compare-left-", ".txt", instrumentation.getTargetContext().getCacheDir());
        right = File.createTempFile("compare-right-", ".txt", instrumentation.getTargetContext().getCacheDir());
        try (FileOutputStream out = new FileOutputStream(left)) {
            out.write("shared\nold\ntail\n".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream out = new FileOutputStream(right)) {
            out.write("shared\nnew\ntail\n".getBytes(StandardCharsets.UTF_8));
        }
        activity = (CompareTextActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), CompareTextActivity.class)
                .putExtra("file1", left.getAbsolutePath()).putExtra("file2", right.getAbsolutePath())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> ready.set(activity.findViewById(
                    R.id.text_compare_progress).getVisibility() != View.VISIBLE));
            if (ready.get()) break;
            Thread.sleep(100);
        }
        instrumentation.runOnMainSync(() -> {
            assertNotEquals(View.VISIBLE, activity.findViewById(R.id.text_compare_progress).getVisibility());
            menu = new PopupMenu(activity, activity.findViewById(R.id.compare_text_toolbar)).getMenu();
            activity.onCreateOptionsMenu(menu);
        });
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> { if (activity != null) activity.finish(); });
        if (left != null) left.delete();
        if (right != null) right.delete();
    }

    private void filter(int id, int expectedRows) {
        instrumentation.runOnMainSync(() -> {
            assertNotNull(menu.findItem(id));
            assertTrue(activity.onOptionsItemSelected(menu.findItem(id)));
            RecyclerView list = activity.findViewById(R.id.text_diff_list);
            assertNotNull(list.getAdapter());
            assertEquals(expectedRows, list.getAdapter().getItemCount());
        });
    }

    @Test public void filtersShowOnlyMatchingOrDifferingRows() {
        filter(R.id.action_filter_differences, 1);
        filter(R.id.action_filter_same, 2);
        filter(R.id.action_filter_all, 3);
    }

    @Test public void exportWritesFullDiffToReturnedDocumentUri() throws Exception {
        filter(R.id.action_filter_same, 2);
        File output = File.createTempFile("comparison-result-", ".diff",
                instrumentation.getTargetContext().getFilesDir());
        java.lang.reflect.Method result = CompareTextActivity.class.getDeclaredMethod(
                "onActivityResult", int.class, int.class, Intent.class);
        result.setAccessible(true);
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    result.invoke(activity, 4201, Activity.RESULT_OK,
                            new Intent().setData(android.net.Uri.fromFile(output)));
                } catch (Exception e) { throw new AssertionError(e); }
            });
            long deadline = System.currentTimeMillis() + 15000;
            while (output.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertTrue("Export must write the selected document", output.length() > 0);
            String diff = new String(java.nio.file.Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
            assertTrue(diff, diff.startsWith("--- "));
            assertTrue(diff, diff.contains("@@ -1,3 +1,3 @@"));
            assertTrue(diff, diff.contains("-old\n+new\n"));
            assertTrue(diff, diff.contains(" shared\n"));
        } finally {
            output.delete();
        }
    }

    @Test public void exportUsesDocumentPickerEvenWhenMatchesOnlyAreVisible() {
        filter(R.id.action_filter_same, 2);
        AtomicReference<Intent> launched = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction())) return null;
                launched.set(intent);
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            instrumentation.runOnMainSync(() -> assertTrue(activity.onOptionsItemSelected(
                    menu.findItem(R.id.action_export_diff))));
            assertNotNull("Export must launch a system document picker", launched.get());
            assertTrue(launched.get().hasCategory(Intent.CATEGORY_OPENABLE));
            assertEquals("text/plain", launched.get().getType());
            assertTrue(launched.get().getStringExtra(Intent.EXTRA_TITLE).endsWith(".diff"));
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }
}
