package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
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
import io.github.abdurazaaqmohammed.utils.ApkIconHelper;
import io.github.abdurazaaqmohammed.utils.ArchiveBackupUtil;
import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        // Save-back rewrites the archive in place, which only zip-family
        // containers support; browsing a 7z/rar/tar is read-only.
        if (!ArchiveBrowseUtil.isZipLike(zipFile)) {
            Extensions.showMessage(activity, R.string.archive_not_editable);
            return;
        }
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
                    String entryName = (entryPath != null && !entryPath.isEmpty()) ? entryPath : null;
                    String modifiedFileName = entryName != null
                            ? entryName.substring(entryName.lastIndexOf("/") + 1)
                            : path.substring(path.lastIndexOf("/") + 1);
                    LinearLayout ll = (LinearLayout) LayoutInflater.from(activity).inflate(R.layout.item_modified_dialog, null);
                    String zipFileName = zipFile.getName();
                    boolean isApk = ApkIconHelper.isApkArchiveName(zipFileName);
                    ll.<TextView>findViewById(R.id.modifiedText).setText(activity.rss.getString(R.string.file_modified_x, modifiedFileName, (isApk ? "APK" : "ZIP")));
                    CheckBox autosign = ll.findViewById(R.id.autosign);
                    boolean[] sign = new boolean[1];
                    // Signing only makes sense for an APK. Plain ZIPs were offered
                    // the sign prompt and then silently ignored it, so hide the
                    // option entirely instead of asking a question with no answer.
                    sign[0] = isApk && settings.getBoolean("autosign", true);
                    autosign.setChecked(sign[0]);
                    View signRow = ll.findViewById(R.id.autosign);
                    View signSettingsRow = ll.findViewById(R.id.sign_settings);
                    if (!isApk && signRow.getParent() instanceof View) {
                        ((View) signRow.getParent()).setVisibility(View.GONE);
                    } else {
                        signRow.setVisibility(View.VISIBLE);
                        signSettingsRow.setVisibility(View.VISIBLE);
                    }
                    autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                    signSettingsRow.setOnClickListener(activity.uiHelper.showSignSettingsDialog());
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
                                    List<String> addedNames = new ArrayList<>();
                                    try (ZipFile zf = new ZipFile(zipFile)) {
                                        if (modifiedFileName.startsWith("classes") && modifiedFileName.endsWith(".dex")) {
                                            File modifiedFile = new File(path);
                                            File folder = modifiedFile.getParentFile();
                                            File[] dexFiles = folder == null ? null : folder.listFiles((dir, name1) -> name1.endsWith(".dex"));
                                            if (dexFiles == null || dexFiles.length == 0) throw new IOException("No dex files found");
                                            boolean changed = false;
                                            for (File dexFile : dexFiles) {
                                                if (ArchiveBackupUtil.isEntryChanged(zf, dexFile.getName(), dexFile)) changed = true;
                                            }
                                            if (!changed) {
                                                // Nothing actually differs, so leave the
                                                // archive (and its backup) untouched.
                                                pm.dismiss();
                                                activity.handler.post(() -> Extensions.showMessage(activity, R.string.archive_unchanged));
                                                return;
                                            }
                                            if (UiPrefs.genBackup(activity)) FileUtils.backupOnce(zipFile);
                                            for (File dexFile : dexFiles) {
                                                addedNames.add(dexFile.getName());
                                            }
                                            zf.addFiles(Arrays.asList(dexFiles));
                                        } else {
                                            File replacement = new File(path);
                                            String targetEntry = entryName != null ? entryName : modifiedFileName;
                                            if (!ArchiveBackupUtil.isEntryChanged(zf, targetEntry, replacement)) {
                                                pm.dismiss();
                                                activity.handler.post(() -> Extensions.showMessage(activity, R.string.archive_unchanged));
                                                return;
                                            }
                                            if (UiPrefs.genBackup(activity)) FileUtils.backupOnce(zipFile);
                                            addedNames.add(modifiedFileName);
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
                                        activity.handler.post(() -> {
                                            for (String n : addedNames) activity.flashPaneItem(pane1, n);
                                        });
                                        activity.handler.post(() -> chainUpdateOuter(zipFile, wrapper, sign[0], pane1, 0));
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

    public void offerOuterUpdate(File innerZip, boolean pane1) {
        if (innerZip == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.handler.post(() -> offerOuterUpdate(innerZip, pane1));
            return;
        }
        boolean sign = false;
        try {
            sign = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean("autosign", true);
        } catch (Exception ignored) {
        }
        chainUpdateOuter(innerZip, new SignWrapper[1], sign, pane1, 0);
    }

    private void chainUpdateOuter(File innerZip, SignWrapper[] wrapper, boolean sign, boolean pane1, int depth) {
        if (depth > 5 || innerZip == null) return;
        MainActivity.ArchiveParent parent;
        try {
            parent = activity.archiveParentOf(innerZip.getAbsolutePath());
        } catch (Exception e) {
            return;
        }
        if (parent == null || parent.outerZip() == null || parent.entryPath() == null) return;
        if (!parent.outerZip().isFile() || !innerZip.isFile()) return;
        String innerName = innerZip.getName();
        String outerName = parent.outerZip().getName();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.add_to_outer_title))
                .setMessage(activity.getString(R.string.add_to_outer_msg, innerName, outerName))
                .setPositiveButton(activity.getString(R.string.yes), (d, w) -> {
                    try {
                        d.dismiss();
                    } catch (Exception ignored) {}
                    boolean outerApk = ApkIconHelper.isApkArchiveName(parent.outerZip().getName());
                    Runnable apply = () -> {
                        ProgressManager pm = new ProgressManager(activity, true).show();
                        pm.setText(activity.rss.getString(R.string.adding, innerName));
                        new Thread(() -> {
                            try (ZipFile zf = new ZipFile(parent.outerZip())) {
                                // An unchanged inner archive must not rewrite the
                                // outer one or trigger a pointless re-sign.
                                if (!ArchiveBackupUtil.isEntryChanged(zf, parent.entryPath(), innerZip)) {
                                    pm.dismiss();
                                    activity.handler.post(() -> chainUpdateOuter(parent.outerZip(), wrapper, sign, pane1, depth + 1));
                                    return;
                                }
                                if (UiPrefs.genBackup(activity)) FileUtils.backupOnce(parent.outerZip());
                                ZipParameters zp = new ZipParameters();
                                zp.setCompressionMethod(CompressionMethod.DEFLATE);
                                zp.setFileNameInZip(parent.entryPath());
                                zf.addFile(innerZip.getAbsolutePath(), zp);
                                if (sign && outerApk && wrapper[0] != null)
                                    wrapper[0].signApk(parent.outerZip());
                                pm.dismiss();
                                activity.handler.post(() -> {
                                    try {
                                        Extensions.showMessage(activity, activity.rss.getString(R.string.outer_updated_f, innerName, outerName));
                                    } catch (Exception ignored) {}
                                    chainUpdateOuter(parent.outerZip(), wrapper, sign, pane1, depth + 1);
                                });
                            } catch (Exception e) {
                                pm.dismiss();
                                new ErrorUtil(activity).showError(e);
                            }
                        }).start();
                    };
                    if (sign && outerApk && wrapper[0] == null) {
                        SignWrapper.requireAuth(activity, sw -> {
                            wrapper[0] = sw;
                            apply.run();
                        });
                    } else apply.run();
                })
                .setNegativeButton(activity.getString(android.R.string.cancel), null)
                .show();
    }
}
