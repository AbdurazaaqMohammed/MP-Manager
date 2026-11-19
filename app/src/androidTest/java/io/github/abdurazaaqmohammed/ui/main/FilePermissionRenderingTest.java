package io.github.abdurazaaqmohammed.ui.main;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.system.Os;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.data.prefs.UiPrefs;
import io.github.abdurazaaqmohammed.util.text.FileSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Renders real rows through FileIconLoader with the "show file permissions"
 * option on and off. Non-root mode on purpose: every fixture lives in the
 * app's own cache dirs, so permissions resolve from the local lstat with no
 * backend shell involved, which keeps the assertions deterministic. Storage
 * rows must keep the date, system (non-storage) rows must show the mode, and
 * a chmod followed by a fresh listing must show the new mode.
 */
public class FilePermissionRenderingTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> original = new HashMap<>();
    private SharedPreferences preferences;
    private MainActivity main;
    private File dir;
    private File file;

    @Before public void prepare() throws Exception {
        Application application = (Application) instrumentation.getTargetContext().getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(application);
        String[] keys = {"working_mode", "su_command", "startup_request_root", "root_file_ops",
                "shizuku_file_ops", "show_file_permissions", UiPrefs.FOLDER_SIZE_KEY};
        for (String key : keys) original.put(key, preferences.getAll().get(key));
        // Non-root mode and no Shizuku file ops: no backend shell can interfere
        // and no su prompt can steal the foreground during startup. Folder-size
        // "never" keeps folder rows deterministic (mode text alone, no size walk).
        preferences.edit().putString("working_mode", "non_root")
                .putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false).putBoolean("root_file_ops", false)
                .putBoolean("shizuku_file_ops", false)
                .putInt(UiPrefs.FOLDER_SIZE_KEY, UiPrefs.FOLDER_SIZE_NONE)
                .putBoolean("show_file_permissions", false).commit();
        dir = new File(application.getFilesDir(), "perm-rendering");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        file = new File(dir, "perm.txt");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[64]);
        }
        Os.chmod(file.getAbsolutePath(), 0640);
        Os.chmod(dir.getAbsolutePath(), 0750);
        // locatePath pins startup to the fixture folder instead of the async
        // home listing, which races cold launches on slow emulators.
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        application.startActivity(new Intent(application, MainActivity.class)
                .putExtra("locatePath", dir.getAbsolutePath()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main = (MainActivity) instrumentation.waitForMonitorWithTimeout(monitor, 120000);
        instrumentation.removeMonitor(monitor);
        assertNotNull("Main activity must finish launching", main);
        instrumentation.waitForIdleSync();
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> {
            if (main != null) main.finish();
        });
        if (file != null) file.delete();
        if (dir != null) dir.delete();
        SharedPreferences.Editor restore = preferences.edit();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value == null) restore.remove(entry.getKey());
            else if (value instanceof Boolean) restore.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) restore.putInt(entry.getKey(), (Integer) value);
            else restore.putString(entry.getKey(), (String) value);
        }
        restore.commit();
    }

    /** Binds one real row through a fresh loader (one loader serves one listing). */
    private TextView render(File target, boolean permsOn) {
        UiPrefs.setShowFilePermissions(main, permsOn);
        FileIconLoader loader = new FileIconLoader(main, false);
        TextView detail = new TextView(main);
        loader.setupFileView(target, new ImageView(main), detail);
        return detail;
    }

    @Test public void optionOff_showsDateAndSize() {
        instrumentation.runOnMainSync(() -> {
            String text = render(file, false).getText().toString();
            assertTrue(text, text.startsWith(
                    UiPrefs.formatDate(UiPrefs.getDatePattern(main), file.lastModified())));
            assertTrue(text, text.contains(FileSize.getHumanReadableFileSize(file.length())));
        });
    }

    @Test public void optionOn_systemRowsShowModes() {
        instrumentation.runOnMainSync(() -> {
            assertEquals("rw-r----- (640) " + FileSize.getHumanReadableFileSize(file.length()),
                    render(file, true).getText().toString());
            // Folder rows show the mode alone in "never" folder-size mode.
            assertEquals("rwxr-x--- (750)", render(dir, true).getText().toString());
        });
    }

    @Test public void optionOn_freshListingAfterChmodShowsNewMode() throws Exception {
        instrumentation.runOnMainSync(() -> render(dir, true));
        Os.chmod(dir.getAbsolutePath(), 0700);
        instrumentation.runOnMainSync(() -> {
            // A new listing means a new loader; the per-listing memo must not
            // serve the pre-chmod mode.
            assertEquals("rwx------ (700)", render(dir, true).getText().toString());
        });
    }

    @Test public void optionOn_userStorageRowsKeepDates() throws Exception {
        Application application = (Application) instrumentation.getTargetContext().getApplicationContext();
        File external = application.getExternalCacheDir();
        assertNotNull(external);
        assertTrue(external.getAbsolutePath(), external.getAbsolutePath().startsWith("/storage/"));
        File storageFile = new File(external, "perm-rendering.txt");
        try (FileOutputStream out = new FileOutputStream(storageFile)) {
            out.write(new byte[32]);
        }
        try {
            instrumentation.runOnMainSync(() -> {
                String text = render(storageFile, true).getText().toString();
                assertTrue(text, text.startsWith(UiPrefs.formatDate(
                        UiPrefs.getDatePattern(main), storageFile.lastModified())));
                assertTrue(text, text.contains(FileSize.getHumanReadableFileSize(storageFile.length())));
            });
        } finally {
            storageFile.delete();
        }
    }
}
