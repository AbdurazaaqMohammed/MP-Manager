package io.github.abdurazaaqmohammed.tools;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class CacheCleanerService extends AccessibilityService {
    protected void onServiceConnected() {
        super.onServiceConnected();
        CacheCleaner.setActiveService(this);
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            CacheCleaner.onEvent(this, event);
        } catch (Exception ignored) {
        }
    }

    public void onInterrupt() {
    }

    public boolean onUnbind(Intent intent) {
        CacheCleaner.setActiveService(null);
        return super.onUnbind(intent);
    }
}
