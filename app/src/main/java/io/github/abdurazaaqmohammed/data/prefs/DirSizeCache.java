package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import io.github.abdurazaaqmohammed.utils.AppGson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent cache of recursive folder sizes used by size sorting.
 *
 * <p>Walking a big directory tree just to place it in a size-sorted listing
 * costs seconds; this cache keeps the measured sizes on disk so later visits
 * (and later app starts) skip the walk entirely.
 *
 * <p>An entry stays valid while the folder's own modification time predates
 * the measurement (adding/removing/renaming direct children bumps it), or
 * until {@link #MAX_AGE_MS} passes, which forces an eventual re-measure.
 */
public final class DirSizeCache {

    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    /** Debounce window for disk writes while a batch of walks is finishing. */
    private static final long SAVE_DELAY_MS = 1500;

    private static WeakReference<Context> appContext;
    private static final Object LOCK = new Object();
    private static final Map<String, Long> sizes = new HashMap<>();
    private static final Map<String, Long> measuredAt = new HashMap<>();
    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean saveQueued = new AtomicBoolean(false);

    private DirSizeCache() {
    }

    public static void init(Context context) {
        if (context != null) appContext = new WeakReference<>(context.getApplicationContext());
    }

    /**
     * Parses the cache file off the main thread. Only worth doing when folder
     * sizes are actually shown ("Always" mode) — otherwise the file is never
     * read at all.
     */
    public static void preload() {
        io.execute(DirSizeCache::ensureLoaded);
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /** True once the on-disk cache has been parsed into memory. */
    public static boolean isReady() {
        return loaded.get();
    }

    private static File cacheFile() {
        Context c = appContext == null ? null : appContext.get();
        return c == null ? null : new File(c.getFilesDir(), "dir_size_cache.json");
    }

    /** Cached size for {@code dir}, or null when absent/stale. */
    public static Long peek(File dir) {
        if (dir == null) return null;
        // Main-thread callers never trigger the parse; they simply miss (the
        // preload queued in init() finishes long before the first rebind).
        if (!loaded.get() && isMainThread()) return null;
        ensureLoaded();
        String key;
        long mtime;
        try {
            key = dir.getAbsolutePath();
            mtime = dir.lastModified();
        } catch (Exception e) {
            return null;
        }
        synchronized (LOCK) {
            Long size = sizes.get(key);
            Long at = measuredAt.get(key);
            if (size == null || at == null) return null;
            if (at + MAX_AGE_MS < System.currentTimeMillis()) return null;
            if (mtime > at) return null;
            return size;
        }
    }

    public static void record(File dir, long size) {
        if (dir == null) return;
        if (!loaded.get() && !isMainThread()) ensureLoaded();
        try {
            String key = dir.getAbsolutePath();
            synchronized (LOCK) {
                sizes.put(key, size);
                measuredAt.put(key, System.currentTimeMillis());
            }
            dirty.set(true);
        } catch (Exception ignored) {
        }
    }

    /** Schedules a debounced disk write; cheap enough to call after every walk. */
    public static void scheduleSave() {
        if (!dirty.get() || !saveQueued.compareAndSet(false, true)) return;
        io.execute(() -> {
            try {
                Thread.sleep(SAVE_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            saveQueued.set(false);
            if (dirty.compareAndSet(true, false)) save();
            else if (dirty.get()) scheduleSave();
        });
    }

    private static void ensureLoaded() {
        if (loaded.get()) return;
        synchronized (DirSizeCache.class) {
            if (loaded.get()) return;
            load();
            loaded.set(true);
        }
    }

    private static void load() {
        File f = cacheFile();
        if (f == null || !f.isFile()) return;
        try (FileReader r = new FileReader(f)) {
            Gson gson = AppGson.get();
            Type t = new TypeToken<List<Dto>>() {
            }.getType();
            List<Dto> list = gson.fromJson(r, t);
            if (list == null) return;
            synchronized (LOCK) {
                for (Dto d : list) {
                    if (d == null || d.path == null) continue;
                    sizes.put(d.path, d.size);
                    measuredAt.put(d.path, d.at);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        if (!loaded.get() && !isMainThread()) ensureLoaded(); // never persist a partial map
        File f = cacheFile();
        if (f == null) return;
        try {
            Map<String, Long> snapshotSizes;
            Map<String, Long> snapshotAt;
            List<Dto> out = new ArrayList<>();
            synchronized (LOCK) {
                if (sizes.size() > MAX_ENTRIES) pruneLocked();
                snapshotSizes = new LinkedHashMap<>(sizes);
                snapshotAt = new LinkedHashMap<>(measuredAt);
            }
            for (Map.Entry<String, Long> e : snapshotSizes.entrySet()) {
                Long at = snapshotAt.get(e.getKey());
                if (at == null) continue;
                Dto d = new Dto();
                d.path = e.getKey();
                d.size = e.getValue();
                d.at = at;
                out.add(d);
            }
            try (FileWriter w = new FileWriter(f)) {
                AppGson.get().toJson(out, w);
            }
        } catch (Exception ignored) {
        }
    }

    /** Keeps the newest entries when the cache outgrows {@link #MAX_ENTRIES}. */
    private static void pruneLocked() {
        List<Map.Entry<String, Long>> byAge = new ArrayList<>(measuredAt.entrySet());
        byAge.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int drop = sizes.size() - MAX_ENTRIES;
        for (int i = byAge.size() - 1; i >= 0 && drop > 0; i--) {
            String key = byAge.get(i).getKey();
            if (sizes.remove(key) != null) drop--;
            measuredAt.remove(key);
        }
    }

    private static final class Dto {
        @SerializedName("p")
        String path;
        @SerializedName("s")
        long size;
        @SerializedName("t")
        long at;
    }
}
