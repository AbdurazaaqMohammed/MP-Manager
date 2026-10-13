package io.github.abdurazaaqmohammed.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * File operations over an ADB {@code shell} (uid 2000), exposed with the same
 * shape as {@code java.io.File}.
 *
 * <p>This is the piece that makes the ADB channel useful to a file manager. A
 * normal app is sealed inside its own sandbox. The ADB server, when wireless
 * debugging is on, hands out a {@code shell} (uid 2000) session, and that user
 * sits outside this app's sandbox, so it can reach shared locations the app
 * cannot — {@code /data/local/tmp}, {@code /sdcard}, {@code /system}. This class
 * wires that session up to the handful of operations a browser needs: list,
 * stat, read, write, mkdir, delete, exists. It is not root: SELinux still
 * applies the shell policy, and other apps' private {@code /data/data} trees
 * usually stay off-limits on production builds.
 *
 * <p>One connection detail matters. The ADB server runs one service at a time
 * per socket: the {@code sync:} stream stays open for the life of this object
 * and answers every {@link AdbSync} command in a loop (that is how adbd handles
 * a sequence of push/pull/stat), while {@code mkdir} and {@code rm} — which the
 * sync protocol has no command for — each open their own short-lived
 * {@code shell:} connection instead of fighting the persistent sync one.
 */
public final class AdbFileService implements AutoCloseable {

    /** Unix mode used for newly created files, mirroring a normal 0644 file. */
    private static final int DEFAULT_FILE_MODE = 0644;

    private final AdbClient syncClient;
    private final AdbSync sync;

    private AdbFileService(AdbClient syncClient, AdbSync sync) {
        this.syncClient = syncClient;
        this.sync = sync;
    }

    /**
     * Open a session against the local ADB server.
     *
     * @throws IOException when no authorized server is listening, which is the
     *     normal case until wireless debugging has been switched on
     */
    public static AdbFileService open() throws IOException {
        AdbClient client = AdbClient.connect();
        InputStream in = client.openService("local", "sync:");
        return new AdbFileService(client, new AdbSync(in, client.writer()));
    }

    /** True when a path exists (either a file or a directory). */
    public boolean exists(String path) throws IOException {
        return sync.stat(path) != null;
    }

    /** True when a path exists and is a directory. */
    public boolean isDirectory(String path) throws IOException {
        AdbSync.Entry entry = sync.stat(path);
        return entry != null && entry.isDirectory();
    }

    /** Return the names directly inside a directory. */
    public String[] list(String path) throws IOException {
        List<AdbSync.Entry> entries = sync.list(path);
        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            names[i] = entries.get(i).name;
        }
        return names;
    }

    /** Return full stat entries for the children of a directory. */
    public List<AdbSync.Entry> listFiles(String path) throws IOException {
        return new ArrayList<>(sync.list(path));
    }

    /** Read a whole file into memory. */
    public byte[] read(String path) throws IOException {
        return sync.pull(path);
    }

    /** Write bytes to a path, creating or overwriting it. */
    public void write(String path, byte[] data) throws IOException {
        sync.push(path, data, DEFAULT_FILE_MODE);
    }

    /** Create a directory, including any missing parents. */
    public boolean mkdir(String path) throws IOException {
        return runShell("mkdir -p " + shellQuote(path));
    }

    /** Remove a file, or a directory and everything under it. */
    public boolean delete(String path) throws IOException {
        return runShell("rm -rf " + shellQuote(path));
    }

    /**
     * Run one shell command on a fresh connection.
     *
     * <p>The persistent socket here is already holding the {@code sync:} service,
     * and an ADB connection carries one service at a time, so a command that only
     * {@code shell:} can express gets its own connection and is torn down right
     * after. These are user-driven actions, so the connect cost is invisible.
     */
    private boolean runShell(String command) throws IOException {
        try (AdbClient client = AdbClient.connect()) {
            return client.shell(command).isEmpty();
        }
    }

    /** Close the underlying session. */
    @Override
    public void close() {
        syncClient.close();
    }

    /**
     * Wrap a path for the shell.
     *
     * <p>Paths coming from a file tree can hold spaces or, worse, shell
     * metacharacters. Single-quoting is the safe way to pass them through: the
     * shell treats everything inside the quotes literally, and the only thing
     * that needs escaping is an embedded single quote itself.
     */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
