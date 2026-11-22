package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost.ExternalPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared discovery/matching/staging for external plugins, one entry point
 * per host area. Entry ids are stable ({@code "ext:" + pluginId or
 * package/class}) so sidebar order, file-menu order, and editor menu order
 * persist them like built-ins.
 */
public final class ExternalActions {

    /** One external contribution, host-side view. */
    public static final class Entry {
        public final ExternalPlugin plugin;
        public final String id;
        public final String title;
        public final String action;
        public final String hookId;

        Entry(ExternalPlugin plugin, String id, String title) {
            this(plugin, id, title, null, null);
        }

        Entry(ExternalPlugin plugin, String id, String title, String action, String hookId) {
            this.plugin = plugin;
            this.id = id;
            this.title = PluginMetadata.title(title, plugin.packageName);
            this.action = action;
            this.hookId = hookId;
        }
    }

    private static final long CACHE_TTL_MS = 60_000;
    private static long sFileCacheAt;
    private static List<Entry> sFileCache = new ArrayList<>();
    private static long sSidebarCacheAt;
    private static List<Entry> sSidebarCache = new ArrayList<>();

    private ExternalActions() {
    }

    /** Stable host id for a discovered plugin. */
    public static String entryId(ExternalPlugin plugin) {
        String base;
        String declared = plugin.pluginId;
        if (declared != null && declared.length() <= 128
                && declared.matches("[A-Za-z0-9._-]+")) {
            base = declared;
        } else {
            base = plugin.packageName + "/" + plugin.className;
        }
        return "ext:" + base;
    }

    /** Build the wire request once for legacy actions and generic hooks alike. */
    public static Intent intent(Context context, Entry entry, String legacyAction) {
        Intent intent = PluginHost.explicitIntent(context, entry.plugin,
                entry.action == null ? legacyAction : entry.action);
        if (entry.hookId != null) intent.putExtra(PluginContracts.EXTRA_HOOK_ID, entry.hookId);
        return intent;
    }

    private static List<Entry> available(Context context, List<Entry> entries) {
        java.util.Map<String, Entry> seen = new java.util.LinkedHashMap<>();
        for (Entry entry : entries) {
            if (PluginHost.isDisabled(context, entry.plugin.packageName)) continue;
            Entry previous = seen.get(entry.id);
            if (previous == null || (!PluginHost.isTrusted(context, previous.plugin.packageName)
                    && PluginHost.isTrusted(context, entry.plugin.packageName))) {
                seen.put(entry.id, entry);
            }
        }
        return new ArrayList<>(seen.values());
    }

    public static String metaString(ExternalPlugin plugin, String key, String def) {
        try {
            Bundle meta = plugin.meta;
            if (meta != null) {
                Object value = meta.get(key);
                String v = value instanceof String ? (String) value
                        : value instanceof Integer ? String.valueOf(value)
                        : value instanceof Boolean ? String.valueOf(value) : null;
                if (v != null && PluginRequests.isMetaValueOk(v)) return v;
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    /**
     * Sidebar entries, cached briefly like the file entries: every sidebar
     * rebuild calls this 2-3x and each miss is a PackageManager IPC. Call
     * {@link #clearSidebarCache()} after installs/removals for a fresh query.
     */
    public static synchronized List<Entry> sidebarEntries(Context context) {
        long now = 0;
        try {
            now = android.os.SystemClock.elapsedRealtime();
        } catch (Exception ignored) {
        }
        if (sSidebarCacheAt != 0 && now - sSidebarCacheAt < CACHE_TTL_MS) {
            return available(context, sSidebarCache);
        }
        List<Entry> out = new ArrayList<>();
        try {
            for (ExternalPlugin p : PluginHost.query(context, PluginContracts.ACTION_SIDEBAR_OPEN)) {
                String title = metaString(p, PluginContracts.META_TITLE, null);
                if (title == null || title.isEmpty()) {
                    title = p.label == null ? p.packageName : String.valueOf(p.label);
                }
                out.add(new Entry(p, entryId(p), title));
            }
        } catch (Exception ignored) {
        }
        out.addAll(HookRegistry.entries(context, PluginContracts.HOOK_SIDEBAR_FOOTER));
        sSidebarCache = out;
        sSidebarCacheAt = now;
        return available(context, out);
    }

    /** Drops the cached sidebar entries so the next query hits PackageManager. */
    public static synchronized void clearSidebarCache() {
        sSidebarCacheAt = 0;
    }

    /** Drops every discovery cache after package or trust changes. */
    public static synchronized void clearAllCaches() {
        sSidebarCacheAt = 0;
        sFileCacheAt = 0;
    }

    public static List<Entry> settingEntries(Context context) {
        List<Entry> out = new ArrayList<>();
        try {
            for (ExternalPlugin p : PluginHost.query(context, PluginContracts.ACTION_SETTING_CONFIG)) {
                String title = metaString(p, PluginContracts.META_TITLE, null);
                if (title == null || title.isEmpty()) {
                    title = p.label == null ? p.packageName : String.valueOf(p.label);
                }
                out.add(new Entry(p, entryId(p), title));
            }
        } catch (Exception ignored) {
        }
        return available(context, out);
    }

    public static String settingKey(Entry entry) {
        return PluginMetadata.settingKey(entry.plugin.packageName, declaredSettingKey(entry));
    }

    public static String declaredSettingKey(Entry entry) {
        String key = metaString(entry.plugin, PluginContracts.META_SETTING_KEY, null);
        if (key == null || key.isEmpty()) {
            String pid = entry.plugin.pluginId;
            key = (pid == null || pid.isEmpty()) ? entry.id : pid;
        }
        return key;
    }

    public static void migrateSetting(Context context, Entry entry,
                                      android.content.SharedPreferences settings) {
        String legacyKey = declaredSettingKey(entry);
        String key = settingKey(entry);
        if (!legacyKey.startsWith("plugin.") || settings.contains(key)
                || !settings.contains(legacyKey)
                || !PluginHost.isTrusted(context, entry.plugin.packageName)) return;
        int owners = 0;
        for (Entry candidate : settingEntries(context)) {
            if (legacyKey.equals(declaredSettingKey(candidate))) owners++;
        }
        if (owners != 1) return;
        Object value = settings.getAll().get(legacyKey);
        android.content.SharedPreferences.Editor editor = settings.edit();
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else return;
        editor.remove(legacyKey).apply();
    }

    public static boolean settingIsAction(Entry entry) {
        return "action".equalsIgnoreCase(
                metaString(entry.plugin, PluginContracts.META_SETTING_TYPE, "boolean"));
    }

    /** File entries matching every file in the selection. Cached briefly. */
    public static List<Entry> fileEntries(Context context, List<File> files) {
        List<Entry> all = cachedFileEntries(context);
        List<Entry> out = new ArrayList<>();
        if (files == null || files.isEmpty()) return out;
        for (Entry e : all) {
            boolean ok = true;
            for (File f : files) {
                if (!fileMatches(context, e.plugin, f)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(e);
        }
        return out;
    }

    /** All file-action entries, unfiltered (order list, customizer). */
    public static synchronized List<Entry> cachedFileEntries(Context context) {
        long now = 0;
        try {
            now = android.os.SystemClock.elapsedRealtime();
        } catch (Exception ignored) {
        }
        if (sFileCacheAt != 0 && now - sFileCacheAt < CACHE_TTL_MS) {
            return available(context, sFileCache);
        }
        List<Entry> out = new ArrayList<>();
        try {
            for (ExternalPlugin p : PluginHost.query(context, PluginContracts.ACTION_FILE_MENU)) {
                String title = p.label == null ? p.packageName : String.valueOf(p.label);
                out.add(new Entry(p, entryId(p), title));
            }
        } catch (Exception ignored) {
        }
        out.addAll(HookRegistry.entries(context, PluginContracts.HOOK_FILE_MENU_EXTRA));
        sFileCache = out;
        sFileCacheAt = now;
        return available(context, out);
    }

    public static List<String> fileMenuIds(Context context) {
        List<String> ids = new ArrayList<>();
        for (Entry e : cachedFileEntries(context)) ids.add(e.id);
        return ids;
    }

    public static String fileMenuLabel(Context context, String id) {
        for (Entry e : cachedFileEntries(context)) {
            if (e.id.equals(id)) return e.title;
        }
        return null;
    }

    public static boolean fileMatches(Context context, ExternalPlugin plugin, File file) {
        try {
            if (file == null || !file.isFile()) return false;
            Bundle meta = plugin.meta;
            String mimeFilter = meta == null ? "" : String.valueOf(meta.getString(
                    PluginContracts.META_FILE_MIME, ""));
            String pattern = meta == null ? "" : String.valueOf(meta.getString(
                    PluginContracts.META_FILE_PATTERN, ""));
            if (mimeFilter == null) mimeFilter = "";
            if (pattern == null) pattern = "";
            if (!mimeFilter.isEmpty()) {
                String mime = mimeOf(context, file);
                boolean any = false;
                for (String part : mimeFilter.split(",")) {
                    part = part.trim();
                    if (part.isEmpty()) continue;
                    if ("*/*".equals(part) || mimeMatches(mime, part)) {
                        any = true;
                        break;
                    }
                }
                if (!any) return false;
            }
            if (!pattern.isEmpty()) {
                if (!PluginMetadata.filenameMatches(pattern, file.getName())) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean mimeMatches(String mime, String filter) {
        if (mime == null) mime = "";
        filter = filter.trim();
        if (filter.endsWith("/*")) {
            String prefix = filter.substring(0, filter.length() - 1);
            return mime.startsWith(prefix);
        }
        return mime.equalsIgnoreCase(filter);
    }

    private static String mimeOf(Context context, File file) {
        try {
            String type = context.getContentResolver().getType(
                    Uri.fromFile(file));
            if (type != null) return type;
        } catch (Exception ignored) {
        }
        try {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String ext = name.substring(dot + 1).toLowerCase();
                String guessed = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext);
                if (guessed != null) return guessed;
            }
        } catch (Exception ignored) {
        }
        return "application/octet-stream";
    }

    /**
     * Content URIs for the files via the host FileProvider, or null when any
     * file falls outside the configured roots (then the action is not offered).
     */
    public static List<Uri> stageUris(Context context, List<File> files) {
        try {
            if (files == null || files.isEmpty() || files.size() > 64) return null;
            List<Uri> out = new ArrayList<>();
            int metadataBytes = 0;
            for (File f : files) {
                if (f == null) return null;
                Uri uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider", f);
                if (uri == null) return null;
                metadataBytes += (uri.toString() + f.getName())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (metadataBytes > 64 * 1024) return null;
                out.add(uri);
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<Entry> editorEntries(Context context) {
        List<Entry> out = new ArrayList<>();
        try {
            for (ExternalPlugin p : PluginHost.query(context, PluginContracts.ACTION_EDITOR)) {
                String title = metaString(p, PluginContracts.META_TITLE, null);
                if (title == null || title.isEmpty()) {
                    title = p.label == null ? p.packageName : String.valueOf(p.label);
                }
                out.add(new Entry(p, entryId(p), title));
            }
        } catch (Exception ignored) {
        }
        out.addAll(HookRegistry.entries(context, PluginContracts.HOOK_EDITOR_ACTIONS));
        return available(context, out);
    }

    public static List<Entry> apkEntries(Context context) {
        List<Entry> out = new ArrayList<>();
        try {
            for (ExternalPlugin p : PluginHost.query(context, PluginContracts.ACTION_APK)) {
                String title = metaString(p, PluginContracts.META_TITLE, null);
                if (title == null || title.isEmpty()) {
                    title = p.label == null ? p.packageName : String.valueOf(p.label);
                }
                out.add(new Entry(p, entryId(p), title));
            }
        } catch (Exception ignored) {
        }
        out.addAll(HookRegistry.entries(context, PluginContracts.HOOK_APK_DIALOG_MORE));
        return available(context, out);
    }

    public static Entry findById(List<Entry> entries, String id) {
        if (entries == null || id == null) return null;
        for (Entry e : entries) {
            if (id.equals(e.id)) return e;
        }
        return null;
    }
}
