package org.lsposed.lspatch.share.remote;

import android.os.Bundle;
import android.util.Log;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matrix.vector.ipc.IRemotePreferenceCallback;

/**
 * The live-update path, ported from {@code InjectedModuleService}'s callback bookkeeping.
 *
 * <p>One of these is shared, per module, between that module's read service ({@link
 * LSPatchModuleService}, where a hook subscribes) and its write service ({@link LSPatchXposedService},
 * where the companion app writes). When the companion writes a group, every hook that subscribed to
 * that group <b>for the same Android user</b> is handed the diff — the same shape
 * {@code RemotePreferences.Editor} produced — so it stops serving values the companion has replaced.
 * Without it a hooked process keeps the old values until its process restarts.</p>
 */
public class PreferenceChangeNotifier {

    private static final String TAG = "LSPatch-RemotePrefs";

    private static final class Subscriber {
        final int userId;
        final IRemotePreferenceCallback callback;

        Subscriber(int userId, IRemotePreferenceCallback callback) {
            this.userId = userId;
            this.callback = callback;
        }
    }

    // group -> subscribers; a group a module app never touches never allocates a set.
    private final Map<String, Set<Subscriber>> callbacks = new ConcurrentHashMap<>();

    public void subscribe(String group, int userId, IRemotePreferenceCallback callback) {
        Set<Subscriber> groupCallbacks = callbacks.computeIfAbsent(group, g -> ConcurrentHashMap.newKeySet());
        Subscriber subscriber = new Subscriber(userId, callback);
        groupCallbacks.add(subscriber);
        try {
            callback.asBinder().linkToDeath(() -> groupCallbacks.remove(subscriber), 0);
        } catch (Throwable t) {
            Log.w(TAG, "subscribe linkToDeath failed", t);
        }
    }

    /** Fires the diff to every subscriber of {@code group} in {@code userId}. */
    public void notifyChanged(String group, int userId, Bundle diff) {
        Set<Subscriber> groupCallbacks = callbacks.get(group);
        if (groupCallbacks == null) return;
        for (Subscriber subscriber : groupCallbacks) {
            if (subscriber.userId != userId) continue;
            try {
                subscriber.callback.onRemotePreferencesChanged(diff);
            } catch (Throwable t) {
                groupCallbacks.remove(subscriber);
            }
        }
    }
}
