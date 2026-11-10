package io.github.abdurazaaqmohammed.tools.appops;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;

public class AppOpsEngine {

    /** Timeout for one elevated read command. */
    private static final int READ_TIMEOUT_SECONDS = 30;
    /** Timeout for one elevated mutating command. */
    private static final int WRITE_TIMEOUT_SECONDS = 20;

    /** stderr / failure reason of the most recent {@link #execElevated} call. */
    private static volatile String lastError = "";

    /** Why the last elevated command failed; empty after a success. */
    public static String lastError() {
        return lastError;
    }

    /**
     * Runs an elevated command through the app's existing backends and reports
     * whether it actually succeeded, instead of spawning a private
     * {@code su}/{@code rish} pipeline that discarded the exit code.
     *
     * <p>The working mode decides the backend: root mode goes through
     * {@link RootManager#execute} (custom su binary, availability gate, timeout,
     * stderr draining) and Shizuku mode through {@link ShizukuShell#exec}.
     * With neither backend configured the command is never run and the failure
     * is reported, so callers cannot mistake silence for success.
     */
    public static RootManager.ShellResult execElevated(Context context, String cmd) {
        return execElevated(context, cmd, READ_TIMEOUT_SECONDS);
    }

    public static RootManager.ShellResult execElevated(Context context, String cmd, int timeoutSeconds) {
        lastError = "";
        RootManager.ShellResult result;
        try {
            RootManager rm = RootManager.getInstance(context);
            RootManager.WorkingMode mode = rm.getWorkingMode();
            if (mode == RootManager.WorkingMode.ROOT) {
                if (!rm.isRootAvailable()) {
                    result = new RootManager.ShellResult(-1, "", "Root is not available");
                } else {
                    result = rm.execute(cmd, timeoutSeconds);
                }
            } else if (mode == RootManager.WorkingMode.SHIZUKU) {
                ShizukuShell.warmUp(context);
                ShizukuShell.Result r = ShizukuShell.exec(cmd, timeoutSeconds);
                result = new RootManager.ShellResult(r.exitCode, r.stdout, r.stderr);
            } else {
                result = new RootManager.ShellResult(-1, "", "Requires root or Shizuku");
            }
        } catch (Exception e) {
            result = new RootManager.ShellResult(-1, "", e.getMessage() == null ? "Command failed" : e.getMessage());
        }
        if (!result.isSuccess()) {
            lastError = result.error() == null || result.error().isEmpty() ? "Command failed (exit " + result.exitCode() + ")" : result.error();
        }
        return result;
    }

    /** True when the current working mode has an elevated backend ready to run commands. */
    public static boolean hasElevatedBackend(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            RootManager.WorkingMode mode = rm.getWorkingMode();
            if (mode == RootManager.WorkingMode.ROOT) return rm.isRootAvailable();
            if (mode == RootManager.WorkingMode.SHIZUKU) return ShizukuManager.ready();
        } catch (Exception ignored) {
        }
        return false;
    }

    public static List<AppItem> loadInstalledApps(Context context) {
        List<AppItem> cached = AppOpsCache.getCachedAppList();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        PackageManager pm = context.getPackageManager();

        // Batch query all launchable packages in a single IPC call instead of 400+ calls
        Set<String> launchablePackages = new HashSet<>();
        try {
            android.content.Intent launchIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            launchIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(launchIntent, 0);
            for (android.content.pm.ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    launchablePackages.add(ri.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {}

        List<PackageInfo> installedPackages;
        try {
            // Using 0 flags instead of GET_PERMISSIONS avoids parsing all permissions for hundreds of apps
            installedPackages = pm.getInstalledPackages(0);
        } catch (Exception e) {
            installedPackages = new ArrayList<>();
        }

        List<AppItem> list = new ArrayList<>(installedPackages.size());
        for (PackageInfo pi : installedPackages) {
            if (pi.applicationInfo == null) continue;
            boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isEnabled = pi.applicationInfo.enabled;
            boolean isHidden = !launchablePackages.contains(pi.packageName);
            CharSequence labelSeq = pi.applicationInfo.loadLabel(pm);
            String label = labelSeq != null ? labelSeq.toString() : pi.packageName;
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
            boolean enabledState = pi.applicationInfo.enabled;
            data.isEnabled = enabledState;
            data.isSuspended = isAppSuspended(pi.applicationInfo);
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

        // Elevated reads below need root or Shizuku; without a backend the
        // detail screen still shows the static package info, and the UI tells
        // the user why the live state is missing instead of showing defaults
        // that look like real values.
        data.elevatedAvailable = hasElevatedBackend(context);
        if (!data.elevatedAvailable || !isPackageNameValid(packageName)) {
            data.primaryAbi = "unknown";
            // Still list the declared runtime permissions with their grant
            // state from PackageManager; only the op/kernel layers need a
            // shell, so those sections stay empty rather than fake.
            addRuntimePermissions(data, pi, new HashMap<>(), new HashMap<>(), new HashMap<>());
            return data;
        }

        // Query Exact Runtime Permissions via dumpsys package (also carries the
        // app's own primaryCpuAbi, which is what "Primary ABI" should show —
        // Build.SUPPORTED_ABIS[0] is the device ABI, not the app's)
        String dumpsysPkg = execElevated(context, "dumpsys package " + RootManager.escapeShellArg(packageName)).output();
        Map<String, Boolean> dumpsysPerms = parseDumpsysPermissions(dumpsysPkg);
        data.primaryAbi = parsePrimaryAbi(dumpsysPkg);

        // Query Battery & Standby bucket via shell
        String bucketOut = execElevated(context, "am get-standby-bucket " + RootManager.escapeShellArg(packageName)).output().trim();
        data.standbyBucket = parseStandbyBucket(bucketOut);

        String whiteListOut = execElevated(context, "dumpsys deviceidle whitelist").output();
        data.isIgnoringBatteryOptimizations = whiteListOut.contains("," + packageName + ",") || whiteListOut.contains("," + packageName + "\n");

        // Query Network blocked
        String netPolicyOut = execElevated(context, "cmd netpolicy list restrict-background-blacklist").output();
        data.isNetworkBlocked = isUidInNetPolicyList(netPolicyOut, data.uid);

        // Query Real AppOps via shell (cmd appops get)
        Map<String, String[]> opsMap = parseAppOpsOutput(execElevated(context, "cmd appops get " + RootManager.escapeShellArg(packageName)).output());

        // Query Kernel/Uid AppOps via dumpsys appops (The Real Hidden Layer!)
        // Scoped to this package: the unscoped dump is megabytes on real
        // devices, and the scoped one lists only this app's ops.
        Map<String, String> uidOpsMap = parseUidOpsFromDumpsys(
                execElevated(context, "dumpsys appops " + RootManager.escapeShellArg(packageName)).output(), data.uid);

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
                if (trimmed.startsWith("Uid ")) {
                    // Match the uid token exactly: a contains() check attributes
                    // uid 10123's block to uid 123 as well.
                    inTargetUid = uidTokenMatches(trimmed.substring(4).trim(), targetUidStr);
                    continue;
                } else if (inTargetUid && trimmed.startsWith("Package ")) {
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

    /** True when a {@code Uid <n> ...} section header carries exactly the target uid. */
    private static boolean uidTokenMatches(String headerRest, String targetUidStr) {
        int end = 0;
        while (end < headerRest.length() && Character.isDigit(headerRest.charAt(end))) end++;
        return end > 0 && headerRest.substring(0, end).equals(targetUidStr);
    }

    /**
     * Whole-token uid match for netpolicy lists: a bare substring test makes
     * uid 123 look blacklisted whenever any uid containing "123" (10123,
     * 12300, a range endpoint…) appears in the output.
     */
    private static boolean isUidInNetPolicyList(String output, int uid) {
        if (TextUtils.isEmpty(output) || uid <= 0) return false;
        String uidStr = String.valueOf(uid);
        for (String raw : output.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("UID ")) line = line.substring(4).trim();
            for (String token : line.split("[^0-9]+")) {
                if (token.isEmpty()) continue;
                if (token.equals(uidStr)) return true;
                // UID ranges ("10000-20000") cover the uid too.
                int dash = token.indexOf('-');
                if (dash > 0) {
                    try {
                        int lo = Integer.parseInt(token.substring(0, dash));
                        int hi = Integer.parseInt(token.substring(dash + 1));
                        if (uid >= lo && uid <= hi) return true;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return false;
    }

    /** Extracts the app's own {@code primaryCpuAbi=} from dumpsys package output. */
    private static String parsePrimaryAbi(String dumpsysPkg) {
        if (TextUtils.isEmpty(dumpsysPkg)) return "unknown";
        try (BufferedReader reader = new BufferedReader(new StringReader(dumpsysPkg))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("primaryCpuAbi=")) {
                    String abi = line.substring("primaryCpuAbi=".length()).trim();
                    if (!abi.isEmpty() && !"null".equals(abi)) return abi;
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    /** FLAG_SUSPENDED is hidden from the SDK; read it reflectively (API 28+). */
    private static boolean isAppSuspended(ApplicationInfo ai) {
        try {
            Field f = ApplicationInfo.class.getField("FLAG_SUSPENDED");
            return (ai.flags & f.getInt(null)) != 0;
        } catch (Throwable t) {
            return false;
        }
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

    // Mutator actions via root / elevated commands. Every action returns
    // whether the command actually succeeded so the UI can report failures
    // instead of showing "done" for commands that never ran.

    /**
     * Sets one op mode. {@code applyUidMode} also sets the UID-wide mode, which
     * affects every app sharing that uid — only use it when the user explicitly
     * asked for kernel-level control, never as a side effect of a plain toggle.
     */
    public static boolean setAppOpMode(Context context, String packageName, String opName, String mode) {
        return setAppOpMode(context, packageName, opName, mode, false);
    }

    public static boolean setAppOpMode(Context context, String packageName, String opName, String mode, boolean applyUidMode) {
        if (!isPackageNameValid(packageName) || TextUtils.isEmpty(opName) || TextUtils.isEmpty(mode)) return false;
        String pkg = RootManager.escapeShellArg(packageName);
        String op = RootManager.escapeShellArg(opName);
        String m = mode.toLowerCase();
        RootManager.ShellResult r = execElevated(context,
                "cmd appops set " + pkg + " " + op + " " + m, WRITE_TIMEOUT_SECONDS);
        if (!r.isSuccess()) return false;
        if (applyUidMode) {
            r = execElevated(context,
                    "cmd appops set --uid " + pkg + " " + op + " " + m, WRITE_TIMEOUT_SECONDS);
            return r.isSuccess();
        }
        return true;
    }

    public static boolean setPermissionGranted(Context context, String packageName, String permName, boolean grant) {
        if (!isPackageNameValid(packageName) || TextUtils.isEmpty(permName)) return false;
        String action = grant ? "pm grant " : "pm revoke ";
        return execElevated(context,
                action + RootManager.escapeShellArg(packageName) + " " + RootManager.escapeShellArg(permName),
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean setStandbyBucket(Context context, String packageName, String bucket) {
        if (!isPackageNameValid(packageName) || TextUtils.isEmpty(bucket)) return false;
        return execElevated(context,
                "am set-standby-bucket " + RootManager.escapeShellArg(packageName) + " " + bucket.toLowerCase(),
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean setIgnoreBatteryOptimization(Context context, String packageName, boolean ignore) {
        if (!isPackageNameValid(packageName)) return false;
        String sign = ignore ? "+" : "-";
        return execElevated(context,
                "dumpsys deviceidle whitelist " + sign + RootManager.escapeShellArg(packageName),
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean setNetworkBlocked(Context context, int uid, boolean block) {
        if (uid <= 0) return false;
        String sub = block ? "add " : "remove ";
        return execElevated(context,
                "cmd netpolicy " + sub + "restrict-background-blacklist " + uid,
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean forceStopApp(Context context, String packageName) {
        if (!isPackageNameValid(packageName)) return false;
        return execElevated(context,
                "am force-stop " + RootManager.escapeShellArg(packageName),
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean suspendApp(Context context, String packageName, boolean suspend) {
        if (!isPackageNameValid(packageName) || RootManager.isCriticalPackage(packageName)) return false;
        String flag = suspend ? "--suspend " : "--unsuspend ";
        return execElevated(context,
                "pm suspend " + flag + RootManager.escapeShellArg(packageName),
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean enableDisableApp(Context context, String packageName, boolean enable) {
        if (!isPackageNameValid(packageName) || RootManager.isCriticalPackage(packageName)) return false;
        String action = enable ? "enable " : "disable-user --user 0 ";
        return execElevated(context,
                "pm " + action + RootManager.escapeShellArg(packageName),
                WRITE_TIMEOUT_SECONDS).isSuccess();
    }

    public static boolean clearAppData(Context context, String packageName) {
        if (!isPackageNameValid(packageName) || RootManager.isCriticalPackage(packageName)) return false;
        RootManager.ShellResult r = execElevated(context,
                "pm clear " + RootManager.escapeShellArg(packageName), 120);
        return r.isSuccess() && r.output().toLowerCase().contains("success");
    }

    /**
     * Uninstalls the package and verifies it actually happened: {@code pm} can
     * print "Failure [...]" with a non-zero exit or leave the package present
     * for other users, and reporting success then makes the button a placebo.
     */
    public static boolean uninstallApp(Context context, String packageName) {
        if (!isPackageNameValid(packageName) || RootManager.isCriticalPackage(packageName)) return false;
        RootManager.ShellResult r = execElevated(context,
                "pm uninstall " + RootManager.escapeShellArg(packageName), 120);
        if (!r.isSuccess()) return false;
        String out = r.output().toLowerCase();
        if (out.contains("failure")) return false;
        if (out.contains("success")) return true;
        // No clear verdict in the output: trust the package manager.
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return false;
        } catch (PackageManager.NameNotFoundException gone) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPackageNameValid(String packageName) {
        return RootManager.isPackageNameValid(packageName);
    }
}
