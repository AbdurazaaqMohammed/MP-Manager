package io.github.abdurazaaqmohammed.utils;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.widget.CompoundButton;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.files.MainSettingsActivity;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

import static org.junit.Assert.*;

/** Exercises the real drawer and Back routing without stopping the instrumentation process. */
public class NavigationRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> original = new HashMap<>();
    private SharedPreferences preferences;
    private MainActivity main;
    private MainSettingsActivity settings;
    private File folder;

    @Before public void prepare() throws Exception {
        Application application = (Application) instrumentation.getTargetContext().getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(application);
        String[] keys = {"working_mode", "su_command", "startup_request_root", "root_file_ops",
                "back_navigates_folders", "show_file_permissions"};
        for (String key : keys) original.put(key, preferences.getAll().get(key));
        preferences.edit().putString("working_mode", "non_root")
                .putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false).putBoolean("root_file_ops", false)
                .putBoolean("back_navigates_folders", true).putBoolean("show_file_permissions", false).commit();
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(
                "appops set " + application.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow");
             InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[4096];
            while (input.read(buffer) != -1) { /* wait for appops */ }
        }
        folder = new File(application.getFilesDir(), "navigation-regression/child");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        main = (MainActivity) instrumentation.startActivitySync(new Intent(application, MainActivity.class)
                .putExtra("locatePath", folder.getAbsolutePath()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            main.setSelectedPane(1);
            main.loadFolderInPane(folder, true);
        });
        instrumentation.waitForIdleSync();
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> {
            if (settings != null) settings.finish();
            if (main != null) main.finish();
        });
        SharedPreferences.Editor restore = preferences.edit();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value == null) restore.remove(entry.getKey());
            else if (value instanceof Boolean) restore.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof String) restore.putString(entry.getKey(), (String) value);
        }
        restore.commit();
        if (folder != null) {
            folder.delete();
            folder.getParentFile().delete();
        }
    }

    private boolean exitPromptArmed() throws Exception {
        Field field = MainActivity.class.getDeclaredField("isBackPressedToExit");
        field.setAccessible(true);
        return field.getBoolean(main);
    }

    @Test public void menuKeyOpensAndClosesTheSameDrawerAsTheButton() {
        DrawerLayout drawer = main.findViewById(R.id.drawer_layout);
        assertNotNull(drawer);
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertTrue(drawer.isDrawerOpen(GravityCompat.END)));
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertFalse(drawer.isDrawerOpen(GravityCompat.END)));
        instrumentation.runOnMainSync(() -> main.findViewById(R.id.moreOptionsMenu).performClick());
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertTrue(drawer.isDrawerOpen(GravityCompat.END)));
        instrumentation.runOnMainSync(main::onBackPressed);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertFalse(drawer.isDrawerOpen(GravityCompat.END)));
    }

    @Test public void disablingBackNavigationSkipsFoldersButKeepsUpButtonWorking() throws Exception {
        preferences.edit().putBoolean("back_navigates_folders", false).commit();
        instrumentation.runOnMainSync(main::onBackPressed);
        assertEquals(folder, main.pane1Folder);
        assertTrue("Back should immediately offer exit without navigating", exitPromptArmed());
        instrumentation.runOnMainSync(() -> main.findViewById(R.id.upButton).performClick());
        long deadline = System.currentTimeMillis() + 15000;
        while (!folder.getParentFile().equals(main.pane1Folder) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(folder.getParentFile(), main.pane1Folder);
    }

    @Test public void enabledBackNavigationStillGoesToParentFolder() throws Exception {
        instrumentation.runOnMainSync(main::onBackPressed);
        instrumentation.waitForIdleSync();
        assertEquals(folder.getParentFile(), main.pane1Folder);
        assertFalse(exitPromptArmed());
    }

    private android.widget.TextView findText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView label && text.contentEquals(label.getText())) return label;
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void clickDialogText(int resource) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        java.util.concurrent.atomic.AtomicReference<Boolean> clicked = new java.util.concurrent.atomic.AtomicReference<>(false);
        while (!clicked.get() && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (android.view.View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.widget.TextView button = findText(root, main.getString(resource));
                    if (button != null && button.isShown()) {
                        button.performClick();
                        clicked.set(true);
                        break;
                    }
                }
            });
            if (!clicked.get()) Thread.sleep(100);
        }
        assertTrue("Dialog control must be reachable: " + main.getString(resource), clicked.get());
    }

    @Test public void checksumDialogGeneratesBothManifestFormats() throws Exception {
        File first = new File(folder, "first.txt");
        File second = new File(folder, "second.txt");
        File sfv = new File(folder, folder.getName() + ".sfv");
        File md5 = new File(folder, folder.getName() + ".md5");
        try {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(first)) { out.write(1); }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(second)) { out.write(2); }
            instrumentation.runOnMainSync(() -> new io.github.abdurazaaqmohammed.ui.main.ChecksumDialogs(
                    main, main.dialogUtil).showChecksumsDialog(java.util.Arrays.asList(first, second)));
            clickDialogText(R.string.checksum_generate_sfv);
            long deadline = System.currentTimeMillis() + 15000;
            while (sfv.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertTrue("CRC32 button must create the SFV file", sfv.length() > 0);
            assertEquals(2, io.github.abdurazaaqmohammed.util.hash.HashUtil.verifyChecksumFile(sfv).size());
            clickDialogText(R.string.checksum_generate_md5);
            deadline = System.currentTimeMillis() + 15000;
            while (md5.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertTrue("MD5 button must create the MD5 file", md5.length() > 0);
            for (File manifest : java.util.Arrays.asList(sfv, md5)) {
                for (io.github.abdurazaaqmohammed.util.hash.HashUtil.CheckResult result :
                        io.github.abdurazaaqmohammed.util.hash.HashUtil.verifyChecksumFile(manifest)) {
                    assertTrue(result.fileName, result.valid);
                }
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(second)) { out.write(3); }
            instrumentation.runOnMainSync(() -> new io.github.abdurazaaqmohammed.ui.main.ChecksumDialogs(
                    main, main.dialogUtil).showHashVerifyDialog(md5));
            java.util.concurrent.atomic.AtomicReference<Boolean> reported = new java.util.concurrent.atomic.AtomicReference<>(false);
            deadline = System.currentTimeMillis() + 15000;
            while (!reported.get() && System.currentTimeMillis() < deadline) {
                instrumentation.runOnMainSync(() -> {
                    for (android.view.View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (findText(root, main.getString(R.string.checksum_verify_summary, 1, 1, 0)) != null
                                && findText(root, "second.txt") != null) reported.set(true);
                    }
                });
                if (!reported.get()) Thread.sleep(100);
            }
            assertTrue("Verification must list the changed file and mismatch count", reported.get());
        } finally {
            first.delete(); second.delete(); sfv.delete(); md5.delete();
        }
    }

    @Test public void settingsSwitchesPersistBothNewOptions() {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                MainSettingsActivity.class.getName(), null, false);
        instrumentation.runOnMainSync(main::showSettingsDialog);
        settings = (MainSettingsActivity) instrumentation.waitForMonitorWithTimeout(monitor, 45000);
        instrumentation.removeMonitor(monitor);
        assertNotNull(settings);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            CompoundButton back = settings.findViewById(R.id.backNavigatesFoldersToggle);
            CompoundButton permissions = settings.findViewById(R.id.filePermsSwitch);
            assertNotNull("Behavior settings page must expose Back navigation", back);
            assertNotNull("Files settings page must expose permissions", permissions);
            assertTrue(back.isChecked());
            assertFalse(permissions.isChecked());
            back.setChecked(false);
            permissions.setChecked(true);
        });
        assertFalse(preferences.getBoolean("back_navigates_folders", true));
        assertTrue(preferences.getBoolean("show_file_permissions", false));
    }
}
