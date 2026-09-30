package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.theme.ActiveTheme;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;
import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

public class FileIconLoader {

    private static final ExecutorService iconLoaderService = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Drawable> iconCache = new LruCache<>(100);

    private static Drawable cachedFolderIcon, cachedApkIcon, cachedImageIcon, cachedVideoIcon,
            cachedDexIcon, cachedArscIcon,
            cachedMusicIcon, cachedArchiveIcon, cachedPdfIcon, cachedTextIcon, cachedFileIcon;
    private static int cachedIconTheme = -1;
    private static int cachedIconBucket = -1;

    private final MainActivity context;
    private final boolean isInZip;

    public FileIconLoader(MainActivity context, boolean isInZip) {
        this.context = context;
        this.isInZip = isInZip;
        ensureCachedIcons(context, context.getResources());
    }

    public static Drawable getCachedApkIcon() {
        return cachedApkIcon;
    }

    public void setupZipEntryView(ZipEntryInfo zipEntry, ImageView fileIconView, TextView fileDateView) {
        if (zipEntry == null) return;
        if (zipEntry.isDirectory()) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
        } else {
            setupNonFolderIconView(zipEntry.getFullPath(), fileIconView);
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, zipEntry.getLastModified())).append(' ').append(FileSize.getHumanReadableFileSize(zipEntry.getSize())));
        }
    }

    public void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        if (file.isFile()) {
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            setupNonFolderIconView(file.getPath(), fileIconView);
            fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, file.lastModified())).append(' ').append(FileSize.getHumanReadableFileSize(file.length())));
        } else {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
        }
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView) {
        if (!isInZip) {
            Drawable cached = iconCache.get(path);
            if (cached != null) {
                fileIconView.setImageDrawable(cached);
                return;
            }
        }

        String ext = FilenameUtils.getExtension(path);
        if (ext != null) ext = "." + ext.toLowerCase(Locale.ROOT);
        else ext = "";

        if (".apk".equals(ext)) {
            fileIconView.setImageDrawable(cachedApkIcon);
            if (!isInZip) loadApkIconAsync(path, fileIconView);
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            fileIconView.setImageDrawable(cachedImageIcon);
            if (!isInZip) loadThumbnailAsync(path, fileIconView, false);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            fileIconView.setImageDrawable(cachedVideoIcon);
            if (!isInZip) loadThumbnailAsync(path, fileIconView, true);
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            fileIconView.setImageDrawable(cachedMusicIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            fileIconView.setImageDrawable(cachedArchiveIcon);
        } else if (".pdf".equals(ext)) {
            fileIconView.setImageDrawable(cachedPdfIcon);
        } else if(".dex".equals(ext)) {
            fileIconView.setImageDrawable(cachedDexIcon);
        } else if(".arsc".equals(ext)) {
            fileIconView.setImageDrawable(cachedArscIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) {
            fileIconView.setImageDrawable(cachedTextIcon);
        } else {
            fileIconView.setImageDrawable(cachedFileIcon);
        }
    }

    private void loadApkIconAsync(String path, ImageView fileIconView) {
        fileIconView.setTag(path);
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo packageInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES);
                if (packageInfo != null) {
                    ApplicationInfo appInfo = packageInfo.applicationInfo;
                    if (appInfo != null) {
                        if (TextUtils.isEmpty(appInfo.sourceDir) || TextUtils.isEmpty(appInfo.publicSourceDir)) {
                            appInfo.sourceDir = path;
                            appInfo.publicSourceDir = path;
                        }
                        Drawable icon = appInfo.loadIcon(pm);
                        if (icon != null) {
                            iconCache.put(path, icon);
                            context.runOnUiThread(() -> {
                                if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadThumbnailAsync(String path, ImageView fileIconView, boolean isVideo) {
        fileIconView.setTag(path);
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
            Bitmap bitmap = isVideo ? loadVideoThumbnail(path) : loadImageThumbnail(path);
            if (bitmap == null || !path.equals(fileIconView.getTag())) return;
            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
            iconCache.put(path, icon);
            context.runOnUiThread(() -> {
                if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
            });
        });
    }

    private static void ensureCachedIcons(Context context, Resources res) {
        float density = res.getDisplayMetrics().density;
        int bucket = (int) (density * 4);
        if (cachedIconTheme == ThemeRegistry.getCurrentId(context).hashCode() && cachedIconBucket == bucket) return;
        cachedIconTheme = ThemeRegistry.getCurrentId(context).hashCode();
        cachedIconBucket = bucket;
        // MT Manager draws every badge the same flat dark grey with a grey glyph
        // on top, so only the glyph shape distinguishes the types. Both the badge
        // and the glyph follow the theme, which is why they are re-read whenever
        // the theme id or density changes.
        int glyph = ThemeAttrs.onSurface(context);
        cachedFolderIcon  = badge(res, density, R.drawable.ic_folder_mt, ThemeAttrs.iconFolder(context), glyph);
        cachedApkIcon     = badge(res, density, R.drawable.apk_document_24px, ThemeAttrs.iconFile(context), glyph);
        cachedImageIcon   = badge(res, density, R.drawable.image_24px, ThemeAttrs.iconFile(context), glyph);
        cachedVideoIcon   = badge(res, density, R.drawable.video_24px, ThemeAttrs.iconFile(context), glyph);
        cachedMusicIcon   = badge(res, density, R.drawable.music_24px, ThemeAttrs.iconFile(context), glyph);
        cachedArscIcon    = badge(res, density, R.drawable.stacks_24px, ThemeAttrs.iconFile(context), glyph);
        cachedDexIcon     = badge(res, density, R.drawable.code_24px, ThemeAttrs.iconFile(context), glyph);
        cachedArchiveIcon = badge(res, density, R.drawable.baseline_folder_zip_24, ThemeAttrs.iconFile(context), glyph);
        cachedPdfIcon     = badge(res, density, R.drawable.pdf_24px, ThemeAttrs.iconFile(context), glyph);
        cachedTextIcon    = badge(res, density, R.drawable.baseline_text_snippet_24, ThemeAttrs.iconFile(context), glyph);
        cachedFileIcon    = badge(res, density, R.drawable.baseline_insert_drive_file_24,
                ThemeAttrs.iconFile(context), glyph);
    }

    private static Drawable badge(Resources res, float density, int glyphId, int bgColor, int glyphColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6 * density);
        bg.setColor(bgColor);
        Drawable glyph = ResourcesCompat.getDrawable(res, glyphId, null);
        if (glyph != null) {
            glyph = glyph.mutate();
            DrawableCompat.setTint(glyph, glyphColor);
        } else {
            glyph = new ColorDrawable(Color.TRANSPARENT);
        }
        LayerDrawable layer = new LayerDrawable(
                new Drawable[]{bg, glyph});
        int inset = (int) (6 * density + 0.5f);
        layer.setLayerInset(1, inset, inset, inset, inset);
        return layer;
    }

    private static Bitmap loadImageThumbnail(String path) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            int width = options.outWidth;
            int height = options.outHeight;
            int scale = 1;
            while (width / 2 >= 128 && height / 2 >= 128) {
                width /= 2;
                height /= 2;
                scale *= 2;
            }

            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap loadVideoThumbnail(String path) {
        try {
            return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
        } catch (Throwable t) {
            return null;
        }
    }
}
