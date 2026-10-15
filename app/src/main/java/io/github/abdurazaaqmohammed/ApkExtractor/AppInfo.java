package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String filePath;
    public final String name;
    public final String packageName;
    public Drawable icon;
    public final boolean enabled;
    public final boolean isSplit;
    public String firstInstalled;
    public String lastUpdated;
    public long firstInstall;
    public long lastUpdate;
    public final String versionName;
    final int versionCode;
    public ApplicationInfo appInfo;
    public long baseSize;
    public long totalSize;
    public int splitCount;
    public volatile String sigType;
    public int targetSdk;
    public int minSdk;
    public String installer;
    public String installInitiator;
    public String installOrigin;
    public String dataDir;
    public String extDataDir;
    public String apkDir;
    public AppInfo(String f, String name, Drawable icon, String packageName, boolean enabled, boolean isSplit, String firstInstalled, String lastUpdated, int versionCode, String versionName) {
        this.filePath = f;
        this.name = name;
        this.icon = icon;
        this.packageName = packageName;
        this.enabled = enabled;
        this.isSplit = isSplit;
        this.firstInstalled = firstInstalled;
        this.lastUpdated = lastUpdated;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }
    public String getVersionName() {
        return versionName.replace('/', '_');
    }
    public int getVersionCode() {
        return versionCode;
    }
}
