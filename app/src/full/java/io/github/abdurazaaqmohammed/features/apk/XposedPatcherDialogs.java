package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.IntConsumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.lsposed.lspatch.share.Constants;
import org.lsposed.patch.ApkPatcher;
import org.lsposed.patch.LSPatch;
import org.lsposed.patch.util.Logger;

/**
 * Xposed-patcher UI (integrated mode): patches an APK with Xposed modules
 * embedded so the result runs standalone without a manager app, using the
 * vendored JingMatrix LSPatch fork (org.lsposed.patch). See
 * docs/XPOSED_PATCHER.md.
 *
 * The output is always re-signed with the user's MP-Manager signing key
 * (Sign Settings); the engine's built-in signature only carries the
 * intermediate APK until that happens.
 */
public class XposedPatcherDialogs {

    private static final String PREF_SIGBYPASS = "npatch_sigbypass";
    private static final String PREF_DEBUGGABLE = "npatch_debuggable";
    private static final String PREF_INJECT_DEX = "npatch_injectdex";
    private static final String PREF_CLEARTEXT = "npatch_cleartext";
    private static final String PREF_EXTRACT_LIBS = "npatch_extractlibs";
    private static final String PREF_DOCS_PROVIDER = "npatch_docs_provider";
    private static final String PREF_FAKE_SIGNATURE = "npatch_fake_signature";
    private static final String PREF_GMS_REPLACEMENT = "npatch_gms_replacement";
    private static final String PREF_OVERRIDE_TARGET_SDK = "npatch_override_targetsdk";
    private static final String PREF_TARGET_SDK = "npatch_targetsdk";
    private static final String PREF_ALLOW_DOWN = "npatch_allowdown";
    private static final String PREF_VERSION_CODE = "npatch_versioncode";
    private static final String PREF_LABEL = "npatch_label";

    private static final String BKS_PASSWORD = "npatch-bks";
    private static final String BKS_ALIAS = "mpkey";

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final boolean pane1;

    public XposedPatcherDialogs(MainActivity context, DialogUtil dialogUtil, boolean pane1) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
    }

    private static class Options {
        final List<String> modules = new ArrayList<>();
        int sigBypassLevel = 2;
        boolean debuggable = false;
        boolean injectDex = false;
        boolean usesCleartextTraffic = false;
        boolean extractNativeLibs = false;
        boolean documentsProvider = false;
        boolean fakeSignature = false;
        String gmsReplacement = "";
        boolean overrideTargetSdk = false;
        int targetSdkValue = 28;
        boolean allowDowngrade = false;
        int versionCodeValue = 1;
        String labelOverride = "";
        boolean autoSign = true;
        File userKeyStore;
    }

    public void showDialog(File file, String fileName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        Options options = new Options();
        options.sigBypassLevel = settings.getInt(PREF_SIGBYPASS, 2);
        options.debuggable = settings.getBoolean(PREF_DEBUGGABLE, false);
        options.injectDex = settings.getBoolean(PREF_INJECT_DEX, false);
        options.usesCleartextTraffic = settings.getBoolean(PREF_CLEARTEXT, false);
        options.extractNativeLibs = settings.getBoolean(PREF_EXTRACT_LIBS, false);
        options.documentsProvider = settings.getBoolean(PREF_DOCS_PROVIDER, false);
        options.fakeSignature = settings.getBoolean(PREF_FAKE_SIGNATURE, false);
        options.gmsReplacement = settings.getString(PREF_GMS_REPLACEMENT, "");
        options.overrideTargetSdk = settings.getBoolean(PREF_OVERRIDE_TARGET_SDK, false);
        options.targetSdkValue = settings.getInt(PREF_TARGET_SDK, 28);
        options.allowDowngrade = settings.getBoolean(PREF_ALLOW_DOWN, false);
        options.versionCodeValue = settings.getInt(PREF_VERSION_CODE, 1);
        options.labelOverride = settings.getString(PREF_LABEL, "");
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_xposed_patcher, null, false);

        MaterialButton bypassBtn = dialogView.findViewById(R.id.npatch_sigbypass);
        Runnable updateBypassLabel = () -> {
            String[] names = bypassNames();
            bypassBtn.setText(names[Math.min(options.sigBypassLevel, names.length - 1)]);
        };
        updateBypassLabel.run();

        dialogView.findViewById(R.id.npatch_profiles)
                .setOnClickListener(v -> showProfilesDialog(settings, options, dialogView));

        MaterialButton modulesBtn = dialogView.findViewById(R.id.npatch_modules);
        Runnable updateModulesLabel = () -> modulesBtn.setText(options.modules.isEmpty()
                ? context.getString(R.string.npatch_embed_modules_zero)
                : context.getString(R.string.npatch_embed_modules, options.modules.size()));
        updateModulesLabel.run();
        modulesBtn.setOnClickListener(v -> {
            FilePickerDialog.Properties props = new FilePickerDialog.Properties();
            props.selection_mode = FilePickerDialog.MULTI_MODE;
            props.selection_type = FilePickerDialog.FILE_SELECT;
            props.root = new File(Environment.getExternalStorageDirectory().getPath());
            props.offset = file.getParentFile();
            props.extensions = new String[]{"apk"};
            props.preferenceKey = "npatch_module_pick";
            FilePickerDialog fpd = new FilePickerDialog(context, props);
            fpd.setTitle(context.getString(R.string.npatch_embed_modules_title));
            fpd.setDialogSelectionListener(files -> {
                options.modules.clear();
                if (files != null) options.modules.addAll(Arrays.asList(files));
                updateModulesLabel.run();
            });
            fpd.show();
        });

        bypassBtn.setOnClickListener(v -> {
            String[] names = bypassNames();
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.npatch_sigbypass_title)
                    .setSingleChoiceItems(names, options.sigBypassLevel, (d, which) -> {
                        options.sigBypassLevel = which;
                        bypassBtn.setText(names[which]);
                        persistInt(settings, PREF_SIGBYPASS, which);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        bindSwitch(dialogView, R.id.npatch_debuggable, options.debuggable,
                value -> persist(settings, PREF_DEBUGGABLE, value), val -> options.debuggable = val);
        bindSwitch(dialogView, R.id.npatch_injectdex, options.injectDex,
                value -> persist(settings, PREF_INJECT_DEX, value), val -> options.injectDex = val);
        bindSwitch(dialogView, R.id.npatch_cleartext, options.usesCleartextTraffic,
                value -> persist(settings, PREF_CLEARTEXT, value), val -> options.usesCleartextTraffic = val);
        bindSwitch(dialogView, R.id.npatch_extractlibs, options.extractNativeLibs,
                value -> persist(settings, PREF_EXTRACT_LIBS, value), val -> options.extractNativeLibs = val);
        bindSwitch(dialogView, R.id.npatch_documents_provider, options.documentsProvider,
                value -> persist(settings, PREF_DOCS_PROVIDER, value), val -> options.documentsProvider = val);
        bindSwitch(dialogView, R.id.npatch_fake_signature, options.fakeSignature,
                value -> persist(settings, PREF_FAKE_SIGNATURE, value), val -> options.fakeSignature = val);

        EditText gmsInput = dialogView.findViewById(R.id.npatch_gms_replacement);
        gmsInput.setText(options.gmsReplacement);
        gmsInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            protected void onChanged(CharSequence s) {
                options.gmsReplacement = s.toString();
                settings.edit().putString(PREF_GMS_REPLACEMENT, s.toString()).apply();
            }
        });

        MaterialSwitch targetSdkSwitch = dialogView.findViewById(R.id.npatch_override_targetsdk);
        EditText targetSdkInput = dialogView.findViewById(R.id.npatch_targetsdk);
        targetSdkSwitch.setChecked(options.overrideTargetSdk);
        targetSdkInput.setText(String.valueOf(options.targetSdkValue));
        targetSdkSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
            options.overrideTargetSdk = isChecked;
            persist(settings, PREF_OVERRIDE_TARGET_SDK, isChecked);
        });
        targetSdkInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            protected void onChanged(CharSequence s) {
                parseInt(settings, s, PREF_TARGET_SDK, i -> options.targetSdkValue = i);
            }
        });

        MaterialSwitch downSwitch = dialogView.findViewById(R.id.npatch_allowdown);
        EditText versionCodeInput = dialogView.findViewById(R.id.npatch_versioncode);
        downSwitch.setChecked(options.allowDowngrade);
        versionCodeInput.setText(String.valueOf(options.versionCodeValue));
        downSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
            options.allowDowngrade = isChecked;
            persist(settings, PREF_ALLOW_DOWN, isChecked);
        });
        versionCodeInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            protected void onChanged(CharSequence s) {
                parseInt(settings, s, PREF_VERSION_CODE, i -> options.versionCodeValue = i);
            }
        });

        EditText labelInput = dialogView.findViewById(R.id.npatch_label);
        labelInput.setText(options.labelOverride);
        labelInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            protected void onChanged(CharSequence s) {
                options.labelOverride = s.toString();
                settings.edit().putString(PREF_LABEL, s.toString()).apply();
            }
        });

        // Same Auto Sign pattern as the Optimize dialog: when checked the output
        // is re-signed with the user's key (layout-preserving, see SignWrapper);
        // when unchecked the engine's built-in signature is kept.
        CheckBox autoSignBox = dialogView.findViewById(R.id.autosign);
        autoSignBox.setChecked(options.autoSign = settings.getBoolean("autosign", true));
        autoSignBox.setOnCheckedChangeListener((bv, isChecked) -> {
            options.autoSign = isChecked;
            settings.edit().putBoolean("autosign", isChecked).apply();
        });
        dialogView.findViewById(R.id.sign_settings)
                .setOnClickListener(context.uiHelper.showSignSettingsDialog());

        dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.npatch_title, fileName))
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.npatch_patch, (d, w) -> {
                    for (String module : options.modules) {
                        if (!new File(module).isFile()) {
                            Extensions.showMessage(context, context.getString(R.string.npatch_module_missing, module));
                            return;
                        }
                    }
                    startPatch(file, options);
                })
                .show();
    }

    /** A named snapshot of every patch option, persisted as JSON in prefs. */
    public static class Profile {
        List<String> modules = new ArrayList<>();
        int sigBypassLevel = 2;
        boolean debuggable;
        boolean injectDex;
        boolean usesCleartextTraffic;
        boolean extractNativeLibs;
        boolean documentsProvider;
        boolean fakeSignature;
        String gmsReplacement = "";
        boolean overrideTargetSdk;
        int targetSdkValue = 28;
        boolean allowDowngrade;
        int versionCodeValue = 1;
        String labelOverride = "";

        static Profile of(Options o) {
            Profile p = new Profile();
            p.modules = new ArrayList<>(o.modules);
            p.sigBypassLevel = o.sigBypassLevel;
            p.debuggable = o.debuggable;
            p.injectDex = o.injectDex;
            p.usesCleartextTraffic = o.usesCleartextTraffic;
            p.extractNativeLibs = o.extractNativeLibs;
            p.documentsProvider = o.documentsProvider;
            p.fakeSignature = o.fakeSignature;
            p.gmsReplacement = o.gmsReplacement;
            p.overrideTargetSdk = o.overrideTargetSdk;
            p.targetSdkValue = o.targetSdkValue;
            p.allowDowngrade = o.allowDowngrade;
            p.versionCodeValue = o.versionCodeValue;
            p.labelOverride = o.labelOverride;
            return p;
        }

        void into(Options o) {
            o.modules.clear();
            o.modules.addAll(modules);
            o.sigBypassLevel = sigBypassLevel;
            o.debuggable = debuggable;
            o.injectDex = injectDex;
            o.usesCleartextTraffic = usesCleartextTraffic;
            o.extractNativeLibs = extractNativeLibs;
            o.documentsProvider = documentsProvider;
            o.fakeSignature = fakeSignature;
            o.gmsReplacement = gmsReplacement;
            o.overrideTargetSdk = overrideTargetSdk;
            o.targetSdkValue = targetSdkValue;
            o.allowDowngrade = allowDowngrade;
            o.versionCodeValue = versionCodeValue;
            o.labelOverride = labelOverride;
        }
    }

    private static final String PREF_PROFILES = "npatch_profiles";
    private static final Type PROFILE_MAP_TYPE =
            new TypeToken<LinkedHashMap<String, Profile>>() { }.getType();

    private static Map<String, Profile> loadProfiles(SharedPreferences settings) {
        String json = settings.getString(PREF_PROFILES, null);
        if (TextUtils.isEmpty(json)) return new LinkedHashMap<>();
        try {
            Map<String, Profile> map = new Gson().fromJson(json, PROFILE_MAP_TYPE);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void saveProfiles(SharedPreferences settings, Map<String, Profile> profiles) {
        settings.edit().putString(PREF_PROFILES, new Gson().toJson(profiles)).apply();
    }

    /**
     * Lists saved profiles (tap = load, long-press = delete) and lets the user
     * save the current options - modules and everything else - under a name.
     */
    private void showProfilesDialog(SharedPreferences settings, Options options, View dialogView) {
        Map<String, Profile> profiles = loadProfiles(settings);
        List<String> names = new ArrayList<>(profiles.keySet());

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        TextView empty = new TextView(context);
        empty.setText(R.string.npatch_profile_none);
        empty.setTextSize(12);
        empty.setVisibility(names.isEmpty() ? View.VISIBLE : View.GONE);
        box.addView(empty);

        ListView list = new ListView(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, names);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            String name = names.get(position);
            profiles.get(name).into(options);
            applyLoadedProfile(dialogView, options);
            Extensions.showMessage(context, context.getString(R.string.npatch_profile_loaded, name));
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            String name = names.get(position);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(name)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        profiles.remove(name);
                        saveProfiles(settings, profiles);
                        names.remove(position);
                        adapter.notifyDataSetChanged();
                        empty.setVisibility(names.isEmpty() ? View.VISIBLE : View.GONE);
                        Extensions.showMessage(context, context.getString(R.string.npatch_profile_deleted, name));
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        box.addView(list);

        dialogUtil.getDialogBuilder()
                .setTitle(R.string.npatch_profiles)
                .setView(box)
                .setNeutralButton(R.string.npatch_profile_save, (d, w) ->
                        promptSaveProfile(settings, options, profiles))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptSaveProfile(SharedPreferences settings, Options options,
                                   Map<String, Profile> profiles) {
        EditText nameInput = new EditText(context);
        nameInput.setSingleLine(true);
        nameInput.setHint(R.string.npatch_profile_name);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        nameInput.setPadding(pad, pad / 2, pad, 0);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.npatch_profile_save)
                .setView(nameInput)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) return;
                    profiles.put(name, Profile.of(options));
                    saveProfiles(settings, profiles);
                    Extensions.showMessage(context, context.getString(R.string.npatch_profile_saved, name));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Pushes a loaded profile back into the dialog widgets. The widgets'
     * listeners write the loaded values into {@code options} as they fire, so
     * {@code options} ends up mirroring the profile.
     */
    private void applyLoadedProfile(View dialogView, Options options) {
        bindSwitchSilently(dialogView, R.id.npatch_debuggable, options.debuggable);
        bindSwitchSilently(dialogView, R.id.npatch_injectdex, options.injectDex);
        bindSwitchSilently(dialogView, R.id.npatch_cleartext, options.usesCleartextTraffic);
        bindSwitchSilently(dialogView, R.id.npatch_extractlibs, options.extractNativeLibs);
        bindSwitchSilently(dialogView, R.id.npatch_documents_provider, options.documentsProvider);
        bindSwitchSilently(dialogView, R.id.npatch_fake_signature, options.fakeSignature);
        bindSwitchSilently(dialogView, R.id.npatch_override_targetsdk, options.overrideTargetSdk);
        bindSwitchSilently(dialogView, R.id.npatch_allowdown, options.allowDowngrade);
        ((EditText) dialogView.findViewById(R.id.npatch_targetsdk)).setText(String.valueOf(options.targetSdkValue));
        ((EditText) dialogView.findViewById(R.id.npatch_versioncode)).setText(String.valueOf(options.versionCodeValue));
        ((EditText) dialogView.findViewById(R.id.npatch_gms_replacement)).setText(options.gmsReplacement);
        ((EditText) dialogView.findViewById(R.id.npatch_label)).setText(options.labelOverride);
        updateBypassLabel(dialogView, options);
    }

    private void updateBypassLabel(View dialogView, Options options) {
        MaterialButton bypassBtn = dialogView.findViewById(R.id.npatch_sigbypass);
        String[] names = bypassNames();
        bypassBtn.setText(names[Math.min(options.sigBypassLevel, names.length - 1)]);
    }

    private String[] bypassNames() {
        return new String[]{
                context.getString(R.string.npatch_bypass_lsp_0),
                context.getString(R.string.npatch_bypass_lsp_1),
                context.getString(R.string.npatch_bypass_lsp_2),
                context.getString(R.string.npatch_bypass_lsp_3)};
    }

    private interface SwitchSetter {
        void set(boolean value);
    }

    private interface SwitchPersist {
        void persist(boolean value);
    }

    private void bindSwitch(View root, int id, boolean initial, SwitchPersist persist, SwitchSetter setter) {
        MaterialSwitch sw = root.findViewById(id);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((bv, isChecked) -> {
            setter.set(isChecked);
            persist.persist(isChecked);
        });
    }

    private void bindSwitchSilently(View root, int id, boolean value) {
        MaterialSwitch sw = root.findViewById(id);
        sw.setChecked(value);
    }

    private void persist(SharedPreferences settings, String key, boolean value) {
        settings.edit().putBoolean(key, value).apply();
    }

    private void persistInt(SharedPreferences settings, String key, int value) {
        settings.edit().putInt(key, value).apply();
    }

    private void parseInt(SharedPreferences settings, CharSequence s, String key, IntConsumer setter) {
        try {
            int value = Integer.parseInt(s.toString());
            setter.accept(value);
            persistInt(settings, key, value);
        } catch (NumberFormatException ignored) {
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged(s);
        }
        @Override public void afterTextChanged(Editable s) { }
        protected abstract void onChanged(CharSequence s);
    }

    private void startPatch(File file, Options options) {
        ProgressManager pm = new ProgressManager(context, true).show();
        LsBridgeLogger logger = new LsBridgeLogger(pm);
        final File[] resultHolder = new File[1];
        Runnable finish = () -> {
            File produced = resultHolder[0];
            context.handler.post(() -> {
                Extensions.showMessage(context, context.getString(R.string.npatch_done, produced.getName()));
                context.loadFolderInPane(file.getParentFile(), pane1, false);
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.npatch_done_title)
                        .setMessage(context.getString(R.string.npatch_install_prompt, produced.getName()))
                        .setPositiveButton(R.string.install, (d2, w2) ->
                                InstallUtil.installApkWithDialog(context, produced))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        };
        if (options.autoSign) {
            // Resolve (and possibly password-prompt) the user's key up front on
            // the UI thread, then hand it to the engine (-k). The engine signs
            // its data-multiplexed output in place with the user's key; a
            // post-hoc apksig pass would re-serialize the zip and materialize
            // the entry links, roughly doubling the file size.
            SignWrapper.requireAuth(context, sw -> new Thread(() -> {
                try {
                    exportUserKeys(sw, options);
                    File produced = runLSPatch(file, options, logger);
                    resultHolder[0] = produced;
                    pm.dismiss();
                    finish.run();
                } catch (Throwable t) {
                    logger.e(rootCauseMessage(t));
                    try {
                        PrintWriter pw = new PrintWriter(
                                new File(context.getCacheDir(), "xposed-patcher-last-error.txt"), "UTF-8");
                        pw.print(rootCauseMessage(t));
                        for (Throwable c = t; c != null; c = c.getCause()) {
                            for (StackTraceElement el : c.getStackTrace()) pw.println("    at " + el);
                        }
                        pw.close();
                    } catch (Exception ignored) {
                    }
                    pm.dismiss();
                    context.handler.post(() -> new ErrorUtil(context).showError(rootCauseMessage(t)));
                }
            }, "XposedPatcher").start());
        } else {
            new Thread(() -> {
                try {
                    File produced = runLSPatch(file, options, logger);
                    resultHolder[0] = produced;
                    pm.dismiss();
                    finish.run();
                } catch (Throwable t) {
                    logger.e(t.getMessage() == null ? t.toString() : t.getMessage());
                    pm.dismiss();
                    context.handler.post(() -> new ErrorUtil(context).showError(t));
                }
            }, "XposedPatcher").start();
        }
    }

    /**
     * Writes the user's configured signing key into an engine-readable
     * keystore in the app-private cache (deleted on exit). LSPatch reads
     * KeyStore.getDefaultType(), which is JKS on some Android versions and
     * BKS on others; if that type cannot hold the key, the BKS format is
     * produced instead.
     */
    private void exportUserKeys(SignWrapper sw, Options options) throws Exception {
        String defaultType = KeyStore.getDefaultType();
        File keystore = new File(context.getCacheDir(), "xposed-user-key."
                + defaultType.toLowerCase(Locale.ROOT));
        try {
            sw.exportKey(keystore, defaultType, BKS_PASSWORD, BKS_ALIAS);
        } catch (Exception e) {
            keystore = new File(context.getCacheDir(), "xposed-user-key.bks");
            sw.exportKey(keystore, "BKS", BKS_PASSWORD, BKS_ALIAS);
        }
        options.userKeyStore = keystore;
        keystore.deleteOnExit();
    }

    private static String rootCauseMessage(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        Throwable c = t.getCause();
        int depth = 0;
        while (c != null && depth < 5) {
            sb.append("\\n  caused by: ").append(c);
            c = c.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * Runs the vendored LSPatch (JingMatrix) engine via its CLI front end and
     * ApkPatcher. Parse errors throw ParameterException, and the engine raises
     * PatchException with an actionable message. Its built-in keystore
     * signature is likewise replaced right after by the caller's Auto Sign key.
     */
    private File runLSPatch(File srcApk, Options options, Logger logger) throws Exception {
        File outputDir = outputDir(srcApk);
        final String baseName = baseName(srcApk);
        List<String> args = new ArrayList<>();
        args.add("-o");
        args.add(outputDir.getAbsolutePath());
        args.add("--force");
        if (options.debuggable) args.add("-d");
        args.add("-l");
        args.add(String.valueOf(options.sigBypassLevel));
        if (options.injectDex) args.add("--injectdex");
        if (options.allowDowngrade) {
            args.add("--version-code");
            args.add(String.valueOf(options.versionCodeValue));
        }
        if (options.overrideTargetSdk) {
            args.add("--target-sdk");
            args.add(String.valueOf(options.targetSdkValue));
        }
        for (String module : options.modules) {
            args.add("-m");
            args.add(module);
        }
        if (options.usesCleartextTraffic) args.add("--cleartext");
        if (options.extractNativeLibs) args.add("--extract-libs");
        if (options.documentsProvider) args.add("--documents-provider");
        if (options.fakeSignature) args.add("--fake-signature");
        if (!TextUtils.isEmpty(options.gmsReplacement.trim())) {
            args.add("--gms-replacement");
            args.add(options.gmsReplacement.trim());
        }
        if (!TextUtils.isEmpty(options.labelOverride.trim())) {
            args.add("--name");
            args.add(options.labelOverride.trim());
        }
        if (options.userKeyStore != null) {
            args.add("-k");
            args.add(options.userKeyStore.getAbsolutePath());
            args.add(BKS_PASSWORD);
            args.add(BKS_ALIAS);
            args.add(BKS_PASSWORD);
        }
        args.add(srcApk.getAbsolutePath());

        LSPatch cli = new LSPatch(args.toArray(new String[0]));
        new ApkPatcher(logger, cli.toSpec()).patch();
        return normalizeOutput(outputDir, baseName, Constants.PATCH_FILE_SUFFIX);
    }

    private File outputDir(File srcApk) {
        return srcApk.getParentFile() != null ? srcApk.getParentFile() : context.getCacheDir();
    }

    private static String baseName(File srcApk) {
        String base = srcApk.getName();
        if (base.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            base = base.substring(0, base.length() - 4);
        }
        return base;
    }

    /**
     * The engine writes {@code <base>-<verCode><suffix>} into the output dir;
     * pick the newest and normalize to {@code <base><suffix>} (with _2, _3…
     * dedup suffixes).
     */
    private static File normalizeOutput(File outputDir, String baseName, String suffix)
            throws Exception {
        File[] candidates = outputDir.listFiles((dir, name) ->
                name.startsWith(baseName) && name.endsWith(suffix));
        File produced = null;
        if (candidates != null) {
            long best = -1;
            for (File c : candidates) {
                if (c.lastModified() > best) {
                    best = c.lastModified();
                    produced = c;
                }
            }
        }
        if (produced == null) {
            throw new IllegalStateException("Patcher produced no output APK");
        }
        File renamed = new File(outputDir, baseName + suffix);
        if (renamed.exists()) {
            int i = 1;
            while (renamed.exists()) {
                renamed = new File(outputDir, baseName + "_" + (++i) + suffix);
            }
        }
        if (!renamed.equals(produced) && !produced.renameTo(renamed)) {
            Files.copy(produced.toPath(), renamed.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            produced.delete();
        }
        return renamed;
    }

    /** Bridges LSPatch's Logger to the progress dialog. */
    private static class LsBridgeLogger extends Logger {
        final ProgressManager pm;

        LsBridgeLogger(ProgressManager pm) {
            this.pm = pm;
        }

        @Override
        public void d(String msg) {
            if (verbose) pm.setText(msg);
        }

        @Override
        public void i(String msg) {
            pm.setText(msg);
        }

        @Override
        public void e(String msg) {
            pm.setText(msg);
        }
    }
}
