package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.reandroid.apk.APKLogger;
import com.reandroid.apkeditor.compile.BuildOptions;
import com.reandroid.apkeditor.compile.Builder;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Dialog and executor for recompiling an unpacked APK folder back into an installable APK.
 * Modeled visually and structurally after {@link MergeUtil} (AntiSplit dialog).
 */
public class ApkCompileUtil {

    public static boolean isApkDirectory(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        File[] children = folder.listFiles();
        if (children == null || children.length == 0) return false;
        boolean hasManifest = false;
        boolean hasDexOrSmali = false;
        boolean hasResOrArsc = false;
        for (File f : children) {
            String name = f.getName();
            if (name.equalsIgnoreCase("AndroidManifest.xml") || name.equalsIgnoreCase("AndroidManifest.xml.bin")) {
                hasManifest = true;
            } else if (name.equalsIgnoreCase("classes.dex") || name.startsWith("classes") || name.equalsIgnoreCase("smali")) {
                hasDexOrSmali = true;
            } else if (name.equalsIgnoreCase("resources.arsc") || name.equalsIgnoreCase("resources") || name.equalsIgnoreCase("res") || name.equalsIgnoreCase("uncompressed-files.json")) {
                hasResOrArsc = true;
            }
        }
        return hasManifest && (hasDexOrSmali || hasResOrArsc);
    }

    public static void showCompileDialog(File folder, MainActivity context) {
        if (folder == null || !folder.isDirectory()) return;

        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        // Section header for options checklist
        TextView optionsTitle = new TextView(context);
        optionsTitle.setText(context.getString(R.string.compile_apk_options_title));
        optionsTitle.setTypeface(null, Typeface.BOLD);
        root.addView(optionsTitle);

        LinearLayout checkBoxHolder = new LinearLayout(context);
        checkBoxHolder.setOrientation(LinearLayout.VERTICAL);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean savedVrd = prefs.getBoolean("compile_opt_vrd", true);
        boolean savedNoCache = prefs.getBoolean("compile_opt_no_cache", false);
        boolean savedDexProfile = prefs.getBoolean("compile_opt_dex_profile", false);
        boolean savedAlign = prefs.getBoolean("compile_opt_align", true);
        boolean savedExtract = prefs.getBoolean("compile_opt_extract_native_libs", true);
        boolean savedAutosign = prefs.getBoolean("autosign", true);

        CheckBox cbValidateRes = new MaterialCheckBox(context);
        cbValidateRes.setText(R.string.compile_opt_validate_res);
        cbValidateRes.setChecked(savedVrd);
        checkBoxHolder.addView(cbValidateRes);

        CheckBox cbNoCache = new MaterialCheckBox(context);
        cbNoCache.setText(R.string.compile_opt_no_cache);
        cbNoCache.setChecked(savedNoCache);
        checkBoxHolder.addView(cbNoCache);

        CheckBox cbDexProfile = new MaterialCheckBox(context);
        cbDexProfile.setText(R.string.compile_opt_dex_profile);
        cbDexProfile.setChecked(savedDexProfile);
        checkBoxHolder.addView(cbDexProfile);

        CheckBox cbAlign = new MaterialCheckBox(context);
        cbAlign.setText(R.string.compile_opt_align);
        cbAlign.setChecked(savedAlign);
        checkBoxHolder.addView(cbAlign);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(checkBoxHolder);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (160 * density)));

        MaterialSwitch extractSwitch = new MaterialSwitch(context);
        extractSwitch.setText(context.getString(R.string.set_extract_native_libs));
        extractSwitch.setChecked(savedExtract);
        root.addView(extractSwitch);

        root.addView(MergeUtil.createCompressionDropdownLayout(context));

        LinearLayout signRow = new LinearLayout(context);
        signRow.setOrientation(LinearLayout.HORIZONTAL);
        signRow.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox autosignBox = new MaterialCheckBox(context);
        autosignBox.setText(R.string.auto_sign);
        autosignBox.setChecked(savedAutosign);
        LinearLayout.LayoutParams signParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        signRow.addView(autosignBox, signParams);
        MaterialButton signSettings = new MaterialButton(context);
        signSettings.setText(R.string.sign_set);
        signSettings.setOnClickListener(context.uiHelper.showSignSettingsDialog());
        signRow.addView(signSettings);
        root.addView(signRow);

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.compile_apk))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(context.getString(R.string.compile), (d, w) -> {
                    boolean vrd = cbValidateRes.isChecked();
                    boolean noCache = cbNoCache.isChecked();
                    boolean dexProfile = cbDexProfile.isChecked();
                    boolean align = cbAlign.isChecked();
                    boolean extractLibs = extractSwitch.isChecked();
                    boolean autosign = autosignBox.isChecked();

                    prefs.edit()
                            .putBoolean("compile_opt_vrd", vrd)
                            .putBoolean("compile_opt_no_cache", noCache)
                            .putBoolean("compile_opt_dex_profile", dexProfile)
                            .putBoolean("compile_opt_align", align)
                            .putBoolean("compile_opt_extract_native_libs", extractLibs)
                            .putBoolean("autosign", autosign)
                            .apply();

                    compileApkDirectory(folder, context, vrd, noCache, dexProfile, align, extractLibs, autosign);
                })
                .show();
    }

    private static void compileApkDirectory(File folder, MainActivity context,
                                           boolean validateResDir, boolean noCache,
                                           boolean dexProfile, boolean align,
                                           boolean extractNativeLibs, boolean autosign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doCompile = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            AppLogs.log("COMPILE_APK", "Starting compile for directory: " + folder.getAbsolutePath());
            BuildOptions bo = new BuildOptions();

            File[] children = folder.listFiles();
            boolean isXml = false;
            boolean isJson = false;
            if (children != null) {
                for (File f : children) {
                    String name = f.getName();
                    if (name.equalsIgnoreCase("resources") || name.equalsIgnoreCase("res")) {
                        isXml = true;
                    } else if (name.equalsIgnoreCase("uncompressed-files.json")) {
                        isJson = true;
                    }
                }
            }

            bo.type = isXml ? BuildOptions.TYPE_XML : isJson ? BuildOptions.TYPE_JSON : BuildOptions.TYPE_RAW;
            bo.extractNativeLibs = extractNativeLibs ? "true" : "false";
            bo.dexLib = BuildOptions.DEX_LIB_INTERNAL;
            bo.validateResDir = validateResDir;
            bo.noCache = noCache;
            bo.dexProfile = dexProfile;
            bo.inputFile = folder;
            File parent = folder.getParentFile() != null ? folder.getParentFile() : folder;
            File outFile = new File(parent, folder.getName() + ".apk");
            int counter = 1;
            while (outFile.exists()) {
                outFile = new File(parent, folder.getName() + " (" + counter + ").apk");
                counter++;
            }
            bo.outputFile = outFile;
            final File finalOut = outFile;

            new Thread(() -> {
                try {
                    APKLogger logger = pm.getLogger();
                    new Builder(bo, logger).runCommand();
                    logger.close();

                    if (align) {
                        try {
                            ApkZipAlignUtil.ensureInstallable(finalOut);
                        } catch (Exception alignErr) {
                            AppLogs.logError("COMPILE_APK", "ZipAlign warning", alignErr);
                        }
                    }

                    if (autosign && wrapper[0] != null) {
                        AppLogs.log("COMPILE_APK", "Signing compiled APK: " + finalOut.getAbsolutePath());
                        wrapper[0].signApk(finalOut);
                    }

                    pm.dismiss();
                    AppLogs.log("COMPILE_APK", "Successfully compiled APK: " + finalOut.getAbsolutePath());
                    context.runOnUiThread(() -> {
                        context.reloadCurrentFolder();
                        Extensions.showMessage(context, context.getString(R.string.success_saved, finalOut.getName()));
                    });
                } catch (Exception e) {
                    AppLogs.logError("COMPILE_APK", "Failed to compile APK from directory " + folder.getAbsolutePath(), e);
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        };

        if (autosign) {
            SignWrapper.requireAuth(context, sw -> {
                wrapper[0] = sw;
                doCompile.run();
            });
        } else {
            doCompile.run();
        }
    }
}
