package io.github.abdurazaaqmohammed.plugins.ipc;

import android.app.Activity;
import android.content.Intent;

/**
 * Primitive wire bounds and small result helpers for the plugin IPC
 * protocol. Pure string/number checks; no parsing, no dependencies.
 */
public final class PluginRequests {

    /** Max marshalled size of the EXTRA_HOOK_PAYLOAD Bundle (16 KiB). */
    public static final int MAX_HOOK_PAYLOAD_BYTES = 16 * 1024;

    /** Max characters of a single manifest meta-data value (e.g. META_HOOKS). */
    public static final int MAX_META_VALUE_CHARS = 2048;

    /** Max declared hooks/events per plugin manifest. */
    public static final int MAX_DECLARED_HOOKS = 32;

    /** Max characters of a plugin id, class-derived id or package name. */
    public static final int MAX_ID_CHARS = 128;

    /** Max characters of a declared label/title. */
    public static final int MAX_LABEL_CHARS = 128;

    private PluginRequests() {
    }

    /** True when the payload byte length fits the 16 KiB bound (null is OK). */
    public static boolean isPayloadSizeOk(byte[] payload) {
        return payload == null || payload.length <= MAX_HOOK_PAYLOAD_BYTES;
    }

    /** True when a payload value is a primitive (String/Number/Boolean). */
    public static boolean isPrimitiveValueOk(Object value) {
        return value instanceof String || value instanceof Number
                || value instanceof Boolean;
    }

    /** True when a manifest meta-data value fits the 2048-char bound. */
    public static boolean isMetaValueOk(String value) {
        return value == null || value.length() <= MAX_META_VALUE_CHARS;
    }

    /** True when id is non-empty, at most 128 chars and separator-free. */
    public static boolean isIdOk(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_CHARS) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '/' || c == '@' || c == '\\' || c < 0x20 || c == 0x7F) return false;
        }
        return true;
    }

    /** True when a label is null or at most 128 chars. */
    public static boolean isLabelOk(String label) {
        return label == null || label.length() <= MAX_LABEL_CHARS;
    }

    /** True when the activity result code is RESULT_OK. */
    public static boolean isResultOk(int resultCode) {
        return resultCode == Activity.RESULT_OK;
    }

    /** Empty result intent carrying a short human-readable message. */
    public static Intent resultIntent(String message) {
        Intent out = new Intent();
        if (message != null) {
            out.putExtra(PluginContracts.EXTRA_MESSAGE, message);
        }
        return out;
    }
}
