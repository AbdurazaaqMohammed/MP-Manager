package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import java.io.File;

public class InstallUtil {
    public static void installApk(Context context, File file) {
        Uri uri = Build.VERSION.SDK_INT < 24 ? Uri.fromFile(file) : FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
        try {
            context.startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .setData(uri));
        } catch (Exception e) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception e2) {
                uri = uri.toString().startsWith("content") ? Uri.fromFile(file) : FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                try {
                    context.startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .setData(uri));
                } catch (Exception e3) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                }
            }
        }
    }
}