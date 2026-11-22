package io.github.abdurazaaqmohammed.plugins.ipc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** An action result may request its own installed theme, never silently select one. */
public final class PluginThemeRequests {
    private static final ThreadPoolExecutor IMPORTS = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "plugin-theme-request");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    static {
        IMPORTS.allowCoreThreadTimeOut(true);
    }

    private PluginThemeRequests() {
    }

    public static void handle(Activity activity, ExternalActions.Entry source, Intent result) {
        if (source != null) handle(activity, source.plugin.packageName, result);
    }

    public static void handle(Activity activity, String packageName, Intent result) {
        if (activity == null || result == null || packageName == null) return;
        String id;
        try {
            id = result.getStringExtra(PluginContracts.EXTRA_APPLY_THEME_ID);
            if (id == null || id.isEmpty() || id.length() > 100
                    || !id.matches("[A-Za-z0-9._-]+")
                    || !PluginHost.isTrusted(activity, packageName)
                    || activity.isFinishing() || activity.isDestroyed()) return;
        } catch (Exception ignored) {
            return;
        }
        String digest = PluginHost.certDigest(activity, packageName);
        if (digest == null) return;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.plugin_apply_theme)
                .setMessage(activity.getString(R.string.plugin_apply_theme_message, packageName, id))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Context app = activity.getApplicationContext();
                    try {
                        IMPORTS.execute(() -> {
                            boolean imported = false;
                            if (digest.equals(PluginHost.certDigest(app, packageName))
                                    && PluginHost.isTrusted(app, packageName)) {
                                List<ThemePacks.Entry> themes = ThemePacks.discover(app);
                                for (ThemePacks.Entry theme : themes) {
                                    if (packageName.equals(theme.packageName)) {
                                        imported = ThemePacks.importAndSelect(app, theme, id);
                                        break;
                                    }
                                }
                            }
                            boolean success = imported;
                            activity.runOnUiThread(() -> {
                                if (!success && !activity.isFinishing() && !activity.isDestroyed()) {
                                    Toast.makeText(activity, R.string.plugin_apply_theme_failed,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    } catch (Exception ignored) {
                        Toast.makeText(activity, R.string.plugin_apply_theme_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }
}
