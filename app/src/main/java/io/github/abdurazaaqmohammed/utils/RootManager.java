package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RootManager {

    private static volatile RootManager instance;
    private final Context context;
    private Boolean rootAvailable = null;
    private Process suProcess;
    private DataOutputStream suStdin;
    private BufferedReader suStdout;
    private final Object lock = new Object();

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
        ROOT("root", "Root");

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
        return WorkingMode.NON_ROOT;
    }

    public void setWorkingMode(WorkingMode mode) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString("working_mode", mode.key)
                .apply();
    }

    public boolean isRootMode() {
        return getWorkingMode() == WorkingMode.ROOT;
    }

    public boolean isRootAvailable() {
        if (rootAvailable != null) return rootAvailable;
        rootAvailable = checkRoot();
        return rootAvailable;
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
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
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

    public ShellResult execute(String command) {
        return execute(command, 30);
    }

    public ShellResult execute(String command, int timeoutSeconds) {
        if (!isRootMode()) {
            return new ShellResult(-1, "", "Root mode is disabled");
        }
        synchronized (lock) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
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

    public ShellResult executeWithInput(String command, String input) {
        if (!isRootMode()) {
            return new ShellResult(-1, "", "Root mode is disabled");
        }
        synchronized (lock) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su"});
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
        ShellResult result = execute("ls -1 \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("Failed to list: " + result.error);
        List<String> files = new ArrayList<>();
        for (String line : result.output.split("\n")) {
            if (!line.trim().isEmpty()) files.add(line.trim());
        }
        return files;
    }

    public List<String> listFilesDetailed(String path) throws IOException {
        ShellResult result = execute("ls -la \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("Failed to list: " + result.error);
        List<String> entries = new ArrayList<>();
        for (String line : result.output.split("\n")) {
            if (!line.trim().isEmpty()) entries.add(line);
        }
        return entries;
    }

    public boolean exists(String path) {
        ShellResult result = execute("test -e \"" + path + "\" && echo yes || echo no");
        return result.isSuccess() && result.output.trim().equals("yes");
    }

    public boolean isDirectory(String path) {
        ShellResult result = execute("test -d \"" + path + "\" && echo yes || echo no");
        return result.isSuccess() && result.output.trim().equals("yes");
    }

    public boolean isFile(String path) {
        ShellResult result = execute("test -f \"" + path + "\" && echo yes || echo no");
        return result.isSuccess() && result.output.trim().equals("yes");
    }

    public long getFileSize(String path) {
        ShellResult result = execute("stat -c %s \"" + path + "\"");
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
        ShellResult result = execute("stat -c %a \"" + path + "\"");
        return result.isSuccess() ? result.output.trim() : null;
    }

    public String getOwner(String path) {
        ShellResult result = execute("stat -c %U:%G \"" + path + "\"");
        return result.isSuccess() ? result.output.trim() : null;
    }

    public void chmod(String path, String mode) throws IOException {
        ShellResult result = execute("chmod " + mode + " \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("chmod failed: " + result.error);
    }

    public void chown(String path, String owner, String group) throws IOException {
        String arg = group != null ? owner + ":" + group : owner;
        ShellResult result = execute("chown " + arg + " \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("chown failed: " + result.error);
    }

    public void mkdir(String path) throws IOException {
        ShellResult result = execute("mkdir -p \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("mkdir failed: " + result.error);
    }

    public void delete(String path) throws IOException {
        ShellResult result = execute("rm -rf \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("delete failed: " + result.error);
    }

    public void deleteFile(String path) throws IOException {
        ShellResult result = execute("rm -f \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("delete failed: " + result.error);
    }

    public void rename(String oldPath, String newPath) throws IOException {
        ShellResult result = execute("mv \"" + oldPath + "\" \"" + newPath + "\"");
        if (!result.isSuccess()) throw new IOException("rename failed: " + result.error);
    }

    public void copyFile(String src, String dest) throws IOException {
        ShellResult result = execute("cp -f \"" + src + "\" \"" + dest + "\"");
        if (!result.isSuccess()) throw new IOException("copy failed: " + result.error);
    }

    public void copyDir(String src, String dest) throws IOException {
        ShellResult result = execute("cp -rf \"" + src + "\" \"" + dest + "\"");
        if (!result.isSuccess()) throw new IOException("copy failed: " + result.error);
    }

    public void touch(String path) throws IOException {
        ShellResult result = execute("touch \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("touch failed: " + result.error);
    }

    public void ln(String target, String link) throws IOException {
        ShellResult result = execute("ln -sf \"" + target + "\" \"" + link + "\"");
        if (!result.isSuccess()) throw new IOException("ln failed: " + result.error);
    }

    public String readFile(String path) throws IOException {
        ShellResult result = execute("cat \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("read failed: " + result.error);
        return result.output;
    }

    public void writeFile(String path, String content) throws IOException {
        String escaped = content.replace("'", "'\\''");
        ShellResult result = execute("echo '" + escaped + "' > \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("write failed: " + result.error);
    }

    public void appendFile(String path, String content) throws IOException {
        String escaped = content.replace("'", "'\\''");
        ShellResult result = execute("echo '" + escaped + "' >> \"" + path + "\"");
        if (!result.isSuccess()) throw new IOException("append failed: " + result.error);
    }

    public void copyFromRoot(String rootPath, File localDest) throws IOException {
        ShellResult result = execute("cat \"" + rootPath + "\"");
        if (!result.isSuccess()) throw new IOException("read failed: " + result.error);
        java.io.FileOutputStream fos = new FileOutputStream(localDest);
        fos.write(result.output.getBytes());
        fos.close();
    }

    public void copyToRoot(File localSource, String rootPath) throws IOException {
        ShellResult result = execute("cp \"" + localSource.getAbsolutePath() + "\" \"" + rootPath + "\"");
        if (!result.isSuccess()) throw new IOException("copy to root failed: " + result.error);
    }

    public void installSilent(String apkPath) throws IOException {
        ShellResult copyResult = executeWithInput("cp '" + apkPath + "' /data/local/tmp/_install.apk && pm install -r -g /data/local/tmp/_install.apk && rm /data/local/tmp/_install.apk", null);
        if (!copyResult.isSuccess() || !copyResult.output.contains("Success")) {
                throw new IOException("Install failed: " + copyResult.error + " " + copyResult.output);
         }
    }

    public void installSplitSilent(List<String> apkPaths) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apkPaths.size(); i++) {
            String tmpPath = "/data/local/tmp/_split_" + i + ".apk";
            sb.append("cp '").append(apkPaths.get(i)).append("' ").append(tmpPath).append(" && ");
        }
        sb.append("pm install-create -r -g -S ");
        long totalSize = 0;
        for (String p : apkPaths) totalSize += new File(p).length();
        sb.append(totalSize).append(" ");
        for (int i = 0; i < apkPaths.size(); i++) {
            String tmpPath = "/data/local/tmp/_split_" + i + ".apk";
            sb.append("-i ").append(tmpPath).append(" ");
        }
        sb.append("&& pm install-commit && ");
        for (int i = 0; i < apkPaths.size(); i++) {
            sb.append("rm /data/local/tmp/_split_").append(i).append(".apk && ");
        }
        sb.append("rm /data/local/tmp/_install.apk");

        ShellResult result = executeWithInput(sb.toString(), null);
        if (!result.isSuccess() || (!result.output.contains("Success") && !result.output.contains("install-commit"))) {
            throw new IOException("Split install failed: " + result.error + " " + result.output);
        }
    }

    public void uninstallSilent(String packageName) throws IOException {
        ShellResult result = executeWithInput("pm uninstall '" + packageName + "'", null);
        if (!result.isSuccess() || !result.output.contains("Success")) {
            throw new IOException("Uninstall failed: " + result.error + " " + result.output);
        }
    }

    public void clearAppData(String packageName) throws IOException {
        ShellResult result = execute("pm clear \"" + packageName + "\"");
        if (!result.isSuccess()) throw new IOException("clear data failed: " + result.error);
    }

    public void forceStopApp(String packageName) throws IOException {
        ShellResult result = execute("am force-stop \"" + packageName + "\"");
        if (!result.isSuccess()) throw new IOException("force stop failed: " + result.error);
    }

    public void enableApp(String packageName) throws IOException {
        ShellResult result = execute("pm enable \"" + packageName + "\"");
        if (!result.isSuccess()) throw new IOException("enable failed: " + result.error);
    }

    public void disableApp(String packageName) throws IOException {
        ShellResult result = execute("pm disable-user --user 0 \"" + packageName + "\"");
        if (!result.isSuccess()) throw new IOException("disable failed: " + result.error);
    }

    public void freezeApp(String packageName) throws IOException {
        disableApp(packageName);
    }

    public void unfreezeApp(String packageName) throws IOException {
        enableApp(packageName);
    }

    public String getAppApkPath(String packageName) throws IOException {
        ShellResult result = execute("pm path \"" + packageName + "\"");
        if (result.isSuccess() && result.output.contains("package:")) {
            String path = result.output.replace("package:", "").trim();
            if (path.contains("\n")) path = path.split("\n")[0].trim();
            return path;
        }
        return null;
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
        String cmd;
        if (mode == null || mode.isEmpty()) {
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

    public void setBuildProp(String key, String value) throws IOException {
        ShellResult result = execute("sed -i 's|^" + key + ".*|" + key + "=" + value + "|' /system/build.prop");
        if (!result.isSuccess()) throw new IOException("set build.prop failed: " + result.error);
    }

    public void remountSystemRW() throws IOException {
        ShellResult result = execute("mount -o remount,rw /");
        if (!result.isSuccess()) throw new IOException("remount rw failed: " + result.error);
    }

    public boolean isSystemRemountedRW() {
        ShellResult result = execute("mount | grep ' /system '");
        return result.isSuccess() && result.output.contains("rw");
    }

    public void backupAppData(String packageName, String outputPath) throws IOException {
        ShellResult result = execute("tar -cf \"" + outputPath + "\" -C /data/data \"" + packageName + "\"", 120);
        if (!result.isSuccess()) throw new IOException("backup failed: " + result.error);
    }

    public void restoreAppData(String packageName, String archivePath) throws IOException {
        ShellResult result = execute("tar -xf \"" + archivePath + "\" -C /data/data", 120);
        if (!result.isSuccess()) throw new IOException("restore failed: " + result.error);
        execute("chmod -R 771 '/data/data/" + packageName + "'");
        execute("chown -R $(stat -c %U:%G '/data/data/" + packageName + "' 2>/dev/null || echo system:system) '/data/data/" + packageName + "'");
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

    public static class ShellResult {
        public final int exitCode;
        public final String output;
        public final String error;

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
