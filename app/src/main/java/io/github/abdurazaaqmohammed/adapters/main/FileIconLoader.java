package io.github.abdurazaaqmohammed.adapters.main;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
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
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ApkIconHelper;
import io.github.abdurazaaqmohammed.utils.RootFile;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

public class FileIconLoader {

    private static final ExecutorService iconLoaderService = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Drawable> iconCache = new LruCache<>(100);
    private static final LruCache<String, Drawable> badgeCache = new LruCache<>(200);
    private static final LruCache<String, Long> mtimeCache = new LruCache<>(200);
    private static final LruCache<String, Long> sizeCache = new LruCache<>(200);
    private static final Handler badgeHandler = new Handler(Looper.getMainLooper());

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
        ensureCachedIcons(context.getResources(), context.theme);
    }

    public static Drawable getCachedApkIcon() {
        return cachedApkIcon;
    }

    public void setupZipEntryView(ZipEntryInfo zipEntry, ImageView fileIconView, TextView fileDateView) {
        if (zipEntry == null) return;
        if (zipEntry.isDirectory() && "..".equals(zipEntry.getName())) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
            return;
        }
        if (zipEntry.isDirectory() && zipEntry.getFullPath() != null) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, zipEntry.getLastModified())).append(' ').append(FileSize.getHumanReadableFileSize(zipEntry.getSize())));
        } else if (zipEntry.isDirectory()) {
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
        if (treatAsFolder(file)) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            showFolderDate(file, fileDateView);
        } else {
            long len = 0;
            long mt = 0;
            try {
                len = file.length();
            } catch (Exception ignored) {}
            try {
                mt = file.lastModified();
            } catch (Exception ignored) {}
            if ((len > 0 || mt > 0) || isGhostFile(file) || !AccessManager.fileOpsOn(context)) {
                fileDateView.setTag(null);
                if (isGhostFile(file)) {
                    fileDateView.setVisibility(View.INVISIBLE);
                } else {
                    fileDateView.setVisibility(View.VISIBLE);
                    fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
                    fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, mt)).append(' ').append(FileSize.getHumanReadableFileSize(len)));
                }
            } else {
                showFileStatsAsync(file, fileDateView);
            }
            setupNonFolderIconView(file.getPath(), fileIconView);
        }
    }

    private void showFileStatsAsync(File file, TextView fileDateView) {
        String tag = file.getAbsolutePath();
        Long cachedSize = sizeCache.get(tag);
        Long cachedMtime = mtimeCache.get(tag);
        if (cachedSize != null && cachedMtime != null && (cachedSize > 0 || cachedMtime > 0)) {
            fileDateView.setTag(null);
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, cachedMtime)).append(' ').append(FileSize.getHumanReadableFileSize(cachedSize)));
            return;
        }
        fileDateView.setVisibility(View.INVISIBLE);
        fileDateView.setTag(tag);
        iconLoaderService.execute(() -> {
            if (!tag.equals(fileDateView.getTag())) return;
            long size = -1;
            long mtime = 0;
            try {
                size = AccessManager.getSize(context, tag);
            } catch (Exception ignored) {}
            try {
                mtime = AccessManager.getMtime(context, tag);
            } catch (Exception ignored) {}
            if ((size <= 0 && mtime <= 0) || !tag.equals(fileDateView.getTag())) return;
            sizeCache.put(tag, Math.max(0, size));
            mtimeCache.put(tag, Math.max(0, mtime));
            final long finalSize = Math.max(0, size);
            final long finalMtime = Math.max(0, mtime);
            context.runOnUiThread(() -> {
                if (tag.equals(fileDateView.getTag())) {
                    fileDateView.setVisibility(View.VISIBLE);
                    fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
                    fileDateView.setText(new StringBuilder(UiPrefs.formatDate(context, finalMtime)).append(' ').append(FileSize.getHumanReadableFileSize(finalSize)));
                }
            });
        });
    }

    private static boolean treatAsFolder(File file) {
        try {
            if (file instanceof ShizukuFile || (file instanceof RootFile && ((RootFile) file).hasRootStat()))
                return file.isDirectory();
            if (file.isDirectory()) return file.exists();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isGhostFile(File file) {
        try {
            if (file instanceof ShizukuFile || file instanceof RootFile) return false;
            return !file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    private void showFolderDate(File file, TextView fileDateView) {
        long mtime = 0;
        try {
            mtime = file.lastModified();
        } catch (Exception ignored) {}
        if (mtime > 0) {
            fileDateView.setTag(null);
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            fileDateView.setText(UiPrefs.formatDate(context, mtime));
            return;
        }
        if (!AccessManager.fileOpsOn(context)) {
            fileDateView.setTag(null);
            fileDateView.setVisibility(View.INVISIBLE);
            return;
        }
        String tag = file.getAbsolutePath();
        Long cached = mtimeCache.get(tag);
        if (cached != null && cached > 0) {
            fileDateView.setTag(null);
            fileDateView.setVisibility(View.VISIBLE);
            fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
            fileDateView.setText(UiPrefs.formatDate(context, cached));
            return;
        }
        fileDateView.setVisibility(View.INVISIBLE);
        fileDateView.setTag(tag);
        iconLoaderService.execute(() -> {
            if (!tag.equals(fileDateView.getTag())) return;
            long m = 0;
            try {
                m = AccessManager.getMtime(context, tag);
            } catch (Exception ignored) {}
            if (m <= 0 || !tag.equals(fileDateView.getTag())) return;
            mtimeCache.put(tag, m);
            final long finalM = m;
            context.runOnUiThread(() -> {
                if (tag.equals(fileDateView.getTag())) {
                    fileDateView.setVisibility(View.VISIBLE);
                    fileDateView.setTextSize(UiPrefs.dateSize(UiPrefs.getScale(context)));
                    fileDateView.setText(UiPrefs.formatDate(context, finalM));
                }
            });
        });
    }

    public void requestRowBadge(File file, ImageView badgeView, String listedDir) {
        if (file == null || badgeView == null || isInZip) return;
        String name = file.getName();
        if (name == null || name.isEmpty()) return;
        boolean folderBadge = isDataRoot(listedDir)
                && io.github.abdurazaaqmohammed.utils.RootManager.isPackageNameValid(name);
        String lower = name.toLowerCase(Locale.ROOT);
        boolean splitBadge = file.isFile()
                && (lower.endsWith(".apks") || lower.endsWith(".xapk")
                || lower.endsWith(".apkm") || lower.endsWith(".aspk"));
        if (!folderBadge && !splitBadge) return;
        String key = (folderBadge ? "pkg:" : "split:") + (folderBadge ? name : file.getPath());
        Drawable cached = badgeCache.get(key);
        if (cached != null) {
            badgeView.setImageDrawable(cached);
            badgeView.setVisibility(View.VISIBLE);
            return;
        }
        String tag = file.getAbsolutePath();
        badgeView.setTag(tag);
        badgeHandler.postDelayed(() -> {
            if (!tag.equals(badgeView.getTag())) return;
            iconLoaderService.execute(() -> {
                if (!tag.equals(badgeView.getTag())) return;
                try {
                    Drawable icon = folderBadge ? loadInstalledAppIcon(name)
                            : ApkIconHelper.getApkIcon(context, tag);
                    if (icon == null) return;
                    Drawable circled = circleDrawable(context.getResources(), icon);
                    if (circled == null) return;
                    badgeCache.put(key, circled);
                    context.runOnUiThread(() -> {
                        if (tag.equals(badgeView.getTag())) {
                            badgeView.setImageDrawable(circled);
                            badgeView.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (Exception ignored) {}
            });
        }, 350);
    }

    private Drawable loadInstalledAppIcon(String pkg) {
        try {
            return context.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isDataRoot(String path) {
        if (path == null || path.isEmpty()) return false;
        String p = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        if (p.endsWith("/Android/data") || p.endsWith("/Android/obb") || p.endsWith("/Android/media")) return true;
        if (p.equals("/data/data")) return true;
        String leaf = p.substring(p.lastIndexOf('/') + 1);
        String parent = p.substring(0, p.lastIndexOf('/'));
        if (!leaf.matches("[0-9]+")) return false;
        return parent.equals("/data/user") || parent.equals("/data/user_de");
    }

    private static Drawable circleDrawable(Resources res, Drawable d) {
        Bitmap bm = toBitmap(d);
        if (bm == null) return null;
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w <= 0 || h <= 0) return null;
        int size = Math.min(w, h);
        Bitmap square;
        try {
            square = Bitmap.createBitmap(bm, (w - size) / 2, (h - size) / 2, size, size);
        } catch (Exception e) {
            return null;
        }
        try {
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            c.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            c.drawBitmap(square, 0, 0, paint);
            return new BitmapDrawable(res, out);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap toBitmap(Drawable d) {
        if (d == null) return null;
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                if (b != null) return b;
            }
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            if (w <= 0) w = 192;
            if (h <= 0) h = 192;
            Bitmap bm = Bitmap.createBitmap(Math.min(w, 512), Math.min(h, 512), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, c.getWidth(), c.getHeight());
            d.draw(c);
            return bm;
        } catch (Exception e) {
            return null;
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

        if (ApkIconHelper.isApkLikeExt(ext)) {
            if (isSplitApkExt(ext)) {
                fileIconView.setImageDrawable(cachedArchiveIcon);
            } else {
                fileIconView.setImageDrawable(cachedApkIcon);
                if (!isInZip) loadApkIconAsync(path, fileIconView);
            }
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

    private static boolean isSplitApkExt(String extWithDot) {
        return ".apks".equals(extWithDot) || ".xapk".equals(extWithDot)
                || ".apkm".equals(extWithDot) || ".aspk".equals(extWithDot);
    }

    private void loadApkIconAsync(String path, ImageView fileIconView) {
        fileIconView.setTag(path);
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
            try {
                Drawable icon = ApkIconHelper.getApkIcon(context, path);
                if (icon != null) {
                    iconCache.put(path, icon);
                    context.runOnUiThread(() -> {
                        if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
                    });
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

    private static void ensureCachedIcons(Resources res, int theme) {
        float density = res.getDisplayMetrics().density;
        int bucket = (int) (density * 4);
        if (cachedIconTheme == theme && cachedIconBucket == bucket) return;
        cachedIconTheme = theme;
        cachedIconBucket = bucket;
        cachedFolderIcon  = badge(res, density, R.drawable.ic_folder_mt, 0xFF252525, false);
        cachedApkIcon     = badge(res, density, R.drawable.apk_document_24px, 0xFF2E7D32, true);
        cachedImageIcon   = badge(res, density, R.drawable.image_24px, 0xFF6A1B9A, true);
        cachedVideoIcon   = badge(res, density, R.drawable.video_24px, 0xFFC62828, true);
        cachedMusicIcon   = badge(res, density, R.drawable.music_24px, 0xFF00897B, true);
        cachedArscIcon    = badge(res, density, R.drawable.stacks_24px, 0xFF9A6A00, true);
        cachedDexIcon     = badge(res, density, R.drawable.code_24px, 0xFF549395, true);
        cachedArchiveIcon = badge(res, density, R.drawable.baseline_folder_zip_24, 0xFFE65100, true);
        cachedPdfIcon     = badge(res, density, R.drawable.pdf_24px, 0xFFAD1457, true);
        cachedTextIcon    = badge(res, density, R.drawable.baseline_text_snippet_24, 0xFF1565C0, true);
        cachedFileIcon    = badge(res, density, R.drawable.baseline_insert_drive_file_24,
                theme == R.style.Theme_MyApp_Light ? 0xFF616161 : 0xFF424242, true);
    }

    private static Drawable badge(Resources res, float density, int glyphId, int bgColor, boolean whiteGlyph) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6 * density);
        bg.setColor(bgColor);
        Drawable glyph = ResourcesCompat.getDrawable(res, glyphId, null);
        if (glyph != null) {
            glyph = glyph.mutate();
            if (whiteGlyph) DrawableCompat.setTint(glyph, Color.WHITE);
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
