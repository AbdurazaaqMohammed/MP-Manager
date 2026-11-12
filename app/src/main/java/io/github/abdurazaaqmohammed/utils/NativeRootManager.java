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
     * Executes native fast scan via root. Returns Measure (files, bytes).
     */
    public static FileUtils.Measure scan(Context context, String path) {
        try {
            String mpio = getMpioPath(context);
            String su = RootManager.getInstance(context).suBinary();
            Process process = Runtime.getRuntime().exec(new String[]{
                    su, "-c", mpio + " scan " + RootManager.escapeShellArg(path)
            });

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("SCAN_RESULT")) {
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
                        process.waitFor(5, TimeUnit.SECONDS);
                        return new FileUtils.Measure(Math.max(1, files), bytes);
                    }
                }
            }
            process.waitFor(5, TimeUnit.SECONDS);
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
        String su = RootManager.getInstance(context).suBinary();
        String cmd = mpio + " copy " + RootManager.escapeShellArg(src) + " " + RootManager.escapeShellArg(dest);

        Process process = Runtime.getRuntime().exec(new String[]{su, "-c", cmd});
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
        String su = RootManager.getInstance(context).suBinary();
        String cmd = mpio + " delete " + RootManager.escapeShellArg(path);

        Process process = Runtime.getRuntime().exec(new String[]{su, "-c", cmd});
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
