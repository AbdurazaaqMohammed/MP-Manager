package io.github.abdurazaaqmohammed.plugins.themes;

/**
 * Wire contract between the host and external theme providers. A provider
 * is a normal installed app that
 *
 * <ul>
 *   <li>exposes an activity with intent action {@link #ACTION_THEME}
 *       (category DEFAULT) so PackageManager discovery sees it, and</li>
 *   <li>declares application meta-data {@link #META_AUTHORITY} = the
 *       authority of its ContentProvider serving the fixed one-row
 *       {@code content://<authority>/theme} table.</li>
 * </ul>
 *
 * <p>Row columns: required INTEGER {@link #COLUMN_CONTRACT} (=1), TEXT
 * {@link #COLUMN_ID} and {@link #COLUMN_NAME}; optional INTEGER
 * {@link #COLUMN_ACCENT} / {@link #COLUMN_DARK} / {@link #COLUMN_SCRIM},
 * TEXT {@link #COLUMN_COLORS} ({@code token -> "#AARRGGBB"} JSON) and
 * {@link #COLUMN_STRINGS} ({@code token -> {BCP47 locale -> text}} JSON),
 * plus TEXT {@link #COLUMN_WALLPAPER} whose value must be exactly
 * {@code content://<same authority>/wallpaper}. No locale query hint, no
 * other endpoints, no cross-authority URIs.
 *
 * <p>This class lives in the app (not the SDK) so the wire format can only
 * be consumed through the host's defensive parser.
 */
public final class ThemePackContracts {

    private ThemePackContracts() {
    }

    // ---------- discovery ----------

    /** Intent action a provider activity declares (with category DEFAULT). */
    public static final String ACTION_THEME =
            "io.github.abdurazaaqmohammed.MPManager.action.THEME";

    /** Application meta-data carrying the provider's ContentProvider authority. */
    public static final String META_AUTHORITY =
            "io.github.abdurazaaqmohammed.MPManager.THEME_PROVIDER";

    // ---------- fixed endpoints ----------

    public static final String PATH_THEME = "theme";
    public static final String PATH_WALLPAPER = "wallpaper";
    public static final String PATH_PREVIEW = "preview";

    // ---------- /theme row columns ----------

    public static final String COLUMN_CONTRACT = "contract";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_ACCENT = "accent";
    public static final String COLUMN_DARK = "dark";
    public static final String COLUMN_SCRIM = "scrim";
    public static final String COLUMN_COLORS = "colors";
    public static final String COLUMN_STRINGS = "strings";
    public static final String COLUMN_WALLPAPER = "wallpaper";
    public static final String COLUMN_PREVIEW = "preview";

    /** Required wire version reported in the contract column. */
    public static final int CONTRACT_VERSION = 1;

    // ---------- limits ----------

    public static final int MAX_COLORS_JSON_CHARS = 256 * 1024;
    public static final int MAX_STRINGS_JSON_CHARS = 256 * 1024;
    public static final long MAX_WALLPAPER_BYTES = 8L * 1024 * 1024;
    public static final int MAX_IMAGE_DIMENSION_PX = 8192;
    public static final long MAX_IMAGE_PIXELS = 4096L * 4096L;
    public static final long MAX_PREVIEW_BYTES = 256 * 1024;
    public static final int MAX_PREVIEW_DIMENSION_PX = 2048;
    public static final long MAX_PREVIEW_PIXELS = 2048L * 2048L;
    public static final int MAX_TEXT_CHARS = 100;
    public static final int MAX_LOCALES_PER_LABEL = 32;

    /** Only PNG and JPEG images are accepted for wallpaper/preview. */
    public static boolean isSupportedImageMime(String mime) {
        return "image/png".equals(mime) || "image/jpeg".equals(mime);
    }

    // ---------- allowlists ----------

    /** Color tokens the host accepts (canonical spelling; matching is
     *  case/separator-insensitive). Material 3 names plus the app tokens. */
    public static final String[] COLOR_TOKENS = {
            // Material 3: exact resource slots in ThemePackPalette.
            "colorPrimary", "colorOnPrimary",
            "colorPrimaryContainer", "colorOnPrimaryContainer",
            "colorSecondary", "colorOnSecondary",
            "colorSecondaryContainer", "colorOnSecondaryContainer",
            "colorTertiary", "colorOnTertiary",
            "colorError", "colorOnError",
            "colorOutline", "colorOutlineVariant",
            "colorSurface", "colorOnSurface",
            "colorSurfaceVariant", "colorOnSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow",
            "colorSurfaceContainer", "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest",
            // app tokens
            "wallpaper_scrim", "folder_icon_bg", "file_icon_bg",
            "list_highlight", "nav_bar", "status_bar",
            "editor_background", "editor_text",
    };

    /** Label keys the host accepts (exact keys; matching is also
     *  case/separator-insensitive via {@link ThemePackParser}). */
    public static final String[] LABEL_KEYS = {
            "tab.files", "tab.root", "tab.general",
            "tab.behavior", "tab.other", "settings.theme",
    };
}
