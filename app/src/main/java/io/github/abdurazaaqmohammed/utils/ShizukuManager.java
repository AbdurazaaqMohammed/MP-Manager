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

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
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

    /**
     * The user service only ever opens the app-private Android/{data,obb,media}
     * trees. Shizuku itself runs as the shell user, which can read much more
     * (/data, /vendor, /odm, ...), so anything the service refuses is handled
     * over the shell instead of being reported as inaccessible.
     */
    private static boolean useShell(String path) {
        return !isAllowed(path);
    }

    private static String shellArg(String path) {
        return RootManager.escapeShellArg(path);
    }

    /** Directory listing over the shell, in the same shape {@link #listWithStat} returns. */
    private static File[] listWithShell(String dirPath) {
        try {
            String arg = shellArg(dirPath);
            ShizukuShell.Result r = ShizukuShell.exec(
                    "test -d " + arg + " || exit 4; LC_ALL=C ls -laH -- " + arg + " 2>/dev/null", 30);
            if (r == null || !r.success || r.stdout == null) return null;
            List<File> files = new ArrayList<>();
            for (String line : r.stdout.split("\\r?\\n")) {
                if (line.trim().isEmpty() || line.startsWith("total ")) continue;
                java.util.regex.Matcher matcher = RootManager.LS_LONG_LINE.matcher(line);
                if (!matcher.matches()) continue;
                String mode = matcher.group(1);
                char type = mode.charAt(0);
                if (type != '-' && type != 'd' && type != 'l') continue;
                String name = matcher.group(4);
                int arrow = name.indexOf(" -> ");
                if (arrow > 0) name = name.substring(0, arrow);
                if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;
                long size;
                try {
                    size = Long.parseLong(matcher.group(2));
                } catch (NumberFormatException e) {
                    size = 0L;
                }
                long mtime = RootManager.parseLsDateTimeFlexible(matcher.group(3));
                boolean isDir = type == 'd';
                files.add(new RootFile(dirPath, name, true, isDir, type != 'd',
                        isDir ? 0L : Math.max(0L, size), mtime,
                        mode.length() > 1 ? mode.substring(1) : null));
            }
            return files.toArray(new File[0]);
        } catch (Throwable e) {
            return null;
        }
    }

    private static IOException shellError(String what, ShizukuShell.Result r) {
        String err = r == null ? null : (r.stderr == null || r.stderr.isEmpty() ? r.stdout : r.stderr);
        if (err != null && err.length() > 200) err = err.substring(0, 200);
        return new IOException(err == null || err.trim().isEmpty() ? what + " failed" : err.trim());
    }

    private static void throwIfShellFails(String command, String what, int timeout) throws IOException {
        if (!ready()) throw new IOException("Shizuku not connected or permission missing");
        ShizukuShell.Result r = ShizukuShell.exec(command, timeout);
        if (r == null || !r.success) throw shellError(what, r);
    }

    public static boolean exists(Context context, String path) {
        if (ShizukuConnection.peek() == null) ShizukuConnection.ensureBackground(context);
        try {
            if (!isAllowed(path)) return shellTest(path, "-e");
            requireAllowed(path);
            return serviceFast().pathExists(path);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isDirectory(Context context, String path) {
        if (ShizukuConnection.peek() == null) ShizukuConnection.ensureBackground(context);
        try {
            if (!isAllowed(path)) return shellTest(path, "-d");
            requireAllowed(path);
            return serviceFast().pathIsDir(path);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isFile(Context context, String path) {
        if (ShizukuConnection.peek() == null) ShizukuConnection.ensureBackground(context);
        try {
            if (!isAllowed(path)) return !shellTest(path, "-d") && shellTest(path, "-e");
            requireAllowed(path);
            return serviceFast().pathIsFile(path);
        } catch (Throwable e) {
            return false;
        }
    }

    public static long getFileSize(Context context, String path) {
        try {
            if (!isAllowed(path)) return shellStat(path, "%s");
            requireAllowed(path);
            return serviceFast().pathSize(path);
        } catch (Throwable e) {
            return -1;
        }
    }

    public static long getMtime(Context context, String path) {
        try {
            if (!isAllowed(path)) {
                long m = shellStat(path, "%Y");
                return m < 0 ? 0 : m;
            }
            requireAllowed(path);
            return serviceFast().pathMtime(path);
        } catch (Throwable e) {
            return 0;
        }
    }

    private static boolean shellTest(String path, String op) {
        try {
            if (!ShizukuShell.isGranted()) return false;
            return ShizukuShell.exec("[ " + op + " " + RootManager.escapeShellArg(path) + " ]", 15).success;
        } catch (Throwable e) {
            return false;
        }
    }

    private static long shellStat(String path, String format) {
        try {
            if (!ShizukuShell.isGranted()) return -1;
            ShizukuShell.Result r = ShizukuShell.exec("stat -c " + format + " " + RootManager.escapeShellArg(path), 15);
            if (r == null || !r.success || r.stdout == null) return -1;
            return Long.parseLong(r.stdout.trim().split("\\s+")[0]);
        } catch (Throwable e) {
            return -1;
        }
    }

    public static boolean touchMtimeShell(String path, long millis) {
        try {
            if (RootManager.isPathBlocked(path)) return false;
            long secs = millis / 1000L;
            return ShizukuShell.exec("touch -d @" + secs + " " + RootManager.escapeShellArg(path), 15).success;
        } catch (Throwable e) {
            return false;
        }
    }

    public static long streamFromShell(String srcPath, OutputStream out, long maxBytes) throws IOException {
        if (srcPath == null || srcPath.isEmpty()) throw new IOException("No source path");
        String arg = RootManager.escapeShellArg(srcPath);
        // `-e` only tests the directory entry, not read access: a path the shell
        // can stat but not read (many /vendor and /odm files) used to fail later
        // as an opaque "exit 1" from cat.
        if (!ShizukuShell.isGranted()) throw new IOException("Shizuku not granted");
        ShizukuShell.Result readable = ShizukuShell.exec("[ -r " + arg + " ]", 15);
        if (readable != null && !readable.success) {
            boolean present = ShizukuShell.exec("[ -e " + arg + " ]", 15).success;
            throw new IOException(present
                    ? "The shell user cannot read this file. Enable elevated file access or use root."
                    : "No such file or directory: " + srcPath);
        }
        byte[] data = ShizukuShell.execBytes("cat -- " + arg, maxBytes, 120);
        out.write(data);
        out.flush();
        return data.length;
    }

    public static long streamToShell(File localSrc, String dstPath) throws IOException {
        if (localSrc == null || !localSrc.isFile() || !localSrc.canRead()) {
            throw new IOException("Staged file unreadable");
        }
        if (RootManager.isPathBlocked(dstPath)) throw new IOException("Blocked: refusing to write critical path");
        ShizukuShell.Result r = ShizukuShell.exec(
                "cat " + RootManager.escapeShellArg(localSrc.getAbsolutePath())
                        + " > " + RootManager.escapeShellArg(dstPath), 120);
        if (r == null || !r.success) {
            String err = r == null ? "failed" : (r.stderr == null || r.stderr.isEmpty() ? r.stdout : r.stderr);
            throw new IOException(err == null || err.isEmpty() ? "Shell write failed" : err.trim());
        }
        return localSrc.length();
    }

    public static long dirSize(Context context, String path) {
        try {
            if (useShell(path)) {
                ShizukuShell.Result r = ShizukuShell.exec("du -sk -- " + shellArg(path) + " 2>/dev/null", 60);
                if (r == null || !r.success || r.stdout == null) return -1;
                String[] parts = r.stdout.trim().split("\\s+");
                return Long.parseLong(parts[0].trim()) * 1024L;
            }
            requireAllowed(path);
            return serviceSync(context).dirSize(path);
        } catch (Throwable e) {
            return -1;
        }
    }

    public static File[] listWithStat(Context context, String dirPath) {
        if (useShell(dirPath)) return listWithShell(dirPath);
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
        if (useShell(srcPath)) return streamFromShell(srcPath, out, maxBytes);
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
        if (useShell(dstPath)) {
            streamToShell(localSrc, dstPath);
            return;
        }
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
        if (useShell(src) || useShell(dest)) {
            File destParent = new File(dest).getParentFile();
            StringBuilder cmd = new StringBuilder();
            if (destParent != null) cmd.append("mkdir -p ").append(shellArg(destParent.getAbsolutePath())).append(" && ");
            cmd.append("cat -- ").append(shellArg(src)).append(" > ").append(shellArg(dest));
            throwIfShellFails(cmd.toString(), "copy", 120);
            return;
        }
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
        if (useShell(src) || useShell(dest)) {
            File destParent = new File(dest).getParentFile();
            StringBuilder cmd = new StringBuilder();
            if (destParent != null) cmd.append("mkdir -p ").append(shellArg(destParent.getAbsolutePath())).append(" && ");
            cmd.append("cp -r ").append(shellArg(src)).append(" ").append(shellArg(dest));
            throwIfShellFails(cmd.toString(), "copy", 300);
            return;
        }
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

    /**
     * Runs every copy in one shell process when the shell can reach the paths,
     * and one binder round trip each when it cannot. Per-item shell copies cost
     * a fresh {@code sh -c} process each, which dominated multi-selection copies.
     */
    public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops) throws IOException {
        if (ops == null || ops.isEmpty()) return;
        StringBuilder shell = new StringBuilder();
        int shellOps = 0;
        List<RootManager.CopyOp> service = new ArrayList<>();
        for (RootManager.CopyOp op : ops) {
            String src = op.src;
            String dest = op.dest;
            if (src == null || dest == null || src.isEmpty() || dest.isEmpty() || src.equals(dest)) continue;
            if (useShell(src) || useShell(dest)) {
                if (shell.length() > 0) shell.append(" && ");
                if (op.destExists && op.directory && !RootManager.isPathBlocked(dest)) {
                    shell.append("rm -rf ").append(shellArg(dest)).append(" && ");
                }
                File destParent = new File(dest).getParentFile();
                if (destParent != null) shell.append("mkdir -p ").append(shellArg(destParent.getAbsolutePath())).append(" && ");
                shell.append("cp -p").append(op.directory ? " -r" : "").append(" -- ")
                        .append(shellArg(src)).append(' ').append(shellArg(dest));
                shellOps++;
            } else {
                service.add(op);
            }
        }
        if (shellOps > 0) throwIfShellFails(shell.toString(), "copy", Math.max(60, shellOps * 60));
        if (service.isEmpty()) return;
        IFileService s = serviceSync(context);
        for (RootManager.CopyOp op : service) {
            requireAllowed(op.src);
            requireAllowed(op.dest);
            if (isTopLevel(op.src) || isTopLevel(op.dest)) throw new IOException("Refusing protected top-level path");
            if (op.destExists && op.directory) {
                try {
                    s.deletePath(op.dest);
                } catch (Exception ignored) {
                }
            }
            boolean ok;
            if (op.directory) {
                try {
                    ok = s.copyDir(op.src, op.dest);
                } catch (android.os.RemoteException e) {
                    throw new IOException("copy failed: " + e.getMessage());
                }
            } else {
                try {
                    ok = s.copyFile(op.src, op.dest);
                } catch (android.os.RemoteException e) {
                    throw new IOException("copy failed: " + e.getMessage());
                }
            }
            if (!ok) throw new IOException(serviceError(s));
            try {
                s.touchMtime(op.dest, s.pathMtime(op.src));
            } catch (Exception ignored) {
            }
        }
    }

    public static void mkdir(Context context, String path) throws IOException {
        if (useShell(path)) {
            throwIfShellFails("mkdir -p " + shellArg(path), "mkdir", 30);
            return;
        }
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
        if (useShell(path)) {
            if (RootManager.isPathBlocked(path)) throw new IOException("Blocked: refusing to delete critical path");
            throwIfShellFails("rm -rf " + shellArg(path), "delete", 120);
            return;
        }
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

    public static void deleteAll(Context context, List<String> paths) throws IOException {
        if (paths == null || paths.isEmpty()) return;
        StringBuilder shell = new StringBuilder("rm -rf --");
        int shellPaths = 0;
        List<String> service = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isEmpty()) continue;
            if (useShell(path)) {
                if (RootManager.isPathBlocked(path)) throw new IOException("Blocked: refusing to delete critical path");
                shell.append(' ').append(shellArg(path));
                shellPaths++;
            } else {
                requireAllowed(path);
                if (isTopLevel(path)) throw new IOException("Refusing protected top-level path");
                service.add(path);
            }
        }
        if (shellPaths > 0) throwIfShellFails(shell.toString(), "delete", Math.max(30, shellPaths * 30));
        if (service.isEmpty()) return;
        IFileService s = serviceSync(context);
        for (String path : service) {
            try {
                if (!s.deletePath(path)) throw new IOException(serviceError(s));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("delete failed: " + e.getMessage());
            }
        }
    }

    public static void rename(Context context, String oldPath, String newPath) throws IOException {
        if (useShell(oldPath) || useShell(newPath)) {
            throwIfShellFails("mv " + shellArg(oldPath) + " " + shellArg(newPath), "rename", 120);
            return;
        }
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
        if (useShell(path)) {
            throwIfShellFails("touch " + shellArg(path), "touch", 30);
            return;
        }
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
        if (useShell(path)) {
            if (!touchMtimeShell(path, millis)) throw new IOException("touch failed");
            return;
        }
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
