package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.ShizukuProcessFactory;

/**
 * Streaming logcat reader, ported from LogFox
 * (https://github.com/f0x1d/logfox, GPL-3.0) - its
 * {@code feature/logging} module reads one long-lived
 * {@code logcat -v uid -v epoch} process and parses the same line format;
 * the parser, uid-to-package resolution and well-known uid table below are
 * that code rewritten in Java for this single-module build.
 *
 * <p>Three launch modes, matching LogFox's terminals:
 * <ul>
 *   <li>{@link Mode#ROOT} - {@code su -c logcat}, sees everything;</li>
 *   <li>{@link Mode#SHIZUKU} - a shell-uid process through the Shizuku
 *       binder, like {@code adb shell logcat};</li>
 *   <li>{@link Mode#OWN_APP} - a plain {@code logcat} process, which logd
 *       restricts to this app's own output.</li>
 * </ul>
 */
public final class LogcatReader {

    public enum Mode { ROOT, SHIZUKU, OWN_APP }

    /** Letters logcat prints for priorities; index maps to android.util.Log levels. */
    public static final String LEVEL_LETTERS = "VDIWEFS";

    /** One parsed log line. */
    public static final class LogLine {
        public final long id;
        public final long timeMs;
        public final String uid;
        public final String pid;
        public final String tid;
        public final String packageName;
        public final char level;
        public final String tag;
        public final String content;

        LogLine(long id, long timeMs, String uid, String pid, String tid,
                String packageName, char level, String tag, String content) {
            this.id = id;
            this.timeMs = timeMs;
            this.uid = uid;
            this.pid = pid;
            this.tid = tid;
            this.packageName = packageName;
            this.level = level;
            this.tag = tag;
            this.content = content;
        }

        public int levelIndex() {
            int i = LEVEL_LETTERS.indexOf(level);
            return i < 0 ? 0 : i;
        }
    }

    /** Receives parsed lines on the main thread. */
    public interface Listener {
        /** A batch of new lines, oldest first. */
        void onLines(List<LogLine> lines);

        /** The stream ended; {@code error} is null on a clean stop. */
        void onEof(String error);
    }

    // Format: time, uid, pid, tid, level, tag, message (LogFox's LOG_REGEX).
    private static final Pattern LOG_REGEX =
            Pattern.compile("(.{14}) (.{5,}?) (.{1,5}) (.{1,5}) (.) (.+?): (.+)");
    private static final Pattern MULTI_USER_UID_REGEX = Pattern.compile("u(.+?).*a(.+)");

    private static final int CACHE_SIZE = 200;
    private static final int USER_UID_MULTIPLIER = 100_000;
    private static final int APP_UID_OFFSET = 10_000;
    /** Flush cadence: post accumulated lines once this many are waiting. */
    private static final int BATCH_SIZE = 32;

    /**
     * Well-known Android UIDs, ported from LogFox's WellKnownUids.kt
     * (reference: https://stackoverflow.com/a/28057167). Only the names that
     * actually appear in logcat output are kept.
     */
    private static final Map<String, Integer> WELL_KNOWN_UIDS = new HashMap<>();

    static {
        WELL_KNOWN_UIDS.put("root", 0);
        WELL_KNOWN_UIDS.put("system", 1000);
        WELL_KNOWN_UIDS.put("radio", 1001);
        WELL_KNOWN_UIDS.put("bluetooth", 1002);
        WELL_KNOWN_UIDS.put("graphics", 1003);
        WELL_KNOWN_UIDS.put("input", 1004);
        WELL_KNOWN_UIDS.put("audio", 1005);
        WELL_KNOWN_UIDS.put("camera", 1006);
        WELL_KNOWN_UIDS.put("log", 1007);
        WELL_KNOWN_UIDS.put("compass", 1008);
        WELL_KNOWN_UIDS.put("mount", 1009);
        WELL_KNOWN_UIDS.put("wifi", 1010);
        WELL_KNOWN_UIDS.put("adb", 1011);
        WELL_KNOWN_UIDS.put("install", 1012);
        WELL_KNOWN_UIDS.put("media", 1013);
        WELL_KNOWN_UIDS.put("dhcp", 1014);
        WELL_KNOWN_UIDS.put("sdcard_rw", 1015);
        WELL_KNOWN_UIDS.put("vpn", 1016);
        WELL_KNOWN_UIDS.put("keystore", 1017);
        WELL_KNOWN_UIDS.put("usb", 1018);
        WELL_KNOWN_UIDS.put("drm", 1019);
        WELL_KNOWN_UIDS.put("mdnsr", 1020);
        WELL_KNOWN_UIDS.put("gps", 1021);
        WELL_KNOWN_UIDS.put("media_rw", 1023);
        WELL_KNOWN_UIDS.put("mtp", 1024);
        WELL_KNOWN_UIDS.put("nfc", 1027);
        WELL_KNOWN_UIDS.put("sdcard_r", 1028);
        WELL_KNOWN_UIDS.put("clat", 1029);
        WELL_KNOWN_UIDS.put("loop_radio", 1030);
        WELL_KNOWN_UIDS.put("mediadrm", 1031);
        WELL_KNOWN_UIDS.put("package_info", 1032);
        WELL_KNOWN_UIDS.put("sdcard_pics", 1033);
        WELL_KNOWN_UIDS.put("sdcard_av", 1034);
        WELL_KNOWN_UIDS.put("sdcard_all", 1035);
        WELL_KNOWN_UIDS.put("logd", 1036);
        WELL_KNOWN_UIDS.put("dbus", 1038);
        WELL_KNOWN_UIDS.put("tlsdate", 1039);
        WELL_KNOWN_UIDS.put("mediaex", 1040);
        WELL_KNOWN_UIDS.put("audioserver", 1041);
        WELL_KNOWN_UIDS.put("metrics_coll", 1042);
        WELL_KNOWN_UIDS.put("metricsd", 1043);
        WELL_KNOWN_UIDS.put("webserv", 1044);
        WELL_KNOWN_UIDS.put("debuggerd", 1045);
        WELL_KNOWN_UIDS.put("mediacodec", 1046);
        WELL_KNOWN_UIDS.put("cameraserver", 1047);
        WELL_KNOWN_UIDS.put("firewall", 1048);
        WELL_KNOWN_UIDS.put("trunks", 1049);
        WELL_KNOWN_UIDS.put("nvram", 1050);
        WELL_KNOWN_UIDS.put("dns", 1051);
        WELL_KNOWN_UIDS.put("dns_tether", 1052);
        WELL_KNOWN_UIDS.put("webview_zygote", 1053);
        WELL_KNOWN_UIDS.put("vehicle_network", 1054);
        WELL_KNOWN_UIDS.put("media_audio", 1055);
        WELL_KNOWN_UIDS.put("media_video", 1056);
        WELL_KNOWN_UIDS.put("media_image", 1057);
        WELL_KNOWN_UIDS.put("shell", 2000);
        WELL_KNOWN_UIDS.put("cache", 2001);
        WELL_KNOWN_UIDS.put("diag", 2002);
        WELL_KNOWN_UIDS.put("everybody", 9997);
        WELL_KNOWN_UIDS.put("misc", 9998);
        WELL_KNOWN_UIDS.put("nobody", 9999);
    }

    private final Context appContext;
    private final Mode mode;
    private final LruCache<String, String> uidsCache = new LruCache<>(CACHE_SIZE);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Process process;
    private Thread pumpThread;
    private long nextId;

    public LogcatReader(Context context, Mode mode) {
        this.appContext = context.getApplicationContext();
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    /**
     * Starts the logcat process. History arrives first (logcat prints the
     * current buffer before following new entries), so no separate dump pass
     * is needed.
     */
    public synchronized void start(Listener listener) throws IOException {
        if (pumpThread != null) return;
        Process proc;
        try {
            switch (mode) {
                case ROOT:
                    proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "logcat -v uid -v epoch"});
                    break;
                case SHIZUKU:
                    proc = new ShizukuProcessFactory()
                            .newProcess(new String[]{"logcat", "-v", "uid", "-v", "epoch"}, null, "/");
                    break;
                default:
                    proc = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "uid", "-v", "epoch"});
            }
        } catch (Throwable t) {
            throw new IOException(t.getMessage() == null ? "failed to start logcat" : t.getMessage(), t);
        }
        process = proc;
        final BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()), 64 * 1024);
        pumpThread = new Thread(() -> pump(reader, listener), "logcat-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();
    }

    public synchronized void stop() {
        Thread t = pumpThread;
        pumpThread = null;
        if (t != null) t.interrupt();
        Process proc = process;
        process = null;
        if (proc != null) {
            try {
                proc.destroy();
            } catch (Throwable ignored) {
            }
        }
    }

    private void pump(BufferedReader reader, Listener listener) {
        List<LogLine> batch = new ArrayList<>(BATCH_SIZE);
        String error = null;
        try {
            String line;
            while (pumpThread != null && (line = reader.readLine()) != null) {
                LogLine parsed = parse(line);
                if (parsed != null) batch.add(parsed);
                if (batch.size() >= BATCH_SIZE) {
                    // Hard cap so a device that never stops logging cannot
                    // grow the batch without bound between quiet moments.
                    flush(batch, listener);
                    continue;
                }
                // Otherwise flush when the stream is drained, so fast bursts
                // go out as one batch and slow streams still land promptly.
                if (reader.ready()) continue;
                flush(batch, listener);
            }
        } catch (Exception e) {
            if (pumpThread != null) error = e.getMessage();
        }
        flush(batch, listener);
        final String finalError = error;
        if (pumpThread != null) {
            mainHandler.post(() -> {
                if (pumpThread != null) listener.onEof(finalError);
            });
        }
    }

    private void flush(List<LogLine> batch, Listener listener) {
        if (batch.isEmpty()) return;
        final List<LogLine> out = new ArrayList<>(batch);
        batch.clear();
        mainHandler.post(() -> listener.onLines(out));
    }

    /** Parses one {@code logcat -v uid -v epoch} line; null for header/noise lines. */
    private LogLine parse(String raw) {
        String line = raw.trim();
        Matcher m = LOG_REGEX.matcher(line);
        if (!m.matches()) return null;
        try {
            String uid = m.group(2).trim();
            long timeMs = parseTime(m.group(1));
            char level = m.group(5).charAt(0);
            if (LEVEL_LETTERS.indexOf(level) < 0) return null;
            return new LogLine(nextId++, timeMs, uid, m.group(3).trim(), m.group(4).trim(),
                    resolvePackageName(uid), level, m.group(6).trim(), m.group(7));
        } catch (Exception e) {
            return null;
        }
    }

    /** {@code 1698777600.123} epoch seconds to millis (LogFox's parseTime). */
    private static long parseTime(String timeString) {
        String cleanTime = timeString.trim();
        int dotIndex = cleanTime.indexOf('.');
        if (dotIndex < 0) return Long.parseLong(cleanTime) * 1000;
        long seconds = Long.parseLong(cleanTime.substring(0, dotIndex));
        long millis = dotIndex + 1 < cleanTime.length()
                ? Long.parseLong(cleanTime.substring(dotIndex + 1)) : 0;
        return seconds * 1000 + millis;
    }

    private String resolvePackageName(String uid) {
        String cached = uidsCache.get(uid);
        if (cached != null) return cached;
        Integer integerUid = resolveIntegerUid(uid);
        if (integerUid == null) return null;
        try {
            String[] packages = appContext.getPackageManager().getPackagesForUid(integerUid);
            if (packages != null && packages.length > 0) {
                uidsCache.put(uid, packages[0]);
                return packages[0];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Plain uid, well-known name, or {@code u<userId>a<appId>} multi-user form. */
    private static Integer resolveIntegerUid(String uid) {
        try {
            return Integer.parseInt(uid);
        } catch (NumberFormatException ignored) {
        }
        Integer wellKnown = WELL_KNOWN_UIDS.get(uid);
        if (wellKnown != null) return wellKnown;
        Matcher m = MULTI_USER_UID_REGEX.matcher(uid);
        if (m.matches()) {
            try {
                int userId = Integer.parseInt(m.group(1));
                int appId = Integer.parseInt(m.group(2));
                return USER_UID_MULTIPLIER * userId + APP_UID_OFFSET + appId;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
