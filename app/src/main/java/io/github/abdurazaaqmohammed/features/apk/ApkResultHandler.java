package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;


import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * Re-injects editor-modified files back into the open APK/ZIP.
 * Extracted from MainActivity.handleModifiedFileResult.
 */
public class ApkResultHandler {

    private final MainActivity activity;

    public ApkResultHandler(MainActivity activity) {
        this.activity = activity;
    }

    public void handleModifiedFileResult(Uri uri) {
        handleModifiedFileResult(uri, null, null);
    }

    public void handleModifiedFileResult(Uri uri, String entryPath, String zipFileExtra) {
        boolean pane1 = activity.lastPaneSelected == 1;
        String path = uri == null ? null : uri.getPath();
        File resolvedZip = null;
        if (zipFileExtra != null && !zipFileExtra.isEmpty()) {
            File zp = new File(zipFileExtra);
            if (zp.isFile()) resolvedZip = zp;
        }
        if (resolvedZip == null) resolvedZip = pane1 ? activity.pane1Folder : activity.pane2Folder;
        final File zipFile = resolvedZip;
        if (path == null) return;
        boolean inAppPrivateDir = path.startsWith(activity.getCacheDir().getPath());
        try {
            inAppPrivateDir = inAppPrivateDir || path.startsWith(activity.getFilesDir().getPath());
        } catch (Exception ignored) {
        }
        if (!inAppPrivateDir) return;
        if (zipFile == null || !zipFile.isFile()) {
            Extensions.showMessage(activity, R.string.archive_no_longer_open);
            return;
        }
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                    String entryName = (entryPath != null && !entryPath.isEmpty()) ? entryPath : null;
                    String modifiedFileName = entryName != null
                            ? entryName.substring(entryName.lastIndexOf("/") + 1)
                            : path.substring(path.lastIndexOf("/") + 1);
                    LinearLayout ll = (LinearLayout) LayoutInflater.from(activity).inflate(R.layout.item_modified_dialog, null);
                    String zipFileName = zipFile.getName();
                    boolean isApk = zipFileName.endsWith(".apk");
                    ll.<TextView>findViewById(R.id.modifiedText).setText(activity.rss.getString(R.string.file_modified_x, modifiedFileName, (isApk ? "APK" : "ZIP")));
                    CheckBox autosign = ll.findViewById(R.id.autosign);
                    boolean[] sign = new boolean[1];
                    autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                    autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                    ll.findViewById(R.id.sign_settings).setOnClickListener(activity.uiHelper.showSignSettingsDialog());
                    UIKit.dialog(activity)
                        .setTitle(activity.getString(R.string.file_modified))
                        .setView(ll)
                        .setPositiveButton(activity.getString(R.string.yes), (dialog, which) -> {
                            dialog.dismiss();
                            SignWrapper[] wrapper = new SignWrapper[1];
                            Runnable doWork = () -> {
                                if (!zipFile.isFile() || !new File(path).isFile()) {
                                    Extensions.showMessage(activity, R.string.file_no_longer_available);
                                    return;
                                }
                                ProgressManager pm = new ProgressManager(activity, true).show();
                                pm.setText(activity.rss.getString(R.string.adding, modifiedFileName));
                                new Thread(() -> {
                                    try (ZipFile zf = new ZipFile(zipFile)) {
                                        File backup = new File(zipFile.getParent(), zipFileName + ".bak");
                                        FileUtils.copyFile(zipFile, backup);
                                        if (modifiedFileName.startsWith("classes") && modifiedFileName.endsWith(".dex")) {
                                            File modifiedFile = new File(path);
                                            File folder = modifiedFile.getParentFile();
                                            File[] dexFiles = folder == null ? null : folder.listFiles((dir, name1) -> name1.endsWith(".dex"));
                                            if (dexFiles == null || dexFiles.length == 0) throw new IOException("No dex files found");
                                            zf.addFiles(Arrays.asList(dexFiles));
                                        } else {
                                            ZipParameters zp = new ZipParameters();
                                            boolean store = modifiedFileName.equals("AndroidManifest.xml") || modifiedFileName.equals("resources.arsc");
                                            zp.setCompressionMethod(store ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
                                            if (entryName != null) zp.setFileNameInZip(entryName);
                                            zf.addFile(path, zp);
                                        }
                                    } catch (Exception e) {
                                        pm.dismiss();
                                        new ErrorUtil(activity).showError(e);
                                        return;
                                    }
                                    try {
                                        if (sign[0]) wrapper[0].signApk(zipFile);
                                        pm.dismiss();
                                        activity.handler.post(() -> {
                                            try {
                                                RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
                                                String zipPath = a instanceof MainFilesArrayAdapter
                                                        ? ((MainFilesArrayAdapter) a).currentZipPath : "";
                                                activity.loadZipFolderInPane(zipFile, zipPath, pane1, false);
                                            } catch (Exception ex) {
                                                new ErrorUtil(activity).showError(ex);
                                            }
                                        });
                                    } catch (Exception e) {
                                        pm.dismiss();
                                        new ErrorUtil(activity).showError(e);
                                    }
                                }).start();
                            };
                            if (sign[0]) SignWrapper.requireAuth(activity, sw -> {
                                wrapper[0] = sw;
                                doWork.run();
                            }); else doWork.run();
                        }).setNegativeButton(activity.rss.getString(android.R.string.cancel), null).show();
    }
}
