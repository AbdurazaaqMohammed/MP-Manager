package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.utils.SafFs;
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
import android.util.LruCache;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.data.prefs.DirSizeCache;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.drawable.ApkDrawableRenderer;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ApkIconHelper;
import io.github.abdurazaaqmohammed.utils.RootFile;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.StickerUtil;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

public class FileIconLoader {

    private static final ExecutorService iconLoaderService = Executors.newFixedThreadPool(4);
    /**
     * Backend (root/Shizuku) stat lookups run here instead of on the icon pool.
     * Each one may spawn a shell process, and sharing the pool let a listing of
     * stat requests starve real icon work.
     */
    private static final ExecutorService backendStatService = Executors.newFixedThreadPool(2);
    /**
     * Folder-size walks run alone, delayed and at minimum priority. A recursive
     * walk of a big tree hammers storage; racing it against listing and icon
     * I/O on cold start is what made opening the app feel slow.
     */
    private static final ScheduledExecutorService folderSizeService =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "folder-size");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
    private static final Object STATS_LOCK = new Object();
    private static final SyncCache<Drawable> iconCache = new SyncCache<>(300);
    private static final SyncCache<Drawable> badgeCache = new SyncCache<>(500);
    /** Rendered thumbnails for entries inside archives (images + drawable XMLs). */
    private static final SyncCache<Drawable> zipThumbCache = new SyncCache<>(400);
    private static final SyncCache<String> pkgNameCache = new SyncCache<>(500);
    private static final SyncCache<Long> mtimeCache = new SyncCache<>(400);
    private static final SyncCache<Long> sizeCache = new SyncCache<>(400);
    private static final SyncCache<Boolean> elfStatusCache = new SyncCache<>(1000);
    private static final Set<String> pendingStats = new HashSet<>();

    /** android.util.LruCache is not thread safe and rows bind on the UI thread
     *  while loaders fill it from background threads, so every access is guarded. */
    private static final class SyncCache<V> {
        private final LruCache<String, V> cache;
        SyncCache(int maxEntries) {
            cache = new LruCache<>(maxEntries);
        }
        V get(String key) {
            synchronized (cache) {
                return cache.get(key);
            }
        }
        void put(String key, V value) {
            synchronized (cache) {
                cache.put(key, value);
            }
        }
    }
    private static final Handler badgeHandler = new Handler(Looper.getMainLooper());

    private static Drawable cachedFolderIcon, cachedApkIcon, cachedImageIcon, cachedVideoIcon,
            cachedDexIcon, cachedArscIcon,
            cachedMusicIcon, cachedArchiveIcon, cachedPdfIcon, cachedTextIcon, cachedFileIcon, cachedStickerIcon, cachedElfIcon, cachedImgIcon,
            cachedDbIcon, cachedFontIcon, cachedBinaryIcon, cachedProtoIcon, cachedDocIcon, cachedSheetIcon, cachedSlideIcon, cachedScriptIcon, cachedBackupIcon, cachedCertIcon;
    private static int cachedIconTheme = -1;
    private static int cachedIconBucket = -1;

    private final MainActivity context;
    private final boolean isInZip;

    /**
     * Row metrics snapshotted once per listing (one loader instance serves one
     * adapter). Reading prefs and resolving the active backend used to happen on
     * every bind, which made big folders stutter while scrolling.
     */
    private final float dateTextSize;
    private final int dateTextSizePx;
    private final String datePattern;
    private final boolean elevatedFileOps;
    /** always: recursive sizes everywhere; archive: zip metadata only; never: no sizes. */
    private final String folderSizeMode;
    /** Per-listing memo of [isDir, length, mtime] so rebinds do not re-stat. */
    private final Map<String, long[]> metaMemo = new ConcurrentHashMap<>();
    /** Per-listing memo of resolved icons, so classification runs once per path. */
    private final Map<String, Drawable> iconMemo = new ConcurrentHashMap<>();
    /**
     * Ceiling on backend stat lookups for one listing. A listing normally has
     * the metadata already (RootFile caches it, ShizukuFile now parses it out of
     * {@code ls -l}, plain Files use a local stat), so anything beyond a handful
     * means a backend we cannot get data out of quickly. Rather than queue
     * hundreds of shell calls and leave dates blank for seconds, give up and
     * leave the row without a date.
     */
    private static final int MAX_BACKEND_STATS_PER_LISTING = 48;
    private final AtomicInteger backendStatBudget =
            new AtomicInteger(MAX_BACKEND_STATS_PER_LISTING);

    private boolean claimBackendStatSlot(String tag) {
        if (backendStatBudget.getAndDecrement() <= 0) {
            // Remember the refusal so rebinding this row does not retry.
            synchronized (STATS_LOCK) {
                sizeCache.put(tag, -1L);
                mtimeCache.put(tag, 0L);
            }
            return false;
        }
        return true;
    }

    public FileIconLoader(MainActivity context, boolean isInZip) {
        this(context, isInZip, false);
    }

    public FileIconLoader(MainActivity context, boolean isInZip, boolean metadataOnly) {
        this.context = context;
        this.isInZip = isInZip;
        this.dateTextSize = UiPrefs.dateSize(UiPrefs.getScale(context));
        this.dateTextSizePx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                this.dateTextSize, context.getResources().getDisplayMetrics()));
        this.datePattern = UiPrefs.getDatePattern(context);
        String mode;
        try {
            mode = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("pref_folder_size_mode", "archive");
        } catch (Exception e) {
            mode = "archive";
        }
        this.folderSizeMode = mode;
        boolean elevated = false;
        try {
            elevated = !metadataOnly && AccessManager.fileOpsOn(context);
        } catch (Exception ignored) {
        }
        this.elevatedFileOps = elevated;
        ensureCachedIcons(context.getResources(), context.theme);
    }

    public static Drawable getCachedApkIcon() {
        return cachedApkIcon;
    }

    /** Listing metadata only: no thumbnails, content sniffing, shell stats or folder walks. */
    public void setupMetadataView(String name, boolean directory, long size, long modified,
                                  boolean up, ImageView icon, TextView detail) {
        icon.setTag(null);
        detail.setTag(null);
        icon.setImageDrawable(directory || up ? cachedFolderIcon : resolveNonFolderIcon(name, icon, false));
        applyDateTextSize(detail);
        if (up) {
            detail.setText("");
            detail.setVisibility(View.GONE);
        } else if (directory && modified <= 0) {
            detail.setText("");
            detail.setVisibility(View.INVISIBLE);
        } else {
            detail.setVisibility(View.VISIBLE);
            String date = modified > 0 || !directory ? UiPrefs.formatDate(datePattern, modified) : "";
            detail.setText(directory ? date : date + " " + FileSize.getHumanReadableFileSize(size));
        }
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
            applyDateTextSize(fileDateView);
            StringBuilder sb = new StringBuilder(UiPrefs.formatDate(datePattern, zipEntry.getLastModified()));
            if (!"never".equals(folderSizeMode)) {
                sb.append(' ').append(FileSize.getHumanReadableFileSize(zipEntry.getSize()));
            }
            fileDateView.setText(sb);
        } else if (zipEntry.isDirectory()) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            fileDateView.setVisibility(View.INVISIBLE);
        } else {
            setupZipEntryNonFolderIconView(zipEntry, fileIconView);
            // Replaces the static type icon with the real picture / rendered
            // drawable once the entry is decoded (kept as-is on failure).
            setupZipThumbnailAsync(zipEntry, fileIconView);
            fileDateView.setVisibility(View.VISIBLE);
            applyDateTextSize(fileDateView);
            StringBuilder sb = new StringBuilder(UiPrefs.formatDate(datePattern, zipEntry.getLastModified()));
            if (!"never".equals(folderSizeMode)) {
                sb.append(' ').append(FileSize.getHumanReadableFileSize(zipEntry.getSize()));
            }
            fileDateView.setText(sb);
        }
    }

    /** Cap so a giant archive entry is never decoded into memory for a row icon. */
    private static final long MAX_ZIP_THUMB_BYTES = 5 * 1024 * 1024;

    /** Images, plus drawable/mipmap XMLs (vector & shape drawables render like pictures). */
    private static boolean isZipThumbnailCandidate(String fullPath) {
        if (fullPath == null || fullPath.endsWith("/")) return false;
        String lower = fullPath.toLowerCase(Locale.ROOT);
        String ext = FilenameUtils.getExtension(lower);
        String dot = ext == null || ext.isEmpty() ? "" : "." + ext;
        if (FileUtils.matchExt(dot, FileUtils.IMAGE_EXTS)) return true;
        return ApkDrawableRenderer.isDrawableXmlPath(fullPath);
    }

    /** Row icons are small; rendering at 3x keeps them crisp without wasting memory. */
    private static final int THUMB_RENDER_PX = 96;

    /**
     * Loads a real thumbnail for an archive entry: images are decoded straight
     * from the entry stream; drawable XMLs (binary AXML inside APKs or plain
     * text XML) are inflated into a Drawable and rendered. Results are cached
     * per archive+entry+archive-mtime, including the fallback icon on failure,
     * so scrolling never re-decodes.
     */
    public void setupZipThumbnailAsync(ZipEntryInfo zipEntry, ImageView fileIconView) {
        if (zipEntry == null || fileIconView == null) return;
        String fullPath = zipEntry.getFullPath();
        File zip = zipEntry.getZipFile();
        if (fullPath == null || zip == null || !isZipThumbnailCandidate(fullPath)) {
            fileIconView.setTag(null);
            return;
        }
        if (zipEntry.getSize() <= 0 || zipEntry.getSize() > MAX_ZIP_THUMB_BYTES) {
            fileIconView.setTag(null);
            return;
        }
        String key = "zip:" + zip.getAbsolutePath() + "!" + fullPath + ":" + zip.lastModified();
        Drawable cached = zipThumbCache.get(key);
        if (cached != null) {
            fileIconView.setTag(null);
            fileIconView.setImageDrawable(cached);
            return;
        }
        fileIconView.setTag(key);
        iconLoaderService.execute(() -> {
            if (!key.equals(fileIconView.getTag())) return;
            Drawable result = loadZipEntryDrawable(zip, fullPath);
            if (result == null) result = cachedImageIcon;
            final Drawable finalResult = result;
            zipThumbCache.put(key, finalResult);
            context.runOnUiThread(() -> {
                if (key.equals(fileIconView.getTag())) fileIconView.setImageDrawable(finalResult);
            });
        });
    }

    private Drawable loadZipEntryDrawable(File zip, String fullPath) {
        String lower = fullPath.toLowerCase(Locale.ROOT);
        String ext = "." + FilenameUtils.getExtension(lower);
        try {
            if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
                Bitmap bm = decodeZipBitmap(zip, fullPath);
                return bm != null ? new BitmapDrawable(context.getResources(), bm) : null;
            }
            // Compiled (binary AXML) drawables must be inflated through the APK's
            // own resource table - see ApkDrawableRenderer for why the old
            // "decode to text + Drawable.createFromXml" approach never worked.
            ApkDrawableRenderer.Result result =
                    ApkDrawableRenderer.render(context, zip, fullPath, THUMB_RENDER_PX);
            Bitmap rendered = ApkDrawableRenderer.rasterize(result, null, 0, THUMB_RENDER_PX);
            if (rendered == null) return null;
            // A checkerboard keeps white/black artwork visible in every theme.
            Bitmap composited = ApkDrawableRenderer.onCheckerboard(rendered,
                    Math.max(6, rendered.getWidth() / 8));
            return new BitmapDrawable(context.getResources(), composited);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readZipEntry(File zip, String entryPath) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = ArchiveBrowseUtil.openEntry(zip, entryPath)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (baos.size() + n > MAX_ZIP_THUMB_BYTES) return null;
                baos.write(buf, 0, n);
            }
        } catch (Throwable t) {
            return null;
        }
        return baos.toByteArray();
    }

    private static Bitmap decodeZipBitmap(File zip, String entryPath) {
        try (InputStream in = ArchiveBrowseUtil.openEntry(zip, entryPath)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            int width = options.outWidth;
            int height = options.outHeight;
            int scale = 1;
            while (width / 2 >= 128 && height / 2 >= 128) {
                width /= 2;
                height /= 2;
                scale *= 2;
            }
            try (InputStream in2 = ArchiveBrowseUtil.openEntry(zip, entryPath)) {
                options.inSampleSize = scale;
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeStream(in2, null, options);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public void setupFileView(File file, ImageView fileIconView, TextView fileDateView) {
        long[] meta = metaOf(file);
        if (meta[0] == 1) {
            fileIconView.setImageDrawable(cachedFolderIcon);
            showFolderDate(file, fileDateView, meta);
        } else {
            long len = meta[1];
            long mt = meta[2];
            boolean ghost = len <= 0 && mt <= 0 && isGhostFile(file);
            if ((len > 0 || mt > 0) || ghost || !elevatedFileOps) {
                fileDateView.setTag(null);
                if (ghost) {
                    fileDateView.setVisibility(View.INVISIBLE);
                } else {
                    showFileStats(fileDateView, len, mt);
                }
            } else {
                showFileStatsAsync(file, fileDateView);
            }
            setupNonFolderIconView(file.getPath(), fileIconView);
        }
    }

    private void showFileStats(TextView view, long size, long mtime) {
        view.setVisibility(View.VISIBLE);
        applyDateTextSize(view);
        view.setText(new StringBuilder(UiPrefs.formatDate(datePattern, mtime))
                .append(' ').append(FileSize.getHumanReadableFileSize(size)));
    }

    /**
     * TextView.setTextSize() re-measures and requests a layout even when the size
     * is unchanged. Called per bound row that made every bind cost a layout pass,
     * which is what made big folders stutter while scrolling.
     */
    private void applyDateTextSize(TextView view) {
        if (view.getTextSize() != dateTextSizePx) view.setTextSize(dateTextSize);
    }

    /**
     * isDir/size/mtime for a listing entry, memoized per listing. Every rebind
     * of a plain {@link File} otherwise costs three syscalls, which is what made
     * large system folders such as /vendor stutter while scrolling.
     */
    private long[] metaOf(File file) {
        String key;
        try {
            key = file.getAbsolutePath();
        } catch (Exception e) {
            return statDirect(file);
        }
        long[] memo = metaMemo.get(key);
        if (memo != null) return memo;
        long[] fresh = statDirect(file);
        metaMemo.put(key, fresh);
        return fresh;
    }

    private static long[] statDirect(File file) {
        long[] meta = new long[3];
        meta[0] = treatAsFolder(file) ? 1 : 0;
        // Read the date for folders too. Leaving it at 0 made every folder row
        // take the backend path and spawn a shell stat, which was far slower
        // than the local stat that already answers this.
        try {
            meta[2] = file.lastModified();
        } catch (Exception ignored) {
        }
        if (meta[0] == 0) {
            try {
                meta[1] = file.length();
            } catch (Exception ignored) {
            }
        }
        return meta;
    }

    private void showFileStatsAsync(File file, TextView fileDateView) {
        String tag = file.getAbsolutePath();
        Long cachedSize = sizeCache.get(tag);
        Long cachedMtime = mtimeCache.get(tag);
        if (cachedSize != null && cachedMtime != null) {
            fileDateView.setTag(null);
            if (cachedSize > 0 || cachedMtime > 0) showFileStats(fileDateView, cachedSize, cachedMtime);
            else fileDateView.setVisibility(View.INVISIBLE);
            return;
        }
        fileDateView.setVisibility(View.INVISIBLE);
        fileDateView.setTag(tag);
        if (!claimBackendStatSlot(tag)) return;
        synchronized (STATS_LOCK) {
            // A shell stat per row per bind is what made /vendor crawl, so only
            // one lookup per path may be in flight and failures are cached too.
            if (!pendingStats.add(tag)) return;
        }
        backendStatService.execute(() -> {
            if (!tag.equals(fileDateView.getTag())) {
                releasePending(tag);
                return;
            }
            long size = -1;
            long mtime = 0;
            try {
                size = AccessManager.getSize(context, tag);
            } catch (Exception ignored) {}
            try {
                mtime = AccessManager.getMtime(context, tag);
            } catch (Exception ignored) {}
            releasePending(tag);
            sizeCache.put(tag, size);
            mtimeCache.put(tag, mtime);
            if (!tag.equals(fileDateView.getTag())) return;
            // Memoize the negative result too. Empty files stat as 0/0, and
            // without this every rebind of an empty root-only row asked the
            // backend again, spending two shell spawns each time.
            long[] memo = new long[]{0, Math.max(0, size), Math.max(0, mtime)};
            metaMemo.put(tag, memo);
            if (size <= 0 && mtime <= 0) return;
            final long finalSize = memo[1];
            final long finalMtime = memo[2];
            context.runOnUiThread(() -> {
                if (tag.equals(fileDateView.getTag())) showFileStats(fileDateView, finalSize, finalMtime);
            });
        });
    }

    private static void releasePending(String tag) {
        synchronized (STATS_LOCK) {
            pendingStats.remove(tag);
        }
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

    private void showFolderDate(File file, TextView fileDateView, long[] meta) {
        long mtime = meta[2];
        int sizeMode = UiPrefs.folderSizeMode(context);
        if (sizeMode == UiPrefs.FOLDER_SIZE_NONE) {
            // "Never": date-only row
            fileDateView.setTag(null);
            if (mtime > 0) {
                fileDateView.setVisibility(View.VISIBLE);
                applyDateTextSize(fileDateView);
                fileDateView.setText(UiPrefs.formatDate(datePattern, mtime));
                return;
            }
        } else if (mtime > 0) {
            fileDateView.setTag(null);
            showFolderSizeAndDate(file, fileDateView, UiPrefs.formatDate(datePattern, mtime));
            return;
        }

        String tag = file.getAbsolutePath();
        Long cached = mtimeCache.get(tag);
        if (cached != null && cached > 0) {
            fileDateView.setTag(null);
            if (sizeMode == UiPrefs.FOLDER_SIZE_NONE) {
                fileDateView.setVisibility(View.VISIBLE);
                applyDateTextSize(fileDateView);
                fileDateView.setText(UiPrefs.formatDate(datePattern, cached));
            } else {
                showFolderSizeAndDate(file, fileDateView, UiPrefs.formatDate(datePattern, cached));
            }
            return;
        }

        boolean canElevate = elevatedFileOps
                || (context != null && RootManager.getInstance(context).isRootAvailable())
                || io.github.abdurazaaqmohammed.utils.ShizukuManager.ready();

        if (!canElevate) {
            fileDateView.setTag(null);
            fileDateView.setVisibility(View.INVISIBLE);
            return;
        }

        fileDateView.setVisibility(View.INVISIBLE);
        fileDateView.setTag(tag);
        if (!claimBackendStatSlot(tag)) return;
        synchronized (STATS_LOCK) {
            if (!pendingStats.add(tag)) return;
        }
        backendStatService.execute(() -> {
            if (!tag.equals(fileDateView.getTag())) {
                releasePending(tag);
                return;
            }
            long m = 0;
            try {
                m = AccessManager.getMtime(context, tag);
            } catch (Exception ignored) {}
            releasePending(tag);
            mtimeCache.put(tag, m);
            if (m <= 0 || !tag.equals(fileDateView.getTag())) return;
            long[] memo = metaMemo.get(tag);
            if (memo != null) memo[2] = m;
            final long finalM = m;
            context.runOnUiThread(() -> {
                if (tag.equals(fileDateView.getTag())) {
                    if (sizeMode == UiPrefs.FOLDER_SIZE_NONE) {
                        fileDateView.setVisibility(View.VISIBLE);
                        applyDateTextSize(fileDateView);
                        fileDateView.setText(UiPrefs.formatDate(datePattern, finalM));
                    } else {
                        showFolderSizeAndDate(file, fileDateView, UiPrefs.formatDate(datePattern, finalM));
                    }
                }
            });
        });
    }

    public static boolean isUserStorage(String path) {
        if (path == null) return false;
        return path.startsWith("/storage/")
                || path.startsWith("/sdcard")
                || path.startsWith("/mnt/media_rw/")
                || path.startsWith("/mnt/sdcard")
                || path.equals("/storage")
                || path.equals("/sdcard");
    }

    private void showFolderSizeAndDate(File file, TextView fileDateView, String dateStr) {
        fileDateView.setVisibility(View.VISIBLE);
        applyDateTextSize(fileDateView);
        if (file == null) {
            fileDateView.setText(dateStr);
            return;
        }
        int mode = UiPrefs.folderSizeMode(context);
        if (mode == UiPrefs.FOLDER_SIZE_NONE) {
            fileDateView.setText(dateStr);
            return;
        }
        String path = file.getAbsolutePath();
        if (mode == UiPrefs.FOLDER_SIZE_USER && !isUserStorage(path)) {
            fileDateView.setText(dateStr);
            return;
        }
        Long cached = DirSizeCache.peek(file);
        if (cached != null) {
            fileDateView.setText(new StringBuilder(dateStr).append(' ').append(FileSize.getHumanReadableFileSize(cached)));
            return;
        }
        fileDateView.setText(dateStr);
        if (!DirSizeCache.isReady()) return; // preload in flight; rows rebind and pick it up
        String tag = path;
        fileDateView.setTag(tag);
        folderSizeService.schedule(() -> {
            if (!tag.equals(fileDateView.getTag())) return;
            long size = calculateFolderSize(file, context);
            if (size < 0) return; // walk aborted; leave uncached so it can retry later
            DirSizeCache.record(file, size);
            DirSizeCache.scheduleSave();
            context.runOnUiThread(() -> {
                if (tag.equals(fileDateView.getTag()) && size >= 0) {
                    fileDateView.setText(new StringBuilder(dateStr).append(' ').append(FileSize.getHumanReadableFileSize(size)));
                }
            });
        }, SIZE_WALK_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Delay before a folder-size walk starts, so listing and icons go first. */
    private static final long SIZE_WALK_DELAY_MS = 500;
    private static final int SIZE_WALK_MAX_ENTRIES = 20000;
    private static final long SIZE_WALK_MAX_TIME_MS = 4000;

    /**
     * Iterative walk with entry and time budgets. A whole-subtree scan of a big
     * storage root could otherwise run for minutes; an aborted walk returns -1
     * and is not cached, so it can be retried later. Outside user storage the
     * backend (root/Shizuku) {@code du} is tried first, since those trees are
     * not readable by the app user at all.
     */
    private static long calculateFolderSize(File dir, Context ctx) {
        if (dir == null || !treatAsFolder(dir)) return 0;
        String path = dir.getAbsolutePath();
        if (path.equals("/proc") || path.startsWith("/proc/")
                || path.equals("/sys") || path.startsWith("/sys/")
                || path.equals("/dev") || path.startsWith("/dev/")
                || path.equals("/acct") || path.startsWith("/acct/")
                || path.equals("/config") || path.startsWith("/config/")
                || path.equals("/d") || path.startsWith("/d/")
                || path.equals("/linkerconfig") || path.startsWith("/linkerconfig/")
                || path.equals("/data_mirror") || path.startsWith("/data_mirror/")) {
            return 0;
        }
        if (!isUserStorage(path)) {
            try {
                java.util.Map<String, Long> tree = AccessManager.dirSizeTree(ctx, path);
                if (tree != null && !tree.isEmpty()) {
                    DirSizeCache.recordBatch(tree);
                    Long selfSize = tree.get(path);
                    if (selfSize != null && selfSize >= 0) return selfSize;
                }
                long du = AccessManager.dirSize(ctx, path);
                if (du >= 0) return du;
            } catch (Exception ignored) {}
            // Outside user storage, if directory is not readable by normal Java IO,
            // never run stack.push(dir) which would result in listFiles() returning null
            // and wrongly returning 0 (cached as 0B).
            if (!dir.canRead()) return -1;
        }
        long deadline = System.currentTimeMillis() + SIZE_WALK_MAX_TIME_MS;
        ArrayDeque<File> stack = new ArrayDeque<>();
        stack.push(dir);
        long total = 0;
        int visited = 0;
        boolean rootChecked = false;
        while (!stack.isEmpty()) {
            if (++visited > SIZE_WALK_MAX_ENTRIES || System.currentTimeMillis() > deadline) return -1;
            File f = stack.pop();
            File[] children;
            try {
                children = f.listFiles();
            } catch (Exception e) {
                children = null;
            }
            if (!rootChecked) {
                rootChecked = true;
                if (children == null) return -1; // Unreadable root directory should never be treated as 0B
            }
            if (children == null) continue;
            for (File c : children) {
                if (c == null) continue;
                if (c.isDirectory()) {
                    stack.push(c);
                } else {
                    total += c.length();
                }
            }
        }
        return total;
    }

    private static boolean isDataAppRoot(String path) {
        if (path == null || path.isEmpty()) return false;
        String p = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return p.equals("/data/app");
    }

    private static boolean isInsideDataApp(String path) {
        if (path == null || path.isEmpty()) return false;
        String p = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return p.startsWith("/data/app/");
    }

    private String resolvePackageName(File file, String listedDir) {
        String name = file.getName();
        if (name == null || name.isEmpty()) return null;
        if (isDataRoot(listedDir)) {
            return RootManager.isPackageNameValid(name) ? name : null;
        }
        if (isDataAppRoot(listedDir)) {
            // Android 8+: /data/app/~~.../com.pkg-... or legacy /data/app/com.pkg-...
            int dash = name.indexOf('-');
            String possiblePkg = dash > 0 ? name.substring(0, dash) : name;
            if (RootManager.isPackageNameValid(possiblePkg)) {
                return possiblePkg;
            }
            if (file.isDirectory()) {
                File[] children = null;
                try {
                    children = file.listFiles();
                } catch (Exception ignored) {}
                if (children != null && children.length > 0) {
                    for (File c : children) {
                        String cn = c.getName();
                        int cDash = cn.indexOf('-');
                        String cPkg = cDash > 0 ? cn.substring(0, cDash) : cn;
                        if (RootManager.isPackageNameValid(cPkg)) {
                            return cPkg;
                        }
                    }
                } else if (AccessManager.fileOpsOn(context)) {
                    // Try fast shell lookup if unreadable by normal user
                    try {
                        RootManager.ShellResult r =
                                RootManager.getInstance(context)
                                        .executeFs("ls -1 -- " + RootManager.escapeShellArg(file.getAbsolutePath()) + " 2>/dev/null | head -n 5", 5);
                        if (r != null && r.isSuccess() && r.output() != null) {
                            for (String line : r.output().split("\\r?\\n")) {
                                String cn = line.trim();
                                int cDash = cn.indexOf('-');
                                String cPkg = cDash > 0 ? cn.substring(0, cDash) : cn;
                                if (RootManager.isPackageNameValid(cPkg)) {
                                    return cPkg;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } else if (isInsideDataApp(listedDir)) {
            // Inside ~~...: folders like com.example.app-hash
            int dash = name.indexOf('-');
            String possiblePkg = dash > 0 ? name.substring(0, dash) : name;
            if (RootManager.isPackageNameValid(possiblePkg)) {
                return possiblePkg;
            }
        }
        return null;
    }

    public void requestRowBadge(File file, ImageView badgeView, String listedDir) {
        if (file == null || badgeView == null || isInZip) return;
        String name = file.getName();
        if (name == null || name.isEmpty()) return;
        String tag = file.getAbsolutePath();

        // 1. Check split APK candidates (.apks, .xapk, etc.) first
        String lower = name.toLowerCase(Locale.ROOT);
        boolean splitCandidate = lower.endsWith(".apks") || lower.endsWith(".xapk")
                || lower.endsWith(".apkm") || lower.endsWith(".aspk");
        if (splitCandidate) {
            String key = "split:" + tag;
            Drawable cached = badgeCache.get(key);
            if (cached != null) {
                badgeView.setImageDrawable(cached);
                badgeView.setVisibility(View.VISIBLE);
                return;
            }
        }

        // 2. Fast in-memory check for cached package
        String cachedPkg = pkgNameCache.get(tag);
        if (cachedPkg != null) {
            if (!cachedPkg.isEmpty()) {
                String key = "pkg:" + cachedPkg;
                Drawable cached = badgeCache.get(key);
                if (cached != null) {
                    badgeView.setImageDrawable(cached);
                    badgeView.setVisibility(View.VISIBLE);
                    return;
                }
            } else if (!splitCandidate) {
                return;
            }
        }

        boolean maybeApp = isDataRoot(listedDir) || isDataAppRoot(listedDir) || isInsideDataApp(listedDir);
        if (!maybeApp && !splitCandidate) return;

        badgeView.setTag(tag);
        badgeHandler.postDelayed(() -> {
            if (!tag.equals(badgeView.getTag())) return;
            iconLoaderService.execute(() -> {
                if (!tag.equals(badgeView.getTag())) return;
                try {
                    String targetPkg = pkgNameCache.get(tag);
                    if (targetPkg == null) {
                        targetPkg = resolvePackageName(file, listedDir);
                        pkgNameCache.put(tag, targetPkg != null ? targetPkg : "");
                    }
                    if (targetPkg == null || targetPkg.isEmpty()) {
                        if (splitCandidate && file.isFile()) {
                            String key = "split:" + tag;
                            Drawable icon = badgeCache.get(key);
                            if (icon == null) {
                                Drawable raw = ApkIconHelper.getApkIcon(context, tag);
                                if (raw != null) {
                                    icon = circleDrawable(context.getResources(), raw);
                                    if (icon != null) badgeCache.put(key, icon);
                                }
                            }
                            if (icon != null) {
                                final Drawable finalIcon = icon;
                                context.runOnUiThread(() -> {
                                    if (tag.equals(badgeView.getTag())) {
                                        badgeView.setImageDrawable(finalIcon);
                                        badgeView.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                        }
                        return;
                    }

                    String key = "pkg:" + targetPkg;
                    Drawable icon = badgeCache.get(key);
                    if (icon == null) {
                        Drawable appIcon = loadInstalledAppIcon(targetPkg);
                        if (appIcon != null) {
                            icon = circleDrawable(context.getResources(), appIcon);
                            if (icon != null) badgeCache.put(key, icon);
                        }
                    }
                    if (icon != null) {
                        final Drawable finalIcon = icon;
                        context.runOnUiThread(() -> {
                            if (tag.equals(badgeView.getTag())) {
                                badgeView.setImageDrawable(finalIcon);
                                badgeView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }, 50);
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

    private void setupZipEntryNonFolderIconView(ZipEntryInfo zipEntry, ImageView fileIconView) {
        String path = zipEntry != null ? zipEntry.getFullPath() : null;
        if (path == null) {
            setupNonFolderIconView("", fileIconView);
            return;
        }
        Drawable memo = iconMemo.get(path);
        if (memo != null) {
            fileIconView.setImageDrawable(memo);
            return;
        }
        String ext = FilenameUtils.getExtension(path);
        if (ext != null) ext = "." + ext.toLowerCase(Locale.ROOT);
        else ext = "";

        if (FileUtils.isElf(path)) {
            iconMemo.put(path, cachedElfIcon);
            fileIconView.setImageDrawable(cachedElfIcon);
            return;
        }
        setupNonFolderIconView(path, fileIconView);
    }

    private void setupNonFolderIconView(String path, ImageView fileIconView) {
        if (!isInZip) {
            Drawable cached = iconCache.get(path);
            if (cached != null) {
                fileIconView.setImageDrawable(cached);
                return;
            }
        }
        fileIconView.setImageDrawable(resolveNonFolderIcon(path, fileIconView));
    }

    /**
     * Resolves the icon for a path. The result is memoized per listing: only
     * APKs and images ever produced a cache hit before, so every ordinary file
     * re-ran the extension classification on every bind.
     */
    private Drawable resolveNonFolderIcon(String path, ImageView fileIconView) {
        return resolveNonFolderIcon(path, fileIconView, !isInZip);
    }

    private Drawable resolveNonFolderIcon(String path, ImageView fileIconView, boolean loadContent) {
        Drawable memo = iconMemo.get(path);
        if (memo != null) return memo;
        String ext = FilenameUtils.getExtension(path);
        if (ext != null) ext = "." + ext.toLowerCase(Locale.ROOT);
        else ext = "";

        Drawable resolved;
        if (ApkIconHelper.isApkLikeExt(ext)) {
            if (isSplitApkExt(ext)) {
                resolved = cachedArchiveIcon;
            } else {
                resolved = cachedApkIcon;
                if (loadContent) loadApkIconAsync(path, fileIconView);
            }
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            resolved = cachedImageIcon;
            if (loadContent) loadThumbnailAsync(path, fileIconView, false);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            resolved = cachedVideoIcon;
            if (loadContent) loadThumbnailAsync(path, fileIconView, true);
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) {
            resolved = cachedMusicIcon;
        } else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) {
            resolved = cachedArchiveIcon;
        } else if (".pdf".equals(ext)) {
            resolved = cachedPdfIcon;
        } else if (".dex".equals(ext)) {
            resolved = cachedDexIcon;
        } else if (".arsc".equals(ext)) {
            resolved = cachedArscIcon;
        } else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) {
            resolved = cachedTextIcon;
        } else if (isDatabaseExt(ext)) {
            resolved = cachedDbIcon;
        } else if (isFontExt(ext)) {
            resolved = cachedFontIcon;
        } else if (isBinaryExt(ext)) {
            resolved = cachedBinaryIcon;
        } else if (isProtoExt(ext)) {
            resolved = cachedProtoIcon;
        } else if (isWordExt(ext)) {
            resolved = cachedDocIcon;
        } else if (isSheetExt(ext)) {
            resolved = cachedSheetIcon;
        } else if (isSlideExt(ext)) {
            resolved = cachedSlideIcon;
        } else if (isScriptExt(ext)) {
            resolved = cachedScriptIcon;
        } else if (isBackupExt(ext)) {
            resolved = cachedBackupIcon;
        } else if (isCertExt(ext)) {
            resolved = cachedCertIcon;
        } else if (".img".equals(ext) || ".iso".equals(ext) || ".vdi".equals(ext) || ".vhd".equals(ext)
                || ".vhdx".equals(ext) || ".vmdk".equals(ext) || ".qcow2".equals(ext) || ".ext4".equals(ext)
                || ".wad".equals(ext) || ".payload".equals(ext) || "payload.bin".equalsIgnoreCase(FilenameUtils.getName(path))) {
            resolved = cachedImgIcon;
        } else if (FileUtils.isElf(path)) {
            resolved = cachedElfIcon;
        } else if (!isInZip) {
            resolved = cachedFileIcon;
            loadMagicOrElfIconAsync(path, fileIconView);
        } else {
            resolved = cachedFileIcon;
        }
        iconMemo.put(path, resolved);
        return resolved;
    }

    private static boolean isDatabaseExt(String ext) {
        return ".db".equals(ext) || ".sqlite".equals(ext) || ".sqlite3".equals(ext)
                || ".db-wal".equals(ext) || ".db-shm".equals(ext) || ".db-journal".equals(ext)
                || ".workdb".equals(ext) || ".workdb-wal".equals(ext) || ".workdb-shm".equals(ext)
                || ".ldb".equals(ext) || ".dbf".equals(ext) || ".mdb".equals(ext) || ".accdb".equals(ext)
                || ".rdb".equals(ext) || ".realm".equals(ext) || ".kdbx".equals(ext)
                || ".wallet".equals(ext) || ".keychain".equals(ext);
    }

    private static boolean isFontExt(String ext) {
        return ".ttf".equals(ext) || ".otf".equals(ext) || ".woff".equals(ext) || ".woff2".equals(ext) || ".ttc".equals(ext);
    }

    private static boolean isBinaryExt(String ext) {
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

    private static boolean isProtoExt(String ext) {
        return ".pb".equals(ext) || ".proto".equals(ext) || ".preferences_pb".equals(ext) || ".pma".equals(ext) || ".bson".equals(ext)
                || ".parquet".equals(ext) || ".arrow".equals(ext) || ".avro".equals(ext);
    }

    private static boolean isWordExt(String ext) {
        return ".doc".equals(ext) || ".docx".equals(ext) || ".odt".equals(ext) || ".rtf".equals(ext);
    }

    private static boolean isSheetExt(String ext) {
        return ".xls".equals(ext) || ".xlsx".equals(ext) || ".csv".equals(ext) || ".ods".equals(ext) || ".tsv".equals(ext);
    }

    private static boolean isSlideExt(String ext) {
        return ".ppt".equals(ext) || ".pptx".equals(ext) || ".odp".equals(ext);
    }

    private static boolean isScriptExt(String ext) {
        return ".sh".equals(ext) || ".bash".equals(ext) || ".py".equals(ext) || ".c".equals(ext)
                || ".cpp".equals(ext) || ".h".equals(ext) || ".hpp".equals(ext) || ".cs".equals(ext)
                || ".rs".equals(ext) || ".go".equals(ext) || ".lua".equals(ext);
    }

    private static boolean isBackupExt(String ext) {
        return ".bak".equals(ext) || ".backup".equals(ext) || ".old".equals(ext) || ".orig".equals(ext) || ".save".equals(ext);
    }

    private static boolean isCertExt(String ext) {
        return ".pem".equals(ext) || ".crt".equals(ext) || ".cer".equals(ext) || ".key".equals(ext)
                || ".keystore".equals(ext) || ".jks".equals(ext) || ".bks".equals(ext) || ".der".equals(ext) || ".p12".equals(ext) || ".pfx".equals(ext)
                || ".asc".equals(ext) || ".gpg".equals(ext) || ".csr".equals(ext) || ".crl".equals(ext)
                || ".sig".equals(ext) || ".idsig".equals(ext) || ".pub".equals(ext) || ".p8".equals(ext) || ".pk8".equals(ext);
    }

    private void loadMagicOrElfIconAsync(String path, ImageView fileIconView) {
        fileIconView.setTag(path);
        iconLoaderService.execute(() -> {
            try {
                Drawable magicIcon = probeMagicBytes(path);
                if (magicIcon != null) {
                    iconMemo.put(path, magicIcon);
                    iconCache.put(path, magicIcon);
                    context.runOnUiThread(() -> {
                        if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(magicIcon);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private Drawable probeMagicBytes(String path) {
        try {
            String resolved = resolveAccessiblePath(path);
            File f = new File(resolved);
            if (!f.canRead()) return null;
            byte[] header = new byte[16];
            try (FileInputStream fis = new FileInputStream(f)) {
                int read = fis.read(header);
                if (read < 4) return null;
            }

            // 1. ELF (Executable and Linkable Format)
            if (header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
                return cachedElfIcon;
            }

            // 2. SQLite 3 Database: "SQLite format 3\000"
            if (header[0] == 'S' && header[1] == 'Q' && header[2] == 'L' && header[3] == 'i') {
                return cachedDbIcon;
            }

            // 3. DEX (Dalvik Executable): "dex\n"
            if (header[0] == 'd' && header[1] == 'e' && header[2] == 'x' && header[3] == 0x0A) {
                return cachedDexIcon;
            }

            // 4. ZIP / APK / JAR: "PK\003\004"
            if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
                return cachedArchiveIcon;
            }

            // 5. PDF: "%PDF"
            if (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
                return cachedPdfIcon;
            }

            // 6. PNG Image: 0x89 'P' 'N' 'G'
            if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return cachedImageIcon;
            }

            // 7. JPEG Image: 0xFF 0xD8 0xFF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return cachedImageIcon;
            }

            // 8. GIF Image: "GIF8"
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
                return cachedImageIcon;
            }

            // 9. WebP: "RIFF....WEBP"
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F') {
                return cachedImageIcon;
            }

            // 10. Shell script: "#!"
            if (header[0] == '#' && header[1] == '!') {
                return cachedScriptIcon;
            }

            // 11. TTF / OTF Font: 0x00 0x01 0x00 0x00 or "OTTO"
            if ((header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00)
                    || (header[0] == 'O' && header[1] == 'T' && header[2] == 'T' && header[3] == 'O')) {
                return cachedFontIcon;
            }

            // 12. GZIP Archive: 0x1F 0x8B
            if ((header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
                return cachedArchiveIcon;
            }
        } catch (Throwable ignored) {}
        return null;
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
            if (bitmap != null) {
                if (!path.equals(fileIconView.getTag())) return;
                Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
                iconCache.put(path, icon);
                context.runOnUiThread(() -> {
                    if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
                });
            } else if (!isVideo) {
                // If an image (like a WhatsApp animated sticker .webp) failed decode,
                // check if it is actually a zip/lottie container or special package
                try {
                    String resolved = resolveAccessiblePath(path);
                    File f = new File(resolved);
                    if (StickerUtil.isLottieSticker(f)) {
                        Bitmap stickerBm = StickerUtil.renderThumbnailBitmap(f, 160);
                        Drawable icon = stickerBm != null ? new BitmapDrawable(context.getResources(), stickerBm) : cachedStickerIcon;
                        iconCache.put(path, icon);
                        context.runOnUiThread(() -> {
                            if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(icon);
                        });
                    } else if (isZipFileFormat(path)) {
                        iconCache.put(path, cachedArchiveIcon);
                        context.runOnUiThread(() -> {
                            if (path.equals(fileIconView.getTag())) fileIconView.setImageDrawable(cachedArchiveIcon);
                        });
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private static boolean isZipFileFormat(String path) {
        if (path == null) return false;
        try {
            String resolved = resolveAccessiblePath(path);
            File f = new File(resolved);
            if (f.canRead()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] header = new byte[4];
                    if (fis.read(header) == 4) {
                        return header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
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
        cachedStickerIcon = badge(res, density, R.drawable.ic_sticker_24px, 0xFF00897B, true);
        cachedPdfIcon     = badge(res, density, R.drawable.pdf_24px, 0xFFAD1457, true);
        cachedTextIcon    = badge(res, density, R.drawable.baseline_text_snippet_24, 0xFF1565C0, true);
        cachedElfIcon     = badge(res, density, R.drawable.terminal_24px, 0xFF37474F, true);
        cachedImgIcon     = badge(res, density, R.drawable.ic_disk_drive_24px, 0xFF455A64, true);
        cachedDbIcon      = badge(res, density, R.drawable.ic_database_24px, 0xFF00695C, true);
        cachedFontIcon    = badge(res, density, R.drawable.ic_font_aa_24px, 0xFFD84315, true);
        cachedBinaryIcon  = badge(res, density, R.drawable.baseline_settings_24, 0xFF455A64, true);
        cachedProtoIcon   = badge(res, density, R.drawable.ic_data_proto_24px, 0xFF4527A0, true);
        cachedDocIcon     = badge(res, density, R.drawable.ic_office_doc_24px, 0xFF1565C0, true);
        cachedSheetIcon   = badge(res, density, R.drawable.ic_office_sheet_24px, 0xFF2E7D32, true);
        cachedSlideIcon   = badge(res, density, R.drawable.ic_office_slide_24px, 0xFFE65100, true);
        cachedScriptIcon  = badge(res, density, R.drawable.terminal_24px, 0xFF283593, true);
        cachedBackupIcon  = badge(res, density, R.drawable.ic_history, 0xFF5D4037, true);
        cachedCertIcon    = badge(res, density, R.drawable.ic_certificate_seal_24px, 0xFFF57F17, true);
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
        if (SafFs.isSafPath(path)) {
            // Provider documents have no readable path: decode through the
            // ContentResolver stream (bounds pass, then the scaled pass).
            try (InputStream in = FileUtils.getInputStream(new File(path))) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, options);
                int width = options.outWidth;
                int height = options.outHeight;
                int scale = 1;
                while (width / 2 >= 128 && height / 2 >= 128) {
                    width /= 2;
                    height /= 2;
                    scale *= 2;
                }
                try (InputStream in2 = FileUtils.getInputStream(new File(path))) {
                    options.inSampleSize = scale;
                    options.inJustDecodeBounds = false;
                    return BitmapFactory.decodeStream(in2, null, options);
                }
            } catch (Throwable t) {
                return null;
            }
        }
        String resolvedPath = resolveAccessiblePath(path);
        Bitmap bitmap = decodeBitmapFromFile(resolvedPath);
        if (bitmap == null && !resolvedPath.equals(path)) {
            bitmap = decodeBitmapFromFile(path);
        }
        if (bitmap == null && (path.startsWith("/data/") || isRootAccessAvailable())) {
            bitmap = decodeBitmapViaRootStream(path);
        }
        return bitmap;
    }

    private static boolean isRootAccessAvailable() {
        try {
            RootManager rm = RootManager.getInstance();
            return rm != null && rm.isRootAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static Bitmap decodeBitmapFromFile(String filePath) {
        try {
            File f = new File(filePath);
            if (!f.canRead()) return null;
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

    private static Bitmap decodeBitmapViaRootStream(String path) {
        try {
            RootManager rm = RootManager.getInstance();
            if (rm == null || !rm.isRootAvailable()) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            rm.streamFromRoot(path, baos, 2 * 1024 * 1024); // Up to 2MB for thumbnail
            byte[] data = baos.toByteArray();
            if (data == null || data.length == 0) return null;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);

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
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
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
