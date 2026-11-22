package io.github.abdurazaaqmohammed.plugins.themes;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Private store for ingested theme packs, pure {@link java.io} + Gson so it
 * is unit testable on the JVM. Layout:
 * {@code <filesDir>/theme_packs/<providerPackage>/} holding one
 * {@code snapshot.json} (parsed row data, which names the image files it
 * uses) plus the versioned {@code wallpaper-*}/{@code preview-*} images.
 * Only ever written through {@link ThemePacks#importAndSelect}.
 */
final class ThemePackStore {

    private static final String ROOT = "theme_packs";
    private static final String SNAPSHOT_FILE = "snapshot.json";

    /**
     * Store schema of a snapshot.json. Version 1 = the original field set
     * (v1 wire packs); version 2 adds {@code editorSyntax} and
     * {@code wallpaperScope}. Snapshots written before this field existed
     * read back as schema null, which {@link #readSnapshot} normalizes to
     * {@link #SCHEMA_V1}; an explicit 0 is treated the same way.
     */
    static final int SCHEMA_V1 = 1;
    static final int SCHEMA_V2 = 2;
    static final int SCHEMA_LATEST = SCHEMA_V2;

    /** Parsed /theme row as persisted (Gson round-trips this directly).
     *  Images are referenced by versioned file names so the snapshot.json
     *  rename is the single atomic swap point of an import. */
    static final class StoredSnapshot {
        @com.google.gson.annotations.SerializedName("signer") String signer;
        @com.google.gson.annotations.SerializedName("id") String id;
        @com.google.gson.annotations.SerializedName("name") String name;
        @com.google.gson.annotations.SerializedName("dark") Boolean dark;
        @com.google.gson.annotations.SerializedName("accent") Integer accent;
        @com.google.gson.annotations.SerializedName("scrim") Integer scrim;
        @com.google.gson.annotations.SerializedName("colors") Map<String, Integer> colors;
        @com.google.gson.annotations.SerializedName("strings") Map<String, Map<String, String>> strings;
        @com.google.gson.annotations.SerializedName("wallpaperName") String wallpaperName; // null = none
        @com.google.gson.annotations.SerializedName("previewName") String previewName;   // null = none
        /** Store schema; absent/0 on old snapshots, normalized to 1 on read. */
        @com.google.gson.annotations.SerializedName("schema") Integer schema;
        /** Monotonic per-pack counter, bumped on every successful commit. */
        @com.google.gson.annotations.SerializedName("revision") Long revision;
        /** Canonical editor syntax colors; null = none (v1 packs). */
        @com.google.gson.annotations.SerializedName("editorSyntax") Map<String, Integer> editorSyntax;
        /** Wallpaper application scope; null = files (v1 behavior). */
        @com.google.gson.annotations.SerializedName("wallpaperScope") String wallpaperScope;
    }

    private ThemePackStore() {
    }

    static File root(File filesDir) {
        return new File(filesDir, ROOT);
    }

    static File packDir(File filesDir, String providerPackage) {
        if (providerPackage == null || !providerPackage.matches("[A-Za-z0-9._]+")
                || providerPackage.startsWith(".") || providerPackage.endsWith(".")) {
            throw new IllegalArgumentException("Invalid provider package");
        }
        return new File(root(filesDir), providerPackage);
    }

    static File snapshotFile(File filesDir, String providerPackage) {
        return new File(packDir(filesDir, providerPackage), SNAPSHOT_FILE);
    }

    static File wallpaperFile(File filesDir, String providerPackage, String name) {
        return imageFile(filesDir, providerPackage, name, "wallpaper-");
    }

    static File previewFile(File filesDir, String providerPackage, String name) {
        return imageFile(filesDir, providerPackage, name, "preview-");
    }

    /** Resolves a stored image reference; null unless it matches the
     *  versioned {@code <kind>-<nanoTime>} names this store generates. */
    private static File imageFile(File filesDir, String providerPackage,
                                  String name, String prefix) {
        if (name == null || !name.startsWith(prefix)) return null;
        String suffix = name.substring(prefix.length());
        if (suffix.isEmpty() || !suffix.matches("[0-9]{1,19}")) return null;
        return new File(packDir(filesDir, providerPackage), name);
    }

    static void writeSnapshot(File filesDir, String providerPackage,
                              StoredSnapshot snapshot) throws IOException {
        File dir = packDir(filesDir, providerPackage);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create store dir");
        }
        File dest = snapshotFile(filesDir, providerPackage);
        File tmp = new File(dir, SNAPSHOT_FILE + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp),
                StandardCharsets.UTF_8)) {
            new Gson().toJson(snapshot, w);
        }
        dest.delete();
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("Cannot persist snapshot");
        }
    }

    /** Parsed snapshot, or null when missing/unreadable/malformed. Old
     *  snapshots are normalized: schema null/0 becomes {@link #SCHEMA_V1},
     *  revision null becomes 0, and an unknown wallpaperScope is forced to
     *  {@code files} so a corrupted field cannot widen what is painted. */
    static StoredSnapshot readSnapshot(File filesDir, String providerPackage) {
        File file = snapshotFile(filesDir, providerPackage);
        if (!file.isFile()) return null;
        try (InputStreamReader r = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            StoredSnapshot snapshot = new Gson().fromJson(r, StoredSnapshot.class);
            if (snapshot == null) return null;
            if (snapshot.schema == null || snapshot.schema <= 0) snapshot.schema = SCHEMA_V1;
            if (snapshot.revision == null || snapshot.revision < 0) snapshot.revision = 0L;
            if (snapshot.wallpaperScope != null
                    && !ThemePackContracts.isWallpaperScope(snapshot.wallpaperScope)) {
                snapshot.wallpaperScope = ThemePackContracts.WALLPAPER_SCOPE_FILES;
            }
            return snapshot;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Copies a stream into a unique staged file inside the pack directory,
     * enforcing {@code maxBytes}. Returns null and leaves no partial file
     * when the source is over the limit or IO fails.
     */
    static File stageFile(InputStream in, File packDir, long maxBytes) {
        if (!packDir.isDirectory() && !packDir.mkdirs()) return null;
        File staged = new File(packDir, "staged-" + System.nanoTime());
        try (OutputStream out = new FileOutputStream(staged)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Payload over limit");
                }
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            staged.delete();
            return null;
        }
        return staged;
    }

    /**
     * Commits a prepared import as one unit. Staged images are first
     * renamed to fresh versioned names that nothing references yet, then
     * {@code snapshot.json} (which names those files) is swapped atomically
     * with backup/rollback. A reader therefore always sees a consistent
     * snapshot/image pair. Staged files are consumed or removed either way.
     * A snapshot with a pre-assigned {@code wallpaper-}/{@code preview-}
     * name retains that already-stored file instead (byte-verified by the
     * caller) and rollback paths never delete retained images.
     */
    static boolean commit(File filesDir, String providerPackage,
                          StoredSnapshot snapshot,
                          File stagedWallpaper, File stagedPreview) {
        File dir;
        try {
            dir = packDir(filesDir, providerPackage);
        } catch (Exception e) {
            return false;
        }
        // A pre-assigned image name means "retain the already-stored file":
        // the caller byte-verified it against the staged content, so the
        // commit only swaps snapshot.json and every rollback path must leave
        // the retained (pre-existing) image untouched — the previous snapshot
        // being restored still references it.
        boolean retainedWallpaper = snapshot.wallpaperName != null;
        boolean retainedPreview = snapshot.previewName != null;
        // Fresh versioned names for newly staged files only.
        if (stagedWallpaper != null && !retainedWallpaper) {
            snapshot.wallpaperName = "wallpaper-" + System.nanoTime();
        }
        if (stagedPreview != null && !retainedPreview) {
            snapshot.previewName = "preview-" + System.nanoTime();
        }
        File newWallpaper = snapshot.wallpaperName == null
                ? null : new File(dir, snapshot.wallpaperName);
        File newPreview = snapshot.previewName == null
                ? null : new File(dir, snapshot.previewName);
        try {
            if (stagedWallpaper != null && !stagedWallpaper.renameTo(newWallpaper)) {
                throw new IOException("Cannot commit wallpaper");
            }
            if (stagedPreview != null && !stagedPreview.renameTo(newPreview)) {
                throw new IOException("Cannot commit preview");
            }
        } catch (Exception e) {
            if (!retainedWallpaper) deleteQuietly(newWallpaper);
            if (!retainedPreview) deleteQuietly(newPreview);
            deleteQuietly(stagedWallpaper);
            deleteQuietly(stagedPreview);
            return false;
        }
        // Atomic swap point: the snapshot names the new image files.
        File snap = snapshotFile(filesDir, providerPackage);
        File snapBak = new File(dir, SNAPSHOT_FILE + ".bak");
        if (!moveTo(snap, snapBak)) {
            if (!retainedWallpaper) deleteQuietly(newWallpaper);
            if (!retainedPreview) deleteQuietly(newPreview);
            deleteQuietly(stagedWallpaper);
            deleteQuietly(stagedPreview);
            return false;
        }
        try {
            writeSnapshot(filesDir, providerPackage, snapshot);
        } catch (Exception e) {
            deleteQuietly(new File(dir, SNAPSHOT_FILE + ".tmp"));
            if (!retainedWallpaper) deleteQuietly(newWallpaper);
            if (!retainedPreview) deleteQuietly(newPreview);
            moveTo(snapBak, snap); // previous snapshot (and its images) intact
            return false;
        }
        deleteQuietly(snapBak);
        // Drop images no longer referenced by the new snapshot.
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                if (name.startsWith("wallpaper-") || name.startsWith("preview-")) {
                    if (!name.equals(snapshot.wallpaperName)
                            && !name.equals(snapshot.previewName)) {
                        deleteQuietly(child);
                    }
                }
            }
        }
        return true;
    }

    /** Moves src to dest; true when src is absent or the move succeeded. */
    static boolean moveTo(File src, File dest) {
        if (!src.isFile()) return true;
        dest.delete();
        return src.renameTo(dest);
    }

    static void deleteQuietly(File file) {
        if (file != null) file.delete();
    }

    static void deletePack(File filesDir, String providerPackage) {
        File dir;
        try {
            dir = packDir(filesDir, providerPackage);
        } catch (Exception e) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) child.delete();
        }
        dir.delete();
    }
}
