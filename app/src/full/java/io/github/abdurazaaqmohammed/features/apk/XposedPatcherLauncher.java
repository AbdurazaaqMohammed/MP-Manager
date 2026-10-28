package io.github.abdurazaaqmohammed.features.apk;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.utils.DialogUtil;

/** Full-flavor host: opens the real Xposed-patcher options dialog. */
public final class XposedPatcherLauncher {

    private XposedPatcherLauncher() {
    }

    public static void open(MainActivity context,
                            DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        new NPatchDialogs(context, dialogUtil, pane1).showNPatchDialog(file, fileName);
    }
}
