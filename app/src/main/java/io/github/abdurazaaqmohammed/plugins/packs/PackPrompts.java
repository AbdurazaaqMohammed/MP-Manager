package io.github.abdurazaaqmohammed.plugins.packs;

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

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

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
            t.setText(activity.getString(R.string.pack_unknown_tool));
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
        // Prefer the installed pack's own localized metadata; the catalog JSON
        // is English-only because it is fetched before/without the pack APK.
        io.github.abdurazaaqmohammed.plugins.api.ToolPlugin live = (meta != null)
                ? io.github.abdurazaaqmohammed.plugins.api.PluginRegistry.findById(activity, meta.id)
                : null;
        String label = (live != null && live.title(activity) != null)
                ? live.title(activity) : (meta != null ? meta.title : pack.title);
        title.setText(label);
        root.addView(title);

        TextView desc = new TextView(activity);
        desc.setText(activity.getString(R.string.pack_part_of_downloadable, pack.title, pack.versionName)
                + (pack.hasChecksum() ? " " + activity.getString(R.string.pack_checksum_verified)
                : " " + activity.getString(R.string.pack_no_checksum_published)));
        desc.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(desc);

        Button action = new Button(activity);
        action.setText(activity.getString(PackManager.isInstalled(activity, pack.id)
                ? R.string.pack_action_update : R.string.pack_action_download));
        root.addView(action);
        action.setOnClickListener(v -> {
            action.setEnabled(false);
            Runnable doDownload = () -> startDownload(activity, pack, onInstalled, action);
            if (!pack.hasChecksum()) {
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle(pack.title)
                        .setMessage(activity.getString(R.string.pack_no_checksum_confirm))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> action.setEnabled(true))
                        .setPositiveButton(activity.getString(R.string.download), (d, w) -> doDownload.run())
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
            Toast.makeText(activity, activity.getString(R.string.download_url_missing), Toast.LENGTH_SHORT).show();
            if (action != null) action.setEnabled(true);
            return;
        }
        Toast.makeText(activity, activity.getString(R.string.downloading_fmt, pack.title), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(activity, activity.getString(R.string.download_failed_plain), Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(activity, activity.getString(R.string.pack_installed, pack.title), Toast.LENGTH_SHORT).show();
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
