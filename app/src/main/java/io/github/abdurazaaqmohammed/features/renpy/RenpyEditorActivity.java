package io.github.abdurazaaqmohammed.features.renpy;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Clean, fast editor activity for Ren'Py save files and persistent data.
 */
public class RenpyEditorActivity extends BaseActivity {

    public static final String EXTRA_FILE_PATH = "extra_renpy_file_path";

    private String originalPath;
    private File targetFile;

    private Toolbar toolbar;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private TextView tvStatusCount;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private RecyclerView rvVariables;

    private final List<RenpySaveEngine.RenpyVariable> allVariables = new ArrayList<>();
    private final List<RenpySaveEngine.RenpyVariable> displayedVariables = new ArrayList<>();
    private final Map<String, Object> modifiedMap = new HashMap<>();

    private VariableAdapter adapter;
    private String currentQuery = "";
    private boolean isSaveFile = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        originalPath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (TextUtils.isEmpty(originalPath)) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        targetFile = new File(originalPath);
        if (!targetFile.exists()) {
            Toast.makeText(this, "File not found: " + originalPath, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildUI();
        loadData();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(MaterialColors.getColor(this, android.R.attr.colorBackground, 0xFF121212));

        // Toolbar
        toolbar = new Toolbar(this);
        String name = targetFile.getName();
        toolbar.setTitle(name);
        toolbar.setSubtitle("Ren'Py Save Editor");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Save Action Button in Toolbar
        MenuItem saveItem = toolbar.getMenu().add(0, 1001, 0, R.string.save);
        saveItem.setIcon(R.drawable.save_24px);
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1001) {
                saveModifications();
                return true;
            }
            return false;
        });

        root.addView(toolbar);

        // Search Bar Card
        LinearLayout searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(16);
        int padV = dp(6);
        searchContainer.setPadding(padH, padV, padH, padV);

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(12), 0, dp(12), 0);
        android.graphics.drawable.GradientDrawable boxBg = new android.graphics.drawable.GradientDrawable();
        boxBg.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF1E2533));
        boxBg.setCornerRadius(dp(20));
        searchBox.setBackground(boxBg);
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.baseline_search_24);
        searchIcon.setColorFilter(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        searchBox.addView(searchIcon);

        etSearch = new EditText(this);
        etSearch.setHint(isAr() ? "بحث في المتغيرات..." : "Search variables...");
        etSearch.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF777777));
        etSearch.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        etSearch.setTextSize(13.5f);
        etSearch.setBackground(null);
        etSearch.setSingleLine(true);
        etSearch.setPadding(dp(10), 0, dp(8), 0);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchBox.addView(etSearch);

        btnClearSearch = new ImageView(this);
        btnClearSearch.setImageResource(R.drawable.close_24px);
        btnClearSearch.setColorFilter(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        btnClearSearch.setVisibility(View.GONE);
        btnClearSearch.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
        searchBox.addView(btnClearSearch);

        searchContainer.addView(searchBox);
        root.addView(searchContainer);

        // Status line
        tvStatusCount = new TextView(this);
        tvStatusCount.setTextSize(12f);
        tvStatusCount.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        tvStatusCount.setPadding(dp(18), dp(2), dp(18), dp(6));
        root.addView(tvStatusCount);

        // Progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        // Empty text
        tvEmpty = new TextView(this);
        tvEmpty.setText(isAr() ? "لا توجد متغيرات مطابقة" : "No matching variables found");
        tvEmpty.setTextSize(14f);
        tvEmpty.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(dp(20), dp(40), dp(20), dp(20));
        tvEmpty.setVisibility(View.GONE);
        root.addView(tvEmpty);

        // RecyclerView
        rvVariables = new RecyclerView(this);
        rvVariables.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rvVariables.setLayoutManager(new LinearLayoutManager(this));
        rvVariables.setHasFixedSize(true);

        adapter = new VariableAdapter();
        rvVariables.setAdapter(adapter);
        root.addView(rvVariables);

        setContentView(root);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = (s != null) ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatusCount.setText(isAr() ? "جارٍ قراءة الملف وتحليل المتغيرات..." : "Reading file & parsing variables...");

        new Thread(() -> {
            try {
                RenpySaveEngine.ParseResult result = RenpySaveEngine.parse(RenpyEditorActivity.this, targetFile);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    isSaveFile = result.isSave;
                    allVariables.clear();
                    allVariables.addAll(result.variables);
                    applyFilter();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatusCount.setText("Error: " + e.getMessage());
                    Toast.makeText(RenpyEditorActivity.this, "Failed to read Ren'Py file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void applyFilter() {
        displayedVariables.clear();
        if (TextUtils.isEmpty(currentQuery)) {
            displayedVariables.addAll(allVariables);
        } else {
            String q = currentQuery.toLowerCase(Locale.ROOT);
            for (RenpySaveEngine.RenpyVariable v : allVariables) {
                if (v.displayName.toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(v.value).toLowerCase(Locale.ROOT).contains(q)) {
                    displayedVariables.add(v);
                }
            }
        }

        tvEmpty.setVisibility(displayedVariables.isEmpty() ? View.VISIBLE : View.GONE);
        int total = allVariables.size();
        int disp = displayedVariables.size();
        String countStr = isAr()
                ? ("عدد المتغيرات: " + (disp == total ? total : (disp + " من " + total)))
                : ("Variables: " + (disp == total ? total : (disp + " / " + total)));
        if (!modifiedMap.isEmpty()) {
            countStr += " (" + (isAr() ? "المعدلة: " : "Modified: ") + modifiedMap.size() + ")";
        }
        tvStatusCount.setText(countStr);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(RenpySaveEngine.RenpyVariable var) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(var.displayName);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad / 2);

        TextView tvType = new TextView(this);
        tvType.setText((isAr() ? "النوع: " : "Type: ") + var.type);
        tvType.setTextSize(12f);
        tvType.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        layout.addView(tvType);

        final EditText input = new EditText(this);
        input.setText(var.value == null ? "" : String.valueOf(var.value));
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(14f);

        if ("int".equalsIgnoreCase(var.type)) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else if ("float".equalsIgnoreCase(var.type)) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String valStr = input.getText().toString().trim();
            Object newVal;
            try {
                if ("int".equalsIgnoreCase(var.type)) {
                    newVal = Long.parseLong(valStr);
                } else if ("float".equalsIgnoreCase(var.type)) {
                    newVal = Double.parseDouble(valStr);
                } else if ("bool".equalsIgnoreCase(var.type)) {
                    newVal = Boolean.parseBoolean(valStr) || "1".equals(valStr);
                } else {
                    newVal = valStr;
                }
            } catch (Exception e) {
                Toast.makeText(this, "Invalid value format: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            var.value = newVal;
            modifiedMap.put(var.rawKey, newVal);
            applyFilter();
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void saveModifications() {
        if (modifiedMap.isEmpty()) {
            Toast.makeText(this, isAr() ? "لا توجد أي تعديلات لحفظها" : "No modifications to save", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvStatusCount.setText(isAr() ? "جارٍ الحفظ والنسخ الاحتياطي..." : "Saving & backing up...");

        new Thread(() -> {
            try {
                File backup = RenpySaveEngine.saveWithBackup(RenpyEditorActivity.this, targetFile, modifiedMap);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    modifiedMap.clear();
                    applyFilter();
                    String msg = isAr()
                            ? ("تم الحفظ بنجاح! تم إنشاء نسخة احتياطية: " + backup.getName())
                            : ("Saved successfully! Backup created: " + backup.getName());
                    Toast.makeText(RenpyEditorActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RenpyEditorActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private class VariableAdapter extends RecyclerView.Adapter<VariableAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int padH = dp(16);
            int padV = dp(12);
            row.setPadding(padH, padV, padH, padV);
            row.setBackgroundResource(Extensions.getSelectableItemBackgroundBorderless(parent.getContext()));

            LinearLayout col = new LinearLayout(parent.getContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(parent.getContext());
            tvName.setId(R.id.renpy_var_name);
            tvName.setTextSize(14.5f);
            tvName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvName.setTextColor(MaterialColors.getColor(parent.getContext(), com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            col.addView(tvName);

            TextView tvType = new TextView(parent.getContext());
            tvType.setId(R.id.renpy_var_type);
            tvType.setTextSize(11.5f);
            tvType.setTextColor(MaterialColors.getColor(parent.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
            col.addView(tvType);

            row.addView(col);

            TextView tvVal = new TextView(parent.getContext());
            tvVal.setId(R.id.renpy_var_value);
            tvVal.setTextSize(13.5f);
            tvVal.setTypeface(Typeface.MONOSPACE);
            tvVal.setTextColor(0xFF4CAF50); // Bright Green
            tvVal.setGravity(Gravity.END);
            tvVal.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tvVal);

            return new ViewHolder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RenpySaveEngine.RenpyVariable var = displayedVariables.get(position);
            holder.tvName.setText(var.displayName);
            holder.tvType.setText(var.type);
            holder.tvValue.setText(var.getDisplayValue());

            boolean isModified = modifiedMap.containsKey(var.rawKey);
            holder.tvValue.setTextColor(isModified ? 0xFFFF9800 : 0xFF4CAF50); // Orange if modified, Green if normal

            holder.itemView.setOnClickListener(v -> {
                if ("bool".equalsIgnoreCase(var.type)) {
                    // Quick toggle on click for booleans
                    boolean current = (var.value instanceof Boolean) ? (Boolean) var.value : "true".equalsIgnoreCase(String.valueOf(var.value));
                    boolean next = !current;
                    var.value = next;
                    modifiedMap.put(var.rawKey, next);
                    notifyItemChanged(position);
                    applyFilter();
                    Toast.makeText(v.getContext(), var.displayName + " = " + next, Toast.LENGTH_SHORT).show();
                } else {
                    showEditDialog(var);
                }
            });
        }

        @Override
        public int getItemCount() {
            return displayedVariables.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvType;
            final TextView tvValue;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.renpy_var_name);
                tvType = itemView.findViewById(R.id.renpy_var_type);
                tvValue = itemView.findViewById(R.id.renpy_var_value);
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isAr() {
        return Locale.getDefault().getLanguage().startsWith("ar");
    }
}
