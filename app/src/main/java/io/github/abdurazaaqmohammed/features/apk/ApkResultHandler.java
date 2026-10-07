package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

/**
 * Re-injects editor-modified files back into the open APK/ZIP.
 * Extracted from MainActivity.handleModifiedFileResult.
 */
public class ApkResultHandler {

    /**
     * Request code for "an editor wrote a file back that belongs inside the open archive".
     * Shared so every editor that edits an archived file uses the same contract.
     */
    public static final int REQUEST_MODIFIED_ENTRY = 757;

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
                    new MaterialAlertDialogBuilder(activity)
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
                                        io.github.abdurazaaqmohammed.utils.ZipPassword.apply(zf, zipFile);
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

    /**
     * Several archive entries were rewritten in one go.
     *
     * <p>The layout translation screen mirrors every modified file under a cache root at its own
     * archive path, so the backup, the zip write and the signing prompt happen once for the whole
     * batch rather than once per layout.
     *
     * @param entryPaths   archive entries to replace
     * @param modifiedRoot cache directory holding {@code <entryPath>} for each entry
     */
    public void handleModifiedEntriesResult(Uri uri, String[] entryPaths, String zipFileExtra,
                                            String modifiedRoot) {
        boolean pane1 = activity.lastPaneSelected == 1;
        String marker = uri == null ? null : uri.getPath();
        if (marker == null) return;
        String cachePath = activity.getCacheDir().getPath();
        if (!marker.startsWith(cachePath)) return;
        if (entryPaths == null || entryPaths.length == 0) return;

        final File root = new File(modifiedRoot == null ? "" : modifiedRoot);
        if (!root.getAbsolutePath().startsWith(cachePath)) return;

        File resolved = null;
        if (zipFileExtra != null && !zipFileExtra.isEmpty()) {
            File zp = new File(zipFileExtra);
            if (zp.isFile()) resolved = zp;
        }
        if (resolved == null) resolved = pane1 ? activity.pane1Folder : activity.pane2Folder;
        final File zipFile = resolved;
        if (zipFile == null || !zipFile.isFile()) {
            Extensions.showMessage(activity, R.string.archive_no_longer_open);
            return;
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
        String zipFileName = zipFile.getName();
        boolean isApk = zipFileName.endsWith(".apk");
        String first = new File(entryPaths[0]).getName();
        String label = entryPaths.length > 1
                ? first + " +" + (entryPaths.length - 1)
                : first;

        LinearLayout ll = (LinearLayout) LayoutInflater.from(activity).inflate(R.layout.item_modified_dialog, null);
        ll.<TextView>findViewById(R.id.modifiedText).setText(
                activity.rss.getString(R.string.file_modified_x, label, (isApk ? "APK" : "ZIP")));
        CheckBox autosign = ll.findViewById(R.id.autosign);
        boolean[] sign = new boolean[1];
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        ll.findViewById(R.id.sign_settings).setOnClickListener(activity.uiHelper.showSignSettingsDialog());

        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.file_modified))
                .setView(ll)
                .setPositiveButton(activity.getString(R.string.yes), (dialog, which) -> {
                    dialog.dismiss();
                    SignWrapper[] wrapper = new SignWrapper[1];
                    Runnable doWork = () -> {
                        if (!zipFile.isFile() || !root.isDirectory()) {
                            Extensions.showMessage(activity, R.string.file_no_longer_available);
                            return;
                        }
                        ProgressManager pm = new ProgressManager(activity, true).show();
                        pm.setText(activity.rss.getString(R.string.adding, label));
                        new Thread(() -> {
                            try {
                                File parent = zipFile.getParentFile();
                                if (parent != null) {
                                    FileUtils.copyFile(zipFile, new File(parent, zipFileName + ".bak"));
                                }
                                int written = 0;
                                try (ZipFile zf = new ZipFile(zipFile)) {
                                    io.github.abdurazaaqmohammed.utils.ZipPassword.apply(zf, zipFile);
                                    for (String entryPath : entryPaths) {
                                        File modified = new File(root, entryPath);
                                        if (!modified.isFile()) continue;
                                        ZipParameters zp = new ZipParameters();
                                        zp.setCompressionMethod(CompressionMethod.DEFLATE);
                                        zp.setFileNameInZip(entryPath);
                                        zf.addFile(modified, zp);
                                        written++;
                                    }
                                }
                                if (written == 0) throw new IOException("No modified entries found");
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
                    });
                    else doWork.run();
                })
                .setNegativeButton(activity.rss.getString(android.R.string.cancel), null)
                .show();
    }
}
