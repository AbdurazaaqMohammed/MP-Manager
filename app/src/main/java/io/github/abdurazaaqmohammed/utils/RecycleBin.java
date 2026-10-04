package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A recoverable delete bin.
 *
 * <p>Deleted files are moved here instead of being erased, so a delete that
 * turns out to be wrong can be undone. Each item lives in its own directory
 * holding the original {@code item} plus a {@code meta} file that records where
 * it came from and when, which is what makes restore possible without guessing
 * from the item's name.
 *
 * <p>Items live in the app's external files directory, so no storage permission
 * is involved and the bin is private to the app.
 */
public final class RecycleBin {

    private static final String ROOT = "recycle";
    private static final String ITEM = "item";
    private static final String META = "meta";

    private RecycleBin() {
    }

    private static File root(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = new File(context.getFilesDir(), ROOT);
        File dir = new File(base, ROOT);
        if (!dir.exists() && !dir.mkdirs()) return base;
        return dir;
    }

    /** One trashed file: what it was, where it came from, and when. */
    public static final class Entry {
        public final File dir;
        public final File item;
        public final String originalPath;
        public final String originalName;
        public final long deletedAt;

        Entry(File dir, File item, String originalPath, long deletedAt) {
            this.dir = dir;
            this.item = item;
            this.originalPath = originalPath;
            File f = new File(originalPath);
            this.originalName = f.getName();
            this.deletedAt = deletedAt;
        }

        public boolean wasDirectory() {
            return item.isDirectory();
        }

        /** Size in bytes; directories report the sum of their contents. */
        public long size() {
            try {
                if (item.isFile()) return item.length();
                return com.reandroid.apkeditor.Util.countInsideFolder(item).total();
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /**
     * Moves {@code source} into the bin. Returns the created entry directory, or
     * throws if it could not be moved -- callers should treat that as a failed
     * delete rather than silently dropping the file.
     */
    public static File store(Context context, File source) throws IOException {
        if (!source.exists()) throw new IOException("Nothing to delete: " + source);
        File binRoot = root(context);
        long stamp = System.currentTimeMillis();
        File dir = new File(binRoot, stamp + "_" + nextSuffix(binRoot, stamp));
        if (!dir.mkdirs()) throw new IOException("Could not create bin entry");

        File item = new File(dir, ITEM);
        // Same volume as the app's files dir, so this is a rename in the common
        // case; fall back to a recursive copy when it is not.
        if (!source.renameTo(item)) {
            if (!copyRecursively(source, item)) {
                deleteRecursively(dir);
                throw new IOException("Could not move " + source);
            }
            deleteRecursively(source);
        }

        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, META)),
                StandardCharsets.UTF_8)) {
            w.write(source.getAbsolutePath() + "\n" + stamp + "\n" + (source.isDirectory() ? "dir" : "file"));
        } catch (IOException e) {
            // The item is already safe in the bin; a missing meta only costs us
            // the ability to restore it automatically.
        }
        return dir;
    }

    private static int nextSuffix(File binRoot, long stamp) {
        int n = 1;
        while (new File(binRoot, stamp + "_" + n).exists()) n++;
        return n;
    }

    /** Every trashed item, newest first. */
    public static List<Entry> list(Context context) {
        List<Entry> out = new ArrayList<>();
        File[] dirs = root(context).listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            File item = new File(d, ITEM);
            if (!item.exists()) continue;
            String path = null;
            long stamp = d.lastModified();
            File meta = new File(d, META);
            if (meta.exists()) {
                try {
                    String[] lines = java.nio.file.Files.readAllLines(meta.toPath(),
                            StandardCharsets.UTF_8);
                    if (lines.length > 0 && !lines[0].trim().isEmpty()) path = lines[0].trim();
                    if (lines.length > 1) {
                        try {
                            stamp = Long.parseLong(lines[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (path == null) path = item.getName();
            out.add(new Entry(d, item, path, stamp));
        }
        out.sort(Comparator.comparingLong((Entry e) -> e.deletedAt).reversed());
        return out;
    }

    /**
     * Puts an entry back where it came from. When the original spot is taken,
     * a sibling name is used so an existing file is never overwritten.
     */
    public static boolean restore(Context context, Entry entry) {
        File target = new File(entry.originalPath);
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) return false;
        if (target.exists()) {
            String name = target.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            target = new File(parent, base + "_restored_" + System.currentTimeMillis() + ext);
        }
        if (entry.item.renameTo(target)) {
            deleteRecursively(entry.dir);
            return true;
        }
        if (!copyRecursively(entry.item, target)) return false;
        deleteRecursively(entry.dir);
        return true;
    }

    /** Erases one entry for good. */
    public static boolean purge(Entry entry) {
        return deleteRecursively(entry.dir);
    }

    /** Erases everything in the bin. */
    public static int empty(Context context) {
        int n = 0;
        for (Entry e : list(context)) {
            if (purge(e)) n++;
        }
        return n;
    }

    public static int count(Context context) {
        return list(context).size();
    }

    /** Total bytes held in the bin, used to warn before emptying it. */
    public static long totalSize(Context context) {
        long sum = 0;
        for (Entry e : list(context)) sum += e.size();
        return sum;
    }

    private static boolean copyRecursively(File from, File to) {
        try {
            if (from.isDirectory()) {
                if (!to.exists() && !to.mkdirs()) return false;
                File[] kids = from.listFiles();
                if (kids == null) return false;
                for (File k : kids) {
                    if (!copyRecursively(k, new File(to, k.getName()))) return false;
                }
                return true;
            }
            if (!to.getParentFile().mkdirs() && !to.getParentFile().exists()) return false;
            try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(from));
                 java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(to))) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean deleteRecursively(File f) {
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) deleteRecursively(k);
            }
            return f.delete();
        } catch (Exception e) {
            return false;
        }
    }
}