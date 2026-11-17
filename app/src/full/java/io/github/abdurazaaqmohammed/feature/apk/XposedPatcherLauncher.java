package io.github.abdurazaaqmohammed.feature.apk;

import java.io.File;
import android.content.Intent;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.apk.xposed.LSPatchManagerActivity;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;

/** Full-flavor host: opens the real Xposed-patcher options dialog. */
public final class XposedPatcherLauncher {

    private XposedPatcherLauncher() {
    }

    public static void openManager(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < 28) {
            new MaterialAlertDialogBuilder(activity).setTitle(R.string.lspatch_manager)
                    .setMessage(R.string.lspatch_min_android).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        activity.startActivity(new Intent(activity, LSPatchManagerActivity.class));
    }

    public static void open(MainActivity context,
                            DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        if (Build.VERSION.SDK_INT < 28) {
            new MaterialAlertDialogBuilder(context).setTitle(R.string.npatch_integrated)
                    .setMessage(R.string.lspatch_min_android).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        new XposedPatcherDialogs(context, dialogUtil, pane1).showDialog(file, fileName);
    }
}
