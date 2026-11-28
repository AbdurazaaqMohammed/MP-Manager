package io.github.abdurazaaqmohammed.ui.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Runtime launcher-icon switching via {@code activity-alias} components.
 *
 * <p>The MAIN/LAUNCHER intent filter was moved off MainActivity onto six
 * aliases (see AndroidManifest.xml), one per bundled icon. Switching enables
 * the target alias first, verifies the outcome, then disables previously
 * enabled aliases - so at every step at least one launcher entry stays
 * enabled; a failure anywhere rolls the pass back to the pre-call state and
 * re-checks the "at least one enabled" invariant before giving up. MainActivity
 * itself is never enabled/disabled, which keeps every explicit-component
 * launch path alive: bookmark shortcuts ({@code BookmarkShortcuts} targets
 * MainActivity directly), editor "return to main"
 * ({@code EditorTasks.returnToMain}), tool shortcuts and any plugin deep
 * links. Alias components only redirect to MainActivity;
 * singleTop/alwaysRetainTaskState live on the target, so launcher taps behave
 * exactly as before.
 *
     * <p>Alias names are RELATIVE in the manifest ({@code .LauncherCrimson}),
     * so Android resolves them against the manifest {@code package} attribute.
     * That can differ from the runtime applicationId (the isolated
     * {@code .editorregression} debug build adds an applicationId suffix), so
     * full alias class names are resolved from PackageManager
     * (MAIN/LAUNCHER query, disabled components included) rather than guessed
     * from {@link Context#getPackageName()}; see {@link Choice#component}.
 *
 * <p><b>API for integration.</b>
 * <ul>
 *   <li>Settings UI ({@code SettingsController}): persist and apply the user's
 *       pick via {@link #setSelection(Context, String)}; list options with
 *       {@link #choices()}; the dialog is {@link LauncherIconDialog#show}.</li>
 *   <li>Theme packs ({@code ThemePacks.Snapshot.launcherIcon} /
 *       {@code ThemePackContracts.COLUMN_LAUNCHER_ICON}): call
 *       {@link #sync(Context, String)} whenever a pack is imported,
 *       activated, refreshed or deactivated. {@code sync} never throws, never
 *       returns null, treats blank/unknown values as {@link #ID_DEFAULT} and
 *       only overrides the icon while the user selection is
 *       {@link #ID_FOLLOW_THEME} - which is also the default selection, so a
 *       pack's request takes effect immediately while devices without any
 *       pack keep the default icon.</li>
 * </ul>
 *
 * <p><b>Limitation.</b> Android cannot turn an arbitrary plugin bitmap into a
 * launcher icon - launcher entries must be declared in the manifest at build
 * time and can only be enabled/disabled at runtime. A theme pack therefore
 * selects one of the bundled variants by identifier; it cannot ship its own
 * launcher image. Unknown identifiers fall back to the default icon, so a
 * malformed pack can never leave the app unlaunchable.
 *
 * <p>Persisted strings live in
 * {@link androidx.preference.PreferenceManager} default shared preferences:
 * {@link #PREF_SELECTION} (user pick; missing reads as {@link #ID_FOLLOW_THEME})
 * and {@link #PREF_THEME_REQUEST} (last request resolved from the active pack,
 * missing reads as {@link #ID_DEFAULT}). {@link PackageManager} component
 * state remains the source of truth for the active icon, so manual tampering
 * (adb, other apps) is still reported correctly.
 *
 * <p>The alias transition/rollback planning lives in pure static helpers
 * ({@link #planTransitions}, {@link #planRollback}, {@link #resolvePackRequest})
 * covered by {@code LauncherIconsLogicTest}; {@link #apply} only executes the
 * plan against {@link PackageManager}.
 */
public final class LauncherIcons {

    /** User selection value: icon follows the active theme pack request. */
    public static final String ID_FOLLOW_THEME = "follow_theme";

    /** Built-in bundled identifiers (also the accepted pack values). */
    public static final String ID_DEFAULT = "default";
    public static final String ID_CRIMSON = "crimson";
    public static final String ID_EMERALD = "emerald";
    public static final String ID_AMBER = "amber";
    public static final String ID_VIOLET = "violet";
    public static final String ID_MONO = "mono";

    /**
     * User pick (never null once written; missing reads as
     * {@link #ID_FOLLOW_THEME} so theme-pack requests apply out of the box).
     */
    public static final String PREF_SELECTION = "launcher_icon_selection";
    /** Last identifier resolved from the active theme pack (or ID_DEFAULT). */
    public static final String PREF_THEME_REQUEST = "launcher_icon_theme_request";

    /** One bundled launcher icon choice and its manifest alias. */
    public static final class Choice {
        /** Stable identifier shared with theme packs (lowercase, ASCII). */
        public final String id;
        /** Alias simple name as written in the manifest (relative). */
        public final String aliasSimpleName;
        public final int labelRes;
        public final int iconRes;

        Choice(String id, String aliasSimpleName, int labelRes, int iconRes) {
            this.id = id;
            this.aliasSimpleName = aliasSimpleName;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }

        /**
         * The runtime {@link ComponentName} of this alias. Relative manifest
         * names resolve against the manifest {@code package} attribute, which
         * can DIFFER from the runtime applicationId (the isolated
         * {@code .editorregression} debug build adds an applicationId suffix),
         * so the full class name is resolved from PackageManager instead of
         * being guessed as {@code <applicationId>.<alias>}; the guess remains
         * as a fallback (it is exact when applicationId == manifest package).
         */
        public ComponentName component(Context context) {
            ComponentName resolved = resolvedAliases(context).get(aliasSimpleName);
            if (resolved != null) return resolved;
            return new ComponentName(context.getPackageName(),
                    context.getPackageName() + "." + aliasSimpleName);
        }
    }

    private static final Object ALIAS_RESOLVE_LOCK = new Object();
    /** alias simple name -> runtime component; null = resolution failed. */
    private static volatile java.util.Map<String, ComponentName> sResolvedAliases;
    private static volatile String sResolvedAliasesPackage;

    /**
     * Resolves the app's MAIN/LAUNCHER activity-alias components by querying
     * PackageManager (disabled components included). Alias class names are
     * stable, so the result is cached per package.
     */
    private static java.util.Map<String, ComponentName> resolvedAliases(Context context) {
        String pkg = context.getPackageName();
        java.util.Map<String, ComponentName> cached = sResolvedAliases;
        if (cached != null && pkg.equals(sResolvedAliasesPackage)) return cached;
        synchronized (ALIAS_RESOLVE_LOCK) {
            cached = sResolvedAliases;
            if (cached != null && pkg.equals(sResolvedAliasesPackage)) return cached;
            try {
                android.content.Intent launcherIntent =
                        new android.content.Intent(android.content.Intent.ACTION_MAIN)
                                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                                .setPackage(pkg);
                java.util.List<android.content.pm.ResolveInfo> resolved =
                        context.getPackageManager().queryIntentActivities(
                                launcherIntent, PackageManager.MATCH_DISABLED_COMPONENTS);
                java.util.Map<String, ComponentName> out = new java.util.HashMap<>();
                for (android.content.pm.ResolveInfo ri : resolved) {
                    if (ri == null || ri.activityInfo == null
                            || ri.activityInfo.name == null) continue;
                    String name = ri.activityInfo.name;
                    String simple = name.substring(name.lastIndexOf('.') + 1);
                    out.put(simple, new ComponentName(ri.activityInfo.packageName, name));
                }
                if (!out.isEmpty()) {
                    sResolvedAliases = out;
                    sResolvedAliasesPackage = pkg;
                }
                return out;
            } catch (RuntimeException e) {
                return java.util.Collections.emptyMap();
            }
        }
    }

    /** Display order for the settings dialog; ICONS[0] is the fallback. */
    private static final Choice[] ICONS = {
            new Choice(ID_DEFAULT, "LauncherDefault",
                    R.string.app_icon_default, R.mipmap.ic_launcher),
            new Choice(ID_CRIMSON, "LauncherCrimson",
                    R.string.app_icon_crimson, R.mipmap.ic_launcher_crimson),
            new Choice(ID_EMERALD, "LauncherEmerald",
                    R.string.app_icon_emerald, R.mipmap.ic_launcher_emerald),
            new Choice(ID_AMBER, "LauncherAmber",
                    R.string.app_icon_amber, R.mipmap.ic_launcher_amber),
            new Choice(ID_VIOLET, "LauncherViolet",
                    R.string.app_icon_violet, R.mipmap.ic_launcher_violet),
            new Choice(ID_MONO, "LauncherMono",
                    R.string.app_icon_mono, R.mipmap.ic_launcher_mono),
    };

    /** One planned alias transition: enable ({@code true}) or disable. */
    static final class Op {
        final int index;
        final boolean enable;

        Op(int index, boolean enable) {
            this.index = index;
            this.enable = enable;
        }
    }

    private LauncherIcons() {}

    /**
     * True when {@code className} is one of the launcher alias components.
     * Tasks started from the launcher record the ALIAS component (not
     * MainActivity) in {@code TaskInfo.baseIntent}, so task-scanning code
     * that used to match the MainActivity class name must also treat
     * alias-launched main tasks as the main task - {@code EditorTasks}
     * recents classification and return-to-main do exactly that.
     */
    public static boolean isLauncherAliasClassName(String className) {
        if (className == null) return false;
        String simple = className.substring(className.lastIndexOf('.') + 1);
        for (Choice icon : ICONS) {
            if (icon.aliasSimpleName.equals(simple)) return true;
        }
        return false;
    }

    /**
     * Bundled icon choices users and theme packs can select. The returned
     * array is a fresh copy ({@link Choice} itself is immutable); element
     * {@code [0]} is always the default icon used as fallback.
     */
    public static Choice[] choices() {
        return ICONS.clone();
    }

    /**
     * True when {@code id} may be persisted as a user selection: a bundled
     * identifier, the default icon, or the user-only {@link #ID_FOLLOW_THEME}
     * value. Pack values should NOT use {@link #ID_FOLLOW_THEME} - it is a
     * user selection concept and {@link #resolvePackRequest} never yields it.
     */
    public static boolean isKnownIdentifier(String id) {
        String normalized = normalize(id);
        return choiceFor(normalized) != null || ID_FOLLOW_THEME.equals(normalized);
    }

    /**
     * Applies and persists the user's launcher-icon selection from settings.
     * Unknown ids fall back to {@link #ID_DEFAULT}.
     *
     * <p>{@link #ID_FOLLOW_THEME} selects "follow the active theme pack": the
     * icon immediately matches the last request synced from the active pack
     * (default when none), and every later {@link #sync} call keeps it in
     * step. Any other id pins the icon explicitly until the user picks
     * "follow theme" again.
     *
     * @return true when the requested icon is active afterwards.
     */
    public static synchronized boolean setSelection(Context context, String id) {
        String normalized = normalize(id);
        Choice target = choiceFor(normalized);
        if (target == null) target = ICONS[0];
        String selection = ID_FOLLOW_THEME.equals(normalized) ? ID_FOLLOW_THEME : target.id;
        if (ID_FOLLOW_THEME.equals(selection)) {
            target = choiceFor(themeRequest(context));
            if (target == null) target = ICONS[0];
        }
        if (!apply(context, target)) return false;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_SELECTION, selection).apply();
        return true;
    }

    /**
     * Theme-pack integration point (call from the theme registry on
     * initialization and every theme change; pass
     * {@code snapshot.launcherIcon}, or {@code null} when no pack is active).
     *
     * <p>Resolves the request via {@link #resolvePackRequest} (bundled ids
     * match case-insensitively; blank, {@code none}/{@code system}/
     * {@code original}, {@code follow_theme} and unknown values map to
     * {@link #ID_DEFAULT} - see the class limitation note). The resolved
     * value is persisted ({@link #PREF_THEME_REQUEST}) and applied only while
     * the user selection is {@link #ID_FOLLOW_THEME} (the default), so an
     * explicit user choice always wins over pack data.
     *
     * @return the identifier that is active now (never null).
     */
    public static synchronized String sync(Context context, String snapshotLauncherIcon) {
        Choice requested = choiceFor(resolvePackRequest(snapshotLauncherIcon));
        if (requested == null) requested = ICONS[0];
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_THEME_REQUEST, requested.id).apply();
        Choice effective = choiceFor(effectiveRequested(context));
        apply(context, effective == null ? ICONS[0] : effective);
        return currentIdentifier(context);
    }

    /** User selection as persisted by {@link #setSelection} (never null). */
    public static String selection(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_SELECTION, ID_FOLLOW_THEME);
    }

    /**
     * Identifier of the icon that is currently enabled on the launcher:
     * the active alias if one is enabled, else the persisted effective
     * choice. Never null.
     */
    public static String currentIdentifier(Context context) {
        PackageManager pm = context.getPackageManager();
        for (Choice icon : ICONS) {
            if (effectivelyEnabled(pm, icon.component(context))) return icon.id;
        }
        // Tampered state (every alias disabled): heal back to a launchable
        // state instead of reporting a value the launcher cannot show.
        if (apply(context, ICONS[0])) return ICONS[0].id;
        String fallback = effectiveRequested(context);
        return fallback != null ? fallback : ID_DEFAULT;
    }

    /**
     * Switches the active launcher alias. True no-op (zero PackageManager
     * writes) when the target is already the only enabled alias. Never
     * disables the last launcher entry: the plan enables the target before
     * anything is disabled, a failure or a mismatched final state rolls back
     * to the pre-call state, and the rollback itself is guarded by an
     * "at least one enabled" safety net.
     */
    private static synchronized boolean apply(Context context, Choice target) {
        PackageManager pm = context.getPackageManager();
        ComponentName targetComponent = target.component(context);

        // The alias must exist (guards against name drift between builds).
        try {
            pm.getActivityInfo(targetComponent, PackageManager.MATCH_DISABLED_COMPONENTS);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        int targetIndex = indexOf(target);
        if (targetIndex < 0) return false;
        boolean[] before = readStates(pm, context);
        List<Op> ops = planTransitions(before, targetIndex);
        if (ops.isEmpty()) return true; // already the only enabled alias

        List<Op> executed = new ArrayList<>();
        try {
            for (Op op : ops) {
                pm.setComponentEnabledSetting(componentAt(context, op.index),
                        op.enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                executed.add(op);
            }
        } catch (RuntimeException e) {
            rollback(pm, context, before, targetIndex, executed);
            return false;
        }

        // Verify the launcher actually took the writes; treat a silent no-op
        // like a thrown failure.
        boolean[] after = readStates(pm, context);
        if (!after[targetIndex]) {
            rollback(pm, context, before, targetIndex, executed);
            return false;
        }
        for (int i = 0; i < after.length; i++) {
            if (i != targetIndex && after[i]) {
                rollback(pm, context, before, targetIndex, executed);
                return false;
            }
        }
        return true;
    }

    /**
     * Restores {@code before} as far as possible, then keeps the "at least
     * one enabled alias" invariant: if nothing is enabled afterwards, the
     * target (the only alias known to exist and to accept writes) is forced
     * back on so the app never loses its launcher entry.
     */
    private static void rollback(PackageManager pm, Context context,
                                 boolean[] before, int targetIndex, List<Op> executed) {
        boolean[] state = before.clone();
        for (Op op : executed) state[op.index] = op.enable;
        for (int i = 0; i < state.length; i++) {
            if (state[i] == before[i]) continue;
            try {
                pm.setComponentEnabledSetting(componentAt(context, i),
                        before[i] ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (RuntimeException ignored) {
            }
        }
        boolean[] now = readStates(pm, context);
        if (!atLeastOneEnabled(now)) {
            try {
                pm.setComponentEnabledSetting(componentAt(context, targetIndex),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static ComponentName componentAt(Context context, int index) {
        return ICONS[index].component(context);
    }

    private static int indexOf(Choice target) {
        for (int i = 0; i < ICONS.length; i++) {
            if (ICONS[i].id.equals(target.id)) return i;
        }
        return -1;
    }

    private static boolean[] readStates(PackageManager pm, Context context) {
        boolean[] state = new boolean[ICONS.length];
        for (int i = 0; i < ICONS.length; i++) {
            state[i] = effectivelyEnabled(pm, componentAt(context, i));
        }
        return state;
    }

    /** Last pack request resolved by {@link #sync} (never null). */
    private static String themeRequest(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_THEME_REQUEST, ID_DEFAULT);
    }

    /** The icon {@code sync}/settings would activate right now (never null). */
    private static String effectiveRequested(Context context) {
        String selection = selection(context);
        if (ID_FOLLOW_THEME.equals(selection)) {
            String request = themeRequest(context);
            return choiceFor(request) != null ? choiceFor(request).id : ID_DEFAULT;
        }
        Choice pinned = choiceFor(selection);
        return pinned != null ? pinned.id : ID_DEFAULT;
    }

    /**
     * True when the component resolves as enabled. {@code DEFAULT} inherits
     * the manifest value: the default alias ships enabled, the variants ship
     * disabled - which is what {@code manifestEnabled} encodes per alias.
     */
    private static boolean effectivelyEnabled(PackageManager pm, ComponentName component) {
        try {
            int state = pm.getComponentEnabledSetting(component);
            return isAliasEnabledByState(state,
                    component.getClassName().endsWith(".LauncherDefault"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Pure alias-state rule shared with tests:
     * {@link PackageManager#COMPONENT_ENABLED_STATE_ENABLED} always enabled,
     * {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED} always
     * disabled, {@code DEFAULT} follows the manifest ({@code manifestEnabled}).
     */
    static boolean isAliasEnabledByState(int state, boolean manifestEnabled) {
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && manifestEnabled);
    }

    /**
     * Pure pack-request resolution (mirrors what {@link #sync} applies):
     * bundled ids pass through (case-insensitive, trimmed); blank values and
     * the synonyms {@code none}/{@code system}/{@code original}, the
     * user-only {@code follow_theme} value and anything unknown all map to
     * {@link #ID_DEFAULT}. Never returns null.
     */
    static String resolvePackRequest(String raw) {
        String normalized = normalize(raw);
        if (normalized == null || ID_FOLLOW_THEME.equals(normalized)
                || "none".equals(normalized) || "system".equals(normalized)
                || "original".equals(normalized)) {
            return ID_DEFAULT;
        }
        return choiceFor(normalized) != null ? normalized : ID_DEFAULT;
    }

    /**
     * Pure transition planner: minimal ordered ops that turn {@code state}
     * into "only {@code targetIndex} enabled". The target is enabled BEFORE
     * anything is disabled, so after every op prefix at least one alias is
     * enabled (the launcher can never lose the app). Empty list = no-op
     * (target already the only enabled alias). For a tampered all-disabled
     * state it only enables the target.
     */
    static List<Op> planTransitions(boolean[] state, int targetIndex) {
        List<Op> ops = new ArrayList<>();
        if (!state[targetIndex]) ops.add(new Op(targetIndex, true));
        for (int i = 0; i < state.length; i++) {
            if (i != targetIndex && state[i]) ops.add(new Op(i, false));
        }
        return ops;
    }

    /**
     * Pure rollback planner: ops restoring exactly {@code before} from the
     * state produced by {@code executed}. Callers must still enforce the
     * "at least one enabled" safety net, because {@code before} itself may be
     * an all-disabled (tampered) state.
     */
    static List<Op> planRollback(boolean[] before, List<Op> executed) {
        boolean[] state = before.clone();
        for (Op op : executed) state[op.index] = op.enable;
        List<Op> ops = new ArrayList<>();
        for (int i = 0; i < state.length; i++) {
            if (state[i] != before[i]) ops.add(new Op(i, before[i]));
        }
        return ops;
    }

    /** Pure invariant check shared with tests. */
    static boolean atLeastOneEnabled(boolean[] state) {
        for (boolean enabled : state) {
            if (enabled) return true;
        }
        return false;
    }

    /** Maps a normalized identifier to its bundled choice; null if none. */
    private static Choice choiceFor(String normalized) {
        if (normalized == null || ID_FOLLOW_THEME.equals(normalized)) return null;
        for (Choice icon : ICONS) {
            if (icon.id.equals(normalized)) return icon;
        }
        return null;
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
