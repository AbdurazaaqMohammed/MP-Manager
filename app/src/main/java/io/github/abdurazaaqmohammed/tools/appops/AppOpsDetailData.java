package io.github.abdurazaaqmohammed.tools.appops;

import java.util.ArrayList;
import java.util.List;

public class AppOpsDetailData {
    public String packageName;
    public String label;
    public int uid;
    public String versionName;
    public int targetSdk;
    public int minSdk;
    public String primaryAbi;
    public String dataDir;
    public String installer;
    public String signaturesSha256;
    public long firstInstallTime;
    public long lastUpdateTime;
    public boolean isDebuggable;
    public boolean isBackupAllowed;
    public boolean hasLauncherEntry;
    public boolean isEnabled = true;
    public boolean isSuspended = false;
    /** False when no elevated backend (root/Shizuku) was available, so live op reads were skipped. */
    public boolean elevatedAvailable = true;

    // Battery & Network
    public String standbyBucket = "Active"; // Active, Working, Frequent, Rare, Restricted
    public boolean isIgnoringBatteryOptimizations = false;
    public boolean isNetworkBlocked = false;

    // Sections
    public final List<PermissionEntry> systemPermissions = new ArrayList<>();
    public final List<PermissionEntry> runtimePermissions = new ArrayList<>();
    public final List<AppOpEntry> appOps = new ArrayList<>();

    public static class PermissionEntry {
        public final String name;
        public final String label;
        public boolean isGranted;
        public boolean isFakeGranted; // True if runtime says granted, but AppOp ignores/rejects!
        public String opName;
        public String opMode;
        public String uidMode;

        public PermissionEntry(String name, String label, boolean isGranted, boolean isFakeGranted, String opName, String opMode, String uidMode) {
            this.name = name;
            this.label = label;
            this.isGranted = isGranted;
            this.isFakeGranted = isFakeGranted;
            this.opName = opName;
            this.opMode = opMode != null ? opMode : "default";
            this.uidMode = uidMode != null ? uidMode : "default";
        }
    }

    public static class AppOpEntry {
        public final String opName;
        public final String label;
        public final String description;
        public String mode; // allow, ignore, deny, default, foreground, ask
        public String lastAccessTime;
        public String rejectTime;

        public AppOpEntry(String opName, String label, String description, String mode, String lastAccessTime, String rejectTime) {
            this.opName = opName;
            this.label = label;
            this.description = description;
            this.mode = mode;
            this.lastAccessTime = lastAccessTime;
            this.rejectTime = rejectTime;
        }
    }
}
