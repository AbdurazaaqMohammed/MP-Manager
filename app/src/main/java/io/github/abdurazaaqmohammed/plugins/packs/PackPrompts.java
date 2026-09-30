package io.github.abdurazaaqmohammed.plugins.packs;

import io.github.abdurazaaqmohammed.core.ui.UIKit;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * "This tool is a downloadable pack" prompt used when a tool id is known
 * to the catalog but its pack is not installed. Handles download, checksum
 * verification and install, then runs onInstalled (e.g. recreate the host).
 */
public final class PackPrompts {

    private PackPrompts() {
    }

    /**
     * @return true when a prompt was shown (tool is a known pack tool),
     *         false when the id is unknown entirely.
     */
    public static boolean showForTool(Activity activity, LinearLayout box, String toolId, Runnable onInstalled) {
        List<PackDescriptor> catalog = PackCatalog.load(activity);
        PackDescriptor pack = PackCatalog.packForTool(catalog, toolId);
        if (pack == null) {
            TextView t = new TextView(activity);
            t.setText("Unknown tool");
            box.addView(t);
            return false;
        }
        box.addView(promptView(activity, pack, toolId, onInstalled));
        return true;
    }

    public static View promptView(Activity activity, PackDescriptor pack, String toolId, Runnable onInstalled) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density + 0.5f);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setTextSize(18);
        PackDescriptor.ToolMeta meta = pack.tool(toolId);
        title.setText(meta != null ? meta.title : pack.title);
        root.addView(title);

        TextView desc = new TextView(activity);
        desc.setText("Part of the downloadable \"" + pack.title + "\" pack (v" + pack.versionName + ")."
                + (pack.hasChecksum() ? " Checksum verified on install."
                : " No checksum published for this build — install only if you trust the source."));
        desc.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(desc);

        Button action = new Button(activity);
        action.setText(PackManager.isInstalled(activity, pack.id) ? "Update pack" : "Download pack");
        root.addView(action);
        action.setOnClickListener(v -> {
            action.setEnabled(false);
            Runnable doDownload = () -> startDownload(activity, pack, onInstalled, action);
            if (!pack.hasChecksum()) {
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle(pack.title)
                        .setMessage("No checksum is published for this pack build. Install only if you trust the source. Continue?")
                        .setNegativeButton(android.R.string.cancel, (d, w) -> action.setEnabled(true))
                        .setPositiveButton("Download", (d, w) -> doDownload.run())
                        .show();
            } else {
                doDownload.run();
            }
        });
        return root;
    }

    /** Download + install without a prompt view (e.g. from the ToolsHub store). */
    public static void downloadPack(Activity activity, PackDescriptor pack, Runnable onInstalled) {
        startDownload(activity, pack, onInstalled, null);
    }

    private static void startDownload(Activity activity, PackDescriptor pack, Runnable onInstalled, View action) {
        long downloadId = PackManager.enqueueDownload(activity, pack);
        if (downloadId < 0) {
            UIKit.toast(activity, "Download URL is missing");
            if (action != null) action.setEnabled(true);
            return;
        }
        UIKit.toast(activity, "Downloading " + pack.title + "…");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;
                try {
                    context.unregisterReceiver(this);
                } catch (Exception ignored) {
                }
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                boolean ok = false;
                try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
                    if (c.moveToFirst()) {
                        int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        ok = status == DownloadManager.STATUS_SUCCESSFUL;
                    }
                } catch (Exception ignored) {
                }
                if (!ok) {
                    UIKit.toast(activity, "Download failed");
                    if (action != null) {
                        try {
                            activity.runOnUiThread(() -> action.setEnabled(true));
                        } catch (Exception ignored) {
                        }
                    }
                    return;
                }
                File downloaded = PackManager.downloadOutput(activity, pack.id);
                new Thread(() -> {
                    String error = PackManager.installDownloadedPack(activity, pack, downloaded);
                    activity.runOnUiThread(() -> {
                        if (error == null) {
                            UIKit.toast(activity, pack.title + " installed");
                            if (onInstalled != null) onInstalled.run();
                        } else {
                            UIKit.toast(activity, error, true);
                            if (action != null) action.setEnabled(true);
                        }
                    });
                }).start();
            }
        };
        try {
            if (Build.VERSION.SDK_INT > 32) {
                activity.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception ignored) {
        }
    }
}
