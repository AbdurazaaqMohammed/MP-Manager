package io.github.abdurazaaqmohammed.tools.appops;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppOpsEngine {

    public static String executeElevated(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.getOutputStream().close();
            String output = readStream(p.getInputStream());
            p.waitFor();
            if (!TextUtils.isEmpty(output)) return output;
        } catch (Exception ignored) {}

        // Fallback to rish if su was not granted or failed
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"rish", "-c", cmd});
            p.getOutputStream().close();
            String output = readStream(p.getInputStream());
            p.waitFor();
            return output != null ? output : "";
        } catch (Exception ignored) {}
        return "";
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static List<AppItem> loadInstalledApps(Context context) {
        List<AppItem> cached = AppOpsCache.getCachedAppList();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        PackageManager pm = context.getPackageManager();
        List<PackageInfo> installedPackages;
        try {
            installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        } catch (Exception e) {
            installedPackages = new ArrayList<>();
        }

        List<AppItem> list = new ArrayList<>(installedPackages.size());
        for (PackageInfo pi : installedPackages) {
            if (pi.applicationInfo == null) continue;
            boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isEnabled = pi.applicationInfo.enabled;
            boolean hasLaunch = pm.getLaunchIntentForPackage(pi.packageName) != null;
            boolean isHidden = !hasLaunch;
            String label = pi.applicationInfo.loadLabel(pm).toString();
            if (TextUtils.isEmpty(label)) label = pi.packageName;

            AppItem item = new AppItem(pi.packageName, label, isSystem, isEnabled, isHidden, pi.lastUpdateTime);
            list.add(item);
        }

        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        AppOpsCache.setCachedAppList(list);
        return list;
    }

    public static AppOpsDetailData loadAppDetails(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        AppOpsDetailData data = new AppOpsDetailData();
        data.packageName = packageName;

        PackageInfo pi;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
            }
        } catch (Exception e) {
            try {
                pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            } catch (Exception ex) {
                return data;
            }
        }

        if (pi.applicationInfo != null) {
            data.label = pi.applicationInfo.loadLabel(pm).toString();
            data.uid = pi.applicationInfo.uid;
            data.targetSdk = pi.applicationInfo.targetSdkVersion;
            data.minSdk = (Build.VERSION.SDK_INT >= 24) ? pi.applicationInfo.minSdkVersion : 0;
            data.dataDir = pi.applicationInfo.dataDir;
            data.isDebuggable = (pi.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            data.isBackupAllowed = (pi.applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
            data.hasLauncherEntry = pm.getLaunchIntentForPackage(packageName) != null;
        }
        if (TextUtils.isEmpty(data.label)) data.label = packageName;
        data.versionName = pi.versionName != null ? pi.versionName : "";
        data.firstInstallTime = pi.firstInstallTime;
        data.lastUpdateTime = pi.lastUpdateTime;

        // Installer
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.content.pm.InstallSourceInfo info = pm.getInstallSourceInfo(packageName);
                data.installer = info.getInstallingPackageName();
            } else {
                data.installer = pm.getInstallerPackageName(packageName);
            }
        } catch (Exception ignored) {}
        if (TextUtils.isEmpty(data.installer)) data.installer = "None";

        // Signature SHA-256
        data.signaturesSha256 = getSignatureSha256(pi);

        // Primary ABI via dumpsys or getprop
        data.primaryAbi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : "unknown";

        // Query Battery & Standby bucket via shell
        String bucketOut = executeElevated("am get-standby-bucket " + packageName).trim();
        data.standbyBucket = parseStandbyBucket(bucketOut);

        String whiteListOut = executeElevated("dumpsys deviceidle whitelist");
        data.isIgnoringBatteryOptimizations = whiteListOut.contains("," + packageName + ",") || whiteListOut.contains("," + packageName + "\n");

        // Query Network blocked
        String netPolicyOut = executeElevated("cmd netpolicy list restrict-background-blacklist");
        data.isNetworkBlocked = netPolicyOut.contains("UID " + data.uid) || netPolicyOut.contains(String.valueOf(data.uid));

        // Query Real AppOps via shell (cmd appops get)
        Map<String, String[]> opsMap = parseAppOpsOutput(executeElevated("cmd appops get " + packageName));

        // System Permissions (Special access)
        addSystemSpecialPermissions(data, packageName, opsMap);

        // Runtime Permissions with Dual Verification (Fake Granted Detection!)
        addRuntimePermissions(data, pi, opsMap);

        // Remaining Granular AppOps
        addGranularAppOps(data, opsMap);

        return data;
    }

    private static String parseStandbyBucket(String code) {
        if ("10".equals(code) || "active".equalsIgnoreCase(code)) return "Active (10)";
        if ("20".equals(code) || "working_set".equalsIgnoreCase(code)) return "Working (20)";
        if ("30".equals(code) || "frequent".equalsIgnoreCase(code)) return "Frequent (30)";
        if ("40".equals(code) || "rare".equalsIgnoreCase(code)) return "Rare (40)";
        if ("45".equals(code) || "restricted".equalsIgnoreCase(code)) return "Restricted (45)";
        if ("50".equals(code) || "never".equalsIgnoreCase(code)) return "Never (50)";
        return TextUtils.isEmpty(code) ? "Active (10)" : "Bucket (" + code + ")";
    }

    private static Map<String, String[]> parseAppOpsOutput(String output) {
        // Map<OpName, [mode, lastAccessTime, rejectTime]>
        Map<String, String[]> map = new HashMap<>();
        if (TextUtils.isEmpty(output)) return map;

        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Uid mode:") || line.isEmpty()) continue;
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String op = line.substring(0, colon).trim();
                String rest = line.substring(colon + 1).trim();

                String[] parts = rest.split(";");
                String mode = parts[0].trim().toLowerCase();
                String time = "";
                String rejectTime = "";
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (p.startsWith("time=")) {
                        time = p.substring(5).trim();
                    } else if (p.startsWith("rejectTime=")) {
                        rejectTime = p.substring(11).trim();
                    }
                }
                // Later entries override earlier uid defaults, giving the exact package mode
                map.put(op, new String[]{mode, time, rejectTime});
            }
        } catch (Exception ignored) {}
        return map;
    }

    private static void addSystemSpecialPermissions(AppOpsDetailData data, String pkg, Map<String, String[]> opsMap) {
        // 1. CHANGE_COMPONENT_ENABLED_STATE
        boolean ccesGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "CHANGE_COMPONENT_ENABLED_STATE"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
                "CHANGE_COMPONENT_ENABLED_STATE",
                ccesGranted, false, "CHANGE_COMPONENT_ENABLED_STATE"));

        // 2. PACKAGE_USAGE_STATS
        boolean usageGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "GET_USAGE_STATS"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.PACKAGE_USAGE_STATS",
                "PACKAGE_USAGE_STATS",
                usageGranted, false, "GET_USAGE_STATS"));

        // 3. REQUEST_INSTALL_PACKAGES
        boolean installGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "REQUEST_INSTALL_PACKAGES"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "REQUEST_INSTALL_PACKAGES",
                installGranted, false, "REQUEST_INSTALL_PACKAGES"));

        // 4. SCHEDULE_EXACT_ALARM
        boolean alarmGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "SCHEDULE_EXACT_ALARM"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.SCHEDULE_EXACT_ALARM",
                "SCHEDULE_EXACT_ALARM",
                alarmGranted, false, "SCHEDULE_EXACT_ALARM"));

        // 5. SYSTEM_ALERT_WINDOW (Draw over other apps)
        boolean alertGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "SYSTEM_ALERT_WINDOW"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.SYSTEM_ALERT_WINDOW",
                "SYSTEM_ALERT_WINDOW",
                alertGranted, false, "SYSTEM_ALERT_WINDOW"));

        // 6. MANAGE_EXTERNAL_STORAGE (All files access)
        boolean allFilesGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "MANAGE_EXTERNAL_STORAGE"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "MANAGE_EXTERNAL_STORAGE",
                allFilesGranted, false, "MANAGE_EXTERNAL_STORAGE"));

        // 7. WRITE_SETTINGS
        boolean writeSettingsGranted = "allow".equalsIgnoreCase(getOpMode(opsMap, "WRITE_SETTINGS"));
        data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                "android.permission.WRITE_SETTINGS",
                "WRITE_SETTINGS",
                writeSettingsGranted, false, "WRITE_SETTINGS"));
    }

    private static void addRuntimePermissions(AppOpsDetailData data, PackageInfo pi, Map<String, String[]> opsMap) {
        String[] runtimeList = {
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.CAMERA",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_PHONE_STATE",
                "android.permission.RECORD_AUDIO",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.READ_CALENDAR",
                "android.permission.WRITE_CALENDAR",
                "android.permission.BODY_SENSORS",
                "android.permission.ACTIVITY_RECOGNITION"
        };

        Set<String> declared = new HashSet<>();
        if (pi.requestedPermissions != null) {
            Collections.addAll(declared, pi.requestedPermissions);
        }

        for (String perm : runtimeList) {
            if (!declared.contains(perm)) continue;

            String simpleName = perm.substring(perm.lastIndexOf('.') + 1);
            String matchedOp = mapPermToOp(simpleName);
            String opMode = getOpMode(opsMap, matchedOp);

            // Check manifest grant
            boolean isGranted = isPermissionGranted(pi, perm);

            // SMART DETECTION: If runtime is granted, but AppOps silently ignores or denies it!
            boolean isFakeGranted = isGranted && ("ignore".equalsIgnoreCase(opMode) || "deny".equalsIgnoreCase(opMode));

            data.runtimePermissions.add(new AppOpsDetailData.PermissionEntry(
                    perm, simpleName, isGranted, isFakeGranted, matchedOp));
        }
    }

    private static void addGranularAppOps(AppOpsDetailData data, Map<String, String[]> opsMap) {
        String[][] standardOps = {
                {"RUN_ANY_IN_BACKGROUND", "Run in background", "Deny stops it from running in the background."},
                {"WAKE_LOCK", "Keep awake", "Deny stops the app from holding wake locks."},
                {"READ_CLIPBOARD", "Read clipboard", "Deny stops it from reading your clipboard."},
                {"WRITE_CLIPBOARD", "Write clipboard", "Deny stops it from writing to clipboard."},
                {"SYSTEM_ALERT_WINDOW", "Draw over other apps", "Deny stops it showing over other apps."},
                {"START_FOREGROUND", "Start foreground service", "Allows launching foreground services."},
                {"TAKE_AUDIO_FOCUS", "Take audio focus", "Controls whether the app can steal audio focus."},
                {"ACCESS_NOTIFICATIONS", "Access notifications", "Deny stops access to notification listener service."},
                {"LEGACY_STORAGE", "Legacy storage", "Compatibility mode for non-scoped storage access."},
                {"ACCESS_RESTRICTED_SETTINGS", "Access restricted settings", "Access accessibility, notification listeners, etc."},
                {"COARSE_LOCATION", "Access coarse location", "Approximate location access."},
                {"FINE_LOCATION", "Access fine location", "Precise GPS location access."},
                {"CAMERA", "Camera", "Camera video/photo capture op."},
                {"RECORD_AUDIO", "Record audio", "Microphone audio recording op."},
                {"POST_NOTIFICATION", "Post notification", "Platform level notification posting op."},
                {"READ_PHONE_STATE", "Read phone state", "Cellular info and phone state op."},
                {"GET_USAGE_STATS", "Usage stats", "Access device usage statistics."}
        };

        for (String[] def : standardOps) {
            String op = def[0];
            String label = def[1];
            String desc = def[2];

            String[] val = opsMap.get(op);
            String mode = (val != null && !TextUtils.isEmpty(val[0])) ? val[0] : "default";
            String time = (val != null) ? val[1] : "";
            String rejTime = (val != null) ? val[2] : "";

            data.appOps.add(new AppOpsDetailData.AppOpEntry(op, label, desc, mode, time, rejTime));
        }

        // Add any OEM or extra detected ops not in the default list
        for (Map.Entry<String, String[]> entry : opsMap.entrySet()) {
            String opKey = entry.getKey();
            boolean exists = false;
            for (AppOpsDetailData.AppOpEntry existing : data.appOps) {
                if (existing.opName.equalsIgnoreCase(opKey)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                String mode = entry.getValue()[0];
                String time = entry.getValue()[1];
                String rej = entry.getValue()[2];
                data.appOps.add(new AppOpsDetailData.AppOpEntry(opKey, opKey, "Custom / Platform Op: " + opKey, mode, time, rej));
            }
        }
    }

    private static String mapPermToOp(String simplePermName) {
        if ("POST_NOTIFICATIONS".equals(simplePermName)) return "POST_NOTIFICATION";
        if ("ACCESS_COARSE_LOCATION".equals(simplePermName)) return "COARSE_LOCATION";
        if ("ACCESS_FINE_LOCATION".equals(simplePermName)) return "FINE_LOCATION";
        return simplePermName;
    }

    private static String getOpMode(Map<String, String[]> opsMap, String op) {
        if (opsMap == null || op == null) return "default";
        String[] val = opsMap.get(op);
        return (val != null && !TextUtils.isEmpty(val[0])) ? val[0] : "default";
    }

    private static boolean isPermissionGranted(PackageInfo pi, String permission) {
        if (pi.requestedPermissions == null || pi.requestedPermissionsFlags == null) return false;
        for (int i = 0; i < pi.requestedPermissions.length; i++) {
            if (permission.equals(pi.requestedPermissions[i])) {
                return (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
            }
        }
        return false;
    }

    private static String getSignatureSha256(PackageInfo pi) {
        try {
            android.content.pm.Signature[] sigs = null;
            if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
                sigs = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners() : pi.signingInfo.getSigningCertificateHistory();
            } else {
                sigs = pi.signatures;
            }
            if (sigs != null && sigs.length > 0) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(sigs[0].toByteArray());
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02X", b));
                }
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "Not available";
    }

    // Mutator actions via root / elevated commands
    public static void setAppOpMode(String packageName, String opName, String mode) {
        executeElevated("cmd appops set " + packageName + " " + opName + " " + mode.toLowerCase());
    }

    public static void setPermissionGranted(String packageName, String permName, boolean grant) {
        String action = grant ? "pm grant " : "pm revoke ";
        executeElevated(action + packageName + " " + permName);
    }

    public static void setStandbyBucket(String packageName, String bucket) {
        executeElevated("am set-standby-bucket " + packageName + " " + bucket.toLowerCase());
    }

    public static void setIgnoreBatteryOptimization(String packageName, boolean ignore) {
        String sign = ignore ? "+" : "-";
        executeElevated("dumpsys deviceidle whitelist " + sign + packageName);
    }

    public static void setNetworkBlocked(int uid, boolean block) {
        if (block) {
            executeElevated("cmd netpolicy add restrict-background-blacklist " + uid);
        } else {
            executeElevated("cmd netpolicy remove restrict-background-blacklist " + uid);
        }
    }

    public static void forceStopApp(String packageName) {
        executeElevated("am force-stop " + packageName);
    }

    public static void suspendApp(String packageName, boolean suspend) {
        String flag = suspend ? "--suspend " : "--unsuspend ";
        executeElevated("pm suspend " + flag + packageName);
    }

    public static void enableDisableApp(String packageName, boolean enable) {
        String action = enable ? "enable " : "disable-user --user 0 ";
        executeElevated("pm " + action + packageName);
    }

    public static void clearAppData(String packageName) {
        executeElevated("pm clear " + packageName);
    }

    public static void uninstallApp(String packageName) {
        executeElevated("pm uninstall " + packageName);
    }
}
