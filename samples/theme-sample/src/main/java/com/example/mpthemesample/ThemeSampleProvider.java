package com.example.mpthemesample;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * MP-Manager v2 theme provider sample.
 *
 * Permission-free: exported with no read permission, so the host queries it
 * directly. Fixed endpoints under {@link #AUTHORITY}:
 * <pre>
 *   /theme     one row: contract (INTEGER =2), id, name (TEXT) and optional
 *              accent, dark, scrim (INTEGER), colors, strings, editor_syntax
 *              (JSON TEXT), wallpaper_scope ("files"|"all"|"none" TEXT) and
 *              wallpaper/preview (TEXT, exact content URIs)
 *   /wallpaper wallpaper PNG (asset)
 *   /preview   preview PNG (asset)
 * </pre>
 * Accent/scrim are ARGB ints (from the documented #AARRGGBB values). Colors
 * use tokens from the host allowlist (Material 3 names plus app tokens);
 * editor_syntax maps the eight canonical syntax categories (TextMate scope
 * names are accepted too and mapped by the host) to ARGB colors; strings map
 * a host label key to a BCP47-locale -> text object. Unknown opens throw
 * FileNotFoundException.
 */
public class ThemeSampleProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.mpthemesample.theme";

    public static final Uri THEME_URI =
            Uri.parse("content://" + AUTHORITY + "/theme");
    public static final Uri WALLPAPER_URI =
            Uri.parse("content://" + AUTHORITY + "/wallpaper");
    public static final Uri PREVIEW_URI =
            Uri.parse("content://" + AUTHORITY + "/preview");

    /** Contract version served on the contract column. */
    public static final int CONTRACT_V1 = 1;
    /** Contract version adding editor_syntax + wallpaper_scope (served). */
    public static final int CONTRACT_V2 = 2;

    /** Theme payload; ARGB ints for the INTEGER columns. */
    public static final String THEME_ID = "sample.nova";
    public static final int ACCENT = 0xFF6A5AE0;   // #FF6A5AE0
    public static final int SCRIM = 0xB3000000;    // #B3000000
    public static final String SCRIM_HEX = "#B3000000";
    public static final int DARK = 1;
    public static final String COLOR_PRIMARY = "#FF6A5AE0";
    public static final String COLOR_SURFACE = "#FF191926";
    public static final String COLOR_ON_SURFACE = "#FFE6E6F0";
    public static final String COLOR_FOLDER_ICON_BG = "#FF2A2547";
    public static final String COLOR_LIST_HIGHLIGHT = "#FF3A3358";
    public static final String WALLPAPER_SCOPE = "files";

    /** Editor syntax colors (canonical categories, host-allowlisted). */
    public static final String SYNTAX_COMMENT = "#FF7A7A99";
    public static final String SYNTAX_KEYWORD = "#FF9D7CFF";
    public static final String SYNTAX_STRING = "#FF8FD18F";
    public static final String SYNTAX_NUMBER = "#FFFFB86C";
    public static final String SYNTAX_TYPE = "#FF6FC3DF";
    public static final String SYNTAX_FUNCTION = "#FF7DCFFF";
    public static final String SYNTAX_OPERATOR = "#FFFF7EDB";
    public static final String SYNTAX_VARIABLE = "#FFF8F8F2";
    /** Same value shipped as a TextMate scope alias to show host mapping. */
    public static final String SYNTAX_SCOPE_ENTITY_FUNCTION = "#FF7DCFFF";

    /** Remaining editor tokens (gutter, selection, highlights). */
    public static final String COLOR_EDITOR_GUTTER = "#FF151520";
    public static final String COLOR_EDITOR_LINE_NUMBER = "#FF5A5876";
    public static final String COLOR_EDITOR_SELECTION = "#FF3A3358";
    public static final String COLOR_EDITOR_CURRENT_LINE = "#FF23233A";
    public static final String COLOR_EDITOR_MATCH_HIGHLIGHT = "#FF4A3F6E";

    private static final int MATCH_THEME = 1;
    private static final int MATCH_WALLPAPER = 2;
    private static final int MATCH_PREVIEW = 3;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "theme", MATCH_THEME);
        MATCHER.addURI(AUTHORITY, "wallpaper", MATCH_WALLPAPER);
        MATCHER.addURI(AUTHORITY, "preview", MATCH_PREVIEW);
    }

    private static final String[] COLUMNS = {
            "contract", "id", "name", "accent", "dark",
            "wallpaper", "scrim", "colors", "strings",
            "editor_syntax", "wallpaper_scope", "preview"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (MATCHER.match(uri) != MATCH_THEME) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (projection == null) projection = COLUMNS;
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            row[i] = valueFor(projection[i]);
        }
        MatrixCursor cursor = new MatrixCursor(projection);
        cursor.addRow(row);
        return cursor;
    }

    private Object valueFor(String column) {
        switch (column) {
            case "contract":
                return CONTRACT_V2;
            case "id":
                return THEME_ID;
            case "name":
                return getContext().getString(R.string.theme_name);
            case "accent":
                return ACCENT;
            case "dark":
                return DARK;
            case "wallpaper":
                return WALLPAPER_URI.toString();
            case "scrim":
                return SCRIM;
            case "colors":
                return colorsJson();
            case "strings":
                return stringsJson();
            case "editor_syntax":
                return syntaxJson();
            case "wallpaper_scope":
                return WALLPAPER_SCOPE;
            case "preview":
                return PREVIEW_URI.toString();
            default:
                return null;
        }
    }

    private static String colorsJson() {
        try {
            JSONObject colors = new JSONObject();
            colors.put("colorPrimary", COLOR_PRIMARY);
            colors.put("colorSurface", COLOR_SURFACE);
            colors.put("colorOnSurface", COLOR_ON_SURFACE);
            // app tokens (host allowlist in ThemePackContracts)
            colors.put("folder_icon_bg", COLOR_FOLDER_ICON_BG);
            colors.put("list_highlight", COLOR_LIST_HIGHLIGHT);
            colors.put("wallpaper_scrim", SCRIM_HEX);
            colors.put("editor_background", COLOR_SURFACE);
            colors.put("editor_text", COLOR_ON_SURFACE);
            colors.put("editor_gutter", COLOR_EDITOR_GUTTER);
            colors.put("editor_line_number", COLOR_EDITOR_LINE_NUMBER);
            colors.put("editor_line_number_current", COLOR_PRIMARY);
            colors.put("editor_selection", COLOR_EDITOR_SELECTION);
            colors.put("editor_current_line", COLOR_EDITOR_CURRENT_LINE);
            colors.put("editor_match_highlight", COLOR_EDITOR_MATCH_HIGHLIGHT);
            return colors.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * v2 editor_syntax column: canonical categories plus one TextMate scope
     * alias ({@code entity.name.function}) the host maps onto the same
     * category, to demonstrate both accepted spellings.
     */
    private static String syntaxJson() {
        try {
            JSONObject syntax = new JSONObject();
            syntax.put("comment", SYNTAX_COMMENT);
            syntax.put("keyword", SYNTAX_KEYWORD);
            syntax.put("string", SYNTAX_STRING);
            syntax.put("number", SYNTAX_NUMBER);
            syntax.put("type", SYNTAX_TYPE);
            syntax.put("function", SYNTAX_FUNCTION);
            syntax.put("operator", SYNTAX_OPERATOR);
            syntax.put("variable", SYNTAX_VARIABLE);
            syntax.put("entity.name.function", SYNTAX_SCOPE_ENTITY_FUNCTION);
            return syntax.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Host label keys, each as {en -> ..., zh-CN -> ...} sourced from
     * resources (values/ and values-zh-rCN/).
     */
    private String stringsJson() {
        try {
            JSONObject strings = new JSONObject();
            putLabel(strings, "tab.files", R.string.label_tab_files);
            putLabel(strings, "tab.root", R.string.label_tab_root);
            putLabel(strings, "tab.general", R.string.label_tab_general);
            putLabel(strings, "tab.behavior", R.string.label_tab_behavior);
            putLabel(strings, "tab.other", R.string.label_tab_other);
            putLabel(strings, "settings.theme", R.string.label_settings_theme);
            return strings.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void putLabel(JSONObject strings, String key, int resId) {
        try {
            JSONObject locales = new JSONObject();
            locales.put("en", getContext().getString(resId));
            locales.put("zh-CN", localizedString(resId, "zh-CN"));
            strings.put(key, locales);
        } catch (Exception ignored) {
        }
    }

    /** Resource text for a BCP47 locale via a configuration context. */
    private String localizedString(int resId, String tag) {
        Context context = getContext();
        Configuration config = new Configuration();
        config.setTo(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(tag));
        return context.createConfigurationContext(config).getString(resId);
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case MATCH_THEME:
                return "vnd.android.cursor.item/theme";
            case MATCH_WALLPAPER:
            case MATCH_PREVIEW:
                return "image/png";
            default:
                return null;
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider: " + uri);
        }
        String asset;
        switch (MATCHER.match(uri)) {
            case MATCH_WALLPAPER:
                asset = "wallpaper.png";
                break;
            case MATCH_PREVIEW:
                asset = "preview.png";
                break;
            default:
                throw new FileNotFoundException("Unsupported URI: " + uri);
        }
        try {
            return getContext().getAssets().openFd(asset);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            FileNotFoundException wrapped = new FileNotFoundException(
                    "Cannot open " + uri);
            wrapped.initCause(e);
            throw wrapped;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        AssetFileDescriptor afd = openAssetFile(uri, mode);
        return afd == null ? null : afd.getParcelFileDescriptor();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only theme provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }
}
