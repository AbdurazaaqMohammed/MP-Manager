package io.github.abdurazaaqmohammed.ui.main;

import io.github.abdurazaaqmohammed.feature.apk.ApkIconHelper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import java.util.List;
import java.util.Locale;

/** Shared file-type artwork for pane rows and configurable bookmark shortcuts. */
public record FileIconSpec(int glyph, int background, int foreground, int label) {
    public static final FileIconSpec FOLDER = new FileIconSpec(R.drawable.ic_folder_mt, 0xFFD4887C, Color.WHITE, R.string.folder);
    public static final FileIconSpec APK = spec(R.drawable.apk_document_24px, 0xFF2E7D32, R.string.shortcut_icon_apk);
    public static final FileIconSpec IMAGE = spec(R.drawable.image_24px, 0xFF6A1B9A, R.string.image);
    public static final FileIconSpec VIDEO = spec(R.drawable.video_24px, 0xFFC62828, R.string.shortcut_icon_video);
    public static final FileIconSpec AUDIO = spec(R.drawable.music_24px, 0xFF00897B, R.string.shortcut_icon_audio);
    public static final FileIconSpec ARSC = spec(R.drawable.stacks_24px, 0xFF9A6A00, R.string.shortcut_icon_resources);
    public static final FileIconSpec DEX = spec(R.drawable.code_24px, 0xFF549395, R.string.shortcut_icon_dex);
    public static final FileIconSpec ARCHIVE = spec(R.drawable.baseline_folder_zip_24, 0xFFE65100, R.string.shortcut_icon_archive);
    public static final FileIconSpec STICKER = spec(R.drawable.ic_sticker_24px, 0xFF00897B, R.string.shortcut_icon_sticker);
    public static final FileIconSpec PDF = spec(R.drawable.pdf_24px, 0xFFAD1457, R.string.shortcut_icon_pdf);
    public static final FileIconSpec TEXT = spec(R.drawable.baseline_text_snippet_24, 0xFF1565C0, R.string.shortcut_icon_text);
    public static final FileIconSpec JAVA = spec(R.drawable.code_24px, 0xFFE65100, R.string.shortcut_icon_java);
    public static final FileIconSpec KOTLIN = spec(R.drawable.code_24px, 0xFF7F52FF, R.string.shortcut_icon_kotlin);
    public static final FileIconSpec XML = spec(R.drawable.code_24px, 0xFF0277BD, R.string.shortcut_icon_xml);
    public static final FileIconSpec JSON = spec(R.drawable.code_24px, 0xFF2E7D32, R.string.shortcut_icon_json);
    public static final FileIconSpec PYTHON = spec(R.drawable.code_24px, 0xFF306998, R.string.shortcut_icon_python);
    public static final FileIconSpec SMALI = spec(R.drawable.code_24px, 0xFF00897B, R.string.shortcut_icon_smali);
    public static final FileIconSpec JS = spec(R.drawable.code_24px, 0xFFF9A825, R.string.shortcut_icon_js);
    public static final FileIconSpec ELF = spec(R.drawable.terminal_24px, 0xFF37474F, R.string.shortcut_icon_elf);
    public static final FileIconSpec DISK = spec(R.drawable.ic_disk_drive_24px, 0xFF455A64, R.string.shortcut_icon_disk);
    public static final FileIconSpec DATABASE = spec(R.drawable.ic_database_24px, 0xFF00695C, R.string.shortcut_icon_database);
    public static final FileIconSpec FONT = spec(R.drawable.ic_font_aa_24px, 0xFFD84315, R.string.shortcut_icon_font);
    public static final FileIconSpec BINARY = spec(R.drawable.baseline_settings_24, 0xFF455A64, R.string.shortcut_icon_binary);
    public static final FileIconSpec PROTO = spec(R.drawable.ic_data_proto_24px, 0xFF4527A0, R.string.shortcut_icon_data);
    public static final FileIconSpec DOCUMENT = spec(R.drawable.ic_office_doc_24px, 0xFF1565C0, R.string.shortcut_icon_document);
    public static final FileIconSpec SHEET = spec(R.drawable.ic_office_sheet_24px, 0xFF2E7D32, R.string.shortcut_icon_sheet);
    public static final FileIconSpec SLIDE = spec(R.drawable.ic_office_slide_24px, 0xFFE65100, R.string.shortcut_icon_slide);
    public static final FileIconSpec SCRIPT = spec(R.drawable.terminal_24px, 0xFF283593, R.string.shortcut_icon_script);
    public static final FileIconSpec BACKUP = spec(R.drawable.ic_history, 0xFF5D4037, R.string.shortcut_icon_backup);
    public static final FileIconSpec CERTIFICATE = spec(R.drawable.ic_certificate_seal_24px, 0xFFF57F17, R.string.shortcut_icon_certificate);
    public static final FileIconSpec FILE = spec(R.drawable.baseline_insert_drive_file_24, 0xFF424242, R.string.file);

    private static FileIconSpec spec(int glyph, int color, int label) { return new FileIconSpec(glyph, color, Color.WHITE, label); }

    public static List<FileIconSpec> all() {
        return List.of(FOLDER, FILE, APK, IMAGE, VIDEO, AUDIO, DEX, ARSC, ARCHIVE, STICKER, PDF, TEXT,
                JAVA, KOTLIN, XML, JSON, PYTHON, SMALI, JS,
                ELF, DISK, DATABASE, FONT, BINARY, PROTO, DOCUMENT, SHEET, SLIDE, SCRIPT, BACKUP, CERTIFICATE);
    }

    /** Returns the distinct code/markup FileIconSpec for a dotted lowercase extension, or null. */
    public static FileIconSpec codeSpecForExt(String extWithDot) {
        if (extWithDot == null) return null;
        return switch (extWithDot) {
            case ".java" -> JAVA;
            case ".kt", ".kts" -> KOTLIN;
            case ".xml", ".axml", ".html", ".htm", ".xhtml" -> XML;
            case ".json", ".yaml", ".yml", ".toml" -> JSON;
            case ".py", ".pyw" -> PYTHON;
            case ".smali" -> SMALI;
            case ".js", ".mjs", ".cjs", ".ts", ".mts", ".jsx", ".tsx" -> JS;
            default -> null;
        };
    }

    public static FileIconSpec forPath(String path, boolean directory) {
        return forPath(path, directory, 0);
    }

    public static FileIconSpec forPath(String path, boolean directory, int theme) {
        if (directory) return FOLDER;
        String extension = "." + FilenameUtils.getExtension(path).toLowerCase(Locale.ROOT);
        if (ApkIconHelper.isApkLikeExt(extension))
            return List.of(".apks", ".xapk", ".apkm", ".aspk").contains(extension) ? ARCHIVE : APK;
        if (FileUtils.matchExt(extension, FileUtils.IMAGE_EXTS)) return IMAGE;
        if (FileUtils.matchExt(extension, FileUtils.VIDEO_EXTS)) return VIDEO;
        if (FileUtils.matchExt(extension, FileUtils.AUDIO_EXTS)) return AUDIO;
        if (FileUtils.matchExt(extension, FileUtils.ARCHIVE_EXTS)) return ARCHIVE;
        if (extension.equals(".pdf")) return PDF;
        if (extension.equals(".dex")) return DEX;
        if (extension.equals(".arsc")) return ARSC;
        FileIconSpec codeSpec = codeSpecForExt(extension);
        if (codeSpec != null) return codeSpec;
        if (isDatabaseExt(extension)) return DATABASE;
        if (isFontExt(extension)) return FONT;
        if (isBinaryExt(extension)) return BINARY;
        if (isProtoExt(extension)) return PROTO;
        if (isWordExt(extension)) return DOCUMENT;
        if (isSheetExt(extension)) return SHEET;
        if (isSlideExt(extension)) return SLIDE;
        if (isScriptExt(extension)) return SCRIPT;
        if (isBackupExt(extension)) return BACKUP;
        if (isCertExt(extension)) return CERTIFICATE;
        if (FileUtils.matchExt(extension, FileUtils.TEXT_EXTS)) return TEXT;
        if (List.of(".img", ".iso", ".vdi", ".vhd", ".vhdx", ".vmdk", ".qcow2", ".ext4", ".wad", ".payload").contains(extension)
                || "payload.bin".equalsIgnoreCase(FilenameUtils.getName(path))) return DISK;
        if (FileUtils.isElf(path)) return ELF;
        return theme == R.style.Theme_MyApp_Light ? spec(FILE.glyph, 0xFF616161, R.string.file) : FILE;
    }

    static boolean isDatabaseExt(String ext) {
        return ".db".equals(ext) || ".sqlite".equals(ext) || ".sqlite3".equals(ext)
                || ".db-wal".equals(ext) || ".db-shm".equals(ext) || ".db-journal".equals(ext)
                || ".workdb".equals(ext) || ".workdb-wal".equals(ext) || ".workdb-shm".equals(ext)
                || ".ldb".equals(ext) || ".dbf".equals(ext) || ".mdb".equals(ext) || ".accdb".equals(ext)
                || ".rdb".equals(ext) || ".realm".equals(ext) || ".kdbx".equals(ext)
                || ".wallet".equals(ext) || ".keychain".equals(ext);
    }

    static boolean isFontExt(String ext) {
        return ".ttf".equals(ext) || ".otf".equals(ext) || ".woff".equals(ext) || ".woff2".equals(ext) || ".ttc".equals(ext);
    }

    static boolean isBinaryExt(String ext) {
        return ".so".equals(ext) || ".bin".equals(ext) || ".dat".equals(ext) || ".vdex".equals(ext)
                || ".odex".equals(ext) || ".art".equals(ext) || ".o".equals(ext) || ".dylib".equals(ext) || ".dll".equals(ext)
                || ".binproto".equals(ext) || ".binwalk".equals(ext) || ".bios".equals(ext) || ".rom".equals(ext)
                || ".dtbo".equals(ext) || ".vbmeta".equals(ext) || ".sparse".equals(ext) || ".chd".equals(ext)
                || ".cso".equals(ext) || ".pbp".equals(ext) || ".gb".equals(ext) || ".gba".equals(ext)
                || ".gbc".equals(ext) || ".nds".equals(ext) || ".nes".equals(ext) || ".n64".equals(ext)
                || ".z64".equals(ext) || ".sfc".equals(ext) || ".smc".equals(ext) || ".oat".equals(ext)
                || ".pyc".equals(ext) || ".wasm".equals(ext) || ".tflite".equals(ext) || ".onnx".equals(ext)
                || ".h5".equals(ext) || ".mat".equals(ext) || ".msgpack".equals(ext) || ".pcap".equals(ext)
                || ".pcapng".equals(ext) || ".cap".equals(ext) || ".snoop".equals(ext) || ".trace".equals(ext)
                || ".hprof".equals(ext) || ".shm".equals(ext) || ".wal".equals(ext) || ".seed".equals(ext)
                || ".xpo".equals(ext) || ".crypt14".equals(ext) || ".crypt15".equals(ext);
    }

    static boolean isProtoExt(String ext) {
        return ".pb".equals(ext) || ".proto".equals(ext) || ".preferences_pb".equals(ext) || ".pma".equals(ext) || ".bson".equals(ext)
                || ".parquet".equals(ext) || ".arrow".equals(ext) || ".avro".equals(ext);
    }

    static boolean isWordExt(String ext) {
        return ".doc".equals(ext) || ".docx".equals(ext) || ".odt".equals(ext) || ".rtf".equals(ext);
    }

    static boolean isSheetExt(String ext) {
        return ".xls".equals(ext) || ".xlsx".equals(ext) || ".csv".equals(ext) || ".ods".equals(ext) || ".tsv".equals(ext);
    }

    static boolean isSlideExt(String ext) {
        return ".ppt".equals(ext) || ".pptx".equals(ext) || ".odp".equals(ext);
    }

    static boolean isScriptExt(String ext) {
        return ".sh".equals(ext) || ".bash".equals(ext) || ".py".equals(ext) || ".c".equals(ext)
                || ".cpp".equals(ext) || ".h".equals(ext) || ".hpp".equals(ext) || ".cs".equals(ext)
                || ".rs".equals(ext) || ".go".equals(ext) || ".lua".equals(ext);
    }

    static boolean isBackupExt(String ext) {
        return ".bak".equals(ext) || ".backup".equals(ext) || ".old".equals(ext) || ".orig".equals(ext) || ".save".equals(ext);
    }

    static boolean isCertExt(String ext) {
        return ".pem".equals(ext) || ".crt".equals(ext) || ".cer".equals(ext) || ".key".equals(ext)
                || ".keystore".equals(ext) || ".jks".equals(ext) || ".bks".equals(ext) || ".der".equals(ext) || ".p12".equals(ext) || ".pfx".equals(ext)
                || ".asc".equals(ext) || ".gpg".equals(ext) || ".csr".equals(ext) || ".crl".equals(ext)
                || ".sig".equals(ext) || ".idsig".equals(ext) || ".pub".equals(ext) || ".p8".equals(ext) || ".pk8".equals(ext);
    }

    public Drawable drawable(Context context) {
        return FileIconArt.drawable(context, this);
    }

    public static Drawable parentDrawable(Context context) {
        return FileIconArt.parentFolderDrawable(context);
    }

    /** Classic flat vector tile drawable used when 3D icons preference is disabled. */
    public Drawable flatDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setCornerRadius(6 * density);
        int tile = background;
        if (glyph == R.drawable.ic_folder_mt) {
            tile = ThemeRegistry.packColor(context, "folder_icon_bg", 0xFF252525);
        } else if (glyph == R.drawable.baseline_insert_drive_file_24) {
            tile = ThemeRegistry.packColor(context, "file_icon_bg", tile);
        }
        backgroundDrawable.setColor(tile);
        Drawable raw = ResourcesCompat.getDrawable(context.getResources(), glyph, context.getTheme());
        if (raw == null) return backgroundDrawable;
        Drawable icon = raw.mutate();
        DrawableCompat.setTint(icon, foreground);
        LayerDrawable layer = new LayerDrawable(new Drawable[]{backgroundDrawable, icon});
        int inset = Math.round(6 * density);
        layer.setLayerInset(1, inset, inset, inset, inset);
        return layer;
    }

    /** Classic flat parent folder ("..") drawable. */
    public static Drawable flatParentDrawable(Context context) {
        return FOLDER.flatDrawable(context);
    }
}
