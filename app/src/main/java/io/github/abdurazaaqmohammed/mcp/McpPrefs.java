package io.github.abdurazaaqmohammed.mcp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.UUID;

import io.github.abdurazaaqmohammed.utils.UiPrefs;

/** Preferences for the MCP service (port, home folder, auth token, path rules). */
public final class McpPrefs {

    public static final int DEFAULT_PORT = 8787;

    private McpPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static int getPort(Context context) {
        return prefs(context).getInt("mcp_port", DEFAULT_PORT);
    }

    public static void setPort(Context context, int port) {
        prefs(context).edit().putInt("mcp_port", port).apply();
    }

    /** Home folder: base for relative paths and default output location (MT-style). */
    public static File getHome(Context context) {
        String saved = prefs(context).getString("mcp_home", null);
        return TextUtils.isEmpty(saved) ? new File(new File(Environment.getExternalStorageDirectory(), UiPrefs.appPathDir(context, UiPrefs.defaultAppDir())), "mcp") : new File(saved);
    }

    public static void setHome(Context context, String path) {
        prefs(context).edit().putString("mcp_home", path).apply();
    }

    public static boolean authEnabled(Context context) {
        return prefs(context).getBoolean("mcp_auth_enabled", false);
    }

    public static void setAuthEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("mcp_auth_enabled", enabled).apply();
    }

    public static String getToken(Context context) {
        String token = prefs(context).getString("mcp_token", null);
        if (token == null || token.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "");
            prefs(context).edit().putString("mcp_token", token).apply();
        }
        return token;
    }

    public static void regenerateToken(Context context) {
        prefs(context).edit().putString("mcp_token", UUID.randomUUID().toString().replace("-", "")).apply();
    }

    /** Ordered path rules, first match wins. Serialized as a JSON array of {path, mode}. */
    public static String getRulesJson(Context context) {
        return prefs(context).getString("mcp_rules", "[]");
    }

    public static void setRulesJson(Context context, String json) {
        prefs(context).edit().putString("mcp_rules", json).apply();
    }
}
