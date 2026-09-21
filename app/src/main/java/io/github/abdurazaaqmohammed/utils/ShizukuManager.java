package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.shizuku.IFileService;
import io.github.abdurazaaqmohammed.shizuku.ShizukuConnection;
import io.github.abdurazaaqmohammed.shizuku.ShizukuFileService;
import rikka.shizuku.Shizuku;

public final class ShizukuManager {

    public static final int PERMISSION_CODE = 5101;

    private static final Pattern ALLOWED =
            Pattern.compile("^(?:/storage/emulated/\\d+|/sdcard)/Android/(data|obb|media)(/.*)?$");

    private ShizukuManager() {
    }

    public static boolean isRunning() {
        if (Build.VERSION.SDK_INT < 23) return false;
        try {
            if (Shizuku.isPreV11()) return false;
            return Shizuku.pingBinder();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            if (!isRunning()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isDeniedForever() {
        try {
            return isRunning() && Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean ready() {
        try {
            return isRunning() && hasPermission();
        } catch (Throwable e) {
            return false;
        }
    }

    public static void requestPermission(Activity activity,
                                         Shizuku.OnRequestPermissionResultListener listener) {
        try {
            if (Shizuku.isPreV11()) return;
            Shizuku.addRequestPermissionResultListener(listener);
            Shizuku.requestPermission(PERMISSION_CODE);
        } catch (Throwable ignored) {
        }
    }

    public static void removePermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener);
        } catch (Throwable ignored) {
        }
    }

    public static void warmUp(Context context) {
        if (context == null || !ready()) return;
        ShizukuConnection.ensureBackground(context);
    }

    private static IFileService serviceSync(Context context) throws IOException {
        if (!ready()) throw new IOException("Shizuku not connected or permission missing");
        IFileService s = ShizukuConnection.await(context, 20000);
        if (s == null) throw new IOException("Shizuku service unavailable");
        return s;
    }

    private static IFileService serviceFast() throws IOException {
        if (!ready()) throw new IOException("Shizuku not connected or permission missing");
        IFileService s = ShizukuConnection.peek();
        if (s == null) throw new IOException("Shizuku service starting, try again");
        return s;
    }

    private static String serviceError(IFileService s) {
        try {
            String e = s.lastError();
            return e == null || e.isEmpty() ? "Shizuku operation failed" : e;
        } catch (Exception e) {
            return "Shizuku operation failed";
        }
    }

    public static String normalize(String path) {
        if (path == null || !path.startsWith("/")) return null;
        String[] parts = path.split("/");
        ArrayDeque<String> out = new ArrayDeque<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!out.isEmpty()) out.removeLast();
            } else {
                out.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : out) sb.append('/').append(s);
        return sb.length() == 0 ? "/" : sb.toString();
    }

    public static boolean isAllowed(String path) {
        String n = normalize(path);
        if (n == null) return false;
        if (n.equals("/sdcard") || n.startsWith("/sdcard/")) {
            n = "/storage/emulated/0" + n.substring("/sdcard".length());
            if (n.isEmpty()) n = "/";
        }
        return ALLOWED.matcher(n).matches();
    }

    public static boolean isTopLevel(String path) {
        String n = normalize(path);
        if (n == null) return true;
        if (n.equals("/sdcard") || n.startsWith("/sdcard/")) {
            n = "/storage/emulated/0" + n.substring("/sdcard".length());
        }
        return n.equals("/storage/emulated/0/Android/data")
                || n.equals("/storage/emulated/0/Android/obb")
                || n.equals("/storage/emulated/0/Android/media");
    }

    private static void requireAllowed(String path) throws IOException {
        if (!ready()) throw new IOException("Shizuku not connected or permission missing");
        if (!isAllowed(path)) throw new IOException("Shizuku cannot access this path");
    }

    public static boolean exists(Context context, String path) {
        if (ShizukuConnection.peek() == null) ShizukuConnection.ensureBackground(context);
        try {
            requireAllowed(path);
            return serviceFast().pathExists(path);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isDirectory(Context context, String path) {
        if (ShizukuConnection.peek() == null) ShizukuConnection.ensureBackground(context);
        try {
            requireAllowed(path);
            return serviceFast().pathIsDir(path);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isFile(Context context, String path) {
        if (ShizukuConnection.peek() == null) ShizukuConnection.ensureBackground(context);
        try {
            requireAllowed(path);
            return serviceFast().pathIsFile(path);
        } catch (Throwable e) {
            return false;
        }
    }

    public static long getFileSize(Context context, String path) {
        try {
            requireAllowed(path);
            return serviceFast().pathSize(path);
        } catch (Throwable e) {
            return -1;
        }
    }

    public static long getMtime(Context context, String path) {
        try {
            requireAllowed(path);
            return serviceFast().pathMtime(path);
        } catch (Throwable e) {
            return 0;
        }
    }

    public static long dirSize(Context context, String path) {
        try {
            requireAllowed(path);
            return serviceSync(context).dirSize(path);
        } catch (Throwable e) {
            return -1;
        }
    }

    public static File[] listWithStat(Context context, String dirPath) {
        try {
            requireAllowed(dirPath);
            List<String> lines = serviceSync(context).dirStat(dirPath);
            if (lines == null) return null;
            List<File> files = new ArrayList<>();
            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;
                String[] parts = line.split("\037", -1);
                if (parts.length < 4) continue;
                String name = parts[0];
                if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;
                boolean dir = "1".equals(parts[1].trim());
                long size;
                long mtime;
                try {
                    size = Long.parseLong(parts[2].trim());
                } catch (NumberFormatException e) {
                    size = 0L;
                }
                try {
                    mtime = Long.parseLong(parts[3].trim());
                } catch (NumberFormatException e) {
                    mtime = 0L;
                }
                files.add(new RootFile(dirPath, name, true, dir, !dir,
                        dir ? 0L : Math.max(0L, size), mtime, null));
            }
            return files.toArray(new File[0]);
        } catch (Throwable e) {
            return null;
        }
    }

    public static long streamFrom(Context context, String srcPath, OutputStream out, long maxBytes) throws IOException {
        requireAllowed(srcPath);
        IFileService s = serviceSync(context);
        ParcelFileDescriptor pfd;
        try {
            pfd = s.openRead(srcPath, maxBytes);
        } catch (Exception e) {
            throw new IOException("Shizuku read failed: " + e.getMessage());
        }
        if (pfd == null) throw new IOException(serviceError(s));
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) throw new IOException("File too large");
                out.write(buf, 0, n);
            }
            out.flush();
            return total;
        }
    }

    public static void streamTo(Context context, File localSrc, String dstPath) throws IOException {
        requireAllowed(dstPath);
        if (isTopLevel(dstPath)) throw new IOException("Refusing protected top-level path");
        if (localSrc == null || !localSrc.isFile() || !localSrc.canRead()) {
            throw new IOException("Staged file unreadable");
        }
        if (localSrc.length() > ShizukuFileService.MAX_BYTES) throw new IOException("File too large");
        IFileService s = serviceSync(context);
        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (Exception e) {
            throw new IOException("Pipe failed: " + e.getMessage());
        }
        final ParcelFileDescriptor writeEnd = pipe[1];
        final File src = localSrc;
        Thread pump = new Thread(() -> {
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } catch (Exception ignored) {
            }
        });
        pump.start();
        long written;
        try {
            written = s.writeFile(dstPath, pipe[0], ShizukuFileService.MAX_BYTES);
        } catch (Exception e) {
            throw new IOException("Shizuku write failed: " + e.getMessage());
        } finally {
            try {
                pipe[0].close();
            } catch (Exception ignored) {
            }
        }
        try {
            pump.join(60000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (written < 0) throw new IOException(serviceError(s));
    }

    public static void copyFile(Context context, String src, String dest) throws IOException {
        requireAllowed(src);
        requireAllowed(dest);
        if (isTopLevel(src) || isTopLevel(dest)) throw new IOException("Refusing protected top-level path");
        IFileService s = serviceSync(context);
        try {
            if (!s.copyFile(src, dest)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("copy failed: " + e.getMessage());
        }
    }

    public static void copyDir(Context context, String src, String dest) throws IOException {
        requireAllowed(src);
        requireAllowed(dest);
        if (isTopLevel(src) || isTopLevel(dest)) throw new IOException("Refusing protected top-level path");
        IFileService s = serviceSync(context);
        try {
            if (!s.copyDir(src, dest)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("copy failed: " + e.getMessage());
        }
    }

    public static void mkdir(Context context, String path) throws IOException {
        requireAllowed(path);
        IFileService s = serviceSync(context);
        try {
            if (!s.mkdir(path)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("mkdir failed: " + e.getMessage());
        }
    }

    public static void delete(Context context, String path) throws IOException {
        requireAllowed(path);
        if (isTopLevel(path)) throw new IOException("Refusing protected top-level path");
        IFileService s = serviceSync(context);
        try {
            if (!s.deletePath(path)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("delete failed: " + e.getMessage());
        }
    }

    public static void rename(Context context, String oldPath, String newPath) throws IOException {
        requireAllowed(oldPath);
        requireAllowed(newPath);
        if (isTopLevel(oldPath) || isTopLevel(newPath)) {
            throw new IOException("Refusing protected top-level path");
        }
        IFileService s = serviceSync(context);
        try {
            if (!s.renamePath(oldPath, newPath)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("rename failed: " + e.getMessage());
        }
    }

    public static void touch(Context context, String path) throws IOException {
        requireAllowed(path);
        IFileService s = serviceSync(context);
        try {
            if (!s.touchPath(path)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("touch failed: " + e.getMessage());
        }
    }

    public static void touchMtime(Context context, String path, long millis) throws IOException {
        requireAllowed(path);
        IFileService s = serviceSync(context);
        try {
            if (!s.touchMtime(path, millis)) throw new IOException(serviceError(s));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("touch failed: " + e.getMessage());
        }
    }

    public static int shellExit(Context context, String command, int timeoutSeconds) {
        try {
            if (!ready()) return -1;
            IFileService s = serviceSync(context);
            String raw = s.shell(command, timeoutSeconds);
            if (raw == null) return -1;
            int cut = raw.indexOf('\n');
            String code = cut < 0 ? raw.trim() : raw.substring(0, cut).trim();
            return Integer.parseInt(code);
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean shellOk(Context context, String command, int timeoutSeconds) {
        return shellExit(context, command, timeoutSeconds) == 0;
    }

    public static boolean shellOkFast(Context context, String command, int timeoutSeconds) {
        try {
            if (!ready()) return false;
            IFileService s = ShizukuConnection.peek();
            if (s == null) {
                ShizukuConnection.ensureBackground(context);
                return false;
            }
            String raw = s.shell(command, timeoutSeconds);
            if (raw == null) return false;
            int cut = raw.indexOf('\n');
            String code = cut < 0 ? raw.trim() : raw.substring(0, cut).trim();
            return Integer.parseInt(code) == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
