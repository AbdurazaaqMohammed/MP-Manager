package io.github.abdurazaaqmohammed.features.apk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.adapters.main.ApkManifestEditor;
import io.github.abdurazaaqmohammed.plugins.ext.ApkJob;
import io.github.abdurazaaqmohammed.plugins.ext.ApkMoreAction;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ApkInfoUtil;
import io.github.abdurazaaqmohammed.utils.ApkOptimizer;
import io.github.abdurazaaqmohammed.utils.ApkSignatureInfo;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FastDexPatch;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.Il2CppDumperUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.NativeLibPatcher;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.ApkDeepOptimizer;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.adapters.main.FileIconLoader;
import mt.modder.hub.apkCloner.util.ApkCloner;

/**
 * APK info + decompile option dialogs extracted from ApkToolsHandler.
 */
public class ApkInfoDialogs {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;
    private final ApkManifestEditor manifestEditor;
    private final ApkSignatureTools signatures;
    private final ApkOverlayTools overlay;
    /** Memoized {APK has libil2cpp.so, APK has any lib/*.so} for nativeScanFile. */
    private File nativeScanFile;
    private boolean[] nativeScanResult;

    public ApkInfoDialogs(MainActivity context,
                          DialogUtil dialogUtil,
                          UIHelper uiHelper, boolean pane1,
                          ApkManifestEditor manifestEditor,
                          ApkSignatureTools signatures, ApkOverlayTools overlay) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
        this.manifestEditor = manifestEditor;
        this.signatures = signatures;
        this.overlay = overlay;
    }

    /**
     * APK data multiplexing (full flavor): redirects entries that are identical
     * to entries inside a stored embedded APK so the duplicated data is stored
     * once, then signs with the raw-file V2/V3 signer. Lite shows an info
     * dialog instead.
     */
    private void showMultiplexDialog(File file, String fileName) {
        MultiplexLauncher.open(context, dialogUtil, pane1, file, fileName);
    }

    /**
     * Memoized availability scan for the two native-lib More entries:
     * [0] = APK contains libil2cpp.so (il2cppdumper), [1] = APK contains any
     * lib/*.so (LibPatcher). Warmed from the info loader, awaited on demand.
     */
    private synchronized boolean[] nativeScan(File file) {
        if (nativeScanResult != null && nativeScanFile != null && nativeScanFile.equals(file)) {
            return nativeScanResult;
        }
        boolean[] r = new boolean[2];
        try {
            r[0] = Il2CppDumperUtil.hasIl2Cpp(file);
        } catch (Exception ignored) {
        }
        try {
            r[1] = NativeLibPatcher.hasNativeLibs(file);
        } catch (Exception ignored) {
        }
        nativeScanFile = file;
        nativeScanResult = r;
        return r;
    }

    /**
     * il2cppdumper: adds libil2cppdumper.so to every supported ABI folder of
     * the APK and wires a System.loadLibrary call into the class picked as the
     * injection point (Application class by default, any activity otherwise),
     * per il2cppdumper-main/README.md.
     */
    private void showIl2CppDumpDialog(File file, String fileName) {
        View ll = LayoutInflater.from(context).inflate(R.layout.dialog_il2cpp_dump, null);
        MaterialAutoCompleteTextView targetView = ll.findViewById(R.id.il2cpp_target);
        TextInputLayout targetLayout = ll.findViewById(R.id.il2cpp_target_layout);
        LinearLayout abiBox = ll.findViewById(R.id.il2cpp_abis);
        MaterialCheckBox providerBox = ll.findViewById(R.id.il2cpp_provider);
        MaterialCheckBox autosign = ll.findViewById(R.id.autosign);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
        providerBox.setChecked(settings.getBoolean("il2cpp_provider", true));
        providerBox.setOnCheckedChangeListener((bv, checked) ->
                settings.edit().putBoolean("il2cpp_provider", checked).apply());

        final List<MaterialCheckBox> abiChecks = new ArrayList<>();
        final List<Il2CppDumperUtil.Target> targets = new ArrayList<>();
        final String[] target = {null};
        targetLayout.setEnabled(false);
        new Thread(() -> {
            List<Il2CppDumperUtil.Target> found = new ArrayList<>();
            List<String> abis = new ArrayList<>();
            try {
                found = Il2CppDumperUtil.listTargets(context, file);
                abis = Il2CppDumperUtil.dumpableAbis(file);
            } catch (Exception ignored) {
            }
            List<Il2CppDumperUtil.Target> fTargets = found;
            List<String> fAbis = abis;
            context.handler.post(() -> {
                targets.addAll(fTargets);
                if (targets.isEmpty()) {
                    targetView.setText(context.getString(R.string.il2cpp_target_none), false);
                } else {
                    List<String> labels = new ArrayList<>();
                    for (Il2CppDumperUtil.Target t : targets) labels.add(targetLabel(t));
                    targetView.setAdapter(new ArrayAdapter<>(context,
                            android.R.layout.simple_dropdown_item_1line, labels));
                    // Restore the last pick when this APK still offers it,
                    // otherwise fall back to the best-ranked class.
                    String remembered = settings.getString("il2cpp_target", null);
                    int index = 0;
                    for (int i = 0; i < targets.size(); i++) {
                        if (targets.get(i).className.equals(remembered)) {
                            index = i;
                            break;
                        }
                    }
                    target[0] = targets.get(index).className;
                    targetView.setText(labels.get(index), false);
                    targetLayout.setEnabled(true);
                }
                for (String abi : fAbis) {
                    MaterialCheckBox cb = new MaterialCheckBox(context);
                    cb.setText(abi);
                    cb.setChecked(settings.getBoolean("il2cpp_abi_" + abi, true));
                    cb.setOnCheckedChangeListener((bv, c) ->
                            settings.edit().putBoolean("il2cpp_abi_" + abi, c).apply());
                    abiChecks.add(cb);
                    abiBox.addView(cb);
                }
                if (fAbis.isEmpty()) {
                    TextView none = new TextView(context);
                    none.setText(R.string.il2cpp_no_abi);
                    none.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                    abiBox.addView(none);
                }
            });
        }).start();
        targetView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= targets.size()) return;
            target[0] = targets.get(position).className;
            settings.edit().putString("il2cpp_target", target[0]).apply();
        });

        dialogUtil.getDialogBuilder()
                .setTitle(R.string.il2cpp_dump_title)
                .setView(ll)
                .setPositiveButton(R.string.patch_apk, (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    for (MaterialCheckBox cb : abiChecks) {
                        if (cb.isChecked()) chosen.add(cb.getText().toString());
                    }
                    if (chosen.isEmpty()) {
                        Extensions.showMessage(context, context.getString(R.string.il2cpp_abis_label));
                        return;
                    }
                    if (TextUtils.isEmpty(target[0])) {
                        Extensions.showMessage(context, context.getString(R.string.il2cpp_pick_target));
                        return;
                    }
                    boolean injectProvider = providerBox.isChecked();
                    SignWrapper[] wrapper = new SignWrapper[1];
                    Runnable doPatch = () -> {
                        ProgressManager pm = new ProgressManager(context, true).show();
                        APKLogger logger = pm.getLogger();
                        new Thread(() -> {
                            try {
                                File out = Il2CppDumperUtil.patchApk(context, file, chosen,
                                        target[0], injectProvider, logger);
                                if (sign[0]) wrapper[0].signApk(out);
                                pm.dismiss();
                                context.handler.post(() -> {
                                    Extensions.showMessage(context, context.getString(R.string.il2cpp_done, out.getName()));
                                    context.loadFolderInPane(file.getParentFile(), pane1, false);
                                });
                            } catch (Exception e) {
                                pm.dismiss();
                                new ErrorUtil(context).showError(e);
                            }
                        }).start();
                    };
                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doPatch.run();
                    }); else doPatch.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Dropdown text for an injection point, prefixed by what kind of class it is. */
    private String targetLabel(Il2CppDumperUtil.Target target) {
        int res;
        switch (target.kind) {
            case Il2CppDumperUtil.Target.APPLICATION: res = R.string.il2cpp_target_application; break;
            case Il2CppDumperUtil.Target.LAUNCHER: res = R.string.il2cpp_target_launcher; break;
            case Il2CppDumperUtil.Target.UNITY: res = R.string.il2cpp_target_unity; break;
            default: res = R.string.il2cpp_target_activity; break;
        }
        return context.getString(res, target.className);
    }

    /** Offsets the label resource for a LibPatcher patch type. */
    private static int patchTypeLabel(String type) {
        switch (type) {
            case "boolean_true": return R.string.libpatcher_type_boolean_true;
            case "boolean_false": return R.string.libpatcher_type_boolean_false;
            case "integer_zero": return R.string.libpatcher_type_integer_zero;
            case "integer16_max": return R.string.libpatcher_type_integer16_max;
            case "integer32_max": return R.string.libpatcher_type_integer32_max;
            case "long_zero": return R.string.libpatcher_type_long_zero;
            case "long_64": return R.string.libpatcher_type_long_64;
            case "float_zero": return R.string.libpatcher_type_float_zero;
            case "double_zero": return R.string.libpatcher_type_double_zero;
            default: return R.string.libpatcher_type_void_nop;
        }
    }

    /**
     * LibPatcher: writes instruction stubs at hex offsets inside a chosen
     * native library of the APK (ported from the LibPatcher python tool).
     */
    private void showLibPatcherDialog(File file, String fileName) {
        View ll = LayoutInflater.from(context).inflate(R.layout.dialog_lib_patcher, null);
        MaterialAutoCompleteTextView libInput = ll.findViewById(R.id.lp_lib);
        TextView archView = ll.findViewById(R.id.lp_arch);
        LinearLayout fieldsBox = ll.findViewById(R.id.lp_fields);
        MaterialCheckBox autosign = ll.findViewById(R.id.autosign);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());

        final Map<String, TextInputEditText> offsetInputs = new LinkedHashMap<>();
        for (String type : NativeLibPatcher.PATCH_TYPES) {
            com.google.android.material.textfield.TextInputLayout box = UiFields.box(context, context.getString(patchTypeLabel(type)));
            TextInputEditText edit = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
            edit.setText(settings.getString("libpatcher_" + type, ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * context.getResources().getDisplayMetrics().density);
            box.setLayoutParams(lp);
            offsetInputs.put(type, edit);
            fieldsBox.addView(box);
        }

        final List<String> libEntries = new ArrayList<>();
        final List<String> libDisplay = new ArrayList<>();
        final String[] chosenEntry = {null};
        new Thread(() -> {
            Map<String, String> libs = NativeLibPatcher.nativeLibArchives(file);
            List<String> entries = new ArrayList<>(libs.keySet());
            List<String> display = new ArrayList<>();
            for (String entry : entries) {
                display.add(entry + "  [" + libs.get(entry) + "]");
            }
            context.handler.post(() -> {
                libEntries.addAll(entries);
                libDisplay.addAll(display);
                if (display.isEmpty()) {
                    archView.setVisibility(View.VISIBLE);
                    archView.setText(R.string.libpatcher_no_libs);
                    return;
                }
                libInput.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_dropdown_item_1line, libDisplay));
                libInput.setText(libDisplay.get(0), false);
                chosenEntry[0] = libEntries.get(0);
                archView.setVisibility(View.VISIBLE);
                archView.setText(libDisplay.get(0).substring(libDisplay.get(0).indexOf('[')));
            });
        }).start();
        libInput.setOnItemClickListener((parent, view, position, id) -> {
            chosenEntry[0] = position >= 0 && position < libEntries.size() ? libEntries.get(position) : null;
            if (position >= 0 && position < libDisplay.size()) {
                archView.setVisibility(View.VISIBLE);
                archView.setText(libDisplay.get(position).substring(libDisplay.get(position).indexOf('[')));
            }
        });

        dialogUtil.getDialogBuilder()
                .setTitle(R.string.libpatcher_title)
                .setView(ll)
                .setPositiveButton(R.string.patch_apk, (d, w) -> {
                    String entry = chosenEntry[0];
                    if (entry == null) {
                        Extensions.showMessage(context, context.getString(R.string.libpatcher_pick_lib));
                        return;
                    }
                    Map<String, String> raw = new LinkedHashMap<>();
                    for (String type : NativeLibPatcher.PATCH_TYPES) {
                        CharSequence text = offsetInputs.get(type).getText();
                        String value = text == null ? "" : text.toString().trim();
                        if (!value.isEmpty()) raw.put(type, value);
                    }
                    if (raw.isEmpty()) {
                        Extensions.showMessage(context, context.getString(R.string.libpatcher_no_offsets));
                        return;
                    }
                    try {
                        NativeLibPatcher.parseOffsets(raw);
                    } catch (IOException e) {
                        Extensions.showMessage(context, context.getString(R.string.libpatcher_invalid_offsets));
                        return;
                    }
                    SignWrapper[] wrapper = new SignWrapper[1];
                    Runnable doPatch = () -> {
                        ProgressManager pm = new ProgressManager(context, true).show();
                        APKLogger logger = pm.getLogger();
                        new Thread(() -> {
                            File workDir = new File(context.getCacheDir(),
                                    "libpatch_" + System.currentTimeMillis());
                            try {
                                workDir.mkdirs();
                                logger.logMessage("Patching " + entry);
                                byte[] so = FastDexPatch.readDexBytes(file, entry);
                                NativeLibPatcher.patch(so, entry, NativeLibPatcher.parseOffsets(raw));
                                File patchedSo = new File(workDir, new File(entry).getName());
                                try (FileOutputStream fos = new FileOutputStream(patchedSo)) {
                                    fos.write(so);
                                }
                                Map<String, File> replacements = new LinkedHashMap<>();
                                replacements.put(entry, patchedSo);
                                File out = FileUtils.getUnusedFile(new File(file.getParentFile(),
                                        FilenameUtils.getBaseName(file.getName()) + "_libpatched.apk"));
                                ApkZipAlignUtil.rebuildApk(file, out, replacements, null, null, null);
                                if (sign[0]) wrapper[0].signApk(out);
                                pm.dismiss();
                                context.handler.post(() -> {
                                    Extensions.showMessage(context,
                                            context.getString(R.string.libpatcher_done, entry, out.getName()));
                                    context.loadFolderInPane(file.getParentFile(), pane1, false);
                                });
                            } catch (Exception e) {
                                pm.dismiss();
                                new ErrorUtil(context).showError(e);
                            } finally {
                                File[] files = workDir.listFiles();
                                if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
                                    f.delete();
                                //noinspection ResultOfMethodCallIgnored
                                workDir.delete();
                            }
                        }).start();
                    };
                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doPatch.run();
                    }); else doPatch.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Single-choice style rows for the More dialog; disabled rows are greyed out and unclickable. */
    private final class MoreItemsAdapter extends BaseAdapter {
        private final List<String> titles;
        private final boolean[] enabled;
        private final LayoutInflater inflater = LayoutInflater.from(context);

        MoreItemsAdapter(List<String> titles, boolean[] enabled) {
            this.titles = titles;
            this.enabled = enabled;
        }

        @Override public int getCount() { return titles.size(); }
        @Override public String getItem(int position) { return titles.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean isEnabled(int position) {
            return position >= 0 && position < enabled.length && enabled[position];
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView != null ? convertView : inflater.inflate(R.layout.item_more_action, parent, false);
            TextView tv = (TextView) v;
            tv.setText(getItem(position));
            tv.setAlpha(isEnabled(position) ? 1f : 0.38f);
            return v;
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
                .setNeutralButton(R.string.more, (dialog, which) -> {
                    // "View certificate" moved onto the Signatures row, which now opens the
                    // full signature sheet on tap.
                    List<String> moreTitles = new ArrayList<>(Arrays.asList(context.rss.getString(R.string.sign_apk), context.rss.getString(R.string.optimize_apk), context.rss.getString(R.string.decompile_reandroid_apkeditor), context.rss.getString(R.string.refactor_obfuscated_resource_names), context.rss.getString(R.string.protect_reandroid_apkeditor), context.rss.getString(R.string.clone_apk), context.rss.getString(R.string.add_toast_dialog), context.rss.getString(R.string.remove_all_toasts), context.rss.getString(R.string.remove_signature), context.rss.getString(R.string.signature_health), context.rss.getString(R.string.manifest_toggles), context.rss.getString(R.string.permissions), context.rss.getString(R.string.npatch_integrated), context.rss.getString(R.string.multiplex_apk), context.rss.getString(R.string.il2cpp_dump_native), context.rss.getString(R.string.patch_native_libs)));
                    final int builtinCount = moreTitles.size();
                    final List<ApkMoreAction> pluginMore =
                            ExtensionRegistry.apkActions();
                    for (ApkMoreAction ext : pluginMore) {
                        if (ext == null) continue;
                        moreTitles.add(ext.title() == null || ext.title().isEmpty() ? ext.id() : ext.title());
                    }
                    // External (out-of-process) APK actions, fire-and-forget.
                    final List<ExternalActions.Entry> externalApk =
                            ExternalActions.apkEntries(context);
                    for (ExternalActions.Entry e : externalApk) {
                        if (e == null) continue;
                        moreTitles.add(e.title == null || e.title.isEmpty() ? e.id : e.title);
                    }
                    // il2cppdumper needs libil2cpp.so, LibPatcher needs any lib/*.so.
                    boolean[] moreEnabled = new boolean[moreTitles.size()];
                    Arrays.fill(moreEnabled, true);
                    boolean[] nativeState = nativeScan(file);
                    if (moreEnabled.length > 15) {
                        moreEnabled[14] = nativeState[0];
                        moreEnabled[15] = nativeState[1];
                    }
                    dialogUtil.getDialogBuilder().setAdapter(new MoreItemsAdapter(moreTitles, moreEnabled), (dialog12, which1) -> {
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
                            CheckBox ultraCompress = ll.findViewById(R.id.ultra_compress);
                            ultraCompress.setChecked(settings.getBoolean("ultra_compress", true));
                            ultraCompress.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("ultra_compress", isChecked).apply());
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
                                                            File opt = ApkOptimizer.optimize(context, file, delFiles[0], settings, settings.getBoolean("ultra_compress", true), logger);
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
                    } else if (which1 == 6) overlay.showAddToastDialog(file, filePath);
                    else if (which1 == 7) overlay.showRemoveAllToastsDialog(file);
                    else if (which1 == 8) signatures.removeSignature(file);
                    else if (which1 == 9) signatures.showSignatureHealthDialog(file);
                    else if (which1 == 10) manifestEditor.showManifestTogglesDialog(file);
                    else if (which1 == 11) manifestEditor.showPermissionsDialog(file);
                    else if (which1 == 12) XposedPatcherLauncher.open(context, dialogUtil, pane1, file, fileName);
                    else if (which1 == 13) showMultiplexDialog(file, fileName);
                    else if (which1 == 14) showIl2CppDumpDialog(file, fileName);
                    else if (which1 == 15) showLibPatcherDialog(file, fileName);
                    else {
                        int pluginIndex = which1 - builtinCount;
                        if (pluginIndex >= 0 && pluginIndex < pluginMore.size()) {
                            ApkMoreAction ext = pluginMore.get(pluginIndex);
                            if (ext != null) {
                                try {
                                    ext.run(new ApkJob(context, file, fileName, filePath));
                                } catch (Exception ignored) {
                                }
                            }
                        } else {
                            int extIndex = which1 - builtinCount - pluginMore.size();
                            if (extIndex >= 0 && extIndex < externalApk.size()) {
                                ExternalActions.Entry found =
                                        externalApk.get(extIndex);
                                    if (found != null) {
                                        try {
                                            File stageDir = new File(context.getCacheDir(), "apk_plugin_stage");
                                            if (!stageDir.exists()) stageDir.mkdirs();
                                            File staged = new File(stageDir, file.getName());
                                            try (InputStream in = new FileInputStream(file);
                                                 FileOutputStream out = new FileOutputStream(staged)) {
                                                byte[] buf = new byte[65536];
                                                int n;
                                                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                                            }
                                            Uri apkUri = FileProvider.getUriForFile(
                                                    context, context.getPackageName() + ".provider", staged);
                                            Intent extIntent = PluginHost.explicitIntent(
                                                    found.plugin, PluginContracts.ACTION_APK);
                                            extIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                            extIntent.putExtra(PluginContracts.EXTRA_PLUGIN_ID,
                                                    found.plugin.pluginId);
                                            extIntent.putExtra(PluginContracts.EXTRA_APK_NAME,
                                                    fileName);
                                            extIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                            try {
                                                context.grantUriPermission(found.plugin.packageName, apkUri,
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                            } catch (Exception ignored) {
                                            }
                                            PluginTrust.ensureTrusted(
                                                    context, found.plugin, () -> {
                                                        List<String> choiceLabels = new ArrayList<>();
                                                        List<String> choiceIds = new ArrayList<>();
                                                        String choicesMeta = null;
                                                        String autoSignMeta = null;
                                                        if (found.plugin.meta != null) {
                                                            Bundle meta = found.plugin.meta;
                                                            choicesMeta = meta.getString(PluginContracts.META_APK_CHOICES);
                                                            autoSignMeta = meta.getString(PluginContracts.META_APK_AUTO_SIGN);
                                                        }
                                                        if (choicesMeta != null && !choicesMeta.isEmpty()) {
                                                            for (String pair : choicesMeta.split(",")) {
                                                                String[] parts = pair.split("\\|");
                                                                if (parts.length == 2) {
                                                                    choiceLabels.add(parts[0]);
                                                                    choiceIds.add(parts[1]);
                                                                } else if (parts.length == 1 && !parts[0].isEmpty()) {
                                                                    choiceLabels.add(parts[0]);
                                                                    choiceIds.add(parts[0]);
                                                                }
                                                            }
                                                        }
                                                        final boolean offerSign = autoSignMeta != null && (autoSignMeta.equals("1") || autoSignMeta.equalsIgnoreCase("true"));
                                                        final int[] selectedChoice = {0};
                                                        final boolean[] selectedSign = {autoSignMeta != null && (autoSignMeta.equals("1") || autoSignMeta.equalsIgnoreCase("true"))};
                                                        final Consumer<ActivityResult> runPlugin = result -> {
                                                            try {
                                                                if (result.getResultCode() == Activity.RESULT_OK) {
                                                                    Intent data = result.getData();
                                                                    String outName = data == null ? null : data.getStringExtra(
                                                                            PluginContracts.EXTRA_OUTPUT_NAME);
                                                                    if (outName == null || outName.isEmpty()
                                                                            || outName.contains("/") || outName.contains("\\")) {
                                                                        int dot = fileName.lastIndexOf('.');
                                                                        outName = (dot > 0 ? fileName.substring(0, dot) : fileName) + "_killed.apk";
                                                                    }
                                                                    File outFile = new File(file.getParentFile(), outName);
                                                                    int i = 1;
                                                                    while (outFile.exists()) {
                                                                        int d = outName.lastIndexOf('.');
                                                                        outFile = new File(file.getParentFile(),
                                                                                (d > 0 ? outName.substring(0, d) : outName) + "_" + (++i) + (d > 0 ? outName.substring(d) : ""));
                                                                    }
                                                                    try (InputStream in = new FileInputStream(staged);
                                                                         FileOutputStream out = new FileOutputStream(outFile)) {
                                                                        byte[] buf = new byte[65536];
                                                                        int n;
                                                                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                                                                    }
                                                                    String msg = data == null ? null : data.getStringExtra(
                                                                            PluginContracts.EXTRA_MESSAGE);
                                                                    if (msg == null || msg.isEmpty()) msg = "Done";
                                                                    if (selectedSign[0]) {
                                                                        final String m = msg;
                                                                        File finalOutFile = outFile;
                                                                        SignWrapper.requireAuth(context, sw -> {
                                                                            try {
                                                                                sw.signApk(finalOutFile);
                                                                                Extensions.showMessage(context, m);
                                                                            } catch (Exception e) {
                                                                                new ErrorUtil(context).showError(e);
                                                                            } finally {
                                                                                context.loadFolderInPane(file.getParentFile(), pane1, false);
                                                                            }
                                                                        });
                                                                    } else {
                                                                        Extensions.showMessage(context, msg);
                                                                        context.loadFolderInPane(file.getParentFile(), pane1, false);
                                                                    }
                                                                }
                                                            } catch (Exception ignored) {
                                                            } finally {
                                                                try { staged.delete(); } catch (Exception ignored) {
                                                                }
                                                            }
                                                        };
                                                        if (choiceLabels.isEmpty() && !offerSign) {
                                                            context.launchExternalApk(extIntent, runPlugin);
                                                        } else {
                                                            LinearLayout box = new LinearLayout(context);
                                                            box.setOrientation(LinearLayout.VERTICAL);
                                                            int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
                                                            box.setPadding(pad, pad, pad, pad);
                                                            RadioGroup group = new RadioGroup(context);
                                                            for (int i = 0; i < choiceLabels.size(); i++) {
                                                                RadioButton rb = new RadioButton(context);
                                                                rb.setText(choiceLabels.get(i));
                                                                rb.setId(i);
                                                                group.addView(rb);
                                                            }
                                                            if (!choiceLabels.isEmpty()) {
                                                                ((RadioButton) group.getChildAt(0)).setChecked(true);
                                                                final int[] sel = selectedChoice;
                                                                group.setOnCheckedChangeListener((g, id) -> sel[0] = id);
                                                            }
                                                            box.addView(group);
                                                            CheckBox signing = new CheckBox(context);
                                                            signing.setText(R.string.auto_sign);
                                                            signing.setChecked(selectedSign[0]);
                                                            signing.setOnCheckedChangeListener((bv, v) -> selectedSign[0] = v);
                                                            box.addView(signing);
                                                            new MaterialAlertDialogBuilder(context)
                                                                    .setTitle(found.plugin.label != null ? String.valueOf(found.plugin.label) : found.title)
                                                                    .setView(box)
                                                                    .setNegativeButton(android.R.string.cancel, null)
                                                                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                                                                        int ch = selectedChoice[0];
                                                                        if (ch >= 0 && ch < choiceIds.size()) extIntent.putExtra("method", choiceIds.get(ch));
                                                                        extIntent.putExtra("sign", selectedSign[0]);
                                                                        context.launchExternalApk(extIntent, runPlugin);
                                                                    })
                                                                    .show();
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
        boolean elevatedFileOps = AccessManager.fileOpsOn(context);
        if (elevatedFileOps) {
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
                    // Capture the package name on the UI thread first — posting
                    // then sleeping races the APK-info loader below that fills
                    // pkgName, so the old code read "" and bailed out early.
                    final String[] pkgHolder = new String[1];
                    try {
                        context.handler.post(() -> {
                            CharSequence t = pkgName.getText();
                            String s = t == null ? "" : t.toString().trim();
                            // Wait until the APK-info loader fills pkgName
                            // (it starts as R.string.loading) instead of
                            // giving up with "No root info available".
                            if (!s.isEmpty()
                                    && !s.equals(context.getString(R.string.loading))) {
                                synchronized (pkgHolder) {
                                    pkgHolder[0] = s;
                                    pkgHolder.notifyAll();
                                }
                            }
                        });
                    } catch (Exception ignored) {}
                    String pkgNameStr = null;
                    synchronized (pkgHolder) {
                        for (int i = 0; i < 100 && pkgHolder[0] == null; i++) {
                            try { pkgHolder.wait(100); } catch (InterruptedException ignored) {}
                            try {
                                final String[] retry = new String[1];
                                context.handler.post(() -> {
                                    CharSequence t = pkgName.getText();
                                    String s = t == null ? "" : t.toString().trim();
                                    if (!s.isEmpty()
                                            && !s.equals(context.getString(R.string.loading))) {
                                        retry[0] = s;
                                    }
                                });
                                // Give the posted runnable a moment to run.
                                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
                                if (retry[0] != null) {
                                    pkgHolder[0] = retry[0];
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                        pkgNameStr = pkgHolder[0];
                    }
                    // Fall back to parsing the APK itself so root info still
                    // loads even if the main APK-info thread hasn't finished.
                    if (TextUtils.isEmpty(pkgNameStr)) {
                        try {
                            PackageInfo pi = context.getPackageManager()
                                    .getPackageArchiveInfo(filePath, 0);
                            if (pi != null) pkgNameStr = pi.packageName;
                        } catch (Exception ignored) {}
                    }
                    if (TextUtils.isEmpty(pkgNameStr)) {
                        context.handler.post(() -> {
                            rootInfoContent.removeAllViews();
                            TextView empty = new TextView(context);
                            empty.setText(R.string.no_root_info_available);
                            empty.setTextSize(12);
                            empty.setPadding(0, dp(4), 0, dp(4));
                            rootInfoContent.addView(empty);
                        });
                        return;
                    }
                    final String resolvedPkg = pkgNameStr;
                    String uid = rm.getAppUid(resolvedPkg);
                    List<String> apkPaths = rm.getAppApkPaths(resolvedPkg);
                    List<String> dataDirs = rm.getAppDataDirs(resolvedPkg);
                    // pm path lists base APK first, then splits — show only
                    // the base path to avoid duplicate-looking rows.
                    final String baseApkPath = (apkPaths != null && !apkPaths.isEmpty())
                            ? apkPaths.get(0) : null;
                    context.handler.post(() -> {
                        rootInfoContent.removeAllViews();
                        if (!TextUtils.isEmpty(uid)) addRootInfoRow(rootInfoContent, "UID", uid, null, ad);
                        if (!TextUtils.isEmpty(baseApkPath)) addRootInfoRow(rootInfoContent, "APK Path", baseApkPath, baseApkPath, ad);
                        for (String dir : dataDirs) {
                            String label = dir.contains("/data/data/")
                                    || dir.contains("/data/user")
                                    || dir.contains("/data/user_de/") ? "Data Dir" : "External Data Dir";
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
            final boolean[] loaded = {false};
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
                long versionCode = ApkInfoUtil.getVersionCode(packageInfo);
                String vCode = versionCode > 0 ? String.valueOf(versionCode) : "";
                String pkg = packageInfo.packageName;
                // Warm the native-lib availability scan for the More dialog.
                nativeScan(file);

                ApkSignatureInfo sigInfo;
                try {
                    sigInfo = ApkSignatureInfo.analyze(file);
                } catch (Exception e) {
                    sigInfo = null;
                }
                final String[] certFp = {""};
                if (sigInfo != null) {
                    X509Certificate primary = sigInfo.getPrimaryCertificate();
                    if (primary != null) certFp[0] = CertUtil.getSha256(primary);
                }
                String signatureStr = sigInfo == null
                        ? context.rss.getString(android.R.string.unknownName) : sigInfo.getStatusLabel();
                final ApkSignatureInfo finalSigInfo = sigInfo;

                String protectedStr;
                try (ArchiveFile af = new ArchiveFile(file); ApkModule am = new ApkModule(af.createZipEntryMap())) {
                    String aProtected = Util.isProtected(am);
                    protectedStr = TextUtils.isEmpty(aProtected) ? context.getString(R.string.protection_not_found) : aProtected;
                } catch (Exception e) {
                    protectedStr = context.rss.getString(android.R.string.unknownName);
                }

                String finalProtectedStr = protectedStr;
                // Hashing the candidate and the installed copy is real I/O, so it
                // stays on this worker thread rather than blocking the dialog.
                String comparisonText = ApkInfoUtil.getInstalledVersion(context, pkg);
                if (comparisonText == null) comparisonText = context.getString(R.string.not_installed);
                else if (ApkInfoUtil.isDowngrade(context, packageInfo))
                    comparisonText += " (" + context.getString(R.string.downgrade) + ")";
                final String comparison = comparisonText;
                final String finalVerName = (TextUtils.isEmpty(verName)
                        ? context.getString(R.string.version_name_unset) : verName)
                        + (TextUtils.isEmpty(vCode) ? "" : " (" + vCode + ")");
                context.handler.post(() -> {
                    apkIcon.setImageDrawable(icon);
                    apkTitle.setText(label);
                    apkVersionName.setText(finalVerName);
                    verCode.setText(TextUtils.isEmpty(vCode) ? "—" : vCode);
                    pkgName.setText(pkg);
                    uiHelper.scrollTextView(pkgName);
                    signaturesInApk.setText(signatureStr);
                    protectedDisplay.setText(finalProtectedStr);
                    fileSize.setText(context.getString(R.string.fs_entries, Formatter.formatFileSize(context, file.length()), ApkInfoUtil.getEntryCount(file)));
                    int target = packageInfo.applicationInfo.targetSdkVersion;
                    apkTargetSdk.setText(target <= 0 ? context.getString(R.string.unknown_sdk) : String.valueOf(target));
                    int min = ApkInfoUtil.getMinSdk(packageInfo);
                    apkMinSdk.setText(min < 0 ? context.getString(R.string.unknown_sdk) : String.valueOf(min));
                    apkCert.setText(TextUtils.isEmpty(certFp[0]) ? context.getString(R.string.no_signature_found) : certFp[0]);
                    apkInstalled.setText(comparison);
                });
                loaded[0] = true;
                context.handler.post(() -> signaturesInApk.setOnClickListener(v -> {
                    if (finalSigInfo != null) {
                        ad.dismiss();
                        SignatureInfoDialog.show(context, dialogUtil, file);
                    }
                }));
                context.handler.post(() -> apkCert.setOnClickListener(v -> {
                    if (finalSigInfo != null) {
                        ad.dismiss();
                        SignatureInfoDialog.show(context, dialogUtil, file);
                    }
                }));
            } catch (Exception e) {
                loaded[0] = false;
                new ErrorUtil(context).showError(e);
            }
            // Only a failed load falls back to placeholders; on success this would
            // wipe the values that were just written into the rows.
            if (!loaded[0]) {
                context.handler.post(() -> showPlaceholders(ad, context.getString(R.string.apk_info_unavailable)));
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

    /**
     * Fills every value row with {@code placeholder}. Without this a failure
     * half-way through the loader left the dialog showing "Loading…" forever,
     * which is what users reported as empty fields.
     */
    private void showPlaceholders(AlertDialog ad, CharSequence placeholder) {
        try {
            if (ad.getWindow() == null) return;
            View root = ad.getWindow().getDecorView();
            int[] ids = {R.id.apkTitle, R.id.apkVersionName, R.id.verCode, R.id.pkgName,
                    R.id.signaturesInApk, R.id.protectedDisplay, R.id.fileSize,
                    R.id.apkTargetSdk, R.id.apkMinSdk, R.id.apkCert, R.id.apkInstalled};
            for (int id : ids) {
                TextView tv = root.findViewById(id);
                if (tv != null) tv.setText(placeholder);
            }
        } catch (Exception ignored) {
        }
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
                // Mirror root folder navigation from the main file manager:
                // plain File.exists() is false for /data/data on Android even
                // with root, so check via AccessManager (root/Shizuku) first.
                boolean exists = false;
                boolean isFile = false;
                try {
                    exists = AccessManager.exists(context, tapPath)
                            || new File(tapPath).exists();
                } catch (Exception ignored) {}
                if (!exists) {
                    Extensions.showMessage(context, "Path " + tapPath + " not accessible");
                    return;
                }
                try {
                    if (AccessManager.active(context) == AccessManager.Backend.SHIZUKU) {
                        isFile = ShizukuManager.isFile(context, tapPath);
                    } else if (AccessManager.active(context) == AccessManager.Backend.ROOT) {
                        RootManager rootMgr = RootManager.getInstance(context);
                        isFile = rootMgr.isFile(tapPath);
                        if (!isFile && !rootMgr.isDirectory(tapPath)) {
                            isFile = new File(tapPath).isFile();
                        }
                    } else {
                        isFile = new File(tapPath).isFile();
                    }
                } catch (Exception ignored) {
                    isFile = new File(tapPath).isFile();
                }
                File target = isFile ? new File(tapPath).getParentFile() : new File(tapPath);
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