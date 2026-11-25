package io.github.abdurazaaqmohammed.feature.apk;

import io.github.abdurazaaqmohammed.feature.apk.flutter.FlutterToolsDialog;
import io.github.abdurazaaqmohammed.feature.apk.frida.FridaGadgetDialog;

import io.github.abdurazaaqmohammed.feature.apk.ApkMoreMenuCustomizer;
import io.github.abdurazaaqmohammed.feature.apk.ApkMoreMenuOrder;
import io.github.abdurazaaqmohammed.feature.apk.ApkOverlayTools;
import io.github.abdurazaaqmohammed.feature.apk.ApkSignatureTools;
import io.github.abdurazaaqmohammed.feature.apk.JiaguUnpackDialog;
import io.github.abdurazaaqmohammed.feature.apk.privacy.PrivacyAuditDialog;
import io.github.abdurazaaqmohammed.feature.apk.SignatureInfoDialog;

import io.github.abdurazaaqmohammed.feature.apk.ApkDeepOptimizer;
import io.github.abdurazaaqmohammed.feature.apk.ApkInfoUtil;
import io.github.abdurazaaqmohammed.feature.apk.ApkOptimizer;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.abdurazaaqmohammed.feature.apk.FastDexPatch;
import io.github.abdurazaaqmohammed.feature.apk.Il2CppDumperUtil;
import io.github.abdurazaaqmohammed.feature.apk.InstallUtil;
import io.github.abdurazaaqmohammed.feature.apk.NativeLibPatcher;
import io.github.abdurazaaqmohammed.feature.dex.DexTools;
import io.github.abdurazaaqmohammed.feature.dex.JadxDecompileHelper;
import io.github.abdurazaaqmohammed.feature.apk.SignWrapper;
import io.github.abdurazaaqmohammed.ui.main.SignatureKeyDialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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

import io.github.abdurazaaqmohammed.ui.main.ApkManifestEditor;
import io.github.abdurazaaqmohammed.plugins.ext.ApkJob;
import io.github.abdurazaaqmohammed.plugins.ext.ApkMoreAction;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginThemeRequests;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apkeditor.decompile.DecompileOptions;
import com.reandroid.apkeditor.decompile.Decompiler;
import com.reandroid.apkeditor.protect.ProtectorOptions;
import com.reandroid.apkeditor.refactor.RefactorOptions;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.feature.apk.MultiplexLauncher;
import io.github.abdurazaaqmohammed.feature.apk.XposedPatcherLauncher;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.UIHelper;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.editor.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.util.io.CopyUtil;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.ui.main.FileIconLoader;
import mt.modder.hub.apkCloner.util.ApkCloner;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

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
    /** file identity + availability triple shared by the info loader and More menu. */
    private File nativeScanFile;
    private long nativeScanSize;
    private long nativeScanModified;
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
     * Last known availability for the native/Flutter More entries:
     * [0] = APK contains libil2cpp.so (il2cppdumper), [1] = APK contains any
     * lib/*.so (LibPatcher), [2] = Flutter artifacts. Published by the info
     * loader, or refreshed in the background by {@link #nativeScanAsync(File, Consumer)}
     * when the More menu is opened before the loader has finished.
     */
    private synchronized boolean[] nativeScanCached(File file) {
        if (nativeScanResult != null && nativeScanFile != null && nativeScanFile.equals(file)
                && nativeScanSize == file.length() && nativeScanModified == file.lastModified()) {
            return nativeScanResult;
        }
        return null;
    }

    /** Records a fresh triple with the file identity it was derived from. */
    private synchronized void setNativeScan(File file, boolean[] result) {
        nativeScanFile = file;
        nativeScanSize = file.length();
        nativeScanModified = file.lastModified();
        nativeScanResult = result;
    }

    /**
     * Ensures {@link #nativeScanCached(File)} has a result for {@code file}
     * without doing ZIP I/O on the caller's thread. The scan reuses the info
     * loader's single central-directory pass, so detection stays identical to
     * the info dialog. {@code onDone} always runs on the main thread, whether
     * the result was already cached or a fresh scan was needed.
     */
    private void nativeScanAsync(File file, Consumer<boolean[]> onDone) {
        boolean[] cached = nativeScanCached(file);
        if (cached != null) {
            context.handler.post(() -> onDone.accept(cached));
            return;
        }
        new Thread(() -> {
            boolean[] r = new boolean[3];
            try {
                ApkInfoLoader.ArchiveFacts facts = ApkInfoLoader.scanArchive(file);
                r[0] = facts.il2cpp;
                r[1] = facts.nativeLibs;
                r[2] = facts.flutter;
            } catch (Exception ignored) {
                // Keep all-false fallback, same as the previous synchronous scan.
            }
            setNativeScan(file, r);
            context.handler.post(() -> onDone.accept(r));
        }, "apk-more-native-scan").start();
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
        AppExecutors.io().execute(() -> {
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
        });
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
                        AppExecutors.io().execute(() -> {
                            try {
                                File out = Il2CppDumperUtil.patchApk(context, file, chosen,
                                        target[0], injectProvider, logger);
                                if (sign[0]) wrapper[0].signApk(out);
                                pm.dismiss();
                                context.handler.post(() -> {
                                    Extensions.showMessage(context, context.getString(R.string.il2cpp_done, out.getName()));
                                    context.refreshAllPanes();
                                });
                            } catch (Exception e) {
                                pm.dismiss();
                                new ErrorUtil(context).showError(e);
                            }
                        });
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
            TextInputLayout box = UiFields.box(context, context.getString(patchTypeLabel(type)));
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
        AppExecutors.io().execute(() -> {
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
        });
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
                        AppExecutors.io().execute(() -> {
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
                                    context.refreshAllPanes();
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
                        });
                    };
                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doPatch.run();
                    }); else doPatch.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showApkMoreDialog(File file, String fileName, String filePath, boolean pane1) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_apk_more_menu, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.apkMoreMenuList);
        ImageButton editButton = dialogView.findViewById(R.id.apkMoreMenuEditButton);

        List<String> order = ApkMoreMenuOrder.load(context);
        Set<String> hidden = ApkMoreMenuOrder.getHiddenIds(context);
        List<String> visibleActions = new ArrayList<>();
        for (String id : order) {
            if (!hidden.contains(id)) visibleActions.add(id);
        }

        // Use the loader-warmed state when available; otherwise show the menu
        // immediately and refresh the native row states after a background scan.
        boolean[] cachedState = nativeScanCached(file);
        final boolean[] nativeState = cachedState != null
                ? cachedState : new boolean[]{false, false, false};

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        if (editButton != null) {
            editButton.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                ApkMoreMenuCustomizer.show(context, () -> showApkMoreDialog(file, fileName, filePath, pane1));
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        final RecyclerView.Adapter<?> menuAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(context).inflate(R.layout.item_file_menu_action, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                String actionId = visibleActions.get(position);
                TextView label = holder.itemView.findViewById(R.id.menuItemLabel);
                label.setText(ApkMoreMenuOrder.labelFor(context, actionId));

                int iconRes = ApkMoreMenuOrder.iconFor(actionId);
                Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), iconRes, null);
                if (drawable != null) {
                    int size = Extensions.dp2px(context, 24);
                    drawable.setBounds(0, 0, size, size);
                    DrawableCompat.setTint(drawable, MaterialColors.getColor(label, android.R.attr.colorPrimary));
                }
                label.setCompoundDrawablesRelative(drawable, null, null, null);
                label.setCompoundDrawablePadding(Extensions.dp2px(context, 8));

                boolean enabled = true;
                if (ApkMoreMenuOrder.IL2CPP.equals(actionId)) enabled = nativeState[0];
                else if (ApkMoreMenuOrder.PATCH_LIBS.equals(actionId)) enabled = nativeState[1];
                else if (ApkMoreMenuOrder.FLUTTER.equals(actionId)) enabled = nativeState.length > 2 && nativeState[2];

                holder.itemView.setAlpha(enabled ? 1f : 0.38f);
                holder.itemView.setEnabled(enabled);
                holder.itemView.setOnClickListener(enabled ? v -> {
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    dispatchApkMoreAction(actionId, file, fileName, filePath, pane1);
                } : null);
            }

            @Override
            public int getItemCount() {
                return visibleActions.size();
            }
        };
        recyclerView.setAdapter(menuAdapter);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setView(dialogView)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
        dialogUtil.styleAlertDialog(dialog);

        if (cachedState == null) {
            nativeScanAsync(file, scanned -> {
                // Late result: only repaint while this dialog is still live.
                if (dialogHolder[0] != dialog || !dialog.isShowing()) return;
                System.arraycopy(scanned, 0, nativeState, 0, scanned.length);
                menuAdapter.notifyDataSetChanged();
            });
        }
    }

    private void dispatchApkMoreAction(String actionId, File file, String fileName, String filePath, boolean pane1) {
        if (ApkMoreMenuOrder.QUICK_EDIT.equals(actionId)) {
            manifestEditor.showEditManifestDialog(file);
        } else if (ApkMoreMenuOrder.SIGN.equals(actionId)) {
            SignatureKeyDialog.show(context, file, false);
        } else if (ApkMoreMenuOrder.OPT.equals(actionId)) {
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
            passesInput.setText(String.valueOf(settings.getInt("deep_opt_max_passes", 0)));
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
            MaterialCheckBox safeMode = ll.findViewById(R.id.deep_optimize_safe_mode);
            safeMode.setChecked(settings.getBoolean("deep_opt_safe_mode", true));
            safeMode.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("deep_opt_safe_mode", isChecked).apply());
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
                            AppExecutors.io().execute(() -> {
                                try {
                                    File opt;
                                    if (deepOpt[0]) {
                                        logger.logMessage(context.rss.getString(R.string.deep_optimize_running));
                                        opt = ApkDeepOptimizer.optimize(context, file,
                                                delFiles[0] ? settings.getStringSet("filesToDelete", null) : null,
                                                settings, logger);
                                    } else {
                                        opt = ApkOptimizer.optimize(context, file, delFiles[0], settings, logger);
                                    }
                                    if (sign[0]) wrapper[0].signApk(opt);
                                    pm.dismiss();
                                    context.handler.post(context::refreshAllPanes);
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            });
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
        } else if (ApkMoreMenuOrder.AAPT2_OPT.equals(actionId)) {
            Aapt2OptimizeDialog.show(context, uiHelper, file);
        } else if (ApkMoreMenuOrder.DECOMPILE.equals(actionId)) {
            showDecompileOptionsDialog(file, fileName);
        } else if (ApkMoreMenuOrder.JADX_JAVA.equals(actionId)) {
            handleApkToJava(file, fileName);
        } else if (ApkMoreMenuOrder.REFACTOR.equals(actionId)) {
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
                        AppExecutors.io().execute(() -> {
                            try {
                                String pXmlFilePath = publicXmlPath[0];
                                if (!TextUtils.isEmpty(pXmlFilePath)) options.publicXml = new File(pXmlFilePath);
                                options.newCommandExecutor(logger).runCommand();
                                logger.close();
                                pm.dismiss();
                                Extensions.showMessage(context, context.getString(R.string.refactored, fileName));
                                context.refreshAllPanes();
                            } catch (Exception e) {
                                pm.dismiss();
                                context.handler.post(() -> new ErrorUtil(context).showError(e));
                                logger.close();
                            }
                        });
                    }).create());
        } else if (ApkMoreMenuOrder.PROTECT.equals(actionId)) {
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
                        AppExecutors.io().execute(() -> {
                            try {
                                options.newCommandExecutor(logger).runCommand();
                                logger.close();
                                pm.dismiss();
                                context.handler.post(() -> {
                                    dialog2.dismiss();
                                    Extensions.showMessage(context, context.rss.getString(R.string.protectd));
                                    context.refreshAllPanes();
                                });
                            } catch (Exception e) {
                                pm.dismiss();
                                context.handler.post(dialog2::dismiss);
                                new ErrorUtil(context).showError(e);
                                logger.close();
                            }
                        });
                    }).create());
        } else if (ApkMoreMenuOrder.CLONE.equals(actionId)) {
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
                            AppExecutors.io().execute(() -> {
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
                                    context.handler.post(context::refreshAllPanes);
                                } catch (Exception e) { pm.dismiss(); new ErrorUtil(context).showError(e); }
                            });
                        };
                        if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                            wrapper[0] = sw;
                            doClone.run();
                        }); else doClone.run();
                    }).show();
        } else if (ApkMoreMenuOrder.ADD_TOAST.equals(actionId)) {
            overlay.showAddToastDialog(file, filePath);
        } else if (ApkMoreMenuOrder.REMOVE_TOASTS.equals(actionId)) {
            overlay.showRemoveAllToastsDialog(file);
        } else if (ApkMoreMenuOrder.REMOVE_SIG.equals(actionId)) {
            signatures.removeSignature(file);
        } else if (ApkMoreMenuOrder.SIG_HEALTH.equals(actionId)) {
            signatures.showSignatureHealthDialog(file);
        } else if (ApkMoreMenuOrder.MANIFEST_TOGGLES.equals(actionId)) {
            manifestEditor.showManifestTogglesDialog(file);
        } else if (ApkMoreMenuOrder.PERMISSIONS.equals(actionId)) {
            manifestEditor.showPermissionsDialog(file);
        } else if (ApkMoreMenuOrder.XPOSED_PATCH.equals(actionId)) {
            XposedPatcherLauncher.open(context, dialogUtil, pane1, file, fileName);
        } else if (ApkMoreMenuOrder.MULTIPLEX.equals(actionId)) {
            showMultiplexDialog(file, fileName);
        } else if (ApkMoreMenuOrder.IL2CPP.equals(actionId)) {
            showIl2CppDumpDialog(file, fileName);
        } else if (ApkMoreMenuOrder.FLUTTER.equals(actionId)) {
            FlutterToolsDialog.showForApk(context, file, fileName);
        } else if (ApkMoreMenuOrder.FRIDA.equals(actionId)) {
            FridaGadgetDialog.showForApk(context, file, fileName);
        } else if (ApkMoreMenuOrder.PATCH_LIBS.equals(actionId)) {
            showLibPatcherDialog(file, fileName);
        } else if (ApkMoreMenuOrder.JIAGU.equals(actionId)) {
            JiaguUnpackDialog.show(context, file);
        } else if (ApkMoreMenuOrder.PRIVACY.equals(actionId)) {
            PrivacyAuditDialog.showForApk(context, file, fileName);
        } else {
            // Check plugin APK actions
            for (ApkMoreAction ext : ExtensionRegistry.apkActions()) {
                if (ext != null && actionId.equals(ext.id())) {
                    try {
                        ext.run(new ApkJob(context, file, fileName, filePath));
                    } catch (Exception ignored) {
                    }
                    return;
                }
            }
            // Check external APK actions
            List<ExternalActions.Entry> externalApk = ExternalActions.apkEntries(context);
            for (ExternalActions.Entry found : externalApk) {
                if (found != null && actionId.equals(found.id)) {
                    try {
                        File stageDir = new File(context.getCacheDir(), "apk_plugin_stage");
                        if (!stageDir.exists()) stageDir.mkdirs();
                        File staged = File.createTempFile("apk-", ".apk", stageDir);
                        try (InputStream in = new FileInputStream(file);
                             FileOutputStream out = new FileOutputStream(staged)) {
                            byte[] buf = new byte[65536];
                            int n;
                            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        }
                        Uri apkUri = FileProvider.getUriForFile(
                                context, context.getPackageName() + ".provider", staged);
                        // Shared builder picks the entry's own public action
                        // (generic hook or legacy APK action) plus plugin/hook/
                        // contract and theme extras. The staged copy URI rides
                        // in data + ClipData; the read+write intent flags carry
                        // the Activity-lifetime grant, so no persistent
                        // grantUriPermission call is made. The plugin rewrites
                        // the same URI in place and the host copies the result.
                        Intent extIntent = ExternalActions.intent(context, found,
                                PluginContracts.ACTION_APK);
                        extIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        extIntent.setClipData(ClipData.newRawUri(
                                "apk_plugin_stage", apkUri));
                        extIntent.putExtra(PluginContracts.EXTRA_APK_NAME,
                                fileName);
                        extIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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
                                            Intent data = result == null ? null : result.getData();
                                            // The plugin result may carry its own
                                            // installed theme; the parent handler
                                            // gates on trust and asks before use.
                                            if (result != null && result.getResultCode() == Activity.RESULT_OK) {
                                                PluginThemeRequests.handle(context, found, data);
                                                String outName = data == null ? null : data.getStringExtra(
                                                        PluginContracts.EXTRA_OUTPUT_NAME);
                                                if (outName == null || outName.isEmpty()
                                                        || outName.length() > 128 || outName.equals(".") || outName.equals("..")
                                                        || outName.matches(".*[\\p{Cntrl}\\p{Cf}].*")
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
                                                msg = boundedPluginMessage(msg);
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
                                                            context.refreshAllPanes();
                                                        }
                                                    });
                                                } else {
                                                    Extensions.showMessage(context, msg);
                                                    context.refreshAllPanes();
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
                                                .setOnCancelListener(d -> { try { staged.delete(); } catch (Exception ignored) {} })
                                                .setNegativeButton(android.R.string.cancel,
                                                        (d, w) -> { try { staged.delete(); } catch (Exception ignored) {} })
                                                .setPositiveButton(android.R.string.ok, (d, w) -> {
                                                    int ch = selectedChoice[0];
                                                    if (ch >= 0 && ch < choiceIds.size()) extIntent.putExtra("method", choiceIds.get(ch));
                                                    extIntent.putExtra("sign", selectedSign[0]);
                                                    context.launchExternalApk(extIntent, runPlugin);
                                                })
                                                .show();
                                    }
                                }, () -> { try { staged.delete(); } catch (Exception ignored) {} });
                    } catch (Exception e2) {
                        try {
                            Extensions.showMessage(context, "Cannot share this APK with the plugin");
                        } catch (Exception ignored) {
                        }
                    }
                    return;
                }
            }
        }
    }


    public void handleApkToJava(File file, String fileName) {
        String baseName = fileName.replaceFirst("\\.[^.]+$", "");
        DexTools.DexPreExtract session = DexTools.preExtractAllDex(context, file, false);
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        pm.setText(context.getString(R.string.loading));

        AppExecutors.io().execute(() -> {
            try {
                session.done.await();
            } catch (InterruptedException e) {
                return;
            }
            context.runOnUiThread(pm::dismiss);
            if (session.error != null) {
                context.runOnUiThread(() -> new ErrorUtil(context).showError(session.error));
                return;
            }
            List<File> dexFiles = new ArrayList<>();
            ArrayList<String> dexPaths = new ArrayList<>();
            try {
                List<String> names = DexTools.listAllDexNames(file);
                for (String name : names) {
                    File f = new File(session.outputDir, name);
                    dexFiles.add(f);
                    dexPaths.add(f.getAbsolutePath());
                }
            } catch (Exception e) {
                context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                return;
            }

            if (dexFiles.isEmpty()) {
                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.no_files_found));
                return;
            }

            context.runOnUiThread(() -> JadxDecompileHelper.promptDecompileAction(
                    context, dexFiles, file, baseName,
                    () -> {
                        DexTools dexTools = new DexTools(context, dialogUtil, pane1, null);
                        dexTools.openDexPlusFiles(dexPaths, null, file, "classes.dex", true);
                    }
            ));
        });
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

                    AppExecutors.io().execute(() -> {
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
                    });
                })
                .setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /** Upper bound for plugin-supplied result text shown to the user. */
    public static final int MAX_PLUGIN_MESSAGE_CHARS = 512;

    /** Plugin-supplied result text is clamped before any UI display. */
    public static String boundedPluginMessage(String message) {
        if (message == null) return "";
        try {
            if (message.length() > MAX_PLUGIN_MESSAGE_CHARS) {
                return message.substring(0, MAX_PLUGIN_MESSAGE_CHARS);
            }
        } catch (Exception ignored) {
        }
        return message;
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    public void showApkInfoDialog(File file, String fileName) {
        showApkInfoDialog(file, fileName, false);
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    public void showApkInfoDialog(File file, String fileName, boolean isApksSplit) {
        String filePath = file.getPath();
        View display = LayoutInflater.from(context).inflate(R.layout.dialog_apk_info, null, false);
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
            AppExecutors.io().execute(() -> {
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
            });
        });
        apkIcon.setImageDrawable(FileIconLoader.getCachedApkIcon());
        apkTitle.setText(R.string.loading);
        apkVersionName.setText(R.string.loading);
        verCode.setText(R.string.loading);
        pkgName.setText(R.string.loading);
        signaturesInApk.setText(R.string.loading);
        protectedDisplay.setText(R.string.loading);


        MaterialAlertDialogBuilder adBuilder = dialogUtil.getDialogBuilder()
                .setView(display);

        AlertDialog ad = adBuilder.create();

        MaterialButton moreBtn = display.findViewById(R.id.apkDialogMore);
        MaterialButton installBtn = display.findViewById(R.id.apkDialogInstall);
        MaterialButton viewBtn = display.findViewById(R.id.apkDialogView);
        moreBtn.setOnClickListener(v -> {
            ad.dismiss();
            showApkMoreDialog(file, fileName, filePath, pane1);
        });
        installBtn.setOnClickListener(v -> {
            ad.dismiss();
            InstallUtil.installApkWithDialog(context, file);
        });
        viewBtn.setOnClickListener(v -> {
            ad.dismiss();
            openZipFile(file);
        });

        dialogUtil.styleAlertDialog(ad);
        LinearLayout rootInfoSection = display.findViewById(R.id.rootInfoSection);
        LinearLayout rootInfoHeader = display.findViewById(R.id.rootInfoHeader);
        ImageView rootInfoChevron = display.findViewById(R.id.rootInfoChevron);
        LinearLayout rootInfoContent = display.findViewById(R.id.rootInfoContent);
        final boolean[] rootInfoLoaded = {false};

        boolean elevatedFileOps = PrivilegeManager.Files.fileOpsOn(context);
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
                AppExecutors.io().execute(() -> {
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
                    String uid = PrivilegeManager.Root.getAppUid(context, resolvedPkg);
                    List<String> apkPaths = PrivilegeManager.Root.getAppApkPaths(context, resolvedPkg);
                    List<String> dataDirs = PrivilegeManager.Root.getAppDataDirs(context, resolvedPkg);
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
                });
            });
        }

        ad.show();
        if (isApksSplit) {
            installBtn.setEnabled(false);
            installBtn.setAlpha(0.4f);
            moreBtn.setEnabled(false);
            moreBtn.setAlpha(0.4f);
        }

        // Basic rows are published first; heavier extractors publish independently.
        // Identity changes (APK replaced while the dialog is open) cancel stale writes.
        final String[] appMeta = new String[]{"", ""}; // [0]: label, [1]: version+code
        final boolean[] sigClickable = {false};
        final ApkInfoLoader loader = new ApkInfoLoader(context, file, new ApkInfoLoader.Listener() {
            @Override
            public void onBasic(ApkInfoLoader.Basic basic) {
                long candVerCode = basic.versionCode;
                // Installed-package state is live and must be read on every open.
                PackageInfo installed = null;
                try {
                    installed = context.getPackageManager().getPackageInfo(basic.packageName, 0);
                } catch (Exception ignored) { }
                final SpannableStringBuilder comparison = new SpannableStringBuilder();
                if (installed == null) {
                    comparison.append(context.getString(R.string.not_installed));
                } else {
                    String instVerName = installed.versionName == null ? "" : installed.versionName;
                    long instVerCode = ApkInfoUtil.getVersionCode(installed);
                    comparison.append(instVerName).append(" (").append(String.valueOf(instVerCode)).append(")");
                    if (instVerCode > candVerCode) {
                        int start = comparison.length();
                        comparison.append(" (").append(context.getString(R.string.downgrade)).append(")");
                        comparison.setSpan(new ForegroundColorSpan(0xFFFF4444), start,
                                comparison.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (candVerCode > instVerCode) {
                        int start = comparison.length();
                        comparison.append(" (").append(context.getString(R.string.upgrade)).append(")");
                        comparison.setSpan(new ForegroundColorSpan(0xFF4CAF50), start,
                                comparison.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                final String finalVerName = (TextUtils.isEmpty(basic.versionName)
                        ? context.getString(R.string.version_name_unset) : basic.versionName)
                        + (basic.versionCode > 0 ? " (" + basic.versionCode + ")" : "");
                final CharSequence finalComparison = comparison;
                appMeta[0] = basic.label;
                appMeta[1] = finalVerName;
                context.handler.post(() -> {
                    if (!ad.isShowing()) return;
                    if (basic.icon != null) apkIcon.setImageDrawable(basic.icon);
                    apkTitle.setText(basic.label);
                    apkVersionName.setText(finalVerName);
                    verCode.setText(basic.versionCode > 0 ? String.valueOf(basic.versionCode) : "—");
                    pkgName.setText(basic.packageName);
                    uiHelper.scrollTextView(pkgName);
                    // Entry count arrives separately; show only the size until
                    // onEntries supplies the real count (avoid a misleading 0).
                    fileSize.setText(Formatter.formatFileSize(context, file.length()));
                    int target = basic.targetSdk;
                    apkTargetSdk.setText(target <= 0 ? context.getString(R.string.unknown_sdk)
                            : ApkInfoUtil.getAndroidVersionName(target));
                    int min = basic.minSdk;
                    apkMinSdk.setText(min < 0 ? context.getString(R.string.unknown_sdk)
                            : ApkInfoUtil.getAndroidVersionName(min));
                    apkInstalled.setText(finalComparison);
                    uiHelper.scrollTextView(apkInstalled);
                });
            }

            @Override
            public void onEntries(int entryCount) {
                final String sizeText = context.getString(R.string.fs_entries,
                        Formatter.formatFileSize(context, file.length()), entryCount);
                context.handler.post(() -> {
                    if (!ad.isShowing()) return;
                    fileSize.setText(sizeText);
                });
            }

            @Override
            public void onSignature(String label, boolean analyzed, String certSha256, boolean clickable) {
                sigClickable[0] = analyzed && clickable;
                context.handler.post(() -> {
                    if (!ad.isShowing()) return;
                    signaturesInApk.setText(analyzed ? label : context.rss.getString(android.R.string.unknownName));
                });
            }

            @Override
            public void onProtection(boolean protectedApk, String summary) {
                context.handler.post(() -> {
                    if (!ad.isShowing()) return;
                    protectedDisplay.setText(protectedApk ? "⚠️ " + summary
                            : context.getString(R.string.protection_not_found));
                    protectedDisplay.setTextColor(protectedApk ? 0xFFFF9800
                            : MaterialColors.getColor(protectedDisplay, android.R.attr.textColorPrimary));
                });
            }

            @Override
            public void onNative(boolean il2cpp, boolean nativeLibs, boolean flutter) {
                // Loader publishes from a pool thread; mirror the other rows and
                // touch dialog/scan state on the main thread only.
                context.handler.post(() -> {
                    if (!ad.isShowing()) return;
                    setNativeScan(file, new boolean[]{il2cpp, nativeLibs, flutter});
                });
            }

            @Override
            public void onInvalid() {
                context.handler.post(() -> {
                    if (!ad.isShowing()) return;
                    ad.dismiss();
                    Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                    context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, context.getContentResolver().getType(uri))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open " + fileName));
                });
            }
        });
        ad.setOnDismissListener(d -> loader.cancel());

        signaturesInApk.setOnClickListener(v -> {
            if (sigClickable[0]) {
                ad.dismiss();
                SignatureInfoDialog.show(context, dialogUtil, file);
            }
        });
        View.OnLongClickListener lcl = v -> {
            if (v instanceof TextView tv) CopyUtil.copyToClipboard(ad, tv.getText());
            return true;
        };
        apkIcon.setOnLongClickListener(v -> {
            exportApkIconZip(file, appMeta[0], appMeta[1]);
            return true;
        });
        signaturesInApk.setOnLongClickListener(lcl);
        protectedDisplay.setOnLongClickListener(lcl);
        pkgName.setOnLongClickListener(lcl);
        apkTitle.setOnLongClickListener(lcl);
        apkVersionName.setOnLongClickListener(lcl);
        verCode.setOnLongClickListener(lcl);
        fileSize.setOnLongClickListener(lcl);
        apkTargetSdk.setOnLongClickListener(lcl);
        apkMinSdk.setOnLongClickListener(lcl);
        apkInstalled.setOnLongClickListener(lcl);
        apkPermissions.setOnLongClickListener(lcl);
        loader.start();
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
                // Mirror root folder navigation from the main file manager:
                // plain File.exists() is false for /data/data on Android even
                // with root, so check via AccessManager (root/Shizuku) first.
                boolean exists = false;
                boolean isFile = false;
                try {
                    exists = PrivilegeManager.Files.exists(context, tapPath)
                            || new File(tapPath).exists();
                } catch (Exception ignored) {}
                if (!exists) {
                    Extensions.showMessage(context, "Path " + tapPath + " not accessible");
                    return;
                }
                try {
                    // PrivilegeManager.Files routes through the active backend
                    // (Shizuku or root) and falls back to plain File checks.
                    isFile = PrivilegeManager.Files.isFile(context, tapPath);
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

    private void exportApkIconZip(File apkFile, String appName, String versionName) {
        AppExecutors.io().execute(() -> {
            try {
                File parentDir = apkFile.getParentFile();
                if (parentDir == null) parentDir = new File(Environment.getExternalStorageDirectory(), "Download");
                String safeName = (TextUtils.isEmpty(appName) ? "App" : appName).replaceAll("[\\\\/:*?\"<>|]", "_");
                String cleanVer = (TextUtils.isEmpty(versionName) ? "1.0" : versionName).replaceAll("[\\\\/:*?\"<>|]", "_");
                int paren = cleanVer.indexOf('(');
                if (paren > 0) cleanVer = cleanVer.substring(0, paren).trim();
                String zipName = safeName + "_" + cleanVer + "_icon.zip";
                File targetZip = new File(parentDir, zipName);

                PackageManager pm = context.getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
                Drawable defaultIcon = null;
                if (pi != null && pi.applicationInfo != null) {
                    pi.applicationInfo.sourceDir = apkFile.getAbsolutePath();
                    pi.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
                    defaultIcon = pi.applicationInfo.loadIcon(pm);
                }

                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
                    // 1. Write root icon.png
                    if (defaultIcon != null) {
                        Bitmap bmp;
                        if (defaultIcon instanceof BitmapDrawable bd && bd.getBitmap() != null) {
                            bmp = bd.getBitmap();
                        } else {
                            int w = Math.max(1, defaultIcon.getIntrinsicWidth());
                            int h = Math.max(1, defaultIcon.getIntrinsicHeight());
                            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(bmp);
                            defaultIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                            defaultIcon.draw(canvas);
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        byte[] rootBytes = baos.toByteArray();
                        ZipEntry ze = new ZipEntry("icon.png");
                        zos.putNextEntry(ze);
                        zos.write(rootBytes);
                        zos.closeEntry();
                    }

                    // 2. Extract launcher icons from inside the APK archive
                    try (ZipFile zf = new ZipFile(apkFile)) {
                        Enumeration<? extends ZipEntry> entries = zf.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if ((name.startsWith("res/mipmap-") || name.startsWith("res/drawable-"))
                                    && (name.contains("ic_launcher") || name.endsWith("icon.png"))
                                    && name.endsWith(".png")) {
                                ZipEntry outEntry = new ZipEntry(name);
                                zos.putNextEntry(outEntry);
                                try (InputStream is = zf.getInputStream(entry)) {
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = is.read(buf)) > 0) zos.write(buf, 0, len);
                                }
                                zos.closeEntry();
                            }
                        }
                    } catch (Exception ignored) {}
                }

                final String finalZipPath = targetZip.getAbsolutePath();
                context.handler.post(() -> {
                    Extensions.showMessage(context, context.getString(R.string.icon_saved, targetZip.getName()));
                    context.refreshPaneAfterChange(pane1, null);
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        });
    }

}
