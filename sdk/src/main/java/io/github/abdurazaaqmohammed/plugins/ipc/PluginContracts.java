package io.github.abdurazaaqmohammed.plugins.ipc;

/**
 * Wire protocol between the host and external (out-of-process) plugins.
 *
 * <p>External plugins are normally-installed APKs with their own UID and
 * permissions. They expose exported activities with one of the intent
 * actions below; the host always invokes them with explicit intents and
 * only after the user pins the plugin's signing certificate. This class
 * lives in the SDK so both sides share the exact strings.
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
     * Act on an APK. Host sends EXTRA_APK_URI (read grant) + EXTRA_APK_NAME.
     * Fire-and-forget; RESULT_OK acknowledges.
     */
    public static final String ACTION_APK =
            "io.github.abdurazaaqmohammed.MPManager.action.APK_ACTION";

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
     * Setting entry type: "boolean" (host persists EXTRA_VALUE) or "action"
     * (tap opens the config screen, nothing persisted). Default "boolean".
     */
    public static final String META_SETTING_TYPE =
            "io.github.abdurazaaqmohammed.MPManager.SETTING_TYPE";

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

    /** Short human-readable result message (FILE_MENU / APK out). */
    public static final String EXTRA_MESSAGE =
            "io.github.abdurazaaqmohammed.MPManager.extra.MESSAGE";

    /** Output file URI produced by the plugin (FILE_MENU out). */
    public static final String EXTRA_OUTPUT_URI =
            "io.github.abdurazaaqmohammed.MPManager.extra.OUTPUT_URI";

    /** Max full-text bytes the host sends to editor plugins. */
    public static final int MAX_FULL_TEXT_BYTES = 150 * 1024;
}
