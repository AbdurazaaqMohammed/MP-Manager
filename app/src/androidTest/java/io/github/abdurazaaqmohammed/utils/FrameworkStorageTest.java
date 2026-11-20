package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;

import com.reandroid.apk.framework.InternalFrameworks;

import io.github.abdurazaaqmohammed.data.prefs.UiPrefs;

import static org.junit.Assert.assertEquals;

public class FrameworkStorageTest {
    @Test public void frameworksStayPrivateWhenWorkingDirectoryChanges() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String original = preferences.getString("app_path_dir", null);
        try {
            preferences.edit().putString("app_path_dir", "/sdcard/custom-workspace").commit();
            File expected = new File(context.getFilesDir(), "frameworks");
            assertEquals(expected, UiPrefs.frameworkDir(context));
            Method resourcePath = InternalFrameworks.class.getDeclaredMethod("toResourcePath", int.class);
            resourcePath.setAccessible(true);
            assertEquals(new File(expected, "android_36.apk").getPath(), resourcePath.invoke(null, 36));
            preferences.edit().putString("app_path_dir", "/sdcard/another-workspace").commit();
            assertEquals(expected, UiPrefs.frameworkDir(context));
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            if (original == null) restore.remove("app_path_dir");
            else restore.putString("app_path_dir", original);
            restore.commit();
            UiPrefs.frameworkDir(context);
        }
    }
}
