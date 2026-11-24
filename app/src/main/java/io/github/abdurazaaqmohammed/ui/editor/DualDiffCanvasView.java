package io.github.abdurazaaqmohammed.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.difflib.text.DiffRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.util.text.TextDiffEngine;

/**
 * High-performance dual-pane diff view modeled after MT Manager's comparator:
 * renders only visible wrapped lines directly on Canvas without blank spacer
 * rows, synchronizes matching blocks smoothly, draws cubic Bezier ribbons
 * in a wide center gutter, differentiates CHANGE/DELETE/INSERT colors,
 * highlights inline character differences lazily, highlights the active jumped
 * hunk, and renders a side diff minimap scrollbar.
 */
public class DualDiffCanvasView extends View {

    private static final int MAX_RENDERED_CHARS_PER_LINE = 4096;
    private static final int MINIMAP_BUCKETS = 360;
    private static final int MAX_INLINE_DP_LEN = 256;

    public static final class PaneLine {
        public final String text;
        public final int lineNumber;

        public PaneLine(String text, int lineNumber) {
            this.text = sanitizeDisplayText(text);
            this.lineNumber = lineNumber;
        }
    }

    /**
     * Replaces NUL and control characters that Skia either ignores (creating huge
     * invisible wrapped blocks on binary files) or renders poorly, while collapsing
     * long NUL runs so binary headers remain readable next to diffs.
     */
    static String sanitizeDisplayText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        int len = Math.min(raw.length(), MAX_RENDERED_CHARS_PER_LINE);
        boolean needsChange = raw.length() > MAX_RENDERED_CHARS_PER_LINE;
        if (!needsChange) {
            for (int i = 0; i < len; i++) {
                char c = raw.charAt(i);
                if (c < 0x20 || c == 0x7F) {
                    needsChange = true;
                    break;
                }
            }
        }
        if (!needsChange) return raw;

        StringBuilder sb = new StringBuilder(Math.min(len, 512));
        int i = 0;
        while (i < len) {
            char c = raw.charAt(i);
            if (c == '\0') {
                int j = i + 1;
                while (j < len && raw.charAt(j) == '\0') j++;
                int count = j - i;
                if (count >= 16) {
                    sb.append("[00×").append(count).append(']');
                } else {
                    for (int k = 0; k < count; k++) sb.append('\uFFFD');
                }
                i = j;
                continue;
            }
            if (c == '\t') {
                sb.append("  ");
            } else if (c < 0x20 || c == 0x7F) {
                sb.append('\uFFFD');
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }

    private static final class InlineSpans {
        final int[] leftRanges;
        final int[] rightRanges;

        InlineSpans(int[] leftRanges, int[] rightRanges) {
            this.leftRanges = leftRanges;
            this.rightRanges = rightRanges;
        }
    }

    private List<PaneLine> leftLines = Collections.emptyList();
    private List<PaneLine> rightLines = Collections.emptyList();
    private List<TextDiffEngine.DiffHunk> hunks = Collections.emptyList();

    private int[] leftLineStartRow = new int[]{0};
    private int[] rightLineStartRow = new int[]{0};
    private int[] pairedRightForLeft = new int[0];
    private int[] pairedLeftForRight = new int[0];
    private int[] hunkScrollStartRow = new int[]{0};
    private int[] hunkLeftStartRow = new int[0];
    private int[] hunkLeftEndRow = new int[0];
    private int[] hunkRightStartRow = new int[0];
    private int[] hunkRightEndRow = new int[0];

    private final LruCache<Long, InlineSpans> inlineDiffCache = new LruCache<>(256);

    /** Precomputed bucket colors for O(1) minimap drawing on huge files. */
    private final int[] minimapBuckets = new int[MINIMAP_BUCKETS];

    private int totalTrackRows = 0;
    private int charsPerLine = 28;
    private float rowHeight;
    private float textBaselineOffset;
    private float numBaselineOffset;
    private float gutterWidth;
    private float numColWidth;
    private float panePaddingH;
    private float minimapWidth;
    private float activeAccentBarWidth;

    private boolean wordWrap = true;
    private float scrollY = 0f;
    private float maxScrollY = 0f;
    private float leftScrollX = 0f;
    private float rightScrollX = 0f;
    private float maxLeftScrollX = 0f;
    private float maxRightScrollX = 0f;
    private int maxLeftLineChars = 0;
    private int maxRightLineChars = 0;
    private boolean activeTouchLeftPane = true;
    private boolean flingLeftPane = true;

    private int highlightedHunkIndex = -1;
    private String searchQuery = "";
    private boolean searchMatchCase = false;
    private boolean draggingMinimap = false;

    private final Paint bgPaint = new Paint();
    private final Paint gutterBgPaint = new Paint();
    private final Paint equalBlockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint equalBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint changeBlockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint changeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inlineDiffPaint = new Paint();
    private final Paint deleteBlockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deleteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint insertBlockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint insertBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeAccentPaint = new Paint();
    private final Paint minimapBgPaint = new Paint();
    private final Paint minimapMarkerPaint = new Paint();
    private final Paint minimapThumbPaint = new Paint();
    private final Paint searchHighlightPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineNumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Path bezierPath = new Path();

    private final OverScroller scroller;
    private final GestureDetector gestureDetector;

    private int colorChangeMarker;
    private int colorDeleteMarker;
    private int colorInsertMarker;

    public DualDiffCanvasView(Context context) {
        this(context, null);
    }

    public DualDiffCanvasView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DualDiffCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                scroller.forceFinished(true);
                float centerX = (getWidth() - minimapWidth) * 0.5f;
                activeTouchLeftPane = e.getX() <= centerX;
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (!draggingMinimap) {
                    if (!wordWrap && Math.abs(distanceX) > Math.abs(distanceY) * 1.15f) {
                        scrollPaneX(activeTouchLeftPane, distanceX);
                    } else if (!wordWrap && Math.abs(distanceY) <= Math.abs(distanceX) * 1.15f) {
                        scrollPaneX(activeTouchLeftPane, distanceX);
                        scrollByY(distanceY);
                    } else {
                        scrollByY(distanceY);
                    }
                }
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (!draggingMinimap) {
                    if (!wordWrap) {
                        flingLeftPane = activeTouchLeftPane;
                        float curX = flingLeftPane ? leftScrollX : rightScrollX;
                        float maxX = flingLeftPane ? maxLeftScrollX : maxRightScrollX;
                        float vx = Math.abs(velocityX) > Math.abs(velocityY) * 0.65f ? -velocityX : 0f;
                        float vy = Math.abs(velocityY) > Math.abs(velocityX) * 0.65f ? -velocityY : 0f;
                        scroller.fling((int) curX, (int) scrollY, (int) vx, (int) vy,
                                0, (int) Math.max(0f, maxX), 0, (int) Math.max(0f, maxScrollY));
                    } else {
                        scroller.fling(0, (int) scrollY, 0, (int) -velocityY,
                                0, 0, 0, (int) Math.max(0f, maxScrollY));
                    }
                    postInvalidateOnAnimation();
                }
                return true;
            }
        });

        initPaints(context);
    }

    private void initPaints(Context context) {
        int nightMask = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkTheme = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11.5f, context.getResources().getDisplayMetrics());
        float numSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 9.5f, context.getResources().getDisplayMetrics());
        panePaddingH = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, context.getResources().getDisplayMetrics());
        minimapWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, context.getResources().getDisplayMetrics());
        activeAccentBarWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 3f, context.getResources().getDisplayMetrics());

        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(textSizePx);

        lineNumPaint.setTypeface(Typeface.MONOSPACE);
        lineNumPaint.setTextSize(numSizePx);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        rowHeight = Math.max(18f, (float) Math.ceil((fm.descent - fm.ascent) * 1.25f));
        textBaselineOffset = (rowHeight - (fm.descent + fm.ascent)) * 0.5f;

        Paint.FontMetrics nfm = lineNumPaint.getFontMetrics();
        numBaselineOffset = (rowHeight - (nfm.descent + nfm.ascent)) * 0.5f;

        if (isDarkTheme) {
            bgPaint.setColor(0xFF1A1B1E);
            gutterBgPaint.setColor(0xFF121316);
            equalBlockPaint.setColor(0xFF26282E);
            equalBorderPaint.setColor(0xFF33363E);

            changeBlockPaint.setColor(0xFF26486B);
            changeBorderPaint.setColor(0xFF3F74AA);
            inlineDiffPaint.setColor(0xFF3E78B5);
            deleteBlockPaint.setColor(0xFF59252A);
            deleteBorderPaint.setColor(0xFFA8424B);
            insertBlockPaint.setColor(0xFF204D36);
            insertBorderPaint.setColor(0xFF388E60);

            activeBorderPaint.setColor(0xFFFFCA28);
            activeAccentPaint.setColor(0xFFFFB300);

            minimapBgPaint.setColor(0xFF141518);
            minimapThumbPaint.setColor(0x44FFFFFF);
            colorChangeMarker = 0xFF4C8ED9;
            colorDeleteMarker = 0xFFE5534B;
            colorInsertMarker = 0xFF3FB950;

            searchHighlightPaint.setColor(0x66FFC107);
            textPaint.setColor(0xFFDCDFE4);
            lineNumPaint.setColor(0xFF8C929C);
            dividerPaint.setColor(0xFF2F3238);
        } else {
            bgPaint.setColor(0xFFFAFAFA);
            gutterBgPaint.setColor(0xFFE3E5E8);
            equalBlockPaint.setColor(0xFFF0F1F3);
            equalBorderPaint.setColor(0xFFD6D9DF);

            changeBlockPaint.setColor(0xFFD6E6F5);
            changeBorderPaint.setColor(0xFF6BA3D6);
            inlineDiffPaint.setColor(0xFF9AC4EC);
            deleteBlockPaint.setColor(0xFFFADAD8);
            deleteBorderPaint.setColor(0xFFD96B68);
            insertBlockPaint.setColor(0xFFD4F2DC);
            insertBorderPaint.setColor(0xFF4CA868);

            activeBorderPaint.setColor(0xFFE65100);
            activeAccentPaint.setColor(0xFFFF8F00);

            minimapBgPaint.setColor(0xFFE9EBEE);
            minimapThumbPaint.setColor(0x33000000);
            colorChangeMarker = 0xFF2176D2;
            colorDeleteMarker = 0xFFD32F2F;
            colorInsertMarker = 0xFF2E7D32;

            searchHighlightPaint.setColor(0x88FFEB3B);
            textPaint.setColor(0xFF1F2328);
            lineNumPaint.setColor(0xFF656D76);
            dividerPaint.setColor(0xFFD0D7DE);
        }

        for (Paint p : new Paint[]{equalBorderPaint, changeBorderPaint, deleteBorderPaint, insertBorderPaint}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.25f);
        }
        activeBorderPaint.setStyle(Paint.Style.STROKE);
        activeBorderPaint.setStrokeWidth(3f);
        dividerPaint.setStrokeWidth(1f);
    }

    public void setWordWrap(boolean wordWrap) {
        if (this.wordWrap == wordWrap) return;
        this.wordWrap = wordWrap;
        this.leftScrollX = 0f;
        this.rightScrollX = 0f;
        scroller.forceFinished(true);
        rebuildLayoutTables();
        invalidate();
    }

    public boolean isWordWrap() {
        return wordWrap;
    }

    public void setData(List<PaneLine> left, List<PaneLine> right, List<TextDiffEngine.DiffHunk> diffHunks) {
        this.leftLines = left != null ? left : Collections.emptyList();
        this.rightLines = right != null ? right : Collections.emptyList();
        this.hunks = diffHunks != null ? diffHunks : Collections.emptyList();
        this.highlightedHunkIndex = -1;
        this.scrollY = 0f;
        this.leftScrollX = 0f;
        this.rightScrollX = 0f;
        this.maxLeftLineChars = maxLineLength(this.leftLines);
        this.maxRightLineChars = maxLineLength(this.rightLines);
        inlineDiffCache.evictAll();
        scroller.forceFinished(true);
        rebuildLayoutTables();
        invalidate();
    }

    private static int maxLineLength(List<PaneLine> lines) {
        int max = 0;
        for (int i = 0; i < lines.size(); i++) {
            int len = Math.min(lines.get(i).text.length(), MAX_RENDERED_CHARS_PER_LINE);
            if (len > max) max = len;
        }
        return max;
    }

    public void setSearchQuery(String query, boolean matchCase) {
        this.searchQuery = query != null ? query : "";
        this.searchMatchCase = matchCase;
        invalidate();
    }

    public void scrollToHunk(int hunkIndex) {
        if (hunkIndex < 0 || hunkIndex >= hunks.size()) return;
        this.highlightedHunkIndex = hunkIndex;
        float targetY = hunkScrollStartRow[hunkIndex] * rowHeight - Math.max(0f, getHeight() * 0.22f);
        setScrollYClamped(targetY);
    }

    public void scrollToLineNumber(int targetLineNumber) {
        if (leftLines.isEmpty() && rightLines.isEmpty()) return;
        int leftIdx = findLineIndexByNumber(leftLines, targetLineNumber);
        int leftRow = leftIdx < leftLineStartRow.length ? leftLineStartRow[leftIdx] : 0;
        int hunkIdx = findHunkForLeftRow(leftRow);
        if (hunkIdx >= 0 && hunkIdx < hunks.size()) {
            int lStart = hunkLeftStartRow[hunkIdx];
            int lLen = Math.max(1, hunkLeftEndRow[hunkIdx] - lStart);
            int tLen = (hunkIdx + 1 < hunkScrollStartRow.length ? hunkScrollStartRow[hunkIdx + 1] : totalTrackRows)
                    - hunkScrollStartRow[hunkIdx];
            float frac = (float) (leftRow - lStart) / (float) lLen;
            float targetTrack = hunkScrollStartRow[hunkIdx] + frac * tLen;
            setScrollYClamped(targetTrack * rowHeight);
        }
    }

    private static int findLineIndexByNumber(List<PaneLine> lines, int targetNumber) {
        int lo = 0, hi = lines.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int num = lines.get(mid).lineNumber;
            if (num < targetNumber) lo = mid + 1;
            else if (num > targetNumber) hi = mid - 1;
            else return mid;
        }
        return Math.min(lo, Math.max(0, lines.size() - 1));
    }

    private void setScrollYClamped(float y) {
        scroller.forceFinished(true);
        scrollY = Math.max(0f, Math.min(maxScrollY, y));
        invalidate();
    }

    private void scrollByY(float dy) {
        scrollY = Math.max(0f, Math.min(maxScrollY, scrollY + dy));
        postInvalidateOnAnimation();
    }

    private void scrollPaneX(boolean isLeft, float dx) {
        if (isLeft) {
            leftScrollX = Math.max(0f, Math.min(maxLeftScrollX, leftScrollX + dx));
        } else {
            rightScrollX = Math.max(0f, Math.min(maxRightScrollX, rightScrollX + dx));
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && w != oldw) {
            rebuildLayoutTables();
        } else {
            updateMaxScroll();
        }
    }

    private void rebuildLayoutTables() {
        int w = getWidth();
        if (w <= 0) return;

        int maxNum = Math.max(
                leftLines.isEmpty() ? 1 : leftLines.get(leftLines.size() - 1).lineNumber,
                rightLines.isEmpty() ? 1 : rightLines.get(rightLines.size() - 1).lineNumber);
        int digits = Math.max(2, String.valueOf(maxNum).length());
        float digitW = lineNumPaint.measureText("8");
        numColWidth = digitW * digits + panePaddingH * 2f;
        float centerBridgeMin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 44f, getResources().getDisplayMetrics());
        gutterWidth = Math.min(w * 0.36f, numColWidth * 2f + centerBridgeMin);
        float contentWidth = Math.max(1f, w - minimapWidth);
        float paneWidth = Math.max(1f, (contentWidth - gutterWidth) * 0.5f);
        float charWidth = Math.max(1f, textPaint.measureText("M"));
        charsPerLine = Math.max(10, (int) Math.floor((paneWidth - panePaddingH * 2f) / charWidth));

        leftLineStartRow = buildWrapOffsets(leftLines, charsPerLine, wordWrap);
        rightLineStartRow = buildWrapOffsets(rightLines, charsPerLine, wordWrap);

        int hCount = hunks.size();
        hunkScrollStartRow = new int[hCount + 1];
        hunkLeftStartRow = new int[hCount];
        hunkLeftEndRow = new int[hCount];
        hunkRightStartRow = new int[hCount];
        hunkRightEndRow = new int[hCount];

        int trackAcc = 0;
        for (int i = 0; i < hCount; i++) {
            TextDiffEngine.DiffHunk hunk = hunks.get(i);
            int lStart = leftLineStartRow[Math.min(hunk.leftStart, leftLines.size())];
            int lEnd = leftLineStartRow[Math.min(hunk.leftEnd, leftLines.size())];
            int rStart = rightLineStartRow[Math.min(hunk.rightStart, rightLines.size())];
            int rEnd = rightLineStartRow[Math.min(hunk.rightEnd, rightLines.size())];
            hunkLeftStartRow[i] = lStart;
            hunkLeftEndRow[i] = lEnd;
            hunkRightStartRow[i] = rStart;
            hunkRightEndRow[i] = rEnd;
            hunkScrollStartRow[i] = trackAcc;
            int trackSpan = Math.max(1, Math.max(lEnd - lStart, rEnd - rStart));
            trackAcc += trackSpan;
        }
        hunkScrollStartRow[hCount] = trackAcc;
        totalTrackRows = trackAcc;

        pairedRightForLeft = new int[leftLines.size()];
        pairedLeftForRight = new int[rightLines.size()];
        Arrays.fill(pairedRightForLeft, -1);
        Arrays.fill(pairedLeftForRight, -1);
        for (int i = 0; i < hCount; i++) {
            TextDiffEngine.DiffHunk hunk = hunks.get(i);
            if (hunk.tag != DiffRow.Tag.CHANGE) continue;
            int common = Math.min(hunk.leftEnd - hunk.leftStart, hunk.rightEnd - hunk.rightStart);
            for (int k = 0; k < common; k++) {
                int lIdx = hunk.leftStart + k;
                int rIdx = hunk.rightStart + k;
                if (lIdx < pairedRightForLeft.length && rIdx < pairedLeftForRight.length) {
                    pairedRightForLeft[lIdx] = rIdx;
                    pairedLeftForRight[rIdx] = lIdx;
                }
            }
        }

        Arrays.fill(minimapBuckets, 0);
        if (totalTrackRows > 0) {
            for (int i = 0; i < hCount; i++) {
                DiffRow.Tag tag = hunks.get(i).tag;
                if (tag == DiffRow.Tag.EQUAL) continue;
                int markerColor = colorForMarker(tag);
                int bStart = Math.max(0, Math.min(MINIMAP_BUCKETS - 1,
                        (int) ((long) hunkScrollStartRow[i] * MINIMAP_BUCKETS / totalTrackRows)));
                int bEnd = Math.max(bStart, Math.min(MINIMAP_BUCKETS - 1,
                        (int) ((long) hunkScrollStartRow[i + 1] * MINIMAP_BUCKETS / totalTrackRows)));
                for (int b = bStart; b <= bEnd; b++) {
                    if (minimapBuckets[b] == 0 || tag != DiffRow.Tag.CHANGE) {
                        minimapBuckets[b] = markerColor;
                    }
                }
            }
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        float totalH = totalTrackRows * rowHeight;
        maxScrollY = Math.max(0f, totalH - getHeight() + rowHeight * 2f);
        if (scrollY > maxScrollY) scrollY = maxScrollY;
        if (wordWrap || getWidth() <= 0) {
            maxLeftScrollX = 0f;
            maxRightScrollX = 0f;
            leftScrollX = 0f;
            rightScrollX = 0f;
        } else {
            float contentWidth = Math.max(1f, getWidth() - minimapWidth);
            float paneWidth = Math.max(1f, (contentWidth - gutterWidth) * 0.5f);
            float charWidth = Math.max(1f, textPaint.measureText("M"));
            maxLeftScrollX = Math.max(0f, maxLeftLineChars * charWidth + panePaddingH * 2f - paneWidth);
            maxRightScrollX = Math.max(0f, maxRightLineChars * charWidth + panePaddingH * 2f - paneWidth);
            if (leftScrollX > maxLeftScrollX) leftScrollX = maxLeftScrollX;
            if (rightScrollX > maxRightScrollX) rightScrollX = maxRightScrollX;
        }
    }

    private static int[] buildWrapOffsets(List<PaneLine> lines, int wrapCols, boolean wrap) {
        int n = lines.size();
        int[] offsets = new int[n + 1];
        if (!wrap) {
            for (int i = 0; i <= n; i++) {
                offsets[i] = i;
            }
            return offsets;
        }
        int acc = 0;
        for (int i = 0; i < n; i++) {
            offsets[i] = acc;
            int len = Math.min(lines.get(i).text.length(), MAX_RENDERED_CHARS_PER_LINE);
            int rows = len == 0 ? 1 : (len + wrapCols - 1) / wrapCols;
            acc += rows;
        }
        offsets[n] = acc;
        return offsets;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = Math.max(0f, Math.min(maxScrollY, scroller.getCurrY()));
            if (!wordWrap) {
                if (flingLeftPane) {
                    leftScrollX = Math.max(0f, Math.min(maxLeftScrollX, scroller.getCurrX()));
                } else {
                    rightScrollX = Math.max(0f, Math.min(maxRightScrollX, scroller.getCurrX()));
                }
            }
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float w = getWidth();
        float minimapStart = w - minimapWidth * 2.5f;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && event.getX() >= minimapStart && maxScrollY > 0) {
            draggingMinimap = true;
            scroller.forceFinished(true);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            jumpScrollFromMinimap(event.getY());
            return true;
        }
        if (draggingMinimap) {
            if (action == MotionEvent.ACTION_MOVE) {
                jumpScrollFromMinimap(event.getY());
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                draggingMinimap = false;
                return true;
            }
        }

        boolean handled = gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return handled || super.onTouchEvent(event);
    }

    private void jumpScrollFromMinimap(float touchY) {
        int h = Math.max(1, getHeight());
        float frac = Math.max(0f, Math.min(1f, touchY / h));
        scrollY = frac * maxScrollY;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRect(0, 0, w, h, bgPaint);
        if (hunks.isEmpty()) return;

        float contentRight = w - minimapWidth;
        float paneWidth = (contentRight - gutterWidth) * 0.5f;
        float leftPaneEnd = paneWidth;
        float rightPaneStart = paneWidth + gutterWidth;

        float trackRow = scrollY / rowHeight;
        int anchorHunk = findHunkForTrackRow(trackRow);
        float leftScrollY = mapLeftScrollY(anchorHunk, trackRow);
        float rightScrollY = mapRightScrollY(anchorHunk, trackRow);

        // 1. Center gutter background
        canvas.drawRect(leftPaneEnd, 0, rightPaneStart, h, gutterBgPaint);

        // 2. Visible hunk backgrounds & Bezier connectors
        float gx1 = leftPaneEnd + numColWidth;
        float gx2 = rightPaneStart - numColWidth;
        float midX = (gx1 + gx2) * 0.5f;

        int firstVisibleHunk = Math.max(0, findHunkForTrackRow(Math.max(0f, trackRow - 4)) - 2);
        int lastVisibleHunk = Math.min(hunks.size() - 1, findHunkForTrackRow(trackRow + h / rowHeight + 4) + 2);

        for (int i = firstVisibleHunk; i <= lastVisibleHunk; i++) {
            TextDiffEngine.DiffHunk hunk = hunks.get(i);
            float ly1 = hunkLeftStartRow[i] * rowHeight - leftScrollY;
            float ly2 = hunkLeftEndRow[i] * rowHeight - leftScrollY;
            float ry1 = hunkRightStartRow[i] * rowHeight - rightScrollY;
            float ry2 = hunkRightEndRow[i] * rowHeight - rightScrollY;

            if (Math.max(ly2, ry2) < -rowHeight || Math.min(ly1, ry1) > h + rowHeight) continue;

            boolean isDiff = hunk.tag != DiffRow.Tag.EQUAL;
            boolean isActive = isDiff && i == highlightedHunkIndex;
            Paint fillPaint = fillPaintFor(hunk.tag);
            Paint borderPaint = isActive ? activeBorderPaint : borderPaintFor(hunk.tag);

            if (isDiff) {
                if (ly2 > ly1) {
                    canvas.drawRect(0, ly1, leftPaneEnd + numColWidth, ly2, fillPaint);
                } else {
                    canvas.drawLine(0, ly1, leftPaneEnd + numColWidth, ly1, borderPaint);
                }
                if (ry2 > ry1) {
                    canvas.drawRect(rightPaneStart - numColWidth, ry1, contentRight, ry2, fillPaint);
                } else {
                    canvas.drawLine(rightPaneStart - numColWidth, ry1, contentRight, ry1, borderPaint);
                }
            }

            // Draw cubic Bezier ribbon across the center bridge for both diffs and equal blocks
            // so matching blocks clearly connect across the dark gutter just like MT Manager.
            bezierPath.reset();
            bezierPath.moveTo(gx1, ly1);
            bezierPath.cubicTo(midX, ly1, midX, ry1, gx2, ry1);
            bezierPath.lineTo(gx2, ry2);
            bezierPath.cubicTo(midX, ry2, midX, ly2, gx1, ly2);
            bezierPath.close();
            canvas.drawPath(bezierPath, fillPaint);

            // Top & bottom ribbon borders
            bezierPath.reset();
            bezierPath.moveTo(gx1, ly1);
            bezierPath.cubicTo(midX, ly1, midX, ry1, gx2, ry1);
            canvas.drawPath(bezierPath, borderPaint);

            bezierPath.reset();
            bezierPath.moveTo(gx1, ly2);
            bezierPath.cubicTo(midX, ly2, midX, ry2, gx2, ry2);
            canvas.drawPath(bezierPath, borderPaint);

            if (isActive) {
                if (ly2 > ly1) {
                    canvas.drawLine(0, ly1, leftPaneEnd + numColWidth, ly1, activeBorderPaint);
                    canvas.drawLine(0, ly2, leftPaneEnd + numColWidth, ly2, activeBorderPaint);
                    canvas.drawRect(0, ly1, activeAccentBarWidth, ly2, activeAccentPaint);
                    canvas.drawRect(leftPaneEnd - activeAccentBarWidth, ly1, leftPaneEnd, ly2, activeAccentPaint);
                }
                if (ry2 > ry1) {
                    canvas.drawLine(rightPaneStart - numColWidth, ry1, contentRight, ry1, activeBorderPaint);
                    canvas.drawLine(rightPaneStart - numColWidth, ry2, contentRight, ry2, activeBorderPaint);
                    canvas.drawRect(rightPaneStart, ry1, rightPaneStart + activeAccentBarWidth, ry2, activeAccentPaint);
                    canvas.drawRect(contentRight - activeAccentBarWidth, ry1, contentRight, ry2, activeAccentPaint);
                }
            }
        }

        // 3. Left pane text & line numbers
        canvas.save();
        canvas.clipRect(0, 0, leftPaneEnd, h);
        drawPaneLines(canvas, leftLines, leftLineStartRow, leftScrollY, leftScrollX, panePaddingH, paneWidth, h, true);
        canvas.restore();
        drawPaneNumbers(canvas, leftLines, leftLineStartRow, leftScrollY,
                leftPaneEnd + numColWidth - panePaddingH, h, true);

        // 4. Right pane text & line numbers
        canvas.save();
        canvas.clipRect(rightPaneStart, 0, contentRight, h);
        drawPaneLines(canvas, rightLines, rightLineStartRow, rightScrollY, rightScrollX, rightPaneStart + panePaddingH, paneWidth, h, false);
        canvas.restore();
        drawPaneNumbers(canvas, rightLines, rightLineStartRow, rightScrollY,
                rightPaneStart - numColWidth + panePaddingH, h, false);

        // Dividers flanking center gutter
        canvas.drawLine(leftPaneEnd, 0, leftPaneEnd, h, dividerPaint);
        canvas.drawLine(rightPaneStart, 0, rightPaneStart, h, dividerPaint);

        // 5. Diff Overview Minimap on the right edge
        drawMinimap(canvas, contentRight, w, h);
    }

    private void drawMinimap(Canvas canvas, float left, int right, int viewHeight) {
        canvas.drawRect(left, 0, right, viewHeight, minimapBgPaint);
        float bucketH = Math.max(2f, (float) viewHeight / MINIMAP_BUCKETS);
        for (int b = 0; b < MINIMAP_BUCKETS; b++) {
            int color = minimapBuckets[b];
            if (color != 0) {
                minimapMarkerPaint.setColor(color);
                float y = (float) b * viewHeight / MINIMAP_BUCKETS;
                canvas.drawRect(left + 1f, y, right - 1f, y + bucketH, minimapMarkerPaint);
            }
        }
        float totalH = Math.max(viewHeight, totalTrackRows * rowHeight);
        float thumbTop = (scrollY / totalH) * viewHeight;
        float thumbHeight = Math.max(14f, (viewHeight / totalH) * viewHeight);
        canvas.drawRect(left, thumbTop, right, Math.min(viewHeight, thumbTop + thumbHeight), minimapThumbPaint);
    }

    private Paint fillPaintFor(DiffRow.Tag tag) {
        return switch (tag) {
            case DELETE -> deleteBlockPaint;
            case INSERT -> insertBlockPaint;
            case CHANGE -> changeBlockPaint;
            default -> equalBlockPaint;
        };
    }

    private Paint borderPaintFor(DiffRow.Tag tag) {
        return switch (tag) {
            case DELETE -> deleteBorderPaint;
            case INSERT -> insertBorderPaint;
            case CHANGE -> changeBorderPaint;
            default -> equalBorderPaint;
        };
    }

    private int colorForMarker(DiffRow.Tag tag) {
        return switch (tag) {
            case DELETE -> colorDeleteMarker;
            case INSERT -> colorInsertMarker;
            default -> colorChangeMarker;
        };
    }

    private void drawPaneLines(Canvas canvas, List<PaneLine> lines, int[] startRows,
                               float paneScrollY, float paneScrollX, float textX,
                               float paneWidth, int viewHeight, boolean isLeft) {
        if (lines.isEmpty()) return;
        int totalRows = startRows[lines.size()];
        int firstRow = Math.max(0, (int) Math.floor(paneScrollY / rowHeight));
        int lastRow = Math.min(totalRows - 1, (int) Math.ceil((paneScrollY + viewHeight) / rowHeight));
        if (firstRow > lastRow) return;

        int lineIdx = findLineForVisualRow(startRows, firstRow);
        String queryLower = searchQuery.isEmpty() ? "" : (searchMatchCase ? searchQuery : searchQuery.toLowerCase());
        float charW = Math.max(1f, textPaint.measureText("M"));

        int visColStart = wordWrap ? 0 : Math.max(0, (int) Math.floor((paneScrollX - panePaddingH) / charW) - 2);
        int visColEnd = wordWrap ? 0 : Math.max(visColStart + 1, (int) Math.ceil((paneScrollX + paneWidth) / charW) + 2);

        int cachedLineIdx = -1;
        int[] cachedRanges = null;

        for (int row = firstRow; row <= lastRow; row++) {
            while (lineIdx + 1 < lines.size() && startRows[lineIdx + 1] <= row) {
                lineIdx++;
            }
            if (lineIdx != cachedLineIdx) {
                cachedLineIdx = lineIdx;
                cachedRanges = getInlineRangesForLine(lineIdx, isLeft);
            }

            PaneLine line = lines.get(lineIdx);
            int maxLen = Math.min(line.text.length(), MAX_RENDERED_CHARS_PER_LINE);
            int sliceStart;
            int sliceEnd;
            float drawX;
            if (wordWrap) {
                int rowInLine = row - startRows[lineIdx];
                sliceStart = rowInLine * charsPerLine;
                if (sliceStart >= maxLen) continue;
                sliceEnd = Math.min(maxLen, sliceStart + charsPerLine);
                drawX = textX;
            } else {
                sliceStart = Math.min(maxLen, visColStart);
                if (sliceStart >= maxLen) continue;
                sliceEnd = Math.min(maxLen, visColEnd);
                drawX = textX - paneScrollX + sliceStart * charW;
            }

            float y = row * rowHeight - paneScrollY;

            if (cachedRanges != null) {
                for (int r = 0; r < cachedRanges.length; r += 2) {
                    int rStart = cachedRanges[r];
                    int rEnd = cachedRanges[r + 1];
                    if (rEnd <= sliceStart) continue;
                    if (rStart >= sliceEnd) break;
                    int drawStart = Math.max(sliceStart, rStart) - sliceStart;
                    int drawEnd = Math.min(sliceEnd, rEnd) - sliceStart;
                    if (drawEnd > drawStart) {
                        canvas.drawRect(drawX + drawStart * charW, y,
                                drawX + drawEnd * charW, y + rowHeight, inlineDiffPaint);
                    }
                }
            }

            if (!queryLower.isEmpty()) {
                String slice = line.text.substring(sliceStart, sliceEnd);
                String hay = searchMatchCase ? slice : slice.toLowerCase();
                int idx = hay.indexOf(queryLower);
                while (idx >= 0) {
                    float hx1 = drawX + idx * charW;
                    float hx2 = hx1 + queryLower.length() * charW;
                    canvas.drawRect(hx1, y, hx2, y + rowHeight, searchHighlightPaint);
                    idx = hay.indexOf(queryLower, idx + queryLower.length());
                }
            }
            canvas.drawText(line.text, sliceStart, sliceEnd, drawX, y + textBaselineOffset, textPaint);
        }
    }

    @Nullable
    private int[] getInlineRangesForLine(int lineIdx, boolean isLeft) {
        int leftIdx;
        int rightIdx;
        if (isLeft) {
            if (lineIdx < 0 || lineIdx >= pairedRightForLeft.length) return null;
            rightIdx = pairedRightForLeft[lineIdx];
            if (rightIdx < 0) return null;
            leftIdx = lineIdx;
        } else {
            if (lineIdx < 0 || lineIdx >= pairedLeftForRight.length) return null;
            leftIdx = pairedLeftForRight[lineIdx];
            if (leftIdx < 0) return null;
            rightIdx = lineIdx;
        }
        long key = (((long) leftIdx) << 32) | (rightIdx & 0xFFFFFFFFL);
        InlineSpans spans = inlineDiffCache.get(key);
        if (spans == null) {
            spans = computeInlineSpans(leftLines.get(leftIdx).text, rightLines.get(rightIdx).text);
            inlineDiffCache.put(key, spans);
        }
        return isLeft ? spans.leftRanges : spans.rightRanges;
    }

    private static InlineSpans computeInlineSpans(String left, String right) {
        int len1 = Math.min(left.length(), MAX_RENDERED_CHARS_PER_LINE);
        int len2 = Math.min(right.length(), MAX_RENDERED_CHARS_PER_LINE);

        int prefix = 0;
        int minLen = Math.min(len1, len2);
        while (prefix < minLen && left.charAt(prefix) == right.charAt(prefix)) {
            prefix++;
        }

        int suffix = 0;
        int maxSuffix = minLen - prefix;
        while (suffix < maxSuffix && left.charAt(len1 - 1 - suffix) == right.charAt(len2 - 1 - suffix)) {
            suffix++;
        }

        int mid1 = len1 - prefix - suffix;
        int mid2 = len2 - prefix - suffix;
        if (mid1 <= 0 && mid2 <= 0) {
            return new InlineSpans(new int[0], new int[0]);
        }
        if (mid1 == 0) {
            return new InlineSpans(new int[0], new int[]{prefix, len2 - suffix});
        }
        if (mid2 == 0) {
            return new InlineSpans(new int[]{prefix, len1 - suffix}, new int[0]);
        }
        if (mid1 > MAX_INLINE_DP_LEN || mid2 > MAX_INLINE_DP_LEN) {
            return new InlineSpans(new int[]{prefix, len1 - suffix}, new int[]{prefix, len2 - suffix});
        }

        // Exact char-level LCS inside the small differing middle region.
        short[][] dp = new short[mid1 + 1][mid2 + 1];
        for (int i = mid1 - 1; i >= 0; i--) {
            char c1 = left.charAt(prefix + i);
            for (int j = mid2 - 1; j >= 0; j--) {
                if (c1 == right.charAt(prefix + j)) {
                    dp[i][j] = (short) (dp[i + 1][j + 1] + 1);
                } else {
                    dp[i][j] = dp[i + 1][j] >= dp[i][j + 1] ? dp[i + 1][j] : dp[i][j + 1];
                }
            }
        }

        boolean[] matchedLeft = new boolean[mid1];
        boolean[] matchedRight = new boolean[mid2];
        int i = 0, j = 0;
        while (i < mid1 && j < mid2) {
            if (left.charAt(prefix + i) == right.charAt(prefix + j)) {
                matchedLeft[i] = true;
                matchedRight[j] = true;
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }

        return new InlineSpans(
                collectUnmatchedRanges(matchedLeft, prefix),
                collectUnmatchedRanges(matchedRight, prefix)
        );
    }

    private static int[] collectUnmatchedRanges(boolean[] matched, int offset) {
        List<Integer> ranges = new ArrayList<>();
        int i = 0;
        int n = matched.length;
        while (i < n) {
            if (matched[i]) {
                i++;
                continue;
            }
            int start = offset + i;
            while (i < n && !matched[i]) i++;
            int end = offset + i;
            ranges.add(start);
            ranges.add(end);
        }
        int[] out = new int[ranges.size()];
        for (int k = 0; k < ranges.size(); k++) out[k] = ranges.get(k);
        return out;
    }

    private void drawPaneNumbers(Canvas canvas, List<PaneLine> lines, int[] startRows,
                                 float paneScrollY, float anchorX, int viewHeight, boolean rightAlign) {
        if (lines.isEmpty()) return;
        int totalRows = startRows[lines.size()];
        int firstRow = Math.max(0, (int) Math.floor(paneScrollY / rowHeight));
        int lastRow = Math.min(totalRows - 1, (int) Math.ceil((paneScrollY + viewHeight) / rowHeight));
        if (firstRow > lastRow) return;

        lineNumPaint.setTextAlign(rightAlign ? Paint.Align.RIGHT : Paint.Align.LEFT);
        int lineIdx = findLineForVisualRow(startRows, firstRow);
        while (lineIdx < lines.size()) {
            int row = startRows[lineIdx];
            if (row > lastRow) break;
            if (row >= firstRow) {
                float y = row * rowHeight - paneScrollY;
                canvas.drawText(String.valueOf(lines.get(lineIdx).lineNumber),
                        anchorX, y + numBaselineOffset, lineNumPaint);
            }
            lineIdx++;
        }
    }

    private int findHunkForTrackRow(float trackRow) {
        int hCount = hunks.size();
        if (hCount == 0) return 0;
        int target = (int) Math.floor(trackRow);
        int lo = 0, hi = hCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (hunkScrollStartRow[mid + 1] <= target) lo = mid + 1;
            else if (hunkScrollStartRow[mid] > target) hi = mid - 1;
            else return mid;
        }
        return Math.max(0, Math.min(hCount - 1, lo));
    }

    private int findHunkForLeftRow(int leftRow) {
        int hCount = hunks.size();
        if (hCount == 0) return 0;
        int lo = 0, hi = hCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (hunkLeftEndRow[mid] <= leftRow) lo = mid + 1;
            else if (hunkLeftStartRow[mid] > leftRow) hi = mid - 1;
            else return mid;
        }
        return Math.max(0, Math.min(hCount - 1, lo));
    }

    private static int findLineForVisualRow(int[] startRows, int row) {
        int idx = Arrays.binarySearch(startRows, row);
        if (idx >= 0) {
            while (idx + 1 < startRows.length - 1 && startRows[idx + 1] == row) idx++;
            return Math.min(idx, startRows.length - 2);
        }
        return Math.max(0, Math.min(startRows.length - 2, -idx - 2));
    }

    private float mapLeftScrollY(int hunkIdx, float trackRow) {
        if (hunkIdx < 0 || hunkIdx >= hunks.size()) return 0f;
        int tStart = hunkScrollStartRow[hunkIdx];
        int tSpan = Math.max(1, hunkScrollStartRow[hunkIdx + 1] - tStart);
        float frac = Math.max(0f, Math.min(1f, (trackRow - tStart) / (float) tSpan));
        int lStart = hunkLeftStartRow[hunkIdx];
        int lSpan = hunkLeftEndRow[hunkIdx] - lStart;
        return (lStart + frac * lSpan) * rowHeight;
    }

    private float mapRightScrollY(int hunkIdx, float trackRow) {
        if (hunkIdx < 0 || hunkIdx >= hunks.size()) return 0f;
        int tStart = hunkScrollStartRow[hunkIdx];
        int tSpan = Math.max(1, hunkScrollStartRow[hunkIdx + 1] - tStart);
        float frac = Math.max(0f, Math.min(1f, (trackRow - tStart) / (float) tSpan));
        int rStart = hunkRightStartRow[hunkIdx];
        int rSpan = hunkRightEndRow[hunkIdx] - rStart;
        return (rStart + frac * rSpan) * rowHeight;
    }
}
