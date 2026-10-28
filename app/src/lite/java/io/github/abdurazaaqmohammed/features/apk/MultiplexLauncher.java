package io.github.abdurazaaqmohammed.features.apk;

import io.github.abdurazaaqmohammed.MPManager.R;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

/** Lite-flavor host: multiplexing signs via the full build's engine signer. */
public final class MultiplexLauncher {

    private MultiplexLauncher() {
    }

    public static void open(io.github.abdurazaaqmohammed.MPManager.MainActivity context,
                            io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        context.handler.post(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.xposed_patcher_lite_title)
                .setMessage(R.string.xposed_patcher_lite_message)
                .setPositiveButton(R.string.download, (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdurazaaqMohammed/MP-Manager/releases"))))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }
}
