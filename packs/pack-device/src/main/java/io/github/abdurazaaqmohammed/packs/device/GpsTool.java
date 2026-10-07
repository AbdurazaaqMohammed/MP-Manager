package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildGps().
 *
 * <p>Note: like the flashlight plugin, this cannot auto-retry after the
 * location grant (the host owns the permission callback); the user taps
 * Start again instead.
 */
public class GpsTool extends BaseToolPlugin {

    private LocationManager locationManager;
    private LocationListener gpsListener;
    private boolean gpsRunning;
    private double gpsMax;
    private double gpsSum;
    private int gpsCount;
    private TextView gpsText;

    public GpsTool() {
        super("gps", "GPS Speedometer", "Track live GPS speed", ToolCategories.DEVICE);
    }

    private void startGpsUpdates(Context context) {
        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }
        if (locationManager == null) {
            ToolViewFactory.toast(context, PackRes.str("device", R.string.s_location_unavailable, "Location unavailable"));
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            try {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 9003);
            } catch (Exception ignored) {
            }
            ToolViewFactory.toast(context, PackRes.str("device", R.string.s_location_permission_needed_then_tap_start, "Location permission needed, then tap Start"));
            return;
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_enable_gps_first, "Enable GPS first"));
            }
        } catch (Exception ignored) {
        }
        if (gpsListener == null) {
            gpsListener = new LocationListener() {
                public void onLocationChanged(Location location) {
                    if (!gpsRunning) {
                        return;
                    }
                    try {
                        float ms = location.hasSpeed() ? location.getSpeed() : 0f;
                        double kmh = ms * 3.6;
                        if (kmh > gpsMax) {
                            gpsMax = kmh;
                        }
                        gpsSum += kmh;
                        gpsCount++;
                        DecimalFormat df = new DecimalFormat("0.0");
                        if (gpsText != null) {
                            gpsText.setText(df.format(kmh) + " km/h\nMax " + df.format(gpsMax) + "  Avg " + df.format(gpsCount == 0 ? 0 : gpsSum / gpsCount));
                        }
                    } catch (Exception ignored) {
                    }
                }
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }
                public void onProviderEnabled(String provider) {
                }
                public void onProviderDisabled(String provider) {
                }
            };
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, gpsListener);
        } catch (Exception e) {
            ToolViewFactory.toast(context, PackRes.str("device", R.string.s_gps_failed, "GPS failed"));
        }
    }

    private void stopGpsUpdates() {
        gpsRunning = false;
        try {
            if (locationManager != null && gpsListener != null) {
                locationManager.removeUpdates(gpsListener);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        gpsRunning = false;
        gpsMax = 0;
        gpsSum = 0;
        gpsCount = 0;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("device", R.string.s_gps_speedometer, "GPS Speedometer"));
        gpsText = ToolViewFactory.makeOutput(box);
        gpsText.setTextSize(36);
        gpsText.setGravity(Gravity.CENTER);
        gpsText.setText(PackRes.str("device", R.string.s_0_0_km_h, "0.0 km/h"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton startBtn = ToolViewFactory.makeRowButton(row, PackRes.str("device", R.string.s_start, "Start"), 1f);
        MaterialButton stopBtn = ToolViewFactory.makeRowButton(row, PackRes.str("device", R.string.s_stop, "Stop"), 1f);
        MaterialButton resetBtn = ToolViewFactory.makeRowButton(row, PackRes.str("device", R.string.s_reset, "Reset"), 1f);
        startBtn.setOnClickListener(v -> {
            gpsRunning = true;
            startGpsUpdates(context);
        });
        stopBtn.setOnClickListener(v -> stopGpsUpdates());
        resetBtn.setOnClickListener(v -> {
            gpsMax = 0;
            gpsSum = 0;
            gpsCount = 0;
            gpsText.setText(PackRes.str("device", R.string.s_0_0_km_h, "0.0 km/h"));
        });
        return box;
    }

    @Override
    public void onDestroy() {
        stopGpsUpdates();
        gpsText = null;
        gpsListener = null;
        locationManager = null;
    }
}
