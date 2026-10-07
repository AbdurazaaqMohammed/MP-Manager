package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildLevel().
 */
public class LevelTool extends BaseToolPlugin {

    private SensorManager sensorManager;
    private SensorEventListener listener;
    private LevelView levelView;
    private TextView levelText;

    public LevelTool() {
        super("level", "Bubble Level", "Check surface level", ToolCategories.DEVICE);
    }

    private void startSensors() {
        if (sensorManager == null) {
            if (levelText != null) {
                levelText.setText(PackRes.str("device", R.string.s_no_sensors_on_this_device, "No sensors on this device"));
            }
            return;
        }
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel == null) {
            if (levelText != null) {
                levelText.setText(PackRes.str("device", R.string.s_accelerometer_not_available, "Accelerometer not available"));
            }
            return;
        }
        try {
            if (listener != null) {
                sensorManager.unregisterListener(listener);
            }
        } catch (Exception ignored) {
        }
        listener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                double pitch = Math.toDegrees(Math.atan2(-x, Math.sqrt(y * y + z * z)));
                double roll = Math.toDegrees(Math.atan2(y, z));
                if (levelView != null) {
                    levelView.setTilt((float) pitch, (float) roll);
                }
                if (levelText != null) {
                    boolean flat = Math.abs(pitch) < 1.5 && Math.abs(roll) < 1.5;
                    levelText.setText("Pitch " + new DecimalFormat("0.0").format(pitch) + "  Roll " + new DecimalFormat("0.0").format(roll) + (flat ? "  LEVEL" : ""));
                }
            }
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        try {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            if (levelText != null) {
                levelText.setText(PackRes.str("device", R.string.s_sensor_error, "Sensor error"));
            }
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("device", R.string.s_bubble_level, "Bubble Level"));
        levelView = new LevelView(context);
        box.addView(levelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 220)));
        levelText = ToolViewFactory.makeOutput(box);
        levelText.setText(PackRes.str("device", R.string.s_waiting_for_sensors, "Waiting for sensors"));
        levelText.setGravity(Gravity.CENTER);
        startSensors();
        return box;
    }

    @Override
    public void onDestroy() {
        try {
            if (sensorManager != null && listener != null) {
                sensorManager.unregisterListener(listener);
            }
        } catch (Exception ignored) {
        }
        listener = null;
        levelView = null;
        levelText = null;
    }

    private static class LevelView extends View {
        private float pitch = 0f;
        private float roll = 0f;
        private int bgColor = Color.WHITE;
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public LevelView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            ringPaint.setColor(primary);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(6f);
            bubblePaint.setColor(Color.parseColor("#D93025"));
        }
        void setTilt(float p, float r) {
            pitch = p;
            roll = r;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(cx, cy) - 24f;
            canvas.drawCircle(cx, cy, maxR, ringPaint);
            canvas.drawCircle(cx, cy, maxR / 3f, ringPaint);
            canvas.drawCircle(cx, cy, 10, ringPaint);
            float bx = cx + Math.max(-1f, Math.min(1f, roll / 20f)) * maxR;
            float by = cy + Math.max(-1f, Math.min(1f, pitch / 20f)) * maxR;
            float dx = bx - cx;
            float dy = by - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > maxR - 30) {
                bx = cx + dx / dist * (maxR - 30);
                by = cy + dy / dist * (maxR - 30);
            }
            canvas.drawCircle(bx, by, 30, bubblePaint);
        }
    }
}
