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
    private static final String GENERATION_KEY = "selection_generation";

    private static volatile Snapshot sCache;
    private record Discovery(List<Entry> entries, long expiresAt) {}
    private static volatile Discovery discovered;

    /** Single-thread worker for background package-update refreshes. */
    private static final java.util.concurrent.ExecutorService REFRESH_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "theme-pack-refresh");
                thread.setDaemon(true);
                return thread;
            });

    /** Observed by ThemeRegistry (never called while holding a lock, and
     *  never before the store/prefs have settled). */
    public interface ActiveListener {
        /** New active state; null when the pack was deactivated. */
        void onActiveThemeChanged(Snapshot snapshot);
    }

    private static final List<ActiveListener> LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Adds a change listener. Listeners fire outside any lock on state
     *  transitions only (successful import, deactivate, removal of the
     *  active pack, signer-mismatch deactivation). */
    public static void addListener(ActiveListener listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static void removeListener(ActiveListener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    /** Fires listeners; callers must not hold {@code ThemePacks.class}. */
    private static void notifyChanged(Snapshot snapshot) {
        for (ActiveListener listener : LISTENERS) {
            try {
                listener.onActiveThemeChanged(snapshot);
            } catch (Exception ignored) {
            }
        }
    }

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
        public final String id;
        public final long revision;
        public final String name;
        public final Boolean dark;        // null = unspecified
        public final Integer accent;      // ARGB, null = none
        public final Integer scrim;       // ARGB, null = none
        public final Map<String, Integer> colors;   // unmodifiable, allowlisted tokens
        public final File wallpaper;      // private-store copy, null = none
        public final File preview;        // private-store copy, null = none
        /**
         * Canonical editor syntax colors ({@code comment}, {@code keyword},
         * {@code string}, {@code number}, {@code type}, {@code function},
         * {@code operator}, {@code variable} -> ARGB). Unmodifiable; empty
         * when the pack ships none. Exactly these keys are consumed by the
         * host editor.
         */
        public final Map<String, Integer> editorSyntax;
        /**
         * Wallpaper application scope: {@link ThemePackContracts#WALLPAPER_SCOPE_FILES}
         * (default, file-list panes only), {@code "all"} (every themed
         * activity background) or {@code "none"} (no wallpaper). Never null.
         */
        public final String wallpaperScope;

        private final Map<String, Map<String, String>> strings;

        Snapshot(String packageName, String name, Boolean dark, Integer accent,
                 Integer scrim, Map<String, Integer> colors,
                 Map<String, Map<String, String>> strings, File wallpaper,
                 File preview, Map<String, Integer> editorSyntax,
                 String wallpaperScope) {
            this(packageName, name, dark, accent, scrim, colors, strings, wallpaper,
                    preview, editorSyntax, wallpaperScope, null, 0);
        }

        Snapshot(String packageName, String name, Boolean dark, Integer accent,
                 Integer scrim, Map<String, Integer> colors,
                 Map<String, Map<String, String>> strings, File wallpaper,
                 File preview, Map<String, Integer> editorSyntax,
                 String wallpaperScope, String id, long revision) {
            this.packageName = packageName;
            this.id = id;
            this.revision = revision;
            this.name = name;
            this.dark = dark;
            this.accent = accent;
            this.scrim = scrim;
            this.colors = colors;
            this.strings = strings;
            this.wallpaper = wallpaper;
            this.preview = preview;
            this.editorSyntax = editorSyntax == null
                    ? Collections.<String, Integer>emptyMap() : editorSyntax;
            this.wallpaperScope = ThemePackContracts.isWallpaperScope(wallpaperScope)
                    ? wallpaperScope : ThemePackContracts.WALLPAPER_SCOPE_FILES;
        }

        /**
         * True when both snapshots would render identically: same pack, id,
         * colors, dark/accent/scrim, strings, editor syntax, wallpaper scope
         * and the same stored wallpaper/preview files. Used to skip epoch
         * bumps and change notifications for refreshes that changed nothing
         * visible. Store-level bookkeeping (revision, signer) is ignored.
         */
        public boolean sameState(Snapshot other) {
            if (other == null) return false;
            return packageName != null && packageName.equals(other.packageName)
                    && eq(id, other.id)
                    && eq(name, other.name)
                    && eq(dark, other.dark)
                    && eq(accent, other.accent)
                    && eq(scrim, other.scrim)
                    && eq(colors, other.colors)
                    && eq(editorSyntax, other.editorSyntax)
                    && eq(wallpaperScope, other.wallpaperScope)
                    && eq(path(wallpaper), path(other.wallpaper))
                    && eq(path(preview), path(other.preview))
                    && eq(strings, other.strings);
        }

        private static boolean eq(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }

        private static String path(File file) {
            return file == null ? null : file.getAbsolutePath();
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
     * the row contract version (v1 or v2), allowlists/limits, the exact
     * {@code content://<authority>/wallpaper} and {@code /preview} forms,
     * size/dimension/pixel caps with PNG/JPEG decode validation, and the
     * independent signer pin (first import pins the current certificate;
     * later imports of the same package fail when the certificate changed).
     * Persistence is staged and committed atomically: the previous
     * snapshot/wallpaper/preview survive a failed import untouched.
     *
     * <p>No-op fast path: when the package is already the active selection,
     * the selection generation is unchanged, and the parsed row (including
     * the pack id) plus the staged image bytes are identical to the stored
     * snapshot and its files, the import returns {@code true} without a
     * commit — no revision/generation bump and no listener notification, so
     * repeated package-update refreshes of an unchanged pack cannot churn
     * the UI. If only the row metadata changed while the images are
     * byte-identical, the commit retains the stored image files. A re-select
     * of a package that is not currently active always runs the full commit
     * (activation pointer, generation bump, notification).
     */
    public static boolean importAndSelect(Context context, Entry entry) {
        return importAndSelect(context, entry, -1, null);
    }

    /**
     * Reverse theme-request path: like {@link #importAndSelect(Context, Entry)}
     * but only proceeds when the provider's served row id equals
     * {@code expectedId} (both sides sanitized/bounded by
     * {@link ThemePackParser#parseId}). The id is validated straight after
     * the row query — before staging, validation work and the commit — so a
     * provider that swapped its payload for another theme id is rejected
     * without importing anything. An invalid {@code expectedId} never
     * reaches the provider.
     */
    public static boolean importAndSelect(Context context, Entry entry,
                                          String expectedId) {
        final String expected;
        try {
            expected = ThemePackParser.parseId(expectedId);
        } catch (ThemePackParser.ParseException e) {
            return false;
        }
        return importAndSelect(context, entry, -1, expected);
    }

    /**
     * Same as {@link #importAndSelect(Context, Entry)} but guarded by a
     * selection generation: when {@code expectedGeneration} is
     * {@code >= 0} and the persisted generation no longer matches (the user
     * deselected or switched themes while this import was in flight), the
     * commit is aborted so a stale refresh cannot re-activate a theme the
     * user moved away from.
     */
    static boolean importAndSelect(Context context, Entry entry,
                                   int expectedGeneration) {
        return importAndSelect(context, entry, expectedGeneration, null);
    }

    private static boolean importAndSelect(Context context, Entry entry,
                                           int expectedGeneration,
                                           String expectedId) {
        File stagedWallpaper = null;
        File stagedPreview = null;
        boolean committed = false;
        Snapshot activated = null;
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
            Map<String, Integer> editorSyntax;
            String wallpaperScope;
            String wallpaperColumn;
            String previewColumn;
            try (Cursor c = context.getContentResolver().query(
                    themeUri, null, null, null, null)) {
                if (c == null || !c.moveToFirst()) return false;
                if (c.getCount() != 1) return false;
                Long contract = longColumn(c, ThemePackContracts.COLUMN_CONTRACT);
                if (contract == null
                        || (contract != ThemePackContracts.CONTRACT_VERSION_V1
                            && contract != ThemePackContracts.CONTRACT_VERSION_V2)) {
                    return false;
                }
                id = ThemePackParser.parseId(stringColumn(c, ThemePackContracts.COLUMN_ID));
                // Reverse-request guard: reject a mismatched id immediately,
                // before any staging or commit work happens.
                if (expectedId != null && !expectedId.equals(id)) return false;
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
                // v2 columns: accepted when present regardless of the row's
                // declared version (an absent column parses to its default).
                editorSyntax = ThemePackParser.parseEditorSyntax(
                        stringColumn(c, ThemePackContracts.COLUMN_EDITOR_SYNTAX));
                wallpaperScope = ThemePackParser.parseWallpaperScope(
                        longOrString(c, ThemePackContracts.COLUMN_WALLPAPER_SCOPE));
                wallpaperColumn = stringColumn(c, ThemePackContracts.COLUMN_WALLPAPER);
                previewColumn = stringColumn(c, ThemePackContracts.COLUMN_PREVIEW);
            } catch (ThemePackParser.ParseException e) {
                return false;
            }
            // Accent-only, label-only, color-only or syntax-only rows are all
            // valid; only a fully empty row is rejected.
            if (accent == null && dark == null && scrim == null
                    && colors.isEmpty() && strings.isEmpty()
                    && editorSyntax.isEmpty()
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

            // scope "none" ignores the wallpaper column entirely.
            if (ThemePackContracts.WALLPAPER_SCOPE_NONE.equals(wallpaperScope)) {
                wallpaperColumn = null;
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
            stored.editorSyntax = editorSyntax.isEmpty() ? null : editorSyntax;
            stored.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_FILES
                    .equals(wallpaperScope) ? null : wallpaperScope;

            // No-op preflight, no monitor held: provider IO is finished and
            // both files are bounded (wallpaper <= 8 MiB, preview <= 256 KiB),
            // so these are short local reads. The staged bytes are compared
            // against the currently stored images — endpoint strings are
            // deliberately not consulted, a provider can change content
            // behind the same URI.
            File filesDir = context.getFilesDir();
            ThemePackStore.StoredSnapshot preflightPrevious =
                    ThemePackStore.readSnapshot(filesDir, pkg);
            String preflightWallpaperName = preflightPrevious == null
                    ? null : preflightPrevious.wallpaperName;
            String preflightPreviewName = preflightPrevious == null
                    ? null : preflightPrevious.previewName;
            boolean wallpaperBytesSame = ThemePackContentEquals.sameBytes(
                    stagedWallpaper,
                    ThemePackStore.wallpaperFile(filesDir, pkg,
                            preflightWallpaperName));
            boolean previewBytesSame = ThemePackContentEquals.sameBytes(
                    stagedPreview,
                    ThemePackStore.previewFile(filesDir, pkg,
                            preflightPreviewName));

            synchronized (ThemePacks.class) {
                // Monitor held only for the local commit; no provider IO inside.
                if (!signer.equals(PluginHost.certDigest(context, pkg))
                        || !pinAllows(context, pkg, signer)) return false;
                // Stale-refresh guard: abort before touching the store when
                // the user deselected/switched while we were querying.
                if (expectedGeneration >= 0
                        && selectionGeneration(context) != expectedGeneration) {
                    return false;
                }
                ThemePackStore.StoredSnapshot previous = ThemePackStore.readSnapshot(
                        filesDir, pkg);
                // No-op re-imports only apply to the CURRENT selection; a
                // re-select of the same package after deactivation must run
                // the commit path below so pointer/bump/notify still happen.
                boolean currentlyActive = pkg.equals(activePrefs(context)
                        .getString(ACTIVE_KEY, null));

                // The preflight byte verdict is reused only when the previous
                // snapshot is still the exact one the bytes were compared
                // against: image names are never rewritten in place, so name
                // equality plus existence re-establishes the comparison. A
                // null staged file is "same" only when the store ships none.
                File previousWallpaper = previous == null ? null
                        : ThemePackStore.wallpaperFile(filesDir, pkg,
                                previous.wallpaperName);
                File previousPreview = previous == null ? null
                        : ThemePackStore.previewFile(filesDir, pkg,
                                previous.previewName);
                boolean wallpaperSame = stagedWallpaper != null
                        ? wallpaperBytesSame
                            && previous != null
                            && previous.wallpaperName != null
                            && previous.wallpaperName.equals(preflightWallpaperName)
                            && previousWallpaper != null
                            && previousWallpaper.isFile()
                        : previous == null || previous.wallpaperName == null;
                boolean previewSame = stagedPreview != null
                        ? previewBytesSame
                            && previous != null
                            && previous.previewName != null
                            && previous.previewName.equals(preflightPreviewName)
                            && previousPreview != null
                            && previousPreview.isFile()
                        : previous == null || previous.previewName == null;

                // Identical row and identical image bytes on the live
                // selection: keep store, revision, generation and listeners
                // exactly as they are (no recreate churn on repeated
                // package-update refreshes).
                if (currentlyActive && previous != null
                        && ThemePackContentEquals.sameMetadata(stored, previous)
                        && wallpaperSame && previewSame) {
                    sCache = buildSnapshot(context, pkg, previous);
                    return true;
                }

                // Metadata changed (or activation needed) but an image is
                // byte-identical: keep the previous image file so the commit
                // rewrites only snapshot.json — no versioned-file/FD churn.
                File commitWallpaper = stagedWallpaper;
                File commitPreview = stagedPreview;
                if (wallpaperSame && stagedWallpaper != null) {
                    stored.wallpaperName = previous.wallpaperName;
                    commitWallpaper = null; // staged file removed by finally
                }
                if (previewSame && stagedPreview != null) {
                    stored.previewName = previous.previewName;
                    commitPreview = null; // staged file removed by finally
                }

                stored.signer = signer;
                stored.schema = ThemePackStore.SCHEMA_LATEST;
                stored.revision = (previous != null && previous.revision != null
                        ? previous.revision : 0L) + 1L;
                if (!ThemePackStore.commit(filesDir, pkg, stored,
                        commitWallpaper, commitPreview)) {
                    return false;
                }
                // Staged files that were actually consumed (renamed) are no
                // longer ours to clean; staged files of retained images stay
                // set so the finally block below deletes them.
                if (commitWallpaper != null) stagedWallpaper = null;
                if (commitPreview != null) stagedPreview = null;

                bumpGeneration(context);
                activePrefs(context).edit().putString(ACTIVE_KEY, pkg).apply();
                activated = buildSnapshot(context, pkg, stored);
                sCache = activated;
                committed = true;
            }
            // Notify outside the monitor; listeners may re-enter accessors.
            if (committed) notifyChanged(activated);
            return committed;
        } catch (Exception e) {
            return false;
        } finally {
            ThemePackStore.deleteQuietly(stagedWallpaper);
            ThemePackStore.deleteQuietly(stagedPreview);
        }
    }

    // ---------- access ----------

    /** Clears the lazily-built active snapshot cache without touching prefs
     *  or the store. Used by ThemeRegistry.reload() to force a re-read. */
    public static synchronized void invalidateCachedSnapshot() {
        sCache = null;
    }

    /** Monotonic counter bumped on every selection change (successful
     *  import/activation, deactivate, removal of the active pack). Refresh
     *  jobs capture it up front so a stale import cannot re-activate a
     *  theme the user deselected or switched away from in the meantime. */
    public static synchronized int selectionGeneration(Context context) {
        try {
            return activePrefs(context.getApplicationContext())
                    .getInt(GENERATION_KEY, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void bumpGeneration(Context context) {
        try {
            SharedPreferences prefs = activePrefs(context);
            prefs.edit().putInt(GENERATION_KEY,
                    prefs.getInt(GENERATION_KEY, 0) + 1).apply();
        } catch (Exception ignored) {
        }
    }

    /** Currently active pack, or null. Verifies install + signer pin lazily. */
    public static Snapshot active(Context context) {
        Snapshot cached = sCache;
        if (cached != null) return cached;
        boolean deactivated = false;
        Snapshot snapshot = null;
        synchronized (ThemePacks.class) {
            try {
                if (context == null) return null;
                context = context.getApplicationContext();
                String pkg = activePrefs(context).getString(ACTIVE_KEY, null);
                if (pkg == null || pkg.isEmpty()) return null;
                ThemePackStore.StoredSnapshot stored = ThemePackStore.readSnapshot(
                        context.getFilesDir(), pkg);
                if (stored == null || stored.signer == null
                        || !stored.signer.equals(PluginHost.certDigest(context, pkg))) {
                    // Signer mismatch or corrupted store: deactivate.
                    activePrefs(context).edit().remove(ACTIVE_KEY).apply();
                    bumpGeneration(context);
                    ThemePackStore.deletePack(context.getFilesDir(), pkg);
                    sCache = null;
                    deactivated = true;
                } else {
                    snapshot = buildSnapshot(context, pkg, stored);
                    sCache = snapshot;
                }
            } catch (Exception e) {
                return null;
            }
        }
        // Signer mismatch deactivates; tell listeners outside the lock.
        if (deactivated) notifyChanged(null);
        return snapshot;
    }

    /** Clears the active pack. The ingested copy is kept until removal. */
    public static void deactivate(Context context) {
        boolean hadActive = false;
        try {
            context = context.getApplicationContext();
            synchronized (ThemePacks.class) {
                hadActive = activePrefs(context).getString(ACTIVE_KEY, null) != null;
                bumpGeneration(context);
                activePrefs(context).edit().remove(ACTIVE_KEY).apply();
                sCache = null;
            }
        } catch (Exception ignored) {
        }
        if (hadActive) notifyChanged(null);
    }

    // ---------- receiver hooks ----------

    /** Package uninstalled: drop its ingested copy and pin; clear if active. */
    public static void onPackageRemoved(Context context, String packageName) {
        discovered = null;
        boolean wasActive = false;
        try {
            if (context == null || packageName == null) return;
            context = context.getApplicationContext();
            synchronized (ThemePacks.class) {
                String active = activePrefs(context).getString(ACTIVE_KEY, null);
                wasActive = packageName.equals(active);
                if (wasActive) {
                    bumpGeneration(context);
                    activePrefs(context).edit().remove(ACTIVE_KEY).apply();
                }
                ThemePackStore.deletePack(context.getFilesDir(), packageName);
                sCache = null;
            }
        } catch (Exception ignored) {
        }
        if (wasActive) notifyChanged(null);
    }

    /**
     * Package installed/updated. When the package is the active selection,
     * schedules a background refresh: re-checks the provider authority and
     * signer, re-imports the row, and either activates the new snapshot,
     * keeps the previous valid snapshot (provider hiccup/rejected row) or
     * deactivates on a signer mismatch — never re-activating a theme the
     * user deselected/switched while the refresh ran (selection-generation
     * guard). Non-active packs only drop cached discovery data.
     */
    public static void onPackageReplaced(Context context, String packageName) {
        discovered = null;
        if (packageName == null || context == null) return;
        try {
            context = context.getApplicationContext();
            boolean isActive;
            synchronized (ThemePacks.class) {
                String active = activePrefs(context).getString(ACTIVE_KEY, null);
                isActive = packageName.equals(active);
                if (isActive) sCache = null; // readers re-verify meanwhile
            }
            if (isActive) {
                final Context app = context;
                final String pkg = packageName;
                REFRESH_EXECUTOR.execute(() -> {
                    try {
                        refreshActivePack(app, pkg);
                    } catch (Exception ignored) {
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Background re-import of the actively selected provider after a
     * package update. Never runs provider IO under the monitor; the commit
     * is generation-guarded, a signer mismatch deactivates, and any other
     * failure keeps the previously ingested snapshot valid and active.
     */
    private static void refreshActivePack(Context app, String pkg) {
        try {
            int generation = selectionGeneration(app);
            // Provider + signature rechecks happen inside importAndSelect;
            // the stale-generation guard aborts the commit when needed.
            boolean imported = importAndSelect(app, new Entry(pkg, pkg, null),
                    generation);
            if (imported) return;
            String active = activePrefs(app).getString(ACTIVE_KEY, null);
            boolean stillSelected = pkg.equals(active);
            boolean generationUnchanged = generation == selectionGeneration(app);
            ThemePackStore.StoredSnapshot stored = ThemePackStore.readSnapshot(
                    app.getFilesDir(), pkg);
            String pinned = stored == null ? null : stored.signer;
            String digest = PluginHost.certDigest(app, pkg);
            boolean signerMismatch = pinned != null && digest != null
                    && !digest.equals(pinned);
            ThemePackUpdatePolicy.Decision decision = ThemePackUpdatePolicy
                    .onRefreshAttempt(stillSelected, generationUnchanged,
                            imported, signerMismatch);
            if (decision == ThemePackUpdatePolicy.Decision.DEACTIVATE_SIGNER) {
                deactivateWithSignerRecheck(app, pkg);
            }
            // KEEP_PREVIOUS / ABORT_STALE: previous snapshot untouched.
        } catch (Exception ignored) {
        }
    }

    /** Deactivates the pack only when it is still selected AND its stored
     *  snapshot's pinned signer no longer matches the installed package. */
    private static void deactivateWithSignerRecheck(Context app, String pkg) {
        boolean deactivated = false;
        synchronized (ThemePacks.class) {
            try {
                String active = activePrefs(app).getString(ACTIVE_KEY, null);
                if (!pkg.equals(active)) return;
                ThemePackStore.StoredSnapshot stored = ThemePackStore.readSnapshot(
                        app.getFilesDir(), pkg);
                String digest = PluginHost.certDigest(app, pkg);
                if (stored == null || stored.signer == null || digest == null
                        || !digest.equals(stored.signer)) {
                    bumpGeneration(app);
                    activePrefs(app).edit().remove(ACTIVE_KEY).apply();
                    ThemePackStore.deletePack(app.getFilesDir(), pkg);
                    sCache = null;
                    deactivated = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (deactivated) notifyChanged(null);
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
        // Re-allowlist stored syntax keys: only canonical categories may
        // reach Snapshot consumers, whatever an old/tampered file contains.
        Map<String, Integer> editorSyntax = new LinkedHashMap<>();
        if (stored.editorSyntax != null) {
            for (Map.Entry<String, Integer> e : stored.editorSyntax.entrySet()) {
                if (e.getValue() == null) continue;
                String canonical = ThemePackParser.canonicalSyntaxKey(e.getKey());
                if (canonical != null) editorSyntax.put(canonical, e.getValue());
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
                wallpaper, preview,
                Collections.unmodifiableMap(editorSyntax),
                stored.wallpaperScope, stored.id,
                stored.revision == null ? 0 : stored.revision);
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
