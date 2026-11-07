package io.github.abdurazaaqmohammed.features.apk.frida;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.Il2CppDumperUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * "Frida Gadget" APK patching dialog (frida-hook-engine Path B): pick the
 * injection point and ABIs, configure the 21 hook modules, and rebuild the
 * APK with the embedded engine (downloaded on first use) plus a generated
 * libfrida-gadget.config.so, then optionally re-sign.
 */
public final class FridaGadgetDialog {

    private FridaGadgetDialog() {
    }

    public static void showForApk(MainActivity context, File apkFile, String fileName) {
        if (apkFile == null || !apkFile.isFile()) return;

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText("Analyzing APK…");
        new Thread(() -> {
            List<String> abis = new ArrayList<>();
            List<Il2CppDumperUtil.Target> targets = new ArrayList<>();
            try {
                abis = FridaGadgetPatcher.gadgetAbis(apkFile);
                targets = Il2CppDumperUtil.listTargets(context, apkFile);
            } catch (Exception ignored) {
            }
            List<String> fAbis = abis;
            List<Il2CppDumperUtil.Target> fTargets = targets;
            context.runOnUiThread(() -> {
                pm.dismiss();
                if (fAbis.isEmpty()) {
                    Extensions.showMessage(context, context.getString(
                            R.string.frida_no_abi));
                    return;
                }
                displayDialog(context, apkFile, fAbis, fTargets);
            });
        }).start();
    }

    private static void displayDialog(MainActivity context, File apkFile,
                                      List<String> abis, List<Il2CppDumperUtil.Target> targets) {
        View ll = LayoutInflater.from(context).inflate(R.layout.dialog_frida_gadget, null);
        MaterialAutoCompleteTextView targetView = ll.findViewById(R.id.frida_target);
        TextInputLayout targetLayout = ll.findViewById(R.id.frida_target_layout);
        LinearLayout abiBox = ll.findViewById(R.id.frida_abis);
        View modulesRow = ll.findViewById(R.id.frida_modules_row);
        TextView modulesSummary = ll.findViewById(R.id.frida_modules_summary);
        MaterialSwitch debugLog = ll.findViewById(R.id.frida_debug_log);
        MaterialCheckBox autosign = ll.findViewById(R.id.autosign);

        android.content.SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(context);
        FridaConfig config = FridaConfig.load(context);
        final boolean[] sign = new boolean[1];
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
        ll.findViewById(R.id.sign_settings).setOnClickListener(v -> {
            // Reuse the shared signing settings entry point.
            new UIHelper(context).showSignSettingsDialog().onClick(v);
        });

        Runnable updateSummary = () -> modulesSummary.setText(context.getString(
                R.string.frida_modules_summary, config.enabledModuleCount(),
                FridaConfig.MODULES.length));
        updateSummary.run();
        modulesRow.setOnClickListener(v -> FridaModuleDialog.show(context, config, updateSummary));

        debugLog.setChecked(config.getBoolean(FridaConfig.DEBUG_LOG_KEY, true));
        debugLog.setOnCheckedChangeListener((bv, checked) -> {
            config.putBoolean(FridaConfig.DEBUG_LOG_KEY, checked);
            config.save(context);
        });

        final List<Il2CppDumperUtil.Target> finalTargets = targets;
        final String[] target = {null};
        if (finalTargets.isEmpty()) {
            targetView.setText(context.getString(R.string.frida_target_none), false);
            targetLayout.setEnabled(false);
        } else {
            List<String> labels = new ArrayList<>();
            for (Il2CppDumperUtil.Target t : finalTargets) {
                labels.add(targetLabel(context, t));
            }
            targetView.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line, labels));
            String remembered = settings.getString("frida_target", null);
            int index = 0;
            for (int i = 0; i < finalTargets.size(); i++) {
                if (finalTargets.get(i).className.equals(remembered)) {
                    index = i;
                    break;
                }
            }
            target[0] = finalTargets.get(index).className;
            targetView.setText(labels.get(index), false);
            targetView.setOnItemClickListener((parent, view, position, id) -> {
                if (position < 0 || position >= finalTargets.size()) return;
                target[0] = finalTargets.get(position).className;
                settings.edit().putString("frida_target", target[0]).apply();
            });
            targetLayout.setEnabled(true);
        }

        final List<MaterialCheckBox> abiChecks = new ArrayList<>();
        // APKs without native libraries can take a gadget for any ABI —
        // default to this device's so the patched build runs here.
        boolean noLibs = !FridaGadgetPatcher.hasNativeLibDir(apkFile);
        String deviceAbi = io.github.abdurazaaqmohammed.utils.NativeToolManager.deviceAbi();
        for (String abi : abis) {
            MaterialCheckBox cb = new MaterialCheckBox(context);
            cb.setText(abi);
            cb.setChecked(settings.getBoolean("frida_abi_" + abi, noLibs && abi.equals(deviceAbi)));
            cb.setOnCheckedChangeListener((bv, c) ->
                    settings.edit().putBoolean("frida_abi_" + abi, c).apply());
            abiChecks.add(cb);
            abiBox.addView(cb);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.frida_gadget_title)
                .setView(ll)
                .setPositiveButton(R.string.patch_apk, (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    for (MaterialCheckBox cb : abiChecks) {
                        if (cb.isChecked()) chosen.add(cb.getText().toString());
                    }
                    if (chosen.isEmpty()) {
                        Extensions.showMessage(context, context.getString(R.string.frida_abis_required));
                        return;
                    }
                    if (target[0] == null) {
                        Extensions.showMessage(context, context.getString(R.string.frida_pick_target));
                        return;
                    }
                    config.save(context);
                    patch(context, apkFile, chosen, target[0], config, sign[0]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String targetLabel(MainActivity context, Il2CppDumperUtil.Target t) {
        String label = t.className;
        if (t.kind == Il2CppDumperUtil.Target.APPLICATION) {
            label += " (" + context.getString(R.string.frida_target_application) + ")";
        } else if (t.kind == Il2CppDumperUtil.Target.LAUNCHER) {
            label += " (" + context.getString(R.string.frida_target_launcher) + ")";
        }
        return label;
    }

    private static void patch(MainActivity context, File apkFile, List<String> abis,
                              String targetClass, FridaConfig config, boolean doSign) {
        SignWrapper[] wrapper = new SignWrapper[1];
        Runnable doPatch = () -> FridaGadgetPatcher.ensureBundle(context, () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    File out = FridaGadgetPatcher.patchApk(context, apkFile, abis,
                            targetClass, config, logger);
                    if (doSign && wrapper[0] != null) {
                        pm.setText(context.getString(R.string.signing, out.getName()));
                        wrapper[0].signApk(out);
                    }
                    pm.dismiss();
                    context.runOnUiThread(() -> {
                        Extensions.showMessage(context, context.getString(
                                R.string.frida_done, out.getName()));
                        context.refreshAllPanes();
                    });
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        });
        if (doSign) SignWrapper.requireAuth(context, sw -> {
            wrapper[0] = sw;
            doPatch.run();
        });
        else doPatch.run();
    }
}
