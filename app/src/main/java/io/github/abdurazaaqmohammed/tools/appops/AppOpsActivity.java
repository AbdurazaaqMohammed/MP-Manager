package io.github.abdurazaaqmohammed.tools.appops;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

public class AppOpsActivity extends BaseActivity {

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MaterialToolbar toolbar;
    private ChipGroup chipGroup;
    private EditText searchInput;
    private ProgressBar progressBar;
    private TextView emptyText;
    private RecyclerView recyclerView;
    private AppAdapter adapter;

    private final List<AppItem> masterList = new ArrayList<>();
    private final List<AppItem> filteredList = new ArrayList<>();
    private String currentQuery = "";
    private int currentFilterId = R.id.chip_all;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_ops);

        toolbar = findViewById(R.id.app_ops_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        chipGroup = findViewById(R.id.chip_group_filters);
        searchInput = findViewById(R.id.app_ops_search);
        progressBar = findViewById(R.id.app_ops_progress);
        emptyText = findViewById(R.id.app_ops_empty);
        recyclerView = findViewById(R.id.app_ops_recycler);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(25);
        adapter = new AppAdapter();
        recyclerView.setAdapter(adapter);

        setupListeners();
        loadApps();
    }

    private void setupListeners() {
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds != null && !checkedIds.isEmpty()) {
                currentFilterId = checkedIds.get(0);
            } else {
                currentFilterId = R.id.chip_all;
            }
            filterList();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                filterList();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadApps() {
        List<AppItem> cached = AppOpsCache.getCachedAppList();
        if (cached != null && !cached.isEmpty()) {
            masterList.clear();
            masterList.addAll(cached);
            progressBar.setVisibility(View.GONE);
            updateChipCounts();
            filterList();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<AppItem> apps = AppOpsEngine.loadInstalledApps(this);
            mainHandler.post(() -> {
                masterList.clear();
                masterList.addAll(apps);
                progressBar.setVisibility(View.GONE);
                updateChipCounts();
                filterList();
            });
        });
    }

    private void updateChipCounts() {
        int allCount = masterList.size();
        int userCount = 0;
        int systemCount = 0;
        int disabledCount = 0;
        int hiddenCount = 0;

        for (AppItem item : masterList) {
            if (item.isSystem) systemCount++;
            else userCount++;

            if (!item.isEnabled) disabledCount++;
            if (item.isHidden) hiddenCount++;
        }

        chipGroup.findViewById(R.id.chip_all);
        com.google.android.material.chip.Chip chipAll = findViewById(R.id.chip_all);
        if (chipAll != null) chipAll.setText(getString(R.string.app_ops_chip_all) + " (" + allCount + ")");

        com.google.android.material.chip.Chip chipUser = findViewById(R.id.chip_user);
        if (chipUser != null) chipUser.setText(getString(R.string.app_ops_chip_user) + " (" + userCount + ")");

        com.google.android.material.chip.Chip chipSystem = findViewById(R.id.chip_system);
        if (chipSystem != null) chipSystem.setText(getString(R.string.app_ops_chip_system) + " (" + systemCount + ")");

        com.google.android.material.chip.Chip chipDisabled = findViewById(R.id.chip_disabled);
        if (chipDisabled != null) chipDisabled.setText(getString(R.string.app_ops_chip_disabled) + " (" + disabledCount + ")");

        com.google.android.material.chip.Chip chipHidden = findViewById(R.id.chip_hidden);
        if (chipHidden != null) chipHidden.setText(getString(R.string.app_ops_chip_hidden) + " (" + hiddenCount + ")");
    }

    private void filterList() {
        filteredList.clear();
        for (AppItem item : masterList) {
            // Apply category filter
            if (currentFilterId == R.id.chip_user && item.isSystem) continue;
            if (currentFilterId == R.id.chip_system && !item.isSystem) continue;
            if (currentFilterId == R.id.chip_disabled && item.isEnabled) continue;
            if (currentFilterId == R.id.chip_hidden && !item.isHidden) continue;

            // Apply search query
            if (!TextUtils.isEmpty(currentQuery)) {
                boolean matchesName = item.label != null && item.label.toLowerCase().contains(currentQuery);
                boolean matchesPkg = item.packageName != null && item.packageName.toLowerCase().contains(currentQuery);
                if (!matchesName && !matchesPkg) continue;
            }

            filteredList.add(item);
        }

        adapter.notifyDataSetChanged();
        emptyText.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_ops_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem item = filteredList.get(position);
            holder.nameText.setText(item.label);
            holder.packageText.setText(item.packageName);

            holder.tagType.setText(item.isSystem ? getString(R.string.app_ops_chip_system) : getString(R.string.app_ops_chip_user));
            holder.tagDisabled.setVisibility(item.isEnabled ? View.GONE : View.VISIBLE);
            holder.tagHidden.setVisibility(item.isHidden ? View.VISIBLE : View.GONE);

            // Lazy icon loading with cache
            Drawable cached = AppOpsCache.getIcon(item.packageName);
            if (cached != null) {
                holder.iconView.setImageDrawable(cached);
            } else {
                holder.iconView.setImageResource(R.drawable.ic_android_24);
                executor.execute(() -> {
                    try {
                        PackageManager pm = getPackageManager();
                        Drawable icon = pm.getApplicationIcon(item.packageName);
                        AppOpsCache.putIcon(item.packageName, icon);
                        mainHandler.post(() -> {
                            if (holder.getBindingAdapterPosition() == position) {
                                holder.iconView.setImageDrawable(icon);
                            }
                        });
                    } catch (Exception ignored) {}
                });
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(AppOpsActivity.this, AppOpsDetailActivity.class);
                intent.putExtra("package_name", item.packageName);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView iconView;
            final TextView nameText;
            final TextView packageText;
            final TextView tagType;
            final TextView tagDisabled;
            final TextView tagHidden;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.item_app_icon);
                nameText = itemView.findViewById(R.id.item_app_name);
                packageText = itemView.findViewById(R.id.item_app_package);
                tagType = itemView.findViewById(R.id.tag_type);
                tagDisabled = itemView.findViewById(R.id.tag_disabled);
                tagHidden = itemView.findViewById(R.id.tag_hidden);
            }
        }
    }
}
