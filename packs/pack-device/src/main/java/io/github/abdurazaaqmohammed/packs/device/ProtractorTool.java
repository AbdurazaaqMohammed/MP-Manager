package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildProtractor().
 */
public class ProtractorTool extends BaseToolPlugin {

    public ProtractorTool() {
        super("protractor", "Protractor", "Measure angles with touch", ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Protractor");
        ToolViewFactory.addLabel(box, "Touch the dial to measure an angle from 0 to 180 degrees.");
        ProtractorView protractorView = new ProtractorView(context);
        box.addView(protractorView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 260)));
        TextView protractorText = ToolViewFactory.makeOutput(box);
        protractorText.setText("Angle: 0 deg");
        protractorView.setListener(deg -> protractorText.setText("Angle: " + new DecimalFormat("0.0").format(deg) + " deg"));
        MaterialButton resetBtn = ToolViewFactory.makeButton(box, "Reset");
        resetBtn.setOnClickListener(v -> {
            protractorView.setAngle(0f);
            protractorText.setText("Angle: 0 deg");
        });
        return box;
    }

    private static class ProtractorView extends View {
        interface AngleListener {
            void onAngle(float deg);
        }
        private float angle = 0f;
        private AngleListener listener;
        private int bgColor = Color.WHITE;
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ProtractorView(Context context) {
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
            arcPaint.setColor(primary);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(6f);
            tickPaint.setColor(tick);
            tickPaint.setStrokeWidth(3f);
            needlePaint.setColor(Color.parseColor("#D93025"));
            needlePaint.setStrokeWidth(8f);
            textPaint.setColor(ink);
            textPaint.setTextSize(30f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        void setListener(AngleListener l) {
            listener = l;
        }
        void setAngle(float a) {
            angle = Math.max(0f, Math.min(180f, a));
            invalidate();
        }
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float cx = getWidth() / 2f;
                float cy = getHeight() - 40f;
                float dx = event.getX() - cx;
                float dy = cy - event.getY();
                double deg = Math.toDegrees(Math.atan2(dy, dx));
                if (deg < 0) {
                    deg = 0;
                }
                if (deg > 180) {
                    deg = 180;
                }
                angle = (float) deg;
                invalidate();
                if (listener != null) {
                    listener.onAngle(angle);
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);
            float cx = getWidth() / 2f;
            float cy = getHeight() - 40f;
            float radius = Math.min(getWidth() / 2f - 30f, getHeight() - 80f);
            canvas.drawLine(40, cy, getWidth() - 40, cy, tickPaint);
            for (int d = 0; d <= 180; d += 5) {
                double rad = Math.toRadians(d);
                float inner = radius - (d % 30 == 0 ? 60 : (d % 10 == 0 ? 45 : 28));
                float x1 = cx + (float) (Math.cos(rad) * inner);
                float y1 = cy - (float) (Math.sin(rad) * inner);
                float x2 = cx + (float) (Math.cos(rad) * radius);
                float y2 = cy - (float) (Math.sin(rad) * radius);
                canvas.drawLine(x1, y1, x2, y2, tickPaint);
                if (d % 30 == 0) {
                    float tx = cx + (float) (Math.cos(rad) * (radius - 90));
                    float ty = cy - (float) (Math.sin(rad) * (radius - 90)) + 10;
                    canvas.drawText(String.valueOf(d), tx, ty, textPaint);
                }
            }
            double ar = Math.toRadians(angle);
            float nx = cx + (float) (Math.cos(ar) * radius);
            float ny = cy - (float) (Math.sin(ar) * radius);
            canvas.drawLine(cx, cy, nx, ny, needlePaint);
            canvas.drawCircle(cx, cy, 12, needlePaint);
        }
    }
}
