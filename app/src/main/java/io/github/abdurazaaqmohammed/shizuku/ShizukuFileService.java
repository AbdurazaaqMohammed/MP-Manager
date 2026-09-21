package io.github.abdurazaaqmohammed.shizuku;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ShizukuFileService extends IFileService.Stub {

    public static final int SERVICE_VERSION = 2;
    public static final long MAX_BYTES = 100L * 1024L * 1024L;

    private static final String[] SHELL_PREFIXES = new String[]{
            "settings put global private_dns_mode ",
            "settings put global private_dns_specifier ",
            "settings put secure enabled_accessibility_services ",
            "settings put secure accessibility_enabled ",
            "appops set ",
            "cmd appops set ",
            "pm grant ",
            "pm trim-caches"
    };

    private static final Pattern ALLOWED =
            Pattern.compile("^/storage/emulated/\\d+/Android/(data|obb|media)(/.*)?$");

    private String error = "";

    public ShizukuFileService() {
    }

    @Keep
    public ShizukuFileService(Context context) {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public int version() {
        return SERVICE_VERSION;
    }

    @Override
    public String lastError() {
        return error;
    }

    private void fail(String msg) {
        error = msg;
    }

    private String norm(String path) {
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

    private String canon(String path) {
        String n = norm(path);
        if (n == null) return null;
        if (n.equals("/sdcard") || n.startsWith("/sdcard/")) {
            n = "/storage/emulated/0" + n.substring("/sdcard".length());
        }
        return n;
    }

    private boolean allowed(String path) {
        String n = canon(path);
        return n != null && ALLOWED.matcher(n).matches();
    }

    private boolean topLevel(String path) {
        String n = canon(path);
        return n != null && (n.equals("/storage/emulated/0/Android/data")
                || n.equals("/storage/emulated/0/Android/obb")
                || n.equals("/storage/emulated/0/Android/media"));
    }

    private File checked(String path) {
        if (!allowed(path)) {
            fail("Path not accessible");
            return null;
        }
        return new File(path);
    }

    @Override
    public boolean pathExists(String path) {
        File f = checked(path);
        return f != null && f.exists();
    }

    @Override
    public boolean pathIsDir(String path) {
        File f = checked(path);
        return f != null && f.isDirectory();
    }

    @Override
    public boolean pathIsFile(String path) {
        File f = checked(path);
        return f != null && f.isFile();
    }

    @Override
    public long pathSize(String path) {
        File f = checked(path);
        if (f == null || !f.isFile()) return -1;
        return f.length();
    }

    @Override
    public long pathMtime(String path) {
        File f = checked(path);
        if (f == null || !f.exists()) return 0;
        return f.lastModified();
    }

    @Override
    public List<String> dirStat(String dirPath) {
        List<String> out = new ArrayList<>();
        File dir = checked(dirPath);
        if (dir == null || !dir.isDirectory()) {
            fail("Not a directory");
            return out;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            fail("Cannot list directory");
            return out;
        }
        for (File k : kids) {
            String name = k.getName();
            if (name.equals(".") || name.equals("..")) continue;
            boolean isDir = k.isDirectory();
            out.add(name + "\037" + (isDir ? "1" : "0") + "\037"
                    + (isDir ? 0 : Math.max(0, k.length())) + "\037"
                    + k.lastModified() + "\037");
        }
        return out;
    }

    @Override
    public long dirSize(String dirPath) {
        File dir = checked(dirPath);
        if (dir == null) return -1;
        return du(dir);
    }

    private long du(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += du(k);
        return total;
    }

    @Override
    public ParcelFileDescriptor openRead(String path, long maxBytes) {
        File f = checked(path);
        if (f == null || !f.isFile()) {
            fail("Not a file");
            return null;
        }
        if (f.length() > maxBytes) {
            fail("File too large");
            return null;
        }
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final File src = f;
            final long cap = maxBytes;
            new Thread(() -> {
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] buf = new byte[65536];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > cap) break;
                        out.write(buf, 0, n);
                    }
                } catch (Exception ignored) {
                }
            }).start();
            return pipe[0];
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Override
    public long writeFile(String path, ParcelFileDescriptor data, long maxBytes) {
        File dst = checked(path);
        if (dst == null) return -1;
        if (topLevel(path)) {
            fail("Refusing protected path");
            return -1;
        }
        if (data == null) {
            fail("No data");
            return -1;
        }
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(data);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    fail("File too large");
                    try {
                        dst.delete();
                    } catch (Exception ignored) {
                    }
                    return -1;
                }
                out.write(buf, 0, n);
            }
            out.flush();
            return total;
        } catch (Exception e) {
            fail(e.getMessage());
            return -1;
        }
    }

    private boolean copyRec(File src, File dst) {
        if (src.isDirectory()) {
            if (!dst.isDirectory() && !dst.mkdirs()) return false;
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) {
                if (!copyRec(k, new File(dst, k.getName()))) return false;
            }
            dst.setLastModified(src.lastModified());
            return true;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (Exception e) {
            fail(e.getMessage());
            return false;
        }
        dst.setLastModified(src.lastModified());
        return true;
    }

    @Override
    public boolean copyFile(String src, String dst) {
        File s = checked(src);
        File d = checked(dst);
        if (s == null || d == null || !s.isFile()) {
            fail("Invalid copy source");
            return false;
        }
        if (topLevel(src) || topLevel(dst)) {
            fail("Refusing protected path");
            return false;
        }
        return copyRec(s, d);
    }

    @Override
    public boolean copyDir(String src, String dst) {
        File s = checked(src);
        File d = checked(dst);
        if (s == null || d == null || !s.isDirectory()) {
            fail("Invalid copy source");
            return false;
        }
        if (topLevel(src) || topLevel(dst)) {
            fail("Refusing protected path");
            return false;
        }
        return copyRec(s, new File(d, s.getName()));
    }

    @Override
    public boolean mkdir(String path) {
        File d = checked(path);
        if (d == null) return false;
        return d.isDirectory() || d.mkdirs();
    }

    @Override
    public boolean deletePath(String path) {
        File f = checked(path);
        if (f == null) return false;
        if (topLevel(path)) {
            fail("Refusing protected path");
            return false;
        }
        return delRec(f);
    }

    private boolean delRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) delRec(k);
        }
        return !f.exists() || f.delete();
    }

    @Override
    public boolean renamePath(String from, String to) {
        File s = checked(from);
        File d = checked(to);
        if (s == null || d == null) return false;
        if (topLevel(from) || topLevel(to)) {
            fail("Refusing protected path");
            return false;
        }
        return s.renameTo(d);
    }

    @Override
    public boolean touchPath(String path) {
        File f = checked(path);
        if (f == null) return false;
        try {
            if (!f.exists() && !f.createNewFile()) {
                fail("Cannot create file");
                return false;
            }
            return true;
        } catch (Exception e) {
            fail(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean touchMtime(String path, long millis) {
        File f = checked(path);
        if (f == null || !f.exists()) {
            fail("No such file");
            return false;
        }
        return f.setLastModified(millis);
    }

    @Override
    public String shell(String command, int timeoutSeconds) {
        if (!shellAllowed(command)) {
            fail("Command not allowed");
            return "-1\nrefused";
        }
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            StringBuilder out = new StringBuilder();
            final Process p = process;
            Thread reader = new Thread(() -> {
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    InputStream in = p.getInputStream();
                    while ((n = in.read(buf)) != -1) {
                        synchronized (out) {
                            if (out.length() < 65536) out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            reader.setDaemon(true);
            reader.start();
            int timeout = timeoutSeconds <= 0 ? 15 : Math.min(timeoutSeconds, 60);
            boolean done = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!done) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {
                }
                fail("Command timed out");
                return "-1\ntimeout";
            }
            try {
                reader.join(2000);
            } catch (Exception ignored) {
            }
            int code;
            String text;
            synchronized (out) {
                code = process.exitValue();
                text = out.toString().trim();
            }
            return code + "\n" + text;
        } catch (Exception e) {
            fail(e.getMessage());
            return "-1\nerror";
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean shellAllowed(String command) {
        if (command == null || command.isEmpty() || command.length() > 2048) return false;
        boolean prefixOk = false;
        for (String prefix : SHELL_PREFIXES) {
            if (command.startsWith(prefix)) {
                prefixOk = true;
                break;
            }
        }
        if (!prefixOk) return false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == ';' || c == '&' || c == '|' || c == '`' || c == '$' || c == '(' || c == ')' || c == '<' || c == '>' || c == '\n' || c == '\r') return false;
        }
        return true;
    }
}
