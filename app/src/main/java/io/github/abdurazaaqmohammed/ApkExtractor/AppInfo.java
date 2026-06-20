package io.github.abdurazaaqmohammed.ApkExtractor;

import android.graphics.drawable.Drawable;

import java.io.File;

public class AppInfo {
    public String filePath;
    public String name;
    public String packageName;
    public Drawable icon;
    boolean enabled;
    public boolean isSplit;
    public String firstInstalled;
    public String lastUpdated;
    public long firstInstall;
    public long lastUpdate;
    public String versionName;
    int versionCode;
    public android.content.pm.ApplicationInfo appInfo;
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
}
