package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import androidx.preference.PreferenceManager;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.codehasan.colorpicker.extensions.Extensions;
import rikka.shizuku.Shizuku;

/**
 * Startup privilege handling (root / Shizuku working mode).
 *
 * <p>Everything here is opt-in: the {@code startup_request_root} /
 * {@code startup_request_shell} toggles default to off, so a fresh install
 * never prompts for privileges and the working mode is left exactly as the
 * user last chose.</p>
 */
public final class StartupPrivileges {

    private StartupPrivileges() {
    }

    /** Warms the Shizuku shell binder when the startup-shell toggle allows it. */
    public static void warmShell(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getBoolean("startup_request_shell", false)) {
            ShizukuShell.warmUp(activity);
        }
    }

    /**
     * Applies the startup toggles and kicks the root namespace probe.
     *
     * @param refreshSidebar re-renders the sidebar once a mode change took effect
     */
    public static void apply(Activity activity, Handler handler, Runnable refreshSidebar) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean reqRoot = prefs.getBoolean("startup_request_root", false);
        boolean reqShell = prefs.getBoolean("startup_request_shell", false);
        if (!reqRoot && !reqShell) return; // silent start: no privilege prompts
        RootManager rm = RootManager.getInstance(activity);

        boolean gotRoot = false;
        if (reqRoot) {
            if (rm.autoEnableRootIfAvailable()) {
                gotRoot = true;
                handler.post(() -> {
                    try {
                        Extensions.showMessage(activity, R.string.root_detected_enabled);
                        refreshSidebar.run();
                    } catch (Exception ignored) {
                    }
                });
            } else if (rm.isRootMode()) {
                gotRoot = true;
            }
        }

        if (!gotRoot) {
            if (reqShell) {
                if (ShizukuManager.hasPermission() || Shizuku.pingBinder()) {
                    rm.setWorkingMode(RootManager.WorkingMode.SHIZUKU);
                    handler.post(refreshSidebar);
                }
            } else {
                rm.setWorkingMode(RootManager.WorkingMode.NON_ROOT);
            }
        }
        try {
            rm.kickNsProbe();
        } catch (Exception ignored) {
        }
    }
}
