package io.github.abdurazaaqmohammed.adapters.main;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import io.github.abdurazaaqmohammed.utils.SignatureKillerUtil;
import mt.modder.hub.apkCloner.util.ApkCloner;

public class ApkToolsHandler {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;
    private final ApkManifestEditor manifestEditor;
    private ImageView overlayImagePreviewView;
    private String overlayImageBase64;
    private OverlayForm activeOverlayForm;
    private Runnable activeAdvRender;
    private static OverlayInjectorUtil.AdvWidget styleClipboard;

    private static void pasteStyle(OverlayInjectorUtil.AdvWidget dst,
                                   OverlayInjectorUtil.AdvWidget src) {
        if (dst == null || src == null) return;
        dst.text = src.text;
        dst.textSizeSp = src.textSizeSp;
        dst.textColor = src.textColor;
        dst.fontStyle = src.fontStyle;
        dst.font = src.font;
        dst.fontPath = src.fontPath;
        dst.imageB64 = src.imageB64;
        dst.btnAction = src.btnAction;
        dst.url = src.url;
        dst.btnBg = src.btnBg;
        dst.btnBg2 = src.btnBg2;
        dst.btnCornerRadiusDp = src.btnCornerRadiusDp;
        dst.btnBorderWidthDp = src.btnBorderWidthDp;
        dst.btnBorderColor = src.btnBorderColor;
        dst.btnPaddingDp = src.btnPaddingDp;
        dst.btnAnim = src.btnAnim;
        dst.btnAnimColors = src.btnAnimColors == null ? null
                : new ArrayList<>(src.btnAnimColors);
        dst.btnAnimSpeedMs = src.btnAnimSpeedMs;
        dst.btnAnimRainbow = src.btnAnimRainbow;
    }

    private void renderAdvSafe() {
        if (activeAdvRender != null) activeAdvRender.run();
    }

    public ApkToolsHandler(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper,
                           boolean pane1, ApkManifestEditor manifestEditor) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
        this.manifestEditor = manifestEditor;
    }

    private interface ImagePicked {
        void onImage(Bitmap thumb, String base64);
    }

    private ImagePicked pendingImagePick;

    private void pickImage(ImagePicked cb) {
        pendingImagePick = cb;
        try {
            MainActivity.overlayImageCallback = this::loadOverlayImageV2;
            context.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 9021);
        } catch (Exception e) {
            pendingImagePick = null;
            Extensions.showMessage(context, "Image picker unavailable");
        }
    }

    private void loadOverlayImageV2(Uri uri) {
        ImagePicked cb = pendingImagePick;
        pendingImagePick = null;
        decodePickedImage(uri, (thumb, b64) -> {
            if (cb != null) cb.onImage(thumb, b64);
        });
    }

    private void decodePickedImage(Uri uri, ImagePicked cb) {
        new Thread(() -> {
            try {
                Bitmap bitmap = decodeSampledBitmap(uri, 512);
                if (bitmap == null) throw new IOException("Cannot decode image");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] bytes = baos.toByteArray();
                if (bytes.length > 200 * 1024) {
                    baos.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    bytes = baos.toByteArray();
                }
                final String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                final Bitmap thumb = bitmap;
                final int kb = bytes.length / 1024;
                context.handler.post(() -> {
                    cb.onImage(thumb, b64);
                    Extensions.showMessage(context, "Image attached (" + kb + " KB)");
                });
            } catch (Exception e) {
                context.handler.post(() -> Extensions.showMessage(context, "Image failed: " + e.getMessage()));
            }
        }).start();
    }
    private void loadOverlayImage(Uri uri) {
        decodePickedImage(uri, (thumb, b64) -> {
            overlayImageBase64 = b64;
            if (overlayImagePreviewView != null) {
                overlayImagePreviewView.setImageBitmap(thumb);
                overlayImagePreviewView.setVisibility(View.VISIBLE);
            }
            if (activeOverlayForm != null) updatePreviewImage(activeOverlayForm);
        });
    }

    private Bitmap decodeSampledBitmap(Uri uri, int maxSize) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
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
                    String[] items = new String[]{"Sign APK", "Optimize APK", "Decompile (REAndroid APKEditor)", "Refactor obfuscated resource names", "Protect (REAndroid APKEditor)", "Clone APK", context.getString(R.string.view_certificate), "Kill signature verification", "Add Toast / Dialog", "Remove all toasts", "Remove signature", "Signature health", "Manifest toggles", "Permissions"};
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
                                                    new MaterialAlertDialogBuilder(context)
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
                    }
                    else if (which1 == 5) {
                        View ll = LayoutInflater.from(context).inflate(R.layout.dialog_clone, null);
                        TextView pkgNameView = ll.findViewById(R.id.package_name_input);
                        String pkgNameFromApk = getPackageNameFromApk(filePath);
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
                    } else if (which1 == 6) showCertificateDialog(file);
                    else if (which1 == 7) killSignatureVerification(file, fileName);
                    else if (which1 == 8) showAddToastDialog(file, filePath);
                    else if (which1 == 9) showRemoveAllToastsDialog(file);
                    else if (which1 == 10) removeSignature(file);
                    else if (which1 == 11) showSignatureHealthDialog(file);
                    else if (which1 == 12) manifestEditor.showManifestTogglesDialog(file);
                    else if (which1 == 13) manifestEditor.showPermissionsDialog(file);
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

    private void killSignatureVerification(File file, String fileName) {
        View layout = LayoutInflater.from(context).inflate(R.layout.dialog_kill_signature, null);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        CompoundButton autosign = layout.findViewById(R.id.autosign);
        autosign.setText(R.string.auto_sign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());

        String[] killMethods = {"MT", "RePairip"};
        final int[] selectedMethod = {0};
        AutoCompleteTextView methodDropdown = layout.findViewById(R.id.method_dropdown);
        methodDropdown.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, killMethods));
        methodDropdown.setText(killMethods[0], false);
        methodDropdown.setOnItemClickListener((parent, view, position, id) -> selectedMethod[0] = position);

        layout.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());

        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle("Kill signature verification")
                .setMessage("Note: This feature is for security testing purposes only and is strictly forbidden to use it for any illegal activity")
                .setView(layout)
                .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                .setPositiveButton("Kill", (dialog2, which3) -> {
                    SignWrapper[] wrapper = new SignWrapper[1];
                    final Runnable doKill;
                    if (selectedMethod[0] == 1) {
                        doKill = () -> {
                            ProgressManager pm = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    File result = PairipRemoverUtil.removePairip(context, file);
                                    if (sign[0]) wrapper[0].signApk(result);
                                    pm.dismiss();
                                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        };
                    } else {
                        doKill = () -> {
                            ProgressManager pm = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    File result = SignatureKillerUtil.apply(context, file);
                                    if (sign[0]) wrapper[0].signApk(result);
                                    pm.dismiss();
                                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        };
                    }
                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doKill.run();
                    }); else doKill.run();
                    }).show());
    }

    private void showAddToastDialog(File file, String filePath) {
        List<ActivityInfo> activities = ToastInjectorUtil.getActivities(context, filePath);
        if (activities.isEmpty()) {
            Extensions.showMessage(context, "No activities found in this APK");
            return;
        }
        PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(filePath, 0);
        String packageName = packageInfo != null ? packageInfo.packageName : "";
        String mainActivity = ToastInjectorUtil.findMainLauncherActivity(context, filePath, packageName);

        CharSequence[] displayItems = new CharSequence[activities.size()];
        boolean[] checked = new boolean[activities.size()];
        final Set<Integer> selectedIndices = new HashSet<>();
        if(!TextUtils.isEmpty(mainActivity)) for (int i = 0; i < activities.size(); i++) {
            String name = activities.get(i).name;
            boolean isMain = name.equals(mainActivity);
            displayItems[i] = isMain ? name + " (Main)" : name;
            checked[i] = isMain;
            if (isMain) selectedIndices.add(i);
        }

        dialogUtil.getDialogBuilder()
                .setTitle("Select Activity")
                .setMultiChoiceItems(displayItems, checked, (dialog, which, isChecked) -> {
                    if (isChecked) selectedIndices.add(which);
                    else selectedIndices.remove(which);
                })
                .setPositiveButton("Next", (dialog, which) -> {
                    if (selectedIndices.isEmpty()) return;
                    List<String> selectedActivities = new ArrayList<>();
                    for (int idx : selectedIndices) selectedActivities.add(activities.get(idx).name);
                    showToastMessageDialog(file, selectedActivities);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class OverlayForm {
        MaterialAutoCompleteTextView modeTv;
        MaterialAutoCompleteTextView durationTv;
        MaterialAutoCompleteTextView gravityTv;
        MaterialAutoCompleteTextView posAction;
        MaterialAutoCompleteTextView negAction;
        MaterialAutoCompleteTextView neuAction;
        MaterialAutoCompleteTextView profileTv;
        TextInputEditText messageInput;
        TextInputEditText xInput;
        TextInputEditText yInput;
        TextInputEditText titleInput;
        TextInputEditText msgInput;
        TextInputEditText posInput;
        TextInputEditText posUrl;
        TextInputEditText negInput;
        TextInputEditText negUrl;
        TextInputEditText neuInput;
        TextInputEditText neuUrl;
        MaterialSwitch toastHtml;
        MaterialSwitch toastB64;
        MaterialSwitch cancelSwitch;
        MaterialSwitch dontShowSwitch;
        MaterialSwitch dlgHtml;
        MaterialSwitch dlgB64;
        LinearLayout toastSection;
        LinearLayout dialogSection;
        LinearLayout posBox;
        LinearLayout negBox;
        LinearLayout neuBox;
        TextView toastPreview;
        TextView dlgPreviewTitle;
        TextView dlgPreviewMsg;
        ImageView dlgPreviewImage;
        LinearLayout dlgPreviewBox;
        MaterialSwitch bgSwitch;
        MaterialSwitch animSwitch;
        MaterialButton bg1Btn;
        MaterialButton bg2Btn;
        MaterialButton borderColorBtn;
        LinearLayout animColorsRow;
        final ArrayList<Integer> animColors = new ArrayList<>(
                Arrays.asList(-16776961, -65536));
        Runnable renderAnimChips;
        MaterialButton titleColorBtn;
        TextInputEditText titleColorHex;
        MaterialSwitch rainbowSwitch;
        View animBox;
        MaterialButton dlgFontBtn;
        MaterialButton advFontBtn;
        MaterialButton advFontApplyAllBtn;
        MaterialSwitch advSnapSwitch;
        String dlgFont = "";
        String dlgFontPath = "";
        String advFont = "";
        String advFontPath = "";
        MaterialAutoCompleteTextView orientTv;
        TextInputEditText radiusInput;
        TextInputEditText borderWidthInput;
        TextInputEditText speedInput;
        int bgC1 = -1;
        int bgC2;
        boolean bgC2set;
        int orient;
        float radiusDp = 16f;
        float borderDp;
        int borderC = -1;
        boolean animOn;
        int animMs = 500;
        int titleC;
    }

    private OverlayForm fillForm(View view) {
        OverlayForm f = new OverlayForm();
        f.modeTv = view.findViewById(R.id.overlayModeTv);
        f.durationTv = view.findViewById(R.id.overlayToastDuration);
        f.gravityTv = view.findViewById(R.id.overlayToastGravity);
        f.posAction = view.findViewById(R.id.overlayDlgPosAction);
        f.negAction = view.findViewById(R.id.overlayDlgNegAction);
        f.neuAction = view.findViewById(R.id.overlayDlgNeuAction);
        f.profileTv = view.findViewById(R.id.overlayProfileTv);
        f.messageInput = view.findViewById(R.id.overlayToastMessage);
        f.xInput = view.findViewById(R.id.overlayToastX);
        f.yInput = view.findViewById(R.id.overlayToastY);
        f.titleInput = view.findViewById(R.id.overlayDlgTitle);
        f.msgInput = view.findViewById(R.id.overlayDlgMessage);
        f.posInput = view.findViewById(R.id.overlayDlgPos);
        f.posUrl = view.findViewById(R.id.overlayDlgPosUrl);
        f.negInput = view.findViewById(R.id.overlayDlgNeg);
        f.negUrl = view.findViewById(R.id.overlayDlgNegUrl);
        f.neuInput = view.findViewById(R.id.overlayDlgNeu);
        f.neuUrl = view.findViewById(R.id.overlayDlgNeuUrl);
        f.toastHtml = view.findViewById(R.id.overlayToastHtml);
        f.toastB64 = view.findViewById(R.id.overlayToastB64);
        f.cancelSwitch = view.findViewById(R.id.overlayDlgCancelable);
        f.dontShowSwitch = view.findViewById(R.id.overlayDlgDontShow);
        f.dlgHtml = view.findViewById(R.id.overlayDlgHtml);
        f.dlgB64 = view.findViewById(R.id.overlayDlgB64);
        f.toastSection = view.findViewById(R.id.overlayToastSection);
        f.dialogSection = view.findViewById(R.id.overlayDialogSection);
        f.posBox = view.findViewById(R.id.overlayPosBox);
        f.negBox = view.findViewById(R.id.overlayNegBox);
        f.neuBox = view.findViewById(R.id.overlayNeuBox);
        f.toastPreview = view.findViewById(R.id.overlayToastPreview);
        f.dlgPreviewTitle = view.findViewById(R.id.overlayDlgPreviewTitle);
        f.dlgPreviewMsg = view.findViewById(R.id.overlayDlgPreviewMsg);
        f.dlgPreviewImage = view.findViewById(R.id.overlayDlgPreviewImage);
        f.dlgPreviewBox = view.findViewById(R.id.overlayDlgPreviewBox);
        f.bgSwitch = view.findViewById(R.id.overlayBgSwitch);
        f.animSwitch = view.findViewById(R.id.overlayAnimSwitch);
        f.bg1Btn = view.findViewById(R.id.overlayBg1Btn);
        f.bg2Btn = view.findViewById(R.id.overlayBg2Btn);
        f.borderColorBtn = view.findViewById(R.id.overlayBorderColorBtn);
        f.animColorsRow = view.findViewById(R.id.overlayAnimColorsRow);
        f.titleColorBtn = view.findViewById(R.id.overlayTitleColorBtn);
        f.titleColorHex = view.findViewById(R.id.overlayTitleColorHex);
        f.rainbowSwitch = view.findViewById(R.id.overlayRainbowSwitch);
        f.animBox = view.findViewById(R.id.overlayAnimBox);
        f.dlgFontBtn = view.findViewById(R.id.overlayDlgFontBtn);
        f.advFontBtn = view.findViewById(R.id.advFontBtn);
        f.advFontApplyAllBtn = view.findViewById(R.id.advFontApplyAllBtn);
        f.advSnapSwitch = view.findViewById(R.id.advSnapSwitch);
        f.orientTv = view.findViewById(R.id.overlayOrientTv);
        f.radiusInput = view.findViewById(R.id.overlayRadiusInput);
        f.borderWidthInput = view.findViewById(R.id.overlayBorderWidthInput);
        f.speedInput = view.findViewById(R.id.overlaySpeedInput);
        return f;
    }

    private static String textOf(EditText e) {
        return e != null && e.getText() != null ? e.getText().toString() : "";
    }

    private OverlayInjectorUtil.ToastOptions collectToast(OverlayForm f) {
        OverlayInjectorUtil.ToastOptions toast = new OverlayInjectorUtil.ToastOptions();
        toast.message = textOf(f.messageInput);
        toast.longDuration = f.durationTv.getText() == null || f.durationTv.getText().toString().equals("Long");
        String gravity = f.gravityTv.getText() == null ? "Default" : f.gravityTv.getText().toString();
        if (gravity.equals("Bottom")) toast.gravity = 80;
        else if (gravity.equals("Center")) toast.gravity = 17;
        else if (gravity.equals("Top")) toast.gravity = 48;
        toast.xOffset = clampOffset(textOf(f.xInput));
        toast.yOffset = clampOffset(textOf(f.yInput));
        toast.html = f.toastHtml.isChecked();
        toast.base64 = f.toastB64.isChecked();
        if (toast.message.isEmpty()) {
            Extensions.showMessage(context, "Enter a message");
            return null;
        }
        return toast;
    }

    private OverlayInjectorUtil.DialogOptions collectDialog(OverlayForm f) {
        OverlayInjectorUtil.DialogOptions dlg = new OverlayInjectorUtil.DialogOptions();
        dlg.title = textOf(f.titleInput);
        dlg.message = textOf(f.msgInput);
        dlg.positive = f.posBox.getVisibility() == View.VISIBLE ? textOf(f.posInput).trim() : "";
        dlg.negative = f.negBox.getVisibility() == View.VISIBLE ? textOf(f.negInput).trim() : "";
        dlg.neutral = f.neuBox.getVisibility() == View.VISIBLE ? textOf(f.neuInput).trim() : "";
        boolean[] badUrl = new boolean[1];
        dlg.positiveUrl = f.posBox.getVisibility() == View.VISIBLE ? actionUrl(f.posAction, f.posUrl, badUrl) : null;
        dlg.negativeUrl = f.negBox.getVisibility() == View.VISIBLE ? actionUrl(f.negAction, f.negUrl, badUrl) : null;
        dlg.neutralUrl = f.neuBox.getVisibility() == View.VISIBLE ? actionUrl(f.neuAction, f.neuUrl, badUrl) : null;
        if (badUrl[0]) return null;
        dlg.cancelable = f.cancelSwitch.isChecked();
        dlg.dontShowAgain = f.dontShowSwitch.isChecked();
        dlg.imageBase64 = overlayImageBase64;
        dlg.html = f.dlgHtml.isChecked();
        dlg.base64 = f.dlgB64.isChecked();
        collectStyle(f, dlg);
        dlg.titleColor = f.titleC;
        dlg.dlgFont = f.dlgFont;
        dlg.dlgFontPath = f.dlgFontPath;
        if (dlg.message.isEmpty() && dlg.title.isEmpty()) {
            Extensions.showMessage(context, "Enter a title or message");
            return null;
        }
        if (dlg.positive.isEmpty() && dlg.negative.isEmpty() && dlg.neutral.isEmpty()) {
            dlg.positive = "OK";
        }
        return dlg;
    }

    private OverlayInjectorUtil.DialogOptions collectAdvanced(OverlayForm f,
                                                              List<OverlayInjectorUtil.AdvWidget> widgets) {
        if (widgets == null || widgets.isEmpty()) {
            Extensions.showMessage(context, "Add at least one widget");
            return null;
        }
        OverlayInjectorUtil.DialogOptions dlg = new OverlayInjectorUtil.DialogOptions();
        dlg.advanced = true;
        dlg.widgets = new ArrayList<>();
        for (OverlayInjectorUtil.AdvWidget w : widgets) {
            OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(w);
            if (copy != null) dlg.widgets.add(copy);
        }
        if (dlg.widgets.isEmpty()) {
            Extensions.showMessage(context, "Add at least one widget");
            return null;
        }
        collectStyle(f, dlg);
        dlg.dlgFont = f.advFont;
        dlg.dlgFontPath = f.advFontPath;
        dlg.cancelable = f.cancelSwitch.isChecked();
        boolean hasButton = false;
        for (OverlayInjectorUtil.AdvWidget w : dlg.widgets) {
            if (w != null && "button".equals(w.kind)) {
                hasButton = true;
                break;
            }
        }
        if (!hasButton) dlg.cancelable = true;
        dlg.dontShowAgain = f.dontShowSwitch.isChecked();
        dlg.html = f.dlgHtml.isChecked();
        dlg.base64 = f.dlgB64.isChecked();
        return dlg;
    }

    private void collectStyle(OverlayForm f, OverlayInjectorUtil.DialogOptions dlg) {
        dlg.bgEnabled = f.bgSwitch.isChecked();
        dlg.bgColor1 = f.bgC1;
        dlg.bgColor2 = f.bgC2;
        dlg.bgGradient = f.bgC2set;
        String orientText = f.orientTv.getText() == null ? "" : f.orientTv.getText().toString();
        if (orientText.startsWith("Left")) dlg.bgOrientation = 1;
        else if (orientText.startsWith("Top-left")) dlg.bgOrientation = 2;
        else if (orientText.startsWith("Bottom")) dlg.bgOrientation = 3;
        else dlg.bgOrientation = 0;
        dlg.cornerRadiusDp = parseFloatSafe(textOf(f.radiusInput), 16f);
        dlg.borderWidthDp = parseFloatSafe(textOf(f.borderWidthInput), 0f);
        dlg.borderColor = f.borderC;
        dlg.animBorder = f.animSwitch.isChecked();
        while (f.animColors.size() < 2) f.animColors.add(f.animColors.isEmpty() ? -16776961 : -65536);
        dlg.animColorA = f.animColors.get(0);
        dlg.animColorB = f.animColors.get(1);
        if (f.animColors.size() > 2) {
            dlg.animExtraColors = new ArrayList<>(f.animColors.subList(2, f.animColors.size()));
        } else {
            dlg.animExtraColors = null;
        }
        dlg.rainbowAnim = f.rainbowSwitch != null && f.rainbowSwitch.isChecked();
        dlg.animSpeedMs = Math.max(100, parseIntSafe(textOf(f.speedInput), 2500));
    }

    private void applyAdvanced(OverlayForm f, List<OverlayInjectorUtil.AdvWidget> widgets,
                               OverlayInjectorUtil.AdvWidget[] selected,
                               OverlayInjectorUtil.DialogOptions o) {
        applyDialog(f, o);
        widgets.clear();
        if (o.widgets != null) {
            for (OverlayInjectorUtil.AdvWidget w : o.widgets) {
                OverlayInjectorUtil.AdvWidget copy = OverlayInjectorUtil.copyWidget(w);
                if (copy != null) widgets.add(copy);
            }
        }
        f.advFont = o.dlgFont == null ? "" : o.dlgFont;
        f.advFontPath = o.dlgFontPath == null ? "" : o.dlgFontPath;
        refreshAdvFontLabels(f);
        selected[0] = null;
    }

    private static void setText(EditText e, String s) {
        if (e != null) e.setText(s == null ? "" : s);
    }

    private void applyToast(OverlayForm f, OverlayInjectorUtil.ToastOptions o) {
        setText(f.messageInput, o.message);
        f.durationTv.setText(o.longDuration ? "Long" : "Short", false);
        f.gravityTv.setText(o.gravity == 80 ? "Bottom" : o.gravity == 17 ? "Center" : o.gravity == 48 ? "Top" : "Default", false);
        setText(f.xInput, String.valueOf(o.xOffset));
        setText(f.yInput, String.valueOf(o.yOffset));
        f.toastHtml.setChecked(o.html);
        f.toastB64.setChecked(o.base64);
    }

    private void applyDialog(OverlayForm f, OverlayInjectorUtil.DialogOptions o) {
        setText(f.titleInput, o.title);
        setText(f.msgInput, o.message);
        setupButtonBox(f.posBox, R.id.overlayPosAddBtn, f.posInput, o.positive, f.posAction, f.posUrl, o.positiveUrl);
        setupButtonBox(f.negBox, R.id.overlayNegAddBtn, f.negInput, o.negative, f.negAction, f.negUrl, o.negativeUrl);
        setupButtonBox(f.neuBox, R.id.overlayNeuAddBtn, f.neuInput, o.neutral, f.neuAction, f.neuUrl, o.neutralUrl);
        f.cancelSwitch.setChecked(o.cancelable);
        f.dontShowSwitch.setChecked(o.dontShowAgain);
        f.dlgHtml.setChecked(o.html);
        f.dlgB64.setChecked(o.base64);
        f.bgSwitch.setChecked(o.bgEnabled);
        f.bgC1 = o.bgColor1;
        f.bgC2 = o.bgColor2;
        f.bgC2set = o.bgGradient;
        String[] orients = {"Top to bottom", "Left to right", "Top-left to bottom-right", "Bottom-left to top-right"};
        f.orientTv.setText(orients[Math.max(0, Math.min(3, o.bgOrientation))], false);
        setText(f.radiusInput, String.valueOf(o.cornerRadiusDp));
        setText(f.borderWidthInput, String.valueOf(o.borderWidthDp));
        f.borderC = o.borderColor;
        f.animSwitch.setChecked(o.animBorder);
        f.animOn = o.animBorder;
        if (f.rainbowSwitch != null) f.rainbowSwitch.setChecked(o.rainbowAnim);
        if (f.animBox != null) f.animBox.setVisibility(o.animBorder ? View.VISIBLE : View.GONE);
        f.animColors.clear();
        f.animColors.add(o.animColorA);
        f.animColors.add(o.animColorB);
        if (o.animExtraColors != null) f.animColors.addAll(o.animExtraColors);
        if (f.renderAnimChips != null) f.renderAnimChips.run();
        setText(f.speedInput, String.valueOf(o.animSpeedMs));
        f.animMs = o.animSpeedMs;
        f.titleC = o.titleColor;
        if (f.titleColorHex != null) {
            f.titleColorHex.setText(o.titleColor == 0 ? "" : String.format("#%08X", o.titleColor));
        }
        f.dlgFont = o.dlgFont == null ? "" : o.dlgFont;
        f.dlgFontPath = o.dlgFontPath == null ? "" : o.dlgFontPath;
        f.radiusDp = o.cornerRadiusDp;
        f.borderDp = o.borderWidthDp;
        f.orient = o.bgOrientation;
        overlayImageBase64 = o.imageBase64;
        refreshStyleLabels(f);
        refreshFontLabels(f);
        updatePreviewImage(f);
        syncUrlBoxes(f);
    }

    private void setupButtonBox(LinearLayout box, int addBtnId, TextInputEditText textInput,
                                String text, MaterialAutoCompleteTextView actionTv,
                                TextInputEditText urlInput, String url) {
        View root = (View) box.getParent();
        View addBtn = root.findViewById(addBtnId);
        boolean show = text != null && !text.isEmpty();
        box.setVisibility(show ? View.VISIBLE : View.GONE);
        if (addBtn != null) addBtn.setVisibility(show ? View.GONE : View.VISIBLE);
        setText(textInput, text);
        if (actionTv != null) actionTv.setText(url != null && !url.isEmpty() ? "Open URL" : "Dismiss", false);
        setText(urlInput, url);
    }

    private void refreshProfiles(OverlayForm f, boolean isToast) {
        try {
            List<String> names = isToast ? OverlayProfiles.toastNames(context) : OverlayProfiles.dialogNames(context);
            f.profileTv.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, names));
        } catch (Exception ignored) {
        }
    }

    private void attachFormatMenu(EditText target) {
        if (target == null) return;
        target.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, 101, 0, "Bold");
                menu.add(0, 102, 1, "Italic");
                menu.add(0, 103, 2, "Underline");
                menu.add(0, 104, 3, "Color");
                menu.add(0, 105, 4, "Copy");
                menu.add(0, 106, 5, "Cut");
                menu.add(0, 107, 6, "Paste");
                menu.add(0, 108, 7, "Share");
                menu.add(0, 109, 8, "Select all");
                return true;
            }

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int start = target.getSelectionStart();
                int end = target.getSelectionEnd();
                if (start < 0 || end < 0) return false;
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                switch (item.getItemId()) {
                    case 101:
                        wrapRange(target, start, end, "<b>", "</b>");
                        break;
                    case 102:
                        wrapRange(target, start, end, "<i>", "</i>");
                        break;
                    case 103:
                        wrapRange(target, start, end, "<u>", "</u>");
                        break;
                    case 104:
                        mode.finish();
                        showColorWheelDialog(target, start, end);
                        return true;
                    case 105:
                        copyRange(start, end);
                        break;
                    case 106:
                        copyRange(start, end);
                        replaceRange(start, end, "");
                        break;
                    case 107:
                        pasteAt(start, end);
                        break;
                    case 108: {
                        String text = start == end ? textOf(target)
                                : target.getText().toString().substring(start, end);
                        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text);
                        context.startActivity(Intent.createChooser(share, "Share"));
                        break;
                    }
                    case 109:
                        target.selectAll();
                        return true;
                    default:
                        return false;
                }
                mode.finish();
                return true;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            private void copyRange(int start, int end) {
                try {
                    String text = target.getText().toString().substring(start, end);
                    ClipboardManager cm = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("text", text));
                    Extensions.showMessage(context, "Copied");
                } catch (Exception ignored) {
                }
            }

            private void replaceRange(int start, int end, String replacement) {
                try {
                    Editable text = target.getText();
                    if (text != null) text.replace(start, end, replacement);
                } catch (Exception ignored) {
                }
            }

            private void pasteAt(int start, int end) {
                try {
                    ClipboardManager cm = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return;
                    CharSequence paste = cm.getPrimaryClip().getItemAt(0).coerceToText(context);
                    if (paste != null) replaceRange(start, end, paste.toString());
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void wrapRange(EditText target, int start, int end, String open, String close) {
        if (target == null || target.getText() == null) return;
        try {
            Editable text = target.getText();
            int len = text.length();
            start = Math.max(0, Math.min(start, len));
            end = Math.max(0, Math.min(end, len));
            if (start == end) {
                text.insert(start, open + close);
                target.setSelection(start + open.length());
            } else {
                text.insert(end, close);
                text.insert(start, open);
                target.setSelection(end + open.length() + close.length());
            }
        } catch (Exception ignored) {
        }
    }

    private void updatePreviewImage(OverlayForm f) {
        try {
            if (overlayImageBase64 != null && !overlayImageBase64.isEmpty()) {
                byte[] bytes = Base64.decode(overlayImageBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    f.dlgPreviewImage.setImageBitmap(bitmap);
                    f.dlgPreviewImage.setVisibility(View.VISIBLE);
                    if (overlayImagePreviewView != null) {
                        overlayImagePreviewView.setImageBitmap(bitmap);
                        overlayImagePreviewView.setVisibility(View.VISIBLE);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        f.dlgPreviewImage.setVisibility(View.GONE);
    }

    private void setupStyleControls(View view, OverlayForm f, Runnable updatePreview) {
        View styleBox = view.findViewById(R.id.overlayStyleBox);
        view.findViewById(R.id.overlayStyleToggle).setOnClickListener(v -> {
            boolean show = styleBox.getVisibility() != View.VISIBLE;
            styleBox.setVisibility(show ? View.VISIBLE : View.GONE);
        });
        String[] orients = {"Top to bottom", "Left to right", "Top-left to bottom-right", "Bottom-left to top-right"};
        f.orientTv.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, orients));
        f.orientTv.setText(orients[0], false);
        f.bg1Btn.setOnClickListener(v -> showColorWheel(f.bgC1, (argb, hex) -> {
            f.bgC1 = argb;
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.bg2Btn.setOnClickListener(v -> showColorWheel(f.bgC2set ? f.bgC2 : -16776961, (argb, hex) -> {
            f.bgC2 = argb;
            f.bgC2set = true;
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.bg2Btn.setOnLongClickListener(v -> {
            f.bgC2set = false;
            refreshStyleLabels(f);
            updatePreview.run();
            Extensions.showMessage(context, "Gradient cleared (long-press clears)");
            return true;
        });
        f.borderColorBtn.setOnClickListener(v -> showColorWheel(f.borderC, (argb, hex) -> {
            f.borderC = argb;
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.renderAnimChips = () -> {
            if (f.animColorsRow == null) return;
            f.animColorsRow.removeAllViews();
            if (f.animColors.size() < 2) {
                while (f.animColors.size() < 2) f.animColors.add(f.animColors.isEmpty() ? -16776961 : -65536);
            }
            for (int i = 0; i < f.animColors.size(); i++) {
                final int idx = i;
                int color = f.animColors.get(idx);
                MaterialButton chip = new MaterialButton(context);
                chip.setText("");
                chip.setCornerRadius(dp(24));
                try {
                    chip.setBackgroundTintList(ColorStateList.valueOf(color));
                } catch (Exception ignored) {
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                int m = dp(4);
                lp.setMargins(m, 0, m, 0);
                chip.setLayoutParams(lp);
                chip.setOnClickListener(x -> showColorWheel(color, (argb, hex) -> {
                    f.animColors.set(idx, argb);
                    f.renderAnimChips.run();
                    updatePreview.run();
                }));
                chip.setOnLongClickListener(x -> {
                    if (f.animColors.size() > 2) {
                        f.animColors.remove(idx);
                        f.renderAnimChips.run();
                        updatePreview.run();
                    } else {
                        Extensions.showMessage(context, "Need at least 2 colors");
                    }
                    return true;
                });
                f.animColorsRow.addView(chip);
            }
            MaterialButton add = new MaterialButton(context);
            add.setBackgroundResource(R.drawable.add_24px);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
            int am = dp(4);
            alp.setMargins(am, 0, am, 0);
            add.setLayoutParams(alp);
            add.setOnClickListener(x -> showColorWheel(-65536, (argb, hex) -> {
                f.animColors.add(argb);
                f.renderAnimChips.run();
                updatePreview.run();
            }));
            f.animColorsRow.addView(add);
        };
        f.renderAnimChips.run();
        f.titleColorBtn.setOnClickListener(v -> showColorWheel(f.titleC == 0 ? -16777216 : f.titleC, (argb, hex) -> {
            f.titleC = argb;
            if (f.titleColorHex != null) f.titleColorHex.setText(hex);
            refreshStyleLabels(f);
            updatePreview.run();
        }));
        f.titleColorBtn.setOnLongClickListener(v -> {
            f.titleC = 0;
            if (f.titleColorHex != null) f.titleColorHex.setText("");
            refreshStyleLabels(f);
            updatePreview.run();
            return true;
        });
        if (f.titleColorHex != null) {
            f.titleColorHex.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    int parsed = parseHexColor(s.toString(), 0);
                    if (parsed != 0 || s.toString().trim().isEmpty()) {
                        f.titleC = parsed;
                        refreshStyleLabels(f);
                        updatePreview.run();
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
        }
        f.bgSwitch.setOnCheckedChangeListener((b, c) -> updatePreview.run());
        f.animSwitch.setOnCheckedChangeListener((b, c) -> {
            f.animOn = c;
            if (f.animBox != null) f.animBox.setVisibility(c ? View.VISIBLE : View.GONE);
            updatePreview.run();
        });
        if (f.animBox != null) {
            f.animBox.setVisibility(f.animSwitch.isChecked() ? View.VISIBLE : View.GONE);
        }
        TextWatcher styleWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updatePreview.run();
            }

            public void afterTextChanged(Editable s) {
            }
        };
        f.radiusInput.addTextChangedListener(styleWatcher);
        f.borderWidthInput.addTextChangedListener(styleWatcher);
        f.speedInput.addTextChangedListener(styleWatcher);
        refreshStyleLabels(f);
    }

    private static String shortHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private void refreshStyleLabels(OverlayForm f) {
        f.bg1Btn.setText("BG: " + shortHex(f.bgC1));
        f.bg2Btn.setText(f.bgC2set ? "Gradient: " + shortHex(f.bgC2) : "Gradient: none");
        f.borderColorBtn.setText("Border: " + shortHex(f.borderC));
        if (f.titleColorBtn != null) {
            f.titleColorBtn.setText("");
            try {
                if (f.titleC == 0) {
                    f.titleColorBtn.setBackgroundTintList(null);
                } else {
                    f.titleColorBtn.setBackgroundTintList(
                            ColorStateList.valueOf(f.titleC));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static int parseHexColor(String text, int def) {
        try {
            String hex = text == null ? "" : text.trim();
            if (hex.isEmpty()) return 0;
            if (!hex.startsWith("#")) hex = "#" + hex;
            if (!hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return def;
            return (int) Long.parseLong(hex.substring(1), 16)
                    | (hex.length() == 7 ? 0xFF000000 : 0);
        } catch (Exception e) {
            return def;
        }
    }

    private static float parseFloatSafe(String text, float def) {
        try {
            return Float.parseFloat(text.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseIntSafe(String text, int def) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void updateStylePreview(OverlayForm f) {
        try {
            float density = context.getResources().getDisplayMetrics().density;
            f.radiusDp = parseFloatSafe(textOf(f.radiusInput), 16f);
            f.borderDp = parseFloatSafe(textOf(f.borderWidthInput), 0f);
            f.animMs = Math.max(100, parseIntSafe(textOf(f.speedInput), 2500));
            String orientText = f.orientTv.getText() == null ? "" : f.orientTv.getText().toString();
            if (orientText.startsWith("Left")) f.orient = 1;
            else if (orientText.startsWith("Top-left")) f.orient = 2;
            else if (orientText.startsWith("Bottom")) f.orient = 3;
            else f.orient = 0;
            f.animOn = f.animSwitch.isChecked();
            int onSurface = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurface, 0xFF000000);
            if (f.titleC != 0) f.dlgPreviewTitle.setTextColor(f.titleC);
            else f.dlgPreviewTitle.setTextColor(onSurface);
            f.dlgPreviewMsg.setTextColor(onSurface);
            boolean styled = f.bgSwitch.isChecked() || f.borderDp > 0;
            if (!styled) {
                f.dlgPreviewBox.setBackground(null);
                return;
            }
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(f.radiusDp * density);
            if (f.bgSwitch.isChecked()) {
                if (f.bgC2set) {
                    gd.setColors(new int[]{f.bgC1, f.bgC2});
                    GradientDrawable.Orientation[] values = {
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            GradientDrawable.Orientation.TL_BR,
                            GradientDrawable.Orientation.BL_TR};
                    gd.setOrientation(values[Math.max(0, Math.min(3, f.orient))]);
                } else {
                    gd.setColor(f.bgC1);
                }
            }
            if (f.borderDp > 0) {
                gd.setStroke((int) (f.borderDp * density + 0.5f), f.borderC);
            }
            f.dlgPreviewBox.setBackground(gd);
        } catch (Exception ignored) {
        }
    }

    private static CharSequence displayText(String raw, boolean richText) {
        if (raw == null) raw = "";
        if (!richText) return raw;
        try {
            return HtmlCompat.fromHtml(raw,
                    HtmlCompat.FROM_HTML_MODE_LEGACY);
        } catch (Exception e) {
            return raw;
        }
    }

    private GradientDrawable buildButtonDrawable(
            OverlayInjectorUtil.AdvWidget w) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable gd =
                new GradientDrawable();
        if (w.btnBg != 0 && w.btnBg2 != 0) {
            gd.setColors(new int[]{w.btnBg, w.btnBg2});
        } else if (w.btnBg != 0) {
            gd.setColor(w.btnBg);
        } else {
            return gd;
        }
        if (w.btnCornerRadiusDp > 0) gd.setCornerRadius(w.btnCornerRadiusDp * density);
        float borderDp = w.btnBorderWidthDp > 0 ? w.btnBorderWidthDp : (w.btnAnim ? 2f : 0f);
        if (borderDp > 0) {
            int bc = w.btnBorderColor != -1 ? w.btnBorderColor : w.btnBg;
            gd.setStroke(Math.max(1, (int) (borderDp * density + 0.5f)), bc);
        }
        return gd;
    }

    private static int[] snapWidget(OverlayInjectorUtil.AdvWidget self, View dragged,
                                    int lPx, int tPx, FrameLayout canvas, float density) {
        int thresh = (int) (8 * density + 0.5f);

        int nl = lPx;
        int nt = tPx;
        int vw = dragged.getWidth() > 0 ? dragged.getWidth() : (int) (80 * density);
        int vh = dragged.getHeight() > 0 ? dragged.getHeight() : (int) (48 * density);
        int[] xs = new int[]{0, Math.max(0, (canvas.getWidth() - vw) / 2)};
        int[] ys = new int[]{0};
        if (canvas.getChildCount() > 0) {
            int[] extra = new int[canvas.getChildCount() * 3 + 2];
            System.arraycopy(xs, 0, extra, 0, xs.length);
            int n = xs.length;
            for (int i = 0; i < canvas.getChildCount(); i++) {
                View sib = canvas.getChildAt(i);
                if (sib == dragged) continue;
                extra[n++] = sib.getLeft();
                extra[n++] = sib.getRight();
                extra[n++] = sib.getLeft() + sib.getWidth() / 2;
            }
            xs = Arrays.copyOf(extra, n);
            int[] yextra = new int[canvas.getChildCount() * 3 + 1];
            yextra[0] = 0;
            int m = 1;
            for (int i = 0; i < canvas.getChildCount(); i++) {
                View sib = canvas.getChildAt(i);
                if (sib == dragged) continue;
                yextra[m++] = sib.getTop();
                yextra[m++] = sib.getBottom();
                yextra[m++] = sib.getTop() + sib.getHeight() / 2;
            }
            ys = Arrays.copyOf(yextra, m);
        }
        int[] xEdges = new int[]{nl, nl + vw, nl + vw / 2};
        int bestDx = 0;
        boolean foundX = false;
        for (int e = 0; e < xEdges.length; e++) {
            for (int gx : xs) {
                int d = gx - xEdges[e];
                if (Math.abs(d) <= thresh && (!foundX || Math.abs(d) < Math.abs(bestDx))) {
                    bestDx = d;
                    foundX = true;
                }
            }
        }
        if (foundX) nl += bestDx;
        int[] yEdges = new int[]{nt, nt + vh, nt + vh / 2};
        int bestDy = 0;
        boolean foundY = false;
        for (int yEdge : yEdges) {
            for (int gy : ys) {
                int d = gy - yEdge;
                if (Math.abs(d) <= thresh && (!foundY || Math.abs(d) < Math.abs(bestDy))) {
                    bestDy = d;
                    foundY = true;
                }
            }
        }
        if (foundY) nt += bestDy;
        return new int[]{Math.max(0, nl), Math.max(0, nt)};
    }

    private static String fontLabel(String family) {
        if (family == null || family.isEmpty()) return "Font: Default";
        if ("serif".equals(family)) return "Font: Serif";
        if ("monospace".equals(family)) return "Font: Mono";
        if ("sans-serif".equals(family)) return "Font: Sans";
        if ("sans-serif-light".equals(family)) return "Font: Light";
        if ("sans-serif-medium".equals(family)) return "Font: Medium";
        if ("sans-serif-black".equals(family)) return "Font: Black";
        if ("sans-serif-condensed".equals(family)) return "Font: Condensed";
        return "Font: Default";
    }

    private static final String[] FONT_LABELS = {"Default", "Sans", "Serif", "Mono", "Light", "Medium", "Black", "Condensed"};
    private static final String[] FONT_VALUES = {"", "sans-serif", "serif", "monospace", "sans-serif-light",
            "sans-serif-medium", "sans-serif-black", "sans-serif-condensed"};

    private interface FontPicked {
        void onFont(String path);
    }

    private interface FontApplier {
        void apply(String font, String fontPath);
    }

    private void pickFontFile(FontPicked cb) {
        try {
            FilePickerDialog.Properties props = new FilePickerDialog.Properties();
            props.selection_mode = FilePickerDialog.SINGLE_MODE;
            props.selection_type = FilePickerDialog.FILE_SELECT;
            props.extensions = new String[]{"ttf", "otf"};
            props.preferenceKey = "overlay_font";
            FilePickerDialog dlg = new FilePickerDialog(context, props);
            dlg.setTitle("Choose font (ttf/otf)");
            dlg.setDialogSelectionListener(files -> {
                if (files == null || files.length == 0 || files[0] == null) return;
                onFontFilePicked(new File(files[0]), cb);
            });
            dlg.show();
        } catch (Exception e) {
            Extensions.showMessage(context, "Font picker unavailable");
        }
    }

    private void onFontFilePicked(File src, FontPicked cb) {
        new Thread(() -> {
            try {
                if (src == null || !src.isFile()) throw new IOException("Not a file");
                String lower = src.getName().toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
                    context.handler.post(() -> Extensions.showMessage(context, "Please pick a .ttf or .otf file"));
                    return;
                }
                File dir = context.getExternalFilesDir("Fonts");
                if (dir == null) dir = context.getFilesDir();
                if (!dir.exists()) dir.mkdirs();
                File dst = new File(dir, "mpfont_" + System.currentTimeMillis()
                        + (lower.endsWith(".otf") ? ".otf" : ".ttf"));
                try (InputStream is = new FileInputStream(src);
                     FileOutputStream os = new FileOutputStream(dst)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
                }
                if (!dst.exists() || dst.length() == 0) {
                    context.handler.post(() -> Extensions.showMessage(context, "Font copy failed"));
                    return;
                }
                String path = dst.getAbsolutePath();
                context.handler.post(() -> {
                    if (cb != null) cb.onFont(path);
                    Extensions.showMessage(context, "Font loaded: " + dst.getName());
                });
            } catch (Exception e) {
                context.handler.post(() -> Extensions.showMessage(context, "Font failed: " + e.getMessage()));
            }
        }).start();
    }

    private static String baseName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        String n = i >= 0 ? path.substring(i + 1) : path;
        return n.length() > 24 ? "…" + n.substring(n.length() - 23) : n;
    }

    private static Typeface loadTypeface(String font, String fontPath) {
        try {
            if (fontPath != null && !fontPath.isEmpty()) {
                Typeface tf = Typeface.createFromFile(fontPath);
                if (tf != null) return tf;
            }
            if (font != null && !font.isEmpty()) {
                return Typeface.create(font, Typeface.NORMAL);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void refreshFontLabels(OverlayForm f) {
        if (f == null || f.dlgFontBtn == null) return;
        if (f.dlgFontPath != null && !f.dlgFontPath.isEmpty()) {
            f.dlgFontBtn.setText("Font: " + baseName(f.dlgFontPath));
        } else {
            f.dlgFontBtn.setText(fontLabel(f.dlgFont));
        }
    }

    private void refreshAdvFontLabels(OverlayForm f) {
        if (f == null || f.advFontBtn == null) return;
        if (f.advFontPath != null && !f.advFontPath.isEmpty()) {
            f.advFontBtn.setText("Dialog font: " + baseName(f.advFontPath));
        } else {
            String label = fontLabel(f.advFont).replace("Font:", "Dialog font:");
            f.advFontBtn.setText(label);
        }
    }

    private static String widgetFontLabel(OverlayInjectorUtil.AdvWidget w) {
        if (w == null) return "Font: Default";
        if (w.fontPath != null && !w.fontPath.isEmpty()) return "Font: " + baseName(w.fontPath);
        if ((w.font == null || w.font.isEmpty())) return "Font: Dialog";
        return fontLabel(w.font);
    }

    private static String effectivePreviewFont(OverlayInjectorUtil.AdvWidget w, OverlayForm f) {
        if (w != null && w.font != null && !w.font.isEmpty()) return w.font;
        if (f != null && f.advFont != null && !f.advFont.isEmpty()) return f.advFont;
        return "";
    }

    private static String effectivePreviewFontPath(OverlayInjectorUtil.AdvWidget w, OverlayForm f) {
        if (w != null && w.fontPath != null && !w.fontPath.isEmpty()) return w.fontPath;
        if (f != null && f.advFontPath != null && !f.advFontPath.isEmpty()) return f.advFontPath;
        return "";
    }

    private static void applyWidgetTypeface(TextView tv, OverlayInjectorUtil.AdvWidget w, OverlayForm f) {
        if (tv == null || w == null) return;
        try {
            Typeface tf = loadTypeface(
                    effectivePreviewFont(w, f), effectivePreviewFontPath(w, f));
            if (tf != null) tv.setTypeface(tf, w.fontStyle);
            else if (w.fontStyle != 0) tv.setTypeface(null, w.fontStyle);
        } catch (Exception ignored) {
        }
    }

    private void showFontPickerDialog(FontApplier applier, String currentFont, String currentFontPath,
                                      MaterialButton labelBtn) {
        String[] labels = new String[FONT_LABELS.length + 2];
        System.arraycopy(FONT_LABELS, 0, labels, 0, FONT_LABELS.length);
        labels[FONT_LABELS.length] = "Choose font file (ttf/otf)…";
        labels[FONT_LABELS.length + 1] = "Clear";
        int checked = -1;
        if (currentFontPath == null || currentFontPath.isEmpty()) {
            for (int i = 0; i < FONT_VALUES.length; i++) {
                if (FONT_VALUES[i].equals(currentFont)) {
                    checked = i;
                    break;
                }
            }
        }
        dialogUtil.getDialogBuilder()
                .setTitle("Font")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    if (which < FONT_LABELS.length) {
                        applier.apply(FONT_VALUES[which], "");
                        if (labelBtn != null) labelBtn.setText(fontLabel(FONT_VALUES[which]));
                    } else if (which == FONT_LABELS.length) {
                        pickFontFile(path -> {
                            applier.apply("", path);
                            if (labelBtn != null) labelBtn.setText("Font: " + baseName(path));
                        });
                    } else {
                        applier.apply("", "");
                        if (labelBtn != null) labelBtn.setText("Font: Default");
                    }
                }).show();
    }

    private void showFontPicker(OverlayInjectorUtil.AdvWidget w, MaterialButton fontBtn) {
        showFontPickerDialog((font, path) -> {
            w.font = font;
            w.fontPath = path;
            renderAdvSafe();
        }, w.font, w.fontPath, fontBtn);
    }

    private void showToastMessageDialog(File file, List<String> selectedActivities) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        overlayImageBase64 = null;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_overlay_editor, null);

        MaterialAutoCompleteTextView modeTv = view.findViewById(R.id.overlayModeTv);
        String[] modes = {"Toast", "Dialog", "Advanced"};
        modeTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, modes));
        modeTv.setText(modes[0], false);

        LinearLayout advancedSection = view.findViewById(R.id.overlayAdvancedSection);
        FrameLayout advCanvas = view.findViewById(R.id.overlayCanvas);
        LinearLayout advProps = view.findViewById(R.id.overlayPropsBox);
        final List<OverlayInjectorUtil.AdvWidget> advWidgets = new ArrayList<>();
        final OverlayInjectorUtil.AdvWidget[] advSelected = new OverlayInjectorUtil.AdvWidget[1];
        final float advDensity = context.getResources().getDisplayMetrics().density;
        final Runnable[] renderAdv = new Runnable[1];
        final Runnable[] showAdvProps = new Runnable[1];
        final OverlayForm form = fillForm(view);

        showAdvProps[0] = () -> {
            advProps.removeAllViews();
            OverlayInjectorUtil.AdvWidget w = advSelected[0];
            if (w == null) {
                TextView hint = new TextView(context);
                hint.setText("Tap a widget to edit it");
                hint.setTextSize(13);
                advProps.addView(hint);
                return;
            }
            TextView header = new TextView(context);
            header.setText("Selected: " + w.kind);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextSize(15);
            advProps.addView(header);
            LinearLayout styleRow2 = new LinearLayout(context);
            styleRow2.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton copyBtn = new MaterialButton(context);
            copyBtn.setText("Copy style");
            copyBtn.setOnClickListener(x -> {
                styleClipboard = OverlayInjectorUtil.copyWidget(w);
                Extensions.showMessage(context, "Style copied (" + w.kind + ")");
                showAdvProps[0].run();
            });
            styleRow2.addView(copyBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton pasteBtn = new MaterialButton(context);
            pasteBtn.setText("Paste style");
            boolean canPaste = styleClipboard != null && w.kind.equals(styleClipboard.kind);
            pasteBtn.setEnabled(canPaste);
            pasteBtn.setAlpha(canPaste ? 1f : 0.5f);
            pasteBtn.setOnClickListener(x -> {
                if (styleClipboard == null || !w.kind.equals(styleClipboard.kind)) return;
                pasteStyle(w, styleClipboard);
                renderAdv[0].run();
                showAdvProps[0].run();
                Extensions.showMessage(context, "Style pasted");
            });
            styleRow2.addView(pasteBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            advProps.addView(styleRow2);
            MaterialButton delBtn = new MaterialButton(context);
            delBtn.setText("Delete widget");
            delBtn.setOnClickListener(x -> {
                advWidgets.remove(w);
                advSelected[0] = null;
                renderAdv[0].run();
                showAdvProps[0].run();
            });
            advProps.addView(delBtn);
            if (w.kind.equals("image")) {
                ImageView thumb = new ImageView(context);
                thumb.setAdjustViewBounds(true);
                if (w.imageB64 != null && !w.imageB64.isEmpty()) {
                    try {
                        byte[] bytes = Base64.decode(w.imageB64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bitmap != null) thumb.setImageBitmap(bitmap);
                    } catch (Exception ignored) {
                    }
                }
                advProps.addView(thumb, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
                MaterialButton pickBtn = new MaterialButton(context);
                pickBtn.setText("Choose image");
                pickBtn.setOnClickListener(x -> pickImage((bmp, b64) -> {
                    w.imageB64 = b64;
                    renderAdv[0].run();
                    showAdvProps[0].run();
                }));
                advProps.addView(pickBtn);
                return;
            }
            EditText textInput = new EditText(context);
            textInput.setText(w.text == null ? "" : w.text);
            if (w.kind.equals("button")) textInput.setHint("Button text");
            else textInput.setHint("Text (HTML allowed)");
            advProps.addView(UiFields.wrap(context, textInput, null, 0));
            textInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    w.text = s.toString();
                    renderAdv[0].run();
                }

                public void afterTextChanged(Editable s) {
                }
            });
            EditText sizeInput = new EditText(context);
            sizeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            sizeInput.setText(String.valueOf(w.textSizeSp));
            advProps.addView(UiFields.wrap(context, sizeInput, "Text size sp", 0));
            sizeInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    try {
                        w.textSizeSp = Float.parseFloat(s.toString());
                        renderAdv[0].run();
                    } catch (Exception ignored) {
                    }
                }

                public void afterTextChanged(Editable s) {
                }
            });
            LinearLayout fmtRow = new LinearLayout(context);
            fmtRow.setOrientation(LinearLayout.HORIZONTAL);
            advProps.addView(fmtRow);
            addFormatRow(fmtRow, textInput);
            attachFormatMenu(textInput);
            LinearLayout colorRow = new LinearLayout(context);
            colorRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton fontBtn = new MaterialButton(context);
            fontBtn.setText(widgetFontLabel(w));
            fontBtn.setOnClickListener(x -> showFontPicker(w, fontBtn));
            colorRow.addView(fontBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            advProps.addView(colorRow);
            LinearLayout styleRow = new LinearLayout(context);
            styleRow.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton boldBtn = new MaterialButton(context);
            boldBtn.setText("B");
            final boolean[] isBold = {(w.fontStyle & 1) != 0};
            final boolean[] isItalic = {(w.fontStyle & 2) != 0};
            boldBtn.setAlpha(isBold[0] ? 1f : 0.55f);
            MaterialButton italicBtn = new MaterialButton(context);
            italicBtn.setText("I");
            italicBtn.setAlpha(isItalic[0] ? 1f : 0.55f);
            boldBtn.setOnClickListener(x -> {
                isBold[0] = !isBold[0];
                w.fontStyle = (isBold[0] ? 1 : 0) | (isItalic[0] ? 2 : 0);
                boldBtn.setAlpha(isBold[0] ? 1f : 0.55f);
                renderAdv[0].run();
            });
            italicBtn.setOnClickListener(x -> {
                isItalic[0] = !isItalic[0];
                w.fontStyle = (isBold[0] ? 1 : 0) | (isItalic[0] ? 2 : 0);
                italicBtn.setAlpha(isItalic[0] ? 1f : 0.55f);
                renderAdv[0].run();
            });
            styleRow.addView(boldBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            styleRow.addView(italicBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton inheritBtn = new MaterialButton(context);
            inheritBtn.setText("Dialog font");
            inheritBtn.setOnClickListener(x -> {
                w.font = "";
                w.fontPath = "";
                fontBtn.setText(widgetFontLabel(w));
                renderAdv[0].run();
                Extensions.showMessage(context, "Using dialog font");
            });
            styleRow.addView(inheritBtn, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
            advProps.addView(styleRow);
            if (w.kind.equals("button")) {
                LinearLayout btnBgRow = new LinearLayout(context);
                btnBgRow.setOrientation(LinearLayout.HORIZONTAL);
                MaterialButton bgBtn = new MaterialButton(context);
                bgBtn.setText(w.btnBg == 0 ? "BG color" : "BG: " + shortHex(w.btnBg));
                bgBtn.setOnClickListener(x -> showColorWheel(w.btnBg == 0 ? -7829368 : w.btnBg, (argb, hex) -> {
                    w.btnBg = argb;
                    bgBtn.setText("BG: " + hex);
                    renderAdv[0].run();
                }));
                bgBtn.setOnLongClickListener(x -> {
                    w.btnBg = 0;
                    bgBtn.setText("BG color");
                    renderAdv[0].run();
                    return true;
                });
                btnBgRow.addView(bgBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton gradBtn = new MaterialButton(context);
                gradBtn.setText(w.btnBg2 == 0 ? "Gradient" : "Grad: " + shortHex(w.btnBg2));
                gradBtn.setOnClickListener(x -> showColorWheel(w.btnBg2 == 0 ? -16776961 : w.btnBg2, (argb, hex) -> {
                    w.btnBg2 = argb;
                    gradBtn.setText("Grad: " + hex);
                    renderAdv[0].run();
                }));
                gradBtn.setOnLongClickListener(x -> {
                    w.btnBg2 = 0;
                    gradBtn.setText("Gradient");
                    renderAdv[0].run();
                    Extensions.showMessage(context, "Gradient cleared (long-press clears)");
                    return true;
                });
                btnBgRow.addView(gradBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                advProps.addView(btnBgRow);
                LinearLayout btnShapeRow = new LinearLayout(context);
                btnShapeRow.setOrientation(LinearLayout.HORIZONTAL);
                EditText cornerInput = new EditText(context);
                cornerInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                cornerInput.setText(w.btnCornerRadiusDp > 0 ? String.valueOf(w.btnCornerRadiusDp) : "");
                cornerInput.setHint("Corner dp");
                btnShapeRow.addView(UiFields.wrap(context, cornerInput, "Corner dp", 0),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                cornerInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        try {
                            w.btnCornerRadiusDp = s.length() == 0 ? 0 : Float.parseFloat(s.toString());
                            renderAdv[0].run();
                        } catch (Exception ignored) {
                        }
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
                EditText padInput = new EditText(context);
                padInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                padInput.setText(w.btnPaddingDp > 0 ? String.valueOf(w.btnPaddingDp) : "");
                padInput.setHint("Pad dp");
                btnShapeRow.addView(UiFields.wrap(context, padInput, "Padding dp", 0),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                padInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        try {
                            w.btnPaddingDp = s.length() == 0 ? 0 : Integer.parseInt(s.toString());
                            renderAdv[0].run();
                        } catch (Exception ignored) {
                        }
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
                advProps.addView(btnShapeRow);
                LinearLayout btnBorderRow = new LinearLayout(context);
                btnBorderRow.setOrientation(LinearLayout.HORIZONTAL);
                EditText borderInput = new EditText(context);
                borderInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                borderInput.setText(w.btnBorderWidthDp > 0 ? String.valueOf(w.btnBorderWidthDp) : "");
                borderInput.setHint("Border dp");
                btnBorderRow.addView(UiFields.wrap(context, borderInput, "Border dp", 0),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                borderInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        try {
                            w.btnBorderWidthDp = s.length() == 0 ? 0 : Float.parseFloat(s.toString());
                            renderAdv[0].run();
                        } catch (Exception ignored) {
                        }
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
                MaterialButton borderBtn = new MaterialButton(context);
                borderBtn.setText(w.btnBorderColor == -1 ? "Border color" : "Border: " + shortHex(w.btnBorderColor));
                borderBtn.setOnClickListener(x -> showColorWheel(
                        w.btnBorderColor == -1 ? -1 : w.btnBorderColor, (argb, hex) -> {
                            w.btnBorderColor = argb;
                            borderBtn.setText("Border: " + hex);
                            renderAdv[0].run();
                        }));
                borderBtn.setOnLongClickListener(x -> {
                    w.btnBorderColor = -1;
                    borderBtn.setText("Border color");
                    renderAdv[0].run();
                    return true;
                });
                btnBorderRow.addView(borderBtn, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                advProps.addView(btnBorderRow);
                MaterialSwitch animBtnSwitch = new MaterialSwitch(context);
                animBtnSwitch.setText("Animated border");
                animBtnSwitch.setChecked(w.btnAnim);
                advProps.addView(animBtnSwitch);
                LinearLayout animBtnBox = new LinearLayout(context);
                animBtnBox.setOrientation(LinearLayout.VERTICAL);
                advProps.addView(animBtnBox);
                final Runnable[] renderBtnAnim = new Runnable[1];
                renderBtnAnim[0] = () -> {
                    animBtnBox.removeAllViews();
                    if (!w.btnAnim) return;
                    if (w.btnAnimColors == null) w.btnAnimColors = new ArrayList<>();
                    if (w.btnAnimColors.size() < 2) {
                        while (w.btnAnimColors.size() < 2) {
                            w.btnAnimColors.add(w.btnAnimColors.isEmpty() ? -16776961 : -65536);
                        }
                    }
                    LinearLayout chipRow = new LinearLayout(context);
                    chipRow.setOrientation(LinearLayout.HORIZONTAL);
                    animBtnBox.addView(chipRow);
                    for (int i = 0; i < w.btnAnimColors.size(); i++) {
                        final int idx = i;
                        int color = w.btnAnimColors.get(idx);
                        MaterialButton chip = new MaterialButton(context);
                        chip.setText("");
                        chip.setCornerRadius(dp(24));
                        try {
                            chip.setBackgroundTintList(ColorStateList.valueOf(color));
                        } catch (Exception ignored) {
                        }
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                        int m = dp(4);
                        lp.setMargins(m, 0, m, 0);
                        chip.setLayoutParams(lp);
                        chip.setOnClickListener(x -> showColorWheel(color, (argb, hex) -> {
                            w.btnAnimColors.set(idx, argb);
                            renderBtnAnim[0].run();
                            renderAdv[0].run();
                        }));
                        chip.setOnLongClickListener(x -> {
                            if (w.btnAnimColors.size() > 2) {
                                w.btnAnimColors.remove(idx);
                                renderBtnAnim[0].run();
                                renderAdv[0].run();
                            } else {
                                Extensions.showMessage(context, "Need at least 2 colors");
                            }
                            return true;
                        });
                        chipRow.addView(chip);
                    }
                    MaterialButton add = new MaterialButton(context);
                    add.setBackgroundResource(R.drawable.add_24px);
                    LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
                    int am = dp(4);
                    alp.setMargins(am, 0, am, 0);
                    add.setLayoutParams(alp);
                    add.setOnClickListener(x -> showColorWheel(-65536, (argb, hex) -> {
                        w.btnAnimColors.add(argb);
                        renderBtnAnim[0].run();
                        renderAdv[0].run();
                    }));
                    chipRow.addView(add);
                    EditText speedInput = new EditText(context);
                    speedInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    if (w.btnAnimSpeedMs > 0) speedInput.setText(String.valueOf(w.btnAnimSpeedMs));
                    speedInput.setHint("Speed ms");
                    speedInput.addTextChangedListener(new TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                        }

                        public void onTextChanged(CharSequence s, int a, int b, int c) {
                            try {
                                w.btnAnimSpeedMs = s.length() == 0 ? 0 : Integer.parseInt(s.toString());
                            } catch (Exception ignored) {
                            }
                        }

                        public void afterTextChanged(Editable s) {
                        }
                    });
                    animBtnBox.addView(UiFields.wrap(context, speedInput, "Animation speed ms", 0));
                    MaterialSwitch rainbowBtnSwitch = new MaterialSwitch(context);
                    rainbowBtnSwitch.setText("Rainbow (RGB) mode");
                    rainbowBtnSwitch.setChecked(w.btnAnimRainbow);
                    rainbowBtnSwitch.setOnCheckedChangeListener((b, c) -> w.btnAnimRainbow = c);
                    animBtnBox.addView(rainbowBtnSwitch);
                };
                animBtnSwitch.setOnCheckedChangeListener((b, c) -> {
                    w.btnAnim = c;
                    if (c && (w.btnAnimColors == null || w.btnAnimColors.isEmpty())) {
                        w.btnAnimColors = new ArrayList<>(Arrays.asList(-16776961, -65536));
                    }
                    renderBtnAnim[0].run();
                    renderAdv[0].run();
                });
                renderBtnAnim[0].run();
                MaterialAutoCompleteTextView actionTv = new MaterialAutoCompleteTextView(context);
                actionTv.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_dropdown_item_1line, new String[]{"Dismiss", "Open URL"}));
                actionTv.setText(w.btnAction == null ? "Dismiss" : w.btnAction, false);
                actionTv.setInputType(InputType.TYPE_NULL);
                actionTv.setCursorVisible(false);
                actionTv.setOnClickListener(vv -> actionTv.showDropDown());
                TextInputLayout actionBox =
                        UiFields.box(context, "Tap action");
                actionBox.addView(actionTv);
                advProps.addView(actionBox);
                EditText urlInput = new EditText(context);
                urlInput.setText(w.url == null ? "" : w.url);
                View urlBox = UiFields.wrap(context, urlInput, "URL (for Open URL)", 0);
                advProps.addView(urlBox);
                Runnable syncUrlBox = () -> urlBox.setVisibility(
                        actionTv.getText() != null && actionTv.getText().toString().equals("Open URL")
                                ? View.VISIBLE : View.GONE);
                actionTv.setOnItemClickListener((p, vv, pos, id) -> {
                    w.btnAction = pos == 1 ? "Open URL" : "Dismiss";
                    syncUrlBox.run();
                });
                syncUrlBox.run();
                urlInput.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        w.url = s.toString();
                    }

                    public void afterTextChanged(Editable s) {
                    }
                });
            }
        };

        renderAdv[0] = () -> {
            advCanvas.removeAllViews();
            boolean richText = form.dlgHtml != null && form.dlgHtml.isChecked();
            for (OverlayInjectorUtil.AdvWidget w : new ArrayList<>(advWidgets)) {
                View vw;
                if (w.kind.equals("image")) {
                    ImageView iv = new ImageView(context);
                    iv.setAdjustViewBounds(true);
                    if (w.imageB64 != null && !w.imageB64.isEmpty()) {
                        try {
                            byte[] bytes = Base64.decode(w.imageB64, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bitmap != null) iv.setImageBitmap(bitmap);
                        } catch (Exception ignored) {
                        }
                    } else {
                        iv.setBackgroundColor(0xFF9E9E9E);
                    }
                    vw = iv;
                } else if (w.kind.equals("button")) {
                    MaterialButton btn = new MaterialButton(context);
                    btn.setText(displayText(w.text == null || w.text.isEmpty() ? "Button" : w.text, richText));
                    btn.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 14f);
                    if (w.textColor != 0) btn.setTextColor(w.textColor);
                    applyWidgetTypeface(btn, w, form);
                    btn.setBackground(buildButtonDrawable(w));
                    vw = btn;
                } else {
                    TextView tv = new TextView(context);
                    tv.setText(displayText(w.text == null || w.text.isEmpty() ? "Text" : w.text, richText));
                    tv.setTextSize(w.textSizeSp > 0 ? w.textSizeSp : 16f);
                    if (w.textColor != 0) tv.setTextColor(w.textColor);
                    applyWidgetTypeface(tv, w, form);
                    vw = tv;
                }
                if (w == advSelected[0]) vw.setAlpha(0.65f);
                vw.setTag(w);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = (int) (w.leftDp * advDensity);
                lp.topMargin = (int) (w.topDp * advDensity);
                vw.setLayoutParams(lp);
                vw.setOnTouchListener(new View.OnTouchListener() {
                    float lastX;
                    float lastY;
                    boolean moved;
                    boolean dragging;
                    int snapLockX = Integer.MIN_VALUE;
                    int snapLockY = Integer.MIN_VALUE;

                    public boolean onTouch(View v, MotionEvent event) {
                        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                lastX = event.getRawX();
                                lastY = event.getRawY();
                                moved = false;
                                dragging = true;
                                snapLockX = Integer.MIN_VALUE;
                                snapLockY = Integer.MIN_VALUE;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                return true;
                            case MotionEvent.ACTION_MOVE: {
                                if (!dragging) return false;
                                float dx = event.getRawX() - lastX;
                                float dy = event.getRawY() - lastY;
                                if (!moved && Math.hypot(dx, dy) < 6) return true;
                                moved = true;
                                int rawL = Math.max(0, (int) (p.leftMargin + dx));
                                int rawT = Math.max(0, (int) (p.topMargin + dy));
                                int nl = rawL;
                                int nt = rawT;
                                if (form.advSnapSwitch != null && form.advSnapSwitch.isChecked()) {
                                    int threshPx = (int) (8 * advDensity + 0.5f);
                                    int slackPx = (int) (4 * advDensity + 0.5f);
                                    int[] cand = snapWidget(w, v, rawL, rawT, advCanvas, advDensity);
                                    if (snapLockX != Integer.MIN_VALUE
                                            && Math.abs(rawL - snapLockX) <= threshPx + slackPx) {
                                        nl = snapLockX;
                                    } else {
                                        nl = cand[0];
                                        snapLockX = (cand[0] != rawL) ? cand[0] : Integer.MIN_VALUE;
                                    }
                                    if (snapLockY != Integer.MIN_VALUE
                                            && Math.abs(rawT - snapLockY) <= threshPx + slackPx) {
                                        nt = snapLockY;
                                    } else {
                                        nt = cand[1];
                                        snapLockY = (cand[1] != rawT) ? cand[1] : Integer.MIN_VALUE;
                                    }
                                } else {
                                    snapLockX = Integer.MIN_VALUE;
                                    snapLockY = Integer.MIN_VALUE;
                                }
                                p.leftMargin = nl;
                                p.topMargin = nt;
                                v.setLayoutParams(p);
                                w.leftDp = Math.round(nl / advDensity);
                                w.topDp = Math.round(nt / advDensity);
                                lastX = event.getRawX();
                                lastY = event.getRawY();
                                return true;
                            }
                            case MotionEvent.ACTION_UP:
                                dragging = false;
                                snapLockX = Integer.MIN_VALUE;
                                snapLockY = Integer.MIN_VALUE;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(false);
                                }
                                if (!moved) {
                                    advSelected[0] = w;
                                    renderAdv[0].run();
                                    showAdvProps[0].run();
                                }
                                return true;
                            case MotionEvent.ACTION_CANCEL:
                                dragging = false;
                                snapLockX = Integer.MIN_VALUE;
                                snapLockY = Integer.MIN_VALUE;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(false);
                                }
                                return true;
                        }
                        return false;
                    }
                });
                advCanvas.addView(vw);
            }
        };

        view.findViewById(R.id.advAddText).setOnClickListener(v -> {
            OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
            w.kind = "text";
            w.text = "Text";
            w.textSizeSp = 16f;
            w.leftDp = 16;
            w.topDp = 16 * advWidgets.size();
            advWidgets.add(w);
            advSelected[0] = w;
            renderAdv[0].run();
            showAdvProps[0].run();
        });
        view.findViewById(R.id.advAddImage).setOnClickListener(v -> {
            OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
            w.kind = "image";
            w.leftDp = 16;
            w.topDp = 16 * advWidgets.size();
            advWidgets.add(w);
            advSelected[0] = w;
            renderAdv[0].run();
            showAdvProps[0].run();
            pickImage((thumb, b64) -> {
                w.imageB64 = b64;
                renderAdv[0].run();
                showAdvProps[0].run();
            });
        });
        view.findViewById(R.id.advAddButton).setOnClickListener(v -> {
            OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
            w.kind = "button";
            w.text = "Button";
            w.textSizeSp = 14f;
            w.btnAction = "Dismiss";
            w.leftDp = 16;
            w.topDp = 16 * advWidgets.size();
            advWidgets.add(w);
            advSelected[0] = w;
            renderAdv[0].run();
            showAdvProps[0].run();
        });
        showAdvProps[0].run();

        LinearLayout toastSection = view.findViewById(R.id.overlayToastSection);
        TextInputEditText messageInput = view.findViewById(R.id.overlayToastMessage);
        MaterialAutoCompleteTextView durationTv = view.findViewById(R.id.overlayToastDuration);
        durationTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                new String[]{"Long", "Short"}));
        MaterialAutoCompleteTextView gravityTv = view.findViewById(R.id.overlayToastGravity);
        gravityTv.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                new String[]{"Default", "Bottom", "Center", "Top"}));
        TextInputEditText xInput = view.findViewById(R.id.overlayToastX);
        TextInputEditText yInput = view.findViewById(R.id.overlayToastY);
        MaterialSwitch toastHtmlSwitch = view.findViewById(R.id.overlayToastHtml);
        MaterialSwitch toastB64Switch = view.findViewById(R.id.overlayToastB64);
        TextView toastPreview = view.findViewById(R.id.overlayToastPreview);
        addFormatRow(view.findViewById(R.id.toastFormatRow), messageInput);

        LinearLayout dialogSection = view.findViewById(R.id.overlayDialogSection);
        final LinearLayout dialogSection2 = view.findViewById(R.id.overlayDialogSection2);
        final LinearLayout styleSection = view.findViewById(R.id.overlayStyleSection);
        TextInputEditText dlgTitleInput = view.findViewById(R.id.overlayDlgTitle);
        TextInputEditText dlgMsgInput = view.findViewById(R.id.overlayDlgMessage);
        TextInputEditText dlgPosInput = view.findViewById(R.id.overlayDlgPos);
        MaterialAutoCompleteTextView dlgPosAction = view.findViewById(R.id.overlayDlgPosAction);
        TextInputEditText dlgPosUrl = view.findViewById(R.id.overlayDlgPosUrl);
        TextInputEditText dlgNegInput = view.findViewById(R.id.overlayDlgNeg);
        MaterialAutoCompleteTextView dlgNegAction = view.findViewById(R.id.overlayDlgNegAction);
        TextInputEditText dlgNegUrl = view.findViewById(R.id.overlayDlgNegUrl);
        TextInputEditText dlgNeuInput = view.findViewById(R.id.overlayDlgNeu);
        MaterialAutoCompleteTextView dlgNeuAction = view.findViewById(R.id.overlayDlgNeuAction);
        TextInputEditText dlgNeuUrl = view.findViewById(R.id.overlayDlgNeuUrl);
        MaterialSwitch cancelSwitch = view.findViewById(R.id.overlayDlgCancelable);
        MaterialSwitch dontShowSwitch = view.findViewById(R.id.overlayDlgDontShow);
        MaterialSwitch dlgHtmlSwitch = view.findViewById(R.id.overlayDlgHtml);
        MaterialSwitch dlgB64Switch = view.findViewById(R.id.overlayDlgB64);
        TextView dlgPreviewTitle = view.findViewById(R.id.overlayDlgPreviewTitle);
        TextView dlgPreviewMsg = view.findViewById(R.id.overlayDlgPreviewMsg);
        String[] buttonActions = {"Dismiss", "Open URL"};
        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, buttonActions);
        dlgPosAction.setAdapter(actionAdapter);
        dlgNegAction.setAdapter(actionAdapter);
        dlgNeuAction.setAdapter(actionAdapter);
        addFormatRow(view.findViewById(R.id.dlgFormatRow), dlgMsgInput);
        attachFormatMenu(messageInput);
        attachFormatMenu(dlgMsgInput);
        attachFormatMenu(dlgTitleInput);

        Runnable updatePreview = () -> {
            try {
                if (toastSection.getVisibility() == View.VISIBLE) {
                    String text = messageInput.getText() == null ? "" : messageInput.getText().toString();
                    if (toastHtmlSwitch.isChecked()) {
                        toastPreview.setText(HtmlCompat.fromHtml(text,
                                HtmlCompat.FROM_HTML_MODE_LEGACY));
                    } else {
                        toastPreview.setText(text);
                    }
                } else {
                    String title = dlgTitleInput.getText() == null ? "" : dlgTitleInput.getText().toString();
                    String msg = dlgMsgInput.getText() == null ? "" : dlgMsgInput.getText().toString();
                    if (dlgHtmlSwitch.isChecked()) {
                        dlgPreviewTitle.setText(HtmlCompat.fromHtml(title,
                                HtmlCompat.FROM_HTML_MODE_LEGACY));
                        dlgPreviewMsg.setText(HtmlCompat.fromHtml(msg,
                                HtmlCompat.FROM_HTML_MODE_LEGACY));
                    } else {
                        dlgPreviewTitle.setText(title);
                        dlgPreviewMsg.setText(msg);
                    }
                    Typeface dlgTf = loadTypeface(form.dlgFont, form.dlgFontPath);
                    try {
                        if (dlgTf != null) {
                            dlgPreviewTitle.setTypeface(dlgTf, Typeface.BOLD);
                            dlgPreviewMsg.setTypeface(dlgTf);
                        } else {
                            dlgPreviewTitle.setTypeface(null, Typeface.BOLD);
                            dlgPreviewMsg.setTypeface(null);
                        }
                    } catch (Exception ignored) {
                    }
                    updateStylePreview(form);
                    updatePreviewImage(form);
                }
            } catch (Exception ignored) {
            }
        };
        TextWatcher previewWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updatePreview.run();
            }

            public void afterTextChanged(Editable s) {
            }
        };
        messageInput.addTextChangedListener(previewWatcher);
        dlgTitleInput.addTextChangedListener(previewWatcher);
        dlgMsgInput.addTextChangedListener(previewWatcher);
        toastHtmlSwitch.setOnCheckedChangeListener((b, c) -> updatePreview.run());
        dlgHtmlSwitch.setOnCheckedChangeListener((b, c) -> {
            updatePreview.run();
            renderAdv[0].run();
        });
        MaterialAutoCompleteTextView profileTv = view.findViewById(R.id.overlayProfileTv);
        refreshProfiles(form, true);
        Runnable syncModeSections = () -> {
            String mode = modeTv.getText() == null ? "Toast" : modeTv.getText().toString();
            boolean isToast = !"Dialog".equals(mode) && !"Advanced".equals(mode);
            boolean isAdvanced = "Advanced".equals(mode);
            boolean isDialog = "Dialog".equals(mode);
            toastSection.setVisibility(isToast ? View.VISIBLE : View.GONE);
            dialogSection.setVisibility(isDialog ? View.VISIBLE : View.GONE);
            if (dialogSection2 != null) dialogSection2.setVisibility(isDialog ? View.VISIBLE : View.GONE);
            if (styleSection != null) styleSection.setVisibility(isToast ? View.GONE : View.VISIBLE);
            advancedSection.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
        };
        modeTv.setOnItemClickListener((p, v, pos, id) -> {
            profileTv.setText("", false);
            refreshProfiles(form, pos == 0);
            syncModeSections.run();
            updatePreview.run();
        });
        syncModeSections.run();
        setupStyleControls(view, form, updatePreview);
        activeAdvRender = renderAdv[0];
        bindUrlVisibility(form);
        refreshFontLabels(form);
        refreshAdvFontLabels(form);
        if (form.dlgFontBtn != null) {
            form.dlgFontBtn.setOnClickListener(v -> showFontPickerDialog((font, path) -> {
                form.dlgFont = font;
                form.dlgFontPath = path;
                refreshFontLabels(form);
                updatePreview.run();
            }, form.dlgFont, form.dlgFontPath, form.dlgFontBtn));
        }
        if (form.advFontBtn != null) {
            form.advFontBtn.setOnClickListener(v -> showFontPickerDialog((font, path) -> {
                form.advFont = font;
                form.advFontPath = path;
                refreshAdvFontLabels(form);
                renderAdv[0].run();
            }, form.advFont, form.advFontPath, form.advFontBtn));
        }
        if (form.advFontApplyAllBtn != null) {
            form.advFontApplyAllBtn.setOnClickListener(v -> {
                for (OverlayInjectorUtil.AdvWidget w : advWidgets) {
                    if (w == null || w.kind.equals("image")) continue;
                    w.font = form.advFont == null ? "" : form.advFont;
                    w.fontPath = form.advFontPath == null ? "" : form.advFontPath;
                }
                renderAdv[0].run();
                showAdvProps[0].run();
                Extensions.showMessage(context, "Dialog font applied to all widgets");
            });
        }
        updatePreview.run();
        profileTv.setOnItemClickListener((p, v, pos, id) -> {
            String name = profileTv.getText() == null ? "" : profileTv.getText().toString();
            if (name.isEmpty()) return;
            if (toastSection.getVisibility() == View.VISIBLE) {
                OverlayInjectorUtil.ToastOptions opts = OverlayProfiles.getToast(context, name);
                if (opts != null) {
                    applyToast(form, opts);
                    updatePreview.run();
                }
            } else {
                OverlayInjectorUtil.DialogOptions opts = OverlayProfiles.getDialog(context, name);
                if (opts != null) {
                    if (advancedSection.getVisibility() == View.VISIBLE) {
                        applyAdvanced(form, advWidgets, advSelected, opts);
                        renderAdv[0].run();
                        showAdvProps[0].run();
                    } else {
                        applyDialog(form, opts);
                    }
                    updatePreview.run();
                }
            }
        });
        view.findViewById(R.id.overlayProfileSave).setOnClickListener(v -> {
            boolean isToast = toastSection.getVisibility() == View.VISIBLE;
            boolean isAdvanced = advancedSection.getVisibility() == View.VISIBLE;
            EditText nameInput = new EditText(context);
            nameInput.setSingleLine(true);
            String current = profileTv.getText() == null ? "" : profileTv.getText().toString();
            nameInput.setText(current);
            dialogUtil.getDialogBuilder()
                    .setTitle("Save profile")
                    .setView(UiFields.wrap(context, nameInput, "Profile name", 16))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String profileName = nameInput.getText() == null ? ""
                                : nameInput.getText().toString().trim();
                        if (profileName.isEmpty()) {
                            Extensions.showMessage(context, "Enter a name");
                            return;
                        }
                        if (isToast) {
                            OverlayInjectorUtil.ToastOptions opts = collectToast(form);
                            if (opts == null) return;
                            OverlayProfiles.putToast(context, profileName, opts);
                        } else if (isAdvanced) {
                            OverlayInjectorUtil.DialogOptions opts = collectAdvanced(form, advWidgets);
                            if (opts == null) return;
                            OverlayProfiles.putDialog(context, profileName, opts);
                        } else {
                            OverlayInjectorUtil.DialogOptions opts = collectDialog(form);
                            if (opts == null) return;
                            OverlayProfiles.putDialog(context, profileName, opts);
                        }
                        refreshProfiles(form, isToast);
                        profileTv.setText(profileName, false);
                        Extensions.showMessage(context, "Profile saved");
                    }).show();
        });
        view.findViewById(R.id.overlayProfileDelete).setOnClickListener(v -> {
            String name = profileTv.getText() == null ? "" : profileTv.getText().toString();
            if (name.isEmpty()) return;
            boolean isToast = toastSection.getVisibility() == View.VISIBLE;
            if (isToast) OverlayProfiles.removeToast(context, name);
            else OverlayProfiles.removeDialog(context, name);
            profileTv.setText("", false);
            refreshProfiles(form, isToast);
        });

        overlayImagePreviewView = view.findViewById(R.id.overlayDlgImagePreview);
        overlayImagePreviewView.setVisibility(View.GONE);
        overlayImagePreviewView.setImageBitmap(null);
        view.findViewById(R.id.overlayDlgImageBtn).setOnClickListener(v -> {
            pickImage((thumb, b64) -> {
                overlayImageBase64 = b64;
                if (overlayImagePreviewView != null) {
                    overlayImagePreviewView.setImageBitmap(thumb);
                    overlayImagePreviewView.setVisibility(View.VISIBLE);
                }
                if (activeOverlayForm != null) updatePreviewImage(activeOverlayForm);
            });
        });

        LinearLayout posBox = view.findViewById(R.id.overlayPosBox);
        LinearLayout negBox = view.findViewById(R.id.overlayNegBox);
        LinearLayout neuBox = view.findViewById(R.id.overlayNeuBox);
        view.findViewById(R.id.overlayPosAddBtn).setOnClickListener(v -> {
            posBox.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        });
        view.findViewById(R.id.overlayNegAddBtn).setOnClickListener(v -> {
            negBox.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        });
        view.findViewById(R.id.overlayNeuAddBtn).setOnClickListener(v -> {
            neuBox.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        });
        view.findViewById(R.id.overlayPosRemoveBtn).setOnClickListener(v -> {
            posBox.setVisibility(View.GONE);
            view.findViewById(R.id.overlayPosAddBtn).setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.overlayNegRemoveBtn).setOnClickListener(v -> {
            negBox.setVisibility(View.GONE);
            view.findViewById(R.id.overlayNegAddBtn).setVisibility(View.VISIBLE);
        });
        view.findViewById(R.id.overlayNeuRemoveBtn).setOnClickListener(v -> {
            neuBox.setVisibility(View.GONE);
            view.findViewById(R.id.overlayNeuAddBtn).setVisibility(View.VISIBLE);
        });

        MaterialCheckBox autosign = view.findViewById(R.id.overlayAutosign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        view.findViewById(R.id.overlaySignSettings).setOnClickListener(uiHelper.showSignSettingsDialog());
        activeOverlayForm = form;

        AlertDialog overlayDialog = dialogUtil.getDialogBuilder()
                .setTitle("Toast / Dialog")
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    OverlayInjectorUtil.ToastOptions toast = null;
                    OverlayInjectorUtil.DialogOptions dlg = null;
                    if (advancedSection.getVisibility() == View.VISIBLE) {
                        dlg = collectAdvanced(form, advWidgets);
                        if (dlg == null) return;
                    } else if (toastSection.getVisibility() == View.VISIBLE) {
                        toast = collectToast(form);
                        if (toast == null) return;
                    } else {
                        dlg = collectDialog(form);
                        if (dlg == null) return;
                    }
                    runAddOverlay(file, selectedActivities, toast, dlg, sign[0]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        overlayDialog.setOnDismissListener(d -> {
            activeOverlayForm = null;
            activeAdvRender = null;
        });
        overlayDialog.show();
    }

    private static void syncUrlBox(MaterialAutoCompleteTextView actionTv, TextInputEditText urlInput) {
        if (actionTv == null || urlInput == null || !(urlInput.getParent() instanceof View)) return;
        boolean open = actionTv.getText() != null && actionTv.getText().toString().equals("Open URL");
        ((View) urlInput.getParent()).setVisibility(open ? View.VISIBLE : View.GONE);
    }

    private static void syncUrlBoxes(OverlayForm f) {
        if (f == null) return;
        syncUrlBox(f.posAction, f.posUrl);
        syncUrlBox(f.negAction, f.negUrl);
        syncUrlBox(f.neuAction, f.neuUrl);
    }

    private void bindUrlVisibility(OverlayForm f) {
        MaterialAutoCompleteTextView[] actions = {f.posAction, f.negAction, f.neuAction};
        for (MaterialAutoCompleteTextView actionTv : actions) {
            if (actionTv == null) continue;
            actionTv.setOnItemClickListener((p, v, pos, id) -> syncUrlBoxes(f));
        }
        syncUrlBoxes(f);
    }

    private String actionUrl(MaterialAutoCompleteTextView actionTv, TextInputEditText urlInput, boolean[] badUrl) {
        try {
            if (actionTv.getText() != null && actionTv.getText().toString().equals("Open URL")) {
                String url = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
                if (url.isEmpty() || url.equals("https://") || url.equals("http://")) {
                    Extensions.showMessage(context, "Enter a URL or set the button to Dismiss");
                    badUrl[0] = true;
                    return null;
                }
                return url;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void addFormatRow(LinearLayout row, EditText target) {
        String[][] buttons = {{"B", "<b>", "</b>"}, {"I", "<i>", "</i>"}, {"U", "<u>", "</u>"}};
        for (String[] spec : buttons) {
            MaterialButton btn = new MaterialButton(context);
            btn.setText(spec[0]);
            btn.setFocusable(false);
            btn.setFocusableInTouchMode(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int margin = dp(4);
            params.setMargins(margin, 0, margin, 0);
            btn.setOnClickListener(v -> wrapSelection(target, spec[1], spec[2]));
            row.addView(btn, params);
        }
        MaterialButton colorBtn = new MaterialButton(context);
        colorBtn.setText("Color");
        colorBtn.setFocusable(false);
        colorBtn.setFocusableInTouchMode(false);
        colorBtn.setOnClickListener(v -> showColorWheelDialog(target));
        row.addView(colorBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
    }

    private static void wrapSelection(EditText target, String open, String close) {
        if (target == null) return;
        try {
            int start = target.getSelectionStart();
            int end = target.getSelectionEnd();
            if (start < 0 || end < 0) {
                target.append(open + close);
                target.setSelection(target.getText().length() - close.length());
                return;
            }
            if (start > end) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            Editable text = target.getText();
            if (text == null) return;
            if (start == end) {
                text.insert(start, open + close);
                target.setSelection(start + open.length());
            } else {
                text.insert(end, close);
                text.insert(start, open);
                target.setSelection(end + open.length() + close.length());
            }
        } catch (Exception ignored) {
        }
    }

    private void showColorWheelDialog(EditText target) {
        int start = -1;
        int end = -1;
        try {
            start = target.getSelectionStart();
            end = target.getSelectionEnd();
        } catch (Exception ignored) {
        }
        showColorWheelDialog(target, start, end);
    }

    private interface ColorPicked {
        void onColor(int argb, String hex);
    }

    private void showColorWheel(int initialArgb, ColorPicked onApply) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);
        ColorWheelView wheel = new ColorWheelView(context);
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        root.addView(wheel, wheelParams);
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        View previewSwatch = new View(context);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        swatchParams.rightMargin = dp(8);
        previewRow.addView(previewSwatch, swatchParams);
        EditText hexInput = new EditText(context);
        hexInput.setHint("#RRGGBB");
        hexInput.setSingleLine(true);
        previewRow.addView(UiFields.wrap(context, hexInput, null, 0),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(previewRow);
        SeekBar alphaBar = new SeekBar(context);
        alphaBar.setMax(255);
        alphaBar.setProgress(255);
        root.addView(alphaBar);
        LinearLayout presetRow = new LinearLayout(context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] presets = {0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF2196F3, 0xFF00BCD4,
                0xFF4CAF50, 0xFFFFEB3B, 0xFFFF9800, 0xFFFFC107, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF000000};
        for (int color : presets) {
            TextView swatch = new TextView(context);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1f);
            int margin = dp(2);
            params.setMargins(margin, margin, margin, margin);
            final int presetColor = color;
            swatch.setOnClickListener(v -> wheel.setColor(presetColor));
            presetRow.addView(swatch, params);
        }
        root.addView(presetRow);

        final boolean[] syncing = new boolean[1];
        Runnable syncFromWheel = new Runnable() {
            public void run() {
                if (syncing[0]) return;
                syncing[0] = true;
                try {
                    int color = wheel.getColor(alphaBar.getProgress());
                    previewSwatch.setBackgroundColor(color);
                    String hex = String.format(alphaBar.getProgress() == 255 ? "#%06X" : "#%08X",
                            alphaBar.getProgress() == 255 ? (color & 0xFFFFFF) : color);
                    String cur = hexInput.getText() == null ? "" : hexInput.getText().toString();
                    if (!hex.equalsIgnoreCase(cur)) hexInput.setText(hex);
                } finally {
                    syncing[0] = false;
                }
            }
        };
        wheel.setListener(syncFromWheel);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                syncFromWheel.run();
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        hexInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (syncing[0]) return;
                syncing[0] = true;
                try {
                    String hex = s.toString().trim();
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    if (hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        try {
                            int parsed = (int) Long.parseLong(hex.substring(1), 16)
                                    | (hex.length() == 7 ? 0xFF000000 : 0);
                            if ((parsed & 0xFFFFFF) != (wheel.getColor(255) & 0xFFFFFF)) {
                                wheel.setColor(parsed);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } finally {
                    syncing[0] = false;
                }
            }

            public void afterTextChanged(Editable s) {
            }
        });
        syncFromWheel.run();
        wheel.setColor(initialArgb);
        dialogUtil.getDialogBuilder()
                .setTitle("Pick a color")
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Apply", (d, w) -> {
                    String hex = hexInput.getText() == null ? "" : hexInput.getText().toString().trim();
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    if (!hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        Extensions.showMessage(context, "Invalid hex color");
                        return;
                    }
                    hex = hex.toUpperCase();
                    int argb;
                    try {
                        argb = (int) Long.parseLong(hex.substring(1), 16)
                                | (hex.length() == 7 ? 0xFF000000 : 0);
                    } catch (Exception e) {
                        Extensions.showMessage(context, "Invalid hex color");
                        return;
                    }
                    onApply.onColor(argb, hex);
                })
                .show();
    }

    private void showColorWheelDialog(EditText target, int snapshotStart, int snapshotEnd) {
        final int[] snapshot = new int[]{snapshotStart, snapshotEnd};
        showColorWheel(0xFFFF0000, (argb, hex) -> {
            String tag = "<font color=\"" + hex + "\">";
            if (snapshot[0] >= 0 && snapshot[1] >= 0 && target.getText() != null) {
                int len = target.getText().length();
                int start = Math.max(0, Math.min(snapshot[0], len));
                int end = Math.max(0, Math.min(snapshot[1], len));
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                try {
                    target.setSelection(start, end);
                } catch (Exception ignored) {
                }
            }
            wrapSelection(target, tag, "</font>");
        });
    }

    private static class ColorWheelView extends View {
        private float hue;
        private float sat = 1f;
        private float val = 1f;
        private Runnable listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cx;
        private float cy;
        private float ringOuter;
        private float ringWidth;
        private float sqLeft;
        private float sqTop;
        private float sqSize;

        ColorWheelView(Context ctx) {
            super(ctx);
        }

        void setListener(Runnable listener) {
            this.listener = listener;
        }

        void setColor(int argb) {
            float[] hsv = new float[3];
            Color.colorToHSV(argb, hsv);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            invalidate();
            if (listener != null) listener.run();
        }

        int getColor(int alpha) {
            return Color.HSVToColor(alpha, new float[]{hue, sat, val});
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float size = Math.min(w, h);
            cx = w / 2f;
            cy = h / 2f;
            ringOuter = size / 2f - 4;
            ringWidth = Math.max(36, size * 0.12f);
            float inner = ringOuter - ringWidth;
            sqSize = (float) (inner * 1.35);
            sqLeft = cx - sqSize / 2f;
            sqTop = cy - sqSize / 2f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int[] hueColors = new int[361];
            for (int i = 0; i <= 360; i++) hueColors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ringWidth);
            paint.setShader(new SweepGradient(cx, cy, hueColors, null));
            canvas.drawCircle(cx, cy, ringOuter - ringWidth / 2f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            int hueColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
            paint.setShader(new LinearGradient(sqLeft, 0, sqLeft + sqSize, 0,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP));
            canvas.drawRect(sqLeft, sqTop, sqLeft + sqSize, sqTop + sqSize, paint);
            paint.setShader(new LinearGradient(0, sqTop, 0, sqTop + sqSize,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            canvas.drawRect(sqLeft, sqTop, sqLeft + sqSize, sqTop + sqSize, paint);
            paint.setShader(null);
            double rad = Math.toRadians(hue);
            float hr = ringOuter - ringWidth / 2f;
            float hx = cx + (float) Math.cos(rad) * hr;
            float hy = cy + (float) Math.sin(rad) * hr;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(hx, hy, ringWidth / 2f - 2, paint);
            float sx = sqLeft + sat * sqSize;
            float sy = sqTop + (1f - val) * sqSize;
            canvas.drawCircle(sx, sy, 12, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                return super.onTouchEvent(event);
            }
            float x = event.getX();
            float y = event.getY();
            float dx = x - cx;
            float dy = y - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist >= ringOuter - ringWidth && dist <= ringOuter + 8) {
                hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
                invalidate();
                if (listener != null) listener.run();
                return true;
            }
            if (x >= sqLeft && x <= sqLeft + sqSize && y >= sqTop && y <= sqTop + sqSize) {
                sat = Math.max(0f, Math.min(1f, (x - sqLeft) / sqSize));
                val = Math.max(0f, Math.min(1f, 1f - (y - sqTop) / sqSize));
                invalidate();
                if (listener != null) listener.run();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private static int clampOffset(String text) {
        try {
            int v = Integer.parseInt(text.trim());
            if (v > 9999) return 9999;
            if (v < -9999) return -9999;
            return v;
        } catch (Exception e) {
            return 0;
        }
    }

    private void runAddToast(File file, List<String> selectedActivities, String message, boolean sign) {
        OverlayInjectorUtil.ToastOptions toast = new OverlayInjectorUtil.ToastOptions();
        toast.message = message;
        runAddOverlay(file, selectedActivities, toast, null, sign);
    }

    private void runAddOverlay(File file, List<String> selectedActivities,
                               OverlayInjectorUtil.ToastOptions toast,
                               OverlayInjectorUtil.DialogOptions dialog, boolean sign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doAdd = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    File result = OverlayInjectorUtil.addOverlayToActivities(context, file, selectedActivities, toast, dialog, logger);
                    if (sign) wrapper[0].signApk(result);
                    pm.dismiss();
                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        };
        if (sign) SignWrapper.requireAuth(context, sw -> {
            wrapper[0] = sw;
            doAdd.run();
        }); else doAdd.run();
    }

    private void showRemoveAllToastsDialog(File file) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        CheckBox autosign = new CheckBox(context);
        autosign.setText(R.string.auto_sign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        layout.addView(autosign);

        MaterialButton signSettings = new MaterialButton(context);
        signSettings.setText(R.string.sign_set);
        signSettings.setOnClickListener(uiHelper.showSignSettingsDialog());
        layout.addView(signSettings);

        dialogUtil.getDialogBuilder()
                .setTitle("Remove all toasts")
                .setMessage("This will decompile the APK, remove all Toast.makeText/show calls from smali code, then recompile. Continue?")
                .setView(layout)
                .setPositiveButton("Remove", (dialog, which) -> runRemoveAllToasts(file, sign[0]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runRemoveAllToasts(File file, boolean sign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doRemove = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    File result = ToastInjectorUtil.removeAllToasts(context, file, logger);
                    if (sign) wrapper[0].signApk(result);
                    pm.dismiss();
                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        };
        if (sign) SignWrapper.requireAuth(context, sw -> {
            wrapper[0] = sw;
            doRemove.run();
        }); else doRemove.run();
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
                    tv.setTypeface(Typeface.MONOSPACE);
                    int pad = dp(16);
                    tv.setPadding(pad, pad, pad, pad);
                    ScrollView scroll = new ScrollView(context);
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
        boolean deepOpt = settings.getBoolean("deep_optimize", false);
        Set<String> filesToDelete = settings.getStringSet("filesToDelete", null);
        new Thread(() -> {
            try {
                for (int i = 0; i < apks.size(); i++) {
                    File apk = apks.get(i);
                    String msg = context.rss.getString(R.string.optimizing, apk.getName());
                    pm.setText(msg);
                    logger.logMessage(msg);
                    File opt = ApkOptimizer.optimize(context, apk, delFiles, settings, logger);
                    if (deepOpt) {
                        logger.logMessage(context.rss.getString(R.string.deep_optimize_running));
                        opt = ApkDeepOptimizer.optimize(context, opt, filesToDelete, settings, logger);
                    }
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

    private void removeSignature(File apk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            boolean ok = SignatureStripUtil.strip(apk);
            pm.dismiss();
            context.handler.post(() -> {
                Extensions.showMessage(context, ok ? "Signature removed" : "Failed to remove signature");
                if (ok) context.loadFolderInPane(apk.getParentFile(), pane1, false);
            });
        }).start();
    }

    private void showSignatureHealthDialog(File apk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            String report = buildSignatureHealthReport(apk);
            pm.dismiss();
            context.handler.post(() -> {
                TextView tv = new TextView(context);
                tv.setText(report);
                tv.setTextIsSelectable(true);
                tv.setTextSize(13);
                tv.setTypeface(Typeface.MONOSPACE);
                int pad = dp(16);
                tv.setPadding(pad, pad, pad, pad);
                ScrollView scroll = new ScrollView(context);
                scroll.addView(tv);
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle("Signature health")
                        .setView(scroll)
                        .setNegativeButton(android.R.string.ok, null)
                        .setNeutralButton("Re-sign", (d, w) -> SignatureKeyDialog.show(context, apk, false))
                        .setPositiveButton("Fix alignment", (d, w) -> {
                            ProgressManager pm2 = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    ApkZipAlignUtil.ensureInstallable(apk);
                                    pm2.dismiss();
                                    context.handler.post(() -> showSignatureHealthDialog(apk));
                                } catch (Exception e) {
                                    pm2.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        })
                        .show());
            });
        }).start();
    }

    private String buildSignatureHealthReport(File apk) {
        StringBuilder sb = new StringBuilder();
        String issue = ApkZipAlignUtil.installIssue(apk);
        sb.append("Zipalign: ").append(issue == null ? "OK" : issue).append('\n');
        ApkVerifier.Result result = null;
        String verifyError = null;
        try {
            result = new ApkVerifier.Builder(apk).build().verify();
        } catch (Exception e) {
            verifyError = e.getMessage() != null ? e.getMessage() : e.toString();
        }
        String v1;
        String v2;
        if (verifyError != null) {
            v1 = "error: " + verifyError;
            v2 = "error: " + verifyError;
        } else {
            try {
                v1 = result.isVerifiedUsingV1Scheme() ? "verified" : "missing or invalid";
            } catch (Exception e) {
                v1 = "error: " + e.getMessage();
            }
            try {
                v2 = result.isVerifiedUsingV2Scheme() ? "verified" : "missing or invalid";
            } catch (Exception e) {
                v2 = "error: " + e.getMessage();
            }
        }
        sb.append("V1 (JAR): ").append(v1).append('\n');
        sb.append("V2 (APK Signature Scheme v2): ").append(v2).append('\n');
        String sha256 = "none";
        try {
            List<X509Certificate> certs = CertUtil.getCertificatesUnverified(apk);
            if (certs == null || certs.isEmpty()) certs = CertUtil.getCertificates(apk);
            if (certs != null && !certs.isEmpty()) sha256 = CertUtil.getSha256(certs.get(0));
        } catch (Exception e) {
            sha256 = "error: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
        sb.append("Cert SHA-256: ").append(sha256);
        return sb.toString();
    }
}
