package io.github.abdurazaaqmohammed.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Private DEX working files are disposable only after their last consumer releases them. */
public final class DexWorkspaceStore {
    public static final String ROOT_NAME = "dex_workspaces";
    public static final String EXTRACT_PREFIX = "dexwork_";
    public static final String EDITOR_PREFIX = "dex_editor_";
    public static final long ORPHAN_GRACE_MS = TimeUnit.DAYS.toMillis(1);
    private static final String SOURCE_FILE = ".archive_source";
    private static final long RESULT_HANDOFF_MS = TimeUnit.HOURS.toMillis(1);
    private static final long PROCESS_STARTED = System.currentTimeMillis();
    private static final Map<String, Integer> users = new HashMap<>();
    private static final Map<String, File> deleteWhenUnused = new HashMap<>();
    private static final Map<String, File> deleteAfterResult = new HashMap<>();
    private static final Map<String, File> queuedDeletes = new HashMap<>();
    private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "dex-workspace-delete");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, PendingResult> pendingResults = new HashMap<>();
    private static File privateRoot;

    private DexWorkspaceStore() {}

    public static final class Lease implements AutoCloseable {
        public final File directory;
        private boolean closed;

        private Lease(File directory) {
            this.directory = directory;
        }

        @Override
        public void close() {
            synchronized (DexWorkspaceStore.class) {
                if (closed || directory == null) return;
                closed = true;
                String key = directory.getPath();
                int remaining = users.getOrDefault(key, 0) - 1;
                if (remaining > 0) users.put(key, remaining);
                else {
                    users.remove(key);
                    touch(directory);
                    File disposable = deleteWhenUnused.remove(key);
                    if (disposable != null) queueDelete(disposable);
                }
            }
        }
    }

    private record PendingResult(Lease lease, long created) {}

    public static synchronized void initialize(File filesDir) throws IOException {
        privateRoot = new File(filesDir, ROOT_NAME).getCanonicalFile();
    }

    public static synchronized Lease create(File filesDir, String prefix) throws IOException {
        if (!EXTRACT_PREFIX.equals(prefix) && !EDITOR_PREFIX.equals(prefix)) {
            throw new IllegalArgumentException("Unknown DEX workspace kind");
        }
        initialize(filesDir);
        File root = privateRoot;
        File directory = new File(root, prefix + UUID.randomUUID()).getCanonicalFile();
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create DEX workspace: " + directory);
        }
        return retain(directory);
    }

    /** A lease for non-workspace input files is a no-op; originals are never deleted here. */
    public static synchronized Lease retain(File path) {
        File directory = workspaceFor(path);
        if (directory != null) {
            String key = directory.getPath();
            users.put(key, users.getOrDefault(key, 0) + 1);
            touch(directory);
        }
        return new Lease(directory);
    }

    public static synchronized boolean isInUse(File path) {
        File directory = workspaceFor(path);
        return directory != null && users.getOrDefault(directory.getPath(), 0) > 0;
    }

    public static synchronized void deleteWhenUnused(File path) {
        File directory = workspaceFor(path);
        if (directory == null) return;
        if (isInUse(directory)) deleteWhenUnused.put(directory.getPath(), directory);
        else queueDelete(directory);
    }

    /** Bridges finish() to the posted activity result and its archive-update dialog. */
    public static synchronized void keepForResult(File output) {
        File directory = workspaceFor(output);
        if (directory == null || pendingResults.containsKey(directory.getPath())) return;
        pendingResults.put(directory.getPath(), new PendingResult(retain(directory), System.currentTimeMillis()));
    }

    public static synchronized void finishEditorInput(File output) {
        File directory = workspaceFor(output);
        if (directory == null) return;
        if (pendingResults.containsKey(directory.getPath())) {
            deleteAfterResult.put(directory.getPath(), directory);
        } else {
            deleteWhenUnused(directory);
        }
    }

    public static synchronized Lease takeResult(File output) {
        File directory = workspaceFor(output);
        PendingResult result = directory == null ? null : pendingResults.remove(directory.getPath());
        if (directory != null && deleteAfterResult.remove(directory.getPath()) != null) deleteWhenUnused(directory);
        return result != null ? result.lease : retain(output);
    }

    public static synchronized void finishResult(File output) {
        File directory = workspaceFor(output);
        PendingResult result = directory == null ? null : pendingResults.remove(directory.getPath());
        if (directory != null && deleteAfterResult.remove(directory.getPath()) != null) deleteWhenUnused(directory);
        if (result != null) result.lease.close();
    }

    public static synchronized File workspaceFor(File path) {
        if (path == null) return null;
        try {
            File candidate = path.getCanonicalFile();
            while (candidate != null) {
                File parent = candidate.getParentFile();
                if (privateRoot != null && privateRoot.equals(parent) && isWorkspaceName(candidate.getName())) {
                    return candidate;
                }
                candidate = parent;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean isWorkspaceName(String name) {
        return name.startsWith(EXTRACT_PREFIX) || name.startsWith(EDITOR_PREFIX);
    }

    public static void recordArchive(File directory, File archive) throws IOException {
        if (workspaceFor(directory) == null || archive == null) return;
        Properties properties = new Properties();
        properties.setProperty("archive", archive.getAbsolutePath());
        try (FileOutputStream output = new FileOutputStream(new File(directory, SOURCE_FILE))) {
            properties.store(output, null);
        }
    }

    public static File archiveFor(File staged) {
        File directory = workspaceFor(staged);
        if (directory == null) return null;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(new File(directory, SOURCE_FILE))) {
            properties.load(input);
            String path = properties.getProperty("archive");
            return path == null || path.isEmpty() ? null : new File(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static synchronized void cleanup(File filesDir, File cacheDir) {
        cleanup(filesDir, cacheDir, System.currentTimeMillis());
    }

    static synchronized void cleanup(File filesDir, File cacheDir, long now) {
        try {
            initialize(filesDir);
        } catch (IOException e) {
            return;
        }
        Iterator<PendingResult> results = pendingResults.values().iterator();
        while (results.hasNext()) {
            PendingResult result = results.next();
            if (now - result.created < RESULT_HANDOFF_MS) continue;
            results.remove();
            File directory = result.lease.directory;
            if (directory != null) deleteAfterResult.remove(directory.getPath());
            result.lease.close();
        }
        purge(new File(filesDir, ROOT_NAME), now);
        // Older releases used these names directly under filesDir/cacheDir.
        purge(filesDir, now);
        if (cacheDir != null) purge(cacheDir, now);
    }

    private static void purge(File root, long now) {
        try {
            root = root.getCanonicalFile();
        } catch (IOException e) {
            return;
        }
        File[] directories = root.listFiles();
        if (directories == null) return;
        for (File directory : directories) {
            if (!directory.isDirectory()) continue;
            boolean deleting = directory.getName().startsWith(".dex_delete_");
            if (!deleting && !isWorkspaceName(directory.getName())) continue;
            if (deleting && !privateRoot.equals(root)) continue;
            if (isInUse(directory) || now - directory.lastModified() < ORPHAN_GRACE_MS) continue;
            // A cold-started task may rebind an old workspace just after Application.onCreate.
            if (workspaceFor(directory) != null && now - PROCESS_STARTED < TimeUnit.MINUTES.toMillis(1)) continue;
            queueDelete(directory);
        }
    }

    private static void queueDelete(File directory) {
        String key = directory.getPath();
        if (queuedDeletes.put(key, directory) != null) return;
        cleanupExecutor.execute(() -> {
            File disposable = directory;
            synchronized (DexWorkspaceStore.class) {
                if (queuedDeletes.remove(key) == null) return;
                if (isInUse(directory)) {
                    deleteWhenUnused.put(key, directory);
                    return;
                }
                // Detach atomically before slow recursive deletion; a later open cannot claim
                // the old tree after the last lease was released, and no UI waits on deletion.
                File detached = new File(privateRoot, ".dex_delete_" + UUID.randomUUID());
                if (!privateRoot.isDirectory() && !privateRoot.mkdirs()) return;
                if (directory.exists()) {
                    if (!directory.renameTo(detached)) return;
                    disposable = detached;
                }
            }
            deleteTree(disposable);
        });
    }

    private static void touch(File directory) {
        if (directory.isDirectory()) directory.setLastModified(System.currentTimeMillis());
    }

    private static void deleteTree(File directory) {
        try {
            // Never follow a symlink out of the app-owned directory during cleanup.
            if (!directory.getCanonicalFile().equals(directory.getAbsoluteFile())) return;
            if (directory.isDirectory()) {
                File[] children = directory.listFiles();
                if (children != null) for (File child : children) deleteTree(child);
            }
            directory.delete();
        } catch (IOException ignored) {
        }
    }
}
