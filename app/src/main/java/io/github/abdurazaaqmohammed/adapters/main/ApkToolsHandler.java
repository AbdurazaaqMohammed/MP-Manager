package io.github.abdurazaaqmohammed.adapters.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.android.apksig.ApkVerifier;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
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
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import mt.modder.hub.apkCloner.util.ApkCloner;

public class ApkToolsHandler {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;
    private final ApkManifestEditor manifestEditor;

    public ApkToolsHandler(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper,
                           boolean pane1, ApkManifestEditor manifestEditor) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
        this.manifestEditor = manifestEditor;
    }

    public void showDecompileOptionsDialog(File file, String fileName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_decompile_options, null);

        MaterialAutoCompleteTextView frameworkVersion = dialogView.findViewById(R.id.frameworkVersion);
        MaterialAutoCompleteTextView decodeTypes = dialogView.findViewById(R.id.decodeTypes);
        MaterialAutoCompleteTextView dexLibrary = dialogView.findViewById(R.id.dexLibrary);
        TextInputEditText loadDex = dialogView.findViewById(R.id.loadDex);
        android.widget.CompoundButton flagDex = dialogView.findViewById(R.id.flagDex);
        android.widget.CompoundButton noDexDebug = dialogView.findViewById(R.id.noDexDebug);
        android.widget.CompoundButton dexMarkers = dialogView.findViewById(R.id.dexMarkers);
        android.widget.CompoundButton flagForce = dialogView.findViewById(R.id.flagForce);
        android.widget.CompoundButton keepResPath = dialogView.findViewById(R.id.keepResPath);
        android.widget.CompoundButton splitJson = dialogView.findViewById(R.id.splitJson);
        android.widget.CompoundButton vrd = dialogView.findViewById(R.id.vrd);

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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
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
                .setNeutralButton("More", (dialog, which) -> {
                    String[] items = new String[]{"Sign APK", "Optimize APK", "Decompile (REAndroid APKEditor)", "Refactor obfuscated resource names", "Protect (REAndroid APKEditor)", "Clone APK", context.getString(R.string.view_certificate)};
                    dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog12, which1) -> {
                        dialog12.dismiss();
                        if (which1 == 0) SignatureKeyDialog.show(context, file, false);
                        else if (which1 == 1) {
                            View ll = LayoutInflater.from(context).inflate(R.layout.dialog_opt, null);
                            final boolean[] sign = new boolean[1];
                            final boolean[] delFiles = new boolean[1];
                            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                            CheckBox autosign = ll.findViewById(R.id.autosign);
                            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
                            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
                            ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
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
                                            dialogUtil.getDialogBuilder().setView(et).setNegativeButton(context.rss.getString(android.R.string.cancel), null)
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
                                                            if (sign[0]) wrapper[0].signApk(opt);
                                                            pm.dismiss();
                                                            context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                                        } catch (Exception e) {
                                                            pm.dismiss();
                                                            new ErrorUtil(context).showError(e);
                                                        }
                                                    }).start();
                                                };
                                                if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                                    wrapper[0] = sw;
                                                    doOpt.run();
                                                }); else doOpt.run();
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
                            DialogProperties properties = new DialogProperties();
                            properties.selection_mode = DialogConfigs.SINGLE_MODE;
                            properties.selection_type = DialogConfigs.FILE_SELECT;
                            properties.root = new File(DialogConfigs.DEFAULT_DIR);
                            properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
                            properties.offset = new File(DialogConfigs.DEFAULT_DIR);
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
                                .setPositiveButton("Refactor", (dialog2, which3) -> {
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
                    } else if (which1 == 4) {
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
                                .setPositiveButton("Protect", (dialog2, which3) -> {
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
                    } else if (which1 == 5) {
                        View ll = LayoutInflater.from(context).inflate(R.layout.dialog_clone, null);
                        TextView pkgNameView = ll.findViewById(R.id.package_name_input);
                        String pkgNameFromApk = getPackageNameFromApk(filePath);
                        pkgNameView.setText(ApkCloner.changeEndCharacter(pkgNameFromApk));
                        final boolean[] sign = new boolean[1];
                        SharedPreferences settings = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
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
                                                if (sign[0]) wrapper[0].signApk(new File(filePath.replace(".apk", "_clone.apk")));
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
                    } else if (which1 == 6) showCertificateDialog(file);
                    }).show();
                })
                .setPositiveButton("Install", (dialog, which) -> InstallUtil.installApkWithDialog(context, file))
                .setNegativeButton("View", (dialog, which) -> openZipFile(file))
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
                if(v instanceof TextView tv) CopyUtil.copyToClipboard(context, tv.getText());
                return false;
            };
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
        }).start();
    }

    private void openZipFile(File file) {
        context.loadZipFolderInPane(file, "", pane1, true);
    }

    private String getPackageNameFromApk(String filePath) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES);
            if (pi != null && pi.applicationInfo != null) return pi.applicationInfo.packageName;
        } catch (Exception ignored) {}
        return "";
    }

    public void showCertificateDialog(File apkFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                List<X509Certificate> certs = CertUtil.getCertificates(apkFile);
                CharSequence text;
                if (certs == null || certs.isEmpty()) text = context.getString(R.string.no_signature_found);
                else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < certs.size(); i++) {
                        if (i > 0) sb.append("\n\n");
                        sb.append(context.getString(R.string.cert, i + 1)).append('\n');
                        sb.append(CertUtil.describe(certs.get(i)));
                    }
                    text = sb;
                }
                pm.dismiss();
                context.handler.post(() -> dialogUtil.getDialogBuilder()
                        .setTitle(R.string.view_certificate)
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, text))
                        .show());
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void showCompareApksDialog(File f1, File f2) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                String report = ApkCompareUtil.compare(context, f1, f2);
                pm.dismiss();
                context.handler.post(() -> {
                    TextView tv = new TextView(context);
                    tv.setText(report);
                    tv.setTextIsSelectable(true);
                    tv.setTextSize(13);
                    tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                    int pad = dp(16);
                    tv.setPadding(pad, pad, pad, pad);
                    android.widget.ScrollView scroll = new android.widget.ScrollView(context);
                    scroll.addView(tv);
                    dialogUtil.getDialogBuilder()
                            .setTitle(R.string.compare_apks)
                            .setView(scroll)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, report))
                            .show();
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void batchSignApks(List<File> apks) {
        SignWrapper.requireAuth(context, sw -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    for (int i = 0; i < apks.size(); i++) {
                        File apk = apks.get(i);
                        String msg = context.rss.getString(R.string.signing, apk.getName());
                        pm.setText(msg);
                        logger.logMessage(msg);
                        sw.signApk(apk);
                    }
                    pm.dismiss();
                    context.handler.post(() -> {
                        Extensions.showMessage(context, context.rss.getString(R.string.signed, apks.size() + " APKs"));
                        context.loadFolderInPane(apks.get(0).getParentFile(), pane1, false);
                    });
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        });
    }

    public void batchOptimizeApks(List<File> apks) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean sign = settings.getBoolean("autosign", true);
        boolean delFiles = settings.getBoolean("delFiles", true);
        if (sign) SignWrapper.requireAuth(context, sw -> runBatchOptimize(apks, delFiles, sw, settings));
        else runBatchOptimize(apks, delFiles, null, settings);
    }

    private void runBatchOptimize(List<File> apks, boolean delFiles, SignWrapper wrapper, SharedPreferences settings) {
        ProgressManager pm = new ProgressManager(context, true).show();
        APKLogger logger = pm.getLogger();
        new Thread(() -> {
            try {
                for (int i = 0; i < apks.size(); i++) {
                    File apk = apks.get(i);
                    String msg = context.rss.getString(R.string.optimizing, apk.getName());
                    pm.setText(msg);
                    logger.logMessage(msg);
                    File opt = ApkOptimizer.optimize(context, apk, delFiles, settings, logger);
                    if (wrapper != null) wrapper.signApk(opt);
                }
                pm.dismiss();
                context.handler.post(() -> {
                    Extensions.showMessage(context, context.rss.getString(R.string.opt_done));
                    context.loadFolderInPane(apks.get(0).getParentFile(), pane1, false);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void addRootInfoRow(LinearLayout parent, String label, String value, String tapPath, AlertDialog ad) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(11);
        labelView.setTextColor(com.google.android.material.color.MaterialColors.getColor(labelView, com.google.android.material.R.attr.colorOnSurfaceVariant));
        row.addView(labelView);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(12);
        valueView.setTypeface(android.graphics.Typeface.MONOSPACE);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(valueView);

        if (tapPath != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                ad.dismiss();
                java.io.File dir = new java.io.File(tapPath);
                if (!dir.exists()) {
                    Extensions.showMessage(context, "Path " + tapPath + "not accessible");
                    return;
                }
                java.io.File target = dir.isFile() ? dir.getParentFile() : dir;
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
