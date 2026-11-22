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
 * <p>Row columns: required INTEGER {@link #COLUMN_CONTRACT} ({@link #CONTRACT_VERSION_V1}
 * or {@link #CONTRACT_VERSION_V2}), TEXT {@link #COLUMN_ID} and {@link #COLUMN_NAME};
 * optional INTEGER {@link #COLUMN_ACCENT} / {@link #COLUMN_DARK} / {@link #COLUMN_SCRIM},
 * TEXT {@link #COLUMN_COLORS} ({@code token -> "#AARRGGBB"} JSON), {@link #COLUMN_STRINGS}
 * ({@code token -> {BCP47 locale -> text}} JSON), TEXT {@link #COLUMN_EDITOR_SYNTAX}
 * ({@code category -> "#AARRGGBB"} JSON, v2) and TEXT {@link #COLUMN_WALLPAPER_SCOPE}
 * ({@code "files"} | {@code "all"} | {@code "none"}, v2; default {@code "files"}),
 * plus TEXT {@link #COLUMN_WALLPAPER} whose value must be exactly
 * {@code content://<same authority>/wallpaper}. No locale query hint, no
 * other endpoints, no cross-authority URIs.
 *
 * <p>Contract versions: v1 rows are fully accepted (they simply have no
 * {@link #COLUMN_EDITOR_SYNTAX} / {@link #COLUMN_WALLPAPER_SCOPE} columns);
 * v2 rows add those two optional columns. Unknown columns are ignored by
 * the host parser so future revisions stay wire-compatible.
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
    /** v2: optional TEXT JSON {@code category -> "#AARRGGBB"} for editor syntax colors. */
    public static final String COLUMN_EDITOR_SYNTAX = "editor_syntax";
    /** v2: optional TEXT wallpaper application scope; see {@link #WALLPAPER_SCOPE_FILES}. */
    public static final String COLUMN_WALLPAPER_SCOPE = "wallpaper_scope";

    /** Wire version reported in the contract column. */
    public static final int CONTRACT_VERSION_V1 = 1;
    /** Wire version adding editor_syntax and wallpaper_scope columns. */
    public static final int CONTRACT_VERSION_V2 = 2;
    /**
     * Latest contract version this build accepts. Providers should serve
     * {@link #CONTRACT_VERSION_V2} (or {@link #CONTRACT_VERSION_V1} for
     * legacy packs); the host rejects unknown higher versions.
     */
    public static final int CONTRACT_VERSION = CONTRACT_VERSION_V2;

    // ---------- wallpaper scopes ----------

    /** Wallpaper applies only to file-list panes (v1 behavior, the default). */
    public static final String WALLPAPER_SCOPE_FILES = "files";
    /** Wallpaper applies to every themed activity background. */
    public static final String WALLPAPER_SCOPE_ALL = "all";
    /** Wallpaper column is ignored; no wallpaper is applied. */
    public static final String WALLPAPER_SCOPE_NONE = "none";

    /** True when raw is an accepted wallpaper scope value (exact spelling). */
    public static boolean isWallpaperScope(String raw) {
        return WALLPAPER_SCOPE_FILES.equals(raw)
                || WALLPAPER_SCOPE_ALL.equals(raw)
                || WALLPAPER_SCOPE_NONE.equals(raw);
    }

    // ---------- limits ----------

    public static final int MAX_COLORS_JSON_CHARS = 256 * 1024;
    public static final int MAX_STRINGS_JSON_CHARS = 256 * 1024;
    /** editor_syntax JSON payload cap. */
    public static final int MAX_SYNTAX_JSON_CHARS = 32 * 1024;
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
     *  case/separator-insensitive). Material 3 names plus the app tokens.
     *  Every token maps to a resource/style slot actually consumed by the
     *  host UI; tokens without a consumer are rejected here. */
    public static final String[] COLOR_TOKENS = {
            // Material 3: exact resource slots in ThemePackPalette.
            "colorPrimary", "colorOnPrimary",
            "colorPrimaryContainer", "colorOnPrimaryContainer",
            "colorSecondary", "colorOnSecondary",
            "colorSecondaryContainer", "colorOnSecondaryContainer",
            "colorTertiary", "colorOnTertiary",
            "colorTertiaryContainer", "colorOnTertiaryContainer",
            "colorError", "colorOnError",
            "colorErrorContainer", "colorOnErrorContainer",
            "colorSurfaceInverse", "colorOnSurfaceInverse", "colorPrimaryInverse",
            "colorSurfaceDim", "colorSurfaceBright",
            "colorOutline", "colorOutlineVariant",
            "colorSurface", "colorOnSurface",
            "colorSurfaceVariant", "colorOnSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow",
            "colorSurfaceContainer", "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest",
            // app tokens
            "wallpaper_scrim", "folder_icon_bg", "file_icon_bg",
            "list_highlight", "nav_bar", "status_bar",
            // editor tokens, each mapped to an editor scheme slot actually
            // consumed by the host editor: background, normal text, gutter
            // panel, gutter text, current gutter line, selection, current
            // line highlight and match highlight
            "editor_background", "editor_text",
            "editor_gutter", "editor_line_number", "editor_line_number_current",
            "editor_selection", "editor_current_line", "editor_match_highlight",
    };

    /** Label keys the host accepts (exact keys; matching is also
     *  case/separator-insensitive). */
    public static final String[] LABEL_KEYS = {
            "tab.files", "tab.root", "tab.general",
            "tab.behavior", "tab.other", "settings.theme",
    };

    /**
     * Canonical editor syntax categories accepted by the editor_syntax
     * column (matching is case/separator-insensitive). This is the exact
     * set the host editor consumes; a v2 revision may extend it, packs
     * cannot invent categories. Common TextMate scope names are accepted
     * on the wire and mapped onto these canonical categories by the
     * host parser (e.g. {@code entity.name.function} -> {@code function}).
     */
    public static final String[] EDITOR_SYNTAX_CATEGORIES = {
            "comment", "keyword", "string", "number",
            "type", "function", "operator", "variable",
    };

    /**
     * TextMate scope prefixes accepted on the wire besides the canonical
     * category names, each mapped to one canonical category. Entries are
     * dot-separated scope prefixes; any scope starting with {@code prefix.}
     * or equal to {@code prefix} maps to the category after the '='.
     * Anything not listed here (and not a canonical category) is dropped.
     */
    public static final String[] EDITOR_SYNTAX_SCOPE_ALIASES = {
            // comment
            "comment=comment",
            "punctuation.definition.comment=comment",
            // keyword
            "keyword=keyword",
            "keyword.control=keyword",
            "keyword.declaration=keyword",
            "keyword.modifier=keyword",
            "storage.modifier=keyword",
            "storage.type=keyword",
            // string
            "string=string",
            "string.quoted=string",
            "punctuation.definition.string=string",
            "constant.character.escape=string",
            // number
            "constant.numeric=number",
            "constant.numeric.integer=number",
            "constant.numeric.float=number",
            "constant.numeric.hex=number",
            "constant.language.boolean=number",
            // type
            "entity.name.type=type",
            "entity.name.class=type",
            "entity.name.interface=type",
            "entity.name.enum=type",
            "entity.name.struct=type",
            "support.type=type",
            "storage.class=type",
            // function
            "entity.name.function=function",
            "entity.name.method=function",
            "entity.function=function",
            "support.function=function",
            "meta.function-call=function",
            "meta.function-call.generic=function",
            // operator
            "keyword.operator=operator",
            "keyword.operator.assignment=operator",
            "keyword.operator.arithmetic=operator",
            "keyword.operator.logical=operator",
            "keyword.operator.comparison=operator",
            "punctuation.separator.key-value=operator",
            // variable
            "variable=variable",
            "variable.name=variable",
            "variable.other=variable",
            "variable.parameter=variable",
            "variable.language=variable",
            "meta.definition.variable=variable",
    };
}
