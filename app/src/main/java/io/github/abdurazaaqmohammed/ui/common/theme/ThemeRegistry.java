package io.github.abdurazaaqmohammed.ui.common.theme;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;

import androidx.core.os.ConfigurationCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import io.github.abdurazaaqmohammed.plugins.ipc.PluginTheme;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePackContracts;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColors;
import com.google.gson.Gson;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry for ThemePlugins. BaseActivity calls applySaved()
 * so switching themes only touches this registry + prefs.
 *
 * <p>Live application: {@link #currentEpoch()} is a shared revision bumped
 * on every successful relevant theme change (built-in selection, pack
 * import/deactivate/update refresh, accent change via {@link #invalidate()}).
 * Activities compare the epoch around resume (AppFont pattern) and can also
 * register a listener that fires immediately while foregrounded, so a
 * theme change never needs a process kill. {@link #applyPluginThemeExtras}
 * publishes the active palette to plugin activities as bounded primitives.
 */
public final class ThemeRegistry {

    public static final String PREF_KEY = "theme_plugin_id";

    private static final Map<String, ThemePlugin> PLUGINS = new LinkedHashMap<>();
    private static boolean builtInsLoaded;

    /** Shared theme revision; compare across resume to detect changes. */
    private static volatile int sEpoch;

    /** Single parent-attached bridge (PluginEvents); null when none. */
    private static volatile Runnable sParentListener;

    /** Per-activity listeners, weakly held; activities unregister at destroy. */
    private static final List<WeakReference<Runnable>> ACTIVITY_LISTENERS =
            new ArrayList<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Effective theme state this registry last saw: the active pack snapshot
     * PLUS the user theme/accent prefs. Bumps and notifications fire only
     * when this composite changes, so accent-pref changes and built-in
     * choices (where the pack snapshot stays the same or null) still count,
     * while no-op reloads stay silent.
     */
    private static final class EffectiveState {
        final ThemePacks.Snapshot pack;
        final String themeId;
        final String accentMode;
        final Integer accentCustom;
        final int systemNight;

        EffectiveState(ThemePacks.Snapshot pack, String themeId,
                       String accentMode, Integer accentCustom, int systemNight) {
            this.pack = pack;
            this.themeId = themeId;
            this.accentMode = accentMode;
            this.accentCustom = accentCustom;
            this.systemNight = systemNight;
        }

        boolean sameAs(EffectiveState other) {
            if (other == null) return false;
            boolean packSame = pack == null ? other.pack == null
                    : pack.sameState(other.pack);
            return packSame
                    && eq(themeId, other.themeId)
                    && eq(accentMode, other.accentMode)
                    && eq(accentCustom, other.accentCustom)
                    && systemNight == other.systemNight;
        }

        private static boolean eq(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    private static volatile EffectiveState sLastSeen;
    /** Application context captured on first use so the ThemePacks listener
     *  can rebuild the composite without a Context parameter. */
    private static volatile Context sAppContext;

    static {
        // Bridge ThemePacks transitions into epoch bumps + UI notifications.
        // ThemePacks notifies outside its locks on real transitions only.
        try {
            ThemePacks.addListener(snapshot -> onPackState());
        } catch (Throwable ignored) {
        }
    }

    private ThemeRegistry() {
    }

    // ---------- live revision ----------

    /** Monotonic revision of the applied theme state. Activities capture it
     *  after applying and re-check on resume / via listeners. */
    public static int currentEpoch() {
        return sEpoch;
    }

    private static synchronized void bumpEpoch() {
        sEpoch++;
    }

    private static EffectiveState captureState(Context context) {
        try {
            ThemePacks.Snapshot pack = ThemePacks.active(context);
            String themeId = getCurrentId(context);
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(context);
            String accentMode = prefs.getString(ACCENT_PREF_KEY, null);
            Integer accentCustom = prefs.contains(ACCENT_CUSTOM_PREF_KEY)
                    ? prefs.getInt(ACCENT_CUSTOM_PREF_KEY, 0) : null;
            int systemNight = context.getApplicationContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return new EffectiveState(pack, themeId, accentMode, accentCustom, systemNight);
        } catch (Exception e) {
            return null;
        }
    }

    /** Single dedupe point: bump + notify only when the composite changed. */
    private static void applyState(EffectiveState now) {
        synchronized (ThemeRegistry.class) {
            if (now == null ? sLastSeen == null : now.sameAs(sLastSeen)) return;
            sLastSeen = now;
            bumpEpoch();
        }
        notifyThemeChanged();
    }

    private static void onPackState() {
        Context context = sAppContext;
        if (context == null) {
            // Registry not used yet: nothing to compare; advance + notify.
            bumpEpoch();
            notifyThemeChanged();
            return;
        }
        applyState(captureState(context));
    }

    /**
     * Re-reads the effective theme state (active pack + theme id + accent
     * prefs) and bumps the epoch / notifies listeners ONLY when something
     * actually changed. This is the parent's one-stop hook after settings
     * changes: it covers accent-pref changes and built-in theme choices
     * even when no pack snapshot is active, and no-ops when a reload finds
     * the same state (no duplicate loops with setCurrentId/import
     * notifications).
     */
    public static void reload(Context context) {
        try {
            ThemePacks.invalidateCachedSnapshot();
            if (context == null) {
                bumpEpoch();
                notifyThemeChanged();
                return;
            }
            if (sAppContext == null) sAppContext = context.getApplicationContext();
            applyState(captureState(sAppContext));
        } catch (Exception ignored) {
        }
    }

    /**
     * Forces an epoch bump + listener notification for a caller-driven theme
     * change. Callers must only invoke it after the change actually
     * happened. The remembered composite is refreshed so a subsequent
     * {@link #reload(Context)} stays deduplicated.
     */
    public static void invalidate() {
        bumpEpoch();
        notifyThemeChanged();
        Context context = sAppContext;
        if (context != null) sLastSeen = captureState(context);
    }

    /** Single bridge the parent attaches (e.g. to PluginEvents). Fires on
     *  successful relevant changes, outside any lock, on the main thread. */
    public static void setOnThemeChangedListener(Runnable listener) {
        sParentListener = listener;
    }

    public static void clearOnThemeChangedListener() {
        sParentListener = null;
    }

    /**
     * Registers a per-activity listener (weakly held). Returns the runnable
     * to pass to {@link #removeActivityListener(Runnable)} at destroy time.
     * Only fires while it matters: callers are expected to refresh
     * immediately when foregrounded and to compare {@link #currentEpoch()}
     * around resume for changes that happened while backgrounded.
     */
    public static Runnable addActivityListener(Runnable listener) {
        if (listener != null) {
            synchronized (ACTIVITY_LISTENERS) {
                purgeActivityListeners();
                ACTIVITY_LISTENERS.add(new WeakReference<>(listener));
            }
        }
        return listener;
    }

    public static void removeActivityListener(Runnable listener) {
        if (listener == null) return;
        synchronized (ACTIVITY_LISTENERS) {
            for (int i = ACTIVITY_LISTENERS.size() - 1; i >= 0; i--) {
                Runnable r = ACTIVITY_LISTENERS.get(i).get();
                if (r == null || r == listener) ACTIVITY_LISTENERS.remove(i);
            }
        }
    }

    private static void purgeActivityListeners() {
        ACTIVITY_LISTENERS.removeIf(ref -> ref.get() == null);
    }

    /** Notifies the parent bridge then the per-activity listeners, always
     *  on the main thread and never under any lock. */
    private static void notifyThemeChanged() {
        Runnable post = () -> {
            Runnable parent = sParentListener;
            if (parent != null) {
                try {
                    parent.run();
                } catch (Exception ignored) {
                }
            }
            List<Runnable> targets = new ArrayList<>();
            synchronized (ACTIVITY_LISTENERS) {
                purgeActivityListeners();
                for (WeakReference<Runnable> ref : ACTIVITY_LISTENERS) {
                    Runnable r = ref.get();
                    if (r != null) targets.add(r);
                }
            }
            for (Runnable target : targets) {
                try {
                    target.run();
                } catch (Exception ignored) {
                }
            }
        };
        MAIN_HANDLER.post(post);
    }

    // ---------- registration ----------

    public static synchronized void register(ThemePlugin plugin) {
        if (plugin == null || plugin.id() == null) return;
        PLUGINS.put(plugin.id(), plugin);
    }

    public static synchronized void unregister(String id) {
        PLUGINS.remove(id);
    }

    public static synchronized List<ThemePlugin> getAll() {
        ensureBuiltIns();
        return new ArrayList<>(PLUGINS.values());
    }

    public static synchronized ThemePlugin get(String id) {
        ensureBuiltIns();
        return PLUGINS.get(id);
    }

    public static String getCurrentId(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String id = prefs.getString(PREF_KEY, null);
            if (id != null) return id;
            // One-way bridge from the legacy int "theme" pref (style res id)
            // still written by MainActivity, so migrated activities stay
            // consistent until MainActivity moves to ThemeRegistry.
            if (prefs.contains("theme")) {
                try {
                    int legacy = prefs.getInt("theme", 0);
                    if (legacy == io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light)
                        return BuiltInThemes.LIGHT_ID;
                    if (legacy == io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark)
                        return BuiltInThemes.DARK_ID;
                    if (legacy == io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Black)
                        return BuiltInThemes.BLACK_ID;
                } catch (Exception ignored) {
                }
            }
            return BuiltInThemes.SYSTEM_DEFAULT_ID;
        } catch (Exception e) {
            return BuiltInThemes.SYSTEM_DEFAULT_ID;
        }
    }

    public static void setCurrentId(Context context, String id) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(PREF_KEY, id).apply();
        } catch (Exception ignored) {
        }
        // Deliberately NOT calling AppCompatDelegate.setDefaultNightMode:
        // that flips a process-global mode and auto-recreates every
        // AppCompat activity (including editors with unsaved state that
        // override onThemeChanged for in-place refresh). Night mode is
        // applied per-activity in applySaved() instead; recreation for an
        // id change flows through the single deduplicated notification so
        // editors get their overridable hook and default screens recreate
        // exactly once.
        try {
            if (sAppContext == null && context != null) {
                sAppContext = context.getApplicationContext();
            }
            applyState(captureState(sAppContext));
        } catch (Exception e) {
            bumpEpoch();
            notifyThemeChanged();
        }
    }

    public static int modeFor(Context context, String id) {
        ThemePlugin plugin = get(id);
        if (plugin != null) return plugin.nightMode();
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /**
     * Style res of the currently applied plugin. Lets legacy int-theme
     * readers (status-bar tinting, icon colors) stay correct after migration.
     */
    public static int currentStyleRes(Context context) {
        ThemePlugin plugin = get(appliedBaseId(context));
        if (plugin != null) {
            try {
                return plugin.styleRes();
            } catch (Exception ignored) {
            }
        }
        return com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar;
    }

    /** Pref key: accent mode. Null/absent = follow material color.
     *  "preset:<name>" = one of ACCENT_PRESETS; "custom" = ARGB in ACCENT_CUSTOM_PREF_KEY. */
    public static final String ACCENT_PREF_KEY = "accent_color";
    public static final String ACCENT_CUSTOM_PREF_KEY = "accent_custom_color";

    /** Named presets shown as swatches (name -> hex, for the swatch UI). */
    public static final String[][] ACCENT_PRESETS = {
            {"Purple", "#FF6750A4"},
            {"Blue", "#FF1565C0"},
            {"Teal", "#FF00897B"},
            {"Green", "#FF2E7D32"},
            {"Amber", "#FFB26A00"},
            {"Orange", "#FFE65100"},
            {"Red", "#FFC62828"},
            {"Pink", "#FFAD1457"},
    };

    /** ARGB for a preset name, or 0 if unknown. */
    private static int presetArgb(String name) {
        for (String[] preset : ACCENT_PRESETS) {
            if (preset[0].equalsIgnoreCase(name)) {
                return (int) Long.parseLong(preset[1].substring(2), 16);
            }
        }
        return 0;
    }

    /** Generated grid: 36 hues (H0..H35, 10 degrees each) x 6 S/L shades (V0..V5). */
    private static final float[][] SHADES = {
            {0.70f, 0.42f}, {0.62f, 0.48f}, {0.50f, 0.55f},
            {0.38f, 0.62f}, {0.78f, 0.34f}, {0.28f, 0.70f},
    };

    /** Finds the generated overlay closest to the given color: exact hue
     *  bucket (10-degree steps) and nearest saturation/lightness shade. */
    private static int hueOverlayIndex(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float hue;
        float sat;
        float light = (max + min) / 2f;
        if (max == min) {
            hue = 0f;
            sat = 0f;
        } else {
            float d = max - min;
            sat = light > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) hue = ((g - b) / d + (g < b ? 6 : 0)) * 60f;
            else if (max == g) hue = ((b - r) / d + 2) * 60f;
            else hue = ((r - g) / d + 4) * 60f;
        }
        int hueIdx = Math.round(hue / 10f) % 36;
        int bestV = 0;
        float bestDist = Float.MAX_VALUE;
        for (int vi = 0; vi < SHADES.length; vi++) {
            float ds = (SHADES[vi][0] - sat) * 1.5f;
            float dl = SHADES[vi][1] - light;
            float dist = ds * ds + dl * dl;
            if (dist < bestDist) {
                bestDist = dist;
                bestV = vi;
            }
        }
        return hueIdx * SHADES.length + bestV;
    }

    /** Pref key: most-recently-used custom accent colors (JSON int array). */
    public static final String ACCENT_HISTORY_PREF_KEY = "accent_history";
    private static final int ACCENT_HISTORY_MAX = 5;

    /** Moves the color to the front of the recent-accents history (max 5). */
    public static void pushAccentHistory(Context context, int argb) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            List<Integer> history = new ArrayList<>();
            try {
                String json = prefs.getString(ACCENT_HISTORY_PREF_KEY, "[]");
                for (int value : new Gson().fromJson(json, int[].class)) {
                    history.add(value);
                }
            } catch (Exception ignored) {
            }
            history.remove(Integer.valueOf(argb));
            history.add(0, argb);
            while (history.size() > ACCENT_HISTORY_MAX) history.remove(history.size() - 1);
            int[] arr = new int[history.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = history.get(i);
            prefs.edit().putString(ACCENT_HISTORY_PREF_KEY, new Gson().toJson(arr)).apply();
        } catch (Exception ignored) {
        }
    }

    /** @return the most recently used custom accent colors (front = newest). */
    public static List<Integer> getAccentHistory(Context context) {
        List<Integer> out = new ArrayList<>();
        try {
            String json = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(ACCENT_HISTORY_PREF_KEY, "[]");
            for (int value : new Gson().fromJson(json, int[].class)) {
                out.add(value);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Stacks the user's accent onto the base theme. Call after applySaved()
     *  and before super.onCreate() so inflation picks it up.
     *
     *  Two paths (same visual goal, different mechanics):
     *  - Devices with Material dynamic color support (API 31+, incl. Xiaomi):
     *    feed the user's color as a content-based seed to DynamicColors, which
     *    generates the full Material palette from it.
     *  - Every other device: stack the closest generated accent overlay onto
     *    the base theme with Theme.applyStyle(). The overlay only carries
     *    accent attributes and its parent is an empty overlay, so the base
     *    Dark/Black/Light mode is untouched.
     *
     *  The difference between paths doesn't affect behavior; on Black, dynamic
     *  colors are skipped so surfaces stay pure black. */
    public static void applyAccent(Activity activity) {
        ThemePacks.Snapshot pack = ThemePacks.active(activity);
        if (pack != null) {
            try {
                if (ThemePackPalette.apply(activity, pack.colors, pack.accent)) return;
            } catch (RuntimeException ignored) {
                // Keep the base theme if a device cannot install the resource loader.
            }
            Integer primary = pack.colors.get("colorPrimary");
            if (primary == null) primary = pack.accent;
            if (primary != null) applyClosestAccent(activity, primary);
            return;
        }
        ThemePackPalette.clear(activity);
        try {
            String mode = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString(ACCENT_PREF_KEY, null);
            if (mode == null || mode.isEmpty()) return; // follow material color
            int argb;
            if (mode.startsWith("preset:")) {
                argb = presetArgb(mode.substring("preset:".length()));
            } else if ("custom".equals(mode)) {
                argb = PreferenceManager.getDefaultSharedPreferences(activity)
                        .getInt(ACCENT_CUSTOM_PREF_KEY, 0);
            } else if (!"follow".equals(mode)) {
                // Bare preset name (older builds stored just the name).
                argb = presetArgb(mode);
            } else {
                return;
            }
            if (argb == 0) return;

            boolean blackTheme = BuiltInThemes.BLACK_ID.equals(getCurrentId(activity));
            if (!blackTheme && DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(
                        activity,
                        new DynamicColorsOptions.Builder()
                                .setContentBasedSource(argb)
                                .build());
                return;
            }

            applyClosestAccent(activity, argb);
        } catch (Exception ignored) {
        }
    }

    private static void applyClosestAccent(Activity activity, int argb) {
        int overlay = AccentOverlays.RES[hueOverlayIndex(argb)];
        Resources.Theme theme = activity.getTheme();
        if (overlay != 0 && theme != null) theme.applyStyle(overlay, true);
    }

    public static String appliedBaseId(Context context) {
        ThemePacks.Snapshot pack = ThemePacks.active(context);
        if (pack != null && pack.dark != null) {
            return pack.dark ? BuiltInThemes.DARK_ID : BuiltInThemes.LIGHT_ID;
        }
        return getCurrentId(context);
    }

    public static int packColor(Context context, String token, int fallback) {
        ThemePacks.Snapshot pack = ThemePacks.active(context);
        Integer color = pack == null ? null : pack.colors.get(token);
        return color == null ? fallback : color;
    }

    public static CharSequence packLabel(Context context, String token, CharSequence fallback) {
        ThemePacks.Snapshot pack = ThemePacks.active(context);
        if (pack == null) return fallback;
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        String label = pack.label(token, locale == null ? Locale.getDefault() : locale);
        return label == null ? fallback : label;
    }

    /**
     * Active pack's canonical editor syntax colors ({@code comment},
     * {@code keyword}, {@code string}, {@code number}, {@code type},
     * {@code function}, {@code operator}, {@code variable} -> ARGB).
     * Unmodifiable; empty when no pack is active or it ships none. This is
     * exactly the map the editor consumes.
     */
    public static Map<String, Integer> editorSyntax(Context context) {
        ThemePacks.Snapshot pack = ThemePacks.active(context);
        return pack == null ? Collections.<String, Integer>emptyMap() : pack.editorSyntax;
    }

    // ---------- wallpaper application ----------

    /** Decodes the stored wallpaper within the pane-safe sample cap. */
    private static Bitmap decodeWallpaper(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeFile(path, options);
    }

    /** Wallpaper + scrim stack shared by the pane and activity paths. */
    private static LayerDrawable wallpaperDrawable(View view, ThemePacks.Snapshot pack) {
        Bitmap bitmap = decodeWallpaper(pack.wallpaper.getAbsolutePath());
        if (bitmap == null) return null;
        BitmapDrawable wallpaper = new BitmapDrawable(view.getResources(), bitmap);
        wallpaper.setGravity(Gravity.FILL);
        wallpaper.setFilterBitmap(true);
        Integer scrim = pack.colors.get("wallpaper_scrim");
        if (scrim == null) scrim = pack.scrim;
        return new LayerDrawable(new android.graphics.drawable.Drawable[]{
                wallpaper, new ColorDrawable(scrim == null ? 0 : scrim)
        });
    }

    private static final int WALLPAPER_TAG = io.github.abdurazaaqmohammed.MPManager.R.id.theme_pack_wallpaper_state;

    private record WallpaperState(android.graphics.drawable.Drawable original) {}

    private static boolean setWallpaper(View view, ThemePacks.Snapshot pack, boolean enabled) {
        Object tag = view.getTag(WALLPAPER_TAG);
        if (!enabled) {
            if (tag instanceof WallpaperState) {
                view.setBackground(((WallpaperState) tag).original());
                view.setTag(WALLPAPER_TAG, null);
            }
            return false;
        }
        LayerDrawable drawable = wallpaperDrawable(view, pack);
        if (drawable == null) return false;
        if (!(tag instanceof WallpaperState)) {
            view.setTag(WALLPAPER_TAG, new WallpaperState(view.getBackground()));
        }
        view.setBackground(drawable);
        return true;
    }

    /** Applies a snapshot wallpaper to file panes and restores the base when removed. */
    public static boolean applyPaneWallpaper(View paneStack) {
        if (paneStack == null) return false;
        ThemePacks.Snapshot pack = ThemePacks.active(paneStack.getContext());
        return setWallpaper(paneStack, pack, pack != null && pack.wallpaper != null
                && !ThemePackContracts.WALLPAPER_SCOPE_NONE.equals(pack.wallpaperScope));
    }

    /**
     * Applies the pack wallpaper to the whole activity content ("all"
     * scope). No-op (returns false) when no pack is active, the pack ships
     * no wallpaper, or the pack's scope is "files"/"none" — the caller keeps
     * its plain background in those cases.
     */
    public static boolean applyActivityBackground(Activity activity) {
        try {
            ThemePacks.Snapshot pack = ThemePacks.active(activity);
            View content = activity.findViewById(android.R.id.content);
            if (content == null) return false;
            return setWallpaper(content, pack, pack != null && pack.wallpaper != null
                    && ThemePackContracts.WALLPAPER_SCOPE_ALL.equals(pack.wallpaperScope));
        } catch (Exception e) {
            return false;
        }
    }

    /** Hard traversal bound for the chrome helper (view-count cap). */
    private static final int MAX_CHROME_VIEWS = 4096;

    /**
     * Bounded in-place chrome refresh for screens that override
     * {@code onThemeChanged()} to avoid recreate (editors with unsaved
     * state). Re-applies system bars, the scoped activity background, and
     * restyles only host-known chrome classes found in the content
     * hierarchy — appcompat/widget Toolbars and Material TabLayouts:
     * backgrounds, toolbar title/subtitle colors and nav/overflow/menu icon
     * tints from onSurface, tab colors. Traversal is capped at
     * {@value #MAX_CHROME_VIEWS} views. No inflation, no reflection, no
     * third-party or arbitrary views touched; anything else re-resolves its
     * themed attributes on the next inflate. Returns true when at least one
     * chrome view was restyled. Pair with {@link #applyInPlace(Activity)}.
     */
    public static boolean applyEditorChrome(Activity activity) {
        boolean any = false;
        try {
            applySystemBars(activity);
        } catch (Exception ignored) {
        }
        try {
            applyActivityBackground(activity);
        } catch (Exception ignored) {
        }
        try {
            View content = activity.findViewById(android.R.id.content);
            if (content instanceof android.view.ViewGroup) {
                int[] visited = {0};
                any = applyChromeToChildren((android.view.ViewGroup) content,
                        activity, visited);
            }
        } catch (Exception ignored) {
        }
        return any;
    }

    private static boolean applyChromeToChildren(android.view.ViewGroup parent,
                                                 Activity activity, int[] visited) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (visited[0] >= MAX_CHROME_VIEWS) return any;
            visited[0]++;
            View child = parent.getChildAt(i);
            if (child instanceof android.view.ViewGroup) {
                any |= applyChromeToChildren((android.view.ViewGroup) child,
                        activity, visited);
            }
            if (child instanceof androidx.appcompat.widget.Toolbar) {
                try {
                    androidx.appcompat.widget.Toolbar toolbar =
                            (androidx.appcompat.widget.Toolbar) child;
                    toolbar.setBackground(new ColorDrawable(attrColor(activity,
                            com.google.android.material.R.attr.colorSurface)));
                    int onSurface = attrColor(activity,
                            com.google.android.material.R.attr.colorOnSurface);
                    int onSurfaceVariant = attrColor(activity,
                            com.google.android.material.R.attr.colorOnSurfaceVariant);
                    toolbar.setTitleTextColor(onSurface);
                    toolbar.setSubtitleTextColor(onSurfaceVariant);
                    tintIcon(toolbar.getNavigationIcon(), onSurface);
                    tintIcon(toolbar.getOverflowIcon(), onSurface);
                    android.view.Menu menu = toolbar.getMenu();
                    if (menu != null) {
                        for (int m = 0; m < menu.size() && m < MAX_CHROME_VIEWS; m++) {
                            tintIcon(menu.getItem(m).getIcon(), onSurface);
                        }
                    }
                    any = true;
                } catch (Throwable ignored) {
                }
            } else if (child instanceof android.widget.Toolbar) {
                try {
                    android.widget.Toolbar toolbar = (android.widget.Toolbar) child;
                    toolbar.setBackground(new ColorDrawable(attrColor(activity,
                            com.google.android.material.R.attr.colorSurface)));
                    toolbar.setTitleTextColor(attrColor(activity,
                            com.google.android.material.R.attr.colorOnSurface));
                    toolbar.setSubtitleTextColor(attrColor(activity,
                            com.google.android.material.R.attr.colorOnSurfaceVariant));
                    any = true;
                } catch (Throwable ignored) {
                }
            } else if (child instanceof com.google.android.material.tabs.TabLayout) {
                try {
                    com.google.android.material.tabs.TabLayout tabs =
                            (com.google.android.material.tabs.TabLayout) child;
                    tabs.setBackground(new ColorDrawable(attrColor(activity,
                            com.google.android.material.R.attr.colorSurface)));
                    int normal = attrColor(activity,
                            com.google.android.material.R.attr.colorOnSurfaceVariant);
                    int selected = attrColor(activity,
                            com.google.android.material.R.attr.colorPrimary);
                    tabs.setTabTextColors(normal, selected);
                    tabs.setSelectedTabIndicatorColor(selected);
                    any = true;
                } catch (Throwable ignored) {
                }
            }
        }
        return any;
    }

    /** Tints a chrome icon drawable in place (toolbar-owned icons). */
    private static void tintIcon(android.graphics.drawable.Drawable icon, int color) {
        if (icon == null) return;
        try {
            icon.mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) {
        }
    }

    // ---------- system bars ----------

    /**
     * Tints the status/navigation bars from the active theme (pack tokens
     * {@code status_bar}/{@code nav_bar}, else the resolved Material surface)
     * and flips the light-icon appearance where the platform supports it.
     * Pre-M light bars fall back to dark bar colors for icon contrast; the
     * nav bar falls back to onSurface between API 23 and 26 where dark nav
     * icons are not renderable. Pure runtime calls: no overlay APK/XML/dex.
     */
    public static void applySystemBars(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            String pluginId = appliedBaseId(activity);
            boolean lightBars = isLightBars(activity, pluginId);
            int surfaceColor = BuiltInThemes.BLACK_ID.equals(pluginId) ? Color.BLACK
                    : MaterialColors.getColor(activity,
                        com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT);
            android.view.Window window = activity.getWindow();
            window.setStatusBarColor(packColor(activity, "status_bar", surfaceColor));
            // Light navigation icons are only renderable on API 26+ (flag) and
            // reliably on 27+; below that a surface-colored bar shows white
            // icons on white, so use a dark bar to keep the buttons visible.
            boolean lightNavSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;
            if (lightBars && !lightNavSupported) {
                window.setNavigationBarColor(MaterialColors.getColor(activity,
                        com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            } else {
                window.setNavigationBarColor(packColor(activity, "nav_bar", surfaceColor));
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M && lightBars) {
                // Old devices can't render dark status bar icons; use a dark
                // bar so icons stay visible.
                int darkBar = MaterialColors.getColor(activity,
                        android.R.attr.colorPrimary, surfaceColor);
                window.setStatusBarColor(darkBar);
                window.setNavigationBarColor(darkBar);
            }
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(window, window.getDecorView());
            controller.setAppearanceLightStatusBars(lightBars);
            controller.setAppearanceLightNavigationBars(lightBars);
        } catch (Exception ignored) {
        }
    }

    /** Light system-bar icons only when the resolved base theme is light. */
    private static boolean isLightBars(Context context, String pluginId) {
        if (pluginId == null || BuiltInThemes.SYSTEM_DEFAULT_ID.equals(pluginId)) {
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK)
                    != Configuration.UI_MODE_NIGHT_YES;
        }
        return BuiltInThemes.LIGHT_ID.equals(pluginId);
    }

    // ---------- plugin theme extras ----------

    /** Material 3 attr per allowlisted M3 token (fallback resolution for
     *  built-ins and for tokens a pack did not specify). */
    private static final Map<String, Integer> M3_ATTRS = m3Attrs();

    private static Map<String, Integer> m3Attrs() {
        Map<String, Integer> attrs = new LinkedHashMap<>();
        attrs.put("colorPrimary", com.google.android.material.R.attr.colorPrimary);
        attrs.put("colorOnPrimary", com.google.android.material.R.attr.colorOnPrimary);
        attrs.put("colorPrimaryContainer", com.google.android.material.R.attr.colorPrimaryContainer);
        attrs.put("colorOnPrimaryContainer", com.google.android.material.R.attr.colorOnPrimaryContainer);
        attrs.put("colorSecondary", com.google.android.material.R.attr.colorSecondary);
        attrs.put("colorOnSecondary", com.google.android.material.R.attr.colorOnSecondary);
        attrs.put("colorSecondaryContainer", com.google.android.material.R.attr.colorSecondaryContainer);
        attrs.put("colorOnSecondaryContainer", com.google.android.material.R.attr.colorOnSecondaryContainer);
        attrs.put("colorTertiary", com.google.android.material.R.attr.colorTertiary);
        attrs.put("colorOnTertiary", com.google.android.material.R.attr.colorOnTertiary);
        attrs.put("colorTertiaryContainer", com.google.android.material.R.attr.colorTertiaryContainer);
        attrs.put("colorOnTertiaryContainer", com.google.android.material.R.attr.colorOnTertiaryContainer);
        attrs.put("colorErrorContainer", com.google.android.material.R.attr.colorErrorContainer);
        attrs.put("colorOnErrorContainer", com.google.android.material.R.attr.colorOnErrorContainer);
        attrs.put("colorSurfaceInverse", com.google.android.material.R.attr.colorSurfaceInverse);
        attrs.put("colorOnSurfaceInverse", com.google.android.material.R.attr.colorOnSurfaceInverse);
        attrs.put("colorPrimaryInverse", com.google.android.material.R.attr.colorPrimaryInverse);
        attrs.put("colorSurfaceDim", com.google.android.material.R.attr.colorSurfaceDim);
        attrs.put("colorSurfaceBright", com.google.android.material.R.attr.colorSurfaceBright);
        attrs.put("colorError", com.google.android.material.R.attr.colorError);
        attrs.put("colorOnError", com.google.android.material.R.attr.colorOnError);
        attrs.put("colorOutline", com.google.android.material.R.attr.colorOutline);
        attrs.put("colorOutlineVariant", com.google.android.material.R.attr.colorOutlineVariant);
        attrs.put("colorSurface", com.google.android.material.R.attr.colorSurface);
        attrs.put("colorOnSurface", com.google.android.material.R.attr.colorOnSurface);
        attrs.put("colorSurfaceVariant", com.google.android.material.R.attr.colorSurfaceVariant);
        attrs.put("colorOnSurfaceVariant", com.google.android.material.R.attr.colorOnSurfaceVariant);
        attrs.put("colorSurfaceContainerLowest", com.google.android.material.R.attr.colorSurfaceContainerLowest);
        attrs.put("colorSurfaceContainerLow", com.google.android.material.R.attr.colorSurfaceContainerLow);
        attrs.put("colorSurfaceContainer", com.google.android.material.R.attr.colorSurfaceContainer);
        attrs.put("colorSurfaceContainerHigh", com.google.android.material.R.attr.colorSurfaceContainerHigh);
        attrs.put("colorSurfaceContainerHighest", com.google.android.material.R.attr.colorSurfaceContainerHighest);
        return attrs;
    }

    /** Fallback resolution for app tokens the pack (or a built-in) omits. */
    private static Integer appTokenFallback(Context themed, String token) {
        switch (token) {
            case "wallpaper_scrim":
                return 0x66000000;
            case "folder_icon_bg":
            case "file_icon_bg":
                return attrColor(themed, com.google.android.material.R.attr.colorSecondaryContainer);
            case "list_highlight":
                return attrColor(themed, com.google.android.material.R.attr.colorSurfaceContainerHigh);
            case "nav_bar":
            case "status_bar":
            case "editor_background":
            case "editor_gutter":
                return attrColor(themed, com.google.android.material.R.attr.colorSurface);
            case "editor_text":
                return attrColor(themed, com.google.android.material.R.attr.colorOnSurface);
            case "editor_line_number":
            case "editor_line_number_current":
                return attrColor(themed, com.google.android.material.R.attr.colorOnSurfaceVariant);
            case "editor_selection":
                return attrColor(themed, com.google.android.material.R.attr.colorSecondaryContainer);
            case "editor_current_line":
                return attrColor(themed, com.google.android.material.R.attr.colorSurfaceContainerHigh);
            case "editor_match_highlight":
                return attrColor(themed, com.google.android.material.R.attr.colorPrimaryContainer);
            default:
                return null;
        }
    }

    private static int attrColor(Context context, int attr) {
        try {
            return MaterialColors.getColor(context, attr, Color.TRANSPARENT);
        } catch (Exception e) {
            return Color.TRANSPARENT;
        }
    }

    /**
     * Publishes the active theme to a plugin invocation as bounded
     * primitives only, attached via {@link PluginTheme#putThemeExtras} with
     * the {@link io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts}
     * theme extras: theme id (resolved base id), dark flag, accent ARGB
     * (present when non-zero) and the palette Bundle — every allowlisted
     * {@link ThemePackContracts#COLOR_TOKENS} name resolved to an ARGB int
     * (pack token, else the caller's actual themed Material attrs for
     * built-ins, else a sensible token fallback). Editor syntax colors are
     * NOT in the palette (host editor consumes
     * {@link #editorSyntax(Context)}); no wallpaper/preview file paths, no
     * provider or package names cross this boundary.
     */
    public static void applyPluginThemeExtras(Context context, Intent intent) {
        if (intent == null) return;
        try {
            Context app = context == null ? null : context.getApplicationContext();
            if (app == null) return;
            if (sAppContext == null) sAppContext = app;
            ThemePacks.Snapshot pack = ThemePacks.active(app);
            String baseId = appliedBaseId(app);
            String id = pack == null ? baseId : pack.packageName + "/" + pack.id;
            boolean dark;
            if (pack != null && pack.dark != null) {
                dark = pack.dark;
            } else if (BuiltInThemes.DARK_ID.equals(baseId)
                    || BuiltInThemes.BLACK_ID.equals(baseId)) {
                dark = true;
            } else if (BuiltInThemes.LIGHT_ID.equals(baseId)) {
                dark = false;
            } else {
                dark = (app.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
            }
            int accent = resolveAccentArgb(app, pack, context);

            Bundle palette = new Bundle();
            Context themed = resolvePaletteContext(app, context);
            for (String token : ThemePackContracts.COLOR_TOKENS) {
                Integer color = pack == null ? null : pack.colors.get(token);
                if (color == null && "colorPrimary".equals(token)
                        && pack != null && pack.accent != null) {
                    color = pack.accent;
                }
                if (color == null) {
                    Integer attr = M3_ATTRS.get(token);
                    if (attr != null) color = attrColor(themed, attr);
                    else color = appTokenFallback(themed, token);
                }
                if (color != null) palette.putInt(token, color);
            }
            PluginTheme.putThemeExtras(intent, id, dark, accent != 0, accent,
                    palette);
        } catch (Exception ignored) {
        }
    }

    /**
     * Best-effort accent for plugin extras: pack primary/accent, else the
     * user's configured accent (preset/custom), else 0.
     */
    private static int resolveAccentArgb(Context app, ThemePacks.Snapshot pack,
                                         Context caller) {
        if (pack != null) {
            Integer primary = pack.colors.get("colorPrimary");
            if (primary == null) primary = pack.accent;
            return primary == null ? 0 : primary;
        }
        try {
            Context prefSource = caller != null ? caller : app;
            String mode = PreferenceManager.getDefaultSharedPreferences(prefSource)
                    .getString(ACCENT_PREF_KEY, null);
            if (mode == null || mode.isEmpty() || "follow".equals(mode)) return 0;
            if (mode.startsWith("preset:")) return presetArgb(mode.substring("preset:".length()));
            if ("custom".equals(mode)) {
                return PreferenceManager.getDefaultSharedPreferences(prefSource)
                        .getInt(ACCENT_CUSTOM_PREF_KEY, 0);
            }
            return presetArgb(mode);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Context whose Material attrs reflect the applied theme: the caller's
     * own (an Activity after applySaved/applyAccent already carries the
     * pack resource overrides), or a wrapper of the current base style with
     * the closest accent overlay for non-activity callers.
     */
    private static Context resolvePaletteContext(Context app, Context caller) {
        if (caller instanceof Activity) return caller;
        try {
            ContextThemeWrapper wrapper =
                    new ContextThemeWrapper(app, currentStyleRes(app));
            Integer argb = resolveUserAccent(app);
            if (argb != 0) {
                int overlay = AccentOverlays.RES[hueOverlayIndex(argb)];
                if (overlay != 0) wrapper.getTheme().applyStyle(overlay, true);
            }
            return wrapper;
        } catch (Exception e) {
            return app;
        }
    }

    /** The user's accent pref as ARGB, or 0 when following material color. */
    private static int resolveUserAccent(Context context) {
        try {
            String mode = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(ACCENT_PREF_KEY, null);
            if (mode == null || mode.isEmpty() || "follow".equals(mode)) return 0;
            if (mode.startsWith("preset:")) return presetArgb(mode.substring("preset:".length()));
            if ("custom".equals(mode)) {
                return PreferenceManager.getDefaultSharedPreferences(context)
                        .getInt(ACCENT_CUSTOM_PREF_KEY, 0);
            }
            return presetArgb(mode);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Creation path, called by BaseActivity BEFORE super.onCreate(): base
     * style + per-activity night mode (delegate.setLocalNightMode, avoiding
     * the process-global setDefaultNightMode) + plugin hook.
     *
     * <p>Do NOT call this after the activity was created: delegate
     * setLocalNightMode performs AppCompat's day-night handling and WILL
     * recreate a live activity, bypassing any onThemeChanged() override.
     * For refreshes of a live screen use {@link #applyInPlace(Activity)}.
     */
    public static void applySaved(Activity activity) {
        if (sAppContext == null) {
            try {
                sAppContext = activity.getApplicationContext();
            } catch (Exception ignored) {
            }
        }
        ensureBuiltIns();
        ThemePlugin plugin = resolvePlugin(activity);
        if (plugin == null) return;
        try {
            activity.setTheme(plugin.styleRes());
        } catch (Exception ignored) {
        }
        try {
            if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                ((androidx.appcompat.app.AppCompatActivity) activity).getDelegate()
                        .setLocalNightMode(plugin.nightMode());
            }
        } catch (Exception ignored) {
        }
        try {
            plugin.apply(activity);
        } catch (Exception ignored) {
        }
    }

    private static ThemePlugin resolvePlugin(Activity activity) {
        ensureBuiltIns();
        synchronized (ThemeRegistry.class) {
            ThemePlugin plugin = PLUGINS.get(appliedBaseId(activity));
            if (plugin == null && !PLUGINS.isEmpty()) {
                plugin = PLUGINS.values().iterator().next();
            }
            return plugin;
        }
    }

    /** Restyles a live editor without asking AppCompat to recreate its activity. */
    public static void applyInPlace(Activity activity) {
        try {
            if (sAppContext == null) sAppContext = activity.getApplicationContext();
        } catch (Exception ignored) {
        }
        applyNightModeInPlace(activity);
        ThemePlugin plugin = resolvePlugin(activity);
        if (plugin != null) {
            try {
                Resources.Theme base = activity.getResources().newTheme();
                base.applyStyle(plugin.styleRes(), true);
                activity.getTheme().setTo(base);
            } catch (Exception ignored) {
            }
            try {
                plugin.apply(activity);
            } catch (Exception ignored) {
            }
        }
        try {
            applyAccent(activity);
        } catch (Exception ignored) {
        }
        try {
            applySystemBars(activity);
        } catch (Exception ignored) {
        }
        try {
            applyActivityBackground(activity);
        } catch (Exception ignored) {
        }
    }

    /** Update this activity's resources without delegate-driven recreation. */
    @SuppressWarnings("deprecation")
    private static void applyNightModeInPlace(Activity activity) {
        try {
            Resources res = activity.getResources();
            Configuration current = res.getConfiguration();
            int target;
            int mode = modeFor(activity, appliedBaseId(activity));
            ThemePacks.Snapshot pack = ThemePacks.active(activity);
            if (pack != null && pack.dark != null) {
                target = pack.dark ? Configuration.UI_MODE_NIGHT_YES
                        : Configuration.UI_MODE_NIGHT_NO;
            } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
                target = Configuration.UI_MODE_NIGHT_YES;
            } else if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
                target = Configuration.UI_MODE_NIGHT_NO;
            } else {
                // Follow system: read the app-level configuration.
                Context app = sAppContext != null
                        ? sAppContext : activity.getApplicationContext();
                target = app.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
            }
            if ((current.uiMode & Configuration.UI_MODE_NIGHT_MASK) == target) return;
            Configuration configuration = new Configuration(current);
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | target;
            res.updateConfiguration(configuration, res.getDisplayMetrics());
        } catch (Exception ignored) {
        }
    }

    private static void ensureBuiltIns() {
        if (builtInsLoaded) return;
        builtInsLoaded = true;
        try {
            BuiltInThemes.registerAll();
        } catch (Exception ignored) {
        }
    }
}
