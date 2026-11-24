package io.github.abdurazaaqmohammed.feature.apk;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.ui.main.UIHelper;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * AAPT2 optimize dialog: scans the APK once, offers profiles and multi-select
 * dropdowns for only the specs that exist in that APK, and downloads the AAPT2
 * binary on first use.
 */
final class Aapt2OptimizeDialog {
    private Aapt2OptimizeDialog() {
    }

    static void show(MainActivity activity, UIHelper uiHelper, File input) {
        ProgressDialog scanning = ProgressDialog.show(activity, null,
                activity.getString(R.string.aapt2_scanning), true, false);
        new Thread(() -> {
            Aapt2ApkSpecs.Specs specs;
            try {
                specs = Aapt2ApkSpecs.scan(input);
            } catch (Throwable e) {
                activity.handler.post(() -> {
                    scanning.dismiss();
                    new ErrorUtil(activity).showError(e);
                });
                return;
            }
            activity.handler.post(() -> {
                scanning.dismiss();
                if (activity.isFinishing()) return;
                showDialog(activity, uiHelper, input, specs);
            });
        }, "aapt2-scan").start();
    }

    private static void showDialog(MainActivity activity, UIHelper uiHelper, File input,
                                   Aapt2ApkSpecs.Specs specs) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_aapt2_optimize, null);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        Aapt2ProfileManager profiles = new Aapt2ProfileManager(activity);
        CheckBox sign = view.findViewById(R.id.aapt2_autosign);
        sign.setChecked(prefs.getBoolean("autosign", true));
        sign.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean("autosign", checked).apply());
        view.findViewById(R.id.aapt2_sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
        setupFields(activity, view, profiles, specs);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.aapt2_optimize)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.opt, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    final Aapt2Optimizer.Options options;
                    try {
                        options = new Aapt2Optimizer.Options(text(view, R.id.aapt2_densities),
                                text(view, R.id.aapt2_configurations), text(view, R.id.aapt2_abis),
                                text(view, R.id.aapt2_min_sdk), text(view, R.id.aapt2_target_sdk));
                    } catch (IllegalArgumentException e) {
                        Extensions.showMessage(activity, e.getMessage());
                        return;
                    }
                    Aapt2Profile current = currentProfile(view);
                    profiles.setLastUsed(current);
                    List<String> missing = new ArrayList<>();
                    missing.addAll(specs.missing(Aapt2ApkSpecs.split(options.densities), specs.densities));
                    missing.addAll(specs.missing(Aapt2ApkSpecs.split(options.configurations), specs.languages));
                    missing.addAll(specs.missing(options.abis, specs.abis));
                    if (!missing.isEmpty()) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.aapt2_profile_mismatch_title)
                                .setMessage(activity.getString(R.string.aapt2_profile_mismatch,
                                        TextUtils.join(", ", dedupe(missing))))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(android.R.string.ok, (d, w) -> {
                                    dialog.dismiss();
                                    ensureBinaryThenStart(activity, input, options, sign.isChecked());
                                }).show();
                        return;
                    }
                    dialog.dismiss();
                    ensureBinaryThenStart(activity, input, options, sign.isChecked());
                }));
        dialog.show();
    }

    private static void setupFields(MainActivity activity, View view,
                                    Aapt2ProfileManager profiles, Aapt2ApkSpecs.Specs specs) {
        view.setTag(R.id.aapt2_specs_tag, specs);
        setupMultiSelect(activity, view, R.id.aapt2_densities,
                new Aapt2MultiSelectAdapter(activity, new ArrayList<>(specs.densities)));
        setupMultiSelect(activity, view, R.id.aapt2_configurations,
                new Aapt2MultiSelectAdapter(activity, new ArrayList<>(specs.languages)));
        setupMultiSelect(activity, view, R.id.aapt2_abis,
                new Aapt2MultiSelectAdapter(activity, new ArrayList<>(specs.abis)));

        List<Aapt2Profile> list = profiles.getProfiles();
        MaterialAutoCompleteTextView profileField = view.findViewById(R.id.aapt2_profile);
        ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(activity,
                R.layout.dropdownitem, profileNames(list));
        profileField.setAdapter(profileAdapter);
        profileField.setKeyListener(null);
        profileField.setOnItemClickListener((parent, itemView, position, id) -> {
            if (position < 0 || position >= profiles.getProfiles().size()) return;
            applyProfile(profiles.getProfile(position), view, specs);
            profiles.setLastSelectedIndex(position);
        });
        profileField.setOnClickListener(v -> profileField.showDropDown());

        int selected = Math.max(0, Math.min(profiles.lastSelectedIndex(), list.size() - 1));
        Aapt2Profile lastUsed = profiles.getLastUsed();
        int lastMatch = -1;
        if (lastUsed != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).sameValues(lastUsed)) {
                    lastMatch = i;
                    break;
                }
            }
        }
        if (lastMatch >= 0) {
            selected = lastMatch;
            profileField.setText(profileAdapter.getItem(selected), false);
            applyProfile(list.get(selected), view, specs);
        } else if (lastUsed != null) {
            // Restore the exact values from the previous run even when they
            // were never saved as a named profile.
            String name = lastUsed.getName();
            if (TextUtils.isEmpty(name)) name = activity.getString(R.string.aapt2_profile_last_used);
            profileField.setText(name, false);
            applyProfile(lastUsed, view, specs);
        } else {
            profileField.setText(profileAdapter.getItem(selected), false);
            applyProfile(list.get(selected), view, specs);
        }

        view.findViewById(R.id.aapt2_manage_profiles).setOnClickListener(v ->
                showProfileManager(activity, profiles, view, profileAdapter, specs));
    }

    private static void setupMultiSelect(MainActivity activity, View view, int fieldId,
                                         Aapt2MultiSelectAdapter adapter) {
        MaterialAutoCompleteTextView field = view.findViewById(fieldId);
        field.setAdapter(adapter);
        field.setKeyListener(null);
        field.setText("", false);
        field.setOnClickListener(v -> field.showDropDown());
        field.setOnItemClickListener((parent, itemView, position, id) -> {
            adapter.toggleAt(position);
            field.setText(adapter.getValue(), false);
            field.post(field::showDropDown);
        });
        field.post(() -> {
            if (field.getWidth() > 0) field.setDropDownWidth(field.getWidth());
        });
        if (adapter.getCount() == 0) {
            field.setEnabled(false);
            field.setText(activity.getString(R.string.aapt2_none_in_apk), false);
        }
    }

    private static void applyProfile(Aapt2Profile profile, View view, Aapt2ApkSpecs.Specs specs) {
        if (profile == null) return;
        List<String> missing = new ArrayList<>();
        Aapt2Profile resolved = profile.isDeviceDefault()
                ? resolveDeviceProfile(profile, specs, missing) : profile;
        String densities = filterValues(specs, specs.densities, resolved.getDensities(), missing);
        String languages = filterValues(specs, specs.languages, resolved.getConfigurations(), missing);
        String abis = filterValues(specs, specs.abis, resolved.getAbis(), missing);

        setMulti(view, R.id.aapt2_densities, densities);
        setMulti(view, R.id.aapt2_configurations, languages);
        setMulti(view, R.id.aapt2_abis, abis);
        ((TextInputEditText) view.findViewById(R.id.aapt2_min_sdk)).setText(resolved.getMinSdk());
        ((TextInputEditText) view.findViewById(R.id.aapt2_target_sdk)).setText(resolved.getTargetSdk());
        showWarning(view, missing);
    }

    /** Adapts the "this device" profile to what the APK actually contains. */
    private static Aapt2Profile resolveDeviceProfile(Aapt2Profile profile,
                                                     Aapt2ApkSpecs.Specs specs,
                                                     List<String> missing) {
        String density = profile.getDensities();
        if (!density.isEmpty() && !specs.containsIgnoreCase(specs.densities, density)) {
            missing.add(density);
            density = "";
        }
        String language = profile.getConfigurations();
        if (!language.isEmpty() && !specs.containsIgnoreCase(specs.languages, language)) {
            String base = baseLanguage(language);
            List<String> variants = new ArrayList<>();
            for (String candidate : specs.languages) {
                if (baseLanguage(candidate).equalsIgnoreCase(base)) variants.add(candidate);
            }
            if (variants.isEmpty()) {
                missing.add(language);
                language = "";
            } else {
                language = TextUtils.join(",", variants);
            }
        }
        String abi = profile.getAbis();
        if (!abi.isEmpty() && !specs.containsIgnoreCase(specs.abis, abi)) {
            // Never pick another ABI here: that would make the result unusable
            // on this device. Keeping all ABIs is the safe fallback.
            missing.add(abi);
            abi = "";
        }
        return new Aapt2Profile(profile.getName(), density, language, abi,
                profile.getMinSdk(), profile.getTargetSdk(), true);
    }

    private static String filterValues(Aapt2ApkSpecs.Specs specs, Set<String> available,
                                       String csv, List<String> missing) {
        List<String> kept = new ArrayList<>();
        for (String value : Aapt2ApkSpecs.split(csv)) {
            if (specs.containsIgnoreCase(available, value)) kept.add(value);
            else missing.add(value);
        }
        return TextUtils.join(",", kept);
    }

    private static String baseLanguage(String qualifier) {
        if (qualifier.startsWith("b+")) {
            String[] parts = qualifier.split("\\+");
            return parts.length > 1 ? parts[1] : qualifier;
        }
        int dash = qualifier.indexOf("-r");
        return dash > 0 ? qualifier.substring(0, dash) : qualifier;
    }

    private static void showWarning(View view, List<String> missing) {
        View warning = view.findViewById(R.id.aapt2_profile_warning);
        List<String> values = dedupe(missing);
        if (values.isEmpty()) {
            warning.setVisibility(View.GONE);
            return;
        }
        ((android.widget.TextView) warning).setText(view.getContext().getString(
                R.string.aapt2_profile_warning, TextUtils.join(", ", values)));
        warning.setVisibility(View.VISIBLE);
    }

    private static List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static void setMulti(View view, int fieldId, String values) {
        MaterialAutoCompleteTextView field = view.findViewById(fieldId);
        ListAdapter adapter = field.getAdapter();
        if (adapter instanceof Aapt2MultiSelectAdapter) {
            Aapt2MultiSelectAdapter multi = (Aapt2MultiSelectAdapter) adapter;
            multi.setSelected(Aapt2ApkSpecs.split(values));
            field.setText(multi.getCount() == 0
                    ? view.getContext().getString(R.string.aapt2_none_in_apk)
                    : multi.getValue(), false);
        }
    }

    private static List<String> profileNames(List<Aapt2Profile> profiles) {
        List<String> names = new ArrayList<>();
        for (Aapt2Profile profile : profiles) {
            String name = profile.getName();
            names.add(TextUtils.isEmpty(name) ? "" : name);
        }
        return names;
    }

    private static Aapt2Profile currentProfile(View view) {
        return new Aapt2Profile(text(view, R.id.aapt2_profile),
                text(view, R.id.aapt2_densities), text(view, R.id.aapt2_configurations),
                text(view, R.id.aapt2_abis), text(view, R.id.aapt2_min_sdk),
                text(view, R.id.aapt2_target_sdk), false);
    }

    private static void showProfileManager(MainActivity activity, Aapt2ProfileManager manager,
                                           View view, ArrayAdapter<String> profileAdapter,
                                           Aapt2ApkSpecs.Specs specs) {
        List<Aapt2Profile> profiles = manager.getProfiles();
        CharSequence[] names = profileNames(profiles).toArray(new String[0]);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.aapt2_profile_manage)
                .setItems(names, (dialog, which) -> {
                    dialog.dismiss();
                    Aapt2Profile profile = manager.getProfile(which);
                    if (profile == null) return;
                    applyProfile(profile, view, specs);
                    if (profile.isDeviceDefault()) {
                        ((MaterialAutoCompleteTextView) view.findViewById(R.id.aapt2_profile))
                                .setText(profile.getName(), false);
                        manager.setLastSelectedIndex(which);
                        return;
                    }
                    showEditProfile(activity, manager, view, profileAdapter, which, specs);
                })
                .setNeutralButton(R.string.aapt2_profile_add, (d, w) ->
                        showEditProfile(activity, manager, view, profileAdapter, -1, specs))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void showEditProfile(MainActivity activity, Aapt2ProfileManager manager,
                                        View view, ArrayAdapter<String> profileAdapter,
                                        int index, Aapt2ApkSpecs.Specs specs) {
        Aapt2Profile existing = index >= 0 ? manager.getProfile(index) : null;
        View edit = LayoutInflater.from(activity).inflate(R.layout.dialog_aapt2_profile_edit, null);
        EditText name = edit.findViewById(R.id.aapt2_profile_name);
        name.setText(existing != null ? existing.getName()
                : activity.getString(R.string.aapt2_profile_last_used));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(index >= 0 ? R.string.aapt2_profile_edit : R.string.aapt2_profile_add)
                .setView(edit)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Aapt2Profile profile = currentProfile(view);
                    String nameValue = name.getText().toString().trim();
                    if (nameValue.isEmpty()) nameValue = activity.getString(R.string.aapt2_profile_last_used);
                    profile.setName(nameValue);
                    int selected;
                    if (index < 0) {
                        manager.add(profile);
                        selected = manager.getProfiles().size() - 1;
                    } else {
                        manager.update(index, profile);
                        selected = index;
                    }
                    refreshProfiles(activity, manager, view, profileAdapter, selected, specs);
                    Toast.makeText(activity, R.string.aapt2_profile_saved, Toast.LENGTH_SHORT).show();
                });
        if (existing != null && !existing.isDeviceDefault()) {
            builder.setNeutralButton(R.string.aapt2_profile_delete, (d, w) ->
                    new MaterialAlertDialogBuilder(activity)
                            .setMessage(activity.getString(R.string.aapt2_profile_delete_confirm,
                                    existing.getName()))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok, (dd, ww) -> {
                                manager.delete(index);
                                refreshProfiles(activity, manager, view, profileAdapter, 0, specs);
                            }).show());
        }
        builder.show();
    }

    private static void refreshProfiles(MainActivity activity, Aapt2ProfileManager manager,
                                        View view, ArrayAdapter<String> profileAdapter,
                                        int selected, Aapt2ApkSpecs.Specs specs) {
        List<Aapt2Profile> profiles = manager.getProfiles();
        profileAdapter.clear();
        profileAdapter.addAll(profileNames(profiles));
        profileAdapter.notifyDataSetChanged();
        int safe = Math.max(0, Math.min(selected, profiles.size() - 1));
        MaterialAutoCompleteTextView field = view.findViewById(R.id.aapt2_profile);
        field.setText(profileAdapter.getItem(safe), false);
        manager.setLastSelectedIndex(safe);
        applyProfile(profiles.get(safe), view, specs);
    }

    private static void ensureBinaryThenStart(MainActivity activity, File input,
                                              Aapt2Optimizer.Options options, boolean shouldSign) {
        if (Aapt2BinaryManager.isReady(activity)) {
            requestSignAndStart(activity, input, options, shouldSign);
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.aapt2_install_title)
                .setMessage(activity.getString(R.string.aapt2_install_message,
                        Aapt2BinaryManager.approximateSizeMb()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.download, (d, w) ->
                        downloadAndStart(activity, input, options, shouldSign))
                .show();
    }

    private static void requestSignAndStart(MainActivity activity, File input,
                                            Aapt2Optimizer.Options options, boolean shouldSign) {
        if (shouldSign) {
            SignWrapper.requireAuth(activity, wrapper -> start(activity, input, options, wrapper));
        } else {
            start(activity, input, options, null);
        }
    }

    private static void downloadAndStart(MainActivity activity, File input,
                                         Aapt2Optimizer.Options options, boolean shouldSign) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle(R.string.aapt2_installing);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(1000);
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                Aapt2BinaryManager.download(activity, (done, total) -> activity.handler.post(() -> {
                    if (total > 0) progress.setProgress((int) Math.min(1000, done * 1000 / total));
                }));
                activity.handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(activity, R.string.aapt2_install_done, Toast.LENGTH_SHORT).show();
                    requestSignAndStart(activity, input, options, shouldSign);
                });
            } catch (Throwable e) {
                activity.handler.post(() -> {
                    progress.dismiss();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.aapt2_install_title)
                            .setMessage(activity.getString(R.string.aapt2_install_failed,
                                    e.getMessage() == null ? e.toString() : e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        }, "aapt2-download").start();
    }

    private static String text(View view, int id) {
        View field = view.findViewById(id);
        Editable value;
        if (field instanceof TextInputEditText) {
            value = ((TextInputEditText) field).getText();
        } else if (field instanceof MaterialAutoCompleteTextView) {
            value = ((MaterialAutoCompleteTextView) field).getText();
        } else if (field instanceof EditText) {
            value = ((EditText) field).getText();
        } else {
            return "";
        }
        return value == null ? "" : value.toString().trim();
    }

    private static void start(MainActivity activity, File input,
                              Aapt2Optimizer.Options options, SignWrapper signer) {
        ProgressManager progress = new ProgressManager(activity, true).show();
        progress.setText(activity.getString(R.string.aapt2_optimize));
        new Thread(() -> {
            File output = null;
            try {
                output = Aapt2Optimizer.optimize(activity, input, options);
                if (signer != null) Aapt2Optimizer.signOutput(activity, output, signer);
                File result = output;
                progress.dismiss();
                activity.handler.post(() -> {
                    activity.refreshAllPanes();
                    Extensions.showMessage(activity, activity.getString(R.string.aapt2_opt_done, result.getName()));
                });
            } catch (Throwable e) {
                if (output != null) output.delete();
                progress.dismiss();
                activity.handler.post(() -> new ErrorUtil(activity).showError(e));
            }
        }, "aapt2-optimize").start();
    }
}
