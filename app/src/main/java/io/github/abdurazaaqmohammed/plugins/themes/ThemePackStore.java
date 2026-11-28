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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Private store for ingested theme packs, pure {@link java.io} + Gson so it
 * is unit testable on the JVM. Layout:
 * {@code <filesDir>/theme_packs/<providerPackage>/} holding one
 * {@code snapshot.json} (parsed row data, which names the image files it
 * uses) plus the versioned {@code wallpaper-*}/{@code preview-*} images and
 * {@code icon-<key>-<n>} icon PNGs. Only ever written through
 * {@link ThemePacks#importAndSelect} and {@link ThemePacks#activateLocal}.
 */
final class ThemePackStore {

    private static final String ROOT = "theme_packs";
    private static final String SNAPSHOT_FILE = "snapshot.json";

    /**
     * Store schema of a snapshot.json. Version 1 = the original field set
     * (v1 wire packs); version 2 adds {@code editorSyntax} and
     * {@code wallpaperScope}; version 3 adds {@code iconNames} and
     * {@code launcherIcon}. Snapshots written before this field existed
     * read back as schema null, which {@link #readSnapshot} normalizes to
     * {@link #SCHEMA_V1}; an explicit 0 is treated the same way.
     */
    static final int SCHEMA_V1 = 1;
    static final int SCHEMA_V2 = 2;
    static final int SCHEMA_V3 = 3;
    static final int SCHEMA_LATEST = SCHEMA_V3;

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
        /** v3: canonical icon key -> versioned stored PNG name; null/empty = none. */
        @com.google.gson.annotations.SerializedName("iconNames") Map<String, String> iconNames;
        /** v3: short opaque launcher icon selector ID (COLUMN_LAUNCHER_ICON,
         *  ≤ {@link ThemePackContracts#MAX_LAUNCHER_ICON_ID_CHARS}); null =
         *  none. Launcher helpers resolve artwork via the provider's
         *  {@code /launcher_icon} endpoint on demand. */
        @com.google.gson.annotations.SerializedName("launcherIcon") String launcherIcon;
    }

    private ThemePackStore() {
    }

    static File root(File filesDir) {
        return new File(filesDir, ROOT);
    }

    static File packDir(File filesDir, String providerPackage) {
        // The reserved "local." prefix hosts in-app local theme snapshots
        // created by the host theme editor. It contains no ':' so JVM unit
        // tests can create the directory on Windows too; a real installed
        // package with this exact prefix is rejected by the namespace checks
        // at selection time, and local keys are UUID-shaped so collisions
        // are improbable.
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

    /** Resolves a stored icon reference for {@code key}; null unless both the
     *  key is allowlisted and the name matches the versioned
     *  {@code icon-<key>-<nanoTime>} form this store generates. */
    static File iconFile(File filesDir, String providerPackage, String key, String name) {
        if (!ThemePackContracts.isIconKey(key)) return null;
        String prefix = "icon-" + key + "-";
        if (name == null || !name.startsWith(prefix)) return null;
        String suffix = name.substring(prefix.length());
        if (suffix.isEmpty() || !suffix.matches("[0-9]{1,19}")) return null;
        return new File(packDir(filesDir, providerPackage), name);
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
            if (snapshot.iconNames != null && !snapshot.iconNames.isEmpty()) {
                Map<String, String> clean = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : snapshot.iconNames.entrySet()) {
                    if (iconFile(filesDir, providerPackage, e.getKey(), e.getValue()) == null) {
                        continue; // corrupted/unknown entry: never resolve to a file
                    }
                    clean.put(e.getKey(), e.getValue());
                }
                snapshot.iconNames = clean.isEmpty() ? null : clean;
            }
            if (snapshot.launcherIcon != null
                    && (snapshot.launcherIcon.isEmpty()
                        || snapshot.launcherIcon.length()
                            > ThemePackContracts.MAX_LAUNCHER_ICON_ID_CHARS)) {
                snapshot.launcherIcon = null;
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

    /** Backward-compatible form used by legacy callers/tests with no icons. */
    static boolean commit(File filesDir, String providerPackage,
                          StoredSnapshot snapshot,
                          File stagedWallpaper, File stagedPreview) {
        return commit(filesDir, providerPackage, snapshot, stagedWallpaper,
                stagedPreview, Collections.<String, File>emptyMap());
    }

    /**
     * Commits a prepared import as one unit. Staged images are first
     * renamed to fresh versioned names that nothing references yet, then
     * {@code snapshot.json} (which names those files) is swapped atomically
     * with backup/rollback. A reader therefore always sees a consistent
     * snapshot/image pair. Staged files are consumed or removed either way.
     * A snapshot with a pre-assigned {@code wallpaper-}/{@code preview-}
     * name (or an {@code iconNames} entry) retains that already-stored file
     * instead (byte-verified by the caller) and rollback paths never delete
     * retained images.
     *
     * @param stagedIcons canonical icon key -> staged PNG (must be consumed
     *                    or deleted by the caller on failure)
     */
    static boolean commit(File filesDir, String providerPackage,
                          StoredSnapshot snapshot, File stagedWallpaper,
                          File stagedPreview, Map<String, File> stagedIcons) {
        File dir;
        try {
            dir = packDir(filesDir, providerPackage);
        } catch (Exception e) {
            return false;
        }
        // Staged icons may be renamed in from a DIFFERENT pack dir (the
        // import flow stages under a temporary key before the save assigns
        // the final one); the destination dir must exist before any rename.
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        if (stagedIcons == null) stagedIcons = Collections.emptyMap();
        // A pre-assigned image name means "retain the already-stored file":
        // the caller byte-verified it against the staged content, so the
        // commit only swaps snapshot.json and every rollback path must leave
        // the retained (pre-existing) image untouched — the previous snapshot
        // being restored still references it.
        boolean retainedWallpaper = snapshot.wallpaperName != null;
        boolean retainedPreview = snapshot.previewName != null;
        // Files created by this commit; every failure path removes them.
        java.util.List<File> newFiles = new java.util.ArrayList<>();
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
        // Icon names: pre-assigned (retained) entries stay as-is; every staged
        // icon gets a fresh versioned name derived from its canonical key.
        Map<String, String> iconNames = snapshot.iconNames == null
                ? null : new LinkedHashMap<>(snapshot.iconNames);
        Map<String, File> committedIcons = new LinkedHashMap<>();
        try {
            if (stagedWallpaper != null && !stagedWallpaper.renameTo(newWallpaper)) {
                throw new IOException("Cannot commit wallpaper");
            }
            if (stagedWallpaper != null) newFiles.add(newWallpaper);
            if (stagedPreview != null && !stagedPreview.renameTo(newPreview)) {
                throw new IOException("Cannot commit preview");
            }
            if (stagedPreview != null) newFiles.add(newPreview);
            for (Map.Entry<String, File> e : stagedIcons.entrySet()) {
                String key = e.getKey();
                if (!ThemePackContracts.isIconKey(key) || e.getValue() == null) continue;
                if (iconNames != null && iconNames.containsKey(key)) continue; // retained
                String name = "icon-" + key + "-" + System.nanoTime();
                File dest = new File(dir, name);
                if (!e.getValue().renameTo(dest)) {
                    throw new IOException("Cannot commit icon " + key);
                }
                newFiles.add(dest);
                if (iconNames == null) iconNames = new LinkedHashMap<>();
                iconNames.put(key, name);
                committedIcons.put(key, dest);
            }
        } catch (Exception e) {
            for (File f : newFiles) deleteQuietly(f);
            deleteStagedQuietly(stagedWallpaper);
            deleteStagedQuietly(stagedPreview);
            for (File f : stagedIcons.values()) deleteStagedQuietly(f);
            return false;
        }
        snapshot.iconNames = iconNames == null || iconNames.isEmpty() ? null : iconNames;
        // Atomic swap point: the snapshot names the new image files.
        File snap = snapshotFile(filesDir, providerPackage);
        File snapBak = new File(dir, SNAPSHOT_FILE + ".bak");
        if (!moveTo(snap, snapBak)) {
            for (File f : newFiles) deleteQuietly(f);
            deleteStagedQuietly(stagedWallpaper);
            deleteStagedQuietly(stagedPreview);
            for (File f : stagedIcons.values()) deleteStagedQuietly(f);
            return false;
        }
        try {
            writeSnapshot(filesDir, providerPackage, snapshot);
        } catch (Exception e) {
            deleteQuietly(new File(dir, SNAPSHOT_FILE + ".tmp"));
            for (File f : newFiles) deleteQuietly(f);
            for (File f : stagedIcons.values()) deleteStagedQuietly(f);
            moveTo(snapBak, snap); // previous snapshot (and its images) intact
            return false;
        }
        deleteQuietly(snapBak);
        // Drop images no longer referenced by the new snapshot.
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                boolean referenced = name.equals(snapshot.wallpaperName)
                        || name.equals(snapshot.previewName)
                        || (snapshot.iconNames != null
                            && snapshot.iconNames.containsValue(name));
                if (!referenced && (name.startsWith("wallpaper-")
                        || name.startsWith("preview-") || name.startsWith("icon-"))) {
                    deleteQuietly(child);
                }
            }
        }
        // Staged files consumed by rename no longer exist; delete any that
        // were skipped (e.g. retained icons) so the caller has no leftovers.
        // Only true staged temp files are removed — never stored assets.
        deleteStagedQuietly(stagedWallpaper);
        deleteStagedQuietly(stagedPreview);
        for (Map.Entry<String, File> e : stagedIcons.entrySet()) {
            if (!committedIcons.containsKey(e.getKey())) deleteStagedQuietly(e.getValue());
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

    /** Deletes a staged temp file. Only files this store created (the
     *  {@code staged-} prefix) are ever removed, so a caller passing a wrong
     *  reference can never delete a stored versioned asset. */
    static void deleteStagedQuietly(File file) {
        if (file != null && file.getName().startsWith("staged-")) file.delete();
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
