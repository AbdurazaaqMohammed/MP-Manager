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
    public static final FileIconSpec FOLDER = new FileIconSpec(R.drawable.ic_folder_mt, 0xFF252525, Color.WHITE, R.string.folder);
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
                ELF, DISK, DATABASE, FONT, BINARY, PROTO, DOCUMENT, SHEET, SLIDE, SCRIPT, BACKUP, CERTIFICATE);
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
        if (FileUtils.matchExt(extension, FileUtils.TEXT_EXTS)) return TEXT;
        if (FileIconLoader.isDatabaseExt(extension)) return DATABASE;
        if (FileIconLoader.isFontExt(extension)) return FONT;
        if (FileIconLoader.isBinaryExt(extension)) return BINARY;
        if (FileIconLoader.isProtoExt(extension)) return PROTO;
        if (FileIconLoader.isWordExt(extension)) return DOCUMENT;
        if (FileIconLoader.isSheetExt(extension)) return SHEET;
        if (FileIconLoader.isSlideExt(extension)) return SLIDE;
        if (FileIconLoader.isScriptExt(extension)) return SCRIPT;
        if (FileIconLoader.isBackupExt(extension)) return BACKUP;
        if (FileIconLoader.isCertExt(extension)) return CERTIFICATE;
        if (List.of(".img", ".iso", ".vdi", ".vhd", ".vhdx", ".vmdk", ".qcow2", ".ext4", ".wad", ".payload").contains(extension)
                || "payload.bin".equalsIgnoreCase(FilenameUtils.getName(path))) return DISK;
        if (FileUtils.isElf(path)) return ELF;
        return theme == R.style.Theme_MyApp_Light ? spec(FILE.glyph, 0xFF616161, R.string.file) : FILE;
    }

    public Drawable drawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setCornerRadius(6 * density);
        int tile = background;
        if (glyph == R.drawable.ic_folder_mt) {
            tile = ThemeRegistry.packColor(context, "folder_icon_bg", tile);
        } else if (glyph == R.drawable.baseline_insert_drive_file_24) {
            tile = ThemeRegistry.packColor(context, "file_icon_bg", tile);
        }
        backgroundDrawable.setColor(tile);
        Drawable icon = ResourcesCompat.getDrawable(context.getResources(), glyph, context.getTheme()).mutate();
        DrawableCompat.setTint(icon, foreground);
        LayerDrawable layer = new LayerDrawable(new Drawable[]{backgroundDrawable, icon});
        int inset = Math.round(6 * density);
        layer.setLayerInset(1, inset, inset, inset, inset);
        return layer;
    }
}
