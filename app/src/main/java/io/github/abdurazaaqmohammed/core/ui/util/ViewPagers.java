package io.github.abdurazaaqmohammed.core.ui.util;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.reflect.Field;

/**
 * ViewPager2 helpers. Extracted from MainActivity.reduceDragSensitivity.
 */
public final class ViewPagers {

    private ViewPagers() {
    }

    public static void reduceDragSensitivity(ViewPager2 viewPager) {
        try {
            Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
            recyclerViewField.setAccessible(true);
            RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(viewPager);

            Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
            touchSlopField.setAccessible(true);
            int touchSlop = (int) touchSlopField.get(recyclerView);
            touchSlopField.set(recyclerView, touchSlop * 2);
        } catch (Exception ignored) {
        }
    }
}
