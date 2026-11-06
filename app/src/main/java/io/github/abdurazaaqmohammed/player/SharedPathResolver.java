package io.github.abdurazaaqmohammed.player;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * Fast replacement for the old LocateActivity path search.
 *
 * <p>Order is cheapest-first and every step is an indexed provider query or a
 * direct file check. The unbounded recursive disk walk that made "Locate" slow
 * is now a last resort: time-boxed, bounded, and cancellable. The single most
 * important speedup is using {@code RELATIVE_PATH + DISPLAY_NAME} from
 * MediaStore (API 29+) to reconstruct the path instead of walking the disk.
 */
public final class SharedPathResolver {

    /**
     * Hard stop for the last-resort disk walk so a huge tree can't hang the UI.
     * This budget is shared by every expensive step (package data dirs + walk)
     * so a no-hit resolution can't stack several full budgets.
     */
    private static final long DISK_WALK_BUDGET_MS = 5000L;
    private static final int DISK_WALK_MAX_DIRS = 4000;
    private static final int DISK_WALK_MAX_DEPTH = 6;

    /** Elevated walks shell out per directory, so keep this budget much tighter. */
    private static final int ELEVATED_MAX_DIRS = 600;
    private static final int ELEVATED_MAX_DEPTH = 8;

    private static final String[] LIKELY_DIRS = {
            "Download",
            "Documents",
            "Pictures",
            "DCIM",
            "Movies",
            "Music",
            "Android/media",
            "WhatsApp",
            "Telegram",
    };

    /** Cancellation hook so the activity can stop the walk in onDestroy. */
    public interface Cancel {
        boolean isCancelled();
    }

    public static final class Result {
        public String path;
        /** True when the path exists but only through root/shizuku. */
        public boolean needsElevated;
        /** True when the path is under a protected tree and no backend can read it. */
        public boolean inaccessible;
        /**
         * True when the path is only a same-name guess (name search without a
         * size confirmation). The UI must label these "Potential match" instead
         * of presenting them as the definite file.
         */
        public boolean potential;
        public String displayName;
        public long size = -1;
    }

    /** A name-search hit plus whether the size confirmed it. */
    private static final class Hit {
        final String path;
        final boolean sizeMatched;

        Hit(String path, boolean sizeMatched) {
            this.path = path;
            this.sizeMatched = sizeMatched;
        }
    }

    private SharedPathResolver() {
    }

    public static Result resolve(Context ctx, Uri uri, Cancel cancel) {
        return resolve(ctx, uri, null, cancel);
    }

    /**
     * @param sourcePackage package of the app that shared the content, if known
     *                      (lets us search its private data dir first instead of
     *                      guessing by file name across all of storage).
     */
    public static Result resolve(Context ctx, Uri uri, String sourcePackage, Cancel cancel) {
        Result r = new Result();
        if (ctx == null || uri == null) return r;
        // One shared budget for every expensive step (package data dirs + disk
        // walk). Previously each step got its own full budget, so a miss could
        // burn two to three budgets before returning.
        long deadline = System.currentTimeMillis() + DISK_WALK_BUDGET_MS;
        try {
            fillMeta(ctx, uri, r);
        } catch (Exception ignored) {
        }

        // 1. file:// — direct, no query at all. The exact path is known.
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String p = uri.getPath();
                if (p != null && !p.isEmpty() && accept(ctx, p, r)) {
                    r.potential = false;
                    return r;
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Document Uris — decoded, not searched.
        try {
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                String p = resolveDocument(ctx, uri, r);
                if (p != null && accept(ctx, p, r)) {
                    r.potential = false;
                    return r;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Deprecated _data column — one indexed query, still instant where present.
        try {
            String p = queryDataColumn(ctx, uri);
            if (p != null && accept(ctx, p, r)) {
                r.potential = false;
                return r;
            }
        } catch (Exception ignored) {
        }

        // 4. MediaStore RELATIVE_PATH reconstruction — the fast path that replaces
        // the old disk walk for gallery/downloads shares on Android 10+.
        // This row belongs to the shared content itself, so it is confirmed.
        try {
            String p = resolveViaRelativePath(ctx, uri, r);
            if (p != null && accept(ctx, p, r)) {
                r.potential = false;
                return r;
            }
        } catch (Exception ignored) {
        }

        // 5. Indexed DISPLAY_NAME lookup across the MediaStore collections.
        // Confident only when the size confirms it is the same file; otherwise
        // the newest same-name row is kept as a potential match. This runs
        // BEFORE the expensive searches below: it is one indexed query, and a
        // hit here usually makes them unnecessary.
        Hit potential = null;
        try {
            if (r.displayName != null && !r.displayName.isEmpty()) {
                Hit hit = queryMediaStoreByName(ctx, r.displayName, r.size);
                if (hit != null && hit.sizeMatched && accept(ctx, hit.path, r)) {
                    r.potential = false;
                    return r;
                }
                if (hit != null) potential = hit;
            }
        } catch (Exception ignored) {
        }

        // 6. The sharing app's own data dirs, searched through the same elevated
        // backend the file manager uses to browse /data/data. Scoped to one
        // package, so a name hit here is far more trustworthy than a global
        // same-name match — it replaces the MediaStore guess above. Skipped when
        // the source app is unknown or the time budget is already spent.
        try {
            if (RootManager.isPackageNameValid(sourcePackage) && !isCancelled(cancel)) {
                Hit hit = searchPackageDataDirs(ctx, sourcePackage, nameForSearch(uri, r),
                        r.size, cancel, deadline);
                if (hit != null && accept(ctx, hit.path, r)) {
                    r.potential = !hit.sizeMatched;
                    return r;
                }
            }
        } catch (Exception ignored) {
        }
        if (potential != null) {
            // A same-name candidate already exists; burning seconds walking the
            // disk for another guess is what made Locate feel slow. Prefer the
            // indexed candidate and stop.
            if (accept(ctx, potential.path, r)) {
                r.potential = true;
                return r;
            }
        }

        // 7. Last resort: bounded walk of the likely dirs only. Same-name hits
        // here are guesses unless the size matches.
        try {
            String name = nameForSearch(uri, r);
            if (name != null && !name.isEmpty() && !isCancelled(cancel)) {
                Hit hit = boundedWalk(ctx, name, r.size, cancel, deadline);
                if (hit != null && accept(ctx, hit.path, r)) {
                    r.potential = !hit.sizeMatched;
                    return r;
                }
            }
        } catch (Exception ignored) {
        }
        return r;
    }

    private static String nameForSearch(Uri uri, Result r) {
        if (r.displayName != null && !r.displayName.isEmpty()) return r.displayName;
        try {
            if (uri.getLastPathSegment() != null) {
                return new File(uri.getLastPathSegment()).getName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ steps

    private static String resolveDocument(Context ctx, Uri uri, Result r) {
        String doc;
        try {
            doc = DocumentsContract.getDocumentId(uri);
        } catch (Exception e) {
            return null;
        }
        if (doc == null) return null;
        String authority = uri.getAuthority();

        if ("com.android.externalstorage.documents".equals(authority)) {
            int colon = doc.indexOf(':');
            String volume = colon < 0 ? "primary" : doc.substring(0, colon);
            String rel = colon < 0 ? "" : doc.substring(colon + 1);
            File base = "primary".equalsIgnoreCase(volume)
                    ? Environment.getExternalStorageDirectory()
                    : new File("/storage/" + volume);
            File f = rel.isEmpty() ? base : new File(base, rel);
            return f.getAbsolutePath();
        }
        if ("com.android.providers.downloads.documents".equals(authority)) {
            if (doc.startsWith("raw:")) return doc.substring(4);
            // msf:/msd: ids carry no numeric row — fall through to name lookup.
            try {
                long id = Long.parseLong(doc);
                Uri content = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), id);
                String p = queryDataColumn(ctx, content);
                if (p != null) return p;
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
        if ("com.android.providers.media.documents".equals(authority)) {
            int colon = doc.indexOf(':');
            if (colon > 0) {
                String type = doc.substring(0, colon);
                String id = doc.substring(colon + 1);
                Uri collection = mediaCollectionFor(type);
                if (collection != null) {
                    try {
                        Uri content = ContentUris.withAppendedId(collection, Long.parseLong(id));
                        String p = queryDataColumn(ctx, content);
                        if (p != null) return p;
                        // _data is null on Android 10+; reuse the relative-path step.
                        return resolveViaRelativePath(ctx, content, r);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static Uri mediaCollectionFor(String type) {
        if ("image".equals(type)) return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        if ("video".equals(type)) return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        if ("audio".equals(type)) return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        if (Build.VERSION.SDK_INT >= 29
                && ("document".equals(type) || "download".equals(type) || "file".equals(type))) {
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }
        if (Build.VERSION.SDK_INT >= 29) return MediaStore.Files.getContentUri("external");
        return null;
    }

    private static String queryDataColumn(Context ctx, Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DATA};
        try (Cursor c = ctx.getContentResolver().query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (idx >= 0) {
                    String p = c.getString(idx);
                    if (p != null && !p.isEmpty()) return p;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Rebuilds {@code /storage/emulated/0/<RELATIVE_PATH>/<DISPLAY_NAME>} from a
     * single MediaStore query. One indexed lookup instead of a filesystem walk.
     */
    private static String resolveViaRelativePath(Context ctx, Uri uri, Result r) {
        if (Build.VERSION.SDK_INT < 29) return null;
        String[] projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
        };
        try (Cursor c = ctx.getContentResolver().query(uri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            int dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
            if (dataIdx >= 0) {
                String p = c.getString(dataIdx);
                if (p != null && !p.isEmpty()) return p;
            }
            int nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
            String name = nameIdx >= 0 ? c.getString(nameIdx) : r.displayName;
            String rel = relIdx >= 0 ? c.getString(relIdx) : null;
            if (name == null || name.isEmpty()) return null;
            File ext = Environment.getExternalStorageDirectory();
            if (ext == null) return null;
            File candidate = (rel == null || rel.isEmpty())
                    ? new File(ext, name) : new File(new File(ext, rel), name);
            return candidate.getAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Searches the sharing app's private data dirs through the elevated backend
     * — the same {@code listWithStat} call the file manager uses to browse
     * {@code /data/data}. Plain {@code File.listFiles()} always returns null
     * there for the app uid, which is why the old resolver never found these.
     */
    private static Hit searchPackageDataDirs(Context ctx, String pkg, String name,
                                             long size, Cancel cancel, long deadline) {
        if (name == null || name.isEmpty()) return null;
        boolean elevated;
        try {
            elevated = AccessManager.fileOpsOn(ctx);
        } catch (Exception e) {
            elevated = false;
        }
        List<String> bases = new ArrayList<>(4);
        bases.add("/data/user/0/" + pkg);
        bases.add("/data/user_de/0/" + pkg);
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null) bases.add(new File(ext, "Android/data/" + pkg).getAbsolutePath());
        } catch (Exception ignored) {
        }
        // /data/data/<pkg> is the same directory as /data/user/0/<pkg> on
        // modern Android; walking both doubled the elevated work for nothing.
        bases.add("/data/data/" + pkg);
        int[] visited = {0};
        boolean userZeroReachable = false;
        for (String base : bases) {
            if (isCancelled(cancel) || System.currentTimeMillis() > deadline) return null;
            boolean reachable;
            try {
                reachable = new File(base).isDirectory() || AccessManager.exists(ctx, base);
            } catch (Exception e) {
                reachable = false;
            }
            if (!reachable) continue;
            // Without an elevated backend only the external dir is listable.
            if (!elevated && base.startsWith("/data/")) continue;
            if (base.startsWith("/data/user/0/")) userZeroReachable = true;
            else if (base.equals("/data/data/" + pkg) && userZeroReachable) continue;
            Hit hit = searchElevatedTree(ctx, base, name, size, cancel, deadline, visited);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * Iterative bounded DFS using the elevated listing backend (root/shizuku),
     * falling back to plain listing for world-readable trees. Mirrors how the
     * file manager lists protected dirs instead of relying on the app uid.
     */
    private static Hit searchElevatedTree(Context ctx, String base, String name, long size,
                                          Cancel cancel, long deadline, int[] visited) {
        Deque<String> paths = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        paths.push(base);
        depths.push(0);
        Hit nameMatch = null;
        while (!paths.isEmpty()) {
            if (isCancelled(cancel) || System.currentTimeMillis() > deadline) return nameMatch;
            if (visited[0]++ > ELEVATED_MAX_DIRS) return nameMatch;
            String dir = paths.pop();
            int depth = depths.pop();
            if (depth > ELEVATED_MAX_DEPTH) continue;
            if (isOwnStagingDir(ctx, dir)) continue;
            File[] entries = listDirAny(ctx, dir);
            if (entries == null) continue;
            for (File child : entries) {
                if (isCancelled(cancel) || System.currentTimeMillis() > deadline) return nameMatch;
                String childName;
                boolean isDir;
                long len;
                try {
                    childName = child.getName();
                    isDir = child.isDirectory();
                    len = child.length();
                } catch (Exception ignored) {
                    continue;
                }
                if (isDir) {
                    if (depth < ELEVATED_MAX_DEPTH) {
                        paths.push(dir + "/" + childName);
                        depths.push(depth + 1);
                    }
                } else if (name.equals(childName)) {
                    if (size > 0 && len == size) return new Hit(dir + "/" + childName, true);
                    if (nameMatch == null) nameMatch = new Hit(dir + "/" + childName, false);
                }
            }
        }
        return nameMatch;
    }

    /** Plain listing first (free); elevated shell listing for protected trees. */
    private static File[] listDirAny(Context ctx, String dir) {
        try {
            File[] plain = new File(dir).listFiles();
            if (plain != null && plain.length > 0) return plain;
            File[] elevated = AccessManager.listWithStat(ctx, dir);
            if (elevated != null && elevated.length > 0) return elevated;
            return plain != null ? plain : elevated;
        } catch (Exception e) {
            try {
                return AccessManager.listWithStat(ctx, dir);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static Hit queryMediaStoreByName(Context ctx, String name, long size) {
        List<Uri> collections = new ArrayList<>(5);
        collections.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        collections.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        collections.add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        if (Build.VERSION.SDK_INT >= 29) {
            collections.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI);
            collections.add(MediaStore.Files.getContentUri("external"));
        }
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] args = {name};
        String[] projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
        };
        Hit best = null;
        for (Uri collection : collections) {
            // Newest row first: when size cannot confirm anything, the most
            // recently modified same-name file is the likeliest match (the
            // first-found rule used to hand back any ancient copy).
            try (Cursor c = ctx.getContentResolver().query(
                    collection, projection, selection, args,
                    MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
                if (c == null || c.getColumnIndex(MediaStore.MediaColumns.DATA) < 0) continue;
                int dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                int sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE);
                while (c.moveToNext()) {
                    String p = c.getString(dataIdx);
                    if (p == null || p.isEmpty()) continue;
                    if (size > 0 && sizeIdx >= 0 && c.getLong(sizeIdx) == size) {
                        if (new File(p).exists()) return new Hit(p, true);
                        // Size confirms the row but the file is gone; keep it
                        // as a better-than-nothing candidate for this collection.
                        if (best == null) best = new Hit(p, false);
                    } else if (best == null) {
                        best = new Hit(p, false);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    private static Hit boundedWalk(Context ctx, String name, long size, Cancel cancel, long deadline) {
        int[] visited = {0};
        Hit nameMatch = null;
        for (String root : candidateRoots(ctx)) {
            if (isCancelled(cancel) || System.currentTimeMillis() > deadline) return nameMatch;
            File rootFile = new File(root);
            if (!rootFile.isDirectory()) continue;
            Hit hit = searchDir(ctx, rootFile, name, size, 0, visited, deadline, cancel);
            if (hit != null) {
                if (hit.sizeMatched) return hit;
                if (nameMatch == null) nameMatch = hit;
            }
        }
        return nameMatch;
    }

    /**
     * Copies this app staged in its own cache dirs (previous shares, editor
     * scratch files). Locating the "original" there would hand back our own
     * leftover temp copy.
     */
    private static boolean isOwnStagingDir(Context ctx, String path) {
        try {
            File cache = ctx.getCacheDir();
            if (cache != null && path.startsWith(cache.getAbsolutePath())) return true;
            for (File dir : ctx.getExternalCacheDirs()) {
                if (dir != null && path.startsWith(dir.getAbsolutePath())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Hit searchDir(Context ctx, File dir, String name, long size, int depth,
                                 int[] visited, long deadline, Cancel cancel) {
        if (depth > DISK_WALK_MAX_DEPTH) return null;
        if (isCancelled(cancel) || System.currentTimeMillis() > deadline) return null;
        if (visited[0]++ > DISK_WALK_MAX_DIRS) return null;
        if (isOwnStagingDir(ctx, dir.getAbsolutePath())) return null;
        File[] children;
        try {
            children = dir.listFiles();
        } catch (Exception e) {
            return null;
        }
        if (children == null) return null;
        Hit nameMatch = null;
        for (File child : children) {
            if (isCancelled(cancel) || System.currentTimeMillis() > deadline) return nameMatch;
            try {
                if (child.isDirectory()) {
                    Hit found = searchDir(ctx, child, name, size, depth + 1, visited, deadline, cancel);
                    if (found != null) {
                        if (found.sizeMatched) return found;
                        if (nameMatch == null) nameMatch = found;
                    }
                } else if (child.getName().equals(name)) {
                    if (size > 0 && child.length() == size) {
                        return new Hit(child.getAbsolutePath(), true);
                    }
                    // Among same-name guesses prefer the most recently modified
                    // file instead of whichever the walk stumbled on first.
                    if (nameMatch == null
                            || child.lastModified() > new File(nameMatch.path).lastModified()) {
                        nameMatch = new Hit(child.getAbsolutePath(), false);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return nameMatch;
    }

    private static List<String> candidateRoots(Context ctx) {
        Set<String> roots = new LinkedHashSet<>();
        try {
            File external = Environment.getExternalStorageDirectory();
            if (external != null) {
                for (String dir : LIKELY_DIRS) {
                    roots.add(new File(external, dir).getAbsolutePath());
                }
                roots.add(external.getAbsolutePath());
            }
        } catch (Exception ignored) {
        }
        try {
            for (File dir : ctx.getExternalFilesDirs(null)) {
                if (dir == null) continue;
                File storage = dir;
                for (int i = 0; i < 4 && storage != null; i++) storage = storage.getParentFile();
                if (storage != null && !storage.getAbsolutePath().startsWith("/storage/Android")) {
                    for (String rel : LIKELY_DIRS) {
                        roots.add(new File(storage, rel).getAbsolutePath());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(roots);
    }

    // ------------------------------------------------------------------ helpers

    private static void fillMeta(Context ctx, Uri uri, Result r) {
        String[] projection = {
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
        };
        try (Cursor c = ctx.getContentResolver().query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) r.displayName = c.getString(nameIdx);
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                    long s = c.getLong(sizeIdx);
                    if (s > 0) r.size = s;
                }
            }
        } catch (Exception ignored) {
        }
        if (r.displayName == null || r.displayName.isEmpty()) {
            try {
                String last = uri.getLastPathSegment();
                if (last != null) r.displayName = new File(last).getName();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Records the path and classifies protected trees: anything the app uid
     * cannot stat under /data (or similar) is only locatable with an elevated
     * backend — otherwise it is reported as inaccessible instead of pretending
     * a disk walk could find it.
     */
    private static boolean accept(Context ctx, String path, Result r) {
        if (path == null || path.isEmpty()) return false;
        try {
            if (new File(path).exists()) {
                r.path = path;
                r.needsElevated = isProtected(path);
                r.inaccessible = false;
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            if (AccessManager.exists(ctx, path)) {
                r.path = path;
                r.needsElevated = true;
                r.inaccessible = false;
                return true;
            }
        } catch (Exception ignored) {
        }
        if (isProtected(path)) {
            boolean elevated;
            try {
                elevated = AccessManager.fileOpsOn(ctx);
            } catch (Exception e) {
                elevated = false;
            }
            if (!elevated) {
                // Remember the path so the UI can explain *why* locate is off,
                // but don't treat it as resolved.
                if (r.path == null) r.path = path;
                r.needsElevated = true;
                r.inaccessible = true;
            }
        }
        return false;
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/data/")
                || path.equals("/data")
                || path.startsWith("/config/")
                || path.startsWith("/sys/")
                || path.startsWith("/proc/");
    }

    private static boolean isCancelled(Cancel cancel) {
        try {
            return cancel != null && cancel.isCancelled();
        } catch (Exception e) {
            return true;
        }
    }
}
