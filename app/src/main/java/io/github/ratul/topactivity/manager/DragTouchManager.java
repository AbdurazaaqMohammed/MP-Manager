package io.github.ratul.topactivity.manager;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class DragTouchManager implements View.OnTouchListener {

    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;

    private int xInitCord = 0;
    private int yInitCord = 0;
    private int xInitMargin = 0;
    private int yInitMargin = 0;

    public DragTouchManager(WindowManager windowManager, WindowManager.LayoutParams params) {
        this.windowManager = windowManager;
        this.params = params;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        int xCord = (int) event.getRawX();
        int yCord = (int) event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                xInitCord = xCord;
                yInitCord = yCord;
                xInitMargin = params.x;
                yInitMargin = params.y;
                return true;

            case MotionEvent.ACTION_MOVE:
                params.x = xInitMargin + (xCord - xInitCord);
                params.y = yInitMargin + (yCord - yInitCord);
                windowManager.updateViewLayout(view, params);
                return true;
        }
        return false;
    }
}
