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

        // Query Kernel/Uid AppOps via dumpsys appops (The Real Hidden Layer!)
        Map<String, String> uidOpsMap = parseUidOpsFromDumpsys(executeElevated("dumpsys appops"), data.uid);

        // Query Exact Runtime Permissions via dumpsys package
        Map<String, Boolean> dumpsysPerms = parseDumpsysPermissions(executeElevated("dumpsys package " + packageName));

        // System Permissions (Special access)
        addSystemSpecialPermissions(data, packageName, opsMap, uidOpsMap);

        // Runtime Permissions with Dual Verification (Accurate Dumpsys + AppOps Detection)
        addRuntimePermissions(data, pi, opsMap, uidOpsMap, dumpsysPerms);

        // Granular AppOps (Filtered and Alphabetically Sorted)
        addGranularAppOps(data, opsMap, uidOpsMap);

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

    private static Map<String, String> parseUidOpsFromDumpsys(String output, int uid) {
        Map<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(output) || uid <= 0) return map;

        String targetUidStr = String.valueOf(uid);
        boolean inTargetUid = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Uid ") && (trimmed.contains(targetUidStr))) {
                    inTargetUid = true;
                    continue;
                } else if (inTargetUid && (trimmed.startsWith("Uid ") || trimmed.startsWith("Package "))) {
                    break;
                }

                if (inTargetUid && trimmed.contains(": mode=")) {
                    int idx = trimmed.indexOf(": mode=");
                    if (idx > 0) {
                        String op = trimmed.substring(0, idx).trim();
                        String mode = trimmed.substring(idx + 7).trim().toLowerCase();
                        map.put(op, mode);
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private static Map<String, Boolean> parseDumpsysPermissions(String output) {
        Map<String, Boolean> map = new HashMap<>();
        if (TextUtils.isEmpty(output)) return map;

        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("granted=")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String perm = line.substring(0, colon).trim();
                        String rest = line.substring(colon + 1);
                        boolean granted = rest.contains("granted=true");
                        map.put(perm, granted);
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
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
                        time = formatRelativeTimeToDate(p.substring(5).trim());
                    } else if (p.startsWith("rejectTime=")) {
                        rejectTime = formatRelativeTimeToDate(p.substring(11).trim());
                    }
                }
                // Later entries override earlier defaults, giving the exact package mode
                map.put(op, new String[]{mode, time, rejectTime});
            }
        } catch (Exception ignored) {}
        return map;
    }

    public static String formatRelativeTimeToDate(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        raw = raw.trim();
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\+)?(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:(\\d+)s)?(?:(\\d+)ms)?\\s*ago");
            java.util.regex.Matcher m = p.matcher(raw);
            if (m.find()) {
                long days = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
                long hours = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
                long minutes = m.group(4) != null ? Long.parseLong(m.group(4)) : 0;
                long seconds = m.group(5) != null ? Long.parseLong(m.group(5)) : 0;
                long millis = m.group(6) != null ? Long.parseLong(m.group(6)) : 0;

                long totalMs = ((days * 86400L + hours * 3600L + minutes * 60L + seconds) * 1000L) + millis;
                if (totalMs > 0) {
                    long targetTime = System.currentTimeMillis() - totalMs;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    return sdf.format(new java.util.Date(targetTime));
                }
            }
        } catch (Exception ignored) {}
        return raw;
    }

    private static void addSystemSpecialPermissions(AppOpsDetailData data, String pkg, Map<String, String[]> opsMap, Map<String, String> uidOpsMap) {
        String[] specialList = {
                "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.MANAGE_EXTERNAL_STORAGE",
                "android.permission.WRITE_SETTINGS"
        };

        String[] opList = {
                "CHANGE_COMPONENT_ENABLED_STATE",
                "GET_USAGE_STATS",
                "REQUEST_INSTALL_PACKAGES",
                "SCHEDULE_EXACT_ALARM",
                "SYSTEM_ALERT_WINDOW",
                "MANAGE_EXTERNAL_STORAGE",
                "WRITE_SETTINGS"
        };

        for (int i = 0; i < specialList.length; i++) {
            String perm = specialList[i];
            String op = opList[i];
            String simpleName = perm.substring(perm.lastIndexOf('.') + 1);

            String opMode = getOpMode(opsMap, op);
            String uidMode = uidOpsMap.containsKey(op) ? uidOpsMap.get(op) : "default";

            boolean isGranted = "allow".equalsIgnoreCase(opMode);
            boolean isFakeGranted = isGranted && ("ignore".equalsIgnoreCase(uidMode) || "deny".equalsIgnoreCase(uidMode));

            data.systemPermissions.add(new AppOpsDetailData.PermissionEntry(
                    perm, simpleName, isGranted, isFakeGranted, op, opMode, uidMode));
        }
    }

    private static void addRuntimePermissions(AppOpsDetailData data, PackageInfo pi, Map<String, String[]> opsMap, Map<String, String> uidOpsMap, Map<String, Boolean> dumpsysPerms) {
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
                "android.permission.READ_CALL_LOG",
                "android.permission.WRITE_CALL_LOG",
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_MMS",
                "android.permission.BODY_SENSORS",
                "android.permission.ACTIVITY_RECOGNITION",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT"
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
            String uidMode = uidOpsMap.containsKey(matchedOp) ? uidOpsMap.get(matchedOp) : "default";

            // Visible status to user/app: dumpsys package has the visible permission status
            boolean isGranted;
            if (dumpsysPerms.containsKey(perm)) {
                isGranted = Boolean.TRUE.equals(dumpsysPerms.get(perm));
            } else {
                isGranted = isPermissionGranted(pi, perm);
            }

            // SMART DETECTION: Granted in Package/System, but UID mode in kernel silently drops/ignores it!
            boolean isFakeGranted = isGranted && ("ignore".equalsIgnoreCase(uidMode) || "deny".equalsIgnoreCase(uidMode) || "ignore".equalsIgnoreCase(opMode) || "deny".equalsIgnoreCase(opMode));

            data.runtimePermissions.add(new AppOpsDetailData.PermissionEntry(
                    perm, simpleName, isGranted, isFakeGranted, matchedOp, opMode, uidMode));
        }

        // Sort runtime permissions alphabetically by simple name
        Collections.sort(data.runtimePermissions, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private static void addGranularAppOps(AppOpsDetailData data, Map<String, String[]> opsMap, Map<String, String> uidOpsMap) {
        // AppOps labels and descriptions dictionary
        Map<String, String[]> knownOps = new HashMap<>();
        knownOps.put("RUN_ANY_IN_BACKGROUND", new String[]{"Run in background", "Allow or prevent app from running in the background."});
        knownOps.put("WAKE_LOCK", new String[]{"Keep awake", "Allow app to keep device or CPU awake."});
        knownOps.put("READ_CLIPBOARD", new String[]{"Read clipboard", "Allow app to read clipboard contents."});
        knownOps.put("WRITE_CLIPBOARD", new String[]{"Write clipboard", "Allow app to write to clipboard."});
        knownOps.put("SYSTEM_ALERT_WINDOW", new String[]{"Draw over other apps", "Display floating windows or overlays over other applications."});
        knownOps.put("START_FOREGROUND", new String[]{"Start foreground service", "Allow starting foreground services."});
        knownOps.put("TAKE_AUDIO_FOCUS", new String[]{"Take audio focus", "Allow stealing audio focus from other applications."});
        knownOps.put("ACCESS_NOTIFICATIONS", new String[]{"Access notifications", "Read or listen to active notifications."});
        knownOps.put("LEGACY_STORAGE", new String[]{"Legacy storage", "Compatibility access for non-scoped storage."});
        knownOps.put("ACCESS_RESTRICTED_SETTINGS", new String[]{"Access restricted settings", "Access accessibility, notification listeners, etc."});
        knownOps.put("READ_DEVICE_IDENTIFIERS", new String[]{"Read device identifiers", "Access IMEI, MEID, ESN and hardware serial numbers."});
        knownOps.put("MANAGE_ONGOING_CALLS", new String[]{"Manage ongoing calls", "Manage or monitor active phone calls."});
        knownOps.put("FOREGROUND_SERVICE_SPECIAL_USE", new String[]{"Foreground service (Special use)", "Run special use foreground services."});
        knownOps.put("USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER", new String[]{"ICC auth with device identifier", "SIM/ICC authentication."});
        knownOps.put("SCHEDULE_EXACT_ALARM", new String[]{"Schedule exact alarm", "Set exact alarms and wake alarms."});
        knownOps.put("GET_USAGE_STATS", new String[]{"Usage stats", "Access device app usage statistics."});
        knownOps.put("VIBRATE", new String[]{"Vibrate", "Control device vibration hardware."});

        // MIUI OEM specific ops
        knownOps.put("MIUIOP(10008)", new String[]{"Auto-start (MIUI)", "OP_AUTO_START: Allow app to start automatically on boot or in background."});
        knownOps.put("MIUIOP(10017)", new String[]{"Create desktop shortcuts (MIUI)", "OP_INSTALL_SHORTCUT: Allow creating home screen shortcuts."});
        knownOps.put("MIUIOP(10020)", new String[]{"Show on lock screen (MIUI)", "OP_SHOW_WHEN_LOCKED: Display windows or alerts when device is locked."});
        knownOps.put("MIUIOP(10032)", new String[]{"Read phone state (MIUI Private)", "OP_READ_PHONE_STATE_REAL: Xiaomi proprietary phone state check."});
        knownOps.put("MIUIOP(10033)", new String[]{"Post local notifications (MIUI)", "OP_POST_LOCAL_NOTIFICATION: Xiaomi local notification channel control."});
        knownOps.put("MIUIOP(10037)", new String[]{"Get anonymous ID - OAID (MIUI)", "OP_GET_OAID: Access Xiaomi Open Anonymous Device Identifier."});
        knownOps.put("MIUIOP(10053)", new String[]{"Chain start / Service link (MIUI)", "OP_SERVICE_LINK: Allow wake up by other background services."});

        // Iterate through all ops found for the package in opsMap or uidOpsMap
        Set<String> allOps = new HashSet<>();
        allOps.addAll(opsMap.keySet());
        allOps.addAll(uidOpsMap.keySet());

        for (String opKey : allOps) {
            String[] val = opsMap.get(opKey);
            String pkgMode = (val != null && !TextUtils.isEmpty(val[0])) ? val[0] : "default";
            String uidMode = uidOpsMap.containsKey(opKey) ? uidOpsMap.get(opKey) : "";
            // Effective mode: if uidMode is set and restrictive (ignore/deny), prioritize it
            String effectiveMode = !TextUtils.isEmpty(uidMode) ? uidMode : pkgMode;

            String time = (val != null) ? val[1] : "";
            String rej = (val != null) ? val[2] : "";

            String label = opKey;
            String desc = "Platform Op: " + opKey;

            if (knownOps.containsKey(opKey)) {
                String[] info = knownOps.get(opKey);
                label = info[0];
                desc = info[1];
            } else if (opKey.startsWith("MIUIOP(")) {
                label = "MIUI Op " + opKey.substring(7, opKey.length() - 1);
                desc = "Xiaomi HyperOS/MIUI proprietary permission op: " + opKey;
            }

            data.appOps.add(new AppOpsDetailData.AppOpEntry(opKey, label, desc, effectiveMode, time, rej));
        }

        // Also add standard non-runtime ops that may not have been reported in opsMap yet
        for (Map.Entry<String, String[]> entry : knownOps.entrySet()) {
            String opKey = entry.getKey();
            if (opKey.startsWith("MIUIOP(")) continue;
            boolean alreadyAdded = false;
            for (AppOpsDetailData.AppOpEntry existing : data.appOps) {
                if (existing.opName.equalsIgnoreCase(opKey)) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                String label = entry.getValue()[0];
                String desc = entry.getValue()[1];
                data.appOps.add(new AppOpsDetailData.AppOpEntry(opKey, label, desc, "default", "", ""));
            }
        }

        // Sort AppOps alphabetically A-Z by label
        Collections.sort(data.appOps, (a, b) -> a.label.compareToIgnoreCase(b.label));
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
        setAppOpMode(packageName, opName, mode, true);
    }

    public static void setAppOpMode(String packageName, String opName, String mode, boolean applyUidMode) {
        String m = mode.toLowerCase();
        executeElevated("cmd appops set " + packageName + " " + opName + " " + m);
        if (applyUidMode) {
            executeElevated("cmd appops set --uid " + packageName + " " + opName + " " + m);
        }
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
