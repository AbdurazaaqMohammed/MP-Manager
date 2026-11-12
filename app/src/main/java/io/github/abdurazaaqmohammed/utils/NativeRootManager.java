package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
        List<String> paths = new java.util.ArrayList<>();
        paths.add(path);
        FileUtils.MeasureSet set = scan(context, paths);
        return set != null ? set.total : null;
    }

    /**
     * Executes native fast scan via root for multiple paths. Returns MeasureSet.
     */
    public static FileUtils.MeasureSet scan(Context context, java.util.List<String> paths) {
        if (paths == null || paths.isEmpty()) return null;
        try {
            String mpio = getMpioPath(context);
            StringBuilder sb = new StringBuilder(mpio).append(" scan");
            for (String p : paths) {
                sb.append(' ').append(RootManager.escapeShellArg(p));
            }
            String[] cmd = RootManager.suCommandForFs(context, sb.toString());
            Process process = Runtime.getRuntime().exec(cmd);

            java.util.List<FileUtils.Measure> items = new java.util.ArrayList<>();
            for (int i = 0; i < paths.size(); i++) {
                items.add(new FileUtils.Measure(1, 0L));
            }
            FileUtils.Measure total = null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("ITEM_RESULT")) {
                        // ITEM_RESULT idx=... files=... dirs=... bytes=...
                        int idx = -1;
                        int files = 0;
                        long bytes = 0L;
                        for (String part : line.split("\\s+")) {
                            if (part.startsWith("idx=")) {
                                idx = Integer.parseInt(part.substring(4));
                            } else if (part.startsWith("files=")) {
                                files = Integer.parseInt(part.substring(6));
                            } else if (part.startsWith("bytes=")) {
                                bytes = Long.parseLong(part.substring(6));
                            }
                        }
                        if (idx >= 0 && idx < items.size()) {
                            items.set(idx, new FileUtils.Measure(Math.max(1, files), bytes));
                        }
                    } else if (line.startsWith("SCAN_RESULT")) {
                        // SCAN_RESULT files=... dirs=... bytes=... ms=...
                        int files = 0;
                        long bytes = 0L;
                        for (String part : line.split("\\s+")) {
                            if (part.startsWith("files=")) {
                                files = Integer.parseInt(part.substring(6));
                            } else if (part.startsWith("bytes=")) {
                                bytes = Long.parseLong(part.substring(6));
                            }
                        }
                        total = new FileUtils.Measure(Math.max(1, files), bytes);
                    }
                }
            }
            process.waitFor(10, TimeUnit.SECONDS);
            if (total != null) {
                return new FileUtils.MeasureSet(total, items);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Executes native recursive copy via root with streaming progress and cancellation.
     */
    public static boolean copy(Context context, String src, String dest,
                               ProgressCallback callback, TransferControl control) throws IOException {
        if (control != null) control.checkpoint();
        String mpio = getMpioPath(context);
        String shellCmd = "echo MPIO_PID $$; exec " + RootManager.escapeShellArg(mpio)
                + " copy " + RootManager.escapeShellArg(src) + " " + RootManager.escapeShellArg(dest);
        String[] cmd = RootManager.suCommandForFs(context, shellCmd);
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        return NativeCopyProcess.run(process, callback == null ? null : callback::onProgress, control,
                signal -> {
                    Process signalProcess = Runtime.getRuntime().exec(RootManager.suCommandForFs(context, signal));
                    try {
                        if (!signalProcess.waitFor(10, TimeUnit.SECONDS)) signalProcess.destroyForcibly();
                    } finally {
                        if (signalProcess.isAlive()) signalProcess.destroyForcibly();
                    }
                });
    }

    /**
     * Executes native recursive delete via root with streaming progress and cancellation.
     */
    public static boolean delete(Context context, String path,
                                 ProgressCallback callback, TransferControl control) throws IOException {
        File file = new File(path);
        if (SharedStorageCompat.canDeleteThroughApp(file)) {
            // Removing a public file through /data/media bypasses FUSE's
            // permanent dentry cache and breaks the next same-name creation.
            final long[] done = {0L};
            FileUtils.TransferListener listener = new FileUtils.TransferListener() {
                @Override public void onFile(File source, File destination) {
                    if (callback != null) callback.onProgress(++done[0], 0L, source.getPath());
                }
                @Override public void onBytes(long count) {
                }
                @Override public void checkpoint() {
                    if (control != null) control.checkpoint();
                }
            };
            if (FileUtils.deleteTree(file, listener)) return true;
        }
        String mpio = getMpioPath(context);
        String shellCmd = mpio + " delete " + RootManager.escapeShellArg(path);
        String[] cmd = RootManager.suCommandForFs(context, shellCmd);

        Process process = Runtime.getRuntime().exec(cmd);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Thread watcher = null;
        if (control != null) {
            watcher = new Thread(() -> {
                while (process.isAlive()) {
                    if (control.isCancelled()) {
                        cancelled.set(true);
                        process.destroyForcibly();
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            watcher.start();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("DEL_PROGRESS")) {
                    // DEL_PROGRESS <done_items> <cur_name>
                    String[] parts = line.split("\\s+", 3);
                    if (parts.length >= 2 && callback != null) {
                        try {
                            long items = Long.parseLong(parts[1]);
                            String curName = parts.length >= 3 ? parts[2] : "";
                            callback.onProgress(items, 0L, curName);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if (line.startsWith("DELETE_DONE")) {
                    break;
                }
            }
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (watcher != null) watcher.interrupt();
        }

        if (cancelled.get() || (control != null && control.isCancelled())) {
            throw new TransferCancelledException();
        }

        return process.exitValue() == 0;
    }
}
