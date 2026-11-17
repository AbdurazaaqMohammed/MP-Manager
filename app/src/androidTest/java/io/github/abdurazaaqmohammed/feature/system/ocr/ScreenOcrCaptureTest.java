package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.system.ToolsHubActivity;

import static org.junit.Assert.*;

/** Exercises genuine Android projection consent, repeat captures and service cleanup. */
public class ScreenOcrCaptureTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;

    @Test public void repeatCaptureUsesOneProjectionAndStopsCleanly() throws Exception {
        context = instrumentation.getTargetContext();
        assertTrue(context.getPackageName().endsWith(".editorregression"));
        AccessibilityServiceInfo automationInfo = instrumentation.getUiAutomation().getServiceInfo();
        automationInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        instrumentation.getUiAutomation().setServiceInfo(automationInfo);
        assertTrue("Stage pinned OCR models", OcrModels.ready(context, OcrModels.find("en")));
        // Dismiss a system consent dialog left open by a previous failed run.
        shell("input keyevent 4");
        shell("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant " + context.getPackageName()
                + " android.permission.POST_NOTIFICATIONS");
        OcrModels.select(context, OcrModels.find("en"));
        instrumentation.runOnMainSync(OcrSession::clear);
        ScreenOcrActivity activity = (ScreenOcrActivity) instrumentation.startActivitySync(
                new Intent(context, ScreenOcrActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ToolsHubActivity hub = null;
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                View button = findText(activity.findViewById(R.id.ocr_content), context.getString(R.string.ocr_start));
                assertNotNull(button);
                button.performClick();
            });
            AccessibilityNodeInfo firstConsent = awaitAny(context.getString(R.string.ocr_continue), "Allow");
            boolean notificationsFirst = "Allow".equalsIgnoreCase(String.valueOf(firstConsent.getText()));
            click(firstConsent);
            if (notificationsFirst) clickNode(context.getString(R.string.ocr_continue));
            // Android 14+ exposes single-app vs entire-screen selection.
            clickIfPresent("A single app");
            clickIfPresent("Share one app");
            clickIfPresent("Entire screen");
            clickIfPresent("Share your entire screen");
            clickIfPresent("Share entire screen");
            AccessibilityNodeInfo confirmation = awaitAny("Start now", "Share screen", "Share", "Start recording");
            click(confirmation);
            awaitRunning();
            hub = (ToolsHubActivity) instrumentation.startActivitySync(new Intent(context, ToolsHubActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0) instrumentation.startActivitySync(new Intent(context, ToolsHubActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                click(awaitDescription(context.getString(R.string.ocr_capture_button)));
                AtomicReference<Bitmap> capture = new AtomicReference<>();
                long deadline = SystemClock.uptimeMillis() + 10000;
                do {
                    instrumentation.runOnMainSync(() -> capture.set(OcrSession.image()));
                    if (capture.get() != null) break;
                    SystemClock.sleep(50);
                } while (SystemClock.uptimeMillis() < deadline);
                assertNotNull("The floating button did not deliver a screenshot", capture.get());
                assertTrue(capture.get().getWidth() > 500);
                assertTrue(capture.get().getHeight() > 500);
                AtomicReference<Boolean> busy = new AtomicReference<>(true);
                deadline = SystemClock.uptimeMillis() + 45000;
                do {
                    instrumentation.runOnMainSync(() -> busy.set(OcrSession.busy()));
                    if (!busy.get()) break;
                    SystemClock.sleep(50);
                } while (SystemClock.uptimeMillis() < deadline);
                instrumentation.runOnMainSync(() -> {
                    assertNull(OcrSession.error());
                    assertNotNull("Automatic screen recognition did not finish", OcrSession.result());
                    assertFalse("The captured app screen should contain readable text", OcrSession.result().lines.isEmpty());
                });
                instrumentation.runOnMainSync(OcrSession::clear);
            }
            ScreenOcrService.stop(context);
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (ScreenOcrService.isRunning() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertFalse(ScreenOcrService.isRunning());
        } finally {
            ScreenOcrService.stop(context);
            ToolsHubActivity finalHub = hub;
            instrumentation.runOnMainSync(() -> {
                OcrSession.clear();
                activity.finish();
                if (finalHub != null) finalHub.finish();
            });
        }
    }

    private void shell(String command) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) {}
        }
    }

    private void awaitRunning() {
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (!ScreenOcrService.isRunning() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertTrue("Projection foreground service did not start", ScreenOcrService.isRunning());
    }

    private void clickIfPresent(String text) {
        long deadline = SystemClock.uptimeMillis() + 500;
        do {
            AccessibilityNodeInfo node = findNode(instrumentation.getUiAutomation().getRootInActiveWindow(), text, false);
            if (node != null) { click(node); return; }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
    }

    private void clickNode(String text) { click(awaitAny(text)); }

    private AccessibilityNodeInfo awaitDescription(String description) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
                AccessibilityNodeInfo node = findNode(window.getRoot(), description, true);
                if (node != null) return node;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        StringBuilder windows = new StringBuilder();
        for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
            windows.append(window).append(':').append(visibleText(window.getRoot()));
        }
        try {
            Field visible = ScreenOcrService.class.getDeclaredField("uiVisible");
            visible.setAccessible(true);
            windows.append(" uiVisible=").append(visible.get(null));
            Field current = ScreenOcrService.class.getDeclaredField("current");
            current.setAccessible(true);
            Object service = ((WeakReference<?>) current.get(null)).get();
            Field bubble = ScreenOcrService.class.getDeclaredField("bubble");
            bubble.setAccessible(true);
            View view = (View) bubble.get(service);
            windows.append(" bubbleVisibility=").append(view.getVisibility());
        } catch (Exception ignored) {}
        throw new AssertionError("Missing floating capture button; service running=" + ScreenOcrService.isRunning()
                + "; windows=" + windows);
    }

    private AccessibilityNodeInfo awaitAny(String... texts) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            for (String text : texts) {
                AccessibilityNodeInfo node = findNode(instrumentation.getUiAutomation().getRootInActiveWindow(), text, false);
                if (node != null) return node;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing consent button: " + Arrays.toString(texts)
                + "; visible: " + visibleText(instrumentation.getUiAutomation().getRootInActiveWindow()));
    }

    private void click(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        assertNotNull(node);
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        instrumentation.waitForIdleSync();
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String text, boolean description) {
        if (node == null) return null;
        CharSequence value = description ? node.getContentDescription() : node.getText();
        if (text.equalsIgnoreCase(value == null ? "" : value.toString())) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo match = findNode(node.getChild(index), text, description);
            if (match != null) return match;
        }
        return null;
    }

    private View findText(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View match = findText(group.getChildAt(i), text);
            if (match != null) return match;
        }
        return null;
    }

    private String visibleText(AccessibilityNodeInfo node) {
        if (node == null) return "no active window";
        StringBuilder text = new StringBuilder();
        if (node.getText() != null) text.append(node.getText()).append(" | ");
        for (int index = 0; index < node.getChildCount(); index++) text.append(visibleText(node.getChild(index)));
        return text.toString();
    }
}
