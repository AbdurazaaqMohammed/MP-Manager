package io.github.abdurazaaqmohammed.tools;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

import io.github.abdurazaaqmohammed.utils.ActivityLogStore;

/**
 * Records which screen is in the foreground so an APK's navigation can be
 * followed while it is being inspected.
 *
 * <p>Only window state changes are observed, and only the package and class
 * names are kept -- no view content, no input, no touch exploration. That is
 * enough to answer "which activity opens when I tap X" without the service
 * having any capability to act on the screen.
 *
 * <p>A dialog usually shows up as a window rather than a state change, so window
 * changes are enumerated too; that is what catches popup classes that never
 * appear as a WINDOW_STATE_CHANGED.
 */
public class ActivityLoggerService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ActivityLogStore.add(String.valueOf(event.getPackageName()),
                    String.valueOf(event.getClassName()), false);
        } else if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            dumpWindows();
        }
    }

    private void dumpWindows() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return;
            for (AccessibilityWindowInfo w : windows) {
                if (w == null || w.getRoot() == null) continue;
                CharSequence pkg = w.getRoot().getPackageName();
                CharSequence cls = w.getRoot().getClassName();
                ActivityLogStore.add(pkg == null ? "" : String.valueOf(pkg),
                        cls == null ? "" : String.valueOf(cls), true);
            }
        } catch (Exception ignored) {
            // Windows can disappear mid-enumeration; nothing useful to report.
        }
    }

    @Override
    public void onInterrupt() {
    }
}