package io.github.abdurazaaqmohammed.data.prefs;

import io.github.abdurazaaqmohammed.util.text.AppGson;

import android.content.Context;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final int MAX_ENTRIES = 6000;
    private static final int MAX_COOLDOWNS = 2000;
    private static final long MAX_AGE_MS = 6L * 60 * 60 * 1000L; // 6 hours
    private static final long STATIC_MAX_AGE_MS = 24L * 60 * 60 * 1000L; // 24 hours for static system partitions
    public static final long COOLDOWN_TIMEOUT_MS = 2L * 60 * 1000L;
    public static final long COOLDOWN_ERROR_MS = 90L * 1000L;
    /** Debounce window for disk writes while a batch of walks is finishing. */
    private static final long SAVE_DELAY_MS = 2500;
    private static final int CACHE_ENTRY_VERSION = 2;

    private static WeakReference<Context> appContext;
    private static final Object LOCK = new Object();

    private static final class CacheEntry {
        final long size;
        final long at;
        final int ver;

        CacheEntry(long size, long at, int ver) {
            this.size = size;
            this.at = at;
            this.ver = ver;
        }
    }

    private static final Map<String, CacheEntry> entries =
            new LinkedHashMap<String, CacheEntry>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };
    private static final Map<String, Long> cooldownUntil = new HashMap<>();
    private static final Set<String> inFlight = new HashSet<>();

    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dir-size-cache-io");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean saveQueued = new AtomicBoolean(false);

    private DirSizeCache() {
    }

    public static void init(Context context) {
        if (context != null) appContext = new WeakReference<>(context.getApplicationContext());
    }

    public static Context getContext() {
        return appContext == null ? null : appContext.get();
    }

    /**
     * Parses the cache file off the main thread.
     */
    public static void preload() {
        io.execute(DirSizeCache::ensureLoaded);
    }

    private static boolean isMainThread() {
        try {
            return Looper.myLooper() == Looper.getMainLooper();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True once the on-disk cache has been parsed into memory. */
    public static boolean isReady() {
        return loaded.get();
    }

    private static File cacheFile() {
        Context c = appContext == null ? null : appContext.get();
        return c == null ? null : new File(c.getFilesDir(), "dir_size_cache.json");
    }

    static long maxAgeForPath(String path) {
        if (path != null && (path.startsWith("/system")
                || path.startsWith("/vendor")
                || path.startsWith("/product")
                || path.startsWith("/system_ext")
                || path.startsWith("/apex")
                || path.startsWith("/odm")
                || path.startsWith("/oem")
                || path.startsWith("/bootstrap-apex"))) {
            return STATIC_MAX_AGE_MS;
        }
        return MAX_AGE_MS;
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
        long now = System.currentTimeMillis();
        long maxAge = maxAgeForPath(key);
        synchronized (LOCK) {
            CacheEntry e = entries.get(key);
            if (e == null) return null;
            // Drop legacy v1 0B entries on unreadable directories once, while trusting verified v2 0B entries.
            if (e.ver < CACHE_ENTRY_VERSION && e.size == 0L && !dir.canRead()) {
                entries.remove(key);
                dirty.set(true);
                return null;
            }
            if (e.at + maxAge < now) return null;
            if (mtime > e.at) return null;
            return e.size;
        }
    }

    public static boolean isInCooldown(File dir) {
        if (dir == null) return false;
        try {
            return isInCooldown(dir.getAbsolutePath());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isInCooldown(String path) {
        if (path == null) return false;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            Long until = cooldownUntil.get(path);
            if (until == null) return false;
            if (now >= until) {
                cooldownUntil.remove(path);
                return false;
            }
            return true;
        }
    }

    public static void recordCooldown(String path, long durationMs) {
        if (path == null || durationMs <= 0L) return;
        long until = System.currentTimeMillis() + durationMs;
        synchronized (LOCK) {
            if (cooldownUntil.size() > MAX_COOLDOWNS) {
                long now = System.currentTimeMillis();
                cooldownUntil.entrySet().removeIf(e -> e.getValue() <= now);
                if (cooldownUntil.size() > MAX_COOLDOWNS) cooldownUntil.clear();
            }
            cooldownUntil.put(path, until);
            inFlight.remove(path);
        }
    }

    public static boolean isInFlight(String path) {
        if (path == null) return false;
        synchronized (LOCK) {
            return inFlight.contains(path);
        }
    }

    public static boolean tryClaimScan(String path) {
        if (path == null) return false;
        synchronized (LOCK) {
            return inFlight.add(path);
        }
    }

    public static void releaseScan(String path) {
        if (path == null) return;
        synchronized (LOCK) {
            inFlight.remove(path);
        }
    }

    public static void releaseScans(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        synchronized (LOCK) {
            inFlight.removeAll(paths);
        }
    }

    public static void record(File dir, long size) {
        if (dir == null || size < 0L) return;
        if (!loaded.get() && !isMainThread()) ensureLoaded();
        try {
            String key = dir.getAbsolutePath();
            long now = System.currentTimeMillis();
            synchronized (LOCK) {
                entries.put(key, new CacheEntry(size, now, CACHE_ENTRY_VERSION));
                cooldownUntil.remove(key);
                inFlight.remove(key);
            }
            dirty.set(true);
        } catch (Exception ignored) {
        }
    }

    public static void recordBatch(Map<String, Long> batch) {
        if (batch == null || batch.isEmpty()) return;
        if (!loaded.get() && !isMainThread()) ensureLoaded();
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            for (Map.Entry<String, Long> e : batch.entrySet()) {
                String k = e.getKey();
                Long v = e.getValue();
                if (k != null && v != null && v >= 0L) {
                    entries.put(k, new CacheEntry(v, now, CACHE_ENTRY_VERSION));
                    cooldownUntil.remove(k);
                    inFlight.remove(k);
                }
            }
        }
        dirty.set(true);
        scheduleSave();
    }

    public static void resetAll() {
        synchronized (LOCK) {
            entries.clear();
            cooldownUntil.clear();
        }
        dirty.set(true);
        scheduleSave();
    }

    static void clearForTest() {
        synchronized (LOCK) {
            entries.clear();
            cooldownUntil.clear();
            inFlight.clear();
        }
        loaded.set(true);
        dirty.set(false);
    }

    static void recordWithVersionForTest(String path, long size, long at, int ver) {
        synchronized (LOCK) {
            entries.put(path, new CacheEntry(size, at, ver));
        }
    }

    public static void invalidate(File dir) {
        if (dir == null) return;
        try {
            String key = dir.getAbsolutePath();
            synchronized (LOCK) {
                entries.remove(key);
                cooldownUntil.remove(key);
            }
            dirty.set(true);
            scheduleSave();
        } catch (Exception ignored) {
        }
    }

    public static void invalidatePrefix(File root) {
        if (root == null) return;
        try {
            final String exact = root.getAbsolutePath();
            final String prefix = exact.endsWith(File.separator) ? exact : exact + File.separator;
            synchronized (LOCK) {
                entries.entrySet().removeIf(entry -> {
                    String k = entry.getKey();
                    return k.equals(exact) || k.startsWith(prefix);
                });
                cooldownUntil.entrySet().removeIf(entry -> {
                    String k = entry.getKey();
                    return k.equals(exact) || k.startsWith(prefix);
                });
            }
            dirty.set(true);
            scheduleSave();
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
                    if (d == null || d.path == null || d.size < 0L) continue;
                    entries.put(d.path, new CacheEntry(d.size, d.at, d.ver));
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
            List<Dto> out = new ArrayList<>();
            synchronized (LOCK) {
                for (Map.Entry<String, CacheEntry> e : entries.entrySet()) {
                    CacheEntry ce = e.getValue();
                    if (ce == null) continue;
                    Dto d = new Dto();
                    d.path = e.getKey();
                    d.size = ce.size;
                    d.at = ce.at;
                    d.ver = ce.ver;
                    out.add(d);
                }
            }
            File parent = f.getParentFile();
            File tmp = parent != null ? new File(parent, f.getName() + ".tmp") : null;
            if (tmp != null) {
                try (FileWriter w = new FileWriter(tmp)) {
                    AppGson.get().toJson(out, w);
                }
                if (!tmp.renameTo(f)) {
                    try (FileWriter w = new FileWriter(f)) {
                        AppGson.get().toJson(out, w);
                    }
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            } else {
                try (FileWriter w = new FileWriter(f)) {
                    AppGson.get().toJson(out, w);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Dto {
        @SerializedName("p")
        String path;
        @SerializedName("s")
        long size;
        @SerializedName("t")
        long at;
        @SerializedName("v")
        int ver;
    }
}
