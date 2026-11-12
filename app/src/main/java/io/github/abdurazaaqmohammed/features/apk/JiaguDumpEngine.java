package io.github.abdurazaaqmohammed.features.apk;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.text.TextUtils;

import com.reandroid.apk.APKLogger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * Core of the root Jiagu Dumper: launch a protected app, let its stub unpack the
 * real dexes into memory, then carve every valid dex out of /proc/<pid>/mem
 * through su. Shared by the Tools Hub dialog and the MCP tool.
 */
public final class JiaguDumpEngine {

    private static final long MAX_REGION = 2L * 1024 * 1024 * 1024;
    private static final long MAX_DEX = 200L * 1024 * 1024;
    private static final int CHUNK = 4 * 1024 * 1024;
    private static final Pattern MAPS_LINE = Pattern.compile(
            "([0-9a-f]+)-([0-9a-f]+) (\\S+) (\\S+) (\\S+) (\\S+)\\s*(.*)");
    private static final int[] DEX_VERSIONS = {'5', '6', '7', '8', '9'};

    private JiaguDumpEngine() {}

    /** Progress sink: line-based so both the dialog log view and MCP share it. */
    public interface LogSink {
        void log(String line);
    }

    /** Output root: app-external files/jiagu_dump/<package>/<timestamp>. */
    public static File dumpRoot(Context context, String pkg) {
        File base = new File(context.getExternalFilesDir(null), "jiagu_dump/" + pkg);
        return new File(base, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
    }

    /** Newest dump folder of a package that actually contains dexes. */
    public static File latestDump(Context context, String pkg) {
        File base = new File(context.getExternalFilesDir(null), "jiagu_dump/" + pkg);
        File[] runs = base.listFiles(File::isDirectory);
        if (runs == null || runs.length == 0) return null;
        File best = null;
        for (File f : runs) {
            boolean hasDex = false;
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (c.getName().endsWith(".dex")) { hasDex = true; break; }
                }
            }
            if (!hasDex) continue;
            if (best == null || f.getName().compareTo(best.getName()) > 0) best = f;
        }
        return best;
    }

    /** Installed non-system packages, for the package picker. */
    public static List<String> installedPackages(Context context) {
        List<String> out = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                out.add(info.packageName);
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * Full dump: launch, wait for unpacking, scan memory, carve dexes.
     *
     * @return the dump folder holding dump_NNN.dex files + report.txt
     */
    public static File dump(Context context, String pkg, int waitSec, LogSink sink) throws Exception {
        sink.log("== Jiagu dump: " + pkg);
        RootManager rm = RootManager.getInstance(context);

        sink.log("Launching " + pkg + " (waiting up to " + waitSec + "s)...");
        rm.execute("monkey -p " + pkg + " 1", 30);
        long pid = 0;
        long deadline = SystemClock.elapsedRealtime() + waitSec * 1000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            RootManager.ShellResult res = rm.execute("pidof " + pkg, 15);
            String p = res.output() == null ? "" : res.output().trim();
            if (!p.isEmpty() && p.matches("\\d+.*")) {
                pid = Long.parseLong(p.split("\\s+")[0]);
                break;
            }
            SystemClock.sleep(1000);
        }
        if (pid <= 0) throw new IOException("Process not running (pidof " + pkg + " failed)");
        sink.log("pid=" + pid);

        RootManager.ShellResult mapsRes = rm.execute("cat /proc/" + pid + "/maps", 30);
        if (mapsRes.output() == null || mapsRes.output().isEmpty()) {
            throw new IOException("Cannot read /proc/" + pid + "/maps: " + mapsRes.error());
        }
        List<Object[]> candidates = new ArrayList<>(); // {start, end, pgoff, path}
        for (String line : mapsRes.output().split("\n")) {
            Matcher m = MAPS_LINE.matcher(line.trim());
            if (!m.find()) continue;
            if (m.group(1).length() > 16 || m.group(2).length() > 16) continue;
            long start;
            long end;
            long pgoff;
            try {
                start = Long.parseLong(m.group(1), 16);
                end = Long.parseLong(m.group(2), 16);
                pgoff = Long.parseLong(m.group(4), 16);
            } catch (NumberFormatException badAddr) {
                continue;
            }
            String perms = m.group(3);
            String name = m.group(7) == null ? "" : m.group(7);
            if (!perms.contains("r")) continue;
            long size = end - start;
            if (size <= 0 || size > MAX_REGION) continue;
            String lower = name.toLowerCase(Locale.US);
            boolean named = lower.contains("dalvik") || lower.contains("dex")
                    || lower.contains("app_cache") || lower.contains("jit") || lower.endsWith(".vdex");
            boolean anon = name.isEmpty() || lower.startsWith("[anon");
            if (named || anon) candidates.add(new Object[]{start, end, pgoff, name});
        }
        sink.log("Candidate regions: " + candidates.size());

        File outDir = dumpRoot(context, pkg);
        if (!outDir.isDirectory() && !outDir.mkdirs()) throw new IOException("Cannot create " + outDir);

        List<String> lines = new ArrayList<>();
        long scanStart = SystemClock.elapsedRealtime();
        for (Object[] region : candidates) {
            scanRegion(pid, (Long) region[0], (Long) region[1], (Long) region[2], (String) region[3],
                    outDir, lines, sink, rm.suBinary());
        }
        sink.log("Scan took " + (SystemClock.elapsedRealtime() - scanStart) / 1000 + "s, "
                + lines.size() + " dexes");
        if (lines.isEmpty()) {
            sink.log("No dexes found - open the app fully (not just splash) and retry, or increase the wait.");
        }
        writeReport(outDir, lines);
        sink.log("Dump folder: " + outDir.getAbsolutePath());
        return outDir;
    }

    private static void writeReport(File outDir, List<String> dexLines) {
        try (OutputStream os = new FileOutputStream(new File(outDir, "report.txt"))) {
            StringBuilder sb = new StringBuilder();
            for (String l : dexLines) sb.append(l).append('\n');
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    /** Stream one region through su dd in chunks and carve every dex header found. */
    private static void scanRegion(long pid, long start, long end, long pgoff, String path,
                                   File outDir, List<String> lines, LogSink sink, String suBinary) throws Exception {
        long size = end - start;
        byte[] window = new byte[4 + CHUNK];
        byte[] carry = new byte[4];
        long pos = 0;
        List<long[]> hits = new ArrayList<>();
        while (pos < size) {
            long read = Math.min(CHUNK, size - pos);
            int got = readMem(pid, start + pos, read, window, 4, suBinary);
            if (got <= 0) break;
            int scanLen = got;
            int scanFrom = 4; // window[0..4) is garbage on the very first chunk
            if (pos > 0) {
                System.arraycopy(carry, 0, window, 0, 4);
                scanLen = got + 4;
                scanFrom = 0;
            }
            for (int i = scanFrom; i + 8 <= scanLen; i += 4) {
                if (window[i] == 'd' && window[i + 1] == 'e' && window[i + 2] == 'x' && window[i + 3] == '\n'
                        && window[i + 4] == '0' && window[i + 5] == '3' && window[i + 7] == 0) {
                    char v = (char) window[i + 6];
                    boolean ok = false;
                    for (int d : DEX_VERSIONS) if (v == d) { ok = true; break; }
                    if (!ok) continue;
                    long abs = start + pos - 4 + i;
                    hits.add(new long[]{abs, v});
                }
            }
            if (scanLen >= 4) {
                System.arraycopy(window, scanLen - 4, carry, 0, 4);
            }
            pos += got;
            if (got < read) break;
        }
        if (hits.isEmpty()) return;
        sink.log("Region " + Long.toHexString(start) + "-" + Long.toHexString(end)
                + ": " + hits.size() + " dex header(s)");
        int index = lines.size();
        for (long[] hit : hits) {
            long abs = hit[0];
            byte[] header = new byte[112];
            int got = readMem(pid, abs, 112, header, 0, suBinary);
            boolean fromFile = false;
            if (got < 112 && path != null && !path.isEmpty()) {
                // /proc/pid/mem can fail (I/O error) on file-backed pages - read the file instead.
                long fileOff = pgoff + (abs - start);
                got = readFileRange(path, fileOff, 112, header, 0, suBinary);
                fromFile = got >= 112;
            }
            if (got < 112) {
                sink.log("Header read failed @0x" + Long.toHexString(abs) + (path != null && !path.isEmpty() ? " (file fallback used)" : ""));
                continue;
            }
            if (!JiaguUnpackEngine.isDexHeader(header)) continue;
            long fileSize = (header[32] & 0xFFL) | ((header[33] & 0xFFL) << 8)
                    | ((header[34] & 0xFFL) << 16) | ((header[35] & 0xFFL) << 24);
            // DEXes can span adjacent memory mappings - validate the carved file
            // (dexlib parse at unpack time) instead of requiring the whole dex
            // to fit inside this one region.
            if (fileSize < 112 || fileSize > MAX_DEX) continue;
            File out = FileUtils.getUnusedFile(new File(outDir, String.format(Locale.US, "dump_%03d.dex", index++)));
            long copied = carve(pid, abs, fileSize, out, suBinary);
            if (copied != fileSize && path != null && !path.isEmpty() && !fromFile) {
                long fileOff = pgoff + (abs - start);
                copied = carveFile(path, fileOff, fileSize, out, suBinary);
                fromFile = true;
            }
            if (copied != fileSize) {
                sink.log("Truncated dex @ " + Long.toHexString(abs) + " (" + copied + "/" + fileSize + ") - skipped");
                out.delete();
                continue;
            }
            lines.add(out.getName() + " size=" + fileSize + " @0x" + Long.toHexString(abs)
                    + (fromFile ? " (file)" : "") + " ver=03" + (char) hit[1]);
            sink.log("Carved " + out.getName() + " (" + fileSize + " bytes)");
        }
    }

    private static String[] suCmd(String suBinary, String body) {
        return new String[]{suBinary, "-c", body};
    }

    private static int readMem(long pid, long offset, long count, byte[] dst, int dstOffset, String suBinary) throws Exception {
        Process process = Runtime.getRuntime().exec(suCmd(suBinary,
                "dd if=/proc/" + pid + "/mem skip=" + offset + " count=" + count
                        + " iflag=skip_bytes,count_bytes 2>/dev/null"));
        try (InputStream in = new BufferedInputStream(process.getInputStream(), 1024 * 1024)) {
            int total = 0;
            int n;
            while (total < count && (n = in.read(dst, dstOffset + total, (int) Math.min(dst.length - dstOffset - total, count - total))) != -1) {
                total += n;
            }
            return total;
        } finally {
            process.destroy();
        }
    }

    private static long carve(long pid, long offset, long size, File out, String suBinary) throws Exception {
        Process process = Runtime.getRuntime().exec(suCmd(suBinary,
                "dd if=/proc/" + pid + "/mem skip=" + offset + " count=" + size
                        + " iflag=skip_bytes,count_bytes 2>/dev/null"));
        Thread drain = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                while (err.read(buf) != -1) { /* discard */ }
            } catch (IOException ignored) {}
        });
        drain.start();
        long total = 0;
        try (InputStream in = new BufferedInputStream(process.getInputStream(), 1024 * 1024);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while (total < size && (n = in.read(buf, 0, (int) Math.min(buf.length, size - total))) != -1) {
                os.write(buf, 0, n);
                total += n;
            }
        } finally {
            process.destroy();
            try { drain.join(2000); } catch (InterruptedException ignored) {}
        }
        return total;
    }

    /** Rebuild the unpacked APK for an original + dump folder. Returns the output APK. */
    public static File rebuild(Context context, File apk, File dumpDir, LogSink sink) throws Exception {
        return rebuild(context, apk, dumpDir, sink, null);
    }

    /** @param outDir where to write the rebuilt APK (null: next to the original). */
    public static File rebuild(Context context, File apk, File dumpDir, LogSink sink, File outDir) throws Exception {
        List<File> dexes = new ArrayList<>();
        File[] children = dumpDir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isFile() && c.getName().endsWith(".dex")) dexes.add(c);
            }
        }
        if (dexes.isEmpty()) throw new IOException("Dump folder has no .dex files: " + dumpDir);
        JiaguUnpackEngine.Result result = JiaguUnpackEngine.unpack(context, apk, dexes,
                new APKLogger() {
                    @Override public void logMessage(String s) { sink.log(String.valueOf(s)); }
                    @Override public void logError(String s, Throwable t) {
                        sink.log("ERROR: " + s + (t != null ? " " + t.getMessage() : ""));
                    }
                    @Override public void logVerbose(String s) { sink.log(String.valueOf(s)); }
                }, outDir);
        return result.apk;
    }

    /** True when the path looks like a package name. */
    public static boolean isValidPackage(String pkg) {
        return !TextUtils.isEmpty(pkg) && pkg.matches("[\\w.]+");
    }

    /** Read bytes from a file on the device through su (fallback for file-backed memory). */
    private static int readFileRange(String path, long offset, long count, byte[] dst, int dstOffset, String suBinary) throws Exception {
        Process process = Runtime.getRuntime().exec(suCmd(suBinary,
                "dd if=" + escapePath(path) + " skip=" + offset + " count=" + count
                        + " iflag=skip_bytes,count_bytes 2>/dev/null"));
        try (InputStream in = new BufferedInputStream(process.getInputStream(), 1024 * 1024)) {
            int total = 0;
            int n;
            while (total < count && (n = in.read(dst, dstOffset + total, (int) Math.min(dst.length - dstOffset - total, count - total))) != -1) {
                total += n;
            }
            return total;
        } finally {
            process.destroy();
        }
    }

    private static long carveFile(String path, long offset, long size, File out, String suBinary) throws Exception {
        Process process = Runtime.getRuntime().exec(suCmd(suBinary,
                "dd if=" + escapePath(path) + " skip=" + offset + " count=" + size
                        + " iflag=skip_bytes,count_bytes 2>/dev/null"));
        Thread drain = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                while (err.read(buf) != -1) { /* discard */ }
            } catch (IOException ignored) {}
        });
        drain.start();
        long total = 0;
        try (InputStream in = new BufferedInputStream(process.getInputStream(), 1024 * 1024);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while (total < size && (n = in.read(buf, 0, (int) Math.min(buf.length, size - total))) != -1) {
                os.write(buf, 0, n);
                total += n;
            }
        } finally {
            process.destroy();
            try { drain.join(2000); } catch (InterruptedException ignored) {}
        }
        return total;
    }

    private static String escapePath(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
