package io.github.abdurazaaqmohammed.packs.device;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Static system readers backing the Device Hub tabs. Everything is guarded:
 * unreadable nodes, missing APIs, and denied permissions yield "—" instead
 * of crashes. Root-only helpers shell out to su directly so the pack needs
 * no host-only dependencies.
 */
public final class DeviceSys {

    public static final String UNKNOWN = "—";

    private DeviceSys() {
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return new DecimalFormat("0.0").format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return new DecimalFormat("0.0").format(mb) + " MB";
        double gb = mb / 1024.0;
        if (gb < 1024) return new DecimalFormat("0.00").format(gb) + " GB";
        return new DecimalFormat("0.00").format(gb / 1024.0) + " TB";
    }

    public static String readFileFirstLine(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
            String line = r.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception ignored) {
            return "";
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static String readFile(String path) {
        StringBuilder b = new StringBuilder();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        } catch (Exception ignored) {
            return "";
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
        return b.toString();
    }

    // ---------- shell ----------

    public static class ShellResult {
        public final String out;
        public final boolean ok;

        ShellResult(String out, boolean ok) {
            this.out = out;
            this.ok = ok;
        }
    }

    public static ShellResult shell(String[] cmd, long timeoutMs) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            final Process proc = p;
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(timeoutMs);
                    proc.destroy();
                } catch (Exception ignored) {
                }
            });
            killer.setDaemon(true);
            killer.start();
            Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A");
            String out = s.hasNext() ? s.next() : "";
            int rc = p.waitFor();
            return new ShellResult(out.trim(), rc == 0);
        } catch (Exception e) {
            return new ShellResult("", false);
        } finally {
            try {
                if (p != null) p.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    public static ShellResult shell(String cmd, long timeoutMs) {
        return shell(new String[]{"sh", "-c", cmd}, timeoutMs);
    }

    public static ShellResult su(String cmd, long timeoutMs) {
        return shell(new String[]{"su", "-c", cmd}, timeoutMs);
    }

    // ---------- root ----------

    public static boolean suBinaryPresent() {
        ShellResult r = shell("which su", 3000);
        return r.ok && !r.out.isEmpty();
    }

    public static boolean rootGranted() {
        ShellResult r = su("id -u", 5000);
        return r.ok && "0".equals(r.out.trim());
    }

    public static String magiskVersion() {
        ShellResult r = su("magisk -v", 5000);
        if (r.ok && !r.out.isEmpty()) return r.out.split("\n")[0].trim();
        if (new File("/sbin/.magisk").exists() || new File("/dev/.magisk").exists()) {
            return "present (version hidden)";
        }
        return "";
    }

    public static String selinuxStatus() {
        String v = readFileFirstLine("/sys/fs/selinux/enforce");
        if ("1".equals(v)) return "Enforcing";
        if ("0".equals(v)) return "Permissive";
        ShellResult r = shell("getenforce", 3000);
        if (r.ok && !r.out.isEmpty()) return r.out.split("\n")[0].trim();
        return UNKNOWN;
    }

    // ---------- getprop ----------

    public static Map<String, String> getprop() {
        Map<String, String> map = new LinkedHashMap<>();
        ShellResult r = shell("getprop", 8000);
        if (!r.ok) return map;
        for (String line : r.out.split("\n")) {
            line = line.trim();
            if (!line.startsWith("[") || !line.contains("]:")) continue;
            int mid = line.indexOf("]:");
            String key = line.substring(1, mid);
            String val = line.substring(mid + 2).trim();
            if (val.startsWith("[") && val.endsWith("]") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return map;
    }

    public static String getprop(String key) {
        ShellResult r = shell("getprop " + key, 3000);
        return r.ok ? r.out : "";
    }

    // ---------- cpu ----------

    public static int cpuCount() {
        try {
            return Runtime.getRuntime().availableProcessors();
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static boolean cpuOnline(int core) {
        if (core == 0) return true;
        String v = readFileFirstLine("/sys/devices/system/cpu/cpu" + core + "/online");
        return !"0".equals(v);
    }

    /** Current freq in MHz, -1 when unreadable. */
    public static long cpuCurMhz(int core) {
        String v = readFileFirstLine("/sys/devices/system/cpu/cpu" + core + "/cpufreq/scaling_cur_freq");
        if (v.isEmpty()) {
            v = readFileFirstLine("/sys/devices/system/cpu/cpu" + core + "/cpufreq/cpuinfo_cur_freq");
        }
        try {
            return Long.parseLong(v) / 1000;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static long cpuMaxMhz(int core) {
        String v = readFileFirstLine("/sys/devices/system/cpu/cpu" + core + "/cpufreq/cpuinfo_max_freq");
        try {
            return Long.parseLong(v) / 1000;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static String cpuGovernor(int core) {
        String v = readFileFirstLine("/sys/devices/system/cpu/cpu" + core + "/cpufreq/scaling_governor");
        return v.isEmpty() ? UNKNOWN : v;
    }

    public static Map<String, String> cpuInfo() {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = readFile("/proc/cpuinfo");
        if (raw.isEmpty()) return map;
        String impl = "", part = "", hard = "", model = "", feats = "";
        for (String line : raw.split("\n")) {
            int c = line.indexOf(':');
            if (c < 0) continue;
            String k = line.substring(0, c).trim().toLowerCase(Locale.US);
            String v = line.substring(c + 1).trim();
            if (k.equals("hardware") && hard.isEmpty()) hard = v;
            else if (k.equals("processor") && model.isEmpty() && !v.matches("\\d+")) model = v;
            else if (k.equals("model name") && model.isEmpty()) model = v;
            else if (k.equals("cpu implementer") && impl.isEmpty()) impl = v;
            else if (k.equals("cpu part") && part.isEmpty()) part = v;
            else if ((k.equals("features") || k.equals("flags")) && feats.isEmpty()) feats = v;
        }
        if (!hard.isEmpty()) map.put("Hardware", hard);
        if (!model.isEmpty()) map.put("Model", model);
        if (!impl.isEmpty()) map.put("Implementer", impl);
        if (!part.isEmpty()) map.put("Part", part);
        if (!feats.isEmpty()) map.put("Features", feats);
        return map;
    }

    public static class CpuLoad {
        public final float total;
        public final float[] cores;

        CpuLoad(float total, float[] cores) {
            this.total = total;
            this.cores = cores;
        }
    }

    private static long[] statLine(String line) {
        String[] p = line.trim().split("\\s+");
        long[] v = new long[]{0, 0};
        try {
            long idle = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0);
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);
            v[0] = idle;
            v[1] = total;
        } catch (Exception ignored) {
        }
        return v;
    }

    /** Two samples ~300ms apart; blocks the calling (background) thread. */
    public static CpuLoad sampleCpuLoad(int cores) {
        try {
            Map<String, long[]> a = readStat();
            Thread.sleep(300);
            Map<String, long[]> b = readStat();
            float[] per = new float[cores];
            long ai = 0, at = 0, bi = 0, bt = 0;
            for (int i = 0; i < cores; i++) {
                long[] x = a.get("cpu" + i);
                long[] y = b.get("cpu" + i);
                if (x == null || y == null) {
                    per[i] = -1;
                    continue;
                }
                long di = y[0] - x[0];
                long dt = y[1] - x[1];
                per[i] = dt <= 0 ? 0 : Math.max(0f, Math.min(100f, 100f * (1f - (float) di / dt)));
            }
            long[] xa = a.get("cpu");
            long[] ya = b.get("cpu");
            float total = 0;
            if (xa != null && ya != null) {
                long di = ya[0] - xa[0];
                long dt = ya[1] - xa[1];
                if (dt > 0) total = Math.max(0f, Math.min(100f, 100f * (1f - (float) di / dt)));
                ai = xa[0];
                at = xa[1];
                bi = ya[0];
                bt = ya[1];
            }
            if (ai == bi && at == bt) total = 0;
            return new CpuLoad(total, per);
        } catch (Exception ignored) {
            return new CpuLoad(0, new float[cores]);
        }
    }

    private static Map<String, long[]> readStat() {
        Map<String, long[]> map = new LinkedHashMap<>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("cpu")) {
                    String name = line.split("\\s+")[0];
                    map.put(name, statLine(line));
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    // ---------- memory ----------

    public static class MemInfo {
        public long total;
        public long avail;
        public boolean low;
        public final Map<String, Long> proc = new LinkedHashMap<>();
    }

    public static MemInfo memory(Context context) {
        MemInfo mi = new MemInfo();
        try {
            ActivityManager am =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            mi.total = info.totalMem;
            mi.avail = info.availMem;
            mi.low = info.lowMemory;
        } catch (Exception ignored) {
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = r.readLine()) != null) {
                int c = line.indexOf(':');
                if (c < 0) continue;
                String k = line.substring(0, c).trim();
                if (k.equals("MemCached") || k.equals("Cached") || k.equals("SwapTotal")
                        || k.equals("SwapFree") || k.equals("Shmem") || k.equals("Slab")
                        || k.equals("Active") || k.equals("Inactive") || k.equals("Buffers")) {
                    String num = line.substring(c + 1).trim().split("\\s+")[0];
                    mi.proc.put(k, Long.parseLong(num) * 1024);
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
        return mi;
    }

    // ---------- storage ----------

    public static class Volume {
        public String label;
        public String path;
        public long total;
        public long free;
        public boolean removable;
    }

    public static List<Volume> volumes(Context context) {
        List<Volume> out = new ArrayList<>();
        try {
            File internal = Environment.getDataDirectory();
            Volume v = statVolume("Internal storage", internal.getAbsolutePath(), false);
            if (v != null) out.add(v);
        } catch (Exception ignored) {
        }
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                StorageManager sm =
                        (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (StorageVolume sv : sm.getStorageVolumes()) {
                        try {
                            File dir = sv.getDirectory();
                            if (dir == null) continue;
                            String p = dir.getAbsolutePath();
                            boolean dup = false;
                            for (Volume e : out) {
                                if (e.path.equals(p)) {
                                    dup = true;
                                    break;
                                }
                            }
                            if (dup) continue;
                            String label = sv.getDescription(context);
                            if (label == null || label.isEmpty()) label = p;
                            Volume vol = statVolume(label, p, sv.isRemovable());
                            if (vol != null) out.add(vol);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else {
                File ext = Environment.getExternalStorageDirectory();
                if (ext != null) {
                    Volume vol = statVolume("Shared storage", ext.getAbsolutePath(),
                            Environment.isExternalStorageRemovable());
                    if (vol != null) out.add(vol);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static Volume statVolume(String label, String path, boolean removable) {
        try {
            StatFs st = new StatFs(path);
            long bs = st.getBlockSizeLong();
            Volume v = new Volume();
            v.label = label;
            v.path = path;
            v.total = st.getBlockCountLong() * bs;
            v.free = st.getAvailableBlocksLong() * bs;
            v.removable = removable;
            return v;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String fsType(String path) {
        String mounts = readFile("/proc/mounts");
        if (mounts.isEmpty()) return UNKNOWN;
        String best = "";
        String bestType = UNKNOWN;
        for (String line : mounts.split("\n")) {
            String[] p = line.split("\\s+");
            if (p.length < 3) continue;
            String mp = p[1];
            if (path.startsWith(mp) && mp.length() > best.length()) {
                best = mp;
                bestType = p[2];
            }
        }
        return bestType;
    }

    // ---------- battery ----------

    public static class Battery {
        public int pct = -1;
        public String status = UNKNOWN;
        public String plugged = UNKNOWN;
        public float tempC = Float.NaN;
        public int mv = -1;
        public String tech = UNKNOWN;
        public int health = -1;
        public long currentUa = Long.MIN_VALUE;
        public long chargeCounterUah = Long.MIN_VALUE;
        public long fullUah = Long.MIN_VALUE;
        public long designUah = Long.MIN_VALUE;
        public String cycles = UNKNOWN;
    }

    public static Battery battery(Context context) {
        Battery b = new Battery();
        try {
            Intent bat = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0) b.pct = scale <= 0 ? level : Math.round(level * 100f / scale);
                int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                b.status = status == BatteryManager.BATTERY_STATUS_CHARGING ? "Charging"
                        : status == BatteryManager.BATTERY_STATUS_FULL ? "Full"
                        : status == BatteryManager.BATTERY_STATUS_DISCHARGING ? "Discharging"
                        : status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ? "Not charging"
                        : status == BatteryManager.BATTERY_STATUS_UNKNOWN ? "Unknown" : UNKNOWN;
                int plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                b.plugged = plugged == BatteryManager.BATTERY_PLUGGED_AC ? "AC"
                        : plugged == BatteryManager.BATTERY_PLUGGED_USB ? "USB"
                        : plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? "Wireless"
                        : plugged == 0 ? "On battery" : ("Source " + plugged);
                int temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (temp != Integer.MIN_VALUE) b.tempC = temp / 10f;
                b.mv = bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                String tech = bat.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                if (tech != null && !tech.isEmpty()) b.tech = tech;
                b.health = bat.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            }
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                try {
                    b.currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                } catch (Exception ignored) {
                }
                try {
                    b.chargeCounterUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                } catch (Exception ignored) {
                }
            }
            b.fullUah = sysfsLong("/sys/class/power_supply/battery/charge_full");
            b.designUah = sysfsLong("/sys/class/power_supply/battery/charge_full_design");
            String cyc = readFileFirstLine("/sys/class/power_supply/battery/cycle_count");
            if (!cyc.isEmpty()) b.cycles = cyc;
        } catch (Exception ignored) {
        }
        return b;
    }

    private static long sysfsLong(String path) {
        try {
            return Long.parseLong(readFileFirstLine(path));
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    public static String batteryHealth(int h) {
        if (h == BatteryManager.BATTERY_HEALTH_GOOD) return "Good";
        if (h == BatteryManager.BATTERY_HEALTH_OVERHEAT) return "Overheat";
        if (h == BatteryManager.BATTERY_HEALTH_DEAD) return "Dead";
        if (h == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) return "Over voltage";
        if (h == BatteryManager.BATTERY_HEALTH_COLD) return "Cold";
        if (h == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE) return "Failure";
        return UNKNOWN;
    }

    // ---------- thermal ----------

    public static class Zone {
        public String type;
        public float tempC;
    }

    public static List<Zone> thermalZones() {
        List<Zone> out = new ArrayList<>();
        try {
            File dir = new File("/sys/class/thermal");
            File[] zones = dir.listFiles();
            if (zones == null) return out;
            for (File z : zones) {
                try {
                    if (!z.getName().startsWith("thermal_zone")) continue;
                    String type = readFileFirstLine(z.getAbsolutePath() + "/type");
                    String temp = readFileFirstLine(z.getAbsolutePath() + "/temp");
                    if (temp.isEmpty()) continue;
                    Zone zone = new Zone();
                    zone.type = type.isEmpty() ? z.getName() : type;
                    zone.tempC = Long.parseLong(temp) / 1000f;
                    out.add(zone);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------- display ----------

    public static float refreshRate(Display display) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Object r = display.getClass().getMethod("getRefreshRate").invoke(display);
                if (r instanceof Float) return (Float) r;
            }
        } catch (Exception ignored) {
        }
        return Float.NaN;
    }

    public static String displayModes(Display display) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Object modes = display.getClass().getMethod("getSupportedModes").invoke(display);
                if (modes instanceof Object[]) {
                    Object[] arr = (Object[]) modes;
                    StringBuilder b = new StringBuilder();
                    for (Object m : arr) {
                        try {
                            int w = (Integer) m.getClass().getMethod("getPhysicalWidth").invoke(m);
                            int h = (Integer) m.getClass().getMethod("getPhysicalHeight").invoke(m);
                            float r = (Float) m.getClass().getMethod("getRefreshRate").invoke(m);
                            if (b.length() > 0) b.append(", ");
                            b.append(w).append("×").append(h).append(" @ ")
                                    .append(new DecimalFormat("0.#").format(r)).append("Hz");
                        } catch (Exception ignored) {
                        }
                    }
                    return b.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String hdrCaps(Display display) {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                Object caps = display.getClass().getMethod("getHdrCapabilities").invoke(display);
                if (caps == null) return "No HDR";
                int[] types = (int[]) caps.getClass().getMethod("getSupportedHdrTypes").invoke(caps);
                if (types == null || types.length == 0) return "No HDR";
                StringBuilder b = new StringBuilder();
                for (int t : types) {
                    if (b.length() > 0) b.append(", ");
                    if (t == 1) b.append("Dolby Vision");
                    else if (t == 2) b.append("HDR10");
                    else if (t == 3) b.append("HLG");
                    else if (t == 4) b.append("HDR10+");
                    else b.append("Type" + t);
                }
                return b.toString();
            }
        } catch (Exception ignored) {
        }
        return UNKNOWN;
    }

    // ---------- sensors / cameras / codecs ----------

    public static List<Sensor> allSensors(Context context) {
        try {
            SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sm == null) return new ArrayList<>();
            return new ArrayList<>(sm.getSensorList(Sensor.TYPE_ALL));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static class CameraInfo {
        public String id;
        public String facing;
        public String megapixels;
        public String flash;
        public String level;
    }

    public static List<CameraInfo> cameras(Context context) {
        List<CameraInfo> out = new ArrayList<>();
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                CameraManager cm =
                        (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cm == null) return out;
                for (String id : cm.getCameraIdList()) {
                    try {
                        CameraCharacteristics c = cm.getCameraCharacteristics(id);
                        CameraInfo ci = new CameraInfo();
                        ci.id = id;
                        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                        ci.facing = facing != null && facing == 1 ? "Front"
                                : facing != null && facing == 0 ? "Back" : "External";
                        try {
                            Size[] sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                                    .getOutputSizes(ImageFormat.JPEG);
                            long max = 0;
                            if (sizes != null) {
                                for (Size s : sizes) {
                                    max = Math.max(max, (long) s.getWidth() * s.getHeight());
                                }
                            }
                            ci.megapixels = max <= 0 ? UNKNOWN
                                    : new DecimalFormat("0.0").format(max / 1000000.0) + " MP";
                        } catch (Exception ignored) {
                            ci.megapixels = UNKNOWN;
                        }
                        try {
                            Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                            ci.flash = Boolean.TRUE.equals(flash) ? "Yes" : "No";
                        } catch (Exception ignored) {
                            ci.flash = UNKNOWN;
                        }
                        try {
                            Integer level = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                            ci.level = level == null ? UNKNOWN : "Level " + level;
                        } catch (Exception ignored) {
                            ci.level = UNKNOWN;
                        }
                        out.add(ci);
                    } catch (Exception ignored) {
                    }
                }
                return out;
            }
        } catch (Exception ignored) {
        }
        try {
            int n = Camera.getNumberOfCameras();
            for (int i = 0; i < n; i++) {
                CameraInfo ci = new CameraInfo();
                ci.id = String.valueOf(i);
                ci.facing = UNKNOWN;
                ci.megapixels = UNKNOWN;
                ci.flash = UNKNOWN;
                ci.level = UNKNOWN;
                out.add(ci);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<String> codecs() {
        List<String> out = new ArrayList<>();
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                MediaCodecList list = new MediaCodecList(
                        MediaCodecList.ALL_CODECS);
                for (MediaCodecInfo info : list.getCodecInfos()) {
                    try {
                        out.add((info.isEncoder() ? "ENC " : "DEC ") + info.getName());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out);
        return out;
    }

    public static List<String> systemFeatures(Context context) {
        List<String> out = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            FeatureInfo[] feats = pm.getSystemAvailableFeatures();
            if (feats != null) {
                for (FeatureInfo f : feats) {
                    if (f != null && f.name != null) out.add(f.name);
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out);
        return out;
    }

    public static class AppCounts {
        public int total;
        public int user;
        public int system;
        public int updated;
        public int disabled;
    }

    public static AppCounts appCounts(Context context) {
        AppCounts c = new AppCounts();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            c.total = apps.size();
            for (ApplicationInfo a : apps) {
                boolean sys = (a.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean upd = (a.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                boolean dis = !a.enabled;
                if (upd) c.updated++;
                if (sys && !upd) c.system++;
                else c.user++;
                if (dis) c.disabled++;
            }
        } catch (Exception ignored) {
        }
        return c;
    }

    // ---------- network ----------

    public static List<String> ipAddresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                        out.add(ni.getName() + ": " + a.getHostAddress());
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String wifiSummary(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return "Wi-Fi off";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() < 0) return "Not connected";
            String ssid = info.getSSID();
            if (ssid != null) ssid = ssid.replace("\"", "");
            return (ssid == null ? "-" : ssid) + "  •  " + info.getRssi() + " dBm  •  "
                    + info.getLinkSpeed() + " Mbps";
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    public static String activeNetwork(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return UNKNOWN;
            NetworkInfo active = cm.getActiveNetworkInfo();
            if (active == null || !active.isConnected()) return "Disconnected";
            return active.getTypeName() + (active.isRoaming() ? " (roaming)" : "");
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    // ---------- misc ----------

    public static String uptime() {
        try {
            long ms = SystemClock.elapsedRealtime();
            long s = ms / 1000;
            long d = s / 86400;
            long h = (s % 86400) / 3600;
            long m = (s % 3600) / 60;
            StringBuilder b = new StringBuilder();
            if (d > 0) b.append(d).append("d ");
            if (h > 0 || d > 0) b.append(h).append("h ");
            b.append(m).append("m");
            return b.toString();
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    public static String buildTime() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(Build.TIME));
        } catch (Exception ignored) {
            return UNKNOWN;
        }
    }

    public static DisplayMetrics metrics(Context context) {
        try {
            return context.getResources().getDisplayMetrics();
        } catch (Exception ignored) {
            return null;
        }
    }
}
