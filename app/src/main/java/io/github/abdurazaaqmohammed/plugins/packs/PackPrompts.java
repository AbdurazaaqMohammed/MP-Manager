package io.github.abdurazaaqmohammed.plugins.packs;

import android.R;
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
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
                : PackSignatures.isDebuggable(activity)
                        ? " No checksum published for this build \u2014 local builds only, install only if you trust the source."
                        : " No checksum published for this build \u2014 release builds refuse to install it."));
        desc.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(desc);

        Button action = new Button(activity);
        boolean installable = pack.hasChecksum() || PackSignatures.isDebuggable(activity);
        action.setText(PackManager.isInstalled(activity, pack.id) ? "Update pack" : "Download pack");
        action.setEnabled(installable);
        root.addView(action);
        action.setOnClickListener(v -> {
            action.setEnabled(false);
            Runnable doDownload = () -> startDownload(activity, pack, onInstalled, action);
            if (!pack.hasChecksum()) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(pack.title)
                        .setMessage("No checksum is published for this pack build. Release builds refuse to install it, so only proceed on a debug build. Continue?")
                        .setNegativeButton(R.string.cancel, (d, w) -> action.setEnabled(true))
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
            Toast.makeText(activity, "Download URL is missing", Toast.LENGTH_SHORT).show();
            if (action != null) action.setEnabled(true);
            return;
        }
        Toast.makeText(activity, "Downloading " + pack.title + "\u2026", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(activity, "Download failed", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(activity, pack.title + " installed", Toast.LENGTH_SHORT).show();
                            if (onInstalled != null) onInstalled.run();
                        } else {
                            Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                            if (action != null) action.setEnabled(true);
                        }
                    });
                }).start();
            }
        };
        try {
            // ACTION_DOWNLOAD_COMPLETE is a protected broadcast sent by the
            // DownloadProvider (another UID), so the receiver MUST be
            // exported: RECEIVER_NOT_EXPORTED drops it silently and the pack
            // is never installed. Only the system can send a protected
            // broadcast, so exporting it exposes nothing.
            Context appContext = activity.getApplicationContext();
            if (Build.VERSION.SDK_INT > 32) {
                appContext.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception ignored) {
        }
    }
}
