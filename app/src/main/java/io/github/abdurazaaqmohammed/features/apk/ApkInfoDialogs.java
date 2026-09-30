package io.github.abdurazaaqmohammed.features.apk;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.SignatureStripUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import com.android.apksig.ApkVerifier;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.decompile.DecompileOptions;
import com.reandroid.apkeditor.decompile.Decompiler;
import com.reandroid.apkeditor.protect.ProtectorOptions;
import com.reandroid.apkeditor.refactor.RefactorOptions;
import com.reandroid.archive.ArchiveFile;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.apk.ApkBatchTools;
import io.github.abdurazaaqmohammed.features.apk.ApkOverlayTools;
import io.github.abdurazaaqmohammed.features.apk.ApkSignatureTools;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ApkCompareUtil;
import io.github.abdurazaaqmohammed.utils.ApkInfoUtil;
import io.github.abdurazaaqmohammed.utils.ApkOptimizer;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.ToastInjectorUtil;
import io.github.abdurazaaqmohammed.utils.OverlayInjectorUtil;
import io.github.abdurazaaqmohammed.utils.OverlayProfiles;
import io.github.abdurazaaqmohammed.utils.PairipRemoverUtil;
import io.github.abdurazaaqmohammed.utils.ApkDeepOptimizer;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.adapters.main.FileIconLoader;
import mt.modder.hub.apkCloner.util.ApkCloner;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * APK info + decompile option dialogs extracted from ApkToolsHandler.
 */
public class ApkInfoDialogs {

    private final io.github.abdurazaaqmohammed.MPManager.MainActivity context;
    private final io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil;
    private final io.github.abdurazaaqmohammed.ui.UIHelper uiHelper;
    private final boolean pane1;
    private final io.github.abdurazaaqmohammed.adapters.main.ApkManifestEditor manifestEditor;
    private final ApkSignatureTools signatures;
    private final ApkOverlayTools overlay;

    public ApkInfoDialogs(io.github.abdurazaaqmohammed.MPManager.MainActivity context,
                              io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil,
                              io.github.abdurazaaqmohammed.ui.UIHelper uiHelper, boolean pane1,
                              io.github.abdurazaaqmohammed.adapters.main.ApkManifestEditor manifestEditor,
                              ApkSignatureTools signatures, ApkOverlayTools overlay) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
        this.manifestEditor = manifestEditor;
        this.signatures = signatures;
        this.overlay = overlay;
    }

    public void showDecompileOptionsDialog(File file, String fileName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_decompile_options, null);

        MaterialAutoCompleteTextView frameworkVersion = dialogView.findViewById(R.id.frameworkVersion);
        MaterialAutoCompleteTextView decodeTypes = dialogView.findViewById(R.id.decodeTypes);
        MaterialAutoCompleteTextView dexLibrary = dialogView.findViewById(R.id.dexLibrary);
        TextInputEditText loadDex = dialogView.findViewById(R.id.loadDex);
        CompoundButton flagDex = dialogView.findViewById(R.id.flagDex);
        CompoundButton noDexDebug = dialogView.findViewById(R.id.noDexDebug);
        CompoundButton dexMarkers = dialogView.findViewById(R.id.dexMarkers);
        CompoundButton flagForce = dialogView.findViewById(R.id.flagForce);
        CompoundButton keepResPath = dialogView.findViewById(R.id.keepResPath);
        CompoundButton splitJson = dialogView.findViewById(R.id.splitJson);
        CompoundButton vrd = dialogView.findViewById(R.id.vrd);

        int[] frameworkVersions = context.getResources().getIntArray(R.array.framework_versions);
        int savedFramework = settings.getInt("fwVer", 35);
        int frameworkSelection = savedFramework > frameworkVersions.length ? 0 : savedFramework;
        String[] frameworkStrings = new String[frameworkVersions.length];
        for (int i = 0; i < frameworkVersions.length; i++) {
            frameworkStrings[i] = Integer.toString(frameworkVersions[i]);
        }
        ArrayAdapter<String> frameworkAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, frameworkStrings);
        frameworkVersion.setAdapter(frameworkAdapter);
        frameworkVersion.setText(frameworkStrings[frameworkSelection], false);
        frameworkVersion.setOnItemClickListener((parent, view, position, id) -> settings.edit().putInt("fwVer", frameworkVersions[position]).apply());

        String[] decodeTypesArray = new String[]{"xml", "json", "raw", "sig"};
        int savedDecodeType = settings.getInt("decodeTypes", 0);
        ArrayAdapter<String> decodeAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, decodeTypesArray);
        decodeTypes.setAdapter(decodeAdapter);
        decodeTypes.setText(decodeTypesArray[savedDecodeType], false);
        decodeTypes.setOnItemClickListener((parent, view, position, id) -> settings.edit().putInt("decodeTypes", position).apply());

        String[] dexLibraryArray = new String[]{"Internal (REAndroid)", "developer-krushna"};
        int savedDexLib = settings.getInt("dexLib", 0);
        ArrayAdapter<String> dexAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, dexLibraryArray);
        dexLibrary.setAdapter(dexAdapter);
        dexLibrary.setText(dexLibraryArray[savedDexLib], false);
        dexLibrary.setOnItemClickListener((parent, view, position, id) -> settings.edit().putInt("dexLib", position).apply());

        int savedLoadDex = settings.getInt("loadDex", 3);
        loadDex.setText(String.valueOf(savedLoadDex));
        loadDex.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    settings.edit().putInt("loadDex", Integer.parseInt(s.toString())).apply();
                } catch (NumberFormatException ignored) {}
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        flagDex.setChecked(settings.getBoolean("flagDex", false));
        flagDex.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("flagDex", isChecked).apply());

        noDexDebug.setChecked(settings.getBoolean("noDexDebug", true));
        noDexDebug.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("noDexDebug", isChecked).apply());

        dexMarkers.setChecked(settings.getBoolean("dexMarkers", false));
        dexMarkers.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("dexMarkers", isChecked).apply());

        flagForce.setChecked(settings.getBoolean("flagForce", false));
        flagForce.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("flagForce", isChecked).apply());

        keepResPath.setChecked(settings.getBoolean("keepResPath", false));
        keepResPath.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("keepResPath", isChecked).apply());

        splitJson.setChecked(settings.getBoolean("splitJson", true));
        splitJson.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("splitJson", isChecked).apply());

        vrd.setChecked(settings.getBoolean("vrd", true));
        vrd.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("vrd", isChecked).apply());

        AlertDialog.Builder builder = UIKit.dialog(context);
        builder.setTitle(R.string.decompile_options)
                .setView(dialogView)
                .setPositiveButton(R.string.decompile, (d, which) -> {
                    ProgressManager pm = new ProgressManager(context, true).show();
                    APKLogger logger = pm.getLogger();

                    new Thread(() -> {
                        try {
                            int fwVer = settings.getInt("fwVer", 35);
                            int loadDexValue = settings.getInt("loadDex", 3);
                            int decodeTypesValue = settings.getInt("decodeTypes", 0);
                            int dexLibValue = settings.getInt("dexLib", 0);
                            boolean keepDex = settings.getBoolean("flagDex", false);
                            boolean noDexDebugValue = settings.getBoolean("noDexDebug", true);
                            boolean dexMarkersValue = settings.getBoolean("dexMarkers", false);
                            boolean forceDeleteOutputPath = settings.getBoolean("flagForce", false);
                            boolean keepResPathValue = settings.getBoolean("keepResPath", false);
                            boolean splitJsonValue = settings.getBoolean("splitJson", true);
                            boolean vrdValue = settings.getBoolean("vrd", true);
                            DecompileOptions decompileOptions = new DecompileOptions();
                            decompileOptions.inputFile = file;
                            File outputFile = new File(file.getPath().replaceFirst('.' + FilenameUtils.getExtension(fileName) + "$", ""));
                            outputFile.mkdir();
                            decompileOptions.outputFile = outputFile;
                            decompileOptions.frameworkVersion = fwVer;
                            decompileOptions.loadDex = loadDexValue;
                            decompileOptions.type = decodeTypesValue == 0 ? "xml"
                                    : decodeTypesValue == 1 ? "json"
                                    : decodeTypesValue == 2 ? "raw" : "sig";
                            decompileOptions.dexLib = dexLibValue == 0 ? "internal" : "jf";
                            decompileOptions.dex = keepDex;
                            decompileOptions.dexMarkers = dexMarkersValue;
                            decompileOptions.force = forceDeleteOutputPath;
                            decompileOptions.keepResPath = keepResPathValue;
                            decompileOptions.noDexDebug = noDexDebugValue;
                            decompileOptions.splitJson = splitJsonValue;
                            decompileOptions.validateResDir = vrdValue;
                            Decompiler decompiler = decompileOptions.newCommandExecutor(logger);
                            decompiler.setEnableLog(true);
                            decompiler.runCommand();
                            logger.close();
                            pm.dismiss();
                            context.handler.post(() -> {
                                Extensions.showMessage(context, context.getString(R.string.decompiled_to, outputFile.getName()));
                                context.reloadCurrentFolder();
                            });
                        } catch (Exception e) {
                            pm.dismiss();
                            new ErrorUtil(context).showError(e);
                            logger.close();
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    public void showApkInfoDialog(File file, String fileName) {
        String filePath = file.getPath();
        View display = LayoutInflater.from(context).inflate(R.layout.apk_display, null, false);
        ImageView apkIcon = display.findViewById(R.id.apkIcon);
        TextView apkTitle = display.findViewById(R.id.apkTitle);
        TextView apkVersionName = display.findViewById(R.id.apkVersionName);
        TextView verCode = display.findViewById(R.id.verCode);
        TextView pkgName = display.findViewById(R.id.pkgName);
        TextView signaturesInApk = display.findViewById(R.id.signaturesInApk);
        TextView protectedDisplay = display.findViewById(R.id.protectedDisplay);
        TextView fileSize = display.findViewById(R.id.fileSize);
        TextView apkTargetSdk = display.findViewById(R.id.apkTargetSdk);
        TextView apkMinSdk = display.findViewById(R.id.apkMinSdk);
        TextView apkCert = display.findViewById(R.id.apkCert);
        TextView apkInstalled = display.findViewById(R.id.apkInstalled);
        TextView apkPermissions = display.findViewById(R.id.apkPermissions);
        LinearLayout permissionsHeader = display.findViewById(R.id.permissionsHeader);
        ImageView permissionsChevron = display.findViewById(R.id.apkPermissionsChevron);
        final boolean[] permissionsLoaded = {false};
        final boolean[] permissionsLoading = {false};
        permissionsHeader.setOnClickListener(v -> {
            if (apkPermissions.getVisibility() == View.VISIBLE) {
                apkPermissions.setVisibility(View.GONE);
                permissionsChevron.animate().rotation(0f).start();
                return;
            }
            apkPermissions.setVisibility(View.VISIBLE);
            permissionsChevron.animate().rotation(180f).start();
            if (permissionsLoaded[0] || permissionsLoading[0]) return;
            permissionsLoading[0] = true;
            apkPermissions.setText(R.string.loading);
            new Thread(() -> {
                CharSequence perms = "";
                try {
                    PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(filePath, PackageManager.GET_PERMISSIONS);
                    if (pi != null && pi.applicationInfo != null) {
                        pi.applicationInfo.sourceDir = filePath;
                        pi.applicationInfo.publicSourceDir = filePath;
                        perms = ApkInfoUtil.getPermissions(pi);
                    }
                } catch (Exception ignored) { }
                CharSequence finalPerms = perms;
                context.handler.post(() -> {
                    permissionsLoaded[0] = true;
                    permissionsLoading[0] = false;
                    apkPermissions.setText(TextUtils.isEmpty(finalPerms) ? context.getString(R.string.permissions_none) : finalPerms);
                });
            }).start();
        });
        apkIcon.setImageDrawable(FileIconLoader.getCachedApkIcon());
        apkTitle.setText(R.string.loading);
        apkVersionName.setText(R.string.loading);
        verCode.setText(R.string.loading);
        pkgName.setText(R.string.loading);
        signaturesInApk.setText(R.string.loading);
        protectedDisplay.setText(R.string.loading);


        AlertDialog ad = dialogUtil.getDialogBuilder()
                .setView(display)
                .setNeutralButton(R.string.more, (dialog, which) -> {
                    java.util.List<String> moreTitles = new java.util.ArrayList<>(java.util.Arrays.asList(new String[]{context.rss.getString(R.string.sign_apk), context.rss.getString(R.string.optimize_apk), context.rss.getString(R.string.decompile_reandroid_apkeditor), context.rss.getString(R.string.refactor_obfuscated_resource_names), context.rss.getString(R.string.protect_reandroid_apkeditor), context.rss.getString(R.string.clone_apk), context.rss.getString(R.string.view_certificate), context.rss.getString(R.string.kill_signature_verification), context.rss.getString(R.string.add_toast_dialog), context.rss.getString(R.string.remove_all_toasts), context.rss.getString(R.string.remove_signature), context.rss.getString(R.string.signature_health), context.rss.getString(R.string.manifest_toggles), context.rss.getString(R.string.permissions)}));
                    // Third-party APK actions appended after the 14 built-ins.
                    final java.util.List<io.github.abdurazaaqmohammed.plugins.ext.ApkMoreAction> pluginMore =
                            io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry.apkActions();
                    for (io.github.abdurazaaqmohammed.plugins.ext.ApkMoreAction ext : pluginMore) {
                        if (ext == null) continue;
                        moreTitles.add(ext.title() == null || ext.title().isEmpty() ? ext.id() : ext.title());
                    }
                    // External (out-of-process) APK actions, fire-and-forget.
                    final java.util.List<io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions.Entry> externalApk =
                            io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions.apkEntries(context);
                    for (io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions.Entry e : externalApk) {
                        if (e == null) continue;
                        moreTitles.add(e.title == null || e.title.isEmpty() ? e.id : e.title);
                    }
                    String[] items = moreTitles.toArray(new String[0]);
                    dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog12, which1) -> {
                        dialog12.dismiss();
                        if (which1 == 0) SignatureKeyDialog.show(context, file, false);
                        else if (which1 == 1) {
                            View ll = LayoutInflater.from(context).inflate(R.layout.dialog_opt, null);
                            final boolean[] sign = new boolean[1];
                            final boolean[] delFiles = new boolean[1];
                            final boolean[] deepOpt = new boolean[1];
                            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                            CheckBox autosign = ll.findViewById(R.id.autosign);
                            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                            ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                            CheckBox deepOptimize = ll.findViewById(R.id.deep_optimize);
                            deepOptimize.setChecked(deepOpt[0] = settings.getBoolean("deep_optimize", false));
                            deepOptimize.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_optimize", deepOpt[0] = isChecked).apply());
                            MaterialCheckBox phaseB = ll.findViewById(R.id.deep_optimize_phase_b);
                            phaseB.setChecked(settings.getBoolean("deep_opt_phase_b", false));
                            phaseB.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_opt_phase_b", isChecked).apply());
                            MaterialCheckBox preserveDebug = ll.findViewById(R.id.deep_optimize_preserve_debug);
                            preserveDebug.setChecked(settings.getBoolean("deep_opt_preserve_debug", true));
                            preserveDebug.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_opt_preserve_debug", isChecked).apply());
                            MaterialCheckBox removeClasses = ll.findViewById(R.id.deep_optimize_remove_classes);
                            removeClasses.setChecked(settings.getBoolean("deep_opt_remove_classes", true));
                            removeClasses.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_opt_remove_classes", isChecked).apply());
                            MaterialCheckBox removeMethods = ll.findViewById(R.id.deep_optimize_remove_methods);
                            removeMethods.setChecked(settings.getBoolean("deep_opt_remove_methods", true));
                            removeMethods.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_opt_remove_methods", isChecked).apply());
                            MaterialCheckBox removeFields = ll.findViewById(R.id.deep_optimize_remove_fields);
                            removeFields.setChecked(settings.getBoolean("deep_opt_remove_fields", true));
                            removeFields.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_opt_remove_fields", isChecked).apply());
                            TextInputEditText passesInput = ll.findViewById(R.id.deep_optimize_passes);
                            passesInput.setText(String.valueOf(settings.getInt("deep_opt_max_passes", 25)));
                            passesInput.addTextChangedListener(new TextWatcher() {
                                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                                @Override public void afterTextChanged(Editable s) {
                                    try {
                                        int value = Integer.parseInt(s.toString());
                                        if (value >= 0) settings.edit().putInt("deep_opt_max_passes", value).apply();
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            });
                            CheckBox deleteFiles = ll.findViewById(R.id.files_to_delete);
                            deleteFiles.setChecked(delFiles[0] = settings.getBoolean("delFiles", true));
                            deleteFiles.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("delFiles", delFiles[0] = isChecked).apply());
                            ll.findViewById(R.id.choose_files_delete).setOnClickListener(v8 -> {
                                Set<String> filesToDelete = settings.getStringSet("filesToDelete", null);
                                String[] filesFiDelete = (filesToDelete == null) ? new String[]{"assets/audience_network.dex", "androidsupportmultidexversion.txt", "DebugProbesKt.bin", "stamp-cert-sha256", "user-messaging-platform.properties", "transport-runtime.properties", "transport-backend-cct.properties", "transport-api.properties", "protolite-well-known-types.properties", "play-services-tasks.properties", "play-services-stats.properties", "play-services-measurement-sdk-api.properties", "play-services-measurement-sdk.properties", "play-services-measurement-impl.properties", "play-services-measurement-base.properties", "play-services-measurement-api.properties", "play-services-measurement.properties", "play-services-cloud-messaging.properties", "play-services-basement.properties", "play-services-base.properties", "play-services-appset.properties", "play-services-ads-lite.properties", "play-services-ads-identifier.properties", "play-services-ads-base.properties", "play-services-ads.properties", "firebase-abt.properties", "firebase-analytics-ktx.properties", "firebase-analytics.properties", "firebase-annotations.properties", "firebase-common-ktx.properties", "firebase-common.properties", "firebase-components.properties", "firebase-config-ktx.properties", "firebase-config.properties", "firebase-crashlytics-ktx.properties", "firebase-crashlytics.properties", "firebase-datatransport.properties", "firebase-encoders-json.properties", "firebase-encoders-proto.properties", "firebase-encoders.properties", "firebase-iid-interop.properties", "firebase-installations-interop.properties", "firebase-installations.properties", "firebase-measurement-connector.properties", "firebase-messaging-ktx.properties", "firebase-messaging.properties", "firebase-perf-ktx.properties", "firebase-perf.properties"}
                                        : filesToDelete.toArray(new String[0]);
                                List<String> filesFiDel = new ArrayList<>(Arrays.asList(filesFiDelete));
                                ListView listView = new ListView(context);
                                ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.item_bottom_bar_config, filesFiDel) {
                                    @NonNull
                                    @Override
                                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                        if (convertView == null)
                                            convertView = LayoutInflater.from(context).inflate(R.layout.item_bottom_bar_config, parent, false);
                                        TextView textLabel = convertView.findViewById(R.id.text_label);
                                        ImageButton btnEdit = convertView.findViewById(R.id.btn_edit);
                                        ImageButton btnDelete = convertView.findViewById(R.id.btn_delete);
                                        textLabel.setText(filesFiDel.get(position));
                                        btnEdit.setVisibility(View.GONE);
                                        btnDelete.setOnClickListener(v -> {
                                            filesFiDel.remove(position);
                                            notifyDataSetChanged();
                                        });
                                        return convertView;
                                    }
                                };
                                listView.setAdapter(adapter);
                                dialogUtil.getDialogBuilder()
                                        .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                                        .setNeutralButton(context.rss.getString(R.string.add), (dialog9, which6) -> {
                                            EditText et = new EditText(context);
                                            dialogUtil.getDialogBuilder().setView(UiFields.wrap(context, et, null, 16)).setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                                                    .setPositiveButton(context.rss.getString(io.github.rosemoe.sora.R.string.sora_editor_next), (dialog8, which5) -> {
                                                        filesFiDel.add(et.getText().toString());
                                                        adapter.notifyDataSetChanged();
                                                    }).show();
                                        })
                                        .setPositiveButton(context.rss.getString(io.github.rosemoe.sora.R.string.sora_editor_next), (dialog8, which5) -> settings.edit().putStringSet("filesToDelete", new HashSet<>(filesFiDel)).apply())
                                        .setView(listView)
                                        .show();
                            });
                                     dialogUtil.getDialogBuilder().setView(ll)
                                            .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                                            .setPositiveButton(context.rss.getString(R.string.opt), (dialog7, which4) -> {
                                                SignWrapper[] wrapper = new SignWrapper[1];
                                                Runnable doOpt = () -> {
                                                    ProgressManager pm = new ProgressManager(context, true).show();
                                                    APKLogger logger = pm.getLogger();
                                                    new Thread(() -> {
                                                        try {
                                                            File opt = ApkOptimizer.optimize(context, file, delFiles[0], settings, logger);
                                                            if (deepOpt[0]) {
                                                                logger.logMessage(context.rss.getString(R.string.deep_optimize_running));
                                                                opt = ApkDeepOptimizer.optimize(context, opt, settings.getStringSet("filesToDelete", null), settings, logger);
                                                            }
                                                            if (sign[0]) wrapper[0].signApk(opt);
                                                            pm.dismiss();
                                                            context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                                        } catch (Exception e) {
                                                            pm.dismiss();
                                                            new ErrorUtil(context).showError(e);
                                                        }
                                                    }).start();
                                                };
                                                Runnable startOpt = () -> {
                                                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                                        wrapper[0] = sw;
                                                        doOpt.run();
                                                    }); else doOpt.run();
                                                };
                                                if (deepOpt[0]) {
                                                    UIKit.dialog(context)
                                                            .setTitle(context.rss.getString(R.string.deep_optimize))
                                                            .setMessage(context.rss.getString(R.string.deep_optimize_warning))
                                                            .setPositiveButton(context.rss.getString(R.string.opt), (dialog8, which5) -> startOpt.run())
                                                            .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                                                            .show();
                                                } else startOpt.run();
                                            }).show();
                    } else if (which1 == 2) showDecompileOptionsDialog(file, fileName);
                    else if (which1 == 3) {
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                        boolean forceDeleteOutputPath = settings.getBoolean("flagForce", false);
                        boolean cleanMeta = settings.getBoolean("cleanMeta", true);
                        boolean fixTypes = settings.getBoolean("fixTypes", true);
                        RefactorOptions options = new RefactorOptions();
                        options.inputFile = file;
                        String extension = FilenameUtils.getExtension(fileName);
                        options.outputFile = new File(file.getParentFile(), fileName.replace('.' + extension, "_refactored." + extension));
                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        final String[] publicXmlPath = {null};
                        MaterialButton publicXmlInputView = new MaterialButton(context);
                        String publiXmlText = context.rss.getString(R.string.public_xml);
                        publicXmlInputView.setText(publiXmlText);
                        publicXmlInputView.setOnClickListener(v5 -> {
                            FilePickerDialog.Properties properties = new FilePickerDialog.Properties();
                            properties.selection_mode = FilePickerDialog.SINGLE_MODE;
                            properties.selection_type = FilePickerDialog.FILE_SELECT;
                            properties.root = new File(Environment.getExternalStorageDirectory().getPath());
                            properties.offset = new File(Environment.getExternalStorageDirectory().getPath());
                            properties.preferenceKey = "public_xml";
                            properties.extensions = new String[]{"xml"};
                            FilePickerDialog fpd = new FilePickerDialog(context, properties);
                            fpd.setTitle(publiXmlText);
                            fpd.setDialogSelectionListener(files -> publicXmlPath[0] = files[0]);
                            fpd.show();
                        });
                        MaterialSwitch cleanMetaSwitch = new MaterialSwitch(context);
                        cleanMetaSwitch.setText(context.rss.getString(R.string.clean_meta));
                        cleanMetaSwitch.setChecked(cleanMeta);
                        cleanMetaSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("cleanMeta", isChecked2).apply());
                        MaterialSwitch fixTypesSwitch = new MaterialSwitch(context);
                        fixTypesSwitch.setText(R.string.fix_types);
                        fixTypesSwitch.setChecked(fixTypes);
                        fixTypesSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("fixTypes", isChecked2).apply());
                        MaterialSwitch forceSwitch = new MaterialSwitch(context);
                        forceSwitch.setText(context.rss.getString(R.string.force_delete_output_path));
                        forceSwitch.setChecked(forceDeleteOutputPath);
                        forceSwitch.setOnCheckedChangeListener((buttonView, isChecked2) -> settings.edit().putBoolean("flagForce", isChecked2).apply());
                        layout.addView(publicXmlInputView);
                        layout.addView(cleanMetaSwitch);
                        layout.addView(fixTypesSwitch);
                        layout.addView(forceSwitch);
                        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder().setView(layout)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.refactor, (dialog2, which3) -> {
                                    options.cleanMeta = settings.getBoolean("cleanMeta", true);
                                    options.fixTypeNames = settings.getBoolean("fixTypes", true);
                                    options.force = settings.getBoolean("flagForce", false);
                                    ProgressManager pm = new ProgressManager(context, true).show();
                                    APKLogger logger = pm.getLogger();
                                    new Thread(() -> {
                                        try {
                                            String pXmlFilePath = publicXmlPath[0];
                                            if (!TextUtils.isEmpty(pXmlFilePath)) options.publicXml = new File(pXmlFilePath);
                                            options.newCommandExecutor(logger).runCommand();
                                            logger.close();
                                            pm.dismiss();
                                            Extensions.showMessage(context, context.getString(R.string.refactored, fileName));
                                        } catch (Exception e) {
                                            pm.dismiss();
                                            context.handler.post(() -> new ErrorUtil(context).showError(e));
                                            logger.close();
                                        }
                                    }).start();
                                }).create());
                    }
                    else if (which1 == 4) {
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                        boolean skipManifest = settings.getBoolean("skipManifest", false);
                        boolean confuseZip = settings.getBoolean("confuseZip", false);
                        int dexLevel = settings.getInt("dexLevel", 0);
                        boolean flagForce = settings.getBoolean("flagForce", false);
                        ProtectorOptions options = new ProtectorOptions();
                        options.inputFile = file;
                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        LayoutInflater layoutInflater = LayoutInflater.from(context);
                        View skipManifestView = layoutInflater.inflate(R.layout.item_switch, layout, false);
                        TextView skipManifestTitle = skipManifestView.findViewById(R.id.title);
                        CheckBox skipManifestSwtch = skipManifestView.findViewById(R.id.switch_view);
                        skipManifestTitle.setText(context.rss.getString(R.string.skip_manifest_protection));
                        skipManifestSwtch.setChecked(skipManifest);
                        skipManifestSwtch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("skipManifest", isChecked).apply());
                        skipManifestView.setOnClickListener(v2 -> skipManifestSwtch.toggle());
                        View confuseZipView = layoutInflater.inflate(R.layout.item_switch, layout, false);
                        TextView confuseZipTitle = confuseZipView.findViewById(R.id.title);
                        CheckBox confuseZipSwtch = confuseZipView.findViewById(R.id.switch_view);
                        confuseZipTitle.setText(context.rss.getString(R.string.confuse_zip_structure));
                        confuseZipSwtch.setChecked(confuseZip);
                        confuseZipSwtch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("confuseZip", isChecked).apply());
                        confuseZipView.setOnClickListener(v2 -> confuseZipSwtch.toggle());
                        View dexLevelView = layoutInflater.inflate(R.layout.item_edit_number, layout, false);
                        TextView dexLevelTitle = dexLevelView.findViewById(R.id.title);
                        EditText dexLevelInput = dexLevelView.findViewById(R.id.edit_text);
                        dexLevelTitle.setText(context.rss.getString(R.string.dex_protection_level));
                        dexLevelInput.setText(String.valueOf(dexLevel));
                        dexLevelInput.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                            @Override public void afterTextChanged(Editable s) {
                                try {
                                    settings.edit().putInt("dexLevel", Integer.parseInt(s.toString())).apply();
                                } catch (Exception ignored) {}
                            }
                        });
                        View forceView = layoutInflater.inflate(R.layout.item_switch, layout, false);
                        TextView forceTitle = forceView.findViewById(R.id.title);
                        CheckBox forceSwitch = forceView.findViewById(R.id.switch_view);
                        forceTitle.setText(context.rss.getString(R.string.force_delete_output_path));
                        forceSwitch.setChecked(flagForce);
                        forceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("flagForce", isChecked).apply());
                        forceView.setOnClickListener(v2 -> forceSwitch.toggle());
                        layout.addView(skipManifestView);
                        layout.addView(confuseZipView);
                        layout.addView(dexLevelView);
                        layout.addView(forceView);
                        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder().setView(layout)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.protect, (dialog2, which3) -> {
                                    options.skipManifest = settings.getBoolean("skipManifest", false);
                                    options.confuse_zip = settings.getBoolean("confuseZip", false);
                                    options.dexLevel = settings.getInt("dexLevel", 0);
                                    options.force = settings.getBoolean("flagForce", false);
                                    options.outputFile = options.generateOutputFromInput(file);
                                    ProgressManager pm = new ProgressManager(context, true).show();
                                    APKLogger logger = pm.getLogger();
                                    new Thread(() -> {
                                        try {
                                            options.newCommandExecutor(logger).runCommand();
                                            logger.close();
                                            pm.dismiss();
                                            context.handler.post(() -> { dialog2.dismiss(); Extensions.showMessage(context, context.rss.getString(R.string.protectd)); });
                                        } catch (Exception e) {
                                            pm.dismiss();
                                            context.handler.post(dialog2::dismiss);
                                            new ErrorUtil(context).showError(e);
                                            logger.close();
                                        }
                                    }).start();
                                }).create());
                    }
                    else if (which1 == 5) {
                        View ll = LayoutInflater.from(context).inflate(R.layout.dialog_clone, null);
                        TextView pkgNameView = ll.findViewById(R.id.package_name_input);
                        String pkgNameFromApk = overlay.getPackageNameFromApk(filePath);
                        pkgNameView.setText(ApkCloner.changeEndCharacter(pkgNameFromApk));
                        final boolean[] sign = new boolean[1];
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                        CheckBox autosign = ll.findViewById(R.id.autosign);
                        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                        ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
                        dialogUtil.getDialogBuilder().setView(ll)
                                .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                                .setPositiveButton(context.rss.getString(R.string.clone), (dialog6, which2) -> {
                                    SignWrapper[] wrapper = new SignWrapper[1];
                                    Runnable doClone = () -> {
                                        ProgressManager pm = new ProgressManager(context, false).show();
                                        APKLogger logger = pm.getLogger();
                                        new Thread(() -> {
                                            ApkCloner apkCloner = new ApkCloner(context, new ApkCloner.ApkClonerCallBack() {
                                            @Override public void onMessage(String msg) { logger.logMessage(msg); }
                                            @Override public void onProgress(int progress, int total) { pm.setProgress(progress, total); }
                                        });
                                            String pkgNameInput = pkgNameView.getText().toString();
                                            apkCloner.setPath(filePath, pkgNameFromApk, pkgNameInput);
                                            try {
                                                apkCloner.processApk();
                                                File cloned = new File(filePath.replace(".apk", "_clone.apk"));
                                                if (sign[0]) {
                                                    wrapper[0].signApk(cloned);
                                                }
                                                pm.dismiss();
                                                context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                            } catch (Exception e) { pm.dismiss(); new ErrorUtil(context).showError(e); }
                                        }).start();
                                    };
                                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                        wrapper[0] = sw;
                                        doClone.run();
                                    }); else doClone.run();
                                }).show();
                    } else if (which1 == 6) signatures.showCertificateDialog(file);
                    else if (which1 == 7) signatures.killSignatureVerification(file, fileName);
                    else if (which1 == 8) overlay.showAddToastDialog(file, filePath);
                    else if (which1 == 9) overlay.showRemoveAllToastsDialog(file);
                    else if (which1 == 10) signatures.removeSignature(file);
                    else if (which1 == 11) signatures.showSignatureHealthDialog(file);
                    else if (which1 == 12) manifestEditor.showManifestTogglesDialog(file);
                    else if (which1 == 13) manifestEditor.showPermissionsDialog(file);
                    else {
                        // Third-party APK action: indices 0-13 are built-ins above.
                        int pluginIndex = which1 - 14;
                        if (pluginIndex >= 0 && pluginIndex < pluginMore.size()) {
                            io.github.abdurazaaqmohammed.plugins.ext.ApkMoreAction ext = pluginMore.get(pluginIndex);
                            if (ext != null) {
                                try {
                                    ext.run(new io.github.abdurazaaqmohammed.plugins.ext.ApkJob(context, file, fileName, filePath));
                                } catch (Exception ignored) {
                                }
                            }
                        } else {
                            int extIndex = which1 - 14 - pluginMore.size();
                            if (extIndex >= 0 && extIndex < externalApk.size()) {
                                io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions.Entry found =
                                        externalApk.get(extIndex);
                                if (found != null) {
                                    try {
                                        android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                                                context, context.getPackageName() + ".provider", file);
                                        android.content.Intent extIntent = io.github.abdurazaaqmohammed.plugins.ipc.PluginHost.explicitIntent(
                                                found.plugin, io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts.ACTION_APK);
                                        extIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                        extIntent.putExtra(io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts.EXTRA_PLUGIN_ID,
                                                found.plugin.pluginId);
                                        extIntent.putExtra(io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts.EXTRA_APK_NAME,
                                                fileName);
                                        extIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        try {
                                            context.grantUriPermission(found.plugin.packageName, apkUri,
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        } catch (Exception ignored) {
                                        }
                                        io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust.ensureTrusted(
                                                context, found.plugin, () -> {
                                                    try {
                                                        context.startActivity(extIntent);
                                                    } catch (Exception ignored) {
                                                    }
                                                });
                                    } catch (Exception e2) {
                                        try {
                                            Extensions.showMessage(context, "Cannot share this APK with the plugin");
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }).show();
                })
                .setPositiveButton(R.string.install, (dialog, which) -> InstallUtil.installApkWithDialog(context, file))
                .setNegativeButton(R.string.view, (dialog, which) -> openZipFile(file))
                .create();
        dialogUtil.styleAlertDialog(ad);
        LinearLayout rootInfoSection = display.findViewById(R.id.rootInfoSection);
        LinearLayout rootInfoHeader = display.findViewById(R.id.rootInfoHeader);
        ImageView rootInfoChevron = display.findViewById(R.id.rootInfoChevron);
        LinearLayout rootInfoContent = display.findViewById(R.id.rootInfoContent);
        final boolean[] rootInfoLoaded = {false};

        RootManager rm = RootManager.getInstance(context);
        if (rm.isRootAvailable() && rm.isRootFileOpsEnabled()) {
            rootInfoSection.setVisibility(View.VISIBLE);
            rootInfoHeader.setOnClickListener(v -> {
                if (rootInfoContent.getVisibility() == View.VISIBLE) {
                    rootInfoContent.setVisibility(View.GONE);
                    rootInfoChevron.animate().rotation(0f).start();
                    return;
                }
                rootInfoContent.setVisibility(View.VISIBLE);
                rootInfoChevron.animate().rotation(180f).start();
                if (rootInfoLoaded[0]) return;
                rootInfoLoaded[0] = true;
                rootInfoContent.removeAllViews();
                TextView loading = new TextView(context);
                loading.setText(R.string.loading);
                loading.setTextSize(12);
                loading.setPadding(0, dp(4), 0, dp(4));
                rootInfoContent.addView(loading);
                new Thread(() -> {
                    String[] pkg = {""};
                    context.handler.post(() -> pkg[0] = pkgName.getText().toString());
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    String pkgNameStr = pkg[0];
                    if (TextUtils.isEmpty(pkgNameStr)) return;
                    String uid = rm.getAppUid(pkgNameStr);
                    String apkPath = null;
                    try { apkPath = rm.getAppApkPath(pkgNameStr); } catch (Exception ignored) {}
                    List<String> dataDirs = rm.getAppDataDirs(pkgNameStr);
                    String finalApkPath = apkPath;
                    context.handler.post(() -> {
                        rootInfoContent.removeAllViews();
                        if (uid != null) addRootInfoRow(rootInfoContent, "UID", uid, null, ad);
                        if (finalApkPath != null) addRootInfoRow(rootInfoContent, "APK Path", finalApkPath, finalApkPath, ad);
                        for (String dir : dataDirs) {
                            @SuppressLint("SdCardPath")
                            String label = dir.contains("/data/data/") || dir.contains("/data/user/") ? "Data Dir" : "External Data Dir";
                            addRootInfoRow(rootInfoContent, label, dir, dir, ad);
                        }
                        if (rootInfoContent.getChildCount() == 0) {
                            TextView empty = new TextView(context);
                            empty.setText(R.string.no_root_info_available);
                            empty.setTextSize(12);
                            empty.setPadding(0, dp(4), 0, dp(4));
                            rootInfoContent.addView(empty);
                        }
                    });
                }).start();
            });
        }

        display.findViewById(R.id.quickEdit).setOnClickListener(v7 -> {
            ad.dismiss();
            manifestEditor.showEditManifestDialog(file);
        });
        ad.show();

        new Thread(() -> {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo packageInfo = pm.getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES);
                final ApplicationInfo appInfo;
                if (packageInfo == null || (appInfo = packageInfo.applicationInfo) == null) {
                    context.handler.post(() -> {
                        ad.dismiss();
                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, context.getContentResolver().getType(uri))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open " + fileName));
                    });
                    return;
                }
                if (TextUtils.isEmpty(appInfo.sourceDir) || TextUtils.isEmpty(appInfo.publicSourceDir)) {
                    appInfo.sourceDir = filePath;
                    appInfo.publicSourceDir = filePath;
                }
                Drawable icon = appInfo.loadIcon(pm);
                String label = appInfo.loadLabel(pm).toString();
                String verName = packageInfo.versionName;
                int vCode = packageInfo.versionCode;
                String pkg = packageInfo.packageName;

                StringBuilder sigs = new StringBuilder();
                final String[] certFp = {""};
                try {
                    ApkVerifier.Result result = new ApkVerifier.Builder(file).build().verify();
                    try {
                        List<X509Certificate> certs = result.getSignerCertificates();
                        if (certs != null && !certs.isEmpty()) certFp[0] = CertUtil.getSha256(certs.get(0));
                    } catch (Exception ignored) {}
                    boolean verified = result.isVerified();
                    boolean v1 = result.isVerifiedUsingV1Scheme();
                    boolean v2 = result.isVerifiedUsingV2Scheme();
                    boolean v3 = result.isVerifiedUsingV3Scheme();
                    boolean v31 = result.isVerifiedUsingV31Scheme();
                    boolean v4 = result.isVerifiedUsingV4Scheme();
                    if (v1) sigs.append("V1");
                    if (v2) { if (v1) sigs.append(" + "); sigs.append("V2"); }
                    if (v3 || v31) { if (v1 || v2) sigs.append(" + "); sigs.append("V3"); }
                    if (v4) { if (v1 || v2 || v3 || v31) sigs.append(" + "); sigs.append("V4"); }
                    if (verified && !TextUtils.isEmpty(sigs)) { /* use sigs */ }
                    else {
                        sigs.setLength(0);
                        try (ArchiveFile af = new ArchiveFile(file)) {
                            sigs.append(af.getEntrySource("META-INF/MANIFEST.MF") == null ? "Not signed" : "Verification failed");
                        }
                    }
                } catch (Exception e) {
                    sigs.append(context.rss.getString(android.R.string.unknownName));
                }
                String signatureStr = sigs.toString();

                String protectedStr;
                try (ArchiveFile af = new ArchiveFile(file); ApkModule am = new ApkModule(af.createZipEntryMap())) {
                    String aProtected = Util.isProtected(am);
                    protectedStr = TextUtils.isEmpty(aProtected) ? "Not found" : aProtected;
                } catch (Exception e) {
                    protectedStr = context.rss.getString(android.R.string.unknownName);
                }

                String finalProtectedStr = protectedStr;
                context.handler.post(() -> {
                    apkIcon.setImageDrawable(icon);
                    apkTitle.setText(label);
                    apkVersionName.setText(verName);
                    verCode.setText(Integer.toString(vCode));
                    pkgName.setText(pkg);
                    uiHelper.scrollTextView(pkgName);
                    signaturesInApk.setText(signatureStr);
                    protectedDisplay.setText(finalProtectedStr);
                    fileSize.setText(context.getString(R.string.fs_entries, Formatter.formatFileSize(context, file.length()), ApkInfoUtil.getEntryCount(file)));
                    apkTargetSdk.setText(String.valueOf(packageInfo.applicationInfo.targetSdkVersion));
                    int min = ApkInfoUtil.getMinSdk(packageInfo);
                    apkMinSdk.setText(min < 0 ? context.getString(R.string.unknown_sdk) : String.valueOf(min));
                    apkCert.setText(TextUtils.isEmpty(certFp[0]) ? context.getString(R.string.no_signature_found) : certFp[0]);
                    String installedVer = ApkInfoUtil.getInstalledVersion(context, pkg);
                    if (installedVer == null) apkInstalled.setText(context.getString(R.string.not_installed));
                    else {
                        String installedText = installedVer;
                        if (ApkInfoUtil.isDowngrade(context, packageInfo)) installedText += " (" + context.getString(R.string.downgrade) + ")";
                        apkInstalled.setText(installedText);
                    }
                });
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
            View.OnLongClickListener lcl = v -> {
                if(v instanceof TextView tv) CopyUtil.copyToClipboard(ad, tv.getText());
                return false;
            };
            context.handler.post(() -> {
                signaturesInApk.setOnLongClickListener(lcl);
                protectedDisplay.setOnLongClickListener(lcl);
                pkgName.setOnLongClickListener(lcl);
                apkTitle.setOnLongClickListener(lcl);
                apkVersionName.setOnLongClickListener(lcl);
                verCode.setOnLongClickListener(lcl);
                fileSize.setOnLongClickListener(lcl);
                apkTargetSdk.setOnLongClickListener(lcl);
                apkMinSdk.setOnLongClickListener(lcl);
                apkCert.setOnLongClickListener(lcl);
                apkInstalled.setOnLongClickListener(lcl);
                apkPermissions.setOnLongClickListener(lcl);
            });
        }).start();
    }

    private void openZipFile(File file) {
        context.loadZipFolderInPane(file, "", pane1, true);
    }


    private void addRootInfoRow(LinearLayout parent, String label, String value, String tapPath, AlertDialog ad) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(MaterialColors.getColor(labelView, com.google.android.material.R.attr.colorOnSurfaceVariant));
        row.addView(labelView);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(12);
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(valueView);

        if (tapPath != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                ad.dismiss();
                File dir = new File(tapPath);
                if (!dir.exists()) {
                    Extensions.showMessage(context, "Path " + tapPath + "not accessible");
                    return;
                }
                File target = dir.isFile() ? dir.getParentFile() : dir;
                if (target != null) {
                    context.loadFolderInPane(target, pane1);

                }
            });
        }

        parent.addView(row);
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

}