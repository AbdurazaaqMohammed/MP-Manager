package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * Installer source / flag preferences.
 *
 * <p>Port of the profile bits of InstallerX Revived
 * ({@code ConfigModel.installer/installerMode/installFlags}) reduced to what a
 * file manager needs: who the system should blame for the install, plus the
 * small set of install flags that are safe to expose.
 *
 * <p>InstallerX defaults the spoof target to the Play Store
 * ({@code com.android.vending}) with a Shell ({@code com.android.shell})
 * fallback for HyperOS/MIUI interception; the same defaults are used here.
 * See {@code InstallerX-Revived-main/.../AppDataStore.kt} and
 * {@code IBinderAppInstallerRepoImpl.kt resolveConfiguredInstallerPackageName}.
 *
 * <p>SPDX-License-Identifier: GPL-3.0-only
 */
public final class InstallerConfig {

    public static final String PLAY_STORE = "com.android.vending";
    public static final String SHELL = "com.android.shell";

    public static final String SOURCE_SELF = "self";
    public static final String SOURCE_PLAY = "play";
    public static final String SOURCE_SHELL = "shell";
    public static final String SOURCE_CUSTOM = "custom";

    private InstallerConfig() {
    }

    public static String getSource(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("installer_source", SOURCE_SELF);
            if (SOURCE_PLAY.equals(v) || SOURCE_SHELL.equals(v)
                    || SOURCE_CUSTOM.equals(v)) return v;
        } catch (Exception ignored) {
        }
        return SOURCE_SELF;
    }

    public static void setSource(Context context, String source) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString("installer_source", source).apply();
    }

    public static String getCustomPackage(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("installer_custom_pkg", "");
            return v == null ? "" : v.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Who the install should be attributed to. When a privileged caller
     * (root/shell) creates the session, PackageManager trusts this value, so
     * {@code getInstallSourceInfo().getInstallingPackageName()} later returns
     * it. Without privilege the system overwrites it with our own package.
     */
    public static String resolveInstallerPackageName(Context context) {
        String source = getSource(context);
        switch (source) {
            case SOURCE_PLAY:
                return PLAY_STORE;
            case SOURCE_SHELL:
                return SHELL;
            case SOURCE_CUSTOM: {
                String custom = getCustomPackage(context);
                if (RootManager.isPackageNameValid(custom)) return custom;
                return context.getPackageName();
            }
            case SOURCE_SELF:
            default:
                return context.getPackageName();
        }
    }

    /** True when the user asked for Play attribution (the anti-reinstall use case). */
    public static boolean isPlaySpoofEnabled(Context context) {
        return SOURCE_PLAY.equals(getSource(context));
    }

    /** Spoofing only sticks when the session is created as root/shell. */
    public static boolean wantsSpoof(Context context) {
        return !SOURCE_SELF.equals(getSource(context));
    }

    public static boolean isShizukuSilentEnabled(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("shizuku_silent_install", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    // ---- install flags (subset of InstallerX InstallOption) ----

    public static boolean allowTest(Context context) {
        return prefs(context).getBoolean("install_allow_test", false);
    }

    public static boolean allowDowngrade(Context context) {
        return prefs(context).getBoolean("install_allow_downgrade", false);
    }

    public static boolean grantAllPermissions(Context context) {
        return prefs(context).getBoolean("install_grant_all", false);
    }

    public static boolean bypassLowTargetSdk(Context context) {
        return prefs(context).getBoolean("install_bypass_low_target", false);
    }

    public static boolean installAllUsers(Context context) {
        return prefs(context).getBoolean("install_all_users", false);
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static String describeSource(Context context) {
        String source = getSource(context);
        switch (source) {
            case SOURCE_PLAY:
                return PLAY_STORE + " (Play Store spoof)";
            case SOURCE_SHELL:
                return SHELL + " (Shell)";
            case SOURCE_CUSTOM:
                return getCustomPackage(context) + " (custom)";
            default:
                return context.getPackageName() + " (self)";
        }
    }
}
