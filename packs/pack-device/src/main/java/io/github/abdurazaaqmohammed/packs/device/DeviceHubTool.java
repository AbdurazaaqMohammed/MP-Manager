package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.tabs.TabLayout;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full device-info suite: 12 lazy Material tabs covering hardware, SoC,
 * GPU, memory, storage, battery, network, sensors, system, thermals, root
 * status, and apps. Heavy readers run off the UI thread, live tabs refresh
 * on a tick, and everything is released in onDestroy().
 */
public class DeviceHubTool extends BaseToolPlugin {

    private static final String[] TABS = {
            "Overview", "CPU", "GPU", "Memory", "Storage", "Battery",
            "Network", "Sensors", "System", "Thermal", "Root", "Apps"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View[] pages = new View[TABS.length];
    private final boolean[] built = new boolean[TABS.length];
    private final Runnable[] live = new Runnable[TABS.length];
    private int current;
    private boolean stopped;
    private TabLayout tabLayout;
    private FrameLayout pageHolder;

    private SensorManager sensorManager;
    private SensorEventListener activeListener;
    private float[][] sensorLatest;
    private TextView sensorLiveText;

    private MiniGraph cpuGraph;
    private MiniGraph battGraph;
    private MiniGraph thermGraph;

    public DeviceHubTool() {
        super("devicehub", "Device Info", "Battery, CPU, sensors, storage", ToolCategories.DEVICE);
    }

    // ---------- small ui kit ----------

    private static LinearLayout sectionCard(Context context, LinearLayout box, String title) {
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, Color.BLACK));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m14 = ToolViewFactory.dp(context, 14);
        int m6 = ToolViewFactory.dp(context, 6);
        tp.setMargins(0, m14, 0, m6);
        box.addView(t, tp);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int p14 = ToolViewFactory.dp(context, 14);
        card.setPadding(p14, p14, p14, p14);
        try {
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(ToolViewFactory.dp(context, 16));
            gd.setColor(MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.parseColor("#14000000")));
            card.setBackground(gd);
        } catch (Exception ignored) {
        }
        box.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static TextView kv(Context context, LinearLayout card, String key, String value) {
        TextView k = new TextView(context);
        k.setText(key.toUpperCase(Locale.US));
        k.setTextSize(11);
        k.setAlpha(0.65f);
        k.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        card.addView(k);
        TextView v = new TextView(context);
        v.setText(value == null || value.isEmpty() ? DeviceSys.UNKNOWN : value);
        v.setTextSize(14);
        v.setTypeface(Typeface.MONOSPACE);
        v.setTextIsSelectable(true);
        v.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = ToolViewFactory.dp(context, 8);
        card.addView(v, p);
        return v;
    }

    private static ProgressBar barRow(Context context, LinearLayout card, String label) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        card.addView(t);
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = ToolViewFactory.dp(context, 10);
        card.addView(bar, p);
        return bar;
    }

    private static TextView expandable(Context context, LinearLayout card, String buttonText,
                                       final String content) {
        final TextView out = new TextView(context);
        out.setTextSize(12);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setVisibility(View.GONE);
        out.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        MaterialButton btn = new MaterialButton(context);
        btn.setText(buttonText);
        final boolean[] shown = {false};
        btn.setOnClickListener(v -> {
            shown[0] = !shown[0];
            out.setVisibility(shown[0] ? View.VISIBLE : View.GONE);
            if (shown[0] && out.getText().length() == 0) out.setText(content);
        });
        card.addView(btn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(out);
        return out;
    }

    private void bg(Runnable r) {
        Thread t = new Thread(() -> {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ---------- shell ----------

    @Override
    public View createView(Context context, ViewGroup container) {
        stopped = false;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Device Info");
        tabLayout = new TabLayout(context);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        for (String title : TABS) tabLayout.addTab(tabLayout.newTab().setText(title));
        box.addView(tabLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        pageHolder = new FrameLayout(context);
        scroll.addView(pageHolder, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final android.view.GestureDetector swipe =
                new android.view.GestureDetector(context,
                        new android.view.GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onFling(android.view.MotionEvent e1,
                                                   android.view.MotionEvent e2,
                                                   float vx, float vy) {
                                try {
                                    float dx = e2.getX() - e1.getX();
                                    float dy = e2.getY() - e1.getY();
                                    if (Math.abs(dx) > Math.abs(dy) * 1.5f
                                            && Math.abs(dx) > ToolViewFactory.dp(context, 90)
                                            && Math.abs(vx) > 400) {
                                        int next = current + (dx < 0 ? 1 : -1);
                                        if (next < 0) next = TABS.length - 1;
                                        if (next >= TABS.length) next = 0;
                                        TabLayout.Tab tab = tabLayout.getTabAt(next);
                                        if (tab != null) tab.select();
                                        else showTab(context, next);
                                        return true;
                                    }
                                } catch (Exception ignored) {
                                }
                                return false;
                            }
                        });
        scroll.setOnTouchListener((v, e) -> {
            try {
                swipe.onTouchEvent(e);
            } catch (Exception ignored) {
            }
            return false;
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        box.addView(scroll, sp);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(context, tab == null ? 0 : tab.getPosition());
            }

            public void onTabUnselected(TabLayout.Tab tab) {
            }

            public void onTabReselected(TabLayout.Tab tab) {
                showTab(context, tab == null ? 0 : tab.getPosition());
            }
        });
        showTab(context, 0);
        handler.postDelayed(tick, 1500);
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton refreshBtn = ToolViewFactory.makeRowButton(row, "Refresh", 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, "Copy report", 1f);
        refreshBtn.setOnClickListener(v -> {
            built[current] = false;
            showTab(context, current);
            ToolViewFactory.toast(context, "Refreshed");
        });
        copyBtn.setOnClickListener(v -> {
            ToolViewFactory.toast(context, "Building report…");
            bg(() -> {
                final String report = buildReport(context);
                handler.post(() -> ToolViewFactory.copyText(context, "device-hub", report));
            });
        });
        return box;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                if (!stopped && current >= 0 && current < live.length
                        && live[current] != null && pages[current] != null
                        && pages[current].getParent() != null) {
                    live[current].run();
                }
            } catch (Exception ignored) {
            }
            if (!stopped) handler.postDelayed(this, 2000);
        }
    };

    private void showTab(Context context, int index) {
        if (index < 0 || index >= TABS.length) index = 0;
        current = index;
        if (!built[index]) {
            built[index] = true;
            pages[index] = buildTab(context, index);
        }
        pageHolder.removeAllViews();
        if (pages[index] != null) pageHolder.addView(pages[index]);
        if (live[index] != null) {
            try {
                live[index].run();
            } catch (Exception ignored) {
            }
        }
    }

    private View buildTab(Context context, int index) {
        switch (index) {
            case 0:
                return tabOverview(context);
            case 1:
                return tabCpu(context);
            case 2:
                return tabGpu(context);
            case 3:
                return tabMemory(context);
            case 4:
                return tabStorage(context);
            case 5:
                return tabBattery(context);
            case 6:
                return tabNetwork(context);
            case 7:
                return tabSensors(context);
            case 8:
                return tabSystem(context);
            case 9:
                return tabThermal(context);
            case 10:
                return tabRoot(context);
            default:
                return tabApps(context);
        }
    }

    // ---------- tabs ----------

    private View tabOverview(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hero = sectionCard(context, box, "This device");
        final TextView model = kv(context, hero, "Model",
                Build.MANUFACTURER + " " + Build.MODEL);
        kv(context, hero, "Android", "Android " + Build.VERSION.RELEASE
                + "  •  SDK " + Build.VERSION.SDK_INT
                + "  •  Patch " + DeviceSys.UNKNOWN);
        final TextView soc = kv(context, hero, "Chipset", "Reading…");
        final TextView ram = kv(context, hero, "Memory", "Reading…");
        final TextView stor = kv(context, hero, "Storage", "Reading…");
        final TextView batt = kv(context, hero, "Battery", "Reading…");
        final TextView root = kv(context, hero, "Root", "Checking…");
        bg(() -> {
            Map<String, String> cpu = DeviceSys.cpuInfo();
            String chipset = cpu.containsKey("Hardware") ? cpu.get("Hardware") : Build.HARDWARE;
            DeviceSys.MemInfo mi = DeviceSys.memory(context);
            List<DeviceSys.Volume> vols = DeviceSys.volumes(context);
            DeviceSys.Battery b = DeviceSys.battery(context);
            final boolean rooted = DeviceSys.rootGranted();
            final boolean suPresent = DeviceSys.suBinaryPresent();
            String patch = "";
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    patch = Build.VERSION.SECURITY_PATCH;
                }
            } catch (Exception ignored) {
            }
            final String patchF = patch == null || patch.isEmpty() ? DeviceSys.UNKNOWN : patch;
            handler.post(() -> {
                try {
                    model.setText(Build.MANUFACTURER + " " + Build.MODEL
                            + "  •  " + Build.DEVICE);
                    soc.setText(chipset + "  •  " + DeviceSys.cpuCount() + " cores");
                    ram.setText(mi.total <= 0 ? DeviceSys.UNKNOWN
                            : DeviceSys.formatBytes(mi.total) + (mi.low ? "  •  LOW" : ""));
                    long total = 0;
                    for (DeviceSys.Volume v : vols) total += v.total;
                    stor.setText(total <= 0 ? DeviceSys.UNKNOWN : DeviceSys.formatBytes(total));
                    batt.setText(b.pct < 0 ? DeviceSys.UNKNOWN : b.pct + "%  •  " + b.status);
                    root.setText(rooted ? "Granted" : suPresent ? "Binary present, not granted" : "Not rooted");
                    for (int i = 0; i < box.getChildCount(); i++) {
                        View child = box.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            LinearLayout maybe = (LinearLayout) child;
                            for (int j = 0; j < maybe.getChildCount(); j++) {
                                View v = maybe.getChildAt(j);
                                if (v instanceof TextView
                                        && ((TextView) v).getText().toString().startsWith("Android ")) {
                                    ((TextView) v).setText("Android " + Build.VERSION.RELEASE
                                            + "  •  SDK " + Build.VERSION.SDK_INT
                                            + "  •  Patch " + patchF);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        });
        live[0] = () -> {
            try {
                DeviceSys.Battery b = DeviceSys.battery(context);
                if (b.pct >= 0) batt.setText(b.pct + "%  •  " + b.status);
            } catch (Exception ignored) {
            }
        };
        return box;
    }

    private View tabCpu(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout info = sectionCard(context, box, "Processor");
        final TextView infoText = kv(context, info, "Static", "Reading…");
        LinearLayout monitor = sectionCard(context, box, "Live load");
        final TextView totalText = kv(context, monitor, "Total", "Sampling…");
        final ProgressBar totalBar = barRow(context, monitor, "Total usage");
        cpuGraph = new MiniGraph(context, Color.parseColor("#3F7CFF"));
        monitor.addView(cpuGraph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
        LinearLayout coresBox = new LinearLayout(context);
        coresBox.setOrientation(LinearLayout.VERTICAL);
        monitor.addView(coresBox);
        final int cores = DeviceSys.cpuCount();
        final TextView[] coreLabels = new TextView[cores];
        final ProgressBar[] coreBars = new ProgressBar[cores];
        for (int i = 0; i < cores; i++) {
            coreLabels[i] = new TextView(context);
            coreLabels[i].setTextSize(13);
            coresBox.addView(coreLabels[i]);
            ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = ToolViewFactory.dp(context, 8);
            coresBox.addView(bar, p);
            coreBars[i] = bar;
        }
        bg(() -> {
            Map<String, String> cpu = DeviceSys.cpuInfo();
            StringBuilder b = new StringBuilder();
            b.append("Cores: ").append(cores).append('\n');
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    b.append("ABI: ").append(android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS)).append('\n');
                } catch (Exception ignored) {
                }
            }
            b.append("Hardware: ").append(Build.HARDWARE).append("  Board: ").append(Build.BOARD).append('\n');
            b.append("Governor: ").append(DeviceSys.cpuGovernor(0)).append('\n');
            for (Map.Entry<String, String> e : cpu.entrySet()) {
                if (e.getKey().equals("Features")) continue;
                b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            if (cpu.containsKey("Features")) {
                String feats = cpu.get("Features");
                b.append("Features: ").append(feats.length() > 220 ? feats.substring(0, 220) + "…" : feats);
            }
            handler.post(() -> {
                try {
                    infoText.setText(b.toString().trim());
                } catch (Exception ignored) {
                }
            });
        });
        final Runnable[] sampler = new Runnable[1];
        sampler[0] = () -> bg(() -> {
            DeviceSys.CpuLoad load = DeviceSys.sampleCpuLoad(cores);
            handler.post(() -> {
                try {
                    totalText.setText(new DecimalFormat("0.0").format(load.total) + " %");
                    totalBar.setProgress(Math.round(load.total));
                    if (cpuGraph != null) cpuGraph.push(load.total);
                    for (int i = 0; i < cores; i++) {
                        long mhz = DeviceSys.cpuCurMhz(i);
                        long max = DeviceSys.cpuMaxMhz(i);
                        String freq = mhz < 0 ? "offline" : mhz + " MHz"
                                + (max > 0 ? " / " + max : "");
                        float pct = i < load.cores.length && load.cores[i] >= 0 ? load.cores[i] : 0;
                        coreLabels[i].setText("CPU" + i + "  •  " + freq
                                + "  •  " + new DecimalFormat("0.0").format(pct) + " %");
                        coreBars[i].setProgress(Math.round(pct));
                    }
                } catch (Exception ignored) {
                }
            });
        });
        live[1] = () -> {
            try {
                sampler[0].run();
            } catch (Exception ignored) {
            }
        };
        sampler[0].run();
        return box;
    }

    private View tabGpu(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout gpu = sectionCard(context, box, "GPU");
        final TextView gpuText = kv(context, gpu, "Renderer", "Probing…");
        bg(() -> {
            EglGpu.Info info = EglGpu.probe();
            final String text = "Vendor: " + info.vendor + "\nRenderer: " + info.renderer
                    + "\nVersion: " + info.version + "\nShading: " + info.shading;
            handler.post(() -> {
                try {
                    gpuText.setText(text);
                } catch (Exception ignored) {
                }
            });
            if (!info.extensions.isEmpty()) {
                handler.post(() -> {
                    try {
                        expandable(context, gpu, "Show " + info.extensions.split(" ").length + " extensions",
                                info.extensions.replace(" ", "\n"));
                    } catch (Exception ignored) {
                    }
                });
            }
        });
        LinearLayout disp = sectionCard(context, box, "Display");
        DisplayMetrics dm = DeviceSys.metrics(context);
        if (dm != null) {
            kv(context, disp, "Resolution", dm.widthPixels + " × " + dm.heightPixels);
            kv(context, disp, "Density", dm.densityDpi + " dpi  •  ×" + dm.density);
            kv(context, disp, "Physical", new DecimalFormat("0.0").format(dm.xdpi)
                    + " × " + new DecimalFormat("0.0").format(dm.ydpi) + " dpi");
        }
        try {
            android.view.WindowManager wm =
                    (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                android.view.Display display = wm.getDefaultDisplay();
                float hz = DeviceSys.refreshRate(display);
                kv(context, disp, "Refresh rate",
                        Float.isNaN(hz) ? DeviceSys.UNKNOWN : new DecimalFormat("0.#").format(hz) + " Hz");
                String modes = DeviceSys.displayModes(display);
                if (!modes.isEmpty()) expandable(context, disp, "Show display modes", modes);
                kv(context, disp, "HDR", DeviceSys.hdrCaps(display));
            }
        } catch (Exception ignored) {
        }
        LinearLayout cams = sectionCard(context, box, "Cameras");
        final TextView camText = kv(context, cams, "List", "Reading…");
        bg(() -> {
            List<DeviceSys.CameraInfo> list = DeviceSys.cameras(context);
            StringBuilder b = new StringBuilder();
            if (list.isEmpty()) b.append("No cameras reported");
            for (DeviceSys.CameraInfo ci : list) {
                b.append("Camera ").append(ci.id).append(" (").append(ci.facing).append("): ")
                        .append(ci.megapixels).append("  •  Flash ").append(ci.flash)
                        .append("  •  ").append(ci.level).append('\n');
            }
            handler.post(() -> {
                try {
                    camText.setText(b.toString().trim());
                } catch (Exception ignored) {
                }
            });
        });
        return box;
    }

    private View tabMemory(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout mem = sectionCard(context, box, "RAM");
        final TextView usage = kv(context, mem, "Usage", "Reading…");
        final ProgressBar bar = barRow(context, mem, "Used");
        final TextView detail = kv(context, mem, "Breakdown", "Reading…");
        live[3] = () -> {
            try {
                DeviceSys.MemInfo mi = DeviceSys.memory(context);
                if (mi.total <= 0) return;
                long used = mi.total - mi.avail;
                int pct = (int) (used * 100 / mi.total);
                usage.setText(DeviceSys.formatBytes(used) + " / " + DeviceSys.formatBytes(mi.total)
                        + "  •  " + pct + "%" + (mi.low ? "  •  LOW" : ""));
                bar.setProgress(pct);
                StringBuilder b = new StringBuilder();
                b.append("Free: ").append(DeviceSys.formatBytes(mi.avail)).append('\n');
                for (Map.Entry<String, Long> e : mi.proc.entrySet()) {
                    b.append(e.getKey()).append(": ").append(DeviceSys.formatBytes(e.getValue())).append('\n');
                }
                detail.setText(b.toString().trim());
            } catch (Exception ignored) {
            }
        };
        live[3].run();
        return box;
    }

    private View tabStorage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout vols = sectionCard(context, box, "Volumes");
        final TextView volsText = kv(context, vols, "Scanning", "Reading volumes…");
        final LinearLayout bars = new LinearLayout(context);
        bars.setOrientation(LinearLayout.VERTICAL);
        vols.addView(bars);
        live[4] = () -> {
            try {
                List<DeviceSys.Volume> list = DeviceSys.volumes(context);
                StringBuilder b = new StringBuilder();
                bars.removeAllViews();
                for (DeviceSys.Volume v : list) {
                    long used = v.total - v.free;
                    int pct = v.total <= 0 ? 0 : (int) (used * 100 / v.total);
                    b.append(v.label).append(v.removable ? " (removable)" : "").append('\n')
                            .append(v.path).append('\n')
                            .append(DeviceSys.formatBytes(used)).append(" / ")
                            .append(DeviceSys.formatBytes(v.total)).append("  •  ")
                            .append(pct).append("%  •  ").append(DeviceSys.fsType(v.path))
                            .append("\n\n");
                    TextView t = new TextView(context);
                    t.setTextSize(13);
                    t.setText(v.label + "  •  " + pct + "%");
                    bars.addView(t);
                    ProgressBar bar = new ProgressBar(context, null,
                            android.R.attr.progressBarStyleHorizontal);
                    bar.setMax(100);
                    bar.setProgress(pct);
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    p.bottomMargin = ToolViewFactory.dp(context, 10);
                    bars.addView(bar, p);
                }
                volsText.setText(b.toString().trim());
            } catch (Exception ignored) {
            }
        };
        live[4].run();
        return box;
    }

    private View tabBattery(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout batt = sectionCard(context, box, "Battery");
        final TextView level = kv(context, batt, "Charge", "Reading…");
        final ProgressBar bar = barRow(context, batt, "Level");
        battGraph = new MiniGraph(context, Color.parseColor("#2FA84F"));
        battGraph.setForcedMax(50);
        batt.addView(battGraph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
        final TextView detail = kv(context, batt, "Details", "Reading…");
        final TextView capacity = kv(context, batt, "Capacity", "Reading…");
        live[5] = () -> {
            try {
                DeviceSys.Battery b = DeviceSys.battery(context);
                if (b.pct >= 0) {
                    level.setText(b.pct + "%  •  " + b.status);
                    bar.setProgress(b.pct);
                }
                StringBuilder d = new StringBuilder();
                d.append("State: ").append(b.status).append("  •  ").append(b.plugged).append('\n');
                d.append("Temp: ").append(Float.isNaN(b.tempC) ? DeviceSys.UNKNOWN
                        : new DecimalFormat("0.0").format(b.tempC) + " °C").append('\n');
                d.append("Voltage: ").append(b.mv < 0 ? DeviceSys.UNKNOWN : b.mv + " mV").append('\n');
                d.append("Tech: ").append(b.tech).append("  •  Health: ")
                        .append(DeviceSys.batteryHealth(b.health)).append('\n');
                if (b.currentUa != Long.MIN_VALUE) {
                    d.append("Current: ").append(new DecimalFormat("0.0").format(b.currentUa / 1000.0))
                            .append(" mA").append(b.currentUa < 0 ? " (discharging)" : " (charging)").append('\n');
                }
                if (b.chargeCounterUah != Long.MIN_VALUE && b.chargeCounterUah > 0) {
                    d.append("Charge counter: ").append(new DecimalFormat("0.0")
                            .format(b.chargeCounterUah / 1000.0)).append(" mAh\n");
                }
                d.append("Cycles: ").append(b.cycles);
                detail.setText(d.toString());
                StringBuilder c = new StringBuilder();
                if (b.designUah != Long.MIN_VALUE && b.designUah > 0) {
                    c.append("Design: ").append(new DecimalFormat("0.0")
                            .format(b.designUah / 1000.0)).append(" mAh\n");
                }
                if (b.fullUah != Long.MIN_VALUE && b.fullUah > 0) {
                    c.append("Full now: ").append(new DecimalFormat("0.0")
                            .format(b.fullUah / 1000.0)).append(" mAh\n");
                    if (b.designUah != Long.MIN_VALUE && b.designUah > 0) {
                        c.append("Wear: ").append(new DecimalFormat("0.0").format(
                                100f * (1f - (float) b.fullUah / b.designUah))).append("%");
                    }
                }
                if (c.length() == 0) c.append("Capacity nodes unreadable on this device");
                capacity.setText(c.toString());
                if (!Float.isNaN(b.tempC) && battGraph != null) battGraph.push(b.tempC);
            } catch (Exception ignored) {
            }
        };
        live[5].run();
        return box;
    }

    private View tabNetwork(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout net = sectionCard(context, box, "Connection");
        final TextView conn = kv(context, net, "Active", "Reading…");
        final TextView wifi = kv(context, net, "Wi-Fi", "Reading…");
        final TextView ips = kv(context, net, "IP addresses", "Reading…");
        final TextView traffic = kv(context, net, "Since boot", "Reading…");
        live[6] = () -> {
            try {
                conn.setText(DeviceSys.activeNetwork(context));
                wifi.setText(DeviceSys.wifiSummary(context));
                List<String> addrs = DeviceSys.ipAddresses();
                StringBuilder b = new StringBuilder();
                if (addrs.isEmpty()) b.append(DeviceSys.UNKNOWN);
                for (String a : addrs) b.append(a).append('\n');
                ips.setText(b.toString().trim());
                long rxM = 0, txM = 0, rxT = 0, txT = 0;
                try {
                    rxM = TrafficStats.getMobileRxBytes();
                    txM = TrafficStats.getMobileTxBytes();
                    rxT = TrafficStats.getTotalRxBytes();
                    txT = TrafficStats.getTotalTxBytes();
                } catch (Exception ignored) {
                }
                traffic.setText("Mobile ↓ " + DeviceSys.formatBytes(Math.max(0, rxM))
                        + "  ↑ " + DeviceSys.formatBytes(Math.max(0, txM))
                        + "\nTotal ↓ " + DeviceSys.formatBytes(Math.max(0, rxT))
                        + "  ↑ " + DeviceSys.formatBytes(Math.max(0, txT)));
            } catch (Exception ignored) {
            }
        };
        live[6].run();
        return box;
    }

    private View tabSensors(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout feed = sectionCard(context, box, "Live feed");
        sensorLiveText = new TextView(context);
        sensorLiveText.setTextSize(14);
        sensorLiveText.setTypeface(Typeface.MONOSPACE);
        sensorLiveText.setText("Starting live feed…");
        feed.addView(sensorLiveText);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        feed.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton feedBtn = new MaterialButton(context);
        feedBtn.setText("Live feed");
        feedBtn.setOnClickListener(v -> {
            try {
                startSensorsListener();
            } catch (Exception ignored) {
            }
        });
        row.addView(feedBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton altBtn = new MaterialButton(context);
        altBtn.setText("Altimeter");
        altBtn.setOnClickListener(v -> {
            try {
                if (sensorManager == null) return;
                Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (pressure == null) {
                    sensorLiveText.setText("No barometer on this device");
                    return;
                }
                startAltimeterListener(sensorLiveText, pressure);
            } catch (Exception ignored) {
            }
        });
        row.addView(altBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout list = sectionCard(context, box, "All sensors");
        final TextView listText = kv(context, list, "Loading", "Reading sensors…");
        bg(() -> {
            List<Sensor> all = DeviceSys.allSensors(context);
            StringBuilder b = new StringBuilder();
            b.append(all.size()).append(" sensors\n\n");
            for (Sensor s : all) {
                try {
                    b.append(s.getName()).append('\n').append("  ").append(s.getVendor())
                            .append("  •  v").append(s.getVersion())
                            .append("  •  ").append(s.getPower()).append(" mA\n");
                } catch (Exception ignored) {
                }
            }
            handler.post(() -> {
                try {
                    listText.setText(b.toString().trim());
                } catch (Exception ignored) {
                }
            });
        });
        try {
            sensorLatest = new float[5][];
            startSensorsListener();
        } catch (Exception ignored) {
        }
        return box;
    }

    private View tabSystem(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout os = sectionCard(context, box, "Android & build");
        kv(context, os, "Release", "Android " + Build.VERSION.RELEASE + "  •  SDK " + Build.VERSION.SDK_INT);
        String patch = DeviceSys.UNKNOWN;
        try {
            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SECURITY_PATCH != null) {
                patch = Build.VERSION.SECURITY_PATCH;
            }
        } catch (Exception ignored) {
        }
        kv(context, os, "Security patch", patch);
        kv(context, os, "Build", Build.DISPLAY + "  •  " + Build.ID);
        kv(context, os, "Fingerprint", Build.FINGERPRINT);
        kv(context, os, "Tags / type", Build.TAGS + "  •  " + Build.TYPE);
        kv(context, os, "Build time", DeviceSys.buildTime());
        kv(context, os, "Kernel", System.getProperty("os.version"));
        kv(context, os, "Uptime", DeviceSys.uptime());
        kv(context, os, "ABIs", Build.VERSION.SDK_INT >= 21
                ? android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS) : Build.CPU_ABI);
        kv(context, os, "Device", Build.BRAND + " " + Build.DEVICE + "  •  " + Build.PRODUCT);
        kv(context, os, "Hardware", Build.HARDWARE + "  •  " + Build.BOARD
                + "  •  " + Build.BOOTLOADER);
        final TextView treble = kv(context, os, "Treble / VNDK", "Reading…");
        bg(() -> {
            Map<String, String> props = DeviceSys.getprop();
            String t = props.containsKey("ro.treble.enabled") ? props.get("ro.treble.enabled") : "";
            String vndk = props.containsKey("ro.vndk.version") ? props.get("ro.vndk.version") : "";
            final String text = ("true".equals(t) ? "Supported" : "Not detected")
                    + (vndk.isEmpty() ? "" : "  •  VNDK " + vndk);
            handler.post(() -> {
                try {
                    treble.setText(text);
                } catch (Exception ignored) {
                }
            });
        });
        LinearLayout feats = sectionCard(context, box, "Hardware features");
        final TextView featCount = kv(context, feats, "Count", "Reading…");
        bg(() -> {
            List<String> list = DeviceSys.systemFeatures(context);
            handler.post(() -> {
                try {
                    featCount.setText(list.size() + " features");
                    if (!list.isEmpty()) {
                        StringBuilder b = new StringBuilder();
                        for (String f : list) b.append(f).append('\n');
                        expandable(context, feats, "Show all features", b.toString().trim());
                    }
                } catch (Exception ignored) {
                }
            });
        });
        LinearLayout codecs = sectionCard(context, box, "Codecs");
        final TextView codecCount = kv(context, codecs, "Count", "Reading…");
        bg(() -> {
            List<String> list = DeviceSys.codecs();
            handler.post(() -> {
                try {
                    codecCount.setText(list.size() + " codecs");
                    if (!list.isEmpty()) {
                        StringBuilder b = new StringBuilder();
                        for (String c : list) b.append(c).append('\n');
                        expandable(context, codecs, "Show all codecs", b.toString().trim());
                    }
                } catch (Exception ignored) {
                }
            });
        });
        return box;
    }

    private View tabThermal(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout therm = sectionCard(context, box, "Thermal zones");
        final TextView max = kv(context, therm, "Hottest", "Reading…");
        thermGraph = new MiniGraph(context, Color.parseColor("#E06428"));
        thermGraph.setForcedMax(100);
        therm.addView(thermGraph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
        final LinearLayout zones = new LinearLayout(context);
        zones.setOrientation(LinearLayout.VERTICAL);
        therm.addView(zones);
        live[9] = () -> {
            try {
                List<DeviceSys.Zone> list = DeviceSys.thermalZones();
                if (list.isEmpty()) {
                    max.setText("No thermal zones readable on this device");
                    return;
                }
                float hottest = -1000;
                String hotName = "";
                for (DeviceSys.Zone z : list) {
                    if (z.tempC > hottest) {
                        hottest = z.tempC;
                        hotName = z.type;
                    }
                }
                max.setText(hotName + "  •  " + new DecimalFormat("0.0").format(hottest) + " °C");
                if (thermGraph != null) thermGraph.push(hottest);
                zones.removeAllViews();
                for (DeviceSys.Zone z : list) {
                    TextView t = new TextView(context);
                    t.setTextSize(13);
                    t.setText(z.type + "  •  " + new DecimalFormat("0.0").format(z.tempC) + " °C");
                    zones.addView(t);
                    ProgressBar bar = new ProgressBar(context, null,
                            android.R.attr.progressBarStyleHorizontal);
                    bar.setMax(100);
                    bar.setProgress(Math.max(0, Math.min(100, Math.round(z.tempC))));
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    p.bottomMargin = ToolViewFactory.dp(context, 8);
                    zones.addView(bar, p);
                }
            } catch (Exception ignored) {
            }
        };
        live[9].run();
        return box;
    }

    private View tabRoot(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout status = sectionCard(context, box, "Root & verified boot");
        final TextView rootText = kv(context, status, "Checking", "Probing su…");
        final TextView magisk = kv(context, status, "Magisk", "Checking…");
        final TextView selinux = kv(context, status, "SELinux", DeviceSys.selinuxStatus());
        bg(() -> {
            boolean suPresent = DeviceSys.suBinaryPresent();
            boolean granted = suPresent && DeviceSys.rootGranted();
            final String rt = granted ? "Root granted"
                    : suPresent ? "su binary present, access not granted" : "Not rooted";
            handler.post(() -> {
                try {
                    rootText.setText(rt);
                } catch (Exception ignored) {
                }
            });
            String mag = "";
            try {
                mag = DeviceSys.magiskVersion();
            } catch (Exception ignored) {
            }
            final String magF = mag;
            handler.post(() -> {
                try {
                    magisk.setText(magF.isEmpty() ? "Not detected" : magF);
                } catch (Exception ignored) {
                }
            });
        });
        LinearLayout boot = sectionCard(context, box, "Boot & security state");
        final TextView bootText = kv(context, boot, "Reading", "Reading properties…");
        bg(() -> {
            Map<String, String> props = DeviceSys.getprop();
            String[] keys = {"ro.boot.verifiedbootstate", "ro.boot.flash.locked",
                    "ro.boot.vbmeta.digest", "ro.crypto.state", "ro.secure",
                    "ro.debuggable", "ro.build.selinux", "sys.usb.config"};
            StringBuilder b = new StringBuilder();
            for (String k : keys) {
                String v = props.get(k);
                if (v != null && !v.isEmpty()) b.append(k).append(" = ").append(v).append('\n');
            }
            int adb = -1;
            try {
                adb = android.provider.Settings.Global.getInt(
                        context.getContentResolver(),
                        android.provider.Settings.Global.ADB_ENABLED, -1);
            } catch (Exception ignored) {
            }
            if (adb >= 0) b.append("adb_enabled = ").append(adb).append('\n');
            if (b.length() == 0) b.append("Nothing readable");
            final String text = b.toString().trim();
            handler.post(() -> {
                try {
                    bootText.setText(text);
                } catch (Exception ignored) {
                }
            });
        });
        LinearLayout props = sectionCard(context, box, "All properties");
        final TextView propCount = kv(context, props, "Count", "Reading…");
        bg(() -> {
            Map<String, String> all = DeviceSys.getprop();
            handler.post(() -> {
                try {
                    propCount.setText(all.size() + " properties"
                            + (DeviceSys.rootGranted() ? "  •  root active" : ""));
                    if (!all.isEmpty()) {
                        StringBuilder b = new StringBuilder();
                        for (Map.Entry<String, String> e : all.entrySet()) {
                            b.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
                        }
                        expandable(context, props, "Show all properties", b.toString().trim());
                    }
                } catch (Exception ignored) {
                }
            });
        });
        return box;
    }

    private View tabApps(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout apps = sectionCard(context, box, "Installed apps");
        final TextView counts = kv(context, apps, "Counting", "Reading package list…");
        bg(() -> {
            DeviceSys.AppCounts c = DeviceSys.appCounts(context);
            final String text = "Total: " + c.total + "\nUser: " + c.user
                    + "\nSystem: " + c.system + "\nUpdated system: " + c.updated
                    + "\nDisabled: " + c.disabled;
            handler.post(() -> {
                try {
                    counts.setText(text);
                } catch (Exception ignored) {
                }
            });
        });
        return box;
    }

    // ---------- sensors (kept live feed + altimeter) ----------

    private void startSensorsListener() {
        if (sensorManager == null || sensorLiveText == null) {
            return;
        }
        final String[] names = new String[]{"Accel", "Gyro", "Magnet", "Light", "Proximity"};
        final float[][] latest = sensorLatest == null ? (sensorLatest = new float[5][]) : sensorLatest;
        try {
            if (activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        activeListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                int type = event.sensor.getType();
                if (type == Sensor.TYPE_ACCELEROMETER) {
                    latest[0] = event.values.clone();
                } else if (type == Sensor.TYPE_GYROSCOPE) {
                    latest[1] = event.values.clone();
                } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
                    latest[2] = event.values.clone();
                } else if (type == Sensor.TYPE_LIGHT) {
                    latest[3] = event.values.clone();
                } else if (type == Sensor.TYPE_PROXIMITY) {
                    latest[4] = event.values.clone();
                } else {
                    return;
                }
                if (sensorLiveText != null) {
                    StringBuilder b = new StringBuilder();
                    DecimalFormat df = new DecimalFormat("0.00");
                    for (int i = 0; i < 5; i++) {
                        b.append(names[i]).append(": ");
                        if (latest[i] == null) {
                            b.append("-");
                        } else {
                            for (int j = 0; j < latest[i].length; j++) {
                                if (j > 0) {
                                    b.append(", ");
                                }
                                b.append(df.format(latest[i][j]));
                            }
                        }
                        if (i < 4) {
                            b.append("\n");
                        }
                    }
                    sensorLiveText.setText(b.toString());
                }
            }

            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        int[] types = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY};
        int found = 0;
        for (int t : types) {
            try {
                Sensor s = sensorManager.getDefaultSensor(t);
                if (s != null) {
                    sensorManager.registerListener(activeListener, s, SensorManager.SENSOR_DELAY_UI);
                    found++;
                }
            } catch (Exception ignored) {
            }
        }
        if (found == 0 && sensorLiveText != null) {
            sensorLiveText.setText("No common sensors found");
        }
    }

    private void startAltimeterListener(final TextView output, Sensor pressure) {
        try {
            if (activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        activeListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                float hpa = event.values[0];
                double altitude = 44330.0 * (1.0 - Math.pow(hpa / 1013.25, 0.1903));
                DecimalFormat df = new DecimalFormat("0.0");
                if (output != null) {
                    output.setText(df.format(altitude) + " m\n" + df.format(hpa) + " hPa");
                }
            }

            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        try {
            sensorManager.registerListener(activeListener, pressure, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            output.setText("Sensor error");
        }
    }

    // ---------- report ----------

    private String buildReport(Context context) {
        StringBuilder r = new StringBuilder();
        try {
            DisplayMetrics dm = DeviceSys.metrics(context);
            r.append("=== DEVICE ===\n").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    .append("\nAndroid ").append(Build.VERSION.RELEASE)
                    .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                    .append(Build.FINGERPRINT).append('\n');
            r.append("\n=== CPU ===\nCores: ").append(DeviceSys.cpuCount()).append('\n');
            if (Build.VERSION.SDK_INT >= 21) {
                r.append("ABI: ").append(android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS)).append('\n');
            }
            for (Map.Entry<String, String> e : DeviceSys.cpuInfo().entrySet()) {
                r.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            try {
                EglGpu.Info g = EglGpu.probe();
                r.append("\n=== GPU ===\n").append(g.vendor).append(" / ").append(g.renderer)
                        .append("\n").append(g.version).append('\n');
            } catch (Exception ignored) {
            }
            DeviceSys.MemInfo mi = DeviceSys.memory(context);
            r.append("\n=== MEMORY ===\nTotal: ").append(DeviceSys.formatBytes(mi.total))
                    .append("  Free: ").append(DeviceSys.formatBytes(mi.avail)).append('\n');
            r.append("\n=== STORAGE ===\n");
            for (DeviceSys.Volume v : DeviceSys.volumes(context)) {
                r.append(v.label).append(" (").append(v.path).append("): ")
                        .append(DeviceSys.formatBytes(v.total - v.free)).append(" / ")
                        .append(DeviceSys.formatBytes(v.total)).append('\n');
            }
            DeviceSys.Battery b = DeviceSys.battery(context);
            r.append("\n=== BATTERY ===\n").append(b.pct).append("%  ").append(b.status)
                    .append("  ").append(b.plugged).append('\n');
            r.append("\n=== DISPLAY ===\n");
            if (dm != null) {
                r.append(dm.widthPixels).append('x').append(dm.heightPixels)
                        .append("  ").append(dm.densityDpi).append("dpi\n");
            }
            r.append("\n=== NETWORK ===\n").append(DeviceSys.activeNetwork(context)).append('\n')
                    .append(DeviceSys.wifiSummary(context)).append('\n');
            for (String ip : DeviceSys.ipAddresses()) r.append(ip).append('\n');
            r.append("\n=== SENSORS (").append(DeviceSys.allSensors(context).size()).append(") ===\n");
            for (Sensor s : DeviceSys.allSensors(context)) {
                r.append(s.getName()).append(" / ").append(s.getVendor()).append('\n');
            }
            r.append("\n=== SYSTEM ===\nUptime: ").append(DeviceSys.uptime())
                    .append("\nKernel: ").append(System.getProperty("os.version")).append('\n');
            r.append("\n=== THERMAL ===\n");
            for (DeviceSys.Zone z : DeviceSys.thermalZones()) {
                r.append(z.type).append(": ").append(z.tempC).append(" C\n");
            }
            r.append("\n=== ROOT ===\n");
            r.append(DeviceSys.rootGranted() ? "granted"
                    : DeviceSys.suBinaryPresent() ? "su present, not granted" : "not rooted")
                    .append('\n');
            r.append("SELinux: ").append(DeviceSys.selinuxStatus()).append('\n');
            DeviceSys.AppCounts c = DeviceSys.appCounts(context);
            r.append("\n=== APPS ===\nTotal ").append(c.total).append(", user ").append(c.user)
                    .append(", system ").append(c.system).append(", disabled ").append(c.disabled)
                    .append('\n');
        } catch (Exception ignored) {
        }
        return r.toString();
    }

    @Override
    public void onDestroy() {
        stopped = true;
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception ignored) {
        }
        try {
            if (sensorManager != null && activeListener != null) {
                sensorManager.unregisterListener(activeListener);
            }
        } catch (Exception ignored) {
        }
        activeListener = null;
        sensorManager = null;
        sensorLatest = null;
        sensorLiveText = null;
        cpuGraph = null;
        battGraph = null;
        thermGraph = null;
    }
}
