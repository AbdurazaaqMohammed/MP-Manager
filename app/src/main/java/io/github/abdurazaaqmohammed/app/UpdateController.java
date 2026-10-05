package io.github.abdurazaaqmohammed.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import androidx.preference.PreferenceManager;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.UpdateUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;

/**
 * App-update download completion handling extracted from MainActivity.
 * downloadId is persisted in DefaultSharedPreferences (written by UpdateUtil).
 */
public class UpdateController {

    private final MainActivity activity;
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (id == getDownloadId()) {
                setDownloadId(-1);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                            int columnIndex1 = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            String fileUri = cursor.getString(columnIndex1);
                            promptInstallDownloadedUpdate(fileUri);
                        }
                    }
                } catch (Exception e) {
                    Extensions.showMessage(activity, e.toString());
                }
            }
        }
    };

    public UpdateController(MainActivity activity) {
        this.activity = activity;
    }

    private long getDownloadId() {
        return PreferenceManager.getDefaultSharedPreferences(activity)
                .getLong(UpdateUtil.PREF_DOWNLOAD_ID, -1);
    }

    private void setDownloadId(long id) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putLong(UpdateUtil.PREF_DOWNLOAD_ID, id).apply();
    }

    public void register() {
        try {
            if (Build.VERSION.SDK_INT > 32) {
                activity.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception ignored) {
        }
        checkPendingUpdateDownload();
    }

    public void unregister() {
        try {
            activity.unregisterReceiver(onDownloadComplete);
        } catch (Exception ignored) {
        }
    }

    private void checkPendingUpdateDownload() {
        long downloadId = getDownloadId();
        if (downloadId == -1) return;
        try {
            DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            try (Cursor cursor = downloadManager.query(query)) {
                if (cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                        setDownloadId(-1);
                        int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        promptInstallDownloadedUpdate(cursor.getString(uriIndex));
                    } else if (DownloadManager.STATUS_FAILED == cursor.getInt(statusIndex)) {
                        setDownloadId(-1);
                    }
                } else {
                    setDownloadId(-1);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void promptInstallDownloadedUpdate(String fileUri) {
        try {
            File apkFile = null;
            if (fileUri != null && !fileUri.isEmpty()) {
                Uri uri = Uri.parse(fileUri);
                if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                    apkFile = new File(uri.getPath());
                } else if (uri.getScheme() == null) {
                    apkFile = new File(fileUri);
                }
            }
            if (apkFile == null || !apkFile.isFile()) {
                Extensions.showMessage(activity, activity.getString(R.string.file_no_longer_available));
                return;
            }
            InstallUtil.installApkWithDialog(activity, apkFile);
        } catch (Exception e) {
            Extensions.showMessage(activity, e.toString());
        }
    }
}
