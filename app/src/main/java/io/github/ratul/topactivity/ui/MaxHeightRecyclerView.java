package io.github.ratul.topactivity.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class MaxHeightRecyclerView extends RecyclerView {

    private int maxHeightPx = 0;

    public MaxHeightRecyclerView(Context context) {
        super(context);
    }

    public MaxHeightRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MaxHeightRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            int[] attrsArray = new int[]{android.R.attr.maxHeight};
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, attrsArray);
            maxHeightPx = a.getDimensionPixelSize(0, 0);
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int customHeightSpec;
        if (maxHeightPx > 0) {
            customHeightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
        } else {
            customHeightSpec = heightMeasureSpec;
        }
        super.onMeasure(widthMeasureSpec, customHeightSpec);
    }
}
