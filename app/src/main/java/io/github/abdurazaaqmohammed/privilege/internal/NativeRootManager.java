package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.privilege.internal.NativeCopyProcess;

import io.github.abdurazaaqmohammed.privilege.api.NativeDeleteException;
import io.github.abdurazaaqmohammed.privilege.internal.NativeProcess;
import io.github.abdurazaaqmohammed.privilege.internal.NativeScanResult;
import io.github.abdurazaaqmohammed.privilege.internal.RootManager;
import io.github.abdurazaaqmohammed.util.io.DirectoryStats;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.SharedStorageCompat;
import io.github.abdurazaaqmohammed.util.io.TransferCancelledException;
import io.github.abdurazaaqmohammed.util.io.TransferControl;
import io.github.abdurazaaqmohammed.util.io.PathGuards;
import io.github.abdurazaaqmohammed.util.text.ShellQuote;

import android.content.Context;
import android.os.Build;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;

/**
 * Manages the native I/O daemon (mpio) for root-level file operations.
 * Operates similarly to MT Manager's mtio daemon to deliver high performance
 * and granular progress updates when operating across protected paths (/data/data,
 * /data/user/0) and external storage (/data/media/0).
 */
public final class NativeRootManager {

    private static final String BIN_NAME = "mpio";
    private static volatile String cachedMpioPath = null;
    private static final Object extractLock = new Object();

    public interface ProgressCallback {
        void onProgress(long filesDone, long bytesDone, String currentItem);
    }

    /**
     * Extracts and returns the executable path of mpio on device storage.
     */
    public static String getMpioPath(Context context) throws IOException {
        if (cachedMpioPath != null) {
            File f = new File(cachedMpioPath);
            if (f.exists() && f.canExecute()) return cachedMpioPath;
        }

        synchronized (extractLock) {
            if (cachedMpioPath != null) {
                File f = new File(cachedMpioPath);
                if (f.exists() && f.canExecute()) return cachedMpioPath;
            }
            File internalDir = new File(context.getFilesDir(), "bin");
            if (!internalDir.isDirectory() && !internalDir.mkdirs()) {
                throw new IOException("Cannot create native tools directory: " + internalDir);
            }
            File mpioBin = new File(internalDir, BIN_NAME);

            String preferredAbi = "arm64-v8a";
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi) || "x86_64".equals(abi) || "x86".equals(abi)) {
                        preferredAbi = abi;
                        break;
                    }
                }
            }

            String assetPath = "bin/" + preferredAbi + "/" + BIN_NAME;
            // Once per app process, atomically install the current APK's helper.
            // Binary length is not a version: a same-size bug fix must replace it too.
            File temporary = File.createTempFile("mpio-", ".tmp", internalDir);
            try {
                try (InputStream in = context.getAssets().open(assetPath);
                     FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.getFD().sync();
                }
                if (temporary.length() == 0 || !temporary.setExecutable(true, false)
                        || !temporary.setReadable(true, false) || !temporary.renameTo(mpioBin)) {
                    throw new IOException("Cannot install native copy helper: " + mpioBin);
                }
            } finally {
                if (temporary.exists()) temporary.delete();
            }

            cachedMpioPath = mpioBin.getAbsolutePath();
            return cachedMpioPath;
        }
    }

    /**
     * Executes native fast scan via root for a single path. Returns Measure (files, bytes).
     */
    public static FileUtils.Measure scan(Context context, String path) {
        if (path == null) return null;
        List<String> paths = new ArrayList<>();
        paths.add(path);
        FileUtils.MeasureSet set = scan(context, paths);
        return set != null ? set.total : null;
    }

    /**
     * Executes native fast scan via root for multiple paths. Returns MeasureSet.
     */
    public static FileUtils.MeasureSet scan(Context context, List<String> paths) {
        if (paths == null || paths.isEmpty()) return null;
        NativeScanResult result = scanStats(context, paths, null, 120_000);
        if (result == null || !result.total.complete()) return null;
        List<FileUtils.Measure> items = new ArrayList<>();
        for (DirectoryStats item : result.items) items.add(measure(item));
        return new FileUtils.MeasureSet(measure(result.total), items);
    }

    private static FileUtils.Measure measure(DirectoryStats stats) {
        FileUtils.Measure measure = new FileUtils.Measure((int) Math.min(Integer.MAX_VALUE, stats.files()), stats.bytes());
        measure.dirs = (int) Math.min(Integer.MAX_VALUE, stats.folders());
        return measure;
    }

    public static DirectoryStats stats(Context context, String path, TransferControl control, long timeoutMs) {
        NativeScanResult result = scanStats(context, Collections.singletonList(path), control, timeoutMs);
        return result == null ? null : result.total;
    }

    public static List<DirectoryStats> stats(Context context, List<String> paths, TransferControl control, long timeoutMs) {
        NativeScanResult result = scanStats(context, paths, control, timeoutMs);
        return result == null ? null : result.items;
    }

    /** One native traversal per sibling, in one process; keyed by the original pane paths. */
    public static Map<String, Long> sizes(Context context, List<String> paths, long timeoutMs) {
        NativeScanResult result = scanStats(context, paths, null, timeoutMs);
        Map<String, Long> sizes = new HashMap<>();
        if (result != null) for (int i = 0; i < paths.size(); i++) {
            DirectoryStats item = result.items.get(i);
            if (item.complete()) sizes.put(paths.get(i), item.bytes());
        }
        return sizes;
    }

    private static File writePaths(Context context, List<String> paths) throws IOException {
        File list = File.createTempFile("mpio-paths-", ".list", context.getCacheDir());
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(list))) {
            for (String path : paths) {
                if (path == null || path.isEmpty() || path.indexOf('\0') >= 0) throw new IOException("Invalid native path");
                output.write(path.getBytes(StandardCharsets.UTF_8));
                output.write(0);
            }
            return list;
        } catch (IOException error) { list.delete(); throw error; }
    }

    private static NativeScanResult scanStats(Context context, List<String> paths, TransferControl control, long timeoutMs) {
        if (paths == null || paths.isEmpty()) return null;
        File list = null;
        Process process = null;
        AtomicBoolean finished = new AtomicBoolean();
        AtomicLong pid = new AtomicLong(-1);
        Thread watcher = null;
        try {
            if (control != null) control.checkpoint();
            list = writePaths(context, paths);
            String cmd = "echo MPIO_PID $$; exec " + ShellQuote.escapeShellArg(getMpioPath(context))
                    + " scan --list " + ShellQuote.escapeShellArg(list.getPath());
            process = new ProcessBuilder(RootManager.suCommandForFs(context, cmd)).redirectErrorStream(true).start();
            Process running = process;
            watcher = new Thread(() -> {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                boolean paused = false;
                while (!finished.get() && NativeProcess.isAlive(running)) {
                    if ((control != null && control.isCancelled()) || System.nanoTime() > deadline) {
                        if (pid.get() > 1) signal(context, "kill -CONT " + pid.get()
                                + " 2>/dev/null; kill -TERM " + pid.get() + " 2>/dev/null");
                        running.destroy();
                        return;
                    }
                    if (control != null && pid.get() > 1 && control.isPaused() != paused) {
                        paused = control.isPaused();
                        signal(context, "kill -" + (paused ? "STOP " : "CONT ") + pid.get() + " 2>/dev/null");
                    }
                    try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                }
            }, "mpio-scan-control");
            watcher.setDaemon(true);
            watcher.start();
            NativeScanResult result = new NativeScanResult(paths.size());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MPIO_PID ")) {
                        try { pid.set(Long.parseLong(line.substring(9).trim())); } catch (NumberFormatException ignored) {}
                    } else result.accept(line);
                }
            }
            if (control != null && control.isCancelled()) throw new TransferCancelledException();
            return process.waitFor() == 0 && result.valid() ? result : null;
        } catch (TransferCancelledException cancelled) {
            throw cancelled;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException ignored) {
            if (control != null && control.isCancelled()) throw new TransferCancelledException();
            return null;
        } finally {
            finished.set(true);
            if (watcher != null) watcher.interrupt();
            if (process != null && NativeProcess.isAlive(process)) {
                if (pid.get() > 1) signal(context, "kill -CONT " + pid.get()
                        + " 2>/dev/null; kill -TERM " + pid.get() + " 2>/dev/null");
                process.destroy();
            }
            if (list != null) list.delete();
        }
    }

    private static void signal(Context context, String command) {
        try {
            Process process = new ProcessBuilder(RootManager.suCommandForFs(context, command)).redirectErrorStream(true).start();
            try { if (!NativeProcess.await(process, 10_000)) process.destroy(); }
            finally { if (NativeProcess.isAlive(process)) process.destroy(); }
        } catch (Exception ignored) {}
    }

    /**
     * Executes native recursive copy via root with streaming progress and cancellation.
     */
    public static boolean copy(Context context, String src, String dest,
                               ProgressCallback callback, TransferControl control) throws IOException {
        if (control != null) control.checkpoint();
        String mpio = getMpioPath(context);
        String shellCmd = "echo MPIO_PID $$; exec " + ShellQuote.escapeShellArg(mpio)
                + " copy " + ShellQuote.escapeShellArg(src) + " " + ShellQuote.escapeShellArg(dest);
        String[] cmd = RootManager.suCommandForFs(context, shellCmd);
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        return NativeCopyProcess.run(process, callback == null ? null : callback::onProgress, control,
                signal -> {
                    Process signalProcess = Runtime.getRuntime().exec(RootManager.suCommandForFs(context, signal));
                    try {
                        if (!NativeProcess.await(signalProcess, 10_000)) signalProcess.destroy();
                    } finally {
                        if (NativeProcess.isAlive(signalProcess)) signalProcess.destroy();
                    }
                });
    }

    /**
     * Executes native recursive delete via root with streaming progress and cancellation.
     */
    public static boolean delete(Context context, String path,
                                  ProgressCallback callback, TransferControl control) throws IOException {
        return delete(context, Collections.singletonList(path), callback, control);
    }

    public static boolean delete(Context context, List<String> paths,
                                 ProgressCallback callback, TransferControl control) throws IOException {
        List<String> nativePaths = new ArrayList<>();
        for (String path : paths) {
            if (PathGuards.isPathBlocked(path)) throw new NativeDeleteException("Blocked: refusing to delete " + path);
        }
        final long[] done = {0L};
        for (String path : paths) {
            if (control != null) control.checkpoint();
            File file = new File(path);
            if (SharedStorageCompat.canDeleteThroughApp(file)) {
                // Removing a public file through /data/media bypasses FUSE's
                // permanent dentry cache and breaks the next same-name creation.
                FileUtils.TransferListener listener = new FileUtils.TransferListener() {
                    @Override public void onFile(File source, File destination) {
                        ++done[0];
                        if (callback != null) callback.onProgress(done[0], 0L, source.getPath());
                    }
                    @Override public void onBytes(long count) {}
                    @Override public void checkpoint() {
                        if (control != null) control.checkpoint();
                    }
                };
                if (!FileUtils.deleteTree(file, listener)) throw new NativeDeleteException("Cannot delete " + path);
            } else {
                nativePaths.add(path);
            }
        }
        if (nativePaths.isEmpty()) return true;
        File list = null;
        try {
            list = writePaths(context, nativePaths);
            String shellCmd = "echo MPIO_PID $$; exec " + ShellQuote.escapeShellArg(getMpioPath(context))
                    + " delete --list " + ShellQuote.escapeShellArg(list.getPath());
            Process process = new ProcessBuilder(RootManager.suCommandForFs(context, shellCmd)).redirectErrorStream(true).start();
            long base = done[0];
            return NativeCopyProcess.runDelete(process, callback == null ? null
                    : (files, bytes, item) -> callback.onProgress(base + files, bytes, item), control,
                    command -> signal(context, command));
        } catch (IOException unavailable) {
            if (unavailable instanceof NativeDeleteException || done[0] == 0) throw unavailable;
            throw new NativeDeleteException("Cannot continue deletion after " + done[0] + " entries", unavailable);
        } finally {
            if (list != null) list.delete();
        }
    }
}
