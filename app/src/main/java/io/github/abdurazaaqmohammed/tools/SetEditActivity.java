package io.github.abdurazaaqmohammed.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.RootManager;
import com.google.android.material.color.MaterialColors;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Settings Editor (SetEdit): browse, search, edit, add, and delete Android
 * Global, Secure, and System settings, as well as Android System Properties.
 */
public class SetEditActivity extends BaseActivity {

    public enum SettingType {
        GLOBAL("Global", "global"),
        SECURE("Secure", "secure"),
        SYSTEM("System", "system"),
        PROP("Prop", "prop");

        public final String displayName;
        public final String alias;

        SettingType(String displayName, String alias) {
            this.displayName = displayName;
            this.alias = alias;
        }
    }

    public static class SettingEntry {
        public final SettingType type;
        public final String key;
        public String value;

        public SettingEntry(SettingType type, String key, String value) {
            this.type = type;
            this.key = key;
            this.value = value != null ? value : "";
        }
    }

    private Toolbar toolbar;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private ChipGroup chipGroupSources;
    private Chip chipAll, chipGlobal, chipSecure, chipSystem, chipProps;
    private View layoutOverview;
    private MaterialCardView cardGlobal, cardSecure, cardSystem, cardProps;
    private TextView tvCountGlobal, tvCountSecure, tvCountSystem, tvCountProps;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private RecyclerView rvEntries;
    private FloatingActionButton fabAdd;

    private final List<SettingEntry> allEntries = new ArrayList<>();
    private final java.util.Map<SettingType, List<SettingEntry>> entriesByType = new java.util.EnumMap<>(SettingType.class);
    private final List<SettingEntry> displayedEntries = new ArrayList<>();
    private SettingAdapter adapter;

    private SettingType currentFilter = null; // null = All
    private String currentQuery = "";
    private static final Pattern PROP_LINE = Pattern.compile("^\\[(.+?)\\]: \\[(.*?)\\]$");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_edit);

        initViews();
        setupListeners();
        loadAllSettings();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.settings_editor_title);
        toolbar.setNavigationOnClickListener(v -> finish());
        MenuItem refresh = toolbar.getMenu().add(0, 1001, 0, R.string.menu_refresh);
        refresh.setIcon(R.drawable.baseline_refresh_24);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1001) {
                loadAllSettings();
                return true;
            }
            return false;
        });

        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        chipGroupSources = findViewById(R.id.chipGroupSources);
        chipAll = findViewById(R.id.chipAll);
        chipGlobal = findViewById(R.id.chipGlobal);
        chipSecure = findViewById(R.id.chipSecure);
        chipSystem = findViewById(R.id.chipSystem);
        chipProps = findViewById(R.id.chipProps);

        layoutOverview = findViewById(R.id.layoutOverview);
        cardGlobal = findViewById(R.id.cardGlobal);
        cardSecure = findViewById(R.id.cardSecure);
        cardSystem = findViewById(R.id.cardSystem);
        cardProps = findViewById(R.id.cardProps);

        tvCountGlobal = findViewById(R.id.tvCountGlobal);
        tvCountSecure = findViewById(R.id.tvCountSecure);
        tvCountSystem = findViewById(R.id.tvCountSystem);
        tvCountProps = findViewById(R.id.tvCountProps);

        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvEntries = findViewById(R.id.rvEntries);
        fabAdd = findViewById(R.id.fabAdd);

        rvEntries.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SettingAdapter(displayedEntries, this::showEditDialog);
        rvEntries.setAdapter(adapter);
    }

    private void setupListeners() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s != null ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        chipGroupSources.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipGlobal) currentFilter = SettingType.GLOBAL;
            else if (id == R.id.chipSecure) currentFilter = SettingType.SECURE;
            else if (id == R.id.chipSystem) currentFilter = SettingType.SYSTEM;
            else if (id == R.id.chipProps) {
                currentFilter = SettingType.PROP;
                ensurePropsLoaded();
            } else currentFilter = null; // All
            applyFilter();
        });

        cardGlobal.setOnClickListener(v -> chipGlobal.setChecked(true));
        cardSecure.setOnClickListener(v -> chipSecure.setChecked(true));
        cardSystem.setOnClickListener(v -> chipSystem.setChecked(true));
        cardProps.setOnClickListener(v -> {
            chipProps.setChecked(true);
            ensurePropsLoaded();
        });

        fabAdd.setOnClickListener(v -> showAddDialog());
    }

    private boolean propsLoaded = false;
    private boolean isLoadingProps = false;

    private void ensurePropsLoaded() {
        if (propsLoaded || isLoadingProps) return;
        isLoadingProps = true;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<SettingEntry> propList = new ArrayList<>();
            queryProperties(propList);
            Collections.sort(propList, (a, b) -> a.key.compareToIgnoreCase(b.key));
            final int pCount = propList.size();
            runOnUiThread(() -> {
                propsLoaded = true;
                isLoadingProps = false;
                progressBar.setVisibility(View.GONE);
                allEntries.addAll(propList);
                entriesByType.put(SettingType.PROP, propList);
                tvCountProps.setText(getString(R.string.entries_count, pCount));
                chipProps.setText("Props " + pCount);
                chipAll.setText(getString(R.string.all) + " " + allEntries.size());
                applyFilter();
            });
        }).start();
    }

    private void loadAllSettings() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        new Thread(() -> {
            List<SettingEntry> loaded = new ArrayList<>();
            List<SettingEntry> gList = new ArrayList<>();
            List<SettingEntry> sList = new ArrayList<>();
            List<SettingEntry> sysList = new ArrayList<>();

            // Single fast root execution combining all 3 main tables
            String cmd = "settings list global; echo '===SECURE==='; settings list secure; echo '===SYSTEM==='; settings list system";
            String output = executeSu(cmd);

            if (output != null && output.contains("===SECURE===") && output.contains("===SYSTEM===")) {
                String[] parts = output.split("===SECURE===|===SYSTEM===");
                if (parts.length >= 3) {
                    parseSettingsOutput(SettingType.GLOBAL, parts[0], gList);
                    parseSettingsOutput(SettingType.SECURE, parts[1], sList);
                    parseSettingsOutput(SettingType.SYSTEM, parts[2], sysList);
                }
            } else {
                querySettingsList(SettingType.GLOBAL, gList);
                querySettingsList(SettingType.SECURE, sList);
                querySettingsList(SettingType.SYSTEM, sysList);
            }

            Collections.sort(gList, (a, b) -> a.key.compareToIgnoreCase(b.key));
            Collections.sort(sList, (a, b) -> a.key.compareToIgnoreCase(b.key));
            Collections.sort(sysList, (a, b) -> a.key.compareToIgnoreCase(b.key));

            loaded.addAll(gList);
            loaded.addAll(sList);
            loaded.addAll(sysList);
            Collections.sort(loaded, (a, b) -> a.key.compareToIgnoreCase(b.key));

            final int gCount = gList.size();
            final int sCount = sList.size();
            final int sysCount = sysList.size();

            runOnUiThread(() -> {
                allEntries.clear();
                allEntries.addAll(loaded);
                entriesByType.put(SettingType.GLOBAL, gList);
                entriesByType.put(SettingType.SECURE, sList);
                entriesByType.put(SettingType.SYSTEM, sysList);

                tvCountGlobal.setText(getString(R.string.entries_count, gCount));
                tvCountSecure.setText(getString(R.string.entries_count, sCount));
                tvCountSystem.setText(getString(R.string.entries_count, sysCount));
                tvCountProps.setText(getString(R.string.entries_count, 0));

                chipAll.setText(getString(R.string.all) + " " + loaded.size());
                chipGlobal.setText("Global " + gCount);
                chipSecure.setText("Secure " + sCount);
                chipSystem.setText("System " + sysCount);
                chipProps.setText("Props");

                progressBar.setVisibility(View.GONE);
                applyFilter();
            });
        }).start();
    }

    private static String executeSu(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String output = readStream(p.getInputStream());
            p.waitFor();
            if (output != null && !output.trim().isEmpty()) {
                return output;
            }
        } catch (Exception ignored) {}
        try {
            Process p = Runtime.getRuntime().exec(cmd.split(" "));
            String output = readStream(p.getInputStream());
            p.waitFor();
            return output;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readStream(InputStream is) {
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void parseSettingsOutput(SettingType type, String text, List<SettingEntry> out) {
        if (text == null) return;
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    out.add(new SettingEntry(type, key, val));
                }
            }
        } catch (Exception ignored) {}
    }

    private void querySettingsList(SettingType type, List<SettingEntry> out) {
        String output = executeSu("settings list " + type.alias);
        parseSettingsOutput(type, output, out);
    }

    private void queryProperties(List<SettingEntry> out) {
        String output = executeSu("getprop");
        if (output != null) {
            BufferedReader reader = new BufferedReader(new StringReader(output));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    Matcher m = PROP_LINE.matcher(line.trim());
                    if (m.matches()) {
                        String key = m.group(1);
                        String val = m.group(2);
                        out.add(new SettingEntry(SettingType.PROP, key, val));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void applyFilter() {
        displayedEntries.clear();
        boolean hasQuery = !TextUtils.isEmpty(currentQuery);

        if (!hasQuery) {
            if (currentFilter == null) {
                displayedEntries.addAll(allEntries);
            } else {
                List<SettingEntry> list = entriesByType.get(currentFilter);
                if (list != null) displayedEntries.addAll(list);
            }
        } else {
            String q = currentQuery.toLowerCase(Locale.ROOT);
            List<SettingEntry> source = (currentFilter == null) ? allEntries : entriesByType.getOrDefault(currentFilter, Collections.emptyList());
            for (SettingEntry e : source) {
                if (e.key.toLowerCase(Locale.ROOT).contains(q)
                        || e.value.toLowerCase(Locale.ROOT).contains(q)) {
                    displayedEntries.add(e);
                }
            }
        }

        // Show overview only when no search query and on 'All' tab
        layoutOverview.setVisibility((!hasQuery && currentFilter == null) ? View.VISIBLE : View.GONE);
        tvEmpty.setVisibility(displayedEntries.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(SettingEntry entry) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(entry.type.displayName + ": " + entry.key);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        // Action row: Copy Key, Copy Value
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, pad / 2);

        int primaryColor = MaterialColors.getColor(this, android.R.attr.colorPrimary, 0xFF00796B);

        TextView btnCopyKey = new TextView(this);
        btnCopyKey.setText("📋 Copy Key");
        btnCopyKey.setTextColor(primaryColor);
        btnCopyKey.setPadding(0, 0, pad, 0);
        btnCopyKey.setOnClickListener(v -> {
            copyToClipboard(entry.key);
            Toast.makeText(this, "Key copied", Toast.LENGTH_SHORT).show();
        });

        TextView btnCopyVal = new TextView(this);
        btnCopyVal.setText("📋 Copy Value");
        btnCopyVal.setTextColor(primaryColor);
        btnCopyVal.setOnClickListener(v -> {
            copyToClipboard(entry.value);
            Toast.makeText(this, "Value copied", Toast.LENGTH_SHORT).show();
        });

        actions.addView(btnCopyKey);
        actions.addView(btnCopyVal);
        layout.addView(actions);

        // Value Input
        EditText etValue = new EditText(this);
        etValue.setText(entry.value);
        etValue.setSelection(entry.value.length());
        etValue.setHint(R.string.setting_value);
        etValue.setTypeface(android.graphics.Typeface.MONOSPACE);
        etValue.setTextSize(13);
        layout.addView(etValue);

        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (d, w) -> {
            String newVal = etValue.getText().toString();
            saveSetting(entry, newVal);
        });

        builder.setNegativeButton(R.string.delete_setting, (d, w) -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_setting)
                    .setMessage(getString(R.string.confirm_delete_setting, entry.key))
                    .setPositiveButton(R.string.delete, (d2, w2) -> deleteSetting(entry))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        builder.setNeutralButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showAddDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.add_setting);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        // Spinner Table
        Spinner spType = new Spinner(this);
        String[] types = new String[]{"Global Settings", "Secure Settings", "System Settings", "Android Property"};
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        spType.setAdapter(spinAdapter);
        if (currentFilter != null) {
            spType.setSelection(currentFilter.ordinal());
        }
        layout.addView(spType);

        // Key Input
        EditText etKey = new EditText(this);
        etKey.setHint(R.string.setting_key);
        etKey.setTypeface(android.graphics.Typeface.MONOSPACE);
        etKey.setTextSize(14);
        layout.addView(etKey);

        // Value Input
        EditText etVal = new EditText(this);
        etVal.setHint(R.string.setting_value);
        etVal.setTypeface(android.graphics.Typeface.MONOSPACE);
        etVal.setTextSize(13);
        layout.addView(etVal);

        builder.setView(layout);

        builder.setPositiveButton(android.R.string.ok, (d, w) -> {
            String key = etKey.getText().toString().trim();
            String val = etVal.getText().toString();
            if (key.isEmpty()) return;

            SettingType chosenType = switch (spType.getSelectedItemPosition()) {
                case 1 -> SettingType.SECURE;
                case 2 -> SettingType.SYSTEM;
                case 3 -> SettingType.PROP;
                default -> SettingType.GLOBAL;
            };

            SettingEntry newEntry = new SettingEntry(chosenType, key, val);
            saveSetting(newEntry, val);
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void saveSetting(SettingEntry entry, String newValue) {
        new Thread(() -> {
            try {
                String cmd;
                if (entry.type == SettingType.PROP) {
                    cmd = "setprop " + escape(entry.key) + " " + escape(newValue);
                } else {
                    cmd = "settings put " + entry.type.alias + " " + escape(entry.key) + " " + escape(newValue);
                }
                executeSu(cmd);

                runOnUiThread(() -> {
                    entry.value = newValue;
                    if (!allEntries.contains(entry)) {
                        allEntries.add(entry);
                        Collections.sort(allEntries, (a, b) -> a.key.compareToIgnoreCase(b.key));
                    }
                    applyFilter();
                    Toast.makeText(this, R.string.setting_saved, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void deleteSetting(SettingEntry entry) {
        new Thread(() -> {
            try {
                String cmd;
                if (entry.type == SettingType.PROP) {
                    cmd = "setprop " + escape(entry.key) + " ''";
                } else {
                    cmd = "settings delete " + entry.type.alias + " " + escape(entry.key);
                }
                executeSu(cmd);

                runOnUiThread(() -> {
                    allEntries.remove(entry);
                    displayedEntries.remove(entry);
                    applyFilter();
                    Toast.makeText(this, R.string.setting_deleted, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String escape(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("text", text));
        }
    }

    private static class SettingAdapter extends RecyclerView.Adapter<SettingAdapter.VH> {

        interface OnItemClick {
            void onClick(SettingEntry entry);
        }

        private final List<SettingEntry> items;
        private final OnItemClick listener;

        SettingAdapter(List<SettingEntry> items, OnItemClick listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_set_edit_entry, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SettingEntry item = items.get(position);
            holder.tvKey.setText(item.key);
            holder.tvValue.setText(item.value.isEmpty() ? "(empty)" : item.value);
            holder.tvBadge.setText(item.type.displayName);
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvKey;
            final TextView tvValue;
            final TextView tvBadge;

            VH(@NonNull View itemView) {
                super(itemView);
                tvKey = itemView.findViewById(R.id.tvKey);
                tvValue = itemView.findViewById(R.id.tvValue);
                tvBadge = itemView.findViewById(R.id.tvBadge);
            }
        }
    }
}
