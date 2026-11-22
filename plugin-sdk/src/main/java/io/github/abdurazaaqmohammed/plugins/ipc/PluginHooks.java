package io.github.abdurazaaqmohammed.plugins.ipc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, dependency-free helpers for the {@link PluginContracts} hook and
 * event protocol. Safe to call from both the host and plugin sides; no
 * Android classes are touched.
 *
 * <p>All allowlists are derived from the {@link PluginContracts} constants,
 * so new stable ids added in a later contract version are picked up
 * automatically by {@link #parse}, {@link #entryId} and the is* checks.
 */
public final class PluginHooks {

    private PluginHooks() {
    }

    /** Stable hook ids (host allowlist), in declaration order. */
    private static final List<String> KNOWN_HOOKS = Collections.unmodifiableList(Arrays.asList(
            PluginContracts.HOOK_SIDEBAR_FOOTER,
            PluginContracts.HOOK_TOOLBAR_ACTIONS,
            PluginContracts.HOOK_FILE_MENU_EXTRA,
            PluginContracts.HOOK_ELF_DIALOG_EXTRA,
            PluginContracts.HOOK_APK_DIALOG_MORE,
            PluginContracts.HOOK_EDITOR_ACTIONS));

    /** Stable event ids (host allowlist), in declaration order. */
    private static final List<String> KNOWN_EVENTS = Collections.unmodifiableList(Arrays.asList(
            PluginContracts.EVENT_APP_LAUNCH,
            PluginContracts.EVENT_FILE_OPENED,
            PluginContracts.EVENT_THEME_CHANGED));

    /** True when {@code hookId} is one of the stable host hook ids. */
    public static boolean isActionHook(String hookId) {
        return hookId != null && KNOWN_HOOKS.contains(hookId);
    }

    /** True when {@code eventId} is one of the stable host event ids. */
    public static boolean isEvent(String eventId) {
        return eventId != null && KNOWN_EVENTS.contains(eventId);
    }

    /** New array copy of the stable hook ids (safe to attach to intents). */
    public static String[] actionHooks() {
        return KNOWN_HOOKS.toArray(new String[0]);
    }

    /** New array copy of the stable event ids (safe to attach to intents). */
    public static String[] eventHooks() {
        return KNOWN_EVENTS.toArray(new String[0]);
    }

    /** Unmodifiable stable hook ids in declaration order. */
    public static List<String> knownHooks() {
        return KNOWN_HOOKS;
    }

    /** Unmodifiable stable event ids in declaration order. */
    public static List<String> knownEvents() {
        return KNOWN_EVENTS;
    }

    /**
     * Parse a {@link PluginContracts#META_HOOKS} manifest value: a
     * comma-separated list of stable hook and/or event ids. Values over
     * {@link PluginRequests#MAX_META_VALUE_CHARS} characters are rejected
     * outright; empty, unknown and overlong ({@code >} 128 chars) entries
     * are ignored; duplicates collapse to their first occurrence
     * (declaration order preserved); the result is capped at
     * {@link PluginRequests#MAX_DECLARED_HOOKS} entries.
     */
    public static List<String> parse(String declared) {
        if (declared == null || declared.isEmpty()) {
            return Collections.emptyList();
        }
        if (!PluginRequests.isMetaValueOk(declared)) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String token : declared.split(",")) {
            String id = token.trim();
            if (id.isEmpty()) continue;
            if (id.length() > PluginRequests.MAX_ID_CHARS) continue;
            if (!isActionHook(id) && !isEvent(id)) continue;
            seen.add(id);
            if (seen.size() >= PluginRequests.MAX_DECLARED_HOOKS) break;
        }
        return Collections.unmodifiableList(new ArrayList<>(seen));
    }

    /**
     * Stable package-qualified entry id:
     * {@code ext:<package>/<pluginId-or-class>@<hookId>}.
     *
     * <p>Validation makes spoofed ids unable to leak into another package's
     * entry: {@code pkg} must be a valid Android application id (letter-led
     * dot segments, at least two segments, at most 128 chars),
     * {@code pluginId} / {@code className} are bounded to 128 chars and may
     * not contain {@code /}, {@code @}, {@code \} or control characters, and
     * {@code hookId} must be an allowlisted stable hook id. The class name
     * is used when {@code pluginId} is blank. Returns {@code null} when any
     * input is invalid.
     */
    public static String entryId(String pkg, String pluginId, String className, String hookId) {
        if (!isApplicationId(pkg)) return null;
        String id = pluginId != null ? pluginId.trim() : "";
        if (id.isEmpty()) {
            id = className != null ? className.trim() : "";
        }
        if (!isSafeId(id)) return null;
        String hook = hookId != null ? hookId.trim() : "";
        if (!isActionHook(hook)) return null;
        return "ext:" + pkg.trim() + "/" + id + "@" + hook;
    }

    /**
     * Merge two ordered id lists: {@code base} first, then {@code extra}
     * entries not already present (first occurrence wins, order preserved).
     * Null lists are treated as empty; null entries are skipped. The result
     * is unmodifiable.
     */
    public static List<String> mergeOrder(List<String> base, List<String> extra) {
        List<String> out = new ArrayList<>();
        appendNew(out, base);
        appendNew(out, extra);
        return Collections.unmodifiableList(out);
    }

    private static void appendNew(List<String> out, List<String> add) {
        if (add == null) return;
        for (String id : add) {
            if (id != null && !out.contains(id)) out.add(id);
        }
    }

    /** Android application id syntax: >=2 letter-led dot segments. */
    private static boolean isApplicationId(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.isEmpty() || s.length() > PluginRequests.MAX_ID_CHARS) return false;
        String[] segments = s.split("\\.", -1);
        if (segments.length < 2) return false;
        for (String segment : segments) {
            if (segment.isEmpty()) return false;
            if (!Character.isLetter(segment.charAt(0))) return false;
            for (int i = 1; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') return false;
            }
        }
        return true;
    }

    /** Bounded id with no separators that could cross entry boundaries. */
    private static boolean isSafeId(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        if (raw.length() > PluginRequests.MAX_ID_CHARS) return false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '/' || c == '@' || c == '\\' || c < 0x20 || c == 0x7F) return false;
        }
        return true;
    }
}
