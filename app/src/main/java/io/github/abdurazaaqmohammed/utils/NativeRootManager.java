package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
            File internalDir = new File(context.getFilesDir(), "bin");
            if (!internalDir.exists()) internalDir.mkdirs();
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
            boolean extractNeeded = !mpioBin.exists() || mpioBin.length() == 0;

            if (extractNeeded) {
                try (InputStream in = context.getAssets().open(assetPath);
                     OutputStream out = new FileOutputStream(mpioBin)) {
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.flush();
                } catch (IOException e) {
                    // Fallback to arm64-v8a if specified ABI wasn't found
                    try (InputStream in = context.getAssets().open("bin/arm64-v8a/" + BIN_NAME);
                         OutputStream out = new FileOutputStream(mpioBin)) {
                        byte[] buffer = new byte[32768];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        out.flush();
                    }
                }
                mpioBin.setExecutable(true, false);
                mpioBin.setReadable(true, false);
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
        String mpio = getMpioPath(context);
        String shellCmd = mpio + " copy " + RootManager.escapeShellArg(src) + " " + RootManager.escapeShellArg(dest);
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
                if (line.startsWith("PROGRESS")) {
                    // PROGRESS <done_files> <done_bytes> <cur_name>
                    String[] parts = line.split("\\s+", 4);
                    if (parts.length >= 3 && callback != null) {
                        try {
                            long files = Long.parseLong(parts[1]);
                            long bytes = Long.parseLong(parts[2]);
                            String curName = parts.length >= 4 ? parts[3] : "";
                            callback.onProgress(files, bytes, curName);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if (line.startsWith("COPY_DONE")) {
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

    /**
     * Executes native recursive delete via root with streaming progress and cancellation.
     */
    public static boolean delete(Context context, String path,
                                 ProgressCallback callback, TransferControl control) throws IOException {
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
