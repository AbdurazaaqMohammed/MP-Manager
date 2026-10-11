package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Instant-paint cache for the dual-pane file listing (cold-start optimization).
 *
 * <p>On every successful plain-folder load the sorted child names (+ dir flag)
 * are persisted to a small JSON file on a background thread. On the next cold
 * start the cached rows are painted immediately (no stat, no sort, no I/O on
 * the main thread beyond one tiny file read) and the real listing replaces
 * them moments later when the background load finishes.
 *
 * <p>All methods are safe to call from any thread; callers must do
 * {@link #load} off the main thread when possible (it is one small read) and
 * {@link #save} always on a background thread.
 */
public final class PaneCache {

    /** Upper bound so a huge folder never bloats the cache file. */
    private static final int MAX_ENTRIES = 300;

    private static final Gson GSON = new Gson();

    private PaneCache() {
    }

    /** Cached rows for one pane slot. Never null fields when returned non-null. */
    public static final class Snapshot {
        public final String dirPath;
        /** Child names in display (sorted) order, without the ".." entry. */
        public final List<String> names = new ArrayList<>();
        /** Parallel to {@link #names}: true when the child is a directory. */
        public final List<Boolean> isDir = new ArrayList<>();

        Snapshot(String dirPath) {
            this.dirPath = dirPath;
        }

        public boolean isEmpty() {
            return names.isEmpty();
        }
    }

    private static final class Dto {
        @SerializedName("dir")
        String dir;
        @SerializedName("rows")
        List<String[]> rows;
    }

    private static File cacheFile(Context context, boolean pane1) {
        return new File(context.getFilesDir(), pane1 ? "pane_cache_1.json" : "pane_cache_2.json");
    }

    /**
     * Reads the cached snapshot for a pane slot. Returns null when there is no
     * usable cache (first run, corrupt file, ...). Performs file I/O: prefer a
     * background thread, though the file is tiny (a few KB).
     */
    public static Snapshot load(Context context, boolean pane1) {
        File f;
        try {
            f = cacheFile(context, pane1);
            if (f == null || !f.isFile() || f.length() <= 0 || f.length() > 256 * 1024) return null;
        } catch (Exception ignored) {
            return null;
        }
        try (FileReader reader = new FileReader(f)) {
            Dto dto = GSON.fromJson(reader, Dto.class);
            if (dto == null || dto.dir == null || dto.dir.isEmpty() || dto.rows == null) return null;
            Snapshot out = new Snapshot(dto.dir);
            int n = Math.min(dto.rows.size(), MAX_ENTRIES);
            for (int i = 0; i < n; i++) {
                String[] row = dto.rows.get(i);
                if (row == null || row.length < 2 || row[0] == null || row[0].isEmpty()) continue;
                // Never trust ".." or absolute paths from the cache file.
                if ("..".equals(row[0]) || row[0].contains("/") || row[0].contains("\\")) continue;
                out.names.add(row[0]);
                out.isDir.add("d".equals(row[1]));
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Persists the freshly listed (already sorted) children of a folder.
     * Atomic-ish: writes to a temp file then renames. Must run on a
     * background thread. {@code files} must be the raw listed children
     * (without the ".." up-entry the adapter prepends internally).
     */
    public static void save(Context context, boolean pane1, String dirPath, File[] files) {
        if (context == null || dirPath == null || dirPath.isEmpty() || files == null) return;
        try {
            Dto dto = new Dto();
            dto.dir = dirPath;
            dto.rows = new ArrayList<>(Math.min(files.length, MAX_ENTRIES));
            int n = Math.min(files.length, MAX_ENTRIES);
            for (int i = 0; i < n; i++) {
                File child = files[i];
                if (child == null) continue;
                String name = child.getName();
                if (name == null || name.isEmpty() || "..".equals(name)) continue;
                boolean dir;
                try {
                    dir = child.isDirectory();
                } catch (Exception ignored) {
                    dir = false;
                }
                dto.rows.add(new String[]{name, dir ? "d" : "f"});
            }
            File dest = cacheFile(context, pane1);
            File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(dto, writer);
            }
            if (!tmp.renameTo(dest)) {
                // renameTo fails across some filesystems; fall back to copy.
                try (FileReader in = new FileReader(tmp); FileWriter out = new FileWriter(dest)) {
                    char[] buf = new char[4096];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
                try {
                    tmp.delete();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
