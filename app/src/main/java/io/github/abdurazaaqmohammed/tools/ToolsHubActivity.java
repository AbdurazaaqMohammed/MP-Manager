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
import java.util.List;

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
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        int gridPad = (int) (8 * getResources().getDisplayMetrics().density);
        grid.setPadding(gridPad, gridPad, gridPad, gridPad);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        allTools = ToolRegistry.getTools(this);
        toolbar.setSubtitle(allTools.size() + " tools");
        adapter = new ToolAdapter(new ArrayList<>(allTools));
        grid.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            public void afterTextChanged(Editable s) {
            }
        });
    }
    private class ToolAdapter extends RecyclerView.Adapter<ToolViewHolder> {
        private List<ToolRegistry.ToolItem> visible;
        ToolAdapter(List<ToolRegistry.ToolItem> initial) {
            visible = initial;
        }
        void filter(String query) {
            String q = query == null ? "" : query.trim().toLowerCase();
            List<ToolRegistry.ToolItem> result = new ArrayList<>();
            if (q.isEmpty()) {
                result.addAll(allTools);
            } else {
                for (ToolRegistry.ToolItem item : allTools) {
                    if (item.title.toLowerCase().contains(q) || item.subtitle.toLowerCase().contains(q)) {
                        result.add(item);
                    }
                }
            }
            visible = result;
            notifyDataSetChanged();
        }
        @NonNull
        public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float density = parent.getContext().getResources().getDisplayMetrics().density;
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
        public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
            ToolRegistry.ToolItem item = visible.get(position);
            holder.icon.setImageResource(item.iconRes);
            ImageViewCompat.setImageTintList(holder.icon, android.content.res.ColorStateList.valueOf(MaterialColors.getColor(holder.card.getContext(), com.google.android.material.R.attr.colorPrimary, Color.BLACK)));
            holder.title.setText(item.title);
            holder.subtitle.setText(item.subtitle);
            holder.card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent(ToolsHubActivity.this, ToolRunnerActivity.class);
                    intent.putExtra("tool_id", item.id);
                    intent.putExtra("tool_title", item.title);
                    startActivity(intent);
                }
            });
        }
        public int getItemCount() {
            return visible.size();
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
