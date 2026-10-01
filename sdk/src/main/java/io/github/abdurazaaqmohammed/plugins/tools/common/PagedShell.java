package io.github.abdurazaaqmohammed.plugins.tools.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

public final class PagedShell {

    public abstract static class Page {
        View view;

        public abstract String title();

        public abstract View build(Context context);

        public void shown() {
        }

        public void hidden() {
        }

        public void destroy() {
        }
    }

    private final Context context;
    private final LinearLayout root;
    private final ViewPager2 pager;
    private final List<Page> pages;
    private final Adapter adapter;
    private int previous;

    public interface SelectListener {
        void onSelect(int position);
    }

    private SelectListener selectListener;

    public void onSelect(SelectListener listener) {
        selectListener = listener;
    }

    public PagedShell(Context context, List<Page> pages, View topBar, View bottomBar,
                      int selected) {
        this.context = context;
        this.pages = pages;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (topBar != null) {
            root.addView(topBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        pager = new ViewPager2(context);
        adapter = new Adapter();
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(1);
        previous = Math.max(0, Math.min(selected, pages.size() - 1));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                try {
                    if (previous >= 0 && previous < pages.size() && previous != position) {
                        pages.get(previous).hidden();
                    }
                } catch (Exception ignored) {
                }
                previous = position;
                try {
                    if (position >= 0 && position < pages.size()) {
                        pages.get(position).shown();
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (selectListener != null) selectListener.onSelect(position);
                } catch (Exception ignored) {
                }
            }
        });
        root.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (bottomBar != null) {
            root.addView(bottomBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (selected >= 0 && selected < pages.size() && selected != 0) {
            pager.setCurrentItem(selected, false);
        }
    }

    public LinearLayout view() {
        return root;
    }

    public void select(int index) {
        try {
            if (index >= 0 && index < pages.size()) pager.setCurrentItem(index, true);
        } catch (Exception ignored) {
        }
    }

    public int current() {
        try {
            return pager.getCurrentItem();
        } catch (Exception ignored) {
            return 0;
        }
    }

    public void mediate(TabLayout tabs) {
        try {
            new TabLayoutMediator(tabs, pager, (tab, position) -> {
                try {
                    tab.setText(pages.get(position).title());
                } catch (Exception ignored) {
                }
            }).attach();
        } catch (Exception ignored) {
        }
    }

    public void destroy() {
        try {
            for (Page p : pages) {
                try {
                    p.destroy();
                } catch (Exception ignored) {
                }
                p.view = null;
            }
        } catch (Exception ignored) {
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {

        Adapter() {
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new Holder(frame);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Page page;
            try {
                page = pages.get(position);
            } catch (Exception ignored) {
                return;
            }
            try {
                if (page.view == null) page.view = page.build(context);
                FrameLayout frame = (FrameLayout) holder.itemView;
                View v = page.view;
                if (v == null) return;
                try {
                    if (v.getParent() instanceof ViewGroup) {
                        ((ViewGroup) v.getParent()).removeView(v);
                    }
                } catch (Exception ignored) {
                }
                frame.removeAllViews();
                frame.addView(v, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                page.shown();
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull Holder holder) {
            try {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos >= 0 && pos < pages.size()) {
                    pages.get(pos).hidden();
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public int getItemCount() {
            try {
                return pages.size();
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    private static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
