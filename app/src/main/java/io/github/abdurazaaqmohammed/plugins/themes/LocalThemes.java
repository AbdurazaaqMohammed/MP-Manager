package io.github.abdurazaaqmohammed.plugins.themes;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-app ("My themes") frontend over the shared ThemePacks local-theme
 * backend. Local themes are ordinary snapshots stored under the reserved
 * {@link ThemePacks#LOCAL_PKG_PREFIX} namespace; every write goes through
 * the ThemePacks local APIs ({@code commitLocal}/{@code activateLocal}/
 * {@code deleteLocal}) so there is exactly one activation pointer and one
 * store — this class owns no prefs, no files and no activation state of
 * its own.
 *
 * <p>{@link LocalTheme} is the read-only view used by lists and previews;
 * {@link Draft} is the editable model the {@code ThemeEditorDialog}
 * mutates. Staged images are bounded and validated by the backend
 * ({@code ThemePacks.stageValidatedLocalImage}/{@code stageValidatedIconBytes}).
 * Persistence/validation-adjacent helpers (export/import document parsing,
 * allowlist filtering, caps) are pure java.io + Gson so they are unit
 * testable on the JVM; actual commits need an Android Context and are
 * covered by instrumented tests. No method throws: failures come back as
 * {@code null}/{@code false}/empty.
 */
public final class LocalThemes {

    /** Export/import document format marker. Additive format: v1 documents
     *  carried at most wallpaper/preview/folder/file images; the same
     *  marker now covers documents embedding ANY allowlisted icon key
     *  (incl. {@code _light}/{@code _dark} variants). Old documents still
     *  import unchanged; new keys are simply additional members of the
     *  {@code images} object. */
    static final int EXPORT_FORMAT = 1;
    static final String EXPORT_TYPE = "mp-manager-theme";
    /** Portable document extension (SAF mime is application/json). */
    public static final String EXPORT_EXTENSION = ".mtheme.json";
    /**
     * Hard cap for one imported/exported theme document (JSON + base64).
     * Sized for the worst legal document: an 8MB wallpaper plus the 8MB
     * total icon budget plus a preview, all base64-inflated (~1.34x),
     * with headroom for the JSON skeleton.
     */
    public static final long MAX_DOCUMENT_BYTES = 32L * 1024 * 1024;
    /**
     * Max number of {@code images} members an import accepts: wallpaper +
     * preview + every base icon kind plus its two variants.
     */
    static final int MAX_EMBEDDED_IMAGES = 2 + 3 * ThemePackContracts.ICON_KINDS.length;

    /** Local theme ids the editor manages: {@code local.<32 hex>}. */
    static final String LOCAL_ID_PATTERN = "local\\.[0-9a-f]{8,64}";
    /** Shared store root directory name under filesDir. */
    private static final String ThemePackStoreRoot = "theme_packs";

    private LocalThemes() {
    }

    // ---------- ids ----------

    /** New local theme key (dash-free UUID hex; ThemePacks prefixes it). */
    static String newKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Full snapshot id for a raw key. */
    static String idOf(String key) {
        return key == null ? null : ThemePacks.LOCAL_PKG_PREFIX + key;
    }

    /** Raw key of a full id, or null when the id is not a local one. */
    static String keyOf(String id) {
        if (id == null || !id.startsWith(ThemePacks.LOCAL_PKG_PREFIX)) return null;
        String key = id.substring(ThemePacks.LOCAL_PKG_PREFIX.length());
        return key.matches("[0-9a-f]{8,64}") ? key : null;
    }

    // ---------- image roles ----------

    /** Which image of a theme a draft operation targets. One icon slot per
     *  {@link ThemePackContracts#ICON_KINDS} base kind (plus the non-icon
     *  wallpaper/preview slots); {@code _light}/{@code _dark} variant keys
     *  resolve through {@link #iconSlot} without their own constants. */
    public enum ImageSlot {
        WALLPAPER(null, ThemePackContracts.MAX_WALLPAPER_BYTES,
                ThemePackContracts.MAX_IMAGE_DIMENSION_PX,
                ThemePackContracts.MAX_IMAGE_PIXELS),
        PREVIEW(null, ThemePackContracts.MAX_PREVIEW_BYTES,
                ThemePackContracts.MAX_PREVIEW_DIMENSION_PX,
                ThemePackContracts.MAX_PREVIEW_PIXELS),
        FOLDER_ICON("folder", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        PARENT_FOLDER_ICON("parent_folder", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        APK_ICON("apk", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        IMAGE_ICON("image", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        VIDEO_ICON("video", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        AUDIO_ICON("audio", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        ARSC_ICON("arsc", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        DEX_ICON("dex", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        ARCHIVE_ICON("archive", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        STICKER_ICON("sticker", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        PDF_ICON("pdf", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        TEXT_ICON("text", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        JAVA_ICON("java", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        KOTLIN_ICON("kotlin", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        XML_ICON("xml", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        JSON_ICON("json", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        PYTHON_ICON("python", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        SMALI_ICON("smali", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        JS_ICON("js", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        ELF_ICON("elf", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        DISK_ICON("disk", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        DATABASE_ICON("database", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        FONT_ICON("font", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        BINARY_ICON("binary", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        PROTO_ICON("proto", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        DOCUMENT_ICON("document", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        SHEET_ICON("sheet", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        SLIDE_ICON("slide", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        SCRIPT_ICON("script", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        BACKUP_ICON("backup", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        CERTIFICATE_ICON("certificate", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS),
        FILE_ICON("file", ThemePackContracts.MAX_ICON_BYTES,
                ThemePackContracts.MAX_ICON_DIMENSION_PX,
                ThemePackContracts.MAX_ICON_PIXELS);

        /** Contract icon key for icon slots; null for wallpaper/preview. */
        public final String iconKey;
        public final long maxBytes;
        public final int maxDimension;
        public final long maxPixels;

        ImageSlot(String iconKey, long maxBytes, int maxDimension, long maxPixels) {
            this.iconKey = iconKey;
            this.maxBytes = maxBytes;
            this.maxDimension = maxDimension;
            this.maxPixels = maxPixels;
        }

        /** Key -> slot lookup over the base kinds (shared immutable map). */
        private static final Map<String, ImageSlot> ICON_SLOTS;
        static {
            Map<String, ImageSlot> map = new LinkedHashMap<>();
            for (ImageSlot slot : values()) {
                if (slot.iconKey != null) map.put(slot.iconKey, slot);
            }
            ICON_SLOTS = Collections.unmodifiableMap(map);
        }

        /**
         * Slot for a contract icon key: a base {@link ThemePackContracts#ICON_KINDS}
         * kind resolves to its own slot; a {@code _light}/{@code _dark}
         * variant resolves to its BASE kind's slot (variants share the icon
         * caps — staging/clearing always use the exact KEY, not the slot).
         * Null for anything else (unknown keys, wallpaper/preview names).
         */
        public static ImageSlot iconSlot(String iconKey) {
            if (iconKey == null) return null;
            if (ICON_SLOTS.containsKey(iconKey)) return ICON_SLOTS.get(iconKey);
            String base = null;
            if (iconKey.endsWith(ThemePackContracts.ICON_VARIANT_LIGHT)) {
                base = iconKey.substring(0,
                        iconKey.length() - ThemePackContracts.ICON_VARIANT_LIGHT.length());
            } else if (iconKey.endsWith(ThemePackContracts.ICON_VARIANT_DARK)) {
                base = iconKey.substring(0,
                        iconKey.length() - ThemePackContracts.ICON_VARIANT_DARK.length());
            }
            return base == null ? null : ICON_SLOTS.get(base);
        }

        /** True when {@code raw} is exactly a base icon kind (no variant). */
        public static boolean isBaseIconKey(String raw) {
            return raw != null && ICON_SLOTS.containsKey(raw);
        }
    }

    // ---------- read-only model ----------

    /** Immutable view of one saved local theme. */
    public static final class LocalTheme {
        /** Full id ({@code local.<hex>}). */
        public final String id;
        public final String name;
        public final Boolean dark;
        public final Integer accent;
        public final Integer scrim;
        /** Unmodifiable canonical token -> ARGB. */
        public final Map<String, Integer> colors;
        /** Unmodifiable canonical category -> ARGB. */
        public final Map<String, Integer> editorSyntax;
        /** files | all | none; never null. */
        public final String wallpaperScope;
        /** Resolved private-store files; null when absent. */
        public final File wallpaper;
        public final File preview;
        /** Contract icon key -> resolved PNG file (unmodifiable). */
        public final Map<String, File> iconFiles;
        /** Launcher selector id stored on the snapshot; null = none. */
        public final String launcherId;
        /** Store revision. */
        public final long revision;

        LocalTheme(String id, String name, Boolean dark, Integer accent,
                   Integer scrim, Map<String, Integer> colors,
                   Map<String, Integer> editorSyntax, String wallpaperScope,
                   File wallpaper, File preview, Map<String, File> iconFiles,
                   String launcherId, long revision) {
            this.id = id;
            this.name = name;
            this.dark = dark;
            this.accent = accent;
            this.scrim = scrim;
            this.colors = colors;
            this.editorSyntax = editorSyntax;
            this.wallpaperScope = wallpaperScope;
            this.wallpaper = wallpaper;
            this.preview = preview;
            this.iconFiles = iconFiles;
            this.launcherId = launcherId;
            this.revision = revision;
        }
    }

    /** Reads one saved local theme, or null when missing/corrupt. */
    public static LocalTheme read(Context context, String id) {
        try {
            String key = keyOf(id);
            if (key == null) return null;
            ThemePackStore.StoredSnapshot stored = ThemePacks.readLocal(context, key);
            return fromStored(context, key, stored);
        } catch (Exception e) {
            return null;
        }
    }

    /** All saved local themes, sorted by display name. */
    public static List<LocalTheme> list(Context context) {
        List<LocalTheme> out = new ArrayList<>();
        try {
            if (context != null) {
                sweepAbandonedStaged(context.getApplicationContext().getFilesDir());
            }
            for (String key : ThemePacks.localKeys(context)) {
                LocalTheme theme = read(context, idOf(key));
                if (theme != null) out.add(theme);
            }
        } catch (Exception ignored) {
        }
        out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.name == null ? a.id : a.name,
                b.name == null ? b.id : b.name));
        return out;
    }

    /** Backend-stored snapshot -> editor view (read-only file resolution). */
    private static LocalTheme fromStored(Context context, String key,
                                         ThemePackStore.StoredSnapshot stored) {
        if (stored == null || stored.id == null) return null;
        Map<String, Integer> colors = new LinkedHashMap<>();
        if (stored.colors != null) {
            for (Map.Entry<String, Integer> e : stored.colors.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (ThemePackParser.canonicalColorToken(e.getKey()) != null) {
                    colors.put(e.getKey(), e.getValue());
                }
            }
        }
        Map<String, Integer> syntax = new LinkedHashMap<>();
        if (stored.editorSyntax != null) {
            for (Map.Entry<String, Integer> e : stored.editorSyntax.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (ThemePackParser.canonicalSyntaxKey(e.getKey()) != null) {
                    syntax.put(e.getKey(), e.getValue());
                }
            }
        }
        String scope = ThemePackContracts.isWallpaperScope(stored.wallpaperScope)
                ? stored.wallpaperScope : ThemePackContracts.WALLPAPER_SCOPE_FILES;
        File filesDir = context.getFilesDir();
        File wallpaper = ThemePackStore.wallpaperFile(filesDir, idOf(key),
                stored.wallpaperName);
        if (wallpaper != null && !wallpaper.isFile()) wallpaper = null;
        File preview = ThemePackStore.previewFile(filesDir, idOf(key),
                stored.previewName);
        if (preview != null && !preview.isFile()) preview = null;
        Map<String, File> iconFiles = new LinkedHashMap<>();
        if (stored.iconNames != null) {
            for (Map.Entry<String, String> e : stored.iconNames.entrySet()) {
                File icon = ThemePackStore.iconFile(filesDir, idOf(key),
                        e.getKey(), e.getValue());
                if (icon != null && icon.isFile()) iconFiles.put(e.getKey(), icon);
            }
        }
        return new LocalTheme(idOf(key), stored.name, stored.dark, stored.accent,
                stored.scrim, Collections.unmodifiableMap(colors),
                Collections.unmodifiableMap(syntax), scope,
                wallpaper, preview, Collections.unmodifiableMap(iconFiles),
                validLauncherId(stored.launcherIcon),
                stored.revision == null ? 0 : stored.revision);
    }

    private static String validLauncherId(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty() || v.length() > ThemePackContracts.MAX_LAUNCHER_ICON_ID_CHARS) {
            return null;
        }
        // Opaque bounded selector: printable, no control characters.
        for (int i = 0; i < v.length(); i++) {
            if (Character.isISOControl(v.charAt(i))) return null;
        }
        return v;
    }

    /**
     * Resolves a stored image NAME (wallpaper-/preview-/icon-<key>-<n>) to
     * its private-store file under any local theme directory — read-only
     * preview support for drafts (names only, never writes).
     */
    public static File resolveStoredImage(Context context, String name) {
        if (name == null || context == null) return null;
        if (!name.matches("(wallpaper|preview|icon-[a-z_]+)-[0-9]{1,19}")) return null;
        try {
            File root = new File(context.getFilesDir(), ThemePackStoreRoot);
            File[] dirs = root.listFiles();
            if (dirs == null) return null;
            for (File dir : dirs) {
                if (!dir.isDirectory()
                        || !dir.getName().startsWith(ThemePacks.LOCAL_PKG_PREFIX)) {
                    continue;
                }
                File candidate = new File(dir, name);
                if (candidate.isFile()) return candidate;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------- draft ----------

    /**
     * Editable draft. Start from {@link #newDraft}, {@link #draftFrom} or
     * {@link #draftFromSnapshot}, stage images with
     * {@link #stageImage}, then persist via {@link #save} /
     * {@link #saveAndApply}.
     */
    public static final class Draft {
        /** Raw local key; null = create new (assigned on save). */
        public String key;
        public String name = "";
        public boolean dark;
        /** ARGB or null = unset (host falls back). */
        public Integer accent;
        public Integer scrim;
        /** Raw token -> ARGB; unknown tokens are dropped at save time. */
        public final Map<String, Integer> colors = new LinkedHashMap<>();
        /** Raw category -> ARGB; unknown keys dropped at save time. */
        public final Map<String, Integer> editorSyntax = new LinkedHashMap<>();
        /** files | all | none; normalized on save. */
        public String wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_FILES;
        /** Launcher selector id; null = none. */
        public String launcherId;

        // Newly staged replacement files (validated backend-staged files).
        File stagedWallpaper;
        File stagedPreview;
        final Map<String, File> stagedIcons = new LinkedHashMap<>();

        // Retained stored names for untouched images (commit keeps them).
        String retainedWallpaper;
        String retainedPreview;
        final Map<String, String> retainedIcons = new LinkedHashMap<>();

        // Marks images the user removed from a previously saved theme.
        boolean wallpaperCleared;
        boolean previewCleared;
        final Map<String, Boolean> iconsCleared = new LinkedHashMap<>();

        public boolean hasWallpaper() {
            return stagedWallpaper != null || (!wallpaperCleared && retainedWallpaper != null);
        }

        public boolean hasPreview() {
            return stagedPreview != null || (!previewCleared && retainedPreview != null);
        }

        public boolean hasIcon(String contractKey) {
            if (stagedIcons.containsKey(contractKey)) return true;
            if (Boolean.TRUE.equals(iconsCleared.get(contractKey))) return false;
            return retainedIcons.containsKey(contractKey);
        }

        public boolean hasIcon(ImageSlot slot) {
            return slot.iconKey != null && hasIcon(slot.iconKey);
        }

        /** True when any change needs saving (approximate; dialogs use it
         *  to warn before discard). */
        public boolean isDirty() {
            return stagedWallpaper != null || stagedPreview != null
                    || !stagedIcons.isEmpty() || wallpaperCleared
                    || previewCleared || !iconsCleared.isEmpty();
        }

        /** Preview surface: staged wallpaper file, or null. */
        public File stagedWallpaperFile() {
            return stagedWallpaper;
        }

        /** Preview surface: staged icon file for a contract key, or null. */
        public File stagedIconFile(String contractKey) {
            return contractKey == null ? null : stagedIcons.get(contractKey);
        }

        /** Preview surface: retained stored wallpaper name, or null. */
        public String retainedWallpaperName() {
            return wallpaperCleared ? null : retainedWallpaper;
        }

        /** Preview surface: retained stored icon name, or null. */
        public String retainedIconName(String contractKey) {
            if (contractKey == null) return null;
            if (Boolean.TRUE.equals(iconsCleared.get(contractKey))) return null;
            return retainedIcons.get(contractKey);
        }

        /** Icon keys with newly staged (validated) files; read-only. */
        public java.util.Set<String> stagedIconKeys() {
            return Collections.unmodifiableSet(stagedIcons.keySet());
        }

        /** Icon keys retained from the saved snapshot; read-only. */
        public java.util.Set<String> retainedIconKeys() {
            return Collections.unmodifiableSet(retainedIcons.keySet());
        }
    }

    /** Blank draft from one of the starter presets ({@code dark} flag). */
    public static Draft newDraft(String name, boolean dark) {
        Draft draft = new Draft();
        draft.key = null;
        draft.name = name == null ? "" : name;
        draft.dark = dark;
        draft.colors.putAll(presetColors(dark));
        return draft;
    }

    /** Editable copy of a saved local theme (same key: images are retained). */
    public static Draft draftFrom(LocalTheme theme) {
        Draft draft = new Draft();
        if (theme == null) return newDraft("", false);
        draft.key = keyOf(theme.id);
        draft.name = theme.name == null ? "" : theme.name;
        draft.dark = theme.dark != null && theme.dark;
        draft.accent = theme.accent;
        draft.scrim = theme.scrim;
        draft.colors.putAll(theme.colors);
        draft.editorSyntax.putAll(theme.editorSyntax);
        draft.wallpaperScope = theme.wallpaperScope;
        draft.launcherId = theme.launcherId;
        draft.retainedWallpaper = fileNameOf(theme.wallpaper);
        draft.retainedPreview = fileNameOf(theme.preview);
        for (Map.Entry<String, File> e : theme.iconFiles.entrySet()) {
            draft.retainedIcons.put(e.getKey(), fileNameOf(e.getValue()));
        }
        return draft;
    }

    /**
     * Overload that pulls retained image names from the store (use when
     * only the id is known).
     */
    public static Draft draftFrom(Context context, String id) {
        LocalTheme theme = read(context, id);
        Draft draft = draftFrom(theme);
        if (draft.key == null) {
            draft.key = keyOf(id);
            draft.name = "";
            draft.dark = false;
        }
        if (theme == null) {
            ThemePackStore.StoredSnapshot stored;
            try {
                String key = keyOf(id);
                stored = key == null ? null : ThemePacks.readLocal(context, key);
            } catch (Exception e) {
                stored = null;
            }
            if (stored != null) {
                draft.retainedWallpaper = stored.wallpaperName;
                draft.retainedPreview = stored.previewName;
                if (stored.iconNames != null) draft.retainedIcons.putAll(stored.iconNames);
            }
        }
        return draft;
    }

    /**
     * Age after which an unreferenced {@code staged-} temp file in a local
     * pack dir is considered abandoned and removed.
     */
    static final long STAGED_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    /**
     * Removes {@code staged-} temp files older than {@link #STAGED_MAX_AGE_MS}
     * from every {@code local.*} pack dir. An editor dialog torn down without
     * its cancel/discard path (rotation, process death while the SAF picker
     * was up) leaves its staged images behind; the commit sweep only covers
     * versioned {@code wallpaper-/preview-/icon-} names, so without this the
     * orphaned staged files would accumulate forever. Pure janitor: never
     * touches stored versioned assets and never throws. Called from
     * {@link #list} on a background thread.
     */
    static void sweepAbandonedStaged(File filesDir) {
        try {
            File root = new File(filesDir, ThemePackStoreRoot);
            File[] dirs = root.listFiles();
            if (dirs == null) return;
            long cutoff = System.currentTimeMillis() - STAGED_MAX_AGE_MS;
            for (File dir : dirs) {
                if (!dir.isDirectory()
                        || !dir.getName().startsWith(ThemePacks.LOCAL_PKG_PREFIX)) {
                    continue;
                }
                File[] children = dir.listFiles();
                if (children == null) continue;
                for (File child : children) {
                    if (!child.isFile()) continue;
                    String name = child.getName();
                    if (name.startsWith("staged-") && child.lastModified() < cutoff) {
                        child.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Draft seeded from a provider pack snapshot (or the active one). */
    public static Draft draftFromSnapshot(Context context,
                                          ThemePacks.Snapshot snapshot) {
        Draft draft = new Draft();
        if (snapshot == null) {
            snapshot = ThemePacks.active(context);
        }
        if (snapshot == null) return newDraft("", false);
        draft.key = null;
        draft.name = snapshot.name == null ? "" : snapshot.name;
        draft.dark = snapshot.dark != null && snapshot.dark;
        draft.accent = snapshot.accent;
        draft.scrim = snapshot.scrim;
        if (snapshot.colors != null) {
            for (Map.Entry<String, Integer> e : snapshot.colors.entrySet()) {
                if (ThemePackParser.canonicalColorToken(e.getKey()) != null) {
                    draft.colors.put(e.getKey(), e.getValue());
                }
            }
        }
        if (snapshot.editorSyntax != null) {
            for (Map.Entry<String, Integer> e : snapshot.editorSyntax.entrySet()) {
                if (ThemePackParser.canonicalSyntaxKey(e.getKey()) != null) {
                    draft.editorSyntax.put(e.getKey(), e.getValue());
                }
            }
        }
        draft.wallpaperScope = snapshot.wallpaperScope;
        draft.launcherId = snapshot.launcherIcon;
        copyImages(context, draft, snapshot.wallpaper, snapshot.preview, snapshot.iconFiles);
        return draft;
    }

    /** Copies a saved theme into a new draft, including its private artwork. */
    public static Draft duplicate(Context context, LocalTheme theme) {
        Draft draft = draftFrom(theme);
        draft.key = null;
        draft.retainedWallpaper = null;
        draft.retainedPreview = null;
        draft.retainedIcons.clear();
        if (theme != null) {
            copyImages(context, draft, theme.wallpaper, theme.preview, theme.iconFiles);
        }
        return draft;
    }

    private static void copyImages(Context context, Draft draft, File wallpaper,
                                   File preview, Map<String, File> icons) {
        for (ImageSlot slot : new ImageSlot[]{ImageSlot.WALLPAPER, ImageSlot.PREVIEW}) {
            File source = slot == ImageSlot.WALLPAPER ? wallpaper : preview;
            if (source == null) continue;
            try (InputStream in = new FileInputStream(source)) {
                stageImageStream(context, draft, slot, in);
            } catch (Exception ignored) {
            }
        }
        if (icons == null) return;
        for (Map.Entry<String, File> entry : icons.entrySet()) {
            if (!ThemePackContracts.isIconKey(entry.getKey()) || entry.getValue() == null) continue;
            try (InputStream in = new FileInputStream(entry.getValue())) {
                stageIconStream(context, draft, entry.getKey(), in);
            } catch (Exception ignored) {
            }
        }
    }

    private static String fileNameOf(File file) {
        return file == null ? null : file.getName();
    }

    /** Light/dark starter palette (Material baseline, host tokens only). */
    static Map<String, Integer> presetColors(boolean dark) {
        Map<String, Integer> c = new LinkedHashMap<>();
        if (dark) {
            c.put("colorPrimary", 0xFFB69DFF);
            c.put("colorOnPrimary", 0xFF1B1044);
            c.put("colorPrimaryContainer", 0xFF32275B);
            c.put("colorOnPrimaryContainer", 0xFFE7DEFF);
            c.put("colorSecondary", 0xFFCBC2DB);
            c.put("colorOnSecondary", 0xFF332D41);
            c.put("colorSecondaryContainer", 0xFF4A4458);
            c.put("colorOnSecondaryContainer", 0xFFE8DEF8);
            c.put("colorTertiary", 0xFFE8BDD9);
            c.put("colorOnTertiary", 0xFF342941);
            c.put("colorTertiaryContainer", 0xFF4B3F58);
            c.put("colorOnTertiaryContainer", 0xFFFDD8FA);
            c.put("colorSurface", 0xFF1C1B1F);
            c.put("colorOnSurface", 0xFFE6E1E5);
            c.put("colorSurfaceVariant", 0xFF49454F);
            c.put("colorOnSurfaceVariant", 0xFFCAC4D0);
            c.put("colorOutline", 0xFF938F99);
            c.put("colorSurfaceContainerLowest", 0xFF0F0E13);
            c.put("colorSurfaceContainerLow", 0xFF1C1B1F);
            c.put("colorSurfaceContainer", 0xFF201F23);
            c.put("colorSurfaceContainerHigh", 0xFF2B2930);
            c.put("colorSurfaceContainerHighest", 0xFF36343B);
            c.put("nav_bar", 0xFF1C1B1F);
            c.put("status_bar", 0xFF1C1B1F);
            c.put("list_highlight", 0x1428627E);
            c.put("folder_icon_bg", 0x33FFD8A8);
            c.put("file_icon_bg", 0x33B69DFF);
        } else {
            c.put("colorPrimary", 0xFF6750A4);
            c.put("colorOnPrimary", 0xFFFFFFFF);
            c.put("colorPrimaryContainer", 0xFFEADDFF);
            c.put("colorOnPrimaryContainer", 0xFF21005D);
            c.put("colorSecondary", 0xFF625B71);
            c.put("colorOnSecondary", 0xFFFFFFFF);
            c.put("colorSecondaryContainer", 0xFFE8DEF8);
            c.put("colorOnSecondaryContainer", 0xFF1D192B);
            c.put("colorTertiary", 0xFF7D5260);
            c.put("colorOnTertiary", 0xFFFFFFFF);
            c.put("colorTertiaryContainer", 0xFFFFD8E4);
            c.put("colorOnTertiaryContainer", 0xFF31111D);
            c.put("colorSurface", 0xFFFEF7FF);
            c.put("colorOnSurface", 0xFF1D1B20);
            c.put("colorSurfaceVariant", 0xFFE7E0EC);
            c.put("colorOnSurfaceVariant", 0xFF49454F);
            c.put("colorOutline", 0xFF79747E);
            c.put("colorSurfaceContainerLowest", 0xFFFFFFFF);
            c.put("colorSurfaceContainerLow", 0xFFF7F2FA);
            c.put("colorSurfaceContainer", 0xFFF3EDF7);
            c.put("colorSurfaceContainerHigh", 0xFFECE6F0);
            c.put("colorSurfaceContainerHighest", 0xFFE6E0E9);
            c.put("nav_bar", 0xFFF7F2FA);
            c.put("status_bar", 0xFFF7F2FA);
            c.put("list_highlight", 0x1428627E);
            c.put("folder_icon_bg", 0x33FFB74D);
            c.put("file_icon_bg", 0x336750A4);
        }
        return c;
    }

    // ---------- image staging (all validation in the backend) ----------

    /**
     * Copies a SAF image into the local pack dir as a backend-staged,
     * validated file for {@code slot}. Returns false (nothing kept) when
     * the source cannot be read, is not a supported image, or is over the
     * slot's caps.
     */
    public static boolean stageImage(Context context, Draft draft,
                                     ImageSlot slot, Uri uri) {
        if (context == null || draft == null || slot == null || uri == null) {
            return false;
        }
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return stageImageStream(context, draft, slot, in);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Same as {@link #stageImage} but for an exact contract ICON key (a base
     * kind or its {@code _light}/{@code _dark} variant): the draft stages
     * the file under that exact key, so variants never collapse onto the
     * base kind. Icon caps are those of the key's base slot.
     */
    public static boolean stageIcon(Context context, Draft draft,
                                    String iconKey, Uri uri) {
        if (context == null || draft == null || uri == null
                || !ThemePackContracts.isIconKey(iconKey)) {
            return false;
        }
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return stageIconStream(context, draft, iconKey, in);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Same as {@link #stageImage} from a raw stream (import path); the
     * stream is consumed and closed by the backend staging helper. Icon
     * slots delegate to {@link #stageIconStream} with the slot's base key.
     */
    static boolean stageImageStream(Context context, Draft draft,
                                    ImageSlot slot, InputStream in) {
        if (context == null || draft == null || slot == null || in == null) {
            return false;
        }
        if (slot.iconKey != null) {
            return stageIconStream(context, draft, slot.iconKey, in);
        }
        try {
            String key = draft.key == null ? newKey() : draft.key;
            if (!key.matches("[0-9a-f]{8,64}")) return false;
            File staged = ThemePacks.stageValidatedLocalImage(context, key, in,
                    slot.maxBytes, slot.maxDimension, slot.maxPixels);
            if (staged == null) return false;
            draft.key = key;
            if (slot == ImageSlot.WALLPAPER) {
                deleteQuiet(draft.stagedWallpaper);
                draft.stagedWallpaper = staged;
            } else if (slot == ImageSlot.PREVIEW) {
                deleteQuiet(draft.stagedPreview);
                draft.stagedPreview = staged;
            } else {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Stages one icon PNG under an EXACT contract key (base kind or
     * {@code _light}/{@code _dark} variant); the stream is consumed and
     * closed by the backend staging helper. Replaces any staged file for
     * the same key and un-marks a pending clear for it.
     */
    static boolean stageIconStream(Context context, Draft draft,
                                   String iconKey, InputStream in) {
        if (context == null || draft == null || in == null
                || !ThemePackContracts.isIconKey(iconKey)) {
            return false;
        }
        ImageSlot caps = ImageSlot.iconSlot(iconKey);
        if (caps == null) return false;
        try {
            String key = draft.key == null ? newKey() : draft.key;
            if (!key.matches("[0-9a-f]{8,64}")) return false;
            File staged = ThemePacks.stageValidatedIconBytes(context, key, in);
            if (staged == null) return false;
            draft.key = key;
            applyStagedIcon(draft, iconKey, staged);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Pure draft mutation shared by every icon staging path: records a
     * backend-staged file under its EXACT contract key (variants such as
     * {@code folder_dark} keep their own entry), replaces any previous
     * staged file for that key and un-clears a prior removal. A keyless
     * draft gets its local key assigned here (the same key every staging
     * path would use), so staged icons always share one pack dir. Unknown
     * keys never land in the draft.
     */
    static void applyStagedIcon(Draft draft, String iconKey, File staged) {
        if (draft == null || iconKey == null || staged == null
                || !ThemePackContracts.isIconKey(iconKey)) {
            return;
        }
        if (draft.key == null) draft.key = newKey();
        deleteQuiet(draft.stagedIcons.get(iconKey));
        draft.stagedIcons.put(iconKey, staged);
        draft.iconsCleared.remove(iconKey);
    }

    /**
     * Marks a previously stored image as removed (saved themes only); icon
     * slots delegate to {@link #clearIcon} with the slot's base key.
     */
    public static void clearImage(Draft draft, ImageSlot slot) {
        if (draft == null || slot == null) return;
        if (slot == ImageSlot.WALLPAPER) {
            deleteQuiet(draft.stagedWallpaper);
            draft.stagedWallpaper = null;
            draft.retainedWallpaper = null;
            draft.wallpaperCleared = true;
        } else if (slot == ImageSlot.PREVIEW) {
            deleteQuiet(draft.stagedPreview);
            draft.stagedPreview = null;
            draft.retainedPreview = null;
            draft.previewCleared = true;
        } else if (slot.iconKey != null) {
            clearIcon(draft, slot.iconKey);
        }
    }

    /**
     * Marks the icon stored under the EXACT {@code iconKey} (base kind or
     * {@code _light}/{@code _dark} variant) as removed: staged and retained
     * copies are dropped and the key is tombstoned so a previously saved
     * theme loses exactly that artwork on the next save. Other keys —
     * including sibling variants — are untouched; unknown keys are ignored.
     */
    public static void clearIcon(Draft draft, String iconKey) {
        if (draft == null || !ThemePackContracts.isIconKey(iconKey)) return;
        deleteQuiet(draft.stagedIcons.get(iconKey));
        draft.stagedIcons.remove(iconKey);
        draft.retainedIcons.remove(iconKey);
        draft.iconsCleared.put(iconKey, Boolean.TRUE);
    }

    private static void deleteQuiet(File file) {
        if (file != null) file.delete();
    }

    /** Drops every staged file without touching stored state (cancel path). */
    public static void discard(Draft draft) {
        if (draft == null) return;
        deleteQuiet(draft.stagedWallpaper);
        deleteQuiet(draft.stagedPreview);
        for (File f : draft.stagedIcons.values()) deleteQuiet(f);
        draft.stagedWallpaper = null;
        draft.stagedPreview = null;
        draft.stagedIcons.clear();
    }

    // ---------- persistence ----------

    /**
     * Builds the store snapshot from the draft. Pure; both save paths use
     * it, so what the editor shows is exactly what persists.
     */
    static ThemePackStore.StoredSnapshot buildStored(Draft draft, String key,
                                                     ThemePackStore.StoredSnapshot previous) {
        ThemePackStore.StoredSnapshot stored = new ThemePackStore.StoredSnapshot();
        stored.id = key;
        String name = ThemePackParser.parseName(draft.name);
        stored.name = name == null ? "My theme" : name;
        stored.dark = draft.dark;
        stored.accent = draft.accent;
        stored.scrim = draft.scrim;
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : draft.colors.entrySet()) {
            String canonical = ThemePackParser.canonicalColorToken(e.getKey());
            if (canonical == null || e.getValue() == null) continue;
            colors.put(canonical, e.getValue());
        }
        if (draft.scrim != null) colors.put("wallpaper_scrim", draft.scrim);
        stored.colors = colors;
        stored.strings = null;
        Map<String, Integer> syntax = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : draft.editorSyntax.entrySet()) {
            String canonical = ThemePackParser.canonicalSyntaxKey(e.getKey());
            if (canonical == null || e.getValue() == null) continue;
            syntax.put(canonical, e.getValue());
        }
        stored.editorSyntax = syntax.isEmpty() ? null : syntax;
        String scope = ThemePackContracts.isWallpaperScope(draft.wallpaperScope)
                ? draft.wallpaperScope : ThemePackContracts.WALLPAPER_SCOPE_FILES;
        stored.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_FILES
                .equals(scope) ? null : scope;
        stored.launcherIcon = validLauncherId(draft.launcherId);
        stored.signer = null; // local themes carry no signer
        stored.schema = ThemePackStore.SCHEMA_LATEST;
        stored.revision = (previous != null && previous.revision != null
                ? previous.revision : 0L) + 1L;

        // Wallpaper: staged replaces; retained name keeps the stored file;
        // cleared or scope-none drops it (commit's sweep deletes the orphan).
        boolean scopeNone = ThemePackContracts.WALLPAPER_SCOPE_NONE.equals(scope);
        if (scopeNone) {
            stored.wallpaperName = null;
        } else if (draft.stagedWallpaper != null) {
            stored.wallpaperName = null; // commit assigns a fresh name
        } else if (!draft.wallpaperCleared && draft.retainedWallpaper != null
                && draft.retainedWallpaper.matches("wallpaper-[0-9]{1,19}")) {
            stored.wallpaperName = draft.retainedWallpaper;
        }
        if (draft.stagedPreview != null) {
            stored.previewName = null;
        } else if (!draft.previewCleared && draft.retainedPreview != null
                && draft.retainedPreview.matches("preview-[0-9]{1,19}")) {
            stored.previewName = draft.retainedPreview;
        }
        // Icons: retained entries keep stored files; staged entries are
        // committed under their contract key by the shared commit.
        Map<String, String> iconNames = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : draft.retainedIcons.entrySet()) {
            if (draft.stagedIcons.containsKey(e.getKey())) continue;
            if (Boolean.TRUE.equals(draft.iconsCleared.get(e.getKey()))) continue;
            if (!ThemePackContracts.isIconKey(e.getKey())) continue;
            String iconName = e.getValue();
            if (iconName == null) continue;
            // Only this store's generated names may be referenced.
            String prefix = "icon-" + e.getKey() + "-";
            if (!iconName.startsWith(prefix)
                    || !iconName.substring(prefix.length()).matches("[0-9]{1,19}")) {
                continue;
            }
            iconNames.put(e.getKey(), iconName);
        }
        stored.iconNames = iconNames.isEmpty() ? null : iconNames;
        return stored;
    }

    /** Staged icons that commit must consume (not retained ones). */
    static Map<String, File> stagedIconsForCommit(Draft draft) {
        Map<String, File> out = new LinkedHashMap<>();
        for (Map.Entry<String, File> e : draft.stagedIcons.entrySet()) {
            if (Boolean.TRUE.equals(draft.iconsCleared.get(e.getKey()))) continue;
            if (!ThemePackContracts.isIconKey(e.getKey())) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * The staged wallpaper {@link #persist} must hand to the backend commit.
     * With wallpaper scope {@code none}, {@link #buildStored} leaves
     * {@code wallpaperName} null ("scope none drops it"), but
     * {@code ThemePackStore.commit} re-assigns a fresh name whenever a
     * staged wallpaper file is passed — which would silently persist the
     * wallpaper the user asked not to use. Returns null in that case so
     * nothing is committed and the staged file can be dropped; mirrors
     * buildStored's scope normalization exactly.
     */
    static File stagedWallpaperForCommit(Draft draft) {
        if (draft == null || draft.stagedWallpaper == null) return null;
        String scope = ThemePackContracts.isWallpaperScope(draft.wallpaperScope)
                ? draft.wallpaperScope : ThemePackContracts.WALLPAPER_SCOPE_FILES;
        if (ThemePackContracts.WALLPAPER_SCOPE_NONE.equals(scope)) return null;
        return draft.stagedWallpaper;
    }

    /**
     * Saves the draft WITHOUT changing the active theme. Returns the full
     * id or null on failure; on failure stored state is untouched and
     * staged files are cleaned up.
     */
    public static String save(Context context, Draft draft) {
        return persist(context, draft, false);
    }

    /**
     * Saves the draft and makes it the active theme. Returns the full id
     * or null on failure.
     */
    public static String saveAndApply(Context context, Draft draft) {
        return persist(context, draft, true);
    }

    private static String persist(Context context, Draft draft, boolean activate) {
        if (context == null || draft == null) return null;
        String key = draft.key == null ? newKey() : draft.key;
        if (key == null || !key.matches("[0-9a-f]{8,64}")) {
            discard(draft);
            return null;
        }
        ThemePackStore.StoredSnapshot previous;
        try {
            previous = ThemePacks.readLocal(context, key);
        } catch (Exception e) {
            previous = null;
        }
        ThemePackStore.StoredSnapshot stored = buildStored(draft, key, previous);
        Map<String, File> stagedIcons = stagedIconsForCommit(draft);
        // Scope "none" means no wallpaper is persisted: drop a staged file so
        // the commit cannot re-assign a name buildStored intentionally left
        // null (see stagedWallpaperForCommit).
        File stagedWallpaper = stagedWallpaperForCommit(draft);
        if (stagedWallpaper == null && draft.stagedWallpaper != null) {
            deleteQuiet(draft.stagedWallpaper);
            draft.stagedWallpaper = null;
        }
        String id = idOf(key);
        boolean done;
        try {
            done = activate
                    ? ThemePacks.activateLocal(context, key, stored,
                            stagedWallpaper, draft.stagedPreview, stagedIcons) != null
                    : ThemePacks.commitLocal(context, key, stored,
                            stagedWallpaper, draft.stagedPreview, stagedIcons);
        } catch (Exception e) {
            done = false;
        }
        if (!done) {
            discard(draft);
            return null;
        }
        draft.key = key;
        draft.stagedWallpaper = null;
        draft.stagedPreview = null;
        draft.stagedIcons.clear();
        draft.retainedWallpaper = stored.wallpaperName;
        draft.retainedPreview = stored.previewName;
        draft.retainedIcons.clear();
        if (stored.iconNames != null) draft.retainedIcons.putAll(stored.iconNames);
        draft.wallpaperCleared = false;
        draft.previewCleared = false;
        draft.iconsCleared.clear();
        return id;
    }

    /** Activates an already saved local theme. False when missing. */
    public static boolean activate(Context context, String id) {
        try {
            String key = keyOf(id);
            return key != null && ThemePacks.activateExistingLocal(context, key);
        } catch (Exception e) {
            return false;
        }
    }

    /** True when id is the currently active local theme. */
    public static boolean isActive(Context context, String id) {
        ThemePacks.Snapshot active;
        try {
            active = ThemePacks.active(context);
        } catch (Exception e) {
            return false;
        }
        return active != null && active.packageName != null && active.packageName.equals(id);
    }

    /** Removes a saved local theme (backend clears activation if needed). */
    public static boolean delete(Context context, String id) {
        try {
            String key = keyOf(id);
            return key != null && ThemePacks.deleteLocal(context, key);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- export / import ----------

    /**
     * Writes a portable single-file document (JSON with base64-embedded
     * images) for {@code theme} to {@code out}. The stream is not closed.
     */
    public static boolean exportTheme(Context context, LocalTheme theme,
                                      OutputStream out) {
        if (theme == null || out == null) return false;
        try {
            JsonObject doc = new JsonObject();
            doc.addProperty("format", EXPORT_FORMAT);
            doc.addProperty("type", EXPORT_TYPE);
            doc.addProperty("name", theme.name == null ? "My theme" : theme.name);
            if (theme.dark != null) doc.addProperty("dark", theme.dark);
            if (theme.accent != null) doc.addProperty("accent", theme.accent);
            if (theme.scrim != null) doc.addProperty("scrim", theme.scrim);
            doc.addProperty("wallpaperScope", theme.wallpaperScope);
            if (theme.launcherId != null) doc.addProperty("launcherId", theme.launcherId);
            JsonObject colors = new JsonObject();
            for (Map.Entry<String, Integer> e : theme.colors.entrySet()) {
                colors.addProperty(e.getKey(), hex(e.getValue()));
            }
            doc.add("colors", colors);
            if (!theme.editorSyntax.isEmpty()) {
                JsonObject syntax = new JsonObject();
                for (Map.Entry<String, Integer> e : theme.editorSyntax.entrySet()) {
                    syntax.addProperty(e.getKey(), hex(e.getValue()));
                }
                doc.add("editorSyntax", syntax);
            }
            JsonObject images = new JsonObject();
            putImage(images, "wallpaper", theme.wallpaper);
            putImage(images, "preview", theme.preview);
            // Every configured file-type icon travels under its contract
            // key, including optional _light/_dark variants.
            for (Map.Entry<String, File> e : theme.iconFiles.entrySet()) {
                if (!ThemePackContracts.isIconKey(e.getKey())) continue;
                putImage(images, e.getKey(), e.getValue());
            }
            doc.add("images", images);
            try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                new Gson().toJson(doc, w);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void putImage(JsonObject images, String key, File file) {
        if (file == null || !file.isFile()) return;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) buffer.write(buf, 0, n);
        } catch (Exception e) {
            return;
        }
        images.addProperty(key,
                Base64.getEncoder().encodeToString(buffer.toByteArray()));
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    /** Import outcome: theme non-null on success; rejected=true when the
     *  document violated a limit/validation rule (vs plain IO failure). */
    public static final class ImportResult {
        public final LocalTheme theme;
        public final boolean rejected;
        /** Short non-localized reason for logging; null on success. */
        public final String error;

        public ImportResult(LocalTheme theme, boolean rejected, String error) {
            this.theme = theme;
            this.rejected = rejected;
            this.error = error;
        }
    }

    /**
     * Reads a portable document and saves it as a NEW local theme (fresh
     * key, name kept, not activated). Safe-limited: document size cap,
     * base64 image caps with PNG/JPEG magic checks (decode revalidation by
     * the backend staging), allowlist filtering, unknown entries dropped.
     */
    public static ImportResult importTheme(Context context, InputStream in) {
        if (context == null) return new ImportResult(null, false, "no-context");
        File staged = null;
        try {
            staged = stageDocument(context, in, MAX_DOCUMENT_BYTES);
            if (staged == null) return new ImportResult(null, true, "size");
            return importDocument(context, staged);
        } finally {
            deleteQuiet(staged);
        }
    }

    /** Bounded staging of the raw document before parsing. Uses the app
     *  cache dir: {@code java.io.tmpdir} is unset on Android, so the old
     *  fallback location made every real-device import fail. */
    private static File stageDocument(Context context, InputStream in, long maxBytes) {
        if (in == null) return null;
        File cacheDir = context.getCacheDir();
        if (cacheDir == null) return null;
        File tmp = new File(cacheDir, "local-theme-" + System.nanoTime());
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    tmp.delete();
                    return null;
                }
                out.write(buf, 0, n);
            }
            return tmp;
        } catch (Exception e) {
            tmp.delete();
            return null;
        }
    }

    private static ImportResult importDocument(Context context, File document) {
        JsonObject doc;
        try (InputStreamReader r = new InputStreamReader(
                new FileInputStream(document), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root == null || !root.isJsonObject()) {
                return new ImportResult(null, true, "not-json-object");
            }
            doc = root.getAsJsonObject();
        } catch (Exception e) {
            return new ImportResult(null, true, "not-json");
        }
        Integer format = intOrNull(doc, "format");
        String type = stringOrNull(doc, "type");
        if (format == null || format != EXPORT_FORMAT || !EXPORT_TYPE.equals(type)) {
            return new ImportResult(null, true, "format");
        }
        Draft draft = new Draft();
        draft.key = null; // always a fresh theme
        String name = ThemePackParser.parseName(stringOrNull(doc, "name"));
        draft.name = name == null ? "" : name;
        Boolean dark = boolOrNull(doc, "dark");
        draft.dark = dark != null && dark;
        draft.accent = colorOrNull(doc, "accent");
        draft.scrim = colorOrNull(doc, "scrim");
        String scope = stringOrNull(doc, "wallpaperScope");
        if (scope != null && !ThemePackContracts.isWallpaperScope(scope)) {
            return new ImportResult(null, true, "wallpaperScope");
        }
        draft.wallpaperScope = scope == null
                ? ThemePackContracts.WALLPAPER_SCOPE_FILES : scope;
        draft.launcherId = validLauncherId(stringOrNull(doc, "launcherId"));

        JsonObject colors = objectOrNull(doc, "colors");
        if (colors != null && colors.size() > ThemePackContracts.COLOR_TOKENS.length + 8) {
            return new ImportResult(null, true, "colors-too-many");
        }
        if (colors != null) {
            for (Map.Entry<String, JsonElement> e : colors.entrySet()) {
                String canonical = ThemePackParser.canonicalColorToken(e.getKey());
                if (canonical == null) continue;
                Integer argb = colorOrNull(colors, e.getKey());
                if (argb != null) draft.colors.put(canonical, argb);
            }
        }
        JsonObject syntax = objectOrNull(doc, "editorSyntax");
        if (syntax != null
                && syntax.size() > ThemePackContracts.EDITOR_SYNTAX_CATEGORIES.length + 8) {
            return new ImportResult(null, true, "syntax-too-many");
        }
        if (syntax != null) {
            for (Map.Entry<String, JsonElement> e : syntax.entrySet()) {
                String canonical = ThemePackParser.canonicalSyntaxKey(e.getKey());
                if (canonical == null) continue;
                Integer argb = colorOrNull(syntax, e.getKey());
                if (argb != null) draft.editorSyntax.put(canonical, argb);
            }
        }
        JsonObject images = objectOrNull(doc, "images");
        if (images != null) {
            EmbeddedImages embedded = decodeEmbeddedImages(images);
            if (embedded.error != null) {
                return new ImportResult(null, true, embedded.error);
            }
            if (!stageEmbedded(context, draft, embedded)) {
                return new ImportResult(null, true, "images");
            }
        }
        String id = save(context, draft);
        if (id == null) return new ImportResult(null, false, "save");
        return new ImportResult(read(context, id), false, null);
    }

    /** Decoded, validated embedded image payloads of one document. */
    static final class EmbeddedImages {
        /** Allowlisted key -> decoded bytes; empty only when no images. */
        final Map<String, byte[]> payloads = new LinkedHashMap<>();
        /** Non-null when the document violated a limit/validation rule. */
        final String error;

        EmbeddedImages(String error) {
            this.error = error;
        }
    }

    /** Document key -> slot (null = unknown key, silently dropped like the
     *  color allowlist; never decoded). */
    private static ImageSlot documentSlotFor(String key) {
        if ("wallpaper".equals(key)) return ImageSlot.WALLPAPER;
        if ("preview".equals(key)) return ImageSlot.PREVIEW;
        return ImageSlot.iconSlot(key);
    }

    /**
     * PURE document validation (JVM-testable): caps the number of embedded
     * images, then decodes and validates every allowlisted {@code images}
     * member against the per-slot byte caps, the PNG/JPEG magic rule and
     * the shared total icon byte budget. Unknown keys are dropped
     * (forward-compatible); a recognized key that violates any rule rejects
     * the whole document so an update can never silently erase previously
     * valid artwork.
     */
    static EmbeddedImages decodeEmbeddedImages(JsonObject images) {
        if (images.size() > MAX_EMBEDDED_IMAGES) {
            return new EmbeddedImages("images-too-many");
        }
        EmbeddedImages out = new EmbeddedImages(null);
        long iconBytesTotal = 0;
        for (Map.Entry<String, JsonElement> e : images.entrySet()) {
            ImageSlot slot = documentSlotFor(e.getKey());
            if (slot == null) continue; // unknown key: dropped, not decoded
            JsonElement value = e.getValue();
            if (value == null || !value.isJsonPrimitive()
                    || value.getAsJsonPrimitive().isBoolean()
                    || value.getAsJsonPrimitive().isNumber()) {
                return new EmbeddedImages("images");
            }
            String base64 = value.getAsString();
            // Base64 of N bytes is ~4*(N/3) chars; reject early so a
            // multi-megabyte string never reaches the decoder.
            if (base64.length() > (slot.maxBytes / 3 + 4) * 4 + 1024) {
                return new EmbeddedImages("images");
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException ex) {
                return new EmbeddedImages("images");
            }
            if (bytes.length == 0 || bytes.length > slot.maxBytes) {
                return new EmbeddedImages("images");
            }
            if (!hasSupportedImageMagic(bytes)) {
                return new EmbeddedImages("images");
            }
            if (slot.iconKey != null) {
                iconBytesTotal += bytes.length;
                if (iconBytesTotal > ThemePackContracts.MAX_ICONS_TOTAL_BYTES) {
                    return new EmbeddedImages("images");
                }
            }
            out.payloads.put(e.getKey(), bytes);
        }
        return out;
    }

    /** Android-side: stages validated payloads into the draft (order kept);
     *  icon payloads go under their EXACT key so variants survive. */
    private static boolean stageEmbedded(Context context, Draft draft,
                                         EmbeddedImages embedded) {
        for (Map.Entry<String, byte[]> e : embedded.payloads.entrySet()) {
            String key = e.getKey();
            boolean ok;
            if ("wallpaper".equals(key) || "preview".equals(key)) {
                ok = stageImageStream(context, draft, documentSlotFor(key),
                        new ByteArrayInputStream(e.getValue()));
            } else {
                ok = stageIconStream(context, draft, key,
                        new ByteArrayInputStream(e.getValue()));
            }
            if (!ok) return false;
        }
        return true;
    }

    /** Pure magic-byte sniff: only PNG and JPEG pass. */
    static boolean hasSupportedImageMagic(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G') return true;
        return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    // ---------- JSON helpers ----------

    private static JsonObject objectOrNull(JsonObject obj, String key) {
        try {
            if (!obj.has(key)) return null;
            JsonElement e = obj.get(key);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intOrNull(JsonObject obj, String key) {
        try {
            if (!obj.has(key)) return null;
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return null;
            return e.getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean boolOrNull(JsonObject obj, String key) {
        try {
            if (!obj.has(key)) return null;
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return null;
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                long v = p.getAsLong();
                return v == 0 ? Boolean.FALSE : v == 1 ? Boolean.TRUE : null;
            }
            String s = p.getAsString().trim();
            if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringOrNull(JsonObject obj, String key) {
        try {
            if (!obj.has(key)) return null;
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonPrimitive() || e.getAsJsonPrimitive().isBoolean()) {
                return null;
            }
            String v = e.getAsString();
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /** "#AARRGGBB"/"#RRGGBB"/numeric ARGB; delegates to the shared parser. */
    private static Integer colorOrNull(JsonObject obj, String key) {
        try {
            if (!obj.has(key)) return null;
            JsonElement e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return null;
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return null;
            return ThemePackParser.parseColorValue(p.isNumber()
                    ? (Number) p.getAsNumber() : p.getAsString());
        } catch (Exception e) {
            return null;
        }
    }
}
