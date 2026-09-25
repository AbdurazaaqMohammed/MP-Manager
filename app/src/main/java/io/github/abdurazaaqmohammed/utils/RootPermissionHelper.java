package io.github.abdurazaaqmohammed.utils;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import io.github.ratul.topactivity.utils.PermissionUtil;

public class RootPermissionHelper {
    private static final String ACCESSIBILITY_SERVICE = "io.github.ratul.topactivity.services.AccessibilityMonitoringService";

    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return context.checkCallingOrSelfPermission("android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasOverlay(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 23) return Settings.canDrawOverlays(context);
        } catch (Exception ignored) {
        }
        return true;
    }

    public static boolean hasAccessibility(Context context) {
        return hasAccessibilityFor(context, context.getPackageName() + ".topactivity.services.AccessibilityMonitoringService");
    }

    public static boolean hasAccessibilityFor(Context context, String serviceClass) {
        if (context == null || serviceClass == null || serviceClass.isEmpty()) return false;
        String target = serviceClass.contains("/")
                ? serviceClass
                : new ComponentName(context.getPackageName(), serviceClass).flattenToString();
        try {
            int enabled = 0;
            try {
                enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            } catch (Exception ignored) {
            }
            if (enabled != 1) return false;
            String active = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(active)) return false;
            for (String part : active.split(":")) {
                if (target.equalsIgnoreCase(part.trim())) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRootShellReady(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            try {
                rm.autoEnableRootIfAvailable();
            } catch (Exception ignored) {
            }
            return rm.isRootAvailable() && rm.getWorkingMode() == RootManager.WorkingMode.ROOT;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isShizukuShellReady(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            boolean fileOps = false;
            try {
                fileOps = rm.getWorkingMode() == RootManager.WorkingMode.SHIZUKU;
            } catch (Exception ignored) {
            }
            return (fileOps || !isRootShellReady(context)) && ShizukuManager.ready();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasElevatedShell(Context context) {
        return isRootShellReady(context) || isShizukuShellReady(context);
    }

    public static boolean hasAllInspector(Context context) {
        return hasUsageAccess(context) && hasOverlay(context) && hasAccessibility(context);
    }

    public static boolean grantViaRoot(Context context, String command) {
        if (!isRootShellReady(context)) return shizukuGrant(context, command);
        try {
            RootManager.ShellResult r = RootManager.getInstance(context).execute(command, 15);
            if (r.isSuccess()) return true;
        } catch (Exception ignored) {
        }
        return shizukuGrant(context, command);
    }

    private static boolean shizukuGrant(Context context, String command) {
        try {
            if (!isShizukuShellReady(context)) return false;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return ShizukuManager.shellOkFast(context, command, 15);
            }
            return ShizukuManager.shellOk(context, command, 15);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean grantUsageAccessViaRoot(Context context) {
        return grantViaRoot(context, "appops set " + context.getPackageName() + " GET_USAGE_STATS allow");
    }

    public static boolean grantOverlayViaRoot(Context context) {
        return grantViaRoot(context, "appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
    }

    public static boolean grantWriteSecureViaRoot(Context context) {
        return grantViaRoot(context, "pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
    }

    public static boolean enableAccessibilityViaRoot(Context context) {
        return enableAccessibilityForViaElevated(context, ACCESSIBILITY_SERVICE);
    }

    public static boolean enableAccessibilityForViaElevated(Context context, String serviceClass) {
        if (context == null || serviceClass == null || serviceClass.isEmpty()) return false;
        if (!hasElevatedShell(context)) return false;
        try {
            String flat = serviceClass.contains("/")
                    ? serviceClass
                    : new ComponentName(context.getPackageName(), serviceClass).flattenToString();
            String current = "";
            try {
                String c = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (c != null) current = c;
            } catch (Exception ignored) {
            }
            String updated = current;
            boolean found = false;
            if (!TextUtils.isEmpty(current)) {
                for (String part : current.split(":")) {
                    if (flat.equalsIgnoreCase(part.trim())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) updated = TextUtils.isEmpty(current) ? flat : current + ":" + flat;
            boolean ok1 = grantViaRoot(context, "settings put secure enabled_accessibility_services '" + updated + "'");
            boolean ok2 = grantViaRoot(context, "settings put secure accessibility_enabled 1");
            return ok1 && ok2;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean tryAutoGrantInspector(Context context) {
        if (!hasElevatedShell(context)) return false;
        try {
            if (!hasUsageAccess(context)) grantUsageAccessViaRoot(context);
        } catch (Exception ignored) {
        }
        try {
            if (!hasOverlay(context)) grantOverlayViaRoot(context);
        } catch (Exception ignored) {
        }
        try {
            if (!hasAccessibility(context)) enableAccessibilityViaRoot(context);
        } catch (Exception ignored) {
        }
        try {
            if (!DnsManager.canWriteDirect(context)) grantWriteSecureViaRoot(context);
        } catch (Exception ignored) {
        }
        return hasUsageAccess(context) && hasAccessibility(context);
    }

    public static boolean ensureUsageAccess(AppCompatActivity activity) {
        if (hasUsageAccess(activity)) return true;
        if (grantUsageAccessViaRoot(activity) && hasUsageAccess(activity)) return true;
        try {
            PermissionUtil.requestUsageStatsPermission(activity);
        } catch (Exception ignored) {
        }
        return false;
    }
}
