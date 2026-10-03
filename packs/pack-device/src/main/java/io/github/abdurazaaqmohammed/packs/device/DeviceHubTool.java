package io.github.abdurazaaqmohammed.packs.device;

import android.app.Activity;
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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Scanner;

/**
 * Extraction of ToolRunnerActivity.buildDeviceHub() plus the live sensor
 * feed and altimeter. Owns its sensor listener; unregistered in onDestroy().
 */
public class DeviceHubTool extends BaseToolPlugin {

    private SensorManager sensorManager;
    private SensorEventListener activeListener;
    private float[][] sensorLatest;
    private TextView sensorLiveText;
    private Context ctx;
    public DeviceHubTool() {
        super("devicehub", R.string.devicehub_title, R.string.devicehub_sub,  ToolCategories.DEVICE);
    }

    private static LinearLayout addSectionCard(Context context, LinearLayout box, String title) {
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLACK));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
            gd.setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.parseColor("#14000000")));
            card.setBackground(gd);
        } catch (Exception ignored) {}
        box.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static TextView addCardOutput(Context context, LinearLayout card) {
        TextView t = new TextView(context);
        t.setTextSize(14);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return new DecimalFormat("0.0").format(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return new DecimalFormat("0.0").format(mb) + " MB";
        }
        return new DecimalFormat("0.00").format(mb / 1024.0) + " GB";
    }

    private static String readBatterySummary(Context context) {
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return context.getString(R.string.devicehub_unavailable);
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = scale <= 0 ? level : Math.round(level * 100f / scale);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String statusStr = status == BatteryManager.BATTERY_STATUS_CHARGING ? context.getString(R.string.devicehub_batt_charging) : status == BatteryManager.BATTERY_STATUS_FULL ? context.getString(R.string.devicehub_batt_full) : status == BatteryManager.BATTERY_STATUS_DISCHARGING ? context.getString(R.string.devicehub_batt_discharging) : status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ? context.getString(R.string.devicehub_batt_not_charging) : context.getString(R.string.devicehub_batt_unknown);
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            String plugStr = plugged == BatteryManager.BATTERY_PLUGGED_AC ? context.getString(R.string.devicehub_plug_ac) : plugged == BatteryManager.BATTERY_PLUGGED_USB ? context.getString(R.string.devicehub_plug_usb) : plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? context.getString(R.string.devicehub_plug_wireless) : context.getString(R.string.devicehub_plug_none);
            int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            int volt = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            String tech = battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            return context.getString(R.string.devicehub_batt_summary, pct, statusStr, plugStr,
                    new DecimalFormat("0.0").format(temp / 10.0), volt, (tech == null ? "-" : tech));
        } catch (Exception e) {
            return context.getString(R.string.devicehub_unavailable);
        }
    }

    private static String readCpuSummary(Context context) {
        try {
            StringBuilder b = new StringBuilder();
            b.append(context.getString(R.string.devicehub_cores)).append(Runtime.getRuntime().availableProcessors()).append("\n");
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    String[] abis = Build.SUPPORTED_ABIS;
                    if (abis != null) {
                        b.append(context.getString(R.string.devicehub_abi));
                        for (int i = 0; i < abis.length; i++) {
                            if (i > 0) b.append(", ");
                            b.append(abis[i]);
                        }
                        b.append("\n");
                    }
                } catch (Exception ignored) {}
            }
            b.append(context.getString(R.string.devicehub_hw)).append(Build.HARDWARE).append(context.getString(R.string.devicehub_board)).append(Build.BOARD).append("\n");
            try {
                File f = new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
                if (f.exists()) {
                    Scanner s = new Scanner(f);
                    if (s.hasNext()) b.append(context.getString(R.string.devicehub_cpu_freq, Long.parseLong(s.next().trim()) / 1000));
                    s.close();
                }
            } catch (Exception ignored) {}
            return b.toString();
        } catch (Exception e) {
            return context.getString(R.string.devicehub_unavailable);
        }
    }

    private static String readStorageSummary(Context context) {
        try {
            StringBuilder b = new StringBuilder();
            StatFs internal = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long bs = internal.getBlockSizeLong();
            b.append(context.getString(R.string.devicehub_storage_internal, formatBytes((internal.getBlockCountLong() - internal.getAvailableBlocksLong()) * bs), formatBytes(internal.getBlockCountLong() * bs)));
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            b.append(context.getString(R.string.devicehub_storage_ram, formatBytes(mi.totalMem - mi.availMem), formatBytes(mi.totalMem),
                    context.getString(mi.lowMemory ? R.string.devicehub_low : R.string.devicehub_ok)));
            return b.toString();
        } catch (Exception e) {
            return context.getString(R.string.devicehub_unavailable);
        }
    }

    private void startSensorsListener() {
        if (sensorManager == null || sensorLiveText == null) {
            return;
        }
        final String[] names = new String[]{ctx.getString(R.string.devicehub_sensor_accel), ctx.getString(R.string.devicehub_sensor_gyro), ctx.getString(R.string.devicehub_sensor_magnet), ctx.getString(R.string.devicehub_sensor_light), ctx.getString(R.string.devicehub_sensor_proximity)};
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
        int[] types = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY};
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
        if (found == 0) {
            sensorLiveText.setText(sensorLiveText.getContext().getString(R.string.devicehub_no_common_sensors_found));
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
            output.setText(output.getContext().getString(R.string.compass_sensor_error));
        }
    }

    private void buildAltimeter(Context context, LinearLayout box) {
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.devicehub_altimeter));
        final TextView output = ToolViewFactory.makeOutput(box);
        output.setTextSize(28);
        output.setGravity(Gravity.CENTER);
        output.setText(output.getContext().getString(R.string.devicehub_starting));
        if (sensorManager == null) {
            output.setText(output.getContext().getString(R.string.compass_no_sensors_on_this_device));
            return;
        }
        Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressure == null) {
            output.setText(output.getContext().getString(R.string.devicehub_no_barometer_on_this_device));
            return;
        }
        startAltimeterListener(output, pressure);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        ctx = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.devicehub_device_hub));
        LinearLayout overview = addSectionCard(context, box, context.getString(R.string.devicehub_sec_overview));
        final TextView overText = addCardOutput(context, overview);
        LinearLayout power = addSectionCard(context, box, context.getString(R.string.devicehub_sec_battery));
        final TextView powerText = addCardOutput(context, power);
        final ProgressBar levelBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(100);
        power.addView(levelBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout compute = addSectionCard(context, box, context.getString(R.string.devicehub_sec_cpu));
        final TextView computeText = addCardOutput(context, compute);
        LinearLayout stor = addSectionCard(context, box, context.getString(R.string.devicehub_sec_storage));
        final TextView storText = addCardOutput(context, stor);
        LinearLayout disp = addSectionCard(context, box, context.getString(R.string.devicehub_sec_display));
        final TextView dispText = addCardOutput(context, disp);
        LinearLayout sens = addSectionCard(context, box, context.getString(R.string.devicehub_sec_sensors));
        sensorLiveText = addCardOutput(context, sens);
        sensorLiveText.setText(sensorLiveText.getContext().getString(R.string.devicehub_starting_sensors));
        final TextView altText = addCardOutput(context, sens);
        altText.setText(altText.getContext().getString(R.string.devicehub_barometer_starting));
        final Runnable refreshAll = () -> {
            try {
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                String o = Build.MANUFACTURER + " " + Build.MODEL + "\n"
                        + "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                        + Build.BRAND + " " + Build.DEVICE + "  •  " + Build.PRODUCT + "  •  " + Build.HARDWARE;
                overText.setText(o);
                String bs = readBatterySummary(context);
                powerText.setText(bs);
                try {
                    Intent bat = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int l = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int s = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    levelBar.setProgress(s <= 0 ? l : Math.round(l * 100f / s));
                } catch (Exception ignored) {}
                computeText.setText(readCpuSummary(context));
                storText.setText(readStorageSummary(context));
                dispText.setText(context.getString(R.string.devicehub_display_info,
                        String.valueOf(dm.widthPixels), String.valueOf(dm.heightPixels), String.valueOf(dm.densityDpi),
                        new DecimalFormat("0.0").format(dm.xdpi), new DecimalFormat("0.0").format(dm.ydpi),
                        String.valueOf(dm.density)));
                if (sensorManager != null) {
                    List<Sensor> all = sensorManager.getSensorList(Sensor.TYPE_ALL);
                    StringBuilder sl = new StringBuilder();
                    sl.append(context.getString(R.string.devicehub_sensor_count, all.size()));
                    for (int i = 0; i < Math.min(6, all.size()); i++) {
                        if (i > 0) sl.append(", ");
                        sl.append(all.get(i).getName());
                    }
                    if (all.size() > 6) sl.append(" …");
                    sensorLiveText.setText(sensorLiveText.getContext().getString(R.string.devicehub_starting_live_feed) + sl);
                }
            } catch (Exception e) {
                overText.setText(overText.getContext().getString(R.string.devicehub_unavailable));
            }
        };
        refreshAll.run();
        try {
            sensorLatest = new float[5][];
            startSensorsListener();
        } catch (Exception ignored) {}
        try {
            if (sensorManager != null) {
                Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (pressure != null) {
                    altText.setText(altText.getContext().getString(R.string.devicehub_barometer_present_tap_below_fo));
                    MaterialButton altBtn = new MaterialButton(context);
                    altBtn.setText(altBtn.getContext().getString(R.string.devicehub_open_altimeter));
                    sens.addView(altBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    altBtn.setOnClickListener(v -> {
                        box.removeAllViews();
                        buildAltimeter(context, box);
                    });
                } else {
                    altText.setText(altText.getContext().getString(R.string.devicehub_no_barometer_on_this_device));
                }
            }
        } catch (Exception ignored) {}
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton refreshBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.devicehub_refresh_all), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.devicehub_copy_report), 1f);
        refreshBtn.setOnClickListener(v -> {
            refreshAll.run();
            try { startSensorsListener(); } catch (Exception ignored) {}
            ToolViewFactory.toast(context, context.getString(R.string.devicehub_refreshed));
        });
        copyBtn.setOnClickListener(v -> ToolViewFactory.copyText(context, context.getString(R.string.devicehub_device_hub_2),
                overText.getText() + "\n\n" + powerText.getText() + "\n\n" + computeText.getText() + "\n\n" + storText.getText() + "\n\n" + dispText.getText()));
        return box;
    }

    @Override
    public void onDestroy() {
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
        ctx = null;
    }
}
