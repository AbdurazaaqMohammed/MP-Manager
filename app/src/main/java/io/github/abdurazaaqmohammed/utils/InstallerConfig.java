package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * Installer **package** preferences.
 *
 * <p>The value configured here is the package name PackageManager records as the
 * installer of a package ({@code getInstallSourceInfo().getInstallingPackageName()},
 * or {@code getInstallerPackageName()} before API 30). It is deliberately *not*
 * an install <em>source</em>: {@code PackageInstaller.SessionParams#setPackageSource}
 * is a separate Android 13 concept describing where the bytes came from (store,
 * local file, download) and has nothing to do with attribution.
 *
 * <p>Port of the profile bits of InstallerX Revived
 * ({@code ConfigModel.installer} / {@code InstallerMode}, applied in
 * {@code IBinderAppInstallerRepoImpl.resolveInstallerPackageName} and
 * {@code resolvePackageInstallerCallerPackageName}).
 *
 * <p>SPDX-License-Identifier: GPL-3.0-only
 */
public final class InstallerConfig {

    public static final String PLAY_STORE = "com.android.vending";
    public static final String SHELL = "com.android.shell";

    public static final String MODE_SELF = "self";
    public static final String MODE_INITIATOR = "initiator";
    public static final String MODE_PLAY = "play";
    public static final String MODE_SHELL = "shell";
    public static final String MODE_CUSTOM = "custom";

    /** Legacy preference key: held an install-source enum, same value space. */
    private static final String KEY_LEGACY_MODE = "installer_source";
    private static final String KEY_MODE = "installer_package_mode";
    private static final String KEY_CUSTOM_PACKAGE = "installer_custom_pkg";
    private static final String KEY_INITIATOR = "installer_initiator_pkg";

    private InstallerConfig() {
    }

    private static String readStoredMode(Context context) {
        try {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
            String v = p.getString(KEY_MODE, null);
            if (v == null) v = p.getString(KEY_LEGACY_MODE, MODE_SELF);
            return sanitize(v);
        } catch (Exception ignored) {
            return MODE_SELF;
        }
    }

    private static String sanitize(String v) {
        if (v == null) return MODE_SELF;
        switch (v) {
            case MODE_PLAY:
            case MODE_SHELL:
            case MODE_CUSTOM:
            case MODE_INITIATOR:
            case MODE_SELF:
                return v;
            default:
                return MODE_SELF;
        }
    }

    public static String getMode(Context context) {
        return readStoredMode(context);
    }

    public static void setMode(Context context, String mode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(KEY_MODE, sanitize(mode))
                .remove(KEY_LEGACY_MODE)
                .apply();
    }

    public static String getCustomPackage(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_CUSTOM_PACKAGE, "");
            return v == null ? "" : v.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Package that asked us to open this file, used by
     * {@link #MODE_INITIATOR}. Recorded from the activity that received the
     * share/view intent; cleared once consumed so a later, unrelated install
     * does not inherit it.
     */
    public static void setInitiatorPackage(Context context, String packageName) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(KEY_INITIATOR, packageName == null ? "" : packageName.trim())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static String peekInitiatorPackage(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_INITIATOR, "");
            return v == null ? "" : v.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void clearInitiatorPackage(Context context) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .remove(KEY_INITIATOR).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * The package the install should be attributed to.
     *
     * <p>For a privileged caller (root/shell) PackageManager honours this value,
     * so {@code getInstallSourceInfo().getInstallingPackageName()} returns it
     * afterwards. Without privilege the system overwrites it with our own
     * package, and there is nothing to claim.
     */
    public static String resolveInstallerPackageName(Context context) {
        switch (getMode(context)) {
            case MODE_PLAY:
                return PLAY_STORE;
            case MODE_SHELL:
                return SHELL;
            case MODE_INITIATOR: {
                String initiator = peekInitiatorPackage(context);
                if (RootManager.isPackageNameValid(initiator)) return initiator;
                return context.getPackageName();
            }
            case MODE_CUSTOM: {
                String custom = getCustomPackage(context);
                if (RootManager.isPackageNameValid(custom)) return custom;
                return context.getPackageName();
            }
            case MODE_SELF:
            default:
                return context.getPackageName();
        }
    }

    /** True when the resolved installer package differs from our own. */
    public static boolean isSpoofRequested(Context context) {
        if (MODE_SELF.equals(getMode(context))) return false;
        String installer = resolveInstallerPackageName(context);
        return installer != null && !installer.equals(context.getPackageName());
    }

    /**
     * True when the resolved installer package is one a plain shell (uid 2000)
     * caller passes to {@code pm install-create -i}. Kept for callers that want
     * to know the request is well formed; note that PackageInstallerService
     * performs no ownership check on {@code -i} for the shell uid, so a foreign
     * package is accepted here.
     */
    public static boolean isShellClaimable(Context context, String installerPackage) {
        return installerPackage != null && RootManager.isPackageNameValid(installerPackage);
    }

    public static boolean isShizukuSilentEnabled(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("shizuku_silent_install", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    // ---- pre-install comparison sheet ----

    /** Never show the comparison before installing. */
    public static final String INFO_MODE_NEVER = "never";
    /** Show it only when the signer differs from the installed copy. */
    public static final String INFO_MODE_WHEN_SIGNATURE_DIFFERS = "when_differs";
    /** Show it for every install. */
    public static final String INFO_MODE_ALWAYS = "always";

    public static String getInstallInfoMode(Context context) {
        try {
            String v = prefs(context).getString("install_info_mode", INFO_MODE_WHEN_SIGNATURE_DIFFERS);
            if (INFO_MODE_ALWAYS.equals(v) || INFO_MODE_NEVER.equals(v)
                    || INFO_MODE_WHEN_SIGNATURE_DIFFERS.equals(v)) return v;
        } catch (Exception ignored) {
        }
        return INFO_MODE_WHEN_SIGNATURE_DIFFERS;
    }

    public static void setInstallInfoMode(Context context, String mode) {
        String v = INFO_MODE_ALWAYS.equals(mode) ? INFO_MODE_ALWAYS
                : INFO_MODE_NEVER.equals(mode) ? INFO_MODE_NEVER
                : INFO_MODE_WHEN_SIGNATURE_DIFFERS;
        prefs(context).edit().putString("install_info_mode", v).apply();
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

    public static String describeInstallerPackage(Context context) {
        String installer = resolveInstallerPackageName(context);
        switch (getMode(context)) {
            case MODE_PLAY:
                return installer + " (Play Store)";
            case MODE_SHELL:
                return installer + " (Shell)";
            case MODE_INITIATOR:
                return installer + " (initiator)";
            case MODE_CUSTOM:
                return installer + " (custom)";
            default:
                return installer + " (this app)";
        }
    }
}