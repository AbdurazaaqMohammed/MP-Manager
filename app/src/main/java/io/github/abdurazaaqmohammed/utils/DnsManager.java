package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class DnsManager {
    public static final String APPLY_ACTION = "io.github.abdurazaaqmohammed.MPManager.APPLY_DNS_PROFILE";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    private static final String PREFS_CUSTOM = "dns_custom_profiles";
    private static final String PREFS_ACTIVE = "dns_active_profile";

    public static class DnsProfile {
        public final String id;
        public final String name;
        public final String mode;
        public final String hostname;

        public DnsProfile(String id, String name, String mode, String hostname) {
            this.id = id;
            this.name = name;
            this.mode = mode;
            this.hostname = hostname == null ? "" : hostname;
        }

        public String describe() {
            if ("off".equals(mode)) return "Off";
            if ("opportunistic".equals(mode)) return "Automatic";
            return hostname == null || hostname.isEmpty() ? "Hostname" : hostname;
        }
    }

    public static List<DnsProfile> builtins() {
        List<DnsProfile> list = new ArrayList<>();
        list.add(new DnsProfile("off", "Off", "off", ""));
        list.add(new DnsProfile("auto", "Automatic", "opportunistic", ""));
        list.add(new DnsProfile("cloudflare", "Cloudflare", "hostname", "one.one.one.one"));
        list.add(new DnsProfile("google", "Google", "hostname", "dns.google"));
        list.add(new DnsProfile("adguard", "AdGuard", "hostname", "dns.adguard-dns.com"));
        list.add(new DnsProfile("quad9", "Quad9", "hostname", "dns.quad9.net"));
        list.add(new DnsProfile("opendns", "OpenDNS", "hostname", "doh.opendns.com"));
        return list;
    }

    public static List<DnsProfile> getProfiles(Context context) {
        List<DnsProfile> all = new ArrayList<>(builtins());
        try {
            String saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_CUSTOM, "[]");
            List<DnsProfile> custom = new Gson().fromJson(saved, new TypeToken<List<DnsProfile>>() {
            }.getType());
            if (custom != null) all.addAll(custom);
        } catch (Exception ignored) {
        }
        return all;
    }

    public static DnsProfile findProfile(Context context, String id) {
        if (id == null) return null;
        for (DnsProfile p : getProfiles(context)) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    public static void addCustom(Context context, String name, String hostname) {
        List<DnsProfile> custom = new ArrayList<>();
        try {
            String saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_CUSTOM, "[]");
            List<DnsProfile> loaded = new Gson().fromJson(saved, new TypeToken<List<DnsProfile>>() {
            }.getType());
            if (loaded != null) custom.addAll(loaded);
        } catch (Exception ignored) {
        }
        custom.add(new DnsProfile("custom_" + UUID.randomUUID().toString().substring(0, 8), name, "hostname", hostname));
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_CUSTOM, new Gson().toJson(custom)).apply();
    }

    public static void removeCustom(Context context, String id) {
        List<DnsProfile> custom = new ArrayList<>();
        try {
            String saved = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_CUSTOM, "[]");
            List<DnsProfile> loaded = new Gson().fromJson(saved, new TypeToken<List<DnsProfile>>() {
            }.getType());
            if (loaded != null) custom.addAll(loaded);
        } catch (Exception ignored) {
        }
        List<DnsProfile> kept = new ArrayList<>();
        for (DnsProfile p : custom) {
            if (!id.equals(p.id)) kept.add(p);
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_CUSTOM, new Gson().toJson(kept)).apply();
    }

    public static boolean isCustomId(String id) {
        return id != null && id.startsWith("custom_");
    }

    public static String currentMode(Context context) {
        try {
            String mode = Settings.Global.getString(context.getContentResolver(), "private_dns_mode");
            return mode == null ? "" : mode;
        } catch (Exception e) {
            return "";
        }
    }

    public static String currentHostname(Context context) {
        try {
            String host = Settings.Global.getString(context.getContentResolver(), "private_dns_specifier");
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    public static DnsProfile matchingProfile(Context context) {
        String mode = currentMode(context);
        String host = currentHostname(context);
        for (DnsProfile p : getProfiles(context)) {
            if (p.mode.equals(mode)) {
                if (!"hostname".equals(mode)) return p;
                if (p.hostname.equalsIgnoreCase(host)) return p;
            }
        }
        return null;
    }

    public static boolean canWriteDirect(Context context) {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean applyProfile(Context context, DnsProfile profile) {
        if (profile == null) return false;
        if (canWriteDirect(context)) {
            try {
                Settings.Global.putString(context.getContentResolver(), "private_dns_mode", profile.mode);
                Settings.Global.putString(context.getContentResolver(), "private_dns_specifier", profile.hostname == null ? "" : profile.hostname);
                rememberActive(context, profile.id);
                requestTileRefresh(context);
                return true;
            } catch (Exception ignored) {
            }
        }
        try {
            RootManager rm = RootManager.getInstance(context);
            try {
                rm.autoEnableRootIfAvailable();
            } catch (Exception ignored) {
            }
            if (rm.isRootAvailable() && rm.getWorkingMode() == RootManager.WorkingMode.ROOT) {
                String host = profile.hostname == null ? "" : profile.hostname.replace("'", "");
                RootManager.ShellResult r1 = rm.execute("settings put global private_dns_mode " + profile.mode, 10);
                RootManager.ShellResult r2 = rm.execute("settings put global private_dns_specifier '" + host + "'", 10);
                if (r1.isSuccess() && r2.isSuccess()) {
                    rememberActive(context, profile.id);
                    requestTileRefresh(context);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (runShizukuSettingsPut(context, "private_dns_mode", profile.mode)
                    && runShizukuSettingsPut(context, "private_dns_specifier", profile.hostname == null ? "" : profile.hostname)) {
                rememberActive(context, profile.id);
                requestTileRefresh(context);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean runShizukuSettingsPut(Context context, String key, String value) {
        try {
            if (!ShizukuManager.ready()) return false;
            String safe = value == null ? "" : value.replace("'", "");
            return ShizukuManager.shellOk(context, "settings put global " + key + " '" + safe + "'", 10);
        } catch (Exception e) {
            return false;
        }
    }

    public static DnsProfile applyNextProfile(Context context) {
        List<DnsProfile> all = getProfiles(context);
        if (all.isEmpty()) return null;
        DnsProfile current = matchingProfile(context);
        int next = 0;
        if (current != null) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id.equals(current.id)) {
                    next = (i + 1) % all.size();
                    break;
                }
            }
        } else {
            String active = activeId(context);
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id.equals(active)) {
                    next = (i + 1) % all.size();
                    break;
                }
            }
        }
        DnsProfile target = all.get(next);
        return applyProfile(context, target) ? target : null;
    }

    private static void rememberActive(Context context, String id) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREFS_ACTIVE, id).apply();
        } catch (Exception ignored) {
        }
    }

    public static String activeId(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_ACTIVE, "auto");
        } catch (Exception e) {
            return "auto";
        }
    }

    private static void requestTileRefresh(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                TileService.requestListeningState(context, new ComponentName(context, "io.github.abdurazaaqmohammed.tools.PrivateDnsTileService"));
            }
        } catch (Exception ignored) {
        }
    }

    public static void pinShortcut(Activity activity, DnsProfile profile) {
        if (Build.VERSION.SDK_INT < 26 || profile == null) {
            Extensions.showMessage(activity, activity.getString(R.string.dns_shortcut_o));
            return;
        }
        try {
            ShortcutManager sm = (ShortcutManager) activity.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                Extensions.showMessage(activity, activity.getString(R.string.dns_pinned_unsupported));
                return;
            }
            Intent intent = new Intent(APPLY_ACTION);
            intent.setClassName(activity.getPackageName(), "io.github.abdurazaaqmohammed.tools.WifiManagerActivity");
            intent.putExtra(EXTRA_PROFILE_ID, profile.id);
            ShortcutInfo info = new ShortcutInfo.Builder(activity, "dns_" + profile.id)
                    .setShortLabel(activity.getString(R.string.dns_applied, profile.name))
                    .setLongLabel(activity.getString(R.string.qs_private_dns_x, profile.name) + " (" + profile.describe() + ")")
                    .setIcon(Icon.createWithResource(activity, R.drawable.wifi_24px))
                    .setIntent(intent)
                    .build();
            sm.requestPinShortcut(info, null);
        } catch (Exception e) {
            Extensions.showMessage(activity, activity.getString(R.string.dns_shortcut_failed));
        }
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
