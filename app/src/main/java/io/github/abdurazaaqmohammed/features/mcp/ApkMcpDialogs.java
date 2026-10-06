package io.github.abdurazaaqmohammed.features.mcp;

import android.content.ClipboardManager;
import android.content.Context;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.abdurazaaqmohammed.mcp.ApkMcpServer;

/**
 * Start/stop dialog for the embedded APK MCP server, opened from the sidebar's APK MCP row.
 *
 * <p>Deliberately a plain alert: the only state is running vs stopped, the URL to hand to an
 * AI client, and the start/stop action, so there is nothing a layout would add.
 */
public final class ApkMcpDialogs {

    private ApkMcpDialogs() {
    }

    public static void show(MainActivity activity) {
        boolean running = ApkMcpServer.isRunning();
        String message = activity.getString(running
                ? R.string.apk_mcp_running
                : R.string.apk_mcp_stopped,
                ApkMcpServer.isRunning() ? ApkMcpServer.url() : "")
                + "\n\n" + activity.getString(R.string.apk_mcp_hint);

        MaterialAlertDialogBuilder b = activity.dialogUtil.getDialogBuilder()
                .setTitle(R.string.sidebar_apk_mcp)
                .setMessage(message)
                .setNegativeButton(R.string.close, null);

        if (running) {
            b.setPositiveButton(R.string.stop, (d, w) -> ApkMcpServer.stop())
                    .setNeutralButton(R.string.copy, (d, w) -> copyUrl(activity));
        } else {
            b.setPositiveButton(R.string.start, (d, w) -> {
                if (ApkMcpServer.start(activity)) {
                    copyUrl(activity);
                    Extensions.showMessage(activity, activity.getString(
                            R.string.apk_mcp_running, ApkMcpServer.url()));
                } else {
                    Extensions.showMessage(activity, R.string.apk_mcp_failed);
                }
            });
        }
        activity.dialogUtil.styleAlertDialog(b.create());
    }

    private static void copyUrl(MainActivity activity) {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setText(ApkMcpServer.url());
        Extensions.showMessage(activity, R.string.copied);
    }
}
