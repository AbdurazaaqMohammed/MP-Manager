package com.example.mplugtemplate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHooks;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;

/**
 * ACTION_EVENT receiver: logs host events (app.launch, file.opened,
 * theme.changed). The event id rides in EXTRA_HOOK_ID; the payload is a
 * small primitive Bundle (file_name / mime) in EXTRA_HOOK_PAYLOAD, and
 * theme info (theme.changed) rides in the common EXTRA_THEME_* extras.
 * Events never carry raw paths, file contents or URI grants.
 */
public class EventReceiver extends BroadcastReceiver {

    private static final String TAG = "MpPluginTemplate";

    @Override
    public void onReceive(Context context, Intent intent) {
        String eventId = intent == null
                ? null : intent.getStringExtra(PluginContracts.EXTRA_HOOK_ID);
        if (!PluginHooks.isEvent(eventId)) {
            return;
        }
        Bundle payload = intent.getBundleExtra(PluginContracts.EXTRA_HOOK_PAYLOAD);
        PluginTheme.ThemeInfo theme = PluginTheme.from(intent);
        Log.i(TAG, "event=" + eventId
                + " entries=" + (payload == null ? 0 : payload.size())
                + " file_name=" + payloadString(payload,
                        PluginContracts.PAYLOAD_KEY_FILE_NAME)
                + " mime=" + payloadString(payload, PluginContracts.PAYLOAD_KEY_MIME)
                + " theme=" + theme.themeId + " dark=" + theme.dark);
    }

    /** Primitive payload value reader (no reflection, no file access). */
    private static String payloadString(Bundle payload, String key) {
        if (payload == null || !payload.containsKey(key)) return null;
        try {
            return payload.getString(key);
        } catch (ClassCastException ignored) {
            return null;
        }
    }
}
