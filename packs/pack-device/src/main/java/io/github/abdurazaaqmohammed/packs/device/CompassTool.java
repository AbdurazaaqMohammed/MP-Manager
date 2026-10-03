package io.github.abdurazaaqmohammed.packs.device;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildCompass().
 * Owns its sensor listener; unregistered in onDestroy().
 */
public class CompassTool extends BaseToolPlugin {

    private SensorManager sensorManager;
    private SensorEventListener listener;
    private float[] accelValues;
    private float[] magnetValues;
    private CompassView compassView;
    private TextView compassText;

    public CompassTool() {
        super("compass", R.string.compass_title, R.string.compass_sub,  ToolCategories.DEVICE);
    }

    private static String cardinalFor(float az) {
        String[] names = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = Math.round(az / 45f) % 8;
        return names[idx];
    }

    private void startSensors() {
        if (sensorManager == null) {
            if (compassText != null) {
                compassText.setText(compassText.getContext().getString(R.string.compass_no_sensors_on_this_device));
            }
            return;
        }
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accel == null || magnet == null) {
            if (compassText != null) {
                compassText.setText(compassText.getContext().getString(R.string.compass_compass_sensor_not_available));
            }
            return;
        }
        try {
            if (listener != null) {
                sensorManager.unregisterListener(listener);
            }
        } catch (Exception ignored) {
        }
        accelValues = null;
        magnetValues = null;
        listener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    accelValues = event.values.clone();
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    magnetValues = event.values.clone();
                }
                if (accelValues != null && magnetValues != null) {
                    float[] r = new float[9];
                    float[] orient = new float[3];
                    if (SensorManager.getRotationMatrix(r, null, accelValues, magnetValues)) {
                        SensorManager.getOrientation(r, orient);
                        float az = (float) Math.toDegrees(orient[0]);
                        if (az < 0) {
                            az += 360f;
                        }
                        if (compassView != null) {
                            compassView.setBearing(az);
                        }
                        if (compassText != null) {
                            compassText.setText(Math.round(az) + " deg  " + cardinalFor(az));
                        }
                    }
                }
            }
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        try {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            if (compassText != null) {
                compassText.setText(compassText.getContext().getString(R.string.compass_sensor_error));
            }
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.compass_compass));
        compassView = new CompassView(context);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ToolViewFactory.dp(context, 260), ToolViewFactory.dp(context, 260));
        cp.gravity = Gravity.CENTER;
        int m8 = ToolViewFactory.dp(context, 8);
        cp.setMargins(0, m8, 0, m8);
        box.addView(compassView, cp);
        compassText = ToolViewFactory.makeOutput(box);
        compassText.setText(compassText.getContext().getString(R.string.compass_waiting_for_sensors));
        compassText.setGravity(Gravity.CENTER);
        MaterialButton calBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.compass_restart_sensors));
        calBtn.setOnClickListener(v -> startSensors());
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
        compassView = null;
        compassText = null;
    }

    private static class CompassView extends View {
        private float bearing = 0f;
        private int bgColor = Color.WHITE;
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public CompassView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            int tick = Color.parseColor("#5F6368");
            int ink = Color.parseColor("#202124");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                tick = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, tick);
                ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ink);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            circlePaint.setColor(primary);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(8f);
            tickPaint.setColor(tick);
            tickPaint.setStrokeWidth(4f);
            needlePaint.setColor(Color.parseColor("#D93025"));
            needlePaint.setStrokeWidth(10f);
            textPaint.setColor(ink);
            textPaint.setTextSize(44f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        void setBearing(float b) {
            bearing = b;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - 20f;
            canvas.drawColor(bgColor);
            canvas.drawCircle(cx, cy, radius, circlePaint);
            canvas.save();
            canvas.rotate(-bearing, cx, cy);
            String[] labels = new String[]{"N", "E", "S", "W"};
            for (int i = 0; i < 360; i += 15) {
                double rad = Math.toRadians(i);
                float len = i % 90 == 0 ? 50 : 28;
                float x1 = cx + (float) (Math.sin(rad) * (radius - len));
                float y1 = cy - (float) (Math.cos(rad) * (radius - len));
                float x2 = cx + (float) (Math.sin(rad) * radius);
                float y2 = cy - (float) (Math.cos(rad) * radius);
                canvas.drawLine(x1, y1, x2, y2, tickPaint);
            }
            for (int i = 0; i < 4; i++) {
                double rad = Math.toRadians(i * 90);
                float tx = cx + (float) (Math.sin(rad) * (radius - 90));
                float ty = cy - (float) (Math.cos(rad) * (radius - 90)) + 16;
                canvas.drawText(labels[i], tx, ty, textPaint);
            }
            canvas.restore();
            canvas.drawLine(cx, cy, cx, cy - radius + 60, needlePaint);
            canvas.drawCircle(cx, cy, 14, needlePaint);
        }
    }
}
