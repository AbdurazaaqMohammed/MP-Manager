package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class RootManager {

    private static volatile RootManager instance;
    private final Context context;
    private Boolean rootAvailable = null;
    private Process suProcess;
    private DataOutputStream suStdin;
    private BufferedReader suStdout;
    private final Object lock = new Object();

    // Shell argument pattern: alphanumeric, dash, underscore, dot, slash, space, colon
    private static final Pattern SAFE_ARG = Pattern.compile("^[a-zA-Z0-9_./\\-: ]+$");

    // Directories that should never be deleted (absolute block)
    private static final Set<String> BLOCKED_DELETE_PATHS = new HashSet<>(Arrays.asList(
            "/", "/system", "/system_ext", "/vendor", "/product", "/odm", "/oem",
            "/boot", "/recovery", "/sbin", "/proc", "/sys", "/dev",
            "/cache", "/tmp", "/mnt", "/storage",
            "/data", "/data/data", "/data/user", "/data/user_de",
            "/data/system", "/data/app", "/data/dalvik-cache",
            "/sdcard"
    ));

    // Key directories: deletion is allowed but requires extra confirmation
    private static final Set<String> KEY_DIRECTORIES = new HashSet<>(Arrays.asList(
            "/data/data", "/data/user", "/data/user_de",
            "/data/system", "/data/system/packages.xml",
            "/system/build.prop", "/system/etc/hosts"
    ));

    // System-critical apps that must not be uninstalled
    private static final Set<String> CRITICAL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.telephony",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.android.launcher3",
            "com.android.launcher",
            "com.android.inputmethod.latin",
            "com.android.keychain",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.vending",
            "com.android.gms",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.gsf.login",
            "com.android.shell",
            "com.android.providers.downloads",
            "com.android.documentsui",
            "com.android.nfc",
            "com.android.bluetooth",
            "com.android.bluetoothideoserver",
            "com.android.wifi",
            "com.android.connectivitymanager",
            "com.android.networkstack",
            "com.android.networkstack.tethering",
            "android",
            "com.android.captiveportallogin",
            "com.android.storagemanager",
            "com.android.emergency",
            "com.android.apps.tag",
            "com.android.se",
            "com.android.wallpaperbackup",
            "com.android.wallpapercropper",
            "com.android.printspooler",
            "com.android.managedprovisioning",
            "com.android.hotspot2.osulolauncher",
            "com.android.internal.systemui.navbar.gestural",
            "com.android.internal.systemui.navbar.threebutton",
            "com.android.internal.systemui.navbar.twobutton",
            "com.android.internal.systemui.navbar.gestural_wide_back",
            "com.android.internal.systemui.navbar.gestural_narrow_back",
            "com.android.internal.systemui.navbar.gestural_extra_wide_back"
    ));

    private RootManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static RootManager getInstance(Context context) {
        if (instance == null) {
            synchronized (RootManager.class) {
                if (instance == null) {
                    instance = new RootManager(context);
                }
            }
        }
        return instance;
    }

    public enum WorkingMode {
        NON_ROOT("non_root", "Non-root"),
        ROOT("root", "Root"),
        SHIZUKU("shizuku", "Shizuku");

        public final String key;
        public final String label;

        WorkingMode(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public WorkingMode getWorkingMode() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString("working_mode", WorkingMode.NON_ROOT.key);
        if (WorkingMode.ROOT.key.equals(mode)) return WorkingMode.ROOT;
        if (WorkingMode.SHIZUKU.key.equals(mode)) return WorkingMode.SHIZUKU;
        return WorkingMode.NON_ROOT;
    }

    public void setWorkingMode(WorkingMode mode) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString("working_mode", mode.key)
                .apply();
    }

    public boolean autoEnableRootIfAvailable() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean modeTouched = prefs.contains("working_mode");
            boolean opsTouched = prefs.contains("root_file_ops");
            if (modeTouched && opsTouched) return false;
            if (!isRootAvailable()) return false;
            boolean changed = false;
            if (!modeTouched) {
                setWorkingMode(WorkingMode.ROOT);
                changed = true;
            }
            if (!opsTouched) {
                prefs.edit().putBoolean("root_file_ops", true).apply();
                changed = true;
            }
            return changed;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRootMode() {
        return getWorkingMode() == WorkingMode.ROOT;
    }

    public boolean isShizukuMode() {
        return getWorkingMode() == WorkingMode.SHIZUKU;
    }

    public String suBinary() {
        try {
            String custom = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("su_command", "");
            if (custom != null) {
                custom = custom.trim();
                if (!custom.isEmpty() && custom.matches("^[A-Za-z0-9_./-]+$")) return custom;
            }
        } catch (Exception ignored) {
        }
        return "su";
    }

    public boolean isRootAvailable() {
        if (rootAvailable != null) return rootAvailable;
        rootAvailable = checkRoot();
        return rootAvailable;
    }

    public void refreshRootCache() {
        rootAvailable = null;
    }

    public boolean isSilentInstallEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("silent_install", false) && isRootMode();
    }

    public boolean isRootFileOpsEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("root_file_ops", false) && isRootMode();
    }

    public boolean isRootExtractorEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("root_extractor", false) && isRootMode();
    }

    private boolean checkRoot() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{suBinary(), "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            boolean hasRoot = line != null && line.contains("uid=0");
            process.waitFor(5, TimeUnit.SECONDS);
            process.destroy();
            return hasRoot;
        } catch (Exception e) {
            return false;
        }
    }

    public static String escapeShellArg(String arg) {
        if (arg == null) return "";
        if (SAFE_ARG.matcher(arg).matches()) return arg;
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    public static boolean isPathBlocked(String path) {
        if (path == null) return true;
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        if (BLOCKED_DELETE_PATHS.contains(normalized)) return true;
        for (String blocked : BLOCKED_DELETE_PATHS) {
            if (normalized.equals(blocked)) return true;
        }
        return false;
    }

    public static boolean isPathInKeyDirectory(String path) {
        if (path == null) return false;
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        for (String key : KEY_DIRECTORIES) {
            if (normalized.equals(key) || normalized.startsWith(key + "/")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCriticalPackage(String packageName) {
        if (packageName == null) return true;
        return CRITICAL_PACKAGES.contains(packageName);
    }

    public static boolean isPackageNameValid(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        return packageName.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");
    }

    public static boolean isChmodModeValid(String mode) {
        if (mode == null || mode.isEmpty()) return false;
        return mode.matches("^[0-7]{3,4}$");
    }

    public static boolean isChownOwnerValid(String owner) {
        if (owner == null || owner.isEmpty()) return false;
        return owner.matches("^[a-zA-Z0-9_]+(:[a-zA-Z0-9_]+)?$");
    }

    public static boolean isRebootModeValid(String mode) {
        if (mode == null || mode.isEmpty()) return true;
        return mode.equals("recovery") || mode.equals("bootloader") || mode.equals("-p");
    }

    // ========== Execution ==========

    public ShellResult execute(String command) {
        return execute(command, 30);
    }

    public ShellResult execute(String command, int timeoutSeconds) {
        if (!isRootMode()) {
            return new ShellResult(-1, "", "Root mode is disabled");
        }
        return spawnSu(command, timeoutSeconds);
    }

    private ShellResult spawnSu(String command, int timeoutSeconds) {
        synchronized (lock) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{suBinary(), "-c", command});
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new ShellResult(-1, "", "Command timed out");
                }

                StringBuilder output = new StringBuilder();
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = stdout.readLine()) != null) {
                    if (output.length() > 0) output.append("\n");
                    output.append(line);
                }
                while ((line = stderr.readLine()) != null) {
                    if (error.length() > 0) error.append("\n");
                    error.append(line);
                }

                return new ShellResult(process.exitValue(), output.toString(), error.toString());
            } catch (Exception e) {
                return new ShellResult(-1, "", e.getMessage());
            }
        }
    }

    public static String quoteForSh(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String[] suCommandForFs(String shellCommand) {
        boolean global = false;
        try {
            global = prefersGlobalNs();
        } catch (Exception ignored) {
        }
        if (global) {
            return new String[]{suBinary(), "-c",
                    "nsenter --mount=/proc/1/ns/mnt -- /system/bin/sh -c " + quoteForSh(shellCommand)};
        }
        return new String[]{suBinary(), "-c", shellCommand};
    }

    public ShellResult executeGlobalNs(String command, int timeoutSeconds) {
        if (!isRootMode()) {
            return new ShellResult(-1, "", "Root mode is disabled");
        }
        String wrapped = "nsenter --mount=/proc/1/ns/mnt -- /system/bin/sh -c " + quoteForSh(command);
        return spawnSu(wrapped, timeoutSeconds);
    }

    private volatile int nsWinner;
    private volatile boolean nsProbing;

    public void kickNsProbe() {
        boolean start = false;
        synchronized (this) {
            if (nsWinner == 0 && !nsProbing) {
                nsProbing = true;
                start = true;
            }
        }
        if (!start) return;
        new Thread(() -> {
            boolean global = false;
            try {
                global = probeNamespaces();
            } catch (Exception ignored) {
            }
            synchronized (RootManager.this) {
                nsWinner = global ? 2 : 1;
                nsProbing = false;
            }
        }).start();
    }

    public boolean prefersGlobalNs() {
        if (nsWinner == 2) return true;
        kickNsProbe();
        return false;
    }

    private boolean probeNamespaces() {
        try {
            ShellResult plain = spawnSu("ls -1A -- /data/data 2>/dev/null | wc -l", 10);
            int plainCount = parseCount(plain);
            ShellResult global = spawnSu("nsenter --mount=/proc/1/ns/mnt -- /system/bin/sh -c " + quoteForSh("ls -1A -- /data/data 2>/dev/null | wc -l"), 10);
            int globalCount = parseCount(global);
            if (globalCount > plainCount && globalCount > 1) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static int parseCount(ShellResult r) {
        try {
            if (r != null && r.isSuccess() && r.output != null) return Integer.parseInt(r.output.trim());
        } catch (Exception ignored) {
        }
        return -1;
    }

    public ShellResult executeFs(String command, int timeoutSeconds) {
        if (!isRootMode()) {
            return new ShellResult(-1, "", "Root mode is disabled");
        }
        boolean global = false;
        try {
            global = prefersGlobalNs();
        } catch (Exception ignored) {
        }
        if (global) return executeGlobalNs(command, timeoutSeconds);
        return spawnSu(command, timeoutSeconds);
    }

    public ShellResult executeWithInput(String command, String input) {
        if (!isRootMode()) {
            return new ShellResult(-1, "", "Root mode is disabled");
        }
        synchronized (lock) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{suBinary()});
                DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
                stdin.writeBytes(command + "\n");
                if (input != null) {
                    stdin.writeBytes(input + "\n");
                }
                stdin.writeBytes("exit\n");
                stdin.flush();

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new ShellResult(-1, "", "Command timed out");
                }

                StringBuilder output = new StringBuilder();
                StringBuilder error = new StringBuilder();
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = stdout.readLine()) != null) {
                    if (output.length() > 0) output.append("\n");
                    output.append(line);
                }
                while ((line = stderrReader.readLine()) != null) {
                    if (error.length() > 0) error.append("\n");
                    error.append(line);
                }

                return new ShellResult(process.exitValue(), output.toString(), error.toString());
            } catch (Exception e) {
                return new ShellResult(-1, "", e.getMessage());
            }
        }
    }

    // ========== File Operations ==========

    public List<String> listFiles(String path) throws IOException {
        ShellResult result = executeFs("ls -1 " + escapeShellArg(path), 30);
        if (!result.isSuccess()) throw new IOException("Failed to list: " + result.error);
        List<String> files = new ArrayList<>();
        for (String line : result.output.split("\n")) {
            if (!line.trim().isEmpty()) files.add(line.trim());
        }
        return files;
    }

    public List<String> listFilesDetailed(String path) throws IOException {
        ShellResult result = executeFs("ls -la " + escapeShellArg(path), 30);
        if (!result.isSuccess()) throw new IOException("Failed to list: " + result.error);
        List<String> entries = new ArrayList<>();
        for (String line : result.output.split("\n")) {
            if (!line.trim().isEmpty()) entries.add(line);
        }
        return entries;
    }

    public boolean exists(String path) {
        ShellResult result = executeFs("test -e " + escapeShellArg(path) + " && echo yes || echo no", 30);
        return result.isSuccess() && result.output.trim().equals("yes");
    }

    public boolean isDirectory(String path) {
        ShellResult result = executeFs("test -d " + escapeShellArg(path) + " && echo yes || echo no", 30);
        return result.isSuccess() && result.output.trim().equals("yes");
    }

    public boolean isFile(String path) {
        ShellResult result = executeFs("test -f " + escapeShellArg(path) + " && echo yes || echo no", 30);
        return result.isSuccess() && result.output.trim().equals("yes");
    }

    public long getFileSize(String path) {
        ShellResult result = executeFs("stat -c %s " + escapeShellArg(path), 30);
        if (result.isSuccess()) {
            try {
                return Long.parseLong(result.output.trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    public String getPermissions(String path) {
        ShellResult result = executeFs("stat -c %a " + escapeShellArg(path), 30);
        return result.isSuccess() ? result.output.trim() : null;
    }

    public String getOwner(String path) {
        ShellResult result = executeFs("stat -c %U:%G " + escapeShellArg(path), 30);
        return result.isSuccess() ? result.output.trim() : null;
    }

    public File[] listRootFiles(String path) {
        ShellResult result = executeFs("ls -1a " + escapeShellArg(path), 30);
        if (!result.isSuccess() || result.output == null) return null;

        String[] names = result.output.split("\\r?\\n");
        List<File> files = new ArrayList<>();
        for (String name : names) {
            String trimmed = name.trim();
            if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..")) continue;
            files.add(new File(path, trimmed));
        }
        return files.toArray(new File[0]);
    }

    public void chmod(String path, String mode) throws IOException {
        if (!isChmodModeValid(mode)) {
            throw new IOException("Invalid chmod mode: " + mode + ". Must be 3-4 octal digits (e.g. 755, 0644)");
        }
        ShellResult result = execute("chmod " + mode + " " + escapeShellArg(path));
        if (!result.isSuccess()) throw new IOException("chmod failed: " + result.error);
    }

    public void chown(String path, String owner, String group) throws IOException {
        String arg;
        if (group != null) {
            if (!isChownOwnerValid(owner + ":" + group)) {
                throw new IOException("Invalid owner/group: " + owner + ":" + group);
            }
            arg = owner + ":" + group;
        } else {
            if (!isChownOwnerValid(owner)) {
                throw new IOException("Invalid owner: " + owner);
            }
            arg = owner;
        }
        ShellResult result = execute("chown " + arg + " " + escapeShellArg(path));
        if (!result.isSuccess()) throw new IOException("chown failed: " + result.error);
    }

    public void mkdir(String path) throws IOException {
        ShellResult result = executeFs("mkdir -p " + escapeShellArg(path), 30);
        if (!result.isSuccess()) throw new IOException("mkdir failed: " + result.error);
        invalidateListCache();
    }

    public void delete(String path) throws IOException {
        if (isPathBlocked(path)) {
            throw new IOException("Blocked: refusing to delete critical path: " + path);
        }
        ShellResult result = executeFs("rm -rf " + escapeShellArg(path), 30);
        if (!result.isSuccess()) throw new IOException("delete failed: " + result.error);
        invalidateListCache();
    }

    public void deleteFile(String path) throws IOException {
        if (isPathBlocked(path)) {
            throw new IOException("Blocked: refusing to delete critical path: " + path);
        }
        ShellResult result = executeFs("rm -f " + escapeShellArg(path), 30);
        if (!result.isSuccess()) throw new IOException("delete failed: " + result.error);
        invalidateListCache();
    }

    public void rename(String oldPath, String newPath) throws IOException {
        ShellResult result = executeFs("mv " + escapeShellArg(oldPath) + " " + escapeShellArg(newPath), 30);
        if (!result.isSuccess()) throw new IOException("rename failed: " + result.error);
        invalidateListCache();
    }

    public void copyFile(String src, String dest) throws IOException {
        ShellResult result = executeFs("cp -f " + escapeShellArg(src) + " " + escapeShellArg(dest), 60);
        if (!result.isSuccess()) throw new IOException("copy failed: " + result.error);
        invalidateListCache();
    }

    public void copyDir(String src, String dest) throws IOException {
        ShellResult result = executeFs("cp -rf " + escapeShellArg(src) + " " + escapeShellArg(dest), 120);
        if (!result.isSuccess()) throw new IOException("copy failed: " + result.error);
        invalidateListCache();
    }

    public void touch(String path) throws IOException {
        ShellResult result = executeFs("touch " + escapeShellArg(path), 30);
        if (!result.isSuccess()) throw new IOException("touch failed: " + result.error);
        invalidateListCache();
    }

    public void ln(String target, String link) throws IOException {
        ShellResult result = executeFs("ln -sf " + escapeShellArg(target) + " " + escapeShellArg(link), 30);
        if (!result.isSuccess()) throw new IOException("ln failed: " + result.error);
        invalidateListCache();
    }

    public String readFile(String path) throws IOException {
        ShellResult result = execute("cat " + escapeShellArg(path));
        if (!result.isSuccess()) throw new IOException("read failed: " + result.error);
        return result.output;
    }

    public void writeFile(String path, String content) throws IOException {
        if (!isRootMode()) {
            throw new IOException("Root mode is disabled");
        }
        synchronized (lock) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{suBinary()});
                DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
                stdin.writeBytes("cat > " + escapeShellArg(path) + " << 'ENDOFFILE'\n");
                stdin.writeBytes(content);
                stdin.writeBytes("\nENDOFFILE\n");
                stdin.writeBytes("exit\n");
                stdin.flush();

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("write timed out");
                }
                if (process.exitValue() != 0) {
                    BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    StringBuilder err = new StringBuilder();
                    String line;
                    while ((line = stderr.readLine()) != null) {
                        if (err.length() > 0) err.append("\n");
                        err.append(line);
                    }
                    throw new IOException("write failed: " + err);
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("write failed: " + e.getMessage());
            }
        }
    }

    public void appendFile(String path, String content) throws IOException {
        if (!isRootMode()) {
            throw new IOException("Root mode is disabled");
        }
        synchronized (lock) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{suBinary()});
                DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
                stdin.writeBytes("cat >> " + escapeShellArg(path) + " << 'ENDOFFILE'\n");
                stdin.writeBytes(content);
                stdin.writeBytes("\nENDOFFILE\n");
                stdin.writeBytes("exit\n");
                stdin.flush();

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("append timed out");
                }
                if (process.exitValue() != 0) {
                    BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    StringBuilder err = new StringBuilder();
                    String line;
                    while ((line = stderr.readLine()) != null) {
                        if (err.length() > 0) err.append("\n");
                        err.append(line);
                    }
                    throw new IOException("append failed: " + err);
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("append failed: " + e.getMessage());
            }
        }
    }

    public void copyToRoot(File localSource, String rootPath) throws IOException {
        ShellResult result = executeFs("cp " + escapeShellArg(localSource.getAbsolutePath()) + " " + escapeShellArg(rootPath), 60);
        if (!result.isSuccess()) throw new IOException("copy to root failed: " + result.error);
        invalidateListCache();
    }

    public void installSilent(String apkPath) throws IOException {
        String safeApk = escapeShellArg(apkPath);
        ShellResult copyResult = executeWithInput(
                "cp " + safeApk + " /data/local/tmp/_install.apk && pm install -r /data/local/tmp/_install.apk && rm -f /data/local/tmp/_install.apk",
                null);
        if (!copyResult.isSuccess() || !copyResult.output.contains("Success")) {
            throw new IOException("Install failed: " + copyResult.error + " " + copyResult.output);
        }
    }

    public void installSplitSilent(List<String> apkPaths) throws IOException {
        StringBuilder copyCmd = new StringBuilder();
        for (int i = 0; i < apkPaths.size(); i++) {
            String tmpPath = "/data/local/tmp/_split_" + i + ".apk";
            copyCmd.append("cp ").append(escapeShellArg(apkPaths.get(i))).append(" ").append(escapeShellArg(tmpPath)).append(" ; ");
        }

        long totalSize = 0;
        for (String p : apkPaths) totalSize += new File(p).length();
        String createCmd = "pm install-create -r -S " + totalSize;

        ShellResult copyResult = executeWithInput(copyCmd.toString(), null);
        if (!copyResult.isSuccess()) {
            throw new IOException("Failed to copy split APKs: " + copyResult.error);
        }

        ShellResult createResult = execute(createCmd);
        if (!createResult.isSuccess()) {
            throw new IOException("Failed to create install session: " + createResult.error + " " + createResult.output);
        }

        String sessionLine = createResult.output;
        int bracketStart = sessionLine.lastIndexOf('[');
        int bracketEnd = sessionLine.lastIndexOf(']');
        if (bracketStart < 0 || bracketEnd <= bracketStart) {
            throw new IOException("Failed to parse session ID from: " + sessionLine);
        }
        String sessionId = sessionLine.substring(bracketStart + 1, bracketEnd).trim();

        for (int i = 0; i < apkPaths.size(); i++) {
            String tmpPath = "/data/local/tmp/_split_" + i + ".apk";
            long size = new File(apkPaths.get(i)).length();
            String writeCmd = "pm install-write -S " + size + " " + sessionId + " split" + i + " " + escapeShellArg(tmpPath);
            ShellResult writeResult = execute(writeCmd);
            if (!writeResult.isSuccess()) {
                throw new IOException("Failed to stage split APK " + i + ": " + writeResult.error + " " + writeResult.output);
            }
        }

        ShellResult commitResult = execute("pm install-commit " + sessionId);

        StringBuilder cleanup = new StringBuilder();
        for (int i = 0; i < apkPaths.size(); i++) {
            cleanup.append("rm -f '/data/local/tmp/_split_").append(i).append(".apk';");
        }
        cleanup.append("rm -f '/data/local/tmp/_install.apk'");
        executeWithInput(cleanup.toString(), null);

        if (!commitResult.isSuccess() || (!commitResult.output.contains("Success") && !commitResult.output.contains("Session"))) {
            throw new IOException("Split install failed: " + commitResult.error + " " + commitResult.output);
        }
    }

    public void uninstallSilent(String packageName) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        if (isCriticalPackage(packageName)) {
            throw new IOException("Blocked: refusing to uninstall critical system app: " + packageName);
        }
        ShellResult result = executeWithInput("pm uninstall " + escapeShellArg(packageName), null);
        if (!result.isSuccess() || !result.output.contains("Success")) {
            throw new IOException("Uninstall failed: " + result.error + " " + result.output);
        }
    }

    public void clearAppData(String packageName) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        if (isCriticalPackage(packageName)) {
            throw new IOException("Blocked: refusing to clear data of critical system app: " + packageName);
        }
        ShellResult result = execute("pm clear " + escapeShellArg(packageName));
        if (!result.isSuccess()) throw new IOException("clear data failed: " + result.error);
    }

    public void forceStopApp(String packageName) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        if (isCriticalPackage(packageName)) {
            throw new IOException("Blocked: refusing to force stop critical system app: " + packageName);
        }
        ShellResult result = execute("am force-stop " + escapeShellArg(packageName));
        if (!result.isSuccess()) throw new IOException("force stop failed: " + result.error);
    }

    public void enableApp(String packageName) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        ShellResult result = execute("pm enable " + escapeShellArg(packageName));
        if (!result.isSuccess()) throw new IOException("enable failed: " + result.error);
    }

    public void disableApp(String packageName) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        if (isCriticalPackage(packageName)) {
            throw new IOException("Blocked: refusing to disable critical system app: " + packageName);
        }
        ShellResult result = execute("pm disable-user --user 0 " + escapeShellArg(packageName));
        if (!result.isSuccess()) throw new IOException("disable failed: " + result.error);
    }

    public String getAppApkPath(String packageName) throws IOException {
        if (!isPackageNameValid(packageName)) return null;
        ShellResult result = execute("pm path " + escapeShellArg(packageName));
        if (result.isSuccess() && result.output.contains("package:")) {
            String path = result.output.replace("package:", "").trim();
            if (path.contains("\n")) path = path.split("\n")[0].trim();
            return path;
        }
        return null;
    }

    public String getAppUid(String packageName) {
        if (!isPackageNameValid(packageName)) return null;
        ShellResult result = execute("pm dump " + escapeShellArg(packageName) + " | grep 'userId='");
        if (result.isSuccess() && result.output != null) {
            for (String line : result.output.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("userId=")) {
                    return trimmed.substring(8).trim();
                }
            }
        }
        ShellResult statResult = execute("stat -c %u " + escapeShellArg("/data/data/" + packageName));
        if (statResult.isSuccess() && statResult.output != null) {
            return statResult.output.trim();
        }
        return null;
    }

    public List<String> getAppDataDirs(String packageName) {
        List<String> dirs = new ArrayList<>();
        if (!isPackageNameValid(packageName)) return dirs;
        String[] candidates = {
                "/data/data/" + packageName,
                "/data/user/0/" + packageName,
                "/sdcard/Android/data/" + packageName,
        };
        for (String path : candidates) {
            ShellResult result = execute("test -d " + escapeShellArg(path) + " && echo exists");
            if (result.isSuccess() && "exists".equals(result.output.trim())) {
                dirs.add(path);
            }
        }
        return dirs;
    }

    public List<String> getInstalledPackages() throws IOException {
        ShellResult result = execute("pm list packages -f");
        if (!result.isSuccess()) throw new IOException("list packages failed: " + result.error);
        List<String> packages = new ArrayList<>();
        for (String line : result.output.split("\n")) {
            if (line.startsWith("package:")) {
                packages.add(line.substring(8).trim());
            }
        }
        return packages;
    }

    // ========== System Operations ==========

    public void remountSystem(boolean rw) throws IOException {
        String cmd = rw ? "mount -o remount,rw /system" : "mount -o remount,ro /system";
        ShellResult result = execute(cmd);
        if (!result.isSuccess()) throw new IOException("remount failed: " + result.error);
    }

    public void reboot(String mode) throws IOException {
        if (!isRebootModeValid(mode)) {
            throw new IOException("Invalid reboot mode: " + mode);
        }
        String cmd;
        if (TextUtils.isEmpty(mode)) {
            cmd = "reboot";
        } else {
            cmd = "reboot " + mode;
        }
        ShellResult result = execute(cmd);
        if (!result.isSuccess()) throw new IOException("reboot failed: " + result.error);
    }

    public List<String> getRunningProcesses() throws IOException {
        ShellResult result = execute("ps -A -o PID,USER,NAME");
        if (!result.isSuccess()) throw new IOException("ps failed: " + result.error);
        List<String> processes = new ArrayList<>();
        for (String line : result.output.split("\n")) {
            if (!line.trim().isEmpty()) processes.add(line.trim());
        }
        return processes;
    }

    public String getBuildProp() throws IOException {
        return readFile("/system/build.prop");
    }

    public boolean isSystemRemountedRW() {
        ShellResult result = execute("mount | grep ' /system '");
        return result.isSuccess() && result.output.contains("rw");
    }

    public void backupAppData(String packageName, String outputPath) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        ShellResult result = execute("tar -cf " + escapeShellArg(outputPath) + " -C /data/data " + escapeShellArg(packageName), 120);
        if (!result.isSuccess()) throw new IOException("backup failed: " + result.error);
    }

    public void restoreAppData(String packageName, String archivePath) throws IOException {
        if (!isPackageNameValid(packageName)) {
            throw new IOException("Invalid package name: " + packageName);
        }
        ShellResult result = execute("tar -xf " + escapeShellArg(archivePath) + " -C /data/data", 120);
        if (!result.isSuccess()) throw new IOException("restore failed: " + result.error);
        String dataPath = "/data/data/" + packageName;
        ShellResult existsCheck = execute("test -d " + escapeShellArg(dataPath) + " && echo yes || echo no");
        if (existsCheck.isSuccess() && "yes".equals(existsCheck.output.trim())) {
            execute("chmod -R 771 " + escapeShellArg(dataPath));
            execute("chown -R $(stat -c %U:%G " + escapeShellArg(dataPath) + " 2>/dev/null || echo system:system) " + escapeShellArg(dataPath));
        }
    }


    public static final long MAX_STAGE_BYTES = 100L * 1024L * 1024L;

    private static final String[] ROOT_ONLY_PREFIXES = {
            "/data/data", "/data/user", "/data/user_de",
            "/data/system", "/data/misc", "/data/vendor",
            "/data/property", "/data/adb", "/data/app-private",
            "/root"
    };

    public record RootEntry(String path, boolean exists, boolean isDirectory, boolean isFile,
                            long length, long lastModified, String mode) {
    }

    public static boolean isRootOnlyPath(String path) {
        if (path == null) return false;
        String normalized = path.endsWith("/") && path.length() > 1
                ? path.substring(0, path.length() - 1) : path;
        for (String prefix : ROOT_ONLY_PREFIXES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + "/")) return true;
        }
        return false;
    }

    public boolean needsRootFor(String path) {
        if (path == null || path.isEmpty()) return false;
        try {
            File f = new File(path);
            if (f.exists() && f.canRead()) return false;
        } catch (Exception ignored) {
        }
        if (!isRootFileOpsEnabled() || !isRootAvailable()) return false;
        try {
            return exists(path);
        } catch (Exception e) {
            return false;
        }
    }

    public RootEntry statEntry(String path) {
        if (path == null || path.isEmpty() || !isRootMode()) {
            return new RootEntry(path, false, false, false, 0L, 0L, null);
        }
        try {
            ShellResult r = executeFs("stat -c '%n\037%F\037%s\037%Y\037%a' "
                    + escapeShellArg(path) + " 2>/dev/null", 30);
            if (r.isSuccess() && r.output != null && !r.output.trim().isEmpty()) {
                String line = r.output.split("\\r?\\n")[0];
                String[] parts = line.split("\037", -1);
                if (parts.length >= 5) {
                    String type = parts[1].trim();
                    long size;
                    long mtime;
                    try {
                        size = Long.parseLong(parts[2].trim());
                    } catch (NumberFormatException e) {
                        size = 0L;
                    }
                    try {
                        mtime = Long.parseLong(parts[3].trim()) * 1000L;
                    } catch (NumberFormatException e) {
                        mtime = 0L;
                    }
                    boolean dir = "directory".equalsIgnoreCase(type);
                    boolean reg = "regular file".equalsIgnoreCase(type)
                            || "regular empty file".equalsIgnoreCase(type);
                    return new RootEntry(path, true, dir, reg || (!dir && !"directory".equalsIgnoreCase(type)
                            && !"unknown".equalsIgnoreCase(type) && isFile(path)),
                            size, mtime, parts[4].trim());
                }
            }
        } catch (Exception ignored) {
        }
        // Fallback: separate tests (slower but portable).
        try {
            boolean ex = exists(path);
            if (!ex) return new RootEntry(path, false, false, false, 0L, 0L, null);
            boolean dir = isDirectory(path);
            boolean file = !dir && isFile(path);
            long size = dir ? 0L : getFileSize(path);
            return new RootEntry(path, true, dir, file, Math.max(0L, size), 0L, getPermissions(path));
        } catch (Exception ignored) {
            return new RootEntry(path, false, false, false, 0L, 0L, null);
        }
    }

    public File[] listRootFilesWithStat(String dirPath) {
        File[] cached = cachedList(dirPath);
        if (cached != null) return cached;
        File[] fresh = listRootFilesFresh(dirPath);
        putCachedList(dirPath, fresh);
        return fresh == null ? null : fresh.clone();
    }

    private record ListCacheEntry(File[] files, long at) {
    }

    private final java.util.Map<String, ListCacheEntry> listCache = new java.util.HashMap<>();
    private static final long LIST_CACHE_TTL = 8000L;

    private synchronized File[] cachedList(String dirPath) {
        if (dirPath == null) return null;
        try {
            ListCacheEntry e = listCache.get(dirPath);
            if (e != null && e.files != null && System.currentTimeMillis() - e.at < LIST_CACHE_TTL) {
                return e.files.clone();
            }
            if (e != null) listCache.remove(dirPath);
        } catch (Exception ignored) {
        }
        return null;
    }

    private synchronized void putCachedList(String dirPath, File[] files) {
        if (dirPath == null) return;
        try {
            if (files == null) {
                listCache.remove(dirPath);
                return;
            }
            if (listCache.size() > 60) listCache.clear();
            listCache.put(dirPath, new ListCacheEntry(files.clone(), System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
    }

    public synchronized void invalidateListCache() {
        try {
            listCache.clear();
        } catch (Exception ignored) {
        }
    }

    private File[] listRootFilesFresh(String dirPath) {
        File[] primary = listRootFilesDetailed(dirPath);
        if (!isAppDataDir(dirPath)) {
            if (primary != null && primary.length > 0) return primary;
            File[] portable = listRootFilesPortable(dirPath);
            if (portable != null && portable.length > 0) return portable;
            return primary != null ? primary : portable;
        }
        if (primary != null && primary.length >= 30) return primary;
        File[] explicit = listAppDataDirsExplicit(dirPath);
        File[] merged = unionRootFiles(primary, explicit);
        if (merged != null && merged.length > 0) return merged;
        File[] portable = listRootFilesPortable(dirPath);
        return unionRootFiles(merged, portable);
    }

    private static boolean isAppDataDir(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) return false;
        String n = dirPath.endsWith("/") && dirPath.length() > 1
                ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
        if (n.equals("/data/data")) return true;
        return n.matches("^/data/user(_de)?/\\d+$");
    }

    private static File[] unionRootFiles(File[] a, File[] b) {
        java.util.LinkedHashMap<String, File> map = new java.util.LinkedHashMap<>();
        if (a != null) {
            for (File f : a) {
                if (f != null && !map.containsKey(f.getName())) map.put(f.getName(), f);
            }
        }
        if (b != null) {
            for (File f : b) {
                if (f != null && !map.containsKey(f.getName())) map.put(f.getName(), f);
            }
        }
        if (map.isEmpty()) return a != null ? a : b;
        return map.values().toArray(new File[0]);
    }

    public File[] listAppDataDirsExplicit(String dirPath) {
        if (dirPath == null || dirPath.isEmpty() || !isRootMode()) return null;
        String base = dirPath.endsWith("/") && dirPath.length() > 1
                ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
        List<String> pkgs = new ArrayList<>();
        try {
            List<android.content.pm.ApplicationInfo> apps =
                    context.getPackageManager().getInstalledApplications(0);
            if (apps != null) {
                for (android.content.pm.ApplicationInfo app : apps) {
                    if (app != null && isPackageNameValid(app.packageName)) pkgs.add(app.packageName);
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (pkgs.isEmpty()) return null;
        List<File> out = new ArrayList<>();
        for (int i = 0; i < pkgs.size(); i += 150) {
            int end = Math.min(i + 150, pkgs.size());
            StringBuilder script = new StringBuilder("for p in");
            for (int j = i; j < end; j++) {
                script.append(" ").append(escapeShellArg(base + "/" + pkgs.get(j)));
            }
            script.append("; do [ -e \"$p\" ] || [ -L \"$p\" ] || continue; ");
            script.append("if s=$(stat -c '%n\037%F\037%s\037%Y\037%a' \"$p\" 2>/dev/null); then :; ");
            script.append("else s=$(printf '%s\037unknown\0370\0370\037' \"$p\"); fi; ");
            script.append("if [ -d \"$p\" ]; then printf '%s\0371\\n' \"$s\"; ");
            script.append("else printf '%s\0370\\n' \"$s\"; fi; done");
            try {
                ShellResult r = executeFs(script.toString(), 30);
                if (r.isSuccess() && r.output != null) parseStatLines(out, r.output, base);
            } catch (Exception ignored) {
            }
        }
        return out.toArray(new File[0]);
    }

    private void parseStatLines(List<File> files, String output, String fallbackParent) {
        for (String line : output.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\037", -1);
            if (parts.length < 6) continue;
            String full = parts[0];
            String name = full;
            String parent = fallbackParent;
            int slash = full.lastIndexOf('/');
            if (slash >= 0) {
                if (slash + 1 < full.length()) name = full.substring(slash + 1);
                parent = full.substring(0, slash);
            }
            if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;
            long size;
            long mtime;
            try {
                size = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException e) {
                size = 0L;
            }
            try {
                mtime = Long.parseLong(parts[3].trim()) * 1000L;
            } catch (NumberFormatException e) {
                mtime = 0L;
            }
            String mode = parts[4].trim();
            boolean dir = "1".equals(parts[5].trim());
            files.add(new RootFile(parent, name,
                    true, dir, !dir,
                    dir ? 0L : Math.max(0L, size), mtime,
                    mode.isEmpty() ? null : mode));
        }
    }

    public File[] listRootFilesPortable(String dirPath) {
        if (dirPath == null || dirPath.isEmpty() || !isRootMode()) return null;
        ShellResult result = executeFs("ls -1Ap -- " + escapeShellArg(dirPath) + " 2>/dev/null", 30);
        if (!result.isSuccess() || result.output == null) return null;
        List<File> files = new ArrayList<>();
        for (String line : result.output.split("\\r?\\n")) {
            if (line.isEmpty()) continue;
            boolean dir = line.endsWith("/");
            String name = dir ? line.substring(0, line.length() - 1) : line;
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            if (name.isEmpty() || name.equals(".") || name.equals("..")) continue;
            files.add(new RootFile(dirPath, name, true, dir, !dir, 0L, 0L, null));
        }
        return files.toArray(new File[0]);
    }

    public File[] listRootFilesDetailed(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) return null;

        String script = "d=" + escapeShellArg(dirPath) + "; "
                + "for f in \"$d\"/* \"$d\"/.*; do "
                + "[ -e \"$f\" ] || [ -L \"$f\" ] || continue; "
                + "case \"$f\" in \"$d/..\"|\"$d/.\") continue;; esac; "
                + "if s=$(stat -c '%n\037%F\037%s\037%Y\037%a' \"$f\" 2>/dev/null); then :; "
                + "else s=$(printf '%s\037unknown\0370\0370\037' \"$f\"); fi; "
                + "if [ -d \"$f\" ]; then printf '%s\0371\\n' \"$s\"; "
                + "else printf '%s\0370\\n' \"$s\"; fi; "
                + "done";
        ShellResult result = executeFs(script, 30);
        if (!result.isSuccess() || result.output == null) return null;
        List<File> files = new ArrayList<>();
        parseStatLines(files, result.output, dirPath);
        return files.toArray(new File[0]);
    }

    public long streamFromRoot(String srcPath, java.io.OutputStream out, long maxBytes) throws IOException {
        if (!isRootMode()) throw new IOException("Root mode is disabled");
        if (srcPath == null || !srcPath.startsWith("/")) {
            throw new IOException("Refusing to read non-absolute path");
        }
        long size = getFileSize(srcPath);
        if (size > maxBytes) {
            throw new IOException("File too large for preview (" + size + " bytes, limit " + maxBytes + "). Copy it instead.");
        }
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(suCommandForFs("cat " + escapeShellArg(srcPath)));
            java.io.InputStream stdout = process.getInputStream();
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = stdout.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    process.destroyForcibly();
                    throw new IOException("File too large for preview (limit " + maxBytes + " bytes). Copy it instead.");
                }
                out.write(buf, 0, n);
            }
            out.flush();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Read timed out: " + srcPath);
            }
            if (process.exitValue() != 0) {
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder err = new StringBuilder();
                String line;
                while ((line = stderr.readLine()) != null) {
                    if (err.length() > 0) err.append("\n");
                    err.append(line);
                }
                throw new IOException("Root read failed: " + err);
            }
            return total;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Root read failed: " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * Binary-safe root write-back: streams a local staged file into
     * {@code su -c "cat > dst"}. Using {@code cat >} (truncate) preserves the
     * existing inode owner/permissions when overwriting, which is safer than
     * {@code cp} (which could orphan perms). Refuses blocked/critical paths.
     */
    public void streamToRoot(File localSrc, String dstPath) throws IOException {
        if (!isRootMode()) throw new IOException("Root mode is disabled");
        if (dstPath == null || !dstPath.startsWith("/")) {
            throw new IOException("Refusing to write non-absolute path");
        }
        if (isPathBlocked(dstPath)) {
            throw new IOException("Blocked: refusing to write critical path: " + dstPath);
        }
        if (localSrc == null || !localSrc.isFile() || !localSrc.canRead()) {
            throw new IOException("Staged file unreadable");
        }
        if (localSrc.length() > MAX_STAGE_BYTES) {
            throw new IOException("File too large to write back (" + localSrc.length() + " bytes)");
        }
        Process process = null;
        try {
            String cmd = "cat " + escapeShellArg(localSrc.getAbsolutePath())
                    + " > " + escapeShellArg(dstPath);
            process = Runtime.getRuntime().exec(suCommandForFs(cmd));
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Write timed out: " + dstPath);
            }
            if (process.exitValue() != 0) {
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder err = new StringBuilder();
                String line;
                while ((line = stderr.readLine()) != null) {
                    if (err.length() > 0) err.append("\n");
                    err.append(line);
                }
                throw new IOException("Root write failed: " + err);
            }
            invalidateListCache();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Root write failed: " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    // ========== Utility ==========

    public String getMountInfo() throws IOException {
        ShellResult result = execute("mount");
        return result.isSuccess() ? result.output : "";
    }

    public boolean isMountedRW(String mountPoint) {
        ShellResult result = execute("mount | grep ' " + mountPoint + " '");
        return result.isSuccess() && result.output.contains("rw");
    }

    public List<String> listUsers() throws IOException {
        List<String> users = new ArrayList<>();
        String[][] paths = {
                {"/etc/passwd"},
                {"/system/etc/passwd"},
                {"/apex/com.android.runtime/etc/passwd"},
                {"/system/bin/toybox", "cat /etc/passwd"}
        };
        for (String[] cmd : paths) {
            try {
                ShellResult r = execute("cat " + escapeShellArg(cmd[0]) + " 2>/dev/null");
                if (r.isSuccess() && !r.output.trim().isEmpty()) {
                    parsePasswd(r.output, users);
                    if (!users.isEmpty()) return users;
                }
            } catch (Exception ignored) {}
        }
        users.add("root (0)");
        users.add("daemon (1)");
        users.add("system (1000)");
        users.add("shell (2000)");
        users.add("media_rw (1023)");
        users.add("app (10000)");
        users.add("nobody (65534)");
        return users;
    }

    private void parsePasswd(String content, List<String> users) {
        for (String line : content.split("\n")) {
            String[] parts = line.split(":");
            if (parts.length >= 3 && !parts[0].trim().isEmpty()) {
                String name = parts[0].trim();
                String uid = parts[2].trim();
                if (uid.matches("\\d+")) {
                    String entry = name + " (" + uid + ")";
                    boolean exists = false;
                    for (String u : users) {
                        if (u.equals(entry)) { exists = true; break; }
                    }
                    if (!exists) users.add(entry);
                }
            }
        }
    }

    public List<String> listGroups() throws IOException {
        List<String> groups = new ArrayList<>();
        String[] paths = {
                "/etc/group",
                "/system/etc/group",
                "/apex/com.android.runtime/etc/group"
        };
        for (String path : paths) {
            try {
                ShellResult r = execute("cat " + escapeShellArg(path) + " 2>/dev/null");
                if (r.isSuccess() && !r.output.trim().isEmpty()) {
                    parseGroup(r.output, groups);
                    if (!groups.isEmpty()) return groups;
                }
            } catch (Exception ignored) {}
        }
        ShellResult idResult = execute("id 2>/dev/null");
        if (idResult.isSuccess() && !idResult.output.trim().isEmpty()) {
            String output = idResult.output;
            int gidsStart = output.indexOf("groups=");
            if (gidsStart > 0) {
                String gidsStr = output.substring(gidsStart + 7);
                for (String token : gidsStr.split("[,\\s]+")) {
                    token = token.trim();
                    if (token.isEmpty()) continue;
                    String gid = token.contains("(") ? token.substring(0, token.indexOf("(")) : token;
                    String name = token.contains("(") && token.contains(")")
                            ? token.substring(token.indexOf("(") + 1, token.indexOf(")"))
                            : "gid" + gid;
                    if (gid.matches("\\d+")) {
                        groups.add(name + " (" + gid + ")");
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            groups.add("root (0)");
            groups.add("daemon (1)");
            groups.add("system (1000)");
            groups.add("shell (2000)");
            groups.add("media_rw (1023)");
            groups.add("log (1007)");
            groups.add("radio (1001)");
            groups.add("bluetooth (1002)");
            groups.add("sdcard_rw (1015)");
            groups.add("nobody (65534)");
        }
        return groups;
    }

    private void parseGroup(String content, List<String> groups) {
        for (String line : content.split("\n")) {
            String[] parts = line.split(":");
            if (parts.length >= 3 && !parts[0].trim().isEmpty()) {
                String name = parts[0].trim();
                String gid = parts[2].trim();
                if (gid.matches("\\d+")) {
                    String entry = name + " (" + gid + ")";
                    boolean exists = false;
                    for (String g : groups) {
                        if (g.equals(entry)) { exists = true; break; }
                    }
                    if (!exists) groups.add(entry);
                }
            }
        }
    }

    public record ShellResult(int exitCode, String output, String error) {
            public ShellResult(int exitCode, String output, String error) {
                this.exitCode = exitCode;
                this.output = output != null ? output : "";
                this.error = error != null ? error : "";
            }

            public boolean isSuccess() {
                return exitCode == 0;
            }
        }
}
