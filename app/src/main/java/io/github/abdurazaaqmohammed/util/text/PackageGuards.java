package io.github.abdurazaaqmohammed.util.text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards for Android package names, including system-critical packages
 * that must not be uninstalled or disabled.
 */
public final class PackageGuards {

    private PackageGuards() {
    }

    // System-critical apps that must not be uninstalled
    private static final Set<String> CRITICAL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.telephony",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.android.launcher3",
            "com.android.launcher",
            "com.android.inputmethod.latin",
            "com.android.keychain",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.vending",
            "com.android.gms",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.gsf.login",
            "com.android.shell",
            "com.android.providers.downloads",
            "com.android.documentsui",
            "com.android.nfc",
            "com.android.bluetooth",
            "com.android.bluetoothideoserver",
            "com.android.wifi",
            "com.android.connectivitymanager",
            "com.android.networkstack",
            "com.android.networkstack.tethering",
            "android",
            "com.android.captiveportallogin",
            "com.android.storagemanager",
            "com.android.emergency",
            "com.android.apps.tag",
            "com.android.se",
            "com.android.wallpaperbackup",
            "com.android.wallpapercropper",
            "com.android.printspooler",
            "com.android.managedprovisioning",
            "com.android.hotspot2.osulolauncher",
            "com.android.internal.systemui.navbar.gestural",
            "com.android.internal.systemui.navbar.threebutton",
            "com.android.internal.systemui.navbar.twobutton",
            "com.android.internal.systemui.navbar.gestural_wide_back",
            "com.android.internal.systemui.navbar.gestural_narrow_back",
            "com.android.internal.systemui.navbar.gestural_extra_wide_back"
    ));

    public static boolean isCriticalPackage(String packageName) {
        if (packageName == null) return true;
        return CRITICAL_PACKAGES.contains(packageName);
    }

    public static boolean isPackageNameValid(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        return packageName.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");
    }
}
