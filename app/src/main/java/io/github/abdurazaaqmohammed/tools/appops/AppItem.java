package io.github.abdurazaaqmohammed.tools.appops;

import android.graphics.drawable.Drawable;

public class AppItem {
    public final String packageName;
    public final String label;
    public final boolean isSystem;
    public final boolean isEnabled;
    public final boolean isHidden;
    public final long lastUpdateTime;
    public Drawable icon; // loaded lazily / cached

    public AppItem(String packageName, String label, boolean isSystem, boolean isEnabled, boolean isHidden, long lastUpdateTime) {
        this.packageName = packageName;
        this.label = label;
        this.isSystem = isSystem;
        this.isEnabled = isEnabled;
        this.isHidden = isHidden;
        this.lastUpdateTime = lastUpdateTime;
    }
}
