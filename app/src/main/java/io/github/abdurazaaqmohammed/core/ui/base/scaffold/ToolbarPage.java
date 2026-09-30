package io.github.abdurazaaqmohammed.core.ui.base.scaffold;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;

/**
 * Page skeleton absorbing the hand-built "toolbar + scrolling content" layout
 * repeated across feature activities. Subclasses implement pageTitle() and
 * optionally override createBody() for non-scrolling bodies (RecyclerView etc).
 */
public abstract class ToolbarPage extends BaseActivity {

    private MaterialToolbar toolbar;
    private LinearLayout contentBox;
    private View bodyView;

    protected abstract CharSequence pageTitle();

    protected boolean showsBack() {
        return true;
    }

    @Nullable
    protected View createBody(Context c) {
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dpPx(16), dpPx(16), dpPx(16), dpPx(16));
        scroll.addView(box);
        contentBox = box;
        return scroll;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context c = this;

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(null);
        root.setBackgroundColor(ThemeAttrs.surface(c));

        toolbar = new MaterialToolbar(c);
        toolbar.setTitle(pageTitle());
        toolbar.setTitleTextColor(ThemeAttrs.onSurface(c));
        toolbar.setBackgroundColor(ThemeAttrs.surface(c));
        if (showsBack()) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpPx(56)));

        bodyView = createBody(c);
        if (bodyView != null) {
            root.addView(bodyView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f));
        }
        setContentView(root);
    }

    @Nullable
    protected MaterialToolbar toolbar() {
        return toolbar;
    }

    /** Non-null only when the default scrolling body is used. */
    @Nullable
    protected LinearLayout contentBox() {
        return contentBox;
    }

    @Nullable
    protected View body() {
        return bodyView;
    }
}
