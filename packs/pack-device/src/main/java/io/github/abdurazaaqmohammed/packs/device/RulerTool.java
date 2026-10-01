package io.github.abdurazaaqmohammed.packs.device;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildRuler().
 */
public class RulerTool extends BaseToolPlugin {

    private int rulerMode;
    private float rulerCal = 1.0f;
    private RulerView rulerView;
    private TextView rulerInfo;

    public RulerTool() {
        super("ruler", "Ruler", "Measure in cm and inches", ToolCategories.DEVICE);
    }

    private void updateRulerInfo(Context context) {
        if (rulerInfo == null || rulerView == null) {
            return;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float widthPx = (float) rulerView.getMeasuredWidth();
        if (widthPx <= 0) {
            widthPx = (float) dm.widthPixels - ToolViewFactory.dp(context, 32);
        }
        float xdpi = dm.xdpi <= 0 ? 320f : dm.xdpi;
        float inches = widthPx / xdpi * rulerCal;
        if (rulerMode == 0) {
            rulerInfo.setText("Screen width: " + new DecimalFormat("0.0").format(inches * 2.54) + " cm");
        } else {
            rulerInfo.setText("Screen width: " + new DecimalFormat("0.00").format(inches) + " inch");
        }
        rulerView.post(() -> updateRulerInfoText(context));
    }

    private void updateRulerInfoText(Context context) {
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float widthPx = (float) rulerView.getWidth();
            if (widthPx <= 0) {
                return;
            }
            float xdpi = dm.xdpi <= 0 ? 320f : dm.xdpi;
            float inches = widthPx / xdpi * rulerCal;
            if (rulerMode == 0) {
                rulerInfo.setText("Screen width: " + new DecimalFormat("0.0").format(inches * 2.54) + " cm");
            } else {
                rulerInfo.setText("Screen width: " + new DecimalFormat("0.00").format(inches) + " inch");
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        try {
            if (context instanceof Activity) {
                ((Activity) context).setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        } catch (Exception ignored) {
        }
        rulerMode = 0;
        rulerCal = 1.0f;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Ruler");
        ToolViewFactory.addLabel(box, "Place object along the top edge. Toggle units and calibrate with the slider.");
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton cmBtn = new RadioButton(context);
        cmBtn.setId(View.generateViewId());
        cmBtn.setText("cm");
        RadioButton inchBtn = new RadioButton(context);
        inchBtn.setId(View.generateViewId());
        inchBtn.setText("inch");
        group.addView(cmBtn);
        group.addView(inchBtn);
        group.check(rulerMode == 1 ? inchBtn.getId() : cmBtn.getId());
        box.addView(group);
        rulerView = new RulerView(context);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 180));
        int m8 = ToolViewFactory.dp(context, 8);
        rp.setMargins(0, m8, 0, m8);
        box.addView(rulerView, rp);
        rulerInfo = ToolViewFactory.makeOutput(box);
        ToolViewFactory.addLabel(box, "Calibration");
        SeekBar calBar = new SeekBar(context);
        calBar.setMax(40);
        calBar.setProgress(20);
        box.addView(calBar);
        try {
            float saved = context.getSharedPreferences("tools", Context.MODE_PRIVATE).getFloat("ruler_cal", 1.0f);
            rulerCal = saved;
            calBar.setProgress(Math.round((saved - 0.8f) * 100.0f));
        } catch (Exception ignored) {
        }
        updateRulerInfo(context);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            rulerMode = (checkedId == inchBtn.getId()) ? 1 : 0;
            rulerView.setMode(rulerMode);
            rulerView.setCal(rulerCal);
            updateRulerInfo(context);
        });
        calBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                rulerCal = 0.8f + (progress / 100.0f);
                rulerView.setCal(rulerCal);
                updateRulerInfo(context);
                try {
                    context.getSharedPreferences("tools", Context.MODE_PRIVATE).edit().putFloat("ruler_cal", rulerCal).apply();
                } catch (Exception ignored) {
                }
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        rulerView.setMode(rulerMode);
        rulerView.setCal(rulerCal);
        return box;
    }

    private static class RulerView extends View {
        private int mode = 0;
        private float cal = 1.0f;
        private int bgColor = Color.WHITE;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public RulerView(Context context) {
            super(context);
            int primary = Color.parseColor("#1B73E8");
            int ink = Color.parseColor("#202124");
            try {
                primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, primary);
                ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ink);
                bgColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE);
            } catch (Exception ignored) {
            }
            paint.setColor(primary);
            paint.setStrokeWidth(4f);
            textPaint.setColor(ink);
            textPaint.setTextSize(32f);
        }
        void setMode(int m) {
            mode = m;
            invalidate();
        }
        void setCal(float c) {
            cal = c;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(bgColor);
            float xdpi = getResources().getDisplayMetrics().xdpi;
            if (xdpi <= 0) {
                xdpi = 320f;
            }
            float pxPerUnit;
            int maxUnits;
            if (mode == 0) {
                pxPerUnit = xdpi / 2.54f * cal;
                maxUnits = (int) (getWidth() / pxPerUnit) + 1;
                for (int cm = 0; cm <= maxUnits; cm++) {
                    float x = cm * pxPerUnit;
                    canvas.drawLine(x, 0, x, 90, paint);
                    canvas.drawText(String.valueOf(cm), x + 6, 120, textPaint);
                    for (int mm = 1; mm < 10; mm++) {
                        float xm = x + mm * pxPerUnit / 10f;
                        if (xm > getWidth()) {
                            break;
                        }
                        float h = mm == 5 ? 70 : 45;
                        canvas.drawLine(xm, 0, xm, h, paint);
                    }
                }
            } else {
                pxPerUnit = xdpi * cal;
                maxUnits = (int) (getWidth() / pxPerUnit) + 1;
                for (int inch = 0; inch <= maxUnits; inch++) {
                    float x = inch * pxPerUnit;
                    canvas.drawLine(x, 0, x, 90, paint);
                    canvas.drawText(String.valueOf(inch), x + 6, 120, textPaint);
                    for (int q = 1; q < 16; q++) {
                        float xq = x + q * pxPerUnit / 16f;
                        if (xq > getWidth()) {
                            break;
                        }
                        float h = q % 8 == 0 ? 70 : (q % 4 == 0 ? 60 : 45);
                        canvas.drawLine(xq, 0, xq, h, paint);
                    }
                }
            }
            canvas.drawLine(0, getHeight() - 10, getWidth(), getHeight() - 10, paint);
        }
    }
}
