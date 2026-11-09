package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import modder.hub.dexeditor.activity.DexEditorActivity;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import static org.junit.Assert.*;

/** Runs against real Android task routing and the shipped Sora AAR, not mocks. */
public class EditorRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> activities = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private final AtomicReference<Activity> resumed = new AtomicReference<>();
    private final Object resumeLock = new Object();
    private MainActivity main;
    private Application application;
    private final Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) { synchronized (resumeLock) { resumed.set(activity); resumeLock.notifyAll(); } }
        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    };

    @Before public void prepare() throws Exception {
        application = (Application) instrumentation.getTargetContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(callbacks);
        // The existing rooted AVD may have an interactive su manager. UI tests
        // use local fixtures and must not block startup on its grant dialog.
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(application).edit()
                .putString("working_mode", "non_root")
                .putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false)
                .putBoolean("root_file_ops", false).commit();
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(
                "appops set " + application.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow")) {
            java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
            byte[] buffer = new byte[4096];
            while (input.read(buffer) != -1) { /* wait for appops to finish */ }
        }
        main = (MainActivity) instrumentation.startActivitySync(new Intent(application, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activities.add(main);
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> { for (int i = activities.size() - 1; i >= 0; i--) activities.get(i).finish(); });
        application.unregisterActivityLifecycleCallbacks(callbacks);
        for (File file : files) file.delete();
    }

    private File file(String extension, byte[] content) throws Exception {
        File file = File.createTempFile("editor-regression-", extension, application.getCacheDir());
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(content); }
        files.add(file);
        return file;
    }

    private Activity open(Class<? extends Activity> type, File file) {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(type.getName(), null, false);
        instrumentation.runOnMainSync(() -> {
            Intent intent = new Intent(main, type).putExtra("path", file.getPath());
            if (type == DexEditorActivity.class) intent.putStringArrayListExtra("SelectedDexFiles", new ArrayList<>(List.of(file.getPath())));
            main.startActivity(intent);
        });
        Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, 15000);
        instrumentation.removeMonitor(monitor);
        assertNotNull(activity);
        activities.add(activity);
        return activity;
    }

    private void awaitResume(Activity activity) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        synchronized (resumeLock) {
            while (resumed.get() != activity && System.currentTimeMillis() < deadline) resumeLock.wait(1000);
        }
        assertSame("Restored the exact activity instance", activity, resumed.get());
    }

    private void minimize(Activity activity) throws Exception {
        instrumentation.runOnMainSync(() -> MinimizedEditors.minimize(activity, "regression"));
        awaitResume(main);
    }

    private void restore(Activity activity) throws Exception {
        MinimizedEditors.Item item = null;
        for (MinimizedEditors.Item candidate : MinimizedEditors.getItems()) if (candidate.ref.get() == activity) item = candidate;
        assertNotNull(item);
        MinimizedEditors.Item selected = item;
        instrumentation.runOnMainSync(() -> MinimizedEditors.restore(main, selected));
        awaitResume(activity);
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void nibble(Activity activity, int digit) throws Exception {
        Method method = HexEditorActivity.class.getDeclaredMethod("inputNibble", int.class);
        method.setAccessible(true);
        instrumentation.runOnMainSync(() -> { try { method.invoke(activity, digit); } catch (Exception e) { throw new AssertionError(e); } });
    }

    @Test public void twoHexEditorsRestoreTheirOwnEditsInEitherOrder() throws Exception {
        Activity a = open(HexEditorActivity.class, file(".bin", new byte[]{0x12, 0x34}));
        nibble(a, 10);
        minimize(a);
        Activity b = open(HexEditorActivity.class, file(".bin", new byte[]{0x56, 0x78}));
        nibble(b, 11);
        minimize(b);
        assertNotEquals(a.getTaskId(), b.getTaskId());
        restore(a);
        assertEquals(0xA2, ((java.util.Map<?, ?>) field(a, "mods")).get(0L));
        minimize(a);
        restore(b);
        assertEquals(0xB6, ((java.util.Map<?, ?>) field(b, "mods")).get(0L));
        minimize(b);
        restore(a);
        assertEquals(0xA2, ((java.util.Map<?, ?>) field(a, "mods")).get(0L));
    }

    private UnifiedEditorFragment loaded(TextEditorActivity activity, String expected) throws Exception {
        UnifiedEditorFragment fragment = (UnifiedEditorFragment) field(activity, "editorFragment");
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<String> text = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> text.set(fragment.getCode()));
            if (expected.equals(text.get())) return fragment;
            Thread.sleep(20);
        }
        fail("Text did not finish loading");
        return fragment;
    }

    @Test public void textEditorsKeepDistinctDocumentsAndUnsavedEdits() throws Exception {
        TextEditorActivity a = (TextEditorActivity) open(TextEditorActivity.class, file(".txt", "first\r\nline".getBytes(StandardCharsets.UTF_8)));
        UnifiedEditorFragment first = loaded(a, "first\r\nline");
        instrumentation.runOnMainSync(() -> first.getEditor().getText().insert(0, 0, "A:"));
        minimize(a);
        TextEditorActivity b = (TextEditorActivity) open(TextEditorActivity.class, file(".txt", "second\rthird\n".getBytes(StandardCharsets.UTF_8)));
        UnifiedEditorFragment second = loaded(b, "second\rthird\n");
        instrumentation.runOnMainSync(() -> second.getEditor().getText().insert(0, 0, "B:"));
        minimize(b);
        restore(a);
        assertEquals("A:first\r\nline", first.getCode());
        assertTrue(first.getEditor().canUndo());
        minimize(a);
        restore(b);
        assertEquals("B:second\rthird\n", second.getCode());
        assertTrue(second.getEditor().canUndo());
    }

    @Test public void soraStreamingRoundTripPreservesChunkBoundaryCrLfAndSnapshots() throws Exception {
        char[] characters = new char[16383];
        java.util.Arrays.fill(characters, 'x');
        String input = new String(characters) + "\r\n" + "y\r" + "z\nend";
        Content content = ContentIO.createFrom(new java.io.StringReader(input));
        assertEquals(input, content.toString());
        Content snapshot = content.copyTextShallow();
        content.insert(0, 0, "changed");
        assertEquals(input, snapshot.toString());
        java.io.StringWriter writer = new java.io.StringWriter();
        ContentIO.writeTo(snapshot, writer, false);
        assertEquals(input, writer.toString());
    }

    @Test public void largeHexOpensReadOnlyAndReplaceCannotBypassIt() throws Exception {
        File file = file(".bin", new byte[]{0x12});
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(40L * 1024 * 1024); }
        Activity activity = open(HexEditorActivity.class, file);
        assertEquals(true, field(activity, "readOnly"));
        nibble(activity, 15);
        assertTrue(((java.util.Map<?, ?>) field(activity, "mods")).isEmpty());
        assertEquals(android.view.View.GONE, activity.findViewById(R.id.hexKeypad).getVisibility());
    }

    private Object call(Activity activity, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = HexEditorActivity.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        AtomicReference<Object> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set(method.invoke(activity, args)); } catch (Exception error) { throw new AssertionError(error); }
        });
        return result.get();
    }

    @Test public void structuralHexEditsSliceMemoryChunksAndUndoCorrectly() throws Exception {
        Activity activity = open(HexEditorActivity.class, file(".bin", new byte[]{0, 1, 2, 3, 4, 5}));
        Class<?>[] replaceTypes = {long.class, long.class, byte[].class};
        call(activity, "replaceBytesAt", replaceTypes, 1L, 2L, new byte[]{10, 11, 12, 13});
        assertArrayEquals(new byte[]{0, 10, 11, 12, 13, 3, 4, 5}, (byte[]) call(activity, "readRange", new Class<?>[]{long.class, long.class}, 0L, 8L));
        call(activity, "replaceBytesAt", replaceTypes, 3L, 1L, new byte[]{99, 100});
        assertArrayEquals(new byte[]{0, 10, 11, 99, 100, 13, 3, 4, 5}, (byte[]) call(activity, "readRange", new Class<?>[]{long.class, long.class}, 0L, 9L));
        call(activity, "undo", new Class<?>[0]);
        assertArrayEquals(new byte[]{0, 10, 11, 12, 13, 3, 4, 5}, (byte[]) call(activity, "readRange", new Class<?>[]{long.class, long.class}, 0L, 8L));
        call(activity, "undo", new Class<?>[0]);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5}, (byte[]) call(activity, "readRange", new Class<?>[]{long.class, long.class}, 0L, 6L));
    }

    @Test public void dexEditorsHaveIndependentTreesAndTabsAndRestoreByTask() throws Exception {
        File first = file(".dex", new byte[0]);
        File second = file(".dex", new byte[0]);
        for (File file : new File[]{first, second}) {
            FileDataStore store = new FileDataStore(file);
            try { new DexPool(Opcodes.getDefault()).writeTo(store); } finally { store.close(); }
        }
        DexEditorActivity a = (DexEditorActivity) open(DexEditorActivity.class, first);
        awaitTree(a);
        a.isChanged = true;
        minimize(a);
        DexEditorActivity b = (DexEditorActivity) open(DexEditorActivity.class, second);
        awaitTree(b);
        assertNotSame(a.classTree, b.classTree);
        assertNotSame(a.tabs, b.tabs);
        assertFalse(b.isChanged);
        minimize(b);
        restore(a);
        assertTrue(a.isChanged);
        minimize(a);
        restore(b);
        assertFalse(b.isChanged);
    }

    private void awaitTree(DexEditorActivity activity) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (activity.classTree == null && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertNotNull(activity.classTree);
        instrumentation.waitForIdleSync();
    }

    private TextView findText(android.view.View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void largeTextWarningHasWorkingOpenAnywayAndLoadsReadOnlyWithoutCopy() throws Exception {
        File file = file(".txt", new byte[0]);
        byte[] line = new byte[256];
        java.util.Arrays.fill(line, (byte) 'x');
        line[255] = '\n';
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int i = 0; i < 49152; i++) output.write(line); // 12 MiB
        }
        TextEditorActivity activity = (TextEditorActivity) open(TextEditorActivity.class, file);
        String label = activity.getString(R.string.editor_open_text_anyway);
        AtomicReference<TextView> button = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 20000;
        while (button.get() == null && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (android.view.View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    TextView found = findText(root, label);
                    if (found != null) button.set(found);
                }
            });
            if (button.get() == null) Thread.sleep(20);
        }
        assertNotNull("Open anyway button is shown", button.get());
        long started = System.currentTimeMillis();
        instrumentation.runOnMainSync(() -> button.get().performClick());
        UnifiedEditorFragment fragment = (UnifiedEditorFragment) field(activity, "editorFragment");
        AtomicReference<Integer> length = new AtomicReference<>(0);
        deadline = System.currentTimeMillis() + 60000;
        while (length.get() != file.length() && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> length.set(fragment.getEditor().getText().length()));
            Thread.sleep(20);
        }
        assertEquals(file.length(), length.get().longValue());
        assertFalse(fragment.getEditor().isEditable());
        assertFalse(fragment.getEditor().isWordwrap());
        assertTrue(fragment.getEditor().getProps().disableTextExtracting);
        Object tab = ((List<?>) field(activity, "tabs")).get(0);
        assertSame("No second full document copy", field(tab, "document"), fragment.getEditor().getText());
        System.out.println("12 MiB text loaded in " + (System.currentTimeMillis() - started) + " ms; Java heap="
                + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024) + " MiB");
    }

    @Test public void editorTasksPreserveActivityResultRegistryCallbacks() throws Exception {
        File file = file(".txt", "result".getBytes(StandardCharsets.UTF_8));
        AtomicReference<androidx.activity.result.ActivityResult> result = new AtomicReference<>();
        AtomicReference<androidx.activity.result.ActivityResultLauncher<Intent>> launcher = new AtomicReference<>();
        java.util.concurrent.CountDownLatch delivered = new java.util.concurrent.CountDownLatch(1);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(TextEditorActivity.class.getName(), null, false);
        instrumentation.runOnMainSync(() -> {
            launcher.set(main.getActivityResultRegistry().register("editor-regression-result",
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), value -> {
                        result.set(value);
                        delivered.countDown();
                    }));
            launcher.get().launch(new Intent(main, TextEditorActivity.class).putExtra("path", file.getPath()));
        });
        TextEditorActivity editor = (TextEditorActivity) instrumentation.waitForMonitorWithTimeout(monitor, 15000);
        instrumentation.removeMonitor(monitor);
        assertNotNull(editor);
        activities.add(editor);
        loaded(editor, "result");
        instrumentation.runOnMainSync(() -> {
            editor.setEditorResult(Activity.RESULT_OK, new Intent().putExtra("sentinel", "correct-editor"));
            editor.finish();
        });
        assertTrue(delivered.await(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(Activity.RESULT_OK, result.get().getResultCode());
        assertEquals("correct-editor", result.get().getData().getStringExtra("sentinel"));
        instrumentation.runOnMainSync(() -> launcher.get().unregister());
    }

    @Test public void partitionBrowserUsesLightThemeAndArabicResources() throws Exception {
        android.content.res.Resources resources = main.getResources();
        android.content.res.Configuration original = new android.content.res.Configuration(resources.getConfiguration());
        android.content.res.Resources.Theme originalTheme = resources.newTheme();
        originalTheme.setTo(main.getTheme());
        Method browse = io.github.abdurazaaqmohammed.features.dumper.PartitionDumperDialog.class.getDeclaredMethod(
                "browse", androidx.appcompat.app.AppCompatActivity.class, List.class, String.class);
        browse.setAccessible(true);
        var items = List.of(
                new io.github.abdurazaaqmohammed.features.dumper.PartitionItem("boot_a", "boot", "", "/dev/block/by-name/boot_a", "sda1", 64L * 1024 * 1024,
                        io.github.abdurazaaqmohammed.features.dumper.PartitionItem.Slot.A, true, false, "boot_a.img", io.github.abdurazaaqmohammed.features.dumper.PartitionItem.Group.COMMON),
                new io.github.abdurazaaqmohammed.features.dumper.PartitionItem("boot_b", "boot", "", "/dev/block/by-name/boot_b", "sda2", 64L * 1024 * 1024,
                        io.github.abdurazaaqmohammed.features.dumper.PartitionItem.Slot.B, false, false, "boot_b.img", io.github.abdurazaaqmohammed.features.dumper.PartitionItem.Group.COMMON));
        File screenshotDirectory = new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "mp-editor-regression-" + java.util.UUID.randomUUID());
        screenshotDirectory.mkdirs();
        System.out.println("Partition screenshots: " + screenshotDirectory.getAbsolutePath());
        try {
            for (String language : new String[]{"en", "ar"}) {
                android.content.res.Configuration config = new android.content.res.Configuration(original);
                config.setLocale(java.util.Locale.forLanguageTag(language));
                instrumentation.runOnMainSync(() -> {
                    resources.updateConfiguration(config, resources.getDisplayMetrics());
                    main.getTheme().applyStyle(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar, true);
                    try { browse.invoke(null, main, items, "a"); } catch (Exception error) { throw new AssertionError(error); }
                });
                instrumentation.waitForIdleSync();
                android.graphics.Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                if (screenshot != null) {
                    try (FileOutputStream output = new FileOutputStream(new File(screenshotDirectory, "partition-" + language + ".png"))) {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                    }
                    screenshot.recycle();
                }
                instrumentation.runOnMainSync(() -> {
                    boolean titleFound = false;
                    for (android.view.View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (findText(root, main.getString(R.string.partition_dumper_title)) != null) {
                            titleFound = true;
                            TextView title = findText(root, "boot");
                            assertNotNull(title);
                            assertEquals(com.google.android.material.color.MaterialColors.getColor(main,
                                    com.google.android.material.R.attr.colorOnSurface, 0), title.getCurrentTextColor());
                            TextView button = findText(root, main.getString(android.R.string.ok));
                            assertNotNull(button);
                            button.performClick();
                        }
                    }
                    assertTrue("Translated title is visible", titleFound);
                });
            }
        } finally {
            instrumentation.runOnMainSync(() -> {
                resources.updateConfiguration(original, resources.getDisplayMetrics());
                main.getTheme().setTo(originalTheme);
            });
        }
    }
}
