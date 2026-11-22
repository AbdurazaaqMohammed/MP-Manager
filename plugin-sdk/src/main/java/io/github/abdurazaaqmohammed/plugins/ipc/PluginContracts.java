package io.github.abdurazaaqmohammed.plugins.ipc;

/**
 * Wire protocol between the host and external (out-of-process) plugins.
 *
 * <p>External plugins are normally-installed APKs with their own UID and
 * permissions. They expose exported activities with one of the intent
 * actions below; the host always invokes them with explicit intents and
 * only after the user pins the plugin's signing certificate. This class
 * lives in the slim plugin SDK so both sides share the exact strings.
 *
 * <p>Hook and event protocol: a plugin declares the stable hook ids it
 * serves via {@link #META_HOOKS} and handles {@link #ACTION_HOOK}
 * invocations ({@link #EXTRA_HOOK_ID} selects the hook); it registers an
 * exported receiver for {@link #ACTION_EVENT} broadcasts (the event id also
 * rides in {@link #EXTRA_HOOK_ID}). Payloads are small Bundles with
 * primitive values ({@link #PAYLOAD_KEY_FILE_NAME}, {@link #PAYLOAD_KEY_MIME})
 * and never carry raw paths, file contents or URI grants.
 */
public final class PluginContracts {

    private PluginContracts() {
    }

    // ---------- intent actions (plugin manifest intent-filters) ----------

    /** Open a plugin screen from the sidebar. Extras: EXTRA_PLUGIN_ID. */
    public static final String ACTION_SIDEBAR_OPEN =
            "io.github.abdurazaaqmohammed.MPManager.action.SIDEBAR_OPEN";

    /**
     * Configure a setting. Host starts for result; plugin returns
     * EXTRA_VALUE (boolean or string). Extras in: EXTRA_PLUGIN_ID,
     * EXTRA_KEY, EXTRA_VALUE (current).
     */
    public static final String ACTION_SETTING_CONFIG =
            "io.github.abdurazaaqmohammed.MPManager.action.SETTING_CONFIG";

    /**
     * Run a file action. Files arrive as content URIs (ClipData + data URI,
     * read grant; write grant only when the plugin may modify in place).
     * Plugin returns EXTRA_MESSAGE and optionally EXTRA_OUTPUT_URI.
     */
    public static final String ACTION_FILE_MENU =
            "io.github.abdurazaaqmohammed.MPManager.action.FILE_MENU";

    /**
     * Transform editor text. Host sends EXTRA_SELECTED_TEXT (+ truncated
     * EXTRA_FULL_TEXT); plugin returns EXTRA_REPLACE_SELECTION and/or
     * EXTRA_SET_FULL_TEXT (+ EXTRA_NEW_SELECTION).
     */
    public static final String ACTION_EDITOR =
            "io.github.abdurazaaqmohammed.MPManager.action.EDITOR_ACTION";

    /**
     * Act on an APK. Host sends a staged copy URI (read+write grant) +
     * EXTRA_APK_NAME. The plugin processes it and rewrites the same URI
     * with the result, then returns RESULT_OK with EXTRA_MESSAGE and
     * optionally EXTRA_OUTPUT_NAME (suggested file name for the result).
     */
    public static final String ACTION_APK =
            "io.github.abdurazaaqmohammed.MPManager.action.APK_ACTION";

    /**
     * Invoke one declared hook. Host starts the hook activity for result
     * with EXTRA_HOOK_ID = the stable hook id, EXTRA_HOOK_PAYLOAD (Bundle,
     * primitive keys/values only, <= 16 KiB) and the host capability/theme
     * extras. Plugin returns RESULT_OK with EXTRA_MESSAGE (and any
     * hook-specific result extras). Declared ids live in META_HOOKS.
     */
    public static final String ACTION_HOOK =
            "io.github.abdurazaaqmohammed.MPManager.action.HOOK";

    /**
     * Broadcast event. Host sends an explicit broadcast to the plugin's
     * exported receiver with EXTRA_HOOK_ID = the stable event id and
     * EXTRA_HOOK_PAYLOAD (Bundle, primitive values only; no raw paths, no
     * file contents, no URI grants).
     */
    public static final String ACTION_EVENT =
            "io.github.abdurazaaqmohammed.MPManager.action.EVENT";

    // ---------- plugin manifest meta-data ----------

    /** Stable namespaced id, e.g. "mypack.hash". */
    public static final String META_PLUGIN_ID =
            "io.github.abdurazaaqmohammed.MPManager.PLUGIN_ID";

    /** Comma-separated mime list for file actions, e.g. "text/*,application/zip". Empty = all. */
    public static final String META_FILE_MIME =
            "io.github.abdurazaaqmohammed.MPManager.FILE_MIME";

    /** Regex matched against the file name for file actions. Empty = all. */
    public static final String META_FILE_PATTERN =
            "io.github.abdurazaaqmohammed.MPManager.FILE_PATTERN";

    /** Display title override (sidebar entries). Empty = activity label. */
    public static final String META_TITLE =
            "io.github.abdurazaaqmohammed.MPManager.TITLE";

    /** Setting pref key for setting entries. Empty = plugin id. */
    public static final String META_SETTING_KEY =
            "io.github.abdurazaaqmohammed.MPManager.SETTING_KEY";

    /**
     * Comma-separated APK-action options for the host material options dialog.
     * Each entry is either a label (used as both id and label) or
     * "Label|value". The selected value is sent to the plugin activity
     * via the "method" extra.
     */
    public static final String META_APK_CHOICES =
            "io.github.abdurazaaqmohammed.MPManager.APK_CHOICES";

    /**
     * Whether the host should show an "Auto sign" checkbox. "1" = yes.
     */
    public static final String META_APK_AUTO_SIGN =
            "io.github.abdurazaaqmohammed.MPManager.APK_AUTO_SIGN";

    /**
     * Setting entry type: "boolean" (host persists EXTRA_VALUE) or "action"
     * (tap opens the config screen, nothing persisted). Default "boolean".
     */
    public static final String META_SETTING_TYPE =
            "io.github.abdurazaaqmohammed.MPManager.SETTING_TYPE";

    /**
     * Comma-separated stable hook ids served by the hook activity, e.g.
     * "toolbar.actions,editor.toolbar". Unknown ids are ignored by the
     * host; at most 32 entries and 2048 characters (see PluginRequests).
     */
    public static final String META_HOOKS =
            "io.github.abdurazaaqmohammed.MPManager.HOOKS";

    /**
     * Declared wire contract version. Absent = 1; the host accepts 1.
     */
    public static final String META_CONTRACT_VERSION =
            "io.github.abdurazaaqmohammed.MPManager.CONTRACT_VERSION";

    // ---------- wire version ----------

    /** Current hook/event contract version. */
    public static final int CONTRACT_VERSION = 1;

    // ---------- extras ----------

    public static final String EXTRA_PLUGIN_ID =
            "io.github.abdurazaaqmohammed.MPManager.extra.PLUGIN_ID";

    /** Setting key being configured (SETTING_CONFIG in). */
    public static final String EXTRA_KEY =
            "io.github.abdurazaaqmohammed.MPManager.extra.KEY";

    /** Current/new setting value: boolean or String (SETTING_CONFIG). */
    public static final String EXTRA_VALUE =
            "io.github.abdurazaaqmohammed.MPManager.extra.VALUE";

    /** Display names of the shared files (FILE_MENU in). */
    public static final String EXTRA_FILE_NAMES =
            "io.github.abdurazaaqmohammed.MPManager.extra.FILE_NAMES";

    /** Mime type of the shared file(s) (FILE_MENU in). */
    public static final String EXTRA_MIME =
            "io.github.abdurazaaqmohammed.MPManager.extra.MIME";

    /** Editor selection, "" when empty (EDITOR in). */
    public static final String EXTRA_SELECTED_TEXT =
            "io.github.abdurazaaqmohammed.MPManager.extra.SELECTED_TEXT";

    /** Truncated full document, "" when too large (EDITOR in). */
    public static final String EXTRA_FULL_TEXT =
            "io.github.abdurazaaqmohammed.MPManager.extra.FULL_TEXT";

    /** Replacement for the selection (EDITOR out). */
    public static final String EXTRA_REPLACE_SELECTION =
            "io.github.abdurazaaqmohammed.MPManager.extra.REPLACE_SELECTION";

    /** Replacement for the whole document (EDITOR out). */
    public static final String EXTRA_SET_FULL_TEXT =
            "io.github.abdurazaaqmohammed.MPManager.extra.SET_FULL_TEXT";

    /** APK display name (APK in). */
    public static final String EXTRA_APK_NAME =
            "io.github.abdurazaaqmohammed.MPManager.extra.APK_NAME";

    /** Short human-readable result message (FILE_MENU / APK / hooks out). */
    public static final String EXTRA_MESSAGE =
            "io.github.abdurazaaqmohammed.MPManager.extra.MESSAGE";

    /** Output file URI produced by the plugin (FILE_MENU out). */
    public static final String EXTRA_OUTPUT_URI =
            "io.github.abdurazaaqmohammed.MPManager.extra.OUTPUT_URI";

    /** Suggested file name for the plugin's result (APK out). */
    public static final String EXTRA_OUTPUT_NAME =
            "io.github.abdurazaaqmohammed.MPManager.extra.OUTPUT_NAME";

    /** int contract version the host attaches to every invocation (host in). */
    public static final String EXTRA_CONTRACT_VERSION =
            "io.github.abdurazaaqmohammed.MPManager.extra.CONTRACT_VERSION";

    /** Host package name (host in). */
    public static final String EXTRA_HOST_PACKAGE =
            "io.github.abdurazaaqmohammed.MPManager.extra.HOST_PACKAGE";

    /** String[] of stable hook ids the host currently supports (host in). */
    public static final String EXTRA_HOST_HOOKS =
            "io.github.abdurazaaqmohammed.MPManager.extra.HOST_HOOKS";

    /**
     * Stable hook id (ACTION_HOOK invocations) or stable event id
     * (ACTION_EVENT broadcasts) (host in).
     */
    public static final String EXTRA_HOOK_ID =
            "io.github.abdurazaaqmohammed.MPManager.extra.HOOK_ID";

    /**
     * Bundle of primitive key -> primitive value entries (e.g.
     * {@link #PAYLOAD_KEY_FILE_NAME}, {@link #PAYLOAD_KEY_MIME}), at most
     * 16 KiB (host in). Events never carry raw paths, file contents or
     * URI grants.
     */
    public static final String EXTRA_HOOK_PAYLOAD =
            "io.github.abdurazaaqmohammed.MPManager.extra.HOOK_PAYLOAD";

    /** Theme id currently applied by the host (host in). */
    public static final String EXTRA_THEME_ID =
            "io.github.abdurazaaqmohammed.MPManager.extra.THEME_ID";

    /** int accent color of the current host theme (host in). */
    public static final String EXTRA_THEME_ACCENT =
            "io.github.abdurazaaqmohammed.MPManager.extra.THEME_ACCENT";

    /** boolean dark flag of the current host theme (host in). */
    public static final String EXTRA_THEME_DARK =
            "io.github.abdurazaaqmohammed.MPManager.extra.THEME_DARK";

    /**
     * Bundle of allowlisted palette name -> int color primitives only
     * (host in). See PluginTheme.PALETTE_KEYS.
     */
    public static final String EXTRA_THEME_PALETTE =
            "io.github.abdurazaaqmohammed.MPManager.extra.THEME_PALETTE";

    /** Plugin -> host request: apply the theme with this id. */
    public static final String EXTRA_APPLY_THEME_ID =
            "io.github.abdurazaaqmohammed.MPManager.extra.APPLY_THEME_ID";

    // ---------- stable hooks (host allowlist) ----------

    /** Extra footer line under the sidebar tool list. */
    public static final String HOOK_SIDEBAR_FOOTER = "sidebar.footer";

    /** Extra entries appended to the main toolbar overflow/actions area. */
    public static final String HOOK_TOOLBAR_ACTIONS = "toolbar.actions";

    /** Extra entries appended to the file context menu. */
    public static final String HOOK_FILE_MENU_EXTRA = "filemenu.extra";

    /** Extra entries appended to the ELF info dialog. */
    public static final String HOOK_ELF_DIALOG_EXTRA = "dialog.elf.extra";

    /** Extra "more" entries appended to the APK info dialog. */
    public static final String HOOK_APK_DIALOG_MORE = "dialog.apk.more";

    /** Extra actions appended to the editor toolbar. */
    public static final String HOOK_EDITOR_ACTIONS = "editor.toolbar";

    // ---------- events (host allowlist) ----------

    /** Host finished launching; sent once the main UI is ready. */
    public static final String EVENT_APP_LAUNCH = "app.launch";

    /** A file was opened; payload has file_name and mime only. */
    public static final String EVENT_FILE_OPENED = "file.opened";

    /**
     * The active theme changed. No payload required; the theme info rides
     * in the common EXTRA_THEME_* extras.
     */
    public static final String EVENT_THEME_CHANGED = "theme.changed";

    // ---------- payload keys (primitive Bundle values only) ----------

    /** Display file name in event payloads (no path, no URI). */
    public static final String PAYLOAD_KEY_FILE_NAME = "file_name";

    /** Mime type in event payloads. */
    public static final String PAYLOAD_KEY_MIME = "mime";

    /** Max full-text bytes the host sends to editor plugins. */
    public static final int MAX_FULL_TEXT_BYTES = 150 * 1024;
}
