package io.github.abdurazaaqmohammed.features.apk;

import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.File;

/** Lite-flavor host: the Xposed-patcher engines are not bundled. */
public final class XposedPatcherLauncher {

    private XposedPatcherLauncher() {
    }

    public static void open(io.github.abdurazaaqmohammed.MPManager.MainActivity context,
                            io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        context.handler.post(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.xposed_patcher_lite_title)
                .setMessage(R.string.xposed_patcher_lite_message)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }
}
