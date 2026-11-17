package io.github.abdurazaaqmohammed.feature.apk.xposed;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.apk.InstallUtil;
import io.github.abdurazaaqmohammed.feature.apk.XposedPatcherDialogs;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.ui.editor.FilePickerDialog;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.libxposed.service.IHotReloadCallback;

public final class LSPatchManagerActivity extends BaseActivity {
    private LinearLayout contents;
    private LSPatchModuleRepository repository;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new LSPatchModuleRepository(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.lspatch_manager);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(view -> finish());
        root.addView(toolbar);
        ScrollView scroll = new ScrollView(this);
        contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL);
        contents.setPadding(dpPx(16), dpPx(8), dpPx(16), dpPx(16));
        scroll.addView(contents);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        new Thread(() -> {
            List<String> apps = repository.managedApps();
            List<LSPatchModuleRepository.Module> modules = repository.modules();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                contents.removeAllViews();
                text(getString(R.string.lspatch_manager_hint));
                button(getString(R.string.npatch_patch), () -> pick(false));
                button(getString(R.string.lspatch_install_module), () -> pick(true));
                text(getString(R.string.lspatch_apps));
                if (apps.isEmpty()) text(getString(R.string.lspatch_no_apps));
                for (String app : apps) button(app + "\n" + getString(R.string.lspatch_scope_count,
                        repository.scope(app).size()) + (repository.isManaged(app) ? "" : "\n" + getString(R.string.lspatch_missing_app)),
                        () -> appActions(app));
                text(getString(R.string.lspatch_modules));
                if (modules.isEmpty()) text(getString(R.string.lspatch_no_modules));
                for (LSPatchModuleRepository.Module module : modules) button(module.label() + "\n" + module.packageName(),
                        () -> moduleActions(module.packageName()));
            });
        }, "LSPatch-manager-scan").start();
    }

    private void appActions(String app) {
        String[] actions = {getString(R.string.lspatch_select_modules), getString(R.string.patcher_open_app),
                getString(R.string.lspatch_remove_scope)};
        new MaterialAlertDialogBuilder(this).setTitle(app).setItems(actions, (dialog, which) -> {
            if (which == 0) selectModules(this, repository.scope(app), selected -> new Thread(() -> {
                try {
                    Set<String> affected = repository.scope(app);
                    affected.addAll(selected);
                    repository.setScope(app, selected);
                    LSPatchRemoteServices.get(this).push(affected);
                    runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(this).setMessage(R.string.lspatch_scope_saved)
                                .setPositiveButton(android.R.string.ok, null).show();
                        refresh();
                    });
                } catch (Exception error) { showError(error); }
            }, "LSPatch-scope").start());
            else if (which == 1) {
                Intent intent = getPackageManager().getLaunchIntentForPackage(app);
                if (intent != null) startActivity(intent);
            } else new MaterialAlertDialogBuilder(this).setMessage(R.string.lspatch_forget_confirm)
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.delete, (d, w) -> {
                        new Thread(() -> {
                            try { repository.forget(app); runOnUiThread(this::refresh); }
                            catch (Exception error) { showError(error); }
                        }).start();
                    }).show();
        }).show();
    }

    private void moduleActions(String module) {
        new MaterialAlertDialogBuilder(this).setTitle(module).setItems(new String[]{
                getString(R.string.lspatch_module_settings), getString(R.string.lspatch_reload)}, (dialog, which) -> {
            LSPatchRemoteServices services = LSPatchRemoteServices.get(this);
            if (which == 0) {
                services.push(List.of(module));
                Intent intent = getPackageManager().getLaunchIntentForPackage(module);
                if (intent != null) startActivity(intent);
            } else {
                var targets = services.hotReload.getRunningTargets(module);
                if (targets.isEmpty()) {
                    new MaterialAlertDialogBuilder(this).setMessage(R.string.lspatch_reload_none)
                            .setPositiveButton(android.R.string.ok, null).show();
                }
                for (var target : targets) services.hotReload.hotReload(module, target.targetId, null,
                        new IHotReloadCallback.Stub() {
                            @Override public void onHotReloadResult(int status, String message) {
                                runOnUiThread(() -> new MaterialAlertDialogBuilder(LSPatchManagerActivity.this)
                                        .setMessage(getString(R.string.lspatch_reload_result, target.processName,
                                                status + ": " + message)).setPositiveButton(android.R.string.ok, null).show());
                            }
                        });
            }
        }).show();
    }

    public static void selectModules(AppCompatActivity activity, Set<String> initial, Consumer<Set<String>> selected) {
        new Thread(() -> {
            List<LSPatchModuleRepository.Module> modules = new LSPatchModuleRepository(activity).modules();
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (modules.isEmpty()) {
                    new MaterialAlertDialogBuilder(activity).setMessage(R.string.lspatch_no_modules)
                            .setPositiveButton(android.R.string.ok, null).show();
                    return;
                }
                Set<String> scope = new LinkedHashSet<>(initial);
                boolean[] checked = new boolean[modules.size()];
                String[] labels = new String[modules.size()];
                for (int i = 0; i < modules.size(); i++) {
                    var module = modules.get(i);
                    checked[i] = scope.contains(module.packageName());
                    labels[i] = module.label() + "\n" + module.packageName();
                }
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.lspatch_select_modules)
                        .setMultiChoiceItems(labels, checked, (dialog, which, enabled) -> {
                            if (enabled) scope.add(modules.get(which).packageName());
                            else scope.remove(modules.get(which).packageName());
                        }).setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> selected.accept(scope)).show();
            });
        }, "LSPatch-module-scan").start();
    }

    private void pick(boolean module) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.root = Environment.getExternalStorageDirectory();
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.extensions = new String[]{"apk"};
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(module ? R.string.lspatch_install_module : R.string.patcher_pick_apk));
        picker.setDialogSelectionListener(paths -> {
            if (paths == null || paths.length == 0) return;
            File file = new File(paths[0]);
            if (module) {
                if (LSPatchModuleRepository.moduleKind(file) == null) {
                    new MaterialAlertDialogBuilder(this).setMessage(R.string.lspatch_no_modules)
                            .setPositiveButton(android.R.string.ok, null).show();
                    return;
                }
                InstallUtil.installApkWithDialog(this, file);
            }
            else new XposedPatcherDialogs(this, new DialogUtil(this), true).showDialog(file, file.getName());
        });
        picker.show();
    }

    private void text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setPadding(0, dpPx(12), 0, dpPx(8));
        contents.addView(view);
    }

    private void button(String label, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        contents.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showError(Throwable error) { runOnUiThread(() -> new ErrorUtil(this).showError(error)); }
}
