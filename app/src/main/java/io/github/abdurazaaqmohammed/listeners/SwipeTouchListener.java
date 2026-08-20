package io.github.abdurazaaqmohammed.listeners;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;


public class SwipeTouchListener implements View.OnTouchListener {

    private static final float SWIPE_SLOP_DP = 16f;
    private static final float SWIPE_CONFIRM_DP = 60f;

    private final GestureDetectorCompat gestureDetector;
    private final View.OnClickListener originalClickListener;
    private final View.OnLongClickListener originalLongClickListener;
    private final Object arrayAdapter;
    private final int position;

    private final float swipeSlopPx;
    private final float swipeConfirmPx;

    private float initialX;
    private float initialY;
    private boolean isSwiping;
    private boolean gestureHandled;

    private boolean swipeDecided;
    private final int pane;
    private final MainActivity context;

    public SwipeTouchListener(MainActivity context,
                              View.OnClickListener clickListener,
                              View.OnLongClickListener longClickListener,
                              int position,
                              Object arrayAdapter, int pane) {
        this.originalClickListener = clickListener;
        this.originalLongClickListener = longClickListener;
        this.position = position;
        this.context = context;
        this.arrayAdapter = arrayAdapter;
        this.pane = pane;

        float density = context.getResources().getDisplayMetrics().density;
        swipeSlopPx = SWIPE_SLOP_DP * density;
        swipeConfirmPx = SWIPE_CONFIRM_DP * density;

        gestureDetector = new GestureDetectorCompat(context,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        context.setCurrentPane(pane);
                        if (!isSwiping) {
                            if (arrayAdapter instanceof MainFilesArrayAdapter && ((MainFilesArrayAdapter) arrayAdapter).isMultiSelectMode()) {
                                ((MainFilesArrayAdapter) arrayAdapter).handleMultiSelect(position);
                            } else {
                                originalClickListener.onClick(null);
                            }
                            gestureHandled = true;
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        context.setCurrentPane(pane);
                        if (!isSwiping) {
                            originalLongClickListener.onLongClick(null);
                        }
                    }
                });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        context.setCurrentPane(pane);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                initialX = event.getRawX();
                initialY = event.getRawY();
                isSwiping = false;
                swipeDecided = false;
                gestureHandled = false;
                v.getParent().requestDisallowInterceptTouchEvent(false);
                v.onTouchEvent(event);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - initialX;
                float dy = event.getRawY() - initialY;

                if (!swipeDecided) {
                    if (Math.abs(dx) > swipeSlopPx || Math.abs(dy) > swipeSlopPx) {
                        swipeDecided = true;
                        if (Math.abs(dx) > Math.abs(dy)) {
                            isSwiping = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            v.onTouchEvent(cancel);
                            cancel.recycle();
                        } else {
                            isSwiping = false;
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            return false;
                        }
                    }
                }

                if (isSwiping) {
                    float clamped = rubberBand(dx, swipeConfirmPx);
                    v.setTranslationX(clamped);
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isSwiping) {
                    v.onTouchEvent(event);
                    float translation = Math.abs(v.getTranslationX());
                    boolean confirmed = translation >= swipeConfirmPx * 0.75f;

                    if (confirmed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        float direction = v.getTranslationX() > 0 ? 1f : -1f;
                        v.animate()
                                .translationX(direction * swipeConfirmPx * 1.15f)
                                .setDuration(80)
                                .withEndAction(() -> v.animate()
                                        .translationX(0)
                                        .setDuration(220)
                                        .setInterpolator(new DecelerateInterpolator(1.8f))
                                        .start())
                                .start();

                        if (arrayAdapter instanceof MainFilesArrayAdapter ma)
                            ma.handleSwipe(position);
                    } else {
                        v.animate().translationX(0).setDuration(180).setInterpolator(new DecelerateInterpolator(1.5f)).start();
                    }
                    isSwiping = false;
                    return true;
                } else if (!gestureHandled) {
                    v.onTouchEvent(event);
                }
                return true;
            }
        }
        return false;
    }

    private float rubberBand(float dx, float threshold) {
        float abs = Math.abs(dx);
        float sign = dx >= 0 ? 1f : -1f;
        if (abs <= threshold) {
            return dx;
        }
        float excess = abs - threshold;
        float damped = threshold + (float) Math.sqrt(excess * threshold * 0.5f);
        return sign * damped;
    }
}
