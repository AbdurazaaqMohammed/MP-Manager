package io.github.abdurazaaqmohammed.feature.dex;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

public final class JadxDecompileHelper {

    private JadxDecompileHelper() {}

    public static void promptDecompileAction(Activity activity, List<File> dexFiles, File zipFile,
                                             String baseName, Runnable onBrowseJava) {
        if (activity == null || activity.isFinishing()) return;
        String[] options = {
                activity.getString(R.string.open_in_java_mode),
                activity.getString(R.string.export_as_zip),
                activity.getString(R.string.export_to_directory)
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.decompile_to_java_jadx))
                .setItems(options, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        if (onBrowseJava != null) onBrowseJava.run();
                    } else if (which == 1) {
                        File parent = zipFile != null ? zipFile.getParentFile()
                                : (dexFiles != null && !dexFiles.isEmpty() ? dexFiles.get(0).getParentFile() : null);
                        if (parent == null) parent = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                        File targetZip = FileUtils.getUnusedFile(new File(parent, baseName + "_java.zip"));
                        exportDexFiles(activity, dexFiles, targetZip, true);
                    } else if (which == 2) {
                        File parent = zipFile != null ? zipFile.getParentFile()
                                : (dexFiles != null && !dexFiles.isEmpty() ? dexFiles.get(0).getParentFile() : null);
                        if (parent == null) parent = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                        File targetDir = FileUtils.getUnusedFile(new File(parent, baseName + "_java"));
                        exportDexFiles(activity, dexFiles, targetDir, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void exportDexFiles(Activity activity, List<File> dexFiles, File target, boolean asZip) {
        if (dexFiles == null || dexFiles.isEmpty() || target == null) return;
        ProgressManager pm = (activity instanceof AppCompatActivity) ? new ProgressManager((AppCompatActivity) activity, true) : null;
        if (pm != null) {
            pm.show();
            pm.setText(activity.getString(R.string.decompiling));
        }

        new Thread(() -> {
            JadxArgs args = new JadxArgs();
            args.setInputFiles(dexFiles);
            args.setSkipResources(true);
            args.setShowInconsistentCode(true);
            args.setThreadsCount(Math.max(1, Runtime.getRuntime().availableProcessors()));
            JadxDecompiler decompiler = new JadxDecompiler(args);
            try {
                decompiler.load();
                List<JavaClass> classes = decompiler.getClasses();
                int total = classes.size();
                if (total == 0) {
                    activity.runOnUiThread(() -> {
                        pm.dismiss();
                        Toast.makeText(activity, "No classes found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                if (asZip) {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target), 131072))) {
                        ZipEntry man = new ZipEntry("META-INF/MANIFEST.MF");
                        zos.putNextEntry(man);
                        zos.write("Manifest-Version: 1.0\nCreated-By: MP Manager (JADX)\n".getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();

                        java.util.Set<String> addedEntries = new java.util.HashSet<>();
                        for (int i = 0; i < total; i++) {
                            JavaClass cls = classes.get(i);
                            String name = cls.getFullName();
                            if (name == null || name.isEmpty()) continue;
                            String entryName = name.replace('.', '/') + ".java";
                            if (!addedEntries.add(entryName)) continue;
                            try {
                                cls.decompile();
                            } catch (Exception ignored) {}
                            String code = cls.getCode();
                            if (code != null) {
                                ZipEntry ze = new ZipEntry(entryName);
                                zos.putNextEntry(ze);
                                zos.write(code.getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();
                            }
                            try {
                                cls.unload();
                            } catch (Exception ignored) {}

                            final int progress = i + 1;
                            if (progress % 15 == 0 || progress == total) {
                                pm.setProgress(progress, total);
                            }
                        }
                    }
                } else {
                    target.mkdirs();
                    for (int i = 0; i < total; i++) {
                        JavaClass cls = classes.get(i);
                        String name = cls.getFullName();
                        if (name == null || name.isEmpty()) continue;
                        try {
                            cls.decompile();
                        } catch (Exception ignored) {}
                        String code = cls.getCode();
                        if (code != null) {
                            File javaFile = new File(target, name.replace('.', '/') + ".java");
                            File parent = javaFile.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(javaFile)) {
                                fos.write(code.getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        try {
                            cls.unload();
                        } catch (Exception ignored) {}

                        final int progress = i + 1;
                        if (pm != null && (progress % 15 == 0 || progress == total)) {
                            pm.setProgress(progress, total);
                        }
                    }
                }

                activity.runOnUiThread(() -> {
                    if (pm != null) pm.dismiss();
                    Toast.makeText(activity, activity.getString(R.string.java_saved_to, target.getName()), Toast.LENGTH_LONG).show();
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).refreshPane(true);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (pm != null) pm.dismiss();
                    new ErrorUtil(activity).showError(e);
                });
            } finally {
                try {
                    decompiler.close();
                } catch (Exception ignored) {}
            }
        }).start();
    }
}
