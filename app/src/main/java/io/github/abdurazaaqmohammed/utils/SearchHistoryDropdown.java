package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

public final class SearchHistoryDropdown {

    private SearchHistoryDropdown() {
    }

    public interface Listener {
        void onSelect(String query);
        void onChanged(List<SearchHistoryHelper.Item> items);
    }

    public static void show(Context context, View anchor, List<SearchHistoryHelper.Item> items, Listener listener) {
        if (context == null || anchor == null || items == null || items.isEmpty()) return;
        ListView listView = new ListView(context);
        listView.setDivider(new ColorDrawable(0x22000000));
        listView.setDividerHeight(1);
        HistoryAdapter adapter = new HistoryAdapter(context, items);
        listView.setAdapter(adapter);
        int width = Math.max(anchor.getWidth(), (int) (240 * context.getResources().getDisplayMetrics().density));
        PopupWindow popup = new PopupWindow(listView, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(16);
        }
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SearchHistoryHelper.Item it = adapter.getItem(position);
            if (it != null && listener != null) listener.onSelect(it.query);
            popup.dismiss();
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            SearchHistoryHelper.Item it = adapter.getItem(position);
            if (it != null) {
                it.pinned = !it.pinned;
                List<SearchHistoryHelper.Item> pinned = new ArrayList<>();
                List<SearchHistoryHelper.Item> rest = new ArrayList<>();
                for (int i = 0; i < adapter.getCount(); i++) {
                    SearchHistoryHelper.Item x = adapter.getItem(i);
                    if (x.pinned) pinned.add(x);
                    else rest.add(x);
                }
                List<SearchHistoryHelper.Item> all = new ArrayList<>(pinned);
                all.addAll(rest);
                adapter.setItems(all);
                if (listener != null) listener.onChanged(all);
            }
            return true;
        });
        adapter.setOnDelete(query -> {
            List<SearchHistoryHelper.Item> kept = new ArrayList<>();
            for (int i = 0; i < adapter.getCount(); i++) {
                SearchHistoryHelper.Item x = adapter.getItem(i);
                if (!x.query.equals(query)) kept.add(x);
            }
            adapter.setItems(kept);
            if (listener != null) listener.onChanged(kept);
            if (kept.isEmpty()) popup.dismiss();
        });
        int maxH = (int) (320 * context.getResources().getDisplayMetrics().density);
        listView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
        int h = Math.min(listView.getMeasuredHeight(), maxH);
        popup.setHeight(h <= 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : h);
        try {
            popup.showAsDropDown(anchor, 0, 4, Gravity.START);
        } catch (Exception e) {
            popup.showAsDropDown(anchor);
        }
    }

    private static class HistoryAdapter extends BaseAdapter {
        private final Context context;
        private List<SearchHistoryHelper.Item> items;
        private OnDelete onDelete;

        interface OnDelete {
            void onDelete(String query);
        }

        HistoryAdapter(Context context, List<SearchHistoryHelper.Item> items) {
            this.context = context;
            this.items = new ArrayList<>(items);
        }

        void setItems(List<SearchHistoryHelper.Item> next) {
            this.items = new ArrayList<>(next);
            notifyDataSetChanged();
        }

        void setOnDelete(OnDelete onDelete) {
            this.onDelete = onDelete;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public SearchHistoryHelper.Item getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SearchHistoryHelper.Item it = getItem(position);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
            row.setPadding(pad, (int) (pad * 0.7f), pad / 2, (int) (pad * 0.7f));
            row.setBackgroundResource(R.drawable.bg_ripple);
            TextView text = new TextView(context);
            text.setText(it.query);
            text.setTextSize(15);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ImageView del = new ImageView(context);
            try {
                del.setImageResource(R.drawable.close_24px);
            } catch (Exception e) {
                del.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            int s = (int) (32 * context.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(s, s);
            del.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
            try {
                del.setImageTintList(ColorStateList.valueOf(0xFF888888));
            } catch (Exception ignored) {
            }
            del.setOnClickListener(v -> {
                if (onDelete != null) onDelete.onDelete(it.query);
            });
            if(it.pinned) {
                ImageView pin = new ImageView(context);
                pin.setImageResource(R.drawable.keep_24px);
                row.addView(pin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(text, tp);
            row.addView(del, dp);
            return row;
        }
    }
}
