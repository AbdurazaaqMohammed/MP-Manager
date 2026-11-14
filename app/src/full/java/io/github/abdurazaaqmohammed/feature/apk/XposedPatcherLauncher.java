package io.github.abdurazaaqmohammed.feature.apk;

import java.io.File;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;

/** Full-flavor host: opens the real Xposed-patcher options dialog. */
public final class XposedPatcherLauncher {

    private XposedPatcherLauncher() {
    }

    public static void open(MainActivity context,
                            DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        new XposedPatcherDialogs(context, dialogUtil, pane1).showDialog(file, fileName);
    }
}
