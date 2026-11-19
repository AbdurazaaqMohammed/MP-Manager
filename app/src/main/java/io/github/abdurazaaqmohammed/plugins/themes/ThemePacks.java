package io.github.abdurazaaqmohammed.plugins.themes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Host facade for external theme packs. A provider is a normal installed
 * app exposing the fixed one-row {@code content://<authority>/theme}
 * ContentProvider described in {@link ThemePackContracts}.
 *
 * <p>All methods may block (PackageManager/ContentResolver IO) and are
 * meant to be called from a UI worker; none of them throws: failures come
 * back as {@code false}/{@code null}. The active pack is ingested into the
 * private {@link ThemePackStore} at import time, so {@link #active} never
 * touches provider processes again (provider uninstall or certificate
 * rotation simply deactivates it). Trust is an independent signer pin in
 * its own prefs namespace, separate from the external-plugin pins in
 * {@code PluginHost}.
 */
public final class ThemePacks {

    private static final String PREFS_ACTIVE = "theme_packs";
    private static final String ACTIVE_KEY = "active_package";

    private static volatile Snapshot sCache;
    private record Discovery(List<Entry> entries, long expiresAt) {}
    private static volatile Discovery discovered;

    private ThemePacks() {
    }

    // ---------- types ----------

    /** One discovered provider app (PM label until import). */
    public static final class Entry {
        public final String packageName;
        public final CharSequence label;
        public final Drawable icon;

        Entry(String packageName, CharSequence label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    /** Immutable view of the active ingested pack. */
    public static final class Snapshot {
        public final String packageName;
        public final String name;
        public final Boolean dark;        // null = unspecified
        public final Integer accent;      // ARGB, null = none
        public final Integer scrim;       // ARGB, null = none
        public final Map<String, Integer> colors;   // unmodifiable, allowlisted tokens
        public final File wallpaper;      // private-store copy, null = none
        public final File preview;        // private-store copy, null = none

        private final Map<String, Map<String, String>> strings;

        Snapshot(String packageName, String name, Boolean dark, Integer accent,
                 Integer scrim, Map<String, Integer> colors,
                 Map<String, Map<String, String>> strings, File wallpaper,
                 File preview) {
            this.packageName = packageName;
            this.name = name;
            this.dark = dark;
            this.accent = accent;
            this.scrim = scrim;
            this.colors = colors;
            this.strings = strings;
            this.wallpaper = wallpaper;
            this.preview = preview;
        }

        /**
         * Localized label for an allowlisted key ({@code tab.files},
         * {@code tab.root}, {@code tab.general}, {@code tab.behavior},
         * {@code tab.other}, {@code settings.theme}). Resolution: exact
         * BCP47 tag, then same language, then English, then any entry.
         * Null when the key is absent or not allowlisted.
         */
        public String label(String token, Locale locale) {
            Map<String, String> locales = strings.get(
                    ThemePackParser.canonicalLabelKey(token));
            if (locales == null || locales.isEmpty()) return null;
            if (locale != null) {
                String tag = locale.toLanguageTag();
                String v = matchLocale(locales, tag);
                if (v != null) return v;
                String language = locale.getLanguage();
                if (language != null && !language.isEmpty()) {
                    v = matchLocale(locales, language);
                    if (v != null) return v;
                }
            }
            String v = matchLocale(locales, "en");
            if (v != null) return v;
            return locales.values().iterator().next();
        }

        private static String matchLocale(Map<String, String> locales, String tag) {
            for (Map.Entry<String, String> e : locales.entrySet()) {
                if (e.getKey().equalsIgnoreCase(tag)) return e.getValue();
            }
            // Language prefix: "en" matches "en-US" and vice versa.
            int dash = tag.indexOf('-');
            String language = dash > 0 ? tag.substring(0, dash) : tag;
            if (!language.equals(tag)) {
                for (Map.Entry<String, String> e : locales.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(language)) return e.getValue();
                }
            }
            for (Map.Entry<String, String> e : locales.entrySet()) {
                String key = e.getKey();
                int keyDash = key.indexOf('-');
                String keyLanguage = keyDash > 0 ? key.substring(0, keyDash) : key;
                if (keyLanguage.equalsIgnoreCase(language)) return e.getValue();
            }
            return null;
        }
    }

    // ---------- discovery ----------

    /** Installed provider apps (deduplicated by package). Blocking, cheap. */
    public static List<Entry> discover(Context context) {
        Discovery cached = discovered;
        long now = SystemClock.elapsedRealtime();
        if (cached != null && now < cached.expiresAt()) return cached.entries();
        List<Entry> out = new ArrayList<>();
        try {
            context = context.getApplicationContext();
            PackageManager pm = context.getPackageManager();
            Intent query = new Intent(ThemePackContracts.ACTION_THEME)
                    .addCategory(Intent.CATEGORY_DEFAULT);
            List<ResolveInfo> infos = pm.queryIntentActivities(query,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (infos == null) return out;
            Set<String> seen = new HashSet<>();
            for (ResolveInfo info : infos) {
                if (info == null || info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || !info.activityInfo.exported || !seen.add(pkg)) continue;
                if (!ownsProvider(pm, pkg, providerAuthority(pm, pkg))) continue;
                CharSequence label = null;
                try {
                    label = info.loadLabel(pm);
                } catch (Exception ignored) {
                }
                if (label == null) label = pkg;
                Drawable icon = null;
                try {
                    icon = info.loadIcon(pm);
                } catch (Exception ignored) {
                }
                if (icon == null) {
                    try {
                        icon = pm.getDefaultActivityIcon();
                    } catch (Exception ignored) {
                    }
                }
                out.add(new Entry(pkg, label, icon));
            }
        } catch (Exception ignored) {
        }
        List<Entry> entries = List.copyOf(out);
        discovered = new Discovery(entries, now + 15_000);
        return entries;
    }

    // ---------- import ----------

    /**
     * Validates, ingests and activates the provider's theme. Enforces: the
     * application meta-data authority resolves to the entry's own package,
     * the row contract version, allowlists/limits, the exact
     * {@code content://<authority>/wallpaper} and {@code /preview} forms,
     * size/dimension/pixel caps with PNG/JPEG decode validation, and the
     * independent signer pin (first import pins the current certificate;
     * later imports of the same package fail when the certificate changed).
     * Persistence is staged and committed atomically: the previous
     * snapshot/wallpaper/preview survive a failed import untouched.
     */
    public static boolean importAndSelect(Context context, Entry entry) {
        File stagedWallpaper = null;
        File stagedPreview = null;
        try {
            if (context == null || entry == null || entry.packageName == null
                    || entry.packageName.isEmpty()) {
                return false;
            }
            context = context.getApplicationContext();
            PackageManager pm = context.getPackageManager();
            String pkg = entry.packageName;

            String authority = providerAuthority(pm, pkg);
            if (!ownsProvider(pm, pkg, authority)) return false;
            String signer = PluginHost.certDigest(context, pkg);
            if (signer == null || !pinAllows(context, pkg, signer)) return false;

            Uri themeUri = Uri.parse("content://" + authority + "/"
                    + ThemePackContracts.PATH_THEME);
            String id;
            String name;
            Boolean dark;
            Integer accent;
            Integer scrim;
            Map<String, Integer> colors;
            Map<String, Map<String, String>> strings;
            String wallpaperColumn;
            String previewColumn;
            try (Cursor c = context.getContentResolver().query(
                    themeUri, null, null, null, null)) {
                if (c == null || !c.moveToFirst()) return false;
                if (c.getCount() != 1) return false;
                Long contract = longColumn(c, ThemePackContracts.COLUMN_CONTRACT);
                if (contract == null
                        || contract != ThemePackContracts.CONTRACT_VERSION) {
                    return false;
                }
                id = ThemePackParser.parseId(stringColumn(c, ThemePackContracts.COLUMN_ID));
                name = ThemePackParser.parseName(stringColumn(c, ThemePackContracts.COLUMN_NAME));
                dark = ThemePackParser.parseDark(longOrString(c, ThemePackContracts.COLUMN_DARK));
                accent = ThemePackParser.parseColorValue(
                        longOrString(c, ThemePackContracts.COLUMN_ACCENT));
                scrim = ThemePackParser.parseColorValue(
                        longOrString(c, ThemePackContracts.COLUMN_SCRIM));
                colors = ThemePackParser.parseColors(
                        stringColumn(c, ThemePackContracts.COLUMN_COLORS));
                strings = ThemePackParser.parseStrings(
                        stringColumn(c, ThemePackContracts.COLUMN_STRINGS));
                wallpaperColumn = stringColumn(c, ThemePackContracts.COLUMN_WALLPAPER);
                previewColumn = stringColumn(c, ThemePackContracts.COLUMN_PREVIEW);
            } catch (ThemePackParser.ParseException e) {
                return false;
            }
            // Accent-only, label-only or color-only rows are all valid;
            // only a fully empty row is rejected.
            if (accent == null && dark == null && scrim == null
                    && colors.isEmpty() && strings.isEmpty()
                    && empty(wallpaperColumn) && empty(previewColumn)) {
                return false;
            }
            // The scrim column is authoritative; keep it mirrored in colors
            // so both access paths work. Reverse: token-only providers get
            // the scrim field populated from wallpaper_scrim.
            if (scrim != null) {
                colors.put("wallpaper_scrim", scrim);
            } else {
                Integer tokenScrim = colors.get("wallpaper_scrim");
                if (tokenScrim != null) scrim = tokenScrim;
            }

            if (name == null) {
                try {
                    CharSequence appLabel = pm.getApplicationLabel(
                            pm.getApplicationInfo(pkg, 0));
                    name = ThemePackParser.parseName(appLabel == null
                            ? null : String.valueOf(appLabel));
                } catch (Exception ignored) {
                }
                if (name == null) name = id;
            }

            // Stage images outside any monitor: provider streams can be slow.
            if (!empty(wallpaperColumn)) {
                // Exact endpoint form only, served by the same authority.
                if (!("content://" + authority + "/"
                        + ThemePackContracts.PATH_WALLPAPER)
                        .equals(wallpaperColumn)) {
                    return false;
                }
                stagedWallpaper = stageValidatedImage(context,
                        Uri.parse(wallpaperColumn),
                        ThemePackContracts.MAX_WALLPAPER_BYTES,
                        ThemePackContracts.MAX_IMAGE_DIMENSION_PX,
                        ThemePackContracts.MAX_IMAGE_PIXELS,
                        context.getFilesDir(), pkg);
                if (stagedWallpaper == null) return false;
            }
            if (!empty(previewColumn)) {
                if (!("content://" + authority + "/"
                        + ThemePackContracts.PATH_PREVIEW)
                        .equals(previewColumn)) {
                    return false;
                }
                stagedPreview = stageValidatedImage(context,
                        Uri.parse(previewColumn),
                        ThemePackContracts.MAX_PREVIEW_BYTES,
                        ThemePackContracts.MAX_PREVIEW_DIMENSION_PX,
                        ThemePackContracts.MAX_PREVIEW_PIXELS,
                        context.getFilesDir(), pkg);
                if (stagedPreview == null) return false;
            }

            ThemePackStore.StoredSnapshot stored = new ThemePackStore.StoredSnapshot();
            stored.id = id;
            stored.name = name;
            stored.dark = dark;
            stored.accent = accent;
            stored.scrim = scrim;
            stored.colors = colors;
            stored.strings = strings;

            synchronized (ThemePacks.class) {
                // Monitor held only for the local commit; no provider IO inside.
                if (!signer.equals(PluginHost.certDigest(context, pkg))
                        || !pinAllows(context, pkg, signer)) return false;
                stored.signer = signer;
                if (!ThemePackStore.commit(context.getFilesDir(), pkg, stored,
                        stagedWallpaper, stagedPreview)) {
                    return false;
                }
                stagedWallpaper = null; // consumed by the commit
                stagedPreview = null;

                activePrefs(context).edit().putString(ACTIVE_KEY, pkg).apply();
                sCache = buildSnapshot(context, pkg, stored);
                return true;
            }
        } catch (Exception e) {
            return false;
        } finally {
            ThemePackStore.deleteQuietly(stagedWallpaper);
            ThemePackStore.deleteQuietly(stagedPreview);
        }
    }

    // ---------- access ----------

    /** Currently active pack, or null. Verifies install + signer pin lazily. */
    public static synchronized Snapshot active(Context context) {
        Snapshot cached = sCache;
        if (cached != null) return cached;
        try {
            if (context == null) return null;
            context = context.getApplicationContext();
            String pkg = activePrefs(context).getString(ACTIVE_KEY, null);
            if (pkg == null || pkg.isEmpty()) return null;
            ThemePackStore.StoredSnapshot stored = ThemePackStore.readSnapshot(context.getFilesDir(), pkg);
            if (stored == null || stored.signer == null
                    || !stored.signer.equals(PluginHost.certDigest(context, pkg))) {
                activePrefs(context).edit().remove(ACTIVE_KEY).apply();
                ThemePackStore.deletePack(context.getFilesDir(), pkg);
                return null;
            }
            Snapshot snapshot = buildSnapshot(context, pkg, stored);
            sCache = snapshot;
            return snapshot;
        } catch (Exception e) {
            return null;
        }
    }

    /** Clears the active pack. The ingested copy is kept until removal. */
    public static synchronized void deactivate(Context context) {
        try {
            context = context.getApplicationContext();
            activePrefs(context).edit().remove(ACTIVE_KEY).apply();
        } catch (Exception ignored) {
        }
        sCache = null;
    }

    // ---------- receiver hooks ----------

    /** Package uninstalled: drop its ingested copy and pin; clear if active. */
    public static synchronized void onPackageRemoved(Context context, String packageName) {
        discovered = null;
        try {
            if (context == null || packageName == null) return;
            context = context.getApplicationContext();
            String active = activePrefs(context).getString(ACTIVE_KEY, null);
            if (packageName.equals(active)) {
                activePrefs(context).edit().remove(ACTIVE_KEY).apply();
            }
            ThemePackStore.deletePack(context.getFilesDir(), packageName);
        } catch (Exception ignored) {
        }
        sCache = null;
    }

    /** Package installed/updated: cached snapshots re-verify lazily. */
    public static synchronized void onPackageReplaced(Context context, String packageName) {
        discovered = null;
        if (packageName == null) return;
        try {
            Snapshot cached = sCache;
            if (cached != null && packageName.equals(cached.packageName)) {
                sCache = null;
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- internals ----------

    private static Snapshot buildSnapshot(Context context, String pkg,
                                          ThemePackStore.StoredSnapshot stored) {
        Map<String, Integer> colors = stored.colors == null
                ? Collections.<String, Integer>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(stored.colors));
        Map<String, Map<String, String>> strings = new LinkedHashMap<>();
        if (stored.strings != null) {
            for (Map.Entry<String, Map<String, String>> e : stored.strings.entrySet()) {
                if (e.getValue() == null) continue;
                strings.put(e.getKey(),
                        Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
            }
        }
        File wallpaper = ThemePackStore.wallpaperFile(context.getFilesDir(),
                pkg, stored.wallpaperName);
        if (wallpaper != null && !wallpaper.isFile()) wallpaper = null;
        File preview = ThemePackStore.previewFile(context.getFilesDir(),
                pkg, stored.previewName);
        if (preview != null && !preview.isFile()) preview = null;
        return new Snapshot(pkg, stored.name, stored.dark, stored.accent,
                stored.scrim, colors, Collections.unmodifiableMap(strings),
                wallpaper, preview);
    }

    /**
     * Copies the image stream to a unique staged file, then validates it by
     * decoding bounds: must be a real PNG or JPEG within the dimension and
     * pixel caps. Returns the staged file or null (no partial state kept).
     */
    private static File stageValidatedImage(Context context, Uri uri,
                                            long maxBytes, int maxDim, long maxPixels,
                                            File filesDir, String pkg) {
        File staged = null;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            staged = ThemePackStore.stageFile(in,
                    ThemePackStore.packDir(filesDir, pkg), maxBytes);
        } catch (Exception e) {
            ThemePackStore.deleteQuietly(staged);
            return null;
        }
        if (staged == null) return null;
        if (!validateImage(staged, maxDim, maxPixels)) {
            ThemePackStore.deleteQuietly(staged);
            return null;
        }
        return staged;
    }

    /** PNG/JPEG decode-bounds validation with dimension and pixel caps. */
    private static boolean validateImage(File file, int maxDim, long maxPixels) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            if (!ThemePackContracts.isSupportedImageMime(bounds.outMimeType)) return false;
            if (bounds.outWidth > maxDim || bounds.outHeight > maxDim) return false;
            if ((long) bounds.outWidth * bounds.outHeight > maxPixels) return false;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048) {
                options.inSampleSize *= 2;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return false;
            bitmap.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    private static String providerAuthority(PackageManager pm, String pkg) {
        try {
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_META_DATA);
            if (info == null || info.applicationInfo == null
                    || info.applicationInfo.metaData == null) {
                return null;
            }
            return info.applicationInfo.metaData.getString(
                    ThemePackContracts.META_AUTHORITY);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean ownsProvider(PackageManager pm, String pkg, String authority) {
        if (authority == null || !authority.matches("[A-Za-z0-9._-]{1,255}")) return false;
        ProviderInfo provider = pm.resolveContentProvider(authority, 0);
        return provider != null && provider.exported && pkg.equals(provider.packageName);
    }

    private static boolean pinAllows(Context context, String pkg, String signer) {
        ThemePackStore.StoredSnapshot stored = ThemePackStore.readSnapshot(context.getFilesDir(), pkg);
        return stored == null || signer.equals(stored.signer);
    }

    private static SharedPreferences activePrefs(Context context) {
        return context.getSharedPreferences(PREFS_ACTIVE, Context.MODE_PRIVATE);
    }



    private static String stringColumn(Cursor c, String name) {
        try {
            int i = c.getColumnIndex(name);
            return i >= 0 ? c.getString(i) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** INTEGER columns read directly, string columns parsed leniently. */
    private static Object longOrString(Cursor c, String name) {
        try {
            int i = c.getColumnIndex(name);
            if (i < 0) return null;
            if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) return c.getLong(i);
            return c.getString(i);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long longColumn(Cursor c, String name) {
        Object raw = longOrString(c, name);
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).longValue();
        try {
            String s = String.valueOf(raw).trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
