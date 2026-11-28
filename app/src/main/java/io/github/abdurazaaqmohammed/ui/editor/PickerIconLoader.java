package io.github.abdurazaaqmohammed.ui.editor;

import io.github.abdurazaaqmohammed.feature.apk.ApkIconHelper;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FilenameUtils;

import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.ui.main.FileIconSpec;
import io.github.abdurazaaqmohammed.ui.viewer.SvgRenderer;
import io.github.abdurazaaqmohammed.util.text.FileSize;
import io.github.abdurazaaqmohammed.util.io.FileUtils;

public class PickerIconLoader {

    private static final ExecutorService iconLoaderService = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Drawable> iconCache = new LruCache<>(100);

    private static Drawable cachedUpFolderIcon, cachedFolderIcon, cachedApkIcon, cachedImageIcon, cachedVideoIcon,
            cachedMusicIcon, cachedArchiveIcon, cachedPdfIcon, cachedTextIcon, cachedFileIcon;
    private static boolean cachedNight;
    private static boolean cached3d;
    private static int cachedEpoch = -1;
    private static int cachedBucket = -1;
    private static volatile int iconThemeVersion;
    private static volatile Set<String> packArtKeys = Collections.emptySet();

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public PickerIconLoader(Context context) {
        this.context = context;
        ensureCachedIcons(context);
    }

    public void setupUpView(ImageView fileIconView, TextView fileDateView) {
        ensureCachedIcons(context);
        fileIconView.setTag(null);
        fileIconView.setImageDrawable(cachedUpFolderIcon);
        if (fileDateView != null) fileDateView.setVisibility(View.INVISIBLE);
    }

    public void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        ensureCachedIcons(context);
        fileIconView.setTag(file.getPath());
        if (!file.isDirectory()) {
            fileDateView.setVisibility(View.VISIBLE);
            SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH:mm", Locale.US);
            String formattedDate = sdf.format(new Date(file.lastModified()));
            setupNonFolderIconView(file.getPath(), fileIconView);
            fileDateView.setText(formattedDate + ' ' + FileSize.getHumanReadableFileSize(file.length()));
        } else {
            fileIconView.setTag(null);
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
        }
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView) {
        fileIconView.setTag(null);
        Drawable cached = iconCache.get(path);
        if (cached != null) {
            fileIconView.setImageDrawable(cached);
            return;
        }
        String ext = FilenameUtils.getExtension(path);
        ext = ext == null ? "" : "." + ext.toLowerCase(Locale.ROOT);

        if (ApkIconHelper.isApkLikeExt(ext)) {
            fileIconView.setImageDrawable(cachedApkIcon);
            if (!packArtKeys.contains(FileIconSpec.APK.iconKey())) {
                loadApkIconAsync(path, fileIconView);
            }
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            fileIconView.setImageDrawable(cachedImageIcon);
            if (!packArtKeys.contains(FileIconSpec.IMAGE.iconKey())) {
                loadThumbnailAsync(path, fileIconView, false);
            }
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            fileIconView.setImageDrawable(cachedVideoIcon);
            if (!packArtKeys.contains(FileIconSpec.VIDEO.iconKey())) {
                loadThumbnailAsync(path, fileIconView, true);
            }
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            fileIconView.setImageDrawable(cachedMusicIcon);
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            fileIconView.setImageDrawable(cachedArchiveIcon);
        } else if (".pdf".equals(ext)) {
            fileIconView.setImageDrawable(cachedPdfIcon);
        } else {
            FileIconSpec codeSpec = FileIconSpec.codeSpecForExt(ext);
            if (codeSpec != null) {
                fileIconView.setImageDrawable(codeSpec.drawable(context));
            } else {
                FileIconSpec resolved = FileIconSpec.forPath(path, false);
                if (resolved == FileIconSpec.TEXT) {
                    fileIconView.setImageDrawable(cachedTextIcon);
                } else {
                    fileIconView.setImageDrawable(resolved == FileIconSpec.FILE ? cachedFileIcon : resolved.drawable(context));
                }
            }
        }
    }

    private void loadApkIconAsync(String path, ImageView fileIconView) {
        fileIconView.setTag(path);
        int themeAtStart = iconThemeVersion;
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
            try {
                Drawable icon = ApkIconHelper.getApkIcon(context, path);
                if (icon != null && iconThemeVersion == themeAtStart) {
                    iconCache.put(path, icon);
                    handler.post(() -> {
                        if (iconThemeVersion != themeAtStart) return;
                        if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void loadThumbnailAsync(String path, ImageView fileIconView, boolean isVideo) {
        fileIconView.setTag(path);
        int themeAtStart = iconThemeVersion;
        iconLoaderService.execute(() -> {
            if (!path.equals(fileIconView.getTag())) return;
            Bitmap bitmap = isVideo ? loadVideoThumbnail(path) : loadImageThumbnail(path);
            if (bitmap == null || !path.equals(fileIconView.getTag()) || iconThemeVersion != themeAtStart) return;
            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
            iconCache.put(path, icon);
            handler.post(() -> {
                if (iconThemeVersion != themeAtStart) return;
                if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
            });
        });
    }

    private static void ensureCachedIcons(Context context) {
        Resources res = context.getResources();
        boolean night = ThemeRegistry.isDarkTheme(context);
        boolean use3d = io.github.abdurazaaqmohammed.data.prefs.UiPrefs.use3dIcons(context);
        int epoch = ThemeRegistry.currentEpoch();
        int bucket = (int) (res.getDisplayMetrics().density * 4);
        if (cachedNight == night && cached3d == use3d && cachedEpoch == epoch
                && cachedBucket == bucket && cachedFolderIcon != null) {
            return;
        }
        cachedNight = night;
        cached3d = use3d;
        cachedEpoch = epoch;
        cachedBucket = bucket;
        iconCache.evictAll();
        cachedUpFolderIcon = FileIconSpec.parentDrawable(context);
        cachedFolderIcon = FileIconSpec.FOLDER.drawable(context);
        cachedApkIcon = FileIconSpec.APK.drawable(context);
        cachedImageIcon = FileIconSpec.IMAGE.drawable(context);
        cachedVideoIcon = FileIconSpec.VIDEO.drawable(context);
        cachedMusicIcon = FileIconSpec.AUDIO.drawable(context);
        cachedArchiveIcon = FileIconSpec.ARCHIVE.drawable(context);
        cachedPdfIcon = FileIconSpec.PDF.drawable(context);
        cachedTextIcon = FileIconSpec.TEXT.drawable(context);
        cachedFileIcon = FileIconSpec.FILE.drawable(context);
        Set<String> art = new HashSet<>(3);
        for (FileIconSpec spec : new FileIconSpec[]{FileIconSpec.APK, FileIconSpec.IMAGE, FileIconSpec.VIDEO}) {
            if (FileIconSpec.packIconFile(context, spec.iconKey()) != null) art.add(spec.iconKey());
        }
        packArtKeys = Collections.unmodifiableSet(art);
        iconThemeVersion++;
    }

    private static String resolveAccessiblePath(String path) {
        if (path == null) return null;
        if (path.startsWith("/data/media/0/")) {
            return "/storage/emulated/0/" + path.substring("/data/media/0/".length());
        } else if (path.startsWith("/data/media/")) {
            return "/storage/emulated/" + path.substring("/data/media/".length());
        }
        return path;
    }

    private static Bitmap loadImageThumbnail(String path) {
        String resolvedPath = resolveAccessiblePath(path);
        Bitmap bitmap = decodeBitmapFromFile(resolvedPath);
        if (bitmap == null && !resolvedPath.equals(path)) {
            bitmap = decodeBitmapFromFile(path);
        }
        return bitmap;
    }

    private static Bitmap decodeBitmapFromFile(String filePath) {
        try {
            File f = new File(filePath);
            if (!f.canRead()) return null;
            if (SvgRenderer.isSvgName(filePath)) {
                return SvgRenderer.decodeThumbnail(filePath, 160);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            int width = options.outWidth;
            int height = options.outHeight;
            if (width <= 0 || height <= 0) return null;

            int scale = 1;
            while (width / 2 >= 128 && height / 2 >= 128) {
                width /= 2;
                height /= 2;
                scale *= 2;
            }
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(filePath, options);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap loadVideoThumbnail(String path) {
        String resolvedPath = resolveAccessiblePath(path);
        try {
            Bitmap b = ThumbnailUtils.createVideoThumbnail(resolvedPath, MediaStore.Video.Thumbnails.MINI_KIND);
            if (b != null) return b;
        } catch (Throwable ignored) {}
        if (!resolvedPath.equals(path)) {
            try {
                return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            } catch (Throwable ignored) {}
        }
        return null;
    }
}