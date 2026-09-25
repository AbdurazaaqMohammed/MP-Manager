package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.abdurazaaqmohammed.MPManager.R;

public class NativeToolManager {

    public static final String DEFAULT_BASE_URL = "https://github.com/AbdurazaaqMohammed/MP-Manager/releases/download/native-tools-v1";

    public interface ReadyCallback {
        void onReady();

        void onError(String message);
    }

    public interface ProgressCallback {
        void onProgress(String file, long done, long total);

        void onDone();

        void onError(String message);
    }

    private static String baseUrl(Context context) {
        try {
            String custom = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("native_tools_url", "").trim();
            if (!custom.isEmpty()) return custom.replaceAll("/+$", "");
        } catch (Exception ignored) {
        }
        return DEFAULT_BASE_URL;
    }

    public static String deviceAbi() {
        if(Build.VERSION.SDK_INT < 21) return Build.CPU_ABI;
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null) {
            for (String abi : abis) {
                if (abi.startsWith("arm64")) return "arm64-v8a";
                if (abi.startsWith("armeabi")) return "armeabi-v7a";
                if (abi.startsWith("x86_64")) return "x86_64";
                if (abi.startsWith("x86")) return "x86";
            }
            if (abis.length > 0) return abis[0];
        }
        return "arm64-v8a";
    }

    private static File packDir(Context context, String pack) {
        return new File(new File(context.getFilesDir(), "native"), pack);
    }

    private static boolean markerOk(File dir, int version) {
        try {
            File marker = new File(dir, "pack.version");
            if (!marker.isFile()) return false;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(marker)))) {
                return Integer.parseInt(br.readLine().trim()) == version;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeMarker(File dir, int version) {
        try {
            dir.mkdirs();
            try (OutputStream os = new FileOutputStream(new File(dir, "pack.version"))) {
                os.write(String.valueOf(version).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        try {
            f.delete();
        } catch (Exception ignored) {
        }
    }

    private static File nativeRoot(Context context) {
        return packDir(context, "native-" + deviceAbi());
    }

    private static File bundledFile(Context context, String name) {
        try {
            File f = new File(context.getApplicationInfo().nativeLibraryDir, name);
            if (f.isFile()) return f;
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isJpegtranReady(Context context) {
        return markerOk(nativeRoot(context), 4) && jpegtranJniLib(context).isFile();
    }

    public static File jpegtranBinary(Context context) {
        File bundled = bundledFile(context, "libjpegtran.so");
        if (bundled != null) return bundled;
        return new File(nativeRoot(context), "bin/jpegtran");
    }

    public static void ensureJpegtran(Activity activity, ReadyCallback cb) {
        if (bundledFile(activity, "libjpegtran.so") != null) {
            cb.onReady();
            return;
        }
        ensurePacks(activity, new String[]{"native-" + deviceAbi() + ".zip"},
                new String[]{"native-" + deviceAbi()}, 4, cb);
    }

    public static File jpegtranJniLib(Context context) {
        return new File(nativeRoot(context), "lib/libjpegtran_jni.so");
    }

    public static boolean loadJpegtranJni(Context context) {
        File lib = jpegtranJniLib(context);
        if (!lib.isFile()) return false;
        return JpegtranJni.load(lib.getAbsolutePath());
    }

    public static void runJpegtranJni(Context context, int op, String src, String dst,
                                      int cropW, int cropH, int cropX, int cropY) throws Exception {
        if (!loadJpegtranJni(context)) throw new IOException("JNI library not installed");
        String[] err = new String[1];
        int rc = JpegtranJni.transform(src, dst, op, cropW, cropH, cropX, cropY, err);
        if (rc != 0) {
            String message = err[0] == null || err[0].isEmpty() ? "Transform failed" : err[0];
            throw new IOException(message);
        }
        File out = new File(dst);
        if (!out.isFile() || out.length() == 0) throw new IOException("Empty output");
    }

    private static void ensurePacks(Activity activity, String[] zips, String[] packs, int version, ReadyCallback cb) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < packs.length; i++) {
            if (!markerOk(packDir(activity, packs[i]), version)) missing.add(i);
        }
        if (missing.isEmpty()) {
            cb.onReady();
            return;
        }
        long totalBytes = 0;
        for (int i : missing) totalBytes += 12L * 1024 * 1024;
        final long approx = totalBytes;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.native_required))
                .setMessage(activity.getString(R.string.native_need_download, String.valueOf(approx / 1024 / 1024)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> cb.onError(activity.getString(R.string.op_cancelled)))
                .setNeutralButton(activity.getString(R.string.set_url), (d, w) -> showUrlDialog(activity, cb))
                .setPositiveButton(activity.getString(R.string.download), (d, w) -> downloadPacks(activity, zips, packs, version, missing, cb))
                .show();
    }

    private static void showUrlDialog(Activity activity, ReadyCallback cb) {
        EditText input = new EditText(activity);
        input.setHint(DEFAULT_BASE_URL);
        try {
            String current = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString("native_tools_url", "");
            input.setText(current == null ? "" : current);
        } catch (Exception ignored) {
        }
        input.setSingleLine(false);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.native_pack_server))
                .setMessage(activity.getString(R.string.native_pack_msg))
                .setView(input)
                .setNegativeButton(android.R.string.cancel, (d, w) -> cb.onError(activity.getString(R.string.op_cancelled)))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    setCustomBaseUrl(activity, input.getText() == null ? "" : input.getText().toString());
                    cb.onError(activity.getString(R.string.url_saved_reopen));
                }).show();
    }

    private static void downloadPacks(Activity activity, String[] zips, String[] packs, int version,
                                      List<Integer> missing, ReadyCallback cb) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);
        TextView status = new TextView(activity);
        status.setTextSize(14);
        root.addView(status);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.native_downloading))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, (d, w) -> cb.onError(activity.getString(R.string.op_cancelled)))
                .create();
        dialog.show();
        final boolean[] cancelled = {false};
        dialog.setOnDismissListener(d -> cancelled[0] = true);
        String base = baseUrl(activity);
        new Thread(() -> {
            try {
                Map<String, String[]> manifest = fetchManifest(base);
                for (int idx : missing) {
                    if (cancelled[0]) return;
                    String zipName = zips[idx];
                    String pack = packs[idx];
                    final String fZip = zipName;
                    activity.runOnUiThread(() -> {
                        status.setText(activity.getString(R.string.downloading_x, fZip));
                        bar.setIndeterminate(true);
                    });
                    File tmp = new File(activity.getCacheDir(), fZip + ".part");
                    downloadFile(base + "/" + zipName, tmp, (done, total) -> activity.runOnUiThread(() -> {
                        if (total > 0) {
                            bar.setIndeterminate(false);
                            bar.setProgress((int) (1000 * done / total));
                            status.setText(fZip + " " + done / 1024 / 1024 + "/" + total / 1024 / 1024 + " MB");
                        }
                    }));
                    if (cancelled[0]) return;
                    String[] meta = manifest.get(zipName);
                    if (meta != null) {
                        if (tmp.length() != Long.parseLong(meta[1])) throw new IOException("Size mismatch");
                        if (!sha256(tmp).equalsIgnoreCase(meta[0])) throw new IOException("Checksum mismatch");
                    }
                    File dest = packDir(activity, pack);
                    deleteRecursive(dest);
                    dest.mkdirs();
                    unzip(tmp, dest);
                    chmodRecursive(dest);
                    writeMarker(dest, version);
                    tmp.delete();
                }
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    cb.onReady();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    new ErrorUtil(activity).showError(e);
                    //cb.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                });
            }
        }).start();
    }

    private static Map<String, String[]> fetchManifest(String base) {
        Map<String, String[]> out = new HashMap<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(base + "/packs.json").openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject root = new JSONObject(sb.toString());
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (k.equals("version")) continue;
                    JSONObject o = root.getJSONObject(k);
                    out.put(k, new String[]{o.getString("sha256"), String.valueOf(o.getLong("size"))});
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private interface DownloadProgress {
        void onProgress(long done, long total);
    }

    private static void downloadFile(String url, File out, DownloadProgress progress) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        long total = conn.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            long done = 0;
            int n;
            long lastReport = 0;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
                done += n;
                if (done - lastReport > 256 * 1024) {
                    lastReport = done;
                    progress.onProgress(done, total);
                }
            }
            progress.onProgress(done, total);
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private static void unzip(File zip, File dest) throws Exception {
        String destPath = dest.getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buf = new byte[65536];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                while (name.startsWith("/")) name = name.substring(1);
                File out = new File(dest, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.equals(destPath) && !canonical.startsWith(destPath + File.separator)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory() || name.endsWith("/")) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void chmodRecursive(File f) {
        setExecutableChecked(f);
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) chmodRecursive(k);
        }
    }

    public static boolean setExecutableChecked(File f) {
        try {
            f.setExecutable(true, true);
        } catch (Exception ignored) {
        }
        if (f.canExecute()) return true;
        try {
            Process chmod = new ProcessBuilder("sh", "-c", "chmod 700 '" + f.getAbsolutePath().replace("'", "") + "'").start();
            chmod.waitFor();
        } catch (Exception ignored) {
        }
        return f.canExecute();
    }

    public static String describeFile(File f) {
        return "exists=" + f.exists() + " size=" + f.length()
                + " r=" + f.canRead() + " w=" + f.canWrite() + " x=" + f.canExecute();
    }

    public static String explainExecError(Context context, File bin, Exception e) {
        String base = e.getMessage() != null ? e.getMessage() : e.toString();
        if (!base.contains("Permission denied") && !base.contains("error=13")) return base;
        return base + "\n\nThis device forbids running downloaded binaries from app storage"
                + " even though the file is executable. Install a build with bundled native libs"
                + " (jniLibs) instead — see the Check screen.";
    }

    public static int runJpegtran(Context context, List<String> args) throws Exception {
        File bin = jpegtranBinary(context);
        if (!bin.isFile()) throw new IOException("jpegtran missing, reinstall native tools");
        if (!setExecutableChecked(bin)) {
            throw new IOException("Permission denied: cannot execute " + bin.getName()
                    + " (" + describeFile(bin) + ")");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(bin.getAbsolutePath());
        cmd.addAll(args);
        Process process;
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException(explainExecError(context, bin, e));
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        int code = process.waitFor();
        if (code != 0 && sb.length() > 0) throw new IOException(sb.toString().trim());
        if (code != 0) throw new IOException("jpegtran failed");
        return code;
    }

    public static String diagnoseExec(Context context) {
        StringBuilder sb = new StringBuilder();
        try {
            String filesDir = context.getFilesDir().getCanonicalPath();
            sb.append("filesDir: ").append(filesDir).append("\n");
            String best = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 4 && filesDir.startsWith(parts[1]) && parts[1].length() > best.length()) {
                        best = parts[1] + " " + parts[0] + " " + parts[2] + " " + parts[3];
                    }
                }
            } catch (Exception e) {
                best = "unreadable: " + e.getMessage();
            }
            sb.append("mount: ").append(best.isEmpty() ? "none" : best).append("\n");
        } catch (Exception e) {
            sb.append("filesDir: error ").append(e.getMessage()).append("\n");
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("/sys/fs/selinux/enforce")))) {
            sb.append("selinux: enforcing=").append(br.readLine()).append("\n");
        } catch (Exception e) {
            sb.append("selinux: unreadable\n");
        }
        try {
            sb.append("sdk=").append(Build.VERSION.SDK_INT).append(" abi=").append(deviceAbi()).append("\n");
        } catch (Exception ignored) {
        }
        File bin = jpegtranBinary(context);
        sb.append("binary: ").append(describeFile(bin)).append("\n");
        try {
            Process ls = new ProcessBuilder("sh", "-c", "ls -Z '" + bin.getAbsolutePath().replace("'", "") + "' 2>&1").start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(ls.getInputStream()))) {
                char[] buf = new char[1024];
                int n;
                while ((n = br.read(buf)) != -1) out.append(buf, 0, n);
            }
            ls.waitFor();
            sb.append("context: ").append(out.toString().trim()).append("\n");
        } catch (Exception e) {
            sb.append("context: error ").append(e.getMessage()).append("\n");
        }
        try {
            String libDir = context.getApplicationInfo().nativeLibraryDir;
            sb.append("libDir: ").append(libDir).append("\n");
            Process ls = new ProcessBuilder("sh", "-c", "ls -Z '" + libDir.replace("'", "") + "' 2>&1 | head -5").start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(ls.getInputStream()))) {
                char[] buf = new char[1024];
                int n;
                while ((n = br.read(buf)) != -1) out.append(buf, 0, n);
            }
            ls.waitFor();
            sb.append(out.toString().trim()).append("\n");
        } catch (Exception e) {
            sb.append("libDir: error ").append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }

    public static void setCustomBaseUrl(Context context, String url) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString("native_tools_url", url == null ? "" : url.trim()).apply();
        } catch (Exception ignored) {
        }
    }
}
