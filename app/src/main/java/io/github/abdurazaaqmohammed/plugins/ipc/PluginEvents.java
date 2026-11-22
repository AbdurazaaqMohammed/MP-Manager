package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Best-effort, consented metadata notifications; the host never waits for plugin code. */
public final class PluginEvents {
    private static final int MAX_RECEIVERS = 64;
    private static final AtomicBoolean LAUNCHED = new AtomicBoolean();
    private static final ThreadPoolExecutor DISPATCH = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "plugin-events");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    static {
        DISPATCH.allowCoreThreadTimeOut(true);
    }

    private PluginEvents() {
    }

    public static void appLaunched(Context context) {
        if (context != null && LAUNCHED.compareAndSet(false, true)) {
            Context app = context.getApplicationContext();
            io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry.setOnThemeChangedListener(
                    () -> themeChanged(app));
            dispatch(app, PluginContracts.EVENT_APP_LAUNCH, null);
        }
    }

    public static void fileOpened(Context context, String displayName, String mime) {
        Bundle payload = new Bundle();
        // Strip directory components even if a caller accidentally passes a path.
        String name = displayName == null ? "" : displayName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        payload.putString(PluginContracts.PAYLOAD_KEY_FILE_NAME, bounded(name, 128));
        payload.putString(PluginContracts.PAYLOAD_KEY_MIME, bounded(mime, 128));
        dispatch(context, PluginContracts.EVENT_FILE_OPENED, payload);
    }

    public static void themeChanged(Context context) {
        dispatch(context, PluginContracts.EVENT_THEME_CHANGED, null);
    }

    private static void dispatch(Context context, String event, Bundle payload) {
        if (context == null || !PluginHooks.isEvent(event)) return;
        Context app = context.getApplicationContext();
        Bundle copy = payload == null ? null : new Bundle(payload);
        try {
            DISPATCH.execute(() -> {
                List<PluginHost.ExternalPlugin> receivers = PluginHost.queryEventReceivers(app, false);
                Set<String> sent = new HashSet<>();
                int count = 0;
                for (PluginHost.ExternalPlugin receiver : receivers) {
                    if (count >= MAX_RECEIVERS) break;
                    if (!HookRegistry.supportsVersion(receiver)
                            || !HookRegistry.declaredHooks(receiver).contains(event)
                            || !PluginHost.eventAllowed(app, receiver.packageName, event)) continue;
                    if (!sent.add(receiver.packageName + "/" + receiver.className)) continue;
                    try {
                        Intent intent = PluginHost.explicitIntent(app, receiver, PluginContracts.ACTION_EVENT);
                        intent.putExtra(PluginContracts.EXTRA_HOOK_ID, event);
                        if (copy != null) intent.putExtra(PluginContracts.EXTRA_HOOK_PAYLOAD, copy);
                        // No ordered broadcasts, result callbacks, URI permissions, or background activities.
                        app.sendBroadcast(intent);
                        count++;
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ");
        return text.length() <= max ? text : text.substring(0, max);
    }
}
