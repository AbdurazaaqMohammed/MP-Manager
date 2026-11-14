package io.github.abdurazaaqmohammed.feature.apk;

import java.io.File;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;

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
