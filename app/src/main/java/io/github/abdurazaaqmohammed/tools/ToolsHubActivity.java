package io.github.abdurazaaqmohammed.tools;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolsHubActivity extends AppCompatActivity {
    private RecyclerView grid;
    private EditText searchInput;
    private ToolAdapter adapter;
    private List<ToolRegistry.ToolItem> allTools = new ArrayList<>();
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences hubPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hubDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(hubPrefs.getInt("theme", hubDark ? io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark : io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE));
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Tools Kit");
        toolbar.setSubtitle("Loading");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        com.google.android.material.textfield.TextInputLayout searchBox =
                io.github.abdurazaaqmohammed.ui.UiFields.box(this, "Search tools");
        searchInput = new com.google.android.material.textfield.TextInputEditText(searchBox.getContext());
        searchInput.setSingleLine(true);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchBox.addView(searchInput, searchParams);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        searchParams.setMargins(pad, pad, pad, 4);
        root.addView(searchBox, searchParams);
        grid = new RecyclerView(this);
        GridLayoutManager layout = new GridLayoutManager(this, 3);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            public int getSpanSize(int position) {
                return adapter != null && adapter.isHeader(position) ? 3 : 1;
            }
        });
        grid.setLayoutManager(layout);
        int gridPad = (int) (8 * getResources().getDisplayMetrics().density);
        grid.setPadding(gridPad, gridPad, gridPad, gridPad);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        allTools = ToolRegistry.getTools(this);
        toolbar.setSubtitle(allTools.size() + " tools");
        adapter = new ToolAdapter(buildRows(allTools));
        grid.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setRows(buildRows(filterTools(s.toString())));
            }
            public void afterTextChanged(Editable s) {
            }
        });
    }
    private List<ToolRegistry.ToolItem> filterTools(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return new ArrayList<>(allTools);
        List<ToolRegistry.ToolItem> result = new ArrayList<>();
        for (ToolRegistry.ToolItem item : allTools) {
            if (item.title().toLowerCase().contains(q) || item.subtitle().toLowerCase().contains(q) || item.category().toLowerCase().contains(q)) {
                result.add(item);
            }
        }
        return result;
    }
    private List<Object> buildRows(List<ToolRegistry.ToolItem> items) {
        Map<String, List<ToolRegistry.ToolItem>> grouped = new LinkedHashMap<>();
        for (String cat : ToolRegistry.categoriesInOrder()) grouped.put(cat, new ArrayList<>());
        for (ToolRegistry.ToolItem item : items) {
            List<ToolRegistry.ToolItem> bucket = grouped.get(item.category());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(item.category(), bucket);
            }
            bucket.add(item);
        }
        List<Object> rows = new ArrayList<>();
        for (Map.Entry<String, List<ToolRegistry.ToolItem>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            rows.add(entry.getKey());
            rows.addAll(entry.getValue());
        }
        return rows;
    }
    private void openTool(ToolRegistry.ToolItem item) {
        if ("wifimanager".equals(item.id())) {
            startActivity(new Intent(this, WifiManagerActivity.class));
        } else if ("storagemanager".equals(item.id())) {
            startActivity(new Intent(this, StorageManagerActivity.class));
        } else {
            Intent intent = new Intent(this, ToolRunnerActivity.class);
            intent.putExtra("tool_id", item.id());
            intent.putExtra("tool_title", item.title());
            startActivity(intent);
        }
    }
    private class ToolAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Object> rows;
        ToolAdapter(List<Object> initial) {
            rows = initial;
        }
        void setRows(List<Object> next) {
            rows = next;
            notifyDataSetChanged();
        }
        boolean isHeader(int position) {
            return rows.get(position) instanceof String;
        }
        public int getItemViewType(int position) {
            return isHeader(position) ? 0 : 1;
        }
        @NonNull
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            if (viewType == 0) {
                TextView header = new TextView(parent.getContext());
                header.setTextSize(15);
                header.setTypeface(null, android.graphics.Typeface.BOLD);
                header.setTextColor(MaterialColors.getColor(parent.getContext(), com.google.android.material.R.attr.colorPrimary, Color.BLACK));
                header.setPadding((int) (6 * density), (int) (12 * density), (int) (6 * density), (int) (4 * density));
                header.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new HeaderHolder(header);
            }
            MaterialCardView card = new MaterialCardView(parent.getContext());
            card.setRadius(16 * density);
            card.setCardElevation(2 * density);
            RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = (int) (6 * density);
            cardParams.setMargins(m, m, m, m);
            card.setLayoutParams(cardParams);
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout box = new LinearLayout(parent.getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            int p = (int) (12 * density);
            box.setPadding(p, p, p, p);
            ImageView icon = new ImageView(parent.getContext());
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int) (36 * density), (int) (36 * density));
            iconParams.gravity = Gravity.CENTER;
            box.addView(icon, iconParams);
            TextView title = new TextView(parent.getContext());
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(1);
            title.setTextSize(13);
            title.setTextColor(MaterialColors.getColor(parent.getContext(), com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            box.addView(title);
            TextView subtitle = new TextView(parent.getContext());
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setMaxLines(1);
            subtitle.setTextSize(10);
            subtitle.setAlpha(0.7f);
            subtitle.setTextColor(MaterialColors.getColor(parent.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
            box.addView(subtitle);
            card.addView(box);
            return new ToolViewHolder(card, icon, title, subtitle);
        }
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                String cat = (String) row;
                int count = 0;
                for (int i = position + 1; i < rows.size() && rows.get(i) instanceof ToolRegistry.ToolItem; i++) count++;
                ((HeaderHolder) holder).label.setText(cat + "  (" + count + ")");
            } else if (holder instanceof ToolViewHolder) {
                ToolRegistry.ToolItem item = (ToolRegistry.ToolItem) row;
                ToolViewHolder h = (ToolViewHolder) holder;
                h.icon.setImageResource(item.iconRes());
                ImageViewCompat.setImageTintList(h.icon, android.content.res.ColorStateList.valueOf(MaterialColors.getColor(h.card.getContext(), com.google.android.material.R.attr.colorPrimary, Color.BLACK)));
                h.title.setText(item.title());
                h.subtitle.setText(item.subtitle());
                h.card.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        openTool(item);
                    }
                });
            }
        }
        public int getItemCount() {
            return rows.size();
        }
    }
    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView label;
        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            this.label = (TextView) itemView;
        }
    }
    private static class ToolViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView title;
        final TextView subtitle;
        ToolViewHolder(@NonNull View itemView, ImageView icon, TextView title, TextView subtitle) {
            super(itemView);
            this.card = (MaterialCardView) itemView;
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}
