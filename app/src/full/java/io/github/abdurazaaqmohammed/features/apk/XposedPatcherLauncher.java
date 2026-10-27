package io.github.abdurazaaqmohammed.features.apk;

import java.io.File;

/** Full-flavor host: opens the real Xposed-patcher options dialog. */
public final class XposedPatcherLauncher {

    private XposedPatcherLauncher() {
    }

    public static void open(io.github.abdurazaaqmohammed.MPManager.MainActivity context,
                            io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        new NPatchDialogs(context, dialogUtil, pane1).showNPatchDialog(file, fileName);
    }
}
