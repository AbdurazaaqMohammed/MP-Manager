package io.github.abdurazaaqmohammed.feature.apk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;

public final class FullPatcherNotice {
    private FullPatcherNotice() {}

    public static void show(Activity activity, int title, int message) {
        new MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.download, (dialog, which) -> activity.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://github.com/AbdurazaaqMohammed/MP-Manager/releases"))))
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
