package io.github.abdurazaaqmohammed.privilege.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent client for {@code mpio serve}, providing sub-millisecond native
 * directory listings and single-path stats over an already-open NUL-framed pipe.
 */
final class MpioServeClient {

    @FunctionalInterface
    interface DaemonLauncher {
        Process launch(boolean globalNs) throws IOException;
    }

    record ServeDirStats(boolean timedOut, long bytes, long files, long dirs, long hidden, long errors) {
        boolean isCompleteMeasure() {
            return !timedOut && !(files == 0L && dirs == 0L && errors > 0L);
        }
    }

    private static final long STARTUP_TIMEOUT_MS = 5_000L;
    private static final long QUERY_TIMEOUT_MS = 10_000L;
    private static final int MAX_TOKEN_BYTES = 1024 * 1024;
    private static final int ENOENT = 2;
    private static final int ENOTDIR = 20;

    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mpio-serve-watchdog");
                t.setDaemon(true);
                return t;
            });

    private final DaemonLauncher launcher;
    private final Object lock = new Object();
    private final ByteArrayOutputStream tokenScratch = new ByteArrayOutputStream(512);

    private Process process;
    private OutputStream stdin;
    private BufferedInputStream stdout;
    private boolean sessionGlobalNs;
    private volatile int protocolVersion = 1;
    private volatile boolean unsupported;
    private volatile String lastStderr = "";

    MpioServeClient(DaemonLauncher launcher) {
        this.launcher = launcher;
    }

    boolean isUnsupported() {
        return unsupported;
    }

    boolean supportsDirSize() {
        return !unsupported && protocolVersion >= 2;
    }

    File[] listDir(String dirPath, boolean globalNs) {
        if (unsupported || dirPath == null || dirPath.isEmpty() || dirPath.indexOf('\0') >= 0) {
            return null;
        }
        String cleanParent = (dirPath.endsWith("/") && dirPath.length() > 1)
                ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
        synchronized (lock) {
            try {
                ensureStartedLocked(globalNs);
                final Process running = this.process;
                AtomicBoolean done = new AtomicBoolean(false);
                ScheduledFuture<?> timeout = WATCHDOG.schedule(() -> {
                    if (done.compareAndSet(false, true)) {
                        NativeProcess.destroy(running);
                    }
                }, QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                try {
                    sendCommandLocked((byte) 'L', dirPath);
                    File[] result = parseServeListStream(stdout, cleanParent, tokenScratch);
                    if (done.get()) {
                        closeLocked();
                        return null;
                    }
                    return result;
                } finally {
                    done.set(true);
                    timeout.cancel(false);
                }
            } catch (Exception e) {
                closeLocked();
                return null;
            }
        }
    }

    RootManager.RootEntry stat(String path, boolean globalNs) {
        if (unsupported || path == null || path.isEmpty() || path.indexOf('\0') >= 0) {
            return null;
        }
        synchronized (lock) {
            try {
                ensureStartedLocked(globalNs);
                final Process running = this.process;
                AtomicBoolean done = new AtomicBoolean(false);
                ScheduledFuture<?> timeout = WATCHDOG.schedule(() -> {
                    if (done.compareAndSet(false, true)) {
                        NativeProcess.destroy(running);
                    }
                }, QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                try {
                    sendCommandLocked((byte) 'S', path);
                    RootManager.RootEntry entry = parseServeStatResponse(stdout, path, tokenScratch);
                    if (done.get()) {
                        closeLocked();
                        return null;
                    }
                    return entry;
                } finally {
                    done.set(true);
                    timeout.cancel(false);
                }
            } catch (Exception e) {
                closeLocked();
                return null;
            }
        }
    }

    ServeDirStats dirStats(String dirPath, boolean globalNs, long timeoutMs) {
        if (unsupported || dirPath == null || dirPath.isEmpty() || dirPath.indexOf('\0') >= 0) {
            return null;
        }
        long clampedTimeout = Math.max(0L, timeoutMs);
        long watchdogMs = Math.max(QUERY_TIMEOUT_MS, clampedTimeout + 3_000L);
        synchronized (lock) {
            try {
                ensureStartedLocked(globalNs);
                if (protocolVersion < 2) {
                    return null;
                }
                final Process running = this.process;
                AtomicBoolean done = new AtomicBoolean(false);
                ScheduledFuture<?> timeout = WATCHDOG.schedule(() -> {
                    if (done.compareAndSet(false, true)) {
                        NativeProcess.destroy(running);
                    }
                }, watchdogMs, TimeUnit.MILLISECONDS);
                try {
                    sendTokensLocked("D", Long.toString(clampedTimeout), dirPath);
                    ServeDirStats stats = parseServeDirStats(stdout, tokenScratch);
                    if (done.get()) {
                        closeLocked();
                        return null;
                    }
                    return stats;
                } finally {
                    done.set(true);
                    timeout.cancel(false);
                }
            } catch (Exception e) {
                closeLocked();
                return null;
            }
        }
    }

    java.util.Map<String, ServeDirStats> dirSizesBatch(List<String> paths, boolean globalNs,
                                                       long perItemMs, long totalTimeoutMs) {
        return dirSizesBatch(paths, globalNs, perItemMs, totalTimeoutMs, null);
    }

    java.util.Map<String, ServeDirStats> dirSizesBatch(
            List<String> paths, boolean globalNs, long perItemMs, long totalTimeoutMs,
            java.util.function.BiConsumer<String, ServeDirStats> onItem) {
        if (unsupported || paths == null || paths.isEmpty()) {
            return null;
        }
        List<String> valid = new ArrayList<>(paths.size());
        for (String p : paths) {
            if (p != null && !p.isEmpty() && p.indexOf('\0') < 0) {
                valid.add(p);
            }
        }
        if (valid.isEmpty()) return null;

        long clampedPer = Math.max(100L, perItemMs);
        long clampedTotal = Math.max(clampedPer, totalTimeoutMs);
        long watchdogMs = Math.max(QUERY_TIMEOUT_MS, clampedTotal + 4_000L);
        synchronized (lock) {
            try {
                ensureStartedLocked(globalNs);
                if (protocolVersion < 2) {
                    return null;
                }
                final Process running = this.process;
                AtomicBoolean done = new AtomicBoolean(false);
                ScheduledFuture<?> timeout = WATCHDOG.schedule(() -> {
                    if (done.compareAndSet(false, true)) {
                        NativeProcess.destroy(running);
                    }
                }, watchdogMs, TimeUnit.MILLISECONDS);
                try {
                    sendBatchDirSizeLocked(valid, clampedPer, clampedTotal);
                    java.util.Map<String, ServeDirStats> out =
                            parseServeBatchDirStats(stdout, valid, tokenScratch, onItem);
                    if (done.get()) {
                        closeLocked();
                        return null;
                    }
                    return out;
                } finally {
                    done.set(true);
                    timeout.cancel(false);
                }
            } catch (Exception e) {
                closeLocked();
                return null;
            }
        }
    }

    void reset() {
        synchronized (lock) {
            closeLocked();
        }
    }

    private void ensureStartedLocked(boolean globalNs) throws IOException {
        if (process != null && NativeProcess.isAlive(process) && sessionGlobalNs == globalNs) {
            return;
        }
        closeLocked();
        Process started = launcher.launch(globalNs);
        if (started == null) throw new IOException("Cannot start mpio serve");
        lastStderr = "";
        Thread stderrThread = startStderrDrain(started);

        AtomicBoolean done = new AtomicBoolean(false);
        ScheduledFuture<?> timeout = WATCHDOG.schedule(() -> {
            if (done.compareAndSet(false, true)) {
                NativeProcess.destroy(started);
            }
        }, STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            BufferedInputStream in = new BufferedInputStream(started.getInputStream(), 65536);
            String header = readAsciiLine(in, 256);
            if (header == null || !header.startsWith("MPIO_SERVE ")) {
                try {
                    stderrThread.join(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                // Only cache permanent lack of "serve" support when the binary itself says so.
                if (lastStderr.contains("Unknown command")) {
                    unsupported = true;
                }
                NativeProcess.destroy(started);
                throw new IOException("mpio serve handshake failed: " + header);
            }
            int ver = parseIntSafe(header.substring("MPIO_SERVE ".length()).trim(), 1);
            this.protocolVersion = Math.max(1, ver);
            this.process = started;
            this.sessionGlobalNs = globalNs;
            this.stdout = in;
            this.stdin = new BufferedOutputStream(started.getOutputStream(), 4096);
        } catch (IOException e) {
            NativeProcess.destroy(started);
            throw e;
        } finally {
            done.set(true);
            timeout.cancel(false);
        }
    }

    private Thread startStderrDrain(final Process started) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() < 1024) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(line);
                    }
                    lastStderr = sb.toString();
                }
            } catch (Exception ignored) {
            }
        }, "mpio-serve-stderr");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void sendCommandLocked(byte op, String path) throws IOException {
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        stdin.write(op);
        stdin.write(0);
        stdin.write(pathBytes);
        stdin.write(0);
        stdin.flush();
    }

    private void sendTokensLocked(String... tokens) throws IOException {
        for (String t : tokens) {
            stdin.write(t.getBytes(StandardCharsets.UTF_8));
            stdin.write(0);
        }
        stdin.flush();
    }

    private void sendBatchDirSizeLocked(List<String> paths, long perItemMs, long totalTimeoutMs)
            throws IOException {
        stdin.write('B');
        stdin.write(0);
        stdin.write(Long.toString(perItemMs).getBytes(StandardCharsets.UTF_8));
        stdin.write(0);
        stdin.write(Long.toString(totalTimeoutMs).getBytes(StandardCharsets.UTF_8));
        stdin.write(0);
        stdin.write(Integer.toString(paths.size()).getBytes(StandardCharsets.UTF_8));
        stdin.write(0);
        for (String p : paths) {
            stdin.write(p.getBytes(StandardCharsets.UTF_8));
            stdin.write(0);
        }
        stdin.flush();
    }

    private void closeLocked() {
        if (stdin != null) {
            try {
                stdin.write('Q');
                stdin.write(0);
                stdin.flush();
            } catch (Exception ignored) {
            }
            try {
                stdin.close();
            } catch (Exception ignored) {
            }
            stdin = null;
        }
        if (stdout != null) {
            try {
                stdout.close();
            } catch (Exception ignored) {
            }
            stdout = null;
        }
        if (process != null) {
            NativeProcess.destroy(process);
            process = null;
        }
    }

    static ServeDirStats parseServeDirStats(InputStream in, ByteArrayOutputStream scratch)
            throws IOException {
        String tag = readNulToken(in, scratch);
        if (tag == null) throw new EOFException("Unexpected EOF during mpio dirsize");
        if ("ERR".equals(tag)) {
            readNulToken(in, scratch);
            return null;
        }
        if (!tag.startsWith("D ")) {
            throw new IOException("Malformed mpio dirsize frame: " + tag);
        }
        // Format: "D <timedOut> <bytes> <files> <dirs> <hidden> <errors>"
        String[] parts = splitFixedFields(tag, 7);
        if (parts == null) throw new IOException("Invalid mpio dirsize header: " + tag);
        boolean timedOut = "1".equals(parts[1]);
        long bytes = Math.max(0L, parseLongSafe(parts[2], 0L));
        long files = Math.max(0L, parseLongSafe(parts[3], 0L));
        long dirs = Math.max(0L, parseLongSafe(parts[4], 0L));
        long hidden = Math.max(0L, parseLongSafe(parts[5], 0L));
        long errors = Math.max(0L, parseLongSafe(parts[6], 0L));
        return new ServeDirStats(timedOut, bytes, files, dirs, hidden, errors);
    }

    static Map<String, ServeDirStats> parseServeBatchDirStats(InputStream in, List<String> paths,
                                                              ByteArrayOutputStream scratch)
            throws IOException {
        return parseServeBatchDirStats(in, paths, scratch, null);
    }

    static Map<String, ServeDirStats> parseServeBatchDirStats(
            InputStream in, List<String> paths, ByteArrayOutputStream scratch,
            java.util.function.BiConsumer<String, ServeDirStats> onItem)
            throws IOException {
        Map<String, ServeDirStats> out = new HashMap<>(paths.size());
        for (;;) {
            String tag = readNulToken(in, scratch);
            if (tag == null) throw new EOFException("Unexpected EOF during mpio batch dirsize");
            if ("OK".equals(tag)) {
                return out;
            }
            if ("ERR".equals(tag)) {
                readNulToken(in, scratch);
                return null;
            }
            if (!tag.startsWith("I ")) {
                throw new IOException("Malformed mpio batch dirsize frame: " + tag);
            }
            // Format: "I <idx> <timedOut> <bytes> <files> <dirs> <hidden> <errors>"
            String[] parts = splitFixedFields(tag, 8);
            if (parts == null) throw new IOException("Invalid mpio batch item header: " + tag);
            int idx = parseIntSafe(parts[1], -1);
            boolean timedOut = "1".equals(parts[2]);
            long bytes = Math.max(0L, parseLongSafe(parts[3], 0L));
            long files = Math.max(0L, parseLongSafe(parts[4], 0L));
            long dirs = Math.max(0L, parseLongSafe(parts[5], 0L));
            long hidden = Math.max(0L, parseLongSafe(parts[6], 0L));
            long errors = Math.max(0L, parseLongSafe(parts[7], 0L));
            if (idx >= 0 && idx < paths.size()) {
                String path = paths.get(idx);
                ServeDirStats item = new ServeDirStats(timedOut, bytes, files, dirs, hidden, errors);
                out.put(path, item);
                if (onItem != null) {
                    onItem.accept(path, item);
                }
            }
        }
    }

    static File[] parseServeListStream(InputStream in, String cleanParent, ByteArrayOutputStream scratch)
            throws IOException {
        List<File> files = new ArrayList<>();
        for (;;) {
            String tag = readNulToken(in, scratch);
            if (tag == null) throw new EOFException("Unexpected EOF during mpio list");
            if ("OK".equals(tag)) {
                return files.toArray(new File[0]);
            }
            if ("ERR".equals(tag)) {
                // Consume the errno token so the stream stays aligned for the next command.
                readNulToken(in, scratch);
                return null;
            }
            if (!tag.startsWith("E ")) {
                throw new IOException("Malformed mpio list frame: " + tag);
            }
            String name = readNulToken(in, scratch);
            if (name == null) throw new EOFException("Unexpected EOF reading mpio entry name");
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;

            RootFile entry = buildRootFileFromTag(cleanParent, name, tag);
            if (entry != null) files.add(entry);
        }
    }

    static RootManager.RootEntry parseServeStatResponse(InputStream in, String path, ByteArrayOutputStream scratch)
            throws IOException {
        String tag = readNulToken(in, scratch);
        if (tag == null) throw new EOFException("Unexpected EOF during mpio stat");
        if ("ERR".equals(tag)) {
            String errStr = readNulToken(in, scratch);
            int errno = parseIntSafe(errStr, -1);
            if (errno == ENOENT || errno == ENOTDIR) {
                return new RootManager.RootEntry(path, false, false, false, 0L, 0L, null);
            }
            return null;
        }
        if (!tag.startsWith("S ")) {
            throw new IOException("Malformed mpio stat frame: " + tag);
        }
        // Format: "S <exists> <isDir> <isFile> <isLink> <size> <mtimeMs> <mode>"
        String[] parts = splitFixedFields(tag, 8);
        if (parts == null) throw new IOException("Invalid mpio stat header: " + tag);
        boolean exists = "1".equals(parts[1]);
        boolean isDir = "1".equals(parts[2]);
        boolean isFile = "1".equals(parts[3]);
        long size = parseLongSafe(parts[5], 0L);
        long mtime = parseLongSafe(parts[6], 0L);
        String mode = "-".equals(parts[7]) ? null : parts[7];
        return new RootManager.RootEntry(path, exists, isDir, isFile,
                isDir ? 0L : Math.max(0L, size), Math.max(0L, mtime), mode);
    }

    private static RootFile buildRootFileFromTag(String cleanParent, String name, String tag) throws IOException {
        // Format: "E <isDir> <isFile> <isLink> <size> <mtimeMs> <mode>"
        String[] parts = splitFixedFields(tag, 7);
        if (parts == null) throw new IOException("Invalid mpio entry header: " + tag);
        boolean isDir = "1".equals(parts[1]);
        boolean isFile = "1".equals(parts[2]);
        boolean isLink = "1".equals(parts[3]);
        long size = parseLongSafe(parts[4], 0L);
        long mtime = parseLongSafe(parts[5], 0L);
        String mode = "-".equals(parts[6]) ? null : parts[6];
        return new RootFile(cleanParent, name, true, isDir, isFile,
                isDir ? 0L : Math.max(0L, size), Math.max(0L, mtime), mode, isLink);
    }

    private static String[] splitFixedFields(String line, int count) {
        String[] out = new String[count];
        int n = line.length();
        int idx = 0;
        int pos = 0;
        while (pos < n && idx < count - 1) {
            while (pos < n && line.charAt(pos) == ' ') pos++;
            int start = pos;
            while (pos < n && line.charAt(pos) != ' ') pos++;
            if (pos == start) return null;
            out[idx++] = line.substring(start, pos);
        }
        while (pos < n && line.charAt(pos) == ' ') pos++;
        if (pos >= n || idx != count - 1) return null;
        int end = pos;
        while (end < n && line.charAt(end) != ' ') end++;
        out[idx] = line.substring(pos, end);
        return out;
    }

    private static String readAsciiLine(InputStream in, int maxBytes) throws IOException {
        StringBuilder sb = new StringBuilder(32);
        while (sb.length() < maxBytes) {
            int b = in.read();
            if (b == -1) return sb.length() == 0 ? null : sb.toString();
            if (b == '\n') return sb.toString();
            if (b != '\r') sb.append((char) b);
        }
        return sb.toString();
    }

    private static String readNulToken(InputStream in, ByteArrayOutputStream scratch) throws IOException {
        scratch.reset();
        int count = 0;
        for (;;) {
            int b = in.read();
            if (b == -1) {
                if (count == 0) return null;
                throw new EOFException("Truncated NUL token in mpio stream");
            }
            if (b == 0) {
                return scratch.toString(StandardCharsets.UTF_8.name());
            }
            if (++count > MAX_TOKEN_BYTES) {
                throw new IOException("mpio token exceeded size cap");
            }
            scratch.write(b);
        }
    }

    private static long parseLongSafe(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
