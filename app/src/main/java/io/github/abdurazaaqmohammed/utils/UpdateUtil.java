package io.github.abdurazaaqmohammed.utils;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.noties.markwon.Markwon;

public class UpdateUtil {
    public static void checkForUpdates(boolean toast, MainActivity context) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = getHttpURLConnection();

                try (InputStream inputStream = conn.getInputStream();
                     InputStreamReader in = new InputStreamReader(inputStream);
                     BufferedReader reader = new BufferedReader(in)) {
                    String line;
                    String latestVersion = "";
                    String changelog = "";
                    String dl = "";
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("browser_download_url")) {
                            dl = line.split("\"")[3];
                            latestVersion = line.split("/")[7];
                        } else if (line.contains("body")) {
                            changelog = line.split("\"")[3];
                            break;
                        }
                    }
                    String currentVer;
                    try {
                        currentVer = (context).getPackageManager().getPackageInfo((context).getPackageName(), 0).versionName;
                    } catch (Exception e) {
                        currentVer = null;
                    }
                    boolean newVer = false;
                    char[] curr = TextUtils.isEmpty(currentVer) ? new char[] { '1', '0', '7' }
                            : currentVer.replace(".", "").toCharArray();
                    char[] latest = latestVersion.replace(".", "").toCharArray();

                    int maxLength = Math.max(curr.length, latest.length);
                    for (int i = 0; i < maxLength; i++) {
                        char currChar = i < curr.length ? curr[i] : '0';
                        char latestChar = i < latest.length ? latest[i] : '0';

                        if (latestChar > currChar) {
                            newVer = true;
                            break;
                        } else if (latestChar < currChar) {
                            break;
                        }
                    }
                    Resources rss = context.rss;

                    if (newVer) {
                        if (!toast && !TextUtils.isEmpty(context.lastVerChecked) && context.lastVerChecked.equals(latestVersion))
                            return;
                        String ending = ".apk";
                        String filename = "MP-Manager." + latestVersion + ending;
                        String link = dl.endsWith(ending) ? dl : dl + File.separator + filename;
                        Markwon markwon = Markwon.create(context);

                        DisplayMetrics dm = rss.getDisplayMetrics();
                        MaterialTextView tv = new MaterialTextView(context);
                        tv.setMaxHeight(dm.heightPixels / 2);
                        markwon.setMarkdown(tv, changelog.replace("\\r\\n", "\n"));
                        int p = (int) (16 * dm.density + 0.5f);
                        tv.setPadding(p, p, p, p);

                        String finalLatestVersion = latestVersion;
                        context.handler.post(() -> {
                            AlertDialog alertDialog = new MaterialAlertDialogBuilder(context)
                                    .setTitle(rss.getString(R.string.new_ver, finalLatestVersion)).setView(tv)
                                    .setPositiveButton(rss.getString(R.string.download), (dialog, which) -> {
                                        DownloadManager.Request request = new DownloadManager.Request(
                                                Uri.parse(link))
                                                .setTitle(filename).setDescription(filename)
                                                .setMimeType("application/vnd.android.package-archive")
                                                .setDestinationInExternalPublicDir(
                                                        Environment.DIRECTORY_DOWNLOADS, filename)
                                                .setNotificationVisibility(
                                                        DownloadManager.Request.VISIBILITY_VISIBLE
                                                                | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        context.downloadId = ((DownloadManager) context.getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                                    })
                                    .setNegativeButton("Go to GitHub Release", (dialog, which) -> context
                                            .startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(
                                                    "https://github.com/AbdurazaaqMohammed/MP-Manager/releases/latest"))))
                                    .setNeutralButton(rss.getString(android.R.string.cancel), null).create();
                            alertDialog.setOnDismissListener(dialog -> context.lastVerChecked = finalLatestVersion);
                            alertDialog.show();
                            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnLongClickListener(v -> {
                                Extensions.showMessage(context, link);
                                return false;
                            });
                        });
                    } else if (toast) Extensions.showMessage(context, R.string.no_update_found);
                }
            } catch (Exception e) {
                if (toast) Extensions.showMessage(context, R.string.failed_to_check_for_update);
            }
        }).start();
    }

    @NonNull
    private static HttpURLConnection getHttpURLConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.github.com/repos/AbdurazaaqMohammed/MP-Manager/releases").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return conn;
    }
}