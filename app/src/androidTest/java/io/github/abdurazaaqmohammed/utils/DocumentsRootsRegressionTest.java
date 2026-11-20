package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.DocumentsContract;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.provider.StorageDocumentsProvider;

import static org.junit.Assert.*;

public class DocumentsRootsRegressionTest {
    @Test public void rootsFollowSettingsWithoutRequiringShizuku() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String originalMode = preferences.getString("working_mode", null);
        boolean hadToggle = preferences.contains("shizuku_file_ops");
        boolean originalToggle = preferences.getBoolean("shizuku_file_ops", false);
        try {
            preferences.edit().putString("working_mode", "shizuku")
                    .putBoolean("shizuku_file_ops", true).commit();
            // This must work with no Shizuku service, grant or live binder.
            assertTrue(PrivilegeManager.Files.fileOpsConfigured(context));
            StorageDocumentsProvider provider = new StorageDocumentsProvider();
            android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
            info.authority = context.getPackageName() + ".documents";
            info.exported = true;
            info.grantUriPermissions = true;
            info.readPermission = "android.permission.MANAGE_DOCUMENTS";
            info.writePermission = "android.permission.MANAGE_DOCUMENTS";
            provider.attachInfo(context, info);
            try (Cursor roots = provider.queryRoots(null)) {
                assertEquals(2, roots.getCount());
                assertTrue(roots.moveToFirst());
                assertEquals("internal", roots.getString(roots.getColumnIndexOrThrow(
                        DocumentsContract.Root.COLUMN_ROOT_ID)));
            }
            preferences.edit().putBoolean("shizuku_file_ops", false).commit();
            assertFalse(PrivilegeManager.Files.fileOpsConfigured(context));
            try (Cursor roots = provider.queryRoots(null)) {
                assertEquals(1, roots.getCount());
            }
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            if (originalMode == null) restore.remove("working_mode");
            else restore.putString("working_mode", originalMode);
            if (hadToggle) restore.putBoolean("shizuku_file_ops", originalToggle);
            else restore.remove("shizuku_file_ops");
            restore.commit();
        }
    }
}
