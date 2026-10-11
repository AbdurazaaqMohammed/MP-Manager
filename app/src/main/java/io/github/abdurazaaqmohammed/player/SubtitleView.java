package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class SubtitleView extends View {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String currentText = "";
    private float textSize = 42f;
    private int textColor = Color.WHITE;
    private int bgColor = 0x99000000;
    private float bottomMargin = 0.10f;
    private int maxWidthPercent = 90;

    public SubtitleView(Context context) { super(context); init(); }
    public SubtitleView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public SubtitleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setSubpixelText(true);
        textPaint.setTextSize(textSize);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void setSubtitleText(String text) {
        if (text == null) text = "";
        if (!text.equals(currentText)) {
            currentText = text;
            postInvalidate();
        }
    }

    public void setTextSize(float size) { this.textSize = size; textPaint.setTextSize(size); postInvalidate(); }
    public void setTextColor(int color) { this.textColor = color; textPaint.setColor(color); postInvalidate(); }
    public void setBackgroundColor(int color) { this.bgColor = color; bgPaint.setColor(color); postInvalidate(); }
    public void setBottomMargin(float margin) { this.bottomMargin = margin; postInvalidate(); }
    public void setMaxWidthPercent(int percent) { this.maxWidthPercent = percent; postInvalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentText.isEmpty()) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float maxTextWidth = w * maxWidthPercent / 100f;
        float padding = textSize * 0.4f;

        String[] manualLines = currentText.split("\n");
        java.util.List<String> wrapped = new java.util.ArrayList<>();
        for (String line : manualLines) wrapLine(line, maxTextWidth - padding * 2, wrapped);
        if (wrapped.isEmpty()) return;

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) * 1.15f;
        float totalHeight = lineHeight * wrapped.size();
        float bottomY = h - (h * bottomMargin) - padding;
        float startY = bottomY - totalHeight + lineHeight;

        float maxLineWidth = 0;
        for (String line : wrapped) {
            float lw = textPaint.measureText(line);
            if (lw > maxLineWidth) maxLineWidth = lw;
        }

        float bgLeft = (w - maxLineWidth) / 2f - padding;
        float bgRight = (w + maxLineWidth) / 2f + padding;
        float bgTop = startY + fm.ascent - padding * 0.5f;
        float bgBottom = startY + fm.descent + (wrapped.size() - 1) * lineHeight + padding * 0.5f;

        RectF bgRect = new RectF(bgLeft, bgTop, bgRight, bgBottom);
        float radius = textSize * 0.2f;
        canvas.drawRoundRect(bgRect, radius, radius, bgPaint);

        float y = startY;
        for (String line : wrapped) {
            canvas.drawText(line, w / 2f, y, textPaint);
            y += lineHeight;
        }
    }

    private void wrapLine(String line, float maxWidth, java.util.List<String> out) {
        if (line.isEmpty()) { out.add(""); return; }
        if (textPaint.measureText(line) <= maxWidth) { out.add(line); return; }
        String[] words = line.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            String test = sb.length() == 0 ? word : sb + " " + word;
            if (textPaint.measureText(test) <= maxWidth) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(word);
            } else {
                if (sb.length() > 0) out.add(sb.toString());
                sb = new StringBuilder(word);
            }
        }
        if (sb.length() > 0) out.add(sb.toString());
    }
}
