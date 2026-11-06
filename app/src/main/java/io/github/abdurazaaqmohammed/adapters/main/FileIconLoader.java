package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
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
import android.util.Xml;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.apk.axml.aXMLDecoder;

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
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ApkIconHelper;
import io.github.abdurazaaqmohammed.utils.RootFile;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.RootManager;
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
            cachedMusicIcon, cachedArchiveIcon, cachedPdfIcon, cachedTextIcon, cachedFileIcon, cachedStickerIcon;
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
            elevated = AccessManager.fileOpsOn(context);
        } catch (Exception ignored) {
        }
        this.elevatedFileOps = elevated;
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
            setupNonFolderIconView(zipEntry.getFullPath(), fileIconView);
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
        return ".xml".equals(dot) && (lower.contains("/drawable") || lower.contains("/mipmap"));
    }

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
            // drawable / mipmap xml: inflate and render
            byte[] data = readZipEntry(zip, fullPath);
            if (data == null || data.length == 0) return null;
            String xml;
            if (data.length >= 2 && data[0] == 0x03 && data[1] == 0x00) {
                // Binary AXML (compiled resource inside an APK)
                xml = new aXMLDecoder(new java.io.ByteArrayInputStream(data)).decodeAsString();
            } else {
                xml = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                if (!xml.contains("<")) return null;
            }
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new java.io.StringReader(xml));
            int type = parser.next();
            while (type != org.xmlpull.v1.XmlPullParser.START_TAG
                    && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                type = parser.next();
            }
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) return null;
            // Vectors/shapes inflate with the app resources + theme so ?attr
            // references resolve; entries referencing APK-internal @color/@dimen
            // values still fail here and keep the fallback icon.
            return Drawable.createFromXml(context.getResources(), parser, context.getTheme());
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readZipEntry(File zip, String entryPath) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
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
        if (!"always".equals(folderSizeMode)) {
            // "Only in archive" / "Never": no recursive walks for real folders,
            // date-only row (zip listings keep their metadata sizes).
            fileDateView.setTag(null);
            if (mtime > 0) {
                fileDateView.setVisibility(View.VISIBLE);
                applyDateTextSize(fileDateView);
                fileDateView.setText(UiPrefs.formatDate(datePattern, mtime));
            } else {
                fileDateView.setVisibility(View.INVISIBLE);
            }
            return;
        }
        if (mtime > 0) {
            fileDateView.setTag(null);
            showFolderSizeAndDate(file, fileDateView, UiPrefs.formatDate(datePattern, mtime));
            return;
        }
        if (!elevatedFileOps) {
            fileDateView.setTag(null);
            fileDateView.setVisibility(View.INVISIBLE);
            return;
        }
        String tag = file.getAbsolutePath();
        Long cached = mtimeCache.get(tag);
        if (cached != null) {
            fileDateView.setTag(null);
            if (cached > 0) {
                showFolderSizeAndDate(file, fileDateView, UiPrefs.formatDate(datePattern, cached));
            } else {
                fileDateView.setVisibility(View.INVISIBLE);
            }
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
                    showFolderSizeAndDate(file, fileDateView, UiPrefs.formatDate(datePattern, finalM));
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
        if (dir == null || !dir.isDirectory()) return 0;
        String path = dir.getAbsolutePath();
        if (!isUserStorage(path)) {
            try {
                if (AccessManager.fileOpsOn(ctx)) {
                    long du = AccessManager.dirSize(ctx, path);
                    if (du >= 0) return du;
                }
            } catch (Exception ignored) {}
        }
        long deadline = System.currentTimeMillis() + SIZE_WALK_MAX_TIME_MS;
        ArrayDeque<File> stack = new ArrayDeque<>();
        stack.push(dir);
        long total = 0;
        int visited = 0;
        while (!stack.isEmpty()) {
            if (++visited > SIZE_WALK_MAX_ENTRIES || System.currentTimeMillis() > deadline) return -1;
            File f = stack.pop();
            File[] children;
            try {
                children = f.listFiles();
            } catch (Exception e) {
                children = null;
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
                if (!isInZip) loadApkIconAsync(path, fileIconView);
            }
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            resolved = cachedImageIcon;
            if (!isInZip) loadThumbnailAsync(path, fileIconView, false);
        } else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            resolved = cachedVideoIcon;
            if (!isInZip) loadThumbnailAsync(path, fileIconView, true);
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
        } else {
            resolved = cachedFileIcon;
        }
        iconMemo.put(path, resolved);
        return resolved;
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
                    if (io.github.abdurazaaqmohammed.utils.StickerUtil.isLottieSticker(f)) {
                        Bitmap stickerBm = io.github.abdurazaaqmohammed.utils.StickerUtil.renderThumbnailBitmap(f, 160);
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
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
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
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
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
