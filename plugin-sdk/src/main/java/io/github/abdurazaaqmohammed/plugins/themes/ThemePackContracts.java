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
 * <p>The optional {@link #COLUMN_ICONS} JSON maps allowlisted {@link #ICON_KINDS}
 * (plus optional {@code _light}/{@code _dark} variants) to exact
 * {@code content://<same authority>/icons/<key>} PNG endpoints. A pack without
 * that column leaves all host icon defaults untouched. The optional
 * {@link #COLUMN_LAUNCHER_ICON} TEXT column selects a bundled host icon:
 * {@code default}, {@code crimson}, {@code emerald}, {@code amber},
 * {@code violet}, or {@code mono}. Unknown values use the default icon.
 *
 * <p>Contract versions: v1 rows are fully accepted (they simply have no
 * {@link #COLUMN_EDITOR_SYNTAX} / {@link #COLUMN_WALLPAPER_SCOPE} /
 * {@link #COLUMN_ICONS} columns); v2 rows add those optional columns. Unknown
 * columns are ignored by the host parser so future revisions stay wire-compatible.
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
    /** Parent path of every icon endpoint; the full URI is
     *  {@code content://<authority>/icons/<icon key>}. */
    public static final String PATH_ICONS = "icons";
    /** Reserved artwork endpoint for future hosts. This host selects bundled
     *  launcher icons using {@link #COLUMN_LAUNCHER_ICON} and does not read
     *  provider artwork from this endpoint. */
    public static final String PATH_LAUNCHER_ICON = "launcher_icon";

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
    /** v2: optional TEXT JSON map of allowlisted icon key to an exact
     *  {@code content://<same authority>/icons/<key>} PNG endpoint. */
    public static final String COLUMN_ICONS = "icons";
    /** v2: optional TEXT launcher icon selector, a short opaque ID the host
     *  stores and maps to its bundled launcher variants (hosts without
     *  bundled variants fall back to the default icon). The actual artwork
     *  endpoint {@link #PATH_LAUNCHER_ICON} is reserved for hosts that can
     *  consume provider artwork. Bounded by
     *  {@link #MAX_LAUNCHER_ICON_ID_CHARS}. */
    public static final String COLUMN_LAUNCHER_ICON = "launcher_icon";

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
    /** icons JSON payload cap (key -> endpoint URI map). */
    public static final int MAX_ICONS_JSON_CHARS = 32 * 1024;
    /** Per-icon PNG byte cap. */
    public static final long MAX_ICON_BYTES = 256L * 1024;
    /** Per-icon PNG dimension cap (each side). */
    public static final int MAX_ICON_DIMENSION_PX = 256;
    /** Per-icon PNG pixel cap. */
    public static final long MAX_ICON_PIXELS = 256L * 256L;
    /** Total icon bytes allowed for one imported snapshot. */
    public static final long MAX_ICONS_TOTAL_BYTES = 8L * 1024 * 1024;
    /** launcher_icon column cap: a short opaque selector ID, never image
     *  data. Artwork resolution goes through {@link #PATH_LAUNCHER_ICON}. */
    public static final int MAX_LAUNCHER_ICON_ID_CHARS = 64;
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

    /**
     * Base semantic icon kinds accepted in the {@link #COLUMN_ICONS} JSON.
     * Providers may ship any subset; a missing key always falls back to the
     * host's built-in icon. Each base kind may optionally be suffixed with
     * {@code _light} or {@code _dark} for adaptive variants; the host prefers
     * the variant matching the resolved theme and falls back to the base key.
     * Keys are case-sensitive and must match exactly.
     */
    public static final String[] ICON_KINDS = {
            "folder", "parent_folder", "apk", "image", "video", "audio",
            "arsc", "dex", "archive", "sticker", "pdf", "text",
            "java", "kotlin", "xml", "json", "python", "smali", "js",
            "elf", "disk", "database", "font", "binary", "proto", "document",
            "sheet", "slide", "script", "backup", "certificate", "file",
    };

    /** Suffix for a light-theme variant of a base icon key. */
    public static final String ICON_VARIANT_LIGHT = "_light";
    /** Suffix for a dark-theme variant of a base icon key. */
    public static final String ICON_VARIANT_DARK = "_dark";

    /** True when {@code raw} is a base {@link #ICON_KINDS} key or one of its
     *  {@code _light}/{@code _dark} variants (exact spelling). */
    public static boolean isIconKey(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        for (String base : ICON_KINDS) {
            if (base.equals(raw)) return true;
            if ((base + ICON_VARIANT_LIGHT).equals(raw)
                    || (base + ICON_VARIANT_DARK).equals(raw)) return true;
        }
        return false;
    }
}
