package com.example.mpextsample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHooks;

/**
 * Sample ACTION_EVENT receiver: logs host events. Events carry the stable
 * event id in EXTRA_HOOK_ID and a small primitive Bundle payload
 * (file_name / mime) in EXTRA_HOOK_PAYLOAD; no paths, no contents, no
 * URI grants.
 */
public class EventReceiver extends BroadcastReceiver {

    private static final String TAG = "MpSamplePlugin";

    @Override
    public void onReceive(Context context, Intent intent) {
        String eventId = intent == null
                ? null : intent.getStringExtra(PluginContracts.EXTRA_HOOK_ID);
        if (!PluginHooks.isEvent(eventId)) {
            return;
        }
        Bundle payload = intent.getBundleExtra(PluginContracts.EXTRA_HOOK_PAYLOAD);
        Log.i(TAG, "event=" + eventId
                + " entries=" + (payload == null ? 0 : payload.size())
                + " file_name=" + primitiveString(payload,
                        PluginContracts.PAYLOAD_KEY_FILE_NAME)
                + " mime=" + primitiveString(payload,
                        PluginContracts.PAYLOAD_KEY_MIME));
    }

    /** Primitive payload value reader (no reflection, no file access). */
    private static String primitiveString(Bundle payload, String key) {
        if (payload == null || !payload.containsKey(key)) return null;
        try {
            return payload.getString(key);
        } catch (ClassCastException ignored) {
            return null;
        }
    }
}
