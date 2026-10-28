package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recycle bin for plain files: deleting through the file manager moves items
 * here (per the user's Ask/Never/Always choice) instead of removing them from
 * disk, so an accidental delete is recoverable.
 *
 * <p>Trashed items sit directly in the bin directory under their own name;
 * where they came from and when they were deleted is tracked in one metadata
 * file ({@link #META_FILE}) next to them, so the visible layout stays clean.
 * Entries older than the retention period are purged automatically.
 */
public final class RecycleBin {

    public static final String META_FILE = ".recycle_bin_meta.json";
    public static final String MODE_ASK = "ask";
    public static final String MODE_NEVER = "never";
    public static final String MODE_ALWAYS = "always";

    private RecycleBin() {
    }

    /** ask / never / always. Default ask; the delete dialog remembers the
     * last checkbox choice for the ask mode via {@link #lastChoiceEnabled}. */
    public static String mode(Context context) {
        try {
            String m = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("recycle_bin_mode", MODE_ASK);
            if (m != null) {
                switch (m) {
                    case MODE_NEVER:
                    case MODE_ALWAYS:
                        return m;
                }
            }
        } catch (Exception ignored) {
        }
        return MODE_ASK;
    }

    public static void setMode(Context context, String mode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("recycle_bin_mode", mode).apply();
    }

    /** Last checkbox state shown in the delete dialog (ask mode). */
    public static boolean lastChoiceEnabled(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("recycle_bin_last", true);
        } catch (Exception e) {
            return true;
        }
    }

    public static void setLastChoice(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("recycle_bin_last", enabled).apply();
    }

    /** Retention in days; 0 keeps entries until emptied manually. */
    public static int retentionDays(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getInt("recycle_bin_days", 30);
        } catch (Exception e) {
            return 30;
        }
    }

    public static void setRetentionDays(Context context, int days) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt("recycle_bin_days", Math.max(0, days)).apply();
    }

    /** Custom bin location, or {@code <app dir>/Recycle Bin} by default. */
    public static File binDir(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String custom = prefs.getString("recycle_bin_dir", "");
        File dir;
        if (custom != null && !custom.trim().isEmpty()) dir = new File(custom.trim());
        else dir = new File(UiPrefs.appPathDir(context, UiPrefs.defaultAppDir()), "Recycle Bin");
        try {
            if (!dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
        } catch (Exception ignored) {
        }
        return dir;
    }

    public static void setBinDir(Context context, String path) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("recycle_bin_dir", path == null ? "" : path).apply();
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        if (p.startsWith("/sdcard")) {
            p = "/storage/emulated/0" + p.substring("/sdcard".length());
        }
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public static boolean isInsideBin(Context context, File file) {
        if (file == null || context == null) return false;
        File bin = binDir(context);
        if (bin == null) return false;

        String fileAbs = normalizePath(file.getAbsolutePath());
        String binAbs = normalizePath(bin.getAbsolutePath());
        if (!fileAbs.isEmpty() && !binAbs.isEmpty()) {
            if (fileAbs.equals(binAbs) || fileAbs.startsWith(binAbs + "/")) return true;
        }

        try {
            String fileCanon = normalizePath(file.getCanonicalPath());
            String binCanon = normalizePath(bin.getCanonicalPath());
            if (!fileCanon.isEmpty() && !binCanon.isEmpty()) {
                if (fileCanon.equals(binCanon) || fileCanon.startsWith(binCanon + "/")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static final class RestoreResult {
        public final File restoredFile;
        public final boolean wasRenamed;

        public RestoreResult(File restoredFile, boolean wasRenamed) {
            this.restoredFile = restoredFile;
            this.wasRenamed = wasRenamed;
        }
    }

    /** One trashed item: the binned file plus where it came from. */
    public static final class Entry {
        public final File file;
        public final String originPath;
        public final long deletedAt;
        public final String reason;
        public final long lastModified;

        public Entry(File file, String originPath, long deletedAt, String reason, long lastModified) {
            this.file = file;
            this.originPath = originPath;
            this.deletedAt = deletedAt;
            this.reason = reason != null ? reason : "delete";
            this.lastModified = lastModified;
        }

        public String displayName() {
            return file.getName();
        }
    }

    public static List<Entry> entries(Context context) {
        List<Entry> out = new ArrayList<>();
        File root = binDir(context);
        File[] kids = root.listFiles();
        if (kids == null) return out;
        Map<String, Meta> meta = loadMeta(root);
        Arrays.sort(kids, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File k : kids) {
            if (k.getName().equals(META_FILE)) continue;
            Meta m = meta.get(k.getName());
            out.add(new Entry(k, m == null ? null : m.origin,
                    m == null || m.time <= 0 ? k.lastModified() : m.time,
                    m == null ? "delete" : m.reason,
                    m == null ? k.lastModified() : m.lastModified));
        }
        return out;
    }

    private static File metaFile(File binDir) {
        return new File(binDir, META_FILE);
    }

    private static Map<String, Meta> loadMeta(File binDir) {
        try (FileReader r = new FileReader(metaFile(binDir))) {
            Type t = new TypeToken<Map<String, Meta>>() {
            }.getType();
            Map<String, Meta> m = AppGsonHolder.GSON.fromJson(r, t);
            return m == null ? new HashMap<>() : m;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static void saveMeta(File binDir, Map<String, Meta> meta) {
        try (FileWriter w = new FileWriter(metaFile(binDir))) {
            AppGsonHolder.GSON.toJson(meta, w);
        } catch (Exception ignored) {
        }
    }

    private static final class AppGsonHolder {
        static final Gson GSON = AppGson.get();
    }

    public static final class Meta {
        @SerializedName("o")
        public String origin;
        @SerializedName("t")
        public long time;
        @SerializedName("r")
        public String reason;
        @SerializedName("lm")
        public long lastModified;
    }

    public static boolean moveToBin(Context context, File file) {
        return moveToBin(context, file, "delete");
    }

    /**
     * Moves {@code file} into the bin under its own name. Works over the
     * elevated backend too (root/Shizuku) when a plain move is impossible.
     * Returns true when the item ended up in the bin.
     */
    public static boolean moveToBin(Context context, File file, String reason) {
        if (file == null || !file.exists() || isInsideBin(context, file)) return false;
        long origLastMod = file.lastModified();
        File target = freeName(binDir(context), file.getName());
        try {
            if (file.renameTo(target)) {
                target.setLastModified(origLastMod);
                return record(context, file, target, reason, origLastMod);
            }
            if (copyTree(file, target) && deleteFile(context, file)) {
                target.setLastModified(origLastMod);
                return record(context, file, target, reason, origLastMod);
            }
            // Plain I/O failed (root-only path): stage through the elevated backend.
            if (AccessManager.fileOpsOn(context)) {
                try {
                    AccessManager.copyFile(context, file.getAbsolutePath(), target.getAbsolutePath(), true);
                    AccessManager.delete(context, file.getAbsolutePath(), true);
                    target.setLastModified(origLastMod);
                    return record(context, file, target, reason, origLastMod);
                } catch (Exception ignored) {
                    try {
                        deleteTree(target);
                    } catch (Exception ignored2) {
                    }
                }
            }
        } catch (Exception ignored) {
            try {
                deleteTree(target);
            } catch (Exception ignored2) {
            }
        }
        return false;
    }

    private static boolean record(Context context, File original, File target, String reason, long lastModified) {
        File root = target.getParentFile();
        if (root == null) return true;
        Map<String, Meta> meta = loadMeta(root);
        Meta m = new Meta();
        m.origin = original.getAbsolutePath();
        m.time = System.currentTimeMillis();
        m.reason = reason != null ? reason : "delete";
        m.lastModified = lastModified > 0 ? lastModified : original.lastModified();
        meta.put(target.getName(), m);
        saveMeta(root, meta);
        return true;
    }

    /** "name.ext", then "name (2).ext", "name (3).ext", ... inside binDir. */
    private static File freeName(File binDir, String name) {
        File f = new File(binDir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        String ext = dot <= 0 ? "" : name.substring(dot);
        for (int n = 2; ; n++) {
            f = new File(binDir, stem + " (" + n + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    /**
     * Moves a binned item back to its original location (elevated backend as
     * fallback). When the original already exists, the restored item gets a
     * numbered suffix. Returns the restored file, or null on failure.
     */
    public static RestoreResult restoreEntry(Context context, Entry entry) {
        if (entry == null || entry.file == null || !entry.file.exists()) return null;
        String originPath = entry.originPath;
        if (originPath == null || originPath.isEmpty()) return null;
        File original = new File(originPath);
        File parent = original.getParentFile();
        if (parent == null) return null;
        try {
            if (!parent.isDirectory() && !parent.mkdirs()
                    && !AccessManager.fileOpsOn(context)) return null;
        } catch (Exception ignored) {
        }
        File target = original;
        String baseName = original.getName();
        int dot = baseName.lastIndexOf('.');
        String stem = dot <= 0 ? baseName : baseName.substring(0, dot);
        String ext = dot <= 0 ? "" : baseName.substring(dot);
        int n = 1;
        boolean wasRenamed = false;
        while (target.exists()) {
            wasRenamed = true;
            target = new File(parent, stem + " (" + (++n) + ")" + ext);
        }
        long restoreTime = entry.lastModified > 0 ? entry.lastModified : entry.file.lastModified();
        try {
            if (entry.file.renameTo(target)) {
                target.setLastModified(restoreTime);
                forget(context, entry, target);
                return new RestoreResult(target, wasRenamed);
            }
            if (copyTree(entry.file, target) && deleteFile(context, entry.file)) {
                target.setLastModified(restoreTime);
                forget(context, entry, target);
                return new RestoreResult(target, wasRenamed);
            }
            if (AccessManager.fileOpsOn(context)) {
                try {
                    AccessManager.copyFile(context, entry.file.getAbsolutePath(), target.getAbsolutePath(), true);
                    AccessManager.delete(context, entry.file.getAbsolutePath(), true);
                    target.setLastModified(restoreTime);
                    forget(context, entry, target);
                    return new RestoreResult(target, wasRenamed);
                } catch (Exception ignored) {
                    try {
                        deleteTree(target);
                    } catch (Exception ignored2) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean renameEntry(Context context, Entry entry, String newName) {
        if (entry == null || entry.file == null || newName == null || newName.trim().isEmpty()) return false;
        File root = entry.file.getParentFile();
        if (root == null) return false;
        File target = new File(root, newName.trim());
        if (target.exists()) return false;
        if (entry.file.renameTo(target)) {
            Map<String, Meta> meta = loadMeta(root);
            Meta m = meta.remove(entry.file.getName());
            if (m != null) {
                meta.put(target.getName(), m);
                saveMeta(root, meta);
            }
            return true;
        }
        return false;
    }

    private static File forget(Context context, Entry entry, File restored) {
        File root = entry.file.getParentFile();
        if (root != null) {
            Map<String, Meta> meta = loadMeta(root);
            if (meta.remove(entry.file.getName()) != null) saveMeta(root, meta);
        }
        return restored;
    }

    /** Permanently removes one binned item. */
    public static boolean deleteEntry(Context context, Entry entry) {
        if (entry == null || entry.file == null) return false;
        boolean ok = deleteFile(context, entry.file);
        File root = entry.file.getParentFile();
        if (root != null) {
            Map<String, Meta> meta = loadMeta(root);
            if (meta.remove(entry.file.getName()) != null) saveMeta(root, meta);
        }
        return ok;
    }

    /** Permanently removes everything in the bin. */
    public static void emptyBin(Context context) {
        for (Entry e : entries(context)) deleteEntry(context, e);
    }

    /** Removes entries older than the retention period. 0 days = keep all. */
    public static int purgeExpired(Context context) {
        int days = retentionDays(context);
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        int removed = 0;
        for (Entry e : entries(context)) {
            if (e.deletedAt > 0 && e.deletedAt < cutoff && deleteEntry(context, e)) removed++;
        }
        return removed;
    }

    /** Delete that works on root-owned paths through the elevated backend. */
    private static boolean deleteFile(Context context, File f) {
        try {
            if (f.delete()) return true;
        } catch (Exception ignored) {
        }
        if (f.isDirectory()) {
            try {
                if (deleteTree(f)) return true;
            } catch (Exception ignored) {
            }
        }
        try {
            if (AccessManager.fileOpsOn(context)) {
                AccessManager.delete(context, f.getAbsolutePath(), true);
                return !f.exists();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean copyTree(File src, File dst) {
        try {
            if (src.isDirectory()) {
                if (!dst.isDirectory() && !dst.mkdirs()) return false;
                File[] kids = src.listFiles();
                if (kids != null) {
                    for (File k : kids) {
                        if (!copyTree(k, new File(dst, k.getName()))) return false;
                    }
                }
                return true;
            }
            File parent = dst.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            FileUtils.copyFile(src, dst);
            dst.setLastModified(src.lastModified());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean deleteTree(File f) {
        if (f == null || !f.exists()) return true;
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) {
            for (File k : kids) {
                if (!deleteTree(k)) return false;
            }
        }
        return f.delete();
    }
}
