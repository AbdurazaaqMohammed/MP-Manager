package modder.hub.dexeditor.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/*
 Author : @developer-krushna (Krushna Chandra)
 Idea Extracted From MT Manager

 A perfect optimized RecyclerView Fast Scroller for Android
*/

public class FastScrollerRecyclerView extends RecyclerView {

    private final float thumbWidth;
    private final int thumbColor;
    private final boolean isScrollerEnabled = true;
    private final Paint scrollerPaint;
    private final RectF thumbRect;
    private final float thumbHeight;
    private final Runnable hideScrollerRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };
    private boolean isDragging = false;
    private int lastScrollPosition = -1;
    private boolean isScrollerCurrentlyVisible = false;
    private long lastInteractionTime = 0L;
    private float touchOffset;
    private float dragRelativeY = 0f;
    private boolean isDefaultScrollBarEnabled = true;
    private boolean isTrackVisible = true;
    private ItemAnimator savedAnimator;
    private int pendingScrollPosition = -1;
    private final Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingScrollPosition != -1) {
                LayoutManager lm = getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) lm).scrollToPositionWithOffset(pendingScrollPosition, 0);
                } else {
                    scrollToPosition(pendingScrollPosition);
                }
                pendingScrollPosition = -1;
            }
        }
    };

    public FastScrollerRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public FastScrollerRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FastScrollerRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        this.thumbRect = new RectF();
        this.scrollerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float density = context.getResources().getDisplayMetrics().density;
        this.thumbColor = 0xDD666666; // Standard grey
        this.thumbWidth = 8.0f * density;
        this.thumbHeight = 52.0f * density;

        // Essential: Allow drawing on the RecyclerView itself
        setWillNotDraw(false);

        // Essential: Force redraw during scrolling
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!isDragging) {
                    lastInteractionTime = SystemClock.uptimeMillis();
                    removeCallbacks(hideScrollerRunnable);
                    postDelayed(hideScrollerRunnable, 1500);
                    invalidate();
                }
            }
        });

        LinearLayoutManager lm = new LinearLayoutManager(context);
        lm.setSmoothScrollbarEnabled(false); // Optimization for consistent scroller speed
        super.setLayoutManager(lm);

        ItemAnimator animator = getItemAnimator();
        if (animator != null) {
            animator.setAddDuration(100L);
            animator.setRemoveDuration(100L);
            animator.setMoveDuration(200L);
            animator.setChangeDuration(100L);
        }
    }

    @Override
    public void setLayoutManager(@Nullable LayoutManager layout) {
        if (layout instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layout).setSmoothScrollbarEnabled(false);
        }
        super.setLayoutManager(layout);
    }

    /**
     * Set whether the scroll track should be visible behind the thumb.
     */
    public void setTrackVisible(boolean visible) {
        this.isTrackVisible = visible;
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        if (!isScrollerEnabled || getAdapter() == null) return;

        int itemCount = getAdapter().getItemCount();
        int childCount = getChildCount();

        // Check if list is long enough to show scroller
        if (childCount <= 0 || itemCount <= childCount * 2) {
            if (!isDefaultScrollBarEnabled) {
                isDefaultScrollBarEnabled = true;
                setVerticalScrollBarEnabled(true);
            }
            isScrollerCurrentlyVisible = false;
            return;
        }

        // Optimization: Avoid expensive scroll range calculations during drag
        int range = 0, extent = 0, offset = 0, scrollableRange = 0;
        if (!isDragging) {
            range = computeVerticalScrollRange();
            extent = computeVerticalScrollExtent();
            offset = computeVerticalScrollOffset();
            scrollableRange = range - extent;

            if (scrollableRange <= 0) {
                isScrollerCurrentlyVisible = false;
                return;
            }
        }

        // Fading and Sliding Logic
        float alphaMultiplier = 1.0f;
        float slideOffset = 0.0f;

        if (!isDragging) {
            long timeSinceInteraction = SystemClock.uptimeMillis() - lastInteractionTime;
            if (timeSinceInteraction >= 1500) {
                float fadeProgress = Math.min(1.0f, (timeSinceInteraction - 1500) / 300.0f);
                alphaMultiplier = 1.0f - fadeProgress;
                // Slide out to the right (MT Manager style)
                slideOffset = thumbWidth * fadeProgress;
            }
        }

        if (alphaMultiplier <= 0.0f) {
            isScrollerCurrentlyVisible = false;
            return;
        }

        isScrollerCurrentlyVisible = true;
        if (isDefaultScrollBarEnabled) {
            isDefaultScrollBarEnabled = false;
            setVerticalScrollBarEnabled(false);
        }

        int width = getWidth();
        int height = getHeight();

        // Apply slide animation
        canvas.save();
        canvas.translate(slideOffset, 0);

        // 1. Draw Track
        if (isTrackVisible) {
            int trackColor = 0x11000000; // Very faint track
            int trackAlpha = (int) (Color.alpha(trackColor) * alphaMultiplier);
            scrollerPaint.setColor((trackAlpha << 24) | (trackColor & 0x00FFFFFF));
            float trackLeft = width - thumbWidth;
            canvas.drawRect(trackLeft, 0, width, height, scrollerPaint);
        }

        // 2. Draw Thumb
        int activeColor = isDragging ? 0xFF1E88E5 : thumbColor; // Blue if dragging
        int thumbAlpha = (int) (Color.alpha(activeColor) * alphaMultiplier);
        scrollerPaint.setColor((thumbAlpha << 24) | (activeColor & 0x00FFFFFF));

        float thumbTop;
        if (isDragging) {
            thumbTop = dragRelativeY * (height - thumbHeight);
        } else {
            thumbTop = ((float) offset / scrollableRange) * (height - thumbHeight);
            // Sync dragRelativeY for when dragging starts
            dragRelativeY = thumbTop / (height - thumbHeight);
        }

        float thumbBottom = thumbTop + thumbHeight;
        float thumbLeft = width - thumbWidth;

        // Update hit-rect for touch events
        float density = getContext().getResources().getDisplayMetrics().density;
        float touchWidth = 40.0f * density;
        float touchPaddingVertical = 12.0f * density; 

        thumbRect.set(width - touchWidth,
                thumbTop - touchPaddingVertical,
                width,
                thumbBottom + touchPaddingVertical);

        canvas.drawRect(thumbLeft, thumbTop, width, thumbBottom, scrollerPaint);

        canvas.restore();

        // Continue animating if in fade-out state
        if (alphaMultiplier < 1.0f) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isScrollerEnabled && isScrollerCurrentlyVisible && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (thumbRect.contains(ev.getX(), ev.getY())) {
                isDragging = true;
                
                // Optimization: Stop any current scroll and disable animator for smooth fast-scrolling
                stopScroll();
                savedAnimator = getItemAnimator();
                setItemAnimator(null);

                // Initialize lastScrollPosition to avoid jump
                LayoutManager lm = getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    lastScrollPosition = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
                }

                float density = getContext().getResources().getDisplayMetrics().density;
                float actualThumbTop = thumbRect.top + 12.0f * density; 
                touchOffset = ev.getY() - actualThumbTop;
                
                removeCallbacks(hideScrollerRunnable);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isDragging) {
            lastInteractionTime = SystemClock.uptimeMillis();
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    float height = getHeight();
                    if (height <= thumbHeight) return true;
                    
                    float currentThumbTop = ev.getY() - touchOffset;
                    dragRelativeY = Math.max(0.0f, Math.min(1.0f, currentThumbTop / (height - thumbHeight)));

                    if (getAdapter() != null) {
                        int itemCount = getAdapter().getItemCount();
                        int position = (int) (itemCount * dragRelativeY);
                        position = Math.max(0, Math.min(itemCount - 1, position));

                        if (lastScrollPosition != position) {
                            lastScrollPosition = position;
                            pendingScrollPosition = position;
                            removeCallbacks(scrollRunnable);
                            postOnAnimation(scrollRunnable);
                        }
                    }
                    // Use postInvalidateOnAnimation for smoother UI updates during heavy scroll
                    postInvalidateOnAnimation();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    // Restore animator
                    if (getItemAnimator() == null && savedAnimator != null) {
                        setItemAnimator(savedAnimator);
                    }
                    removeCallbacks(hideScrollerRunnable);
                    postDelayed(hideScrollerRunnable, 1500);
                    invalidate();
                    return true;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
