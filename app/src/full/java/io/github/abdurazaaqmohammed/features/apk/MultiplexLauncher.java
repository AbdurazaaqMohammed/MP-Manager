package io.github.abdurazaaqmohammed.features.apk;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.utils.DialogUtil;

/** Full-flavor host: runs the real APK data multiplexing flow. */
public final class MultiplexLauncher {

    private MultiplexLauncher() {
    }

    public static void open(MainActivity context,
                            DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        MultiplexDialogs.show(context, dialogUtil, pane1, file, fileName);
    }
}
