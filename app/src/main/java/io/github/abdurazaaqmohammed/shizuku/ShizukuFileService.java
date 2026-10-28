package io.github.abdurazaaqmohammed.shizuku;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.PrivilegedInstaller;

public class ShizukuFileService extends IFileService.Stub {

    public static final int SERVICE_VERSION = 4;
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

    /** Broadcast action that receives this session's commit result. */
    private static final String RESULT_ACTION_PREFIX =
            "io.github.abdurazaaqmohammed.MPManager.SESSION_RESULT_";

    private static final Pattern ALLOWED =
            Pattern.compile("^/storage/emulated/\\d+/Android/(data|obb|media)(/.*)?$");

    private String error = "";

    private Context context;

    public ShizukuFileService() {
    }

    @Keep
    public ShizukuFileService(Context context) {
        this.context = context;
    }

    @Override
    public String installApks(List<String> paths, String installerPkg, int flags, IntentSender resultReceiver) {
        Context ctx = context;
        if (ctx == null) return "err:no context";
        if (paths == null || paths.isEmpty()) return "err:no apks";
        final PackageManager pm = ctx.getPackageManager();
        // Caller identity for the session. PackageInstallerService records the
        // *initiating* package from the caller package handed to createSession,
        // and forces it to com.android.shell for root/shell uids anyway, so the
        // honest - and, on every ROM, the same - value is the shell package.
        // InstallerX passes "com.android.shell" for its Shizuku/root authorizers
        // too. The *installer of record* (what Settings and the Play Store check)
        // is spoofed through SessionParams.installerPackageName below - that is
        // the only attribution a privileged session can claim.
        final PackageInstaller installer = shellCallerInstaller(ctx, pm);
        List<File> files = new ArrayList<>();
        long total = 0;
        String target = null;
        for (String p : paths) {
            File f = new File(p);
            if (!f.isFile() || f.length() <= 0) return "err:unreadable " + p;
            files.add(f);
            total += f.length();
            if (target == null) {
                try {
                    PackageInfo info = pm.getPackageArchiveInfo(p, 0);
                    if (info != null) target = info.packageName;
                } catch (Throwable ignored) {
                }
            }
        }
        long before = -1;
        if (target != null) {
            try {
                before = pm.getPackageInfo(target, 0).lastUpdateTime;
            } catch (Throwable ignored) {
            }
        }
        int sessionId = -1;
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(total);
            if (target != null) params.setAppPackageName(target);
            setInstallerPackage(params, installerPkg);
            setOriginatingUid(params, installerPkg, pm);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                } catch (Throwable ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    params.setInstallReason(PackageManager.INSTALL_REASON_USER);
                } catch (Throwable ignored) {
                }
            }
            PrivilegedInstaller.applyInstallFlags(params, flags);
            sessionId = installer.createSession(params);
        } catch (Throwable t) {
            if (sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId);
                } catch (Throwable ignored) {
                }
            }
            fail(describe(t));
            return "err:" + describe(t);
        }

        // The commit result arrives asynchronously: listening to it is the only
        // way to report the real failure (INSTALL_FAILED_NO_MATCHING_ABIS,
        // signature mismatch, ...) instead of a blind polling timeout. Package
        // state polling is kept as a safety net for ROMs that swallow the
        // status callback. The IntentSender handed in by the app is not used
        // here: the privileged path reports through this method's return value.
        final int committedSession = sessionId;
        sessionId = -1;
        final String action = RESULT_ACTION_PREFIX + committedSession;
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] failure = {null};
        BroadcastReceiver receiver = null;
        try {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE);
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        failure[0] = "Session is waiting for user confirmation and was not auto-approved";
                    } else if (status != PackageInstaller.STATUS_SUCCESS) {
                        failure[0] = describeCommitResult(intent);
                    }
                    latch.countDown();
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(receiver, new IntentFilter(action));
            }
            try (PackageInstaller.Session session = installer.openSession(committedSession)) {
                for (File f : files) {
                    try (OutputStream out = session.openWrite(f.getName(), 0, f.length());
                         InputStream in = new FileInputStream(f)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        session.fsync(out);
                    }
                }
                PendingIntent result = PendingIntent.getBroadcast(ctx, committedSession,
                        new Intent(action).setPackage(ctx.getPackageName()),
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                session.commit(result.getIntentSender());
            }
            boolean delivered;
            try {
                delivered = latch.await(120, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Install interrupted");
                return "fail:interrupted";
            }
            if (!delivered) {
                if (target != null && pollInstalled(pm, target, before, installerPkg, 15000L)) return "ok";
                fail("Install timed out");
                return "fail:timeout";
            }
            if (failure[0] != null) {
                fail(failure[0]);
                return "fail:" + failure[0];
            }
            return "ok";
        } catch (Throwable t) {
            try {
                installer.abandonSession(committedSession);
            } catch (Throwable ignored) {
            }
            fail(describe(t));
            return "err:" + describe(t);
        } finally {
            if (receiver != null) {
                try {
                    ctx.unregisterReceiver(receiver);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Human-readable reason carried by a failed session commit intent. */
    private static String describeCommitResult(Intent intent) {
        String message = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
        if (message != null && !message.trim().isEmpty()) return message.trim();
        int legacy = intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", Integer.MIN_VALUE);
        if (legacy != Integer.MIN_VALUE) return legacyInstallError(legacy);
        return "Install failed (no reason reported)";
    }

    private static String legacyInstallError(int code) {
        switch (code) {
            case -1: return "INSTALL_FAILED_ALREADY_EXISTS: the package is already installed";
            case -2: return "INSTALL_FAILED_INVALID_APK: the APK is invalid or corrupt";
            case -3: return "INSTALL_FAILED_INVALID_URI: invalid APK path";
            case -4: return "INSTALL_FAILED_INSUFFICIENT_STORAGE: not enough storage space";
            case -5: return "INSTALL_FAILED_DUPLICATE_PACKAGE: duplicate package name";
            case -6: return "INSTALL_FAILED_NO_SHARED_USER: missing shared user";
            case -7: return "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signature of the new APK does not match the installed version (uninstall, or use Core Patch)";
            case -8: return "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE: incompatible shared user";
            case -9: return "INSTALL_FAILED_MISSING_SHARED_LIBRARY: a required shared library is missing";
            case -10: return "INSTALL_FAILED_REPLACE_COULDNT_DELETE: the old version could not be removed";
            case -11: return "INSTALL_FAILED_DEXOPT: dex optimization failed";
            case -12: return "INSTALL_FAILED_OLDER_SDK: the APK requires a newer Android version";
            case -13: return "INSTALL_FAILED_CONFLICTING_PROVIDER: a content provider conflicts with another app";
            case -14: return "INSTALL_FAILED_NEWER_SDK: the APK targets a newer Android version than this device";
            case -15: return "INSTALL_FAILED_TEST_ONLY: test-only APK (enable 'Allow test packages')";
            case -16: return "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE: the APK does not support this device's CPU";
            case -17: return "INSTALL_FAILED_MISSING_FEATURE: a required device feature is missing";
            case -18: return "INSTALL_FAILED_CONTAINER_ERROR: the APK container is not readable";
            case -19: return "INSTALL_FAILED_INVALID_INSTALL_LOCATION: invalid install location";
            case -20: return "INSTALL_FAILED_NO_MATCHING_ABIS: the APK's native libraries support none of this device's CPUs";
            case -21: return "INSTALL_FAILED_WRITE_VERIFICATION_FAILURE: package verification failed while writing";
            case -22: return "INSTALL_FAILED_VERIFICATION_FAILURE: package verification failed";
            case -23: return "INSTALL_FAILED_MEDIA_UNAVAILABLE: the install storage is unavailable";
            case -24: return "INSTALL_FAILED_VERSION_DOWNGRADE: downgrade not allowed (enable 'Allow downgrade')";
            case -25: return "INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE: runtime permission downgrade not allowed";
            case -26: return "INSTALL_FAILED_SANDBOX_VERSION_DOWNGRADE: sandbox version downgrade not allowed";
            case -27: return "INSTALL_FAILED_MISSING_SPLIT: a required split APK is missing";
            case -111: return "INSTALL_FAILED_USER_RESTRICTED: the user restricts installing apps";
            case -112: return "INSTALL_FAILED_DUPLICATE_PERMISSION: duplicate permission definition";
            case -115: return "INSTALL_FAILED_UID_CHANGED: the app's UID changed (clear data and retry)";
            default: return "Install failed (legacy status " + code + ")";
        }
    }

    /**
     * Builds a {@link PackageInstaller} whose session is created under the
     * given caller package, the way InstallerX constructs its privileged
     * installer ({@code new PackageInstaller(ipm, callerPackageName, userId)}).
     * The hidden constructor is used directly because a package context cannot
     * be created for the shell package (it has no APK); when the hidden API is
     * blocked the plain installer is returned and the caller defaults to this
     * app's package.
     */
    private static PackageInstaller shellCallerInstaller(Context ctx, PackageManager pm) {
        try {
            PackageInstaller base = pm.getPackageInstaller();
            Field installerField = PackageInstaller.class.getDeclaredField("mInstaller");
            installerField.setAccessible(true);
            Object ipm = installerField.get(base);
            Class<?> ipmClass = Class.forName("android.content.pm.IPackageInstaller");
            // UserHandle.getUserId is equivalent and keeps this reflection-light.
            int userId = android.os.Process.myUid() / 100000;
            for (Constructor<?> ctor : PackageInstaller.class.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 4 && types[0] == ipmClass
                        && types[1] == String.class && types[2] == String.class
                        && types[3] == int.class) {
                    ctor.setAccessible(true);
                    return (PackageInstaller) ctor.newInstance(ipm, "com.android.shell", null, userId);
                }
                if (types.length == 3 && types[0] == ipmClass
                        && types[1] == String.class && types[2] == int.class) {
                    ctor.setAccessible(true);
                    return (PackageInstaller) ctor.newInstance(ipm, "com.android.shell", userId);
                }
            }
        } catch (Throwable ignored) {
        }
        return pm.getPackageInstaller();
    }

    private static boolean installed(PackageManager pm, String target, long before, String installerPkg) {
        try {
            PackageInfo now = pm.getPackageInfo(target, 0);
            if (now != null && (before < 0 || now.lastUpdateTime != before)) return true;
        } catch (Throwable ignored) {
        }
        if (installerPkg != null && !installerPkg.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                InstallSourceInfo info = pm.getInstallSourceInfo(target);
                if (info != null && installerPkg.equals(info.getInstallingPackageName())) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** Polls the package state; only used when the commit status never arrives. */
    private static boolean pollInstalled(PackageManager pm, String target, long before,
                                         String installerPkg, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (installed(pm, target, before, installerPkg)) return true;
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void setInstallerPackage(PackageInstaller.SessionParams params, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                params.setInstallerPackageName(pkg);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Field f = PackageInstaller.SessionParams.class
                    .getDeclaredField("installerPackageName");
            f.setAccessible(true);
            f.set(params, pkg);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Records the spoofed installer as the *originating* package (the package
     * the install was requested on behalf of), the way InstallerX's install
     * requester mode does with setOriginatingUid. Best effort: the field is
     * hidden and absent on some versions.
     */
    private static void setOriginatingUid(PackageInstaller.SessionParams params,
                                          String installerPkg, PackageManager pm) {
        if (params == null || installerPkg == null || installerPkg.isEmpty()) return;
        try {
            int uid = pm.getPackageUid(installerPkg, 0);
            if (uid <= 0) return;
            Field f = PackageInstaller.SessionParams.class.getDeclaredField("originatingUid");
            f.setAccessible(true);
            f.setInt(params, uid);
        } catch (Throwable ignored) {
        }
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
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
