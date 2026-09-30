package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

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

        Entry(ExternalPlugin plugin, String id, String title) {
            this.plugin = plugin;
            this.id = id;
            this.title = title;
        }
    }

    private static final long CACHE_TTL_MS = 60_000;
    private static long sFileCacheAt;
    private static List<Entry> sFileCache = new ArrayList<>();

    private ExternalActions() {
    }

    /** Stable host id for a discovered plugin. */
    public static String entryId(ExternalPlugin plugin) {
        String base;
        if (plugin.pluginId != null && !plugin.pluginId.isEmpty()) {
            base = plugin.pluginId;
        } else {
            base = plugin.packageName + "/" + plugin.className;
        }
        return "ext:" + base;
    }

    public static String metaString(ExternalPlugin plugin, String key, String def) {
        try {
            Bundle meta = plugin.meta;
            if (meta != null) {
                String v = meta.getString(key);
                if (v != null) return v;
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    public static List<Entry> sidebarEntries(Context context) {
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
        return out;
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
        return out;
    }

    public static String settingKey(Entry entry) {
        String key = metaString(entry.plugin, PluginContracts.META_SETTING_KEY, null);
        if (key == null || key.isEmpty()) {
            String pid = entry.plugin.pluginId;
            key = (pid == null || pid.isEmpty()) ? entry.id : pid;
        }
        return key;
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
            now = System.currentTimeMillis();
        } catch (Exception ignored) {
        }
        if (sFileCache != null && now - sFileCacheAt < CACHE_TTL_MS) {
            return new ArrayList<>(sFileCache);
        }
        List<Entry> out = new ArrayList<>();
        try {
            for (ExternalPlugin p : PluginHost.query(context, PluginContracts.ACTION_FILE_MENU)) {
                String title = p.label == null ? p.packageName : String.valueOf(p.label);
                out.add(new Entry(p, entryId(p), title));
            }
        } catch (Exception ignored) {
        }
        sFileCache = out;
        sFileCacheAt = now;
        return new ArrayList<>(out);
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
                try {
                    if (!file.getName().matches(pattern)) return false;
                } catch (Exception ignored) {
                    return false;
                }
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
                    android.net.Uri.fromFile(file));
            if (type != null) return type;
        } catch (Exception ignored) {
        }
        try {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String ext = name.substring(dot + 1).toLowerCase();
                String guessed = android.webkit.MimeTypeMap.getSingleton()
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
            List<Uri> out = new ArrayList<>();
            for (File f : files) {
                Uri uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider", f);
                if (uri == null) return null;
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
        return out;
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
        return out;
    }

    public static Entry findById(List<Entry> entries, String id) {
        if (entries == null || id == null) return null;
        for (Entry e : entries) {
            if (id.equals(e.id)) return e;
        }
        return null;
    }
}
