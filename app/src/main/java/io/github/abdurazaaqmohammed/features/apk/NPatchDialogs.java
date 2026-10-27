package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import top.nkbe.npatch.patch.NPatch;
import top.nkbe.npatch.patch.util.Logger;

/**
 * NPatch (integrated mode) UI: patches an APK with Xposed modules embedded
 * (NPatch's "--embed" mode), so the result runs standalone without NPatch
 * Manager. The patching engine and loader runtime are vendored from the
 * NPatch source tree — see docs/NPATCH_INTEGRATION.md.
 */
public class NPatchDialogs {

    private static final String PREF_SIGBYPASS = "npatch_sigbypass";
    private static final String PREF_DEBUGGABLE = "npatch_debuggable";
    private static final String PREF_INJECT_DEX = "npatch_injectdex";
    private static final String PREF_MICROG = "npatch_microg";
    private static final String PREF_HIDELIBS = "npatch_hidelibs";
    private static final String PREF_CLEARTEXT = "npatch_cleartext";
    private static final String PREF_PROVIDER = "npatch_provider";
    private static final String PREF_EXTRACT_LIBS = "npatch_extractlibs";
    private static final String PREF_OVERRIDE_TARGET_SDK = "npatch_override_targetsdk";
    private static final String PREF_TARGET_SDK = "npatch_targetsdk";
    private static final String PREF_ALLOW_DOWN = "npatch_allowdown";
    private static final String PREF_VERSION_CODE = "npatch_versioncode";
    private static final String PREF_LABEL = "npatch_label";
    private static final String PREF_KEYSTORE = "npatch_keystore";
    private static final String PREF_MODULES = "npatch_modules";

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final boolean pane1;

    public NPatchDialogs(MainActivity context, DialogUtil dialogUtil, boolean pane1) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
    }

    private static class Options {
        final List<String> modules = new ArrayList<>();
        int sigBypassLevel = 2;      // integrated mode caps at Constants.SIGBYPASS_HIGH
        boolean debuggable = false;
        boolean injectDex = false;
        boolean useMicroG = false;
        boolean hideLibs = false;
        boolean usesCleartextTraffic = false;
        boolean injectProvider = false;
        boolean extractNativeLibs = false;
        boolean overrideTargetSdk = false;
        int targetSdkValue = 28;
        boolean allowDowngrade = false;
        int versionCodeValue = 1;
        String labelOverride = "";
        int keystore = 0;            // 0 = NPatch built-in, 1 = FPA
        boolean autoSign = false;    // re-sign with the user's key (MP-Manager Sign settings)
    }

    public void showNPatchDialog(File file, String fileName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        Options options = new Options();
        options.sigBypassLevel = settings.getInt(PREF_SIGBYPASS, 2);
        options.debuggable = settings.getBoolean(PREF_DEBUGGABLE, false);
        options.injectDex = settings.getBoolean(PREF_INJECT_DEX, false);
        options.useMicroG = settings.getBoolean(PREF_MICROG, false);
        options.hideLibs = settings.getBoolean(PREF_HIDELIBS, false);
        options.usesCleartextTraffic = settings.getBoolean(PREF_CLEARTEXT, false);
        options.injectProvider = settings.getBoolean(PREF_PROVIDER, false);
        options.extractNativeLibs = settings.getBoolean(PREF_EXTRACT_LIBS, false);
        options.overrideTargetSdk = settings.getBoolean(PREF_OVERRIDE_TARGET_SDK, false);
        options.targetSdkValue = settings.getInt(PREF_TARGET_SDK, 28);
        options.allowDowngrade = settings.getBoolean(PREF_ALLOW_DOWN, false);
        options.versionCodeValue = settings.getInt(PREF_VERSION_CODE, 1);
        options.labelOverride = settings.getString(PREF_LABEL, "");
        options.keystore = settings.getInt(PREF_KEYSTORE, 0);
        String savedModules = settings.getString(PREF_MODULES, null);
        if (savedModules != null && !savedModules.isEmpty()) {
            for (String path : savedModules.split("\n")) {
                if (!TextUtils.isEmpty(path)) options.modules.add(path);
            }
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_npatch, null, false);

        MaterialButton modulesBtn = dialogView.findViewById(R.id.npatch_modules);
        modulesBtn.setText(options.modules.isEmpty()
                ? context.getString(R.string.npatch_embed_modules_zero)
                : context.getString(R.string.npatch_embed_modules, options.modules.size()));
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
                settings.edit().putString(PREF_MODULES, TextUtils.join("\n", options.modules)).apply();
                modulesBtn.setText(options.modules.isEmpty()
                        ? context.getString(R.string.npatch_embed_modules_zero)
                        : context.getString(R.string.npatch_embed_modules, options.modules.size()));
            });
            fpd.show();
        });

        String[] bypassNames = {
                context.getString(R.string.npatch_sigbypass_0),
                context.getString(R.string.npatch_sigbypass_1),
                context.getString(R.string.npatch_sigbypass_2)};
        MaterialButton bypassBtn = dialogView.findViewById(R.id.npatch_sigbypass);
        bypassBtn.setText(bypassNames[Math.min(options.sigBypassLevel, 2)]);
        bypassBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.npatch_sigbypass_title)
                .setSingleChoiceItems(bypassNames, options.sigBypassLevel, (d, which) -> {
                    options.sigBypassLevel = which;
                    bypassBtn.setText(bypassNames[which]);
                    settings.edit().putInt(PREF_SIGBYPASS, which).apply();
                    if (which == 0) {
                        options.hideLibs = false;
                        ((MaterialSwitch) dialogView.findViewById(R.id.npatch_hidelibs)).setChecked(false);
                        settings.edit().putBoolean(PREF_HIDELIBS, false).apply();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        String[] keystoreNames = {
                context.getString(R.string.npatch_keystore_npatch),
                context.getString(R.string.npatch_keystore_fpa)};
        MaterialButton keystoreBtn = dialogView.findViewById(R.id.npatch_keystore);
        keystoreBtn.setText(keystoreNames[Math.min(options.keystore, 1)]);
        keystoreBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.npatch_keystore_title)
                .setSingleChoiceItems(keystoreNames, options.keystore, (d, which) -> {
                    options.keystore = which;
                    keystoreBtn.setText(keystoreNames[which]);
                    settings.edit().putInt(PREF_KEYSTORE, which).apply();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        bindSwitch(dialogView, R.id.npatch_debuggable, options.debuggable,
                value -> persist(settings, PREF_DEBUGGABLE, value), val -> options.debuggable = val);
        bindSwitch(dialogView, R.id.npatch_injectdex, options.injectDex,
                value -> persist(settings, PREF_INJECT_DEX, value), val -> options.injectDex = val);
        bindSwitch(dialogView, R.id.npatch_microg, options.useMicroG,
                value -> persist(settings, PREF_MICROG, value), val -> options.useMicroG = val);
        bindSwitch(dialogView, R.id.npatch_hidelibs, options.hideLibs && options.sigBypassLevel > 0,
                value -> persist(settings, PREF_HIDELIBS, value), val -> options.hideLibs = val);
        bindSwitch(dialogView, R.id.npatch_cleartext, options.usesCleartextTraffic,
                value -> persist(settings, PREF_CLEARTEXT, value), val -> options.usesCleartextTraffic = val);
        bindSwitch(dialogView, R.id.npatch_provider, options.injectProvider,
                value -> persist(settings, PREF_PROVIDER, value), val -> options.injectProvider = val);
        bindSwitch(dialogView, R.id.npatch_extractlibs, options.extractNativeLibs,
                value -> persist(settings, PREF_EXTRACT_LIBS, value), val -> options.extractNativeLibs = val);

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
                parse(settings, s, PREF_TARGET_SDK, i -> options.targetSdkValue = i);
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
                parse(settings, s, PREF_VERSION_CODE, i -> options.versionCodeValue = i);
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

        // Same Auto Sign pattern as the Optimize dialog: users can pick their own
        // signature via Sign Settings; when enabled the patched APK is re-signed
        // with that key after NPatch's built-in signing.
        CheckBox autoSign = dialogView.findViewById(R.id.autosign);
        autoSign.setChecked(options.autoSign = settings.getBoolean("autosign", true));
        autoSign.setOnCheckedChangeListener((buttonView, isChecked) ->
                options.autoSign = isChecked);
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

    private void persist(SharedPreferences settings, String key, boolean value) {
        settings.edit().putBoolean(key, value).apply();
    }

    private void parse(SharedPreferences settings, CharSequence s, String key, java.util.function.IntConsumer setter) {
        try {
            int value = Integer.parseInt(s.toString());
            setter.accept(value);
            settings.edit().putInt(key, value).apply();
        } catch (NumberFormatException ignored) {
        }
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged(s);
        }
        @Override public void afterTextChanged(android.text.Editable s) { }
        protected abstract void onChanged(CharSequence s);
    }

    private void startPatch(File file, Options options) {
        ProgressManager pm = new ProgressManager(context, true).show();
        ProgressLogger logger = new ProgressLogger(pm);
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
        // autoSign wraps the NPatch-signed output with the user's own key, so it
        // can install alongside/update an existing install signed by them.
        // requireAuth runs on the UI thread (it may prompt for the key password),
        // then the patch + re-sign sequence continues on a worker thread.
        if (options.autoSign) {
            SignWrapper.requireAuth(context, sw -> new Thread(() -> {
                try {
                    File produced = runNPatch(file, options, logger);
                    logger.i(context.getString(R.string.npatch_autosign_running));
                    sw.signApk(produced);
                    resultHolder[0] = produced;
                    pm.dismiss();
                    finish.run();
                } catch (Throwable t) {
                    logger.e(t.getMessage() == null ? t.toString() : t.getMessage());
                    pm.dismiss();
                    context.handler.post(() -> new ErrorUtil(context).showError(t));
                }
            }, "NPatch").start());
            return;
        }
        new Thread(() -> {
            try {
                File produced = runNPatch(file, options, logger);
                resultHolder[0] = produced;
                pm.dismiss();
                finish.run();
            } catch (Throwable t) {
                logger.e(t.getMessage() == null ? t.toString() : t.getMessage());
                pm.dismiss();
                context.handler.post(() -> new ErrorUtil(context).showError(t));
            }
        }, "NPatch").start();
    }

    /**
     * Runs the vendored NPatch engine in integrated mode (--embed), mirroring the
     * argument construction of NPatch Manager's Patcher.Options.toStringArray()
     * with useManager=false. The engine signs the output with the chosen built-in
     * keystore (V2+V3), so patching works without any extra signing step.
     */
    private File runNPatch(File srcApk, Options options, Logger logger) throws Exception {
        File outputDir = srcApk.getParentFile() != null ? srcApk.getParentFile()
                : context.getCacheDir();
        String base = srcApk.getName();
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            base = base.substring(0, base.length() - 4);
        }
        final String baseName = base;
        List<String> args = new ArrayList<>();
        args.add("-o");
        args.add(outputDir.getAbsolutePath());
        args.add("--force");
        if (options.debuggable) args.add("-d");
        args.add("-l");
        args.add(String.valueOf(options.sigBypassLevel));
        if (options.injectDex) args.add("--injectdex");
        if (options.allowDowngrade) {
            args.add("--allowdown");
            args.add("--versioncode");
            args.add(String.valueOf(options.versionCodeValue));
        }
        if (options.overrideTargetSdk) {
            args.add("--override-target-sdk");
            args.add("--target-sdk");
            args.add(String.valueOf(options.targetSdkValue));
        }
        for (String module : options.modules) {
            args.add("-m");
            args.add(module);
        }
        if (options.injectProvider) args.add("--provider");
        if (options.useMicroG) args.add("--useMicroG");
        if (options.hideLibs && options.sigBypassLevel > 0) args.add("--hidelibs");
        if (options.usesCleartextTraffic) args.add("--cleartext");
        if (!TextUtils.isEmpty(options.labelOverride.trim())) {
            args.add("--name");
            args.add(options.labelOverride.trim());
        }
        if (options.extractNativeLibs) args.add("--extract-libs");
        if (options.keystore == 1) args.add("-fpa");
        else args.add("-npa");
        args.add(srcApk.getAbsolutePath());

        new NPatch(logger, args.toArray(new String[0])).doCommandLine();

        // The engine writes <base>-<verCode>-npatched.apk into the output dir;
        // pick it up and normalize the name to MP-Manager's <base>-npatched.apk.
        File[] candidates = outputDir.listFiles((dir, name) ->
                name.startsWith(baseName) && name.endsWith(top.nkbe.npatch.share.Constants.PATCH_FILE_SUFFIX));
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
            throw new IllegalStateException(context.getString(R.string.npatch_no_output));
        }
        File renamed = new File(outputDir, baseName + top.nkbe.npatch.share.Constants.PATCH_FILE_SUFFIX);
        if (renamed.exists()) {
            int i = 1;
            while (renamed.exists()) {
                renamed = new File(outputDir, baseName + "_" + (++i) + top.nkbe.npatch.share.Constants.PATCH_FILE_SUFFIX);
            }
        }
        if (!renamed.equals(produced) && !produced.renameTo(renamed)) {
            java.nio.file.Files.copy(produced.toPath(), renamed.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Files.copy overwrote, so drop the engine's original name.
            produced.delete();
        }
        return renamed;
    }

    /** Bridges NPatch's Logger to the progress dialog text. */
    private static class ProgressLogger extends Logger {
        private final ProgressManager pm;

        ProgressLogger(ProgressManager pm) {
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
