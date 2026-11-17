package io.github.abdurazaaqmohammed.feature.apk;

import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;

import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.File;

/** Lite-flavor host: the Xposed-patcher engines are not bundled. */
public final class XposedPatcherLauncher {

    private XposedPatcherLauncher() {
    }

    public static void openManager(androidx.appcompat.app.AppCompatActivity activity) {
        FullPatcherNotice.show(activity, R.string.lspatch_manager, R.string.xposed_patcher_lite_message);
    }

    public static void open(io.github.abdurazaaqmohammed.ui.main.MainActivity context,
                            io.github.abdurazaaqmohammed.ui.common.util.DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        context.handler.post(() -> FullPatcherNotice.show(context,
                R.string.xposed_patcher_lite_title, R.string.xposed_patcher_lite_message));
    }
}
