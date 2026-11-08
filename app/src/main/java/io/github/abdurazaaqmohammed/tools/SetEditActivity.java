package io.github.abdurazaaqmohammed.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
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
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

/**
 * Settings Editor (SetEdit): browse, search, edit, add, and delete Android
 * Global, Secure, and System settings, as well as Android System Properties.
 * Optimized with unified root process and RecyclerView view recycling.
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
    private Chip chipAll, chipGlobal, chipSecure, chipSystem, chipProps, chipFavorites;
    private View layoutOverview;
    private MaterialCardView cardGlobal, cardSecure, cardSystem, cardProps, cardFavorites;
    private TextView tvCountGlobal, tvCountSecure, tvCountSystem, tvCountProps, tvCountFavorites;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private RecyclerView rvEntries;
    private FloatingActionButton fabAdd;

    private final List<SettingEntry> allEntries = new ArrayList<>();
    private final java.util.Map<SettingType, List<SettingEntry>> entriesByType = new java.util.EnumMap<>(SettingType.class);
    private final List<SettingEntry> displayedEntries = new ArrayList<>();
    private final java.util.Set<String> favoriteKeys = new java.util.HashSet<>();
    private SettingAdapter adapter;

    private SettingType currentFilter = null; // null = All
    private boolean isFavoritesFilter = false;
    private String currentQuery = "";
    private static final Pattern PROP_LINE = Pattern.compile("^\\[(.+?)\\]: \\[(.*?)\\]$");
    private static final String PREF_SETEDIT_FAVS = "setedit_favorites";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_edit);

        loadFavorites();
        initViews();
        setupListeners();
        loadAllSettings();
    }

    private void loadFavorites() {
        android.content.SharedPreferences sp = getSharedPreferences("setedit_prefs", Context.MODE_PRIVATE);
        java.util.Set<String> set = sp.getStringSet(PREF_SETEDIT_FAVS, null);
        favoriteKeys.clear();
        if (set != null) {
            favoriteKeys.addAll(set);
        }
    }

    private void saveFavorites() {
        android.content.SharedPreferences sp = getSharedPreferences("setedit_prefs", Context.MODE_PRIVATE);
        sp.edit().putStringSet(PREF_SETEDIT_FAVS, new java.util.HashSet<>(favoriteKeys)).apply();
    }

    private static String favKey(SettingEntry entry) {
        return entry.type.alias + ":" + entry.key;
    }

    private boolean isFavorite(SettingEntry entry) {
        return favoriteKeys.contains(favKey(entry));
    }

    private void toggleFavorite(SettingEntry entry) {
        String key = favKey(entry);
        boolean added;
        if (favoriteKeys.contains(key)) {
            favoriteKeys.remove(key);
            added = false;
        } else {
            favoriteKeys.add(key);
            added = true;
        }
        saveFavorites();
        updateCounts();
        if (isFavoritesFilter) {
            applyFilter();
        } else {
            adapter.notifyDataSetChanged();
        }
        String msg = added
                ? (isAr() ? "تمت الإضافة للمفضلة ❤️" : "Added to favorites ❤️")
                : (isAr() ? "تمت الإزالة من المفضلة" : "Removed from favorites");
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
        chipFavorites = findViewById(R.id.chipFavorites);

        // Header view layout for overview cards
        layoutOverview = LayoutInflater.from(this).inflate(R.layout.header_set_edit_overview, null, false);
        cardGlobal = layoutOverview.findViewById(R.id.cardGlobal);
        cardSecure = layoutOverview.findViewById(R.id.cardSecure);
        cardSystem = layoutOverview.findViewById(R.id.cardSystem);
        cardProps = layoutOverview.findViewById(R.id.cardProps);
        cardFavorites = layoutOverview.findViewById(R.id.cardFavorites);

        tvCountGlobal = layoutOverview.findViewById(R.id.tvCountGlobal);
        tvCountSecure = layoutOverview.findViewById(R.id.tvCountSecure);
        tvCountSystem = layoutOverview.findViewById(R.id.tvCountSystem);
        tvCountProps = layoutOverview.findViewById(R.id.tvCountProps);
        tvCountFavorites = layoutOverview.findViewById(R.id.tvCountFavorites);

        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvEntries = findViewById(R.id.rvEntries);
        fabAdd = findViewById(R.id.fabAdd);

        rvEntries.setLayoutManager(new LinearLayoutManager(this));
        rvEntries.setHasFixedSize(true);
        rvEntries.setItemViewCacheSize(20);

        adapter = new SettingAdapter(displayedEntries, layoutOverview, this::showEditDialog, this::toggleFavorite, this::isFavorite);
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
            if (id == R.id.chipGlobal) {
                currentFilter = SettingType.GLOBAL;
                isFavoritesFilter = false;
            } else if (id == R.id.chipSecure) {
                currentFilter = SettingType.SECURE;
                isFavoritesFilter = false;
            } else if (id == R.id.chipSystem) {
                currentFilter = SettingType.SYSTEM;
                isFavoritesFilter = false;
            } else if (id == R.id.chipProps) {
                currentFilter = SettingType.PROP;
                isFavoritesFilter = false;
                ensurePropsLoaded();
            } else if (id == R.id.chipFavorites) {
                currentFilter = null;
                isFavoritesFilter = true;
            } else {
                currentFilter = null; // All
                isFavoritesFilter = false;
            }
            applyFilter();
        });

        cardGlobal.setOnClickListener(v -> chipGlobal.setChecked(true));
        cardSecure.setOnClickListener(v -> chipSecure.setChecked(true));
        cardSystem.setOnClickListener(v -> chipSystem.setChecked(true));
        cardProps.setOnClickListener(v -> {
            chipProps.setChecked(true);
            ensurePropsLoaded();
        });
        cardFavorites.setOnClickListener(v -> chipFavorites.setChecked(true));

        fabAdd.setOnClickListener(v -> showAddDialog());
    }

    private boolean propsLoaded = false;
    private boolean isLoadingProps = false;

    private void ensurePropsLoaded() {
        if (propsLoaded || isLoadingProps) return;
        isLoadingProps = true;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<SettingEntry> propList = new ArrayList<>(3500);
            queryProperties(propList);
            Collections.sort(propList, (a, b) -> a.key.compareToIgnoreCase(b.key));
            runOnUiThread(() -> {
                propsLoaded = true;
                isLoadingProps = false;
                progressBar.setVisibility(View.GONE);
                allEntries.addAll(propList);
                Collections.sort(allEntries, (a, b) -> a.key.compareToIgnoreCase(b.key));
                entriesByType.put(SettingType.PROP, propList);
                updateCounts();
                applyFilter();
            });
        }).start();
    }

    private void updateCounts() {
        int gCount = entriesByType.containsKey(SettingType.GLOBAL) ? entriesByType.get(SettingType.GLOBAL).size() : 0;
        int sCount = entriesByType.containsKey(SettingType.SECURE) ? entriesByType.get(SettingType.SECURE).size() : 0;
        int sysCount = entriesByType.containsKey(SettingType.SYSTEM) ? entriesByType.get(SettingType.SYSTEM).size() : 0;
        int pCount = entriesByType.containsKey(SettingType.PROP) ? entriesByType.get(SettingType.PROP).size() : 0;
        int favCount = favoriteKeys.size();

        tvCountGlobal.setText(getString(R.string.entries_count, gCount));
        tvCountSecure.setText(getString(R.string.entries_count, sCount));
        tvCountSystem.setText(getString(R.string.entries_count, sysCount));
        tvCountProps.setText(propsLoaded ? getString(R.string.entries_count, pCount) : "Tap to load");
        tvCountFavorites.setText(getString(R.string.entries_count, favCount));

        chipAll.setText(getString(R.string.all) + " " + allEntries.size());
        chipGlobal.setText("Global " + gCount);
        chipSecure.setText("Secure " + sCount);
        chipSystem.setText("System " + sysCount);
        chipProps.setText(propsLoaded ? ("Props " + pCount) : "Properties");
        chipFavorites.setText(getString(R.string.favorites) + " " + favCount);
    }

    private void loadAllSettings() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        new Thread(() -> {
            List<SettingEntry> gList = new ArrayList<>(600);
            List<SettingEntry> sList = new ArrayList<>(500);
            List<SettingEntry> sysList = new ArrayList<>(400);

            // Fetch all three tables in a single lightning-fast su shell invocation (<100ms)
            String cmd = "echo '===GLOBAL==='; settings list global; echo '===SECURE==='; settings list secure; echo '===SYSTEM==='; settings list system";
            String output = executeSu(cmd);

            if (output != null) {
                String[] sections = output.split("===(GLOBAL|SECURE|SYSTEM)===");
                // sections[1] -> global, sections[2] -> secure, sections[3] -> system
                if (sections.length > 1) parseSettingsOutput(SettingType.GLOBAL, sections[1], gList);
                if (sections.length > 2) parseSettingsOutput(SettingType.SECURE, sections[2], sList);
                if (sections.length > 3) parseSettingsOutput(SettingType.SYSTEM, sections[3], sysList);
            }

            Collections.sort(gList, (a, b) -> a.key.compareToIgnoreCase(b.key));
            Collections.sort(sList, (a, b) -> a.key.compareToIgnoreCase(b.key));
            Collections.sort(sysList, (a, b) -> a.key.compareToIgnoreCase(b.key));

            List<SettingEntry> loaded = new ArrayList<>(gList.size() + sList.size() + sysList.size());
            loaded.addAll(gList);
            loaded.addAll(sList);
            loaded.addAll(sysList);
            Collections.sort(loaded, (a, b) -> a.key.compareToIgnoreCase(b.key));

            runOnUiThread(() -> {
                allEntries.clear();
                allEntries.addAll(loaded);
                entriesByType.put(SettingType.GLOBAL, gList);
                entriesByType.put(SettingType.SECURE, sList);
                entriesByType.put(SettingType.SYSTEM, sysList);

                updateCounts();
                progressBar.setVisibility(View.GONE);
                applyFilter();
            });
        }).start();
    }

    public static String executeSu(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.getOutputStream().close();
            String output = readStream(p.getInputStream());
            p.waitFor();
            return output != null ? output : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String readStream(InputStream is) {
        if (is == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
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

        List<SettingEntry> source;
        if (isFavoritesFilter) {
            source = new ArrayList<>();
            for (SettingEntry e : allEntries) {
                if (isFavorite(e)) source.add(e);
            }
        } else if (currentFilter == null) {
            source = allEntries;
        } else {
            source = entriesByType.getOrDefault(currentFilter, Collections.emptyList());
        }

        if (!hasQuery) {
            displayedEntries.addAll(source);
        } else {
            String q = currentQuery.toLowerCase(Locale.ROOT);
            for (SettingEntry e : source) {
                if (e.key.toLowerCase(Locale.ROOT).contains(q)
                        || e.value.toLowerCase(Locale.ROOT).contains(q)) {
                    displayedEntries.add(e);
                }
            }
        }

        boolean showHeader = (!hasQuery && currentFilter == null && !isFavoritesFilter);
        adapter.setShowHeader(showHeader);

        tvEmpty.setVisibility(displayedEntries.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(SettingEntry entry) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad / 2);

        // Header: Type Badge + Key
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, pad / 2);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(entry.type.displayName);
        tvBadge.setTextSize(11);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setPadding((int)(8 * getResources().getDisplayMetrics().density), (int)(3 * getResources().getDisplayMetrics().density),
                (int)(8 * getResources().getDisplayMetrics().density), (int)(3 * getResources().getDisplayMetrics().density));
        SettingAdapter.setBadgeStyle(tvBadge, entry.type);
        header.addView(tvBadge);

        TextView tvKey = new TextView(this);
        tvKey.setText(" " + entry.key);
        tvKey.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvKey.setTextSize(14f);
        tvKey.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        tvKey.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvKey.setPadding((int)(8 * getResources().getDisplayMetrics().density), 0, 0, 0);
        header.addView(tvKey);

        layout.addView(header);

        // Quick action pills: Copy Key, Copy Value
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, (int)(12 * getResources().getDisplayMetrics().density));

        int primaryColor = MaterialColors.getColor(this, android.R.attr.colorPrimary, 0xFF64B5F6);

        TextView btnCopyKey = createActionPill("📋 " + (isAr() ? "نسخ المفتاح" : "Copy Key"), primaryColor, () -> {
            copyToClipboard(entry.key);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
        TextView btnCopyVal = createActionPill("📋 " + (isAr() ? "نسخ القيمة" : "Copy Value"), primaryColor, () -> {
            copyToClipboard(entry.value);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });

        actions.addView(btnCopyKey);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams((int)(8 * getResources().getDisplayMetrics().density), 1));
        actions.addView(spacer);
        actions.addView(btnCopyVal);
        layout.addView(actions);

        // Value Input Field
        EditText etValue = new EditText(this);
        etValue.setText(entry.value);
        etValue.setTypeface(Typeface.MONOSPACE);
        etValue.setTextSize(13f);
        etValue.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
        etValue.setHint(isAr() ? "أدخل القيمة…" : "Enter value…");
        etValue.setHintTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        inputBg.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF222734));
        inputBg.setStroke((int)(1 * getResources().getDisplayMetrics().density),
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x44888888));
        etValue.setBackground(inputBg);
        int inputPad = (int)(12 * getResources().getDisplayMetrics().density);
        etValue.setPadding(inputPad, inputPad, inputPad, inputPad);
        etValue.setMaxLines(6);

        layout.addView(etValue);

        builder.setView(layout);
        builder.setTitle(isAr() ? "تعديل الإعداد" : "Edit Setting");

        // Save
        builder.setPositiveButton(R.string.save, (d, w) -> {
            String newVal = etValue.getText().toString();
            saveSetting(entry, newVal);
        });

        // Delete (for non-prop)
        if (entry.type != SettingType.PROP) {
            builder.setNeutralButton(R.string.delete, (d, w) -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete)
                        .setMessage(isAr() ? ("هل أنت متأكد من حذف الإعداد " + entry.key + "؟") : ("Delete setting " + entry.key + "?"))
                        .setPositiveButton(R.string.yes, (d2, w2) -> deleteSetting(entry))
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            });
        }

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private TextView createActionPill(String label, int color, Runnable onClick) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11f);
        tv.setTextColor(color);
        tv.setTypeface(null, Typeface.BOLD);

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(14 * getResources().getDisplayMetrics().density);
        gd.setColor(0x1F64B5F6);
        gd.setStroke((int)(1 * getResources().getDisplayMetrics().density), color & 0x66FFFFFF);
        tv.setBackground(gd);

        int hPad = (int)(10 * getResources().getDisplayMetrics().density);
        int vPad = (int)(5 * getResources().getDisplayMetrics().density);
        tv.setPadding(hPad, vPad, hPad, vPad);

        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private boolean isAr() {
        return "ar".equals(Locale.getDefault().getLanguage());
    }

    private void showAddDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.add_setting);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad / 2);

        // Spinner: Type
        TextView tvType = new TextView(this);
        tvType.setText(isAr() ? "نوع الإعداد:" : "Setting Type:");
        tvType.setTextSize(12f);
        tvType.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        layout.addView(tvType);

        Spinner spType = new Spinner(this);
        String[] types = new String[]{"Global", "Secure", "System", "Prop (setprop)"};
        ArrayAdapter<String> spAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        spType.setAdapter(spAdapter);
        layout.addView(spType);

        // Key
        TextView tvKey = new TextView(this);
        tvKey.setText(isAr() ? "المفتاح (Key):" : "Key:");
        tvKey.setTextSize(12f);
        tvKey.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        tvKey.setPadding(0, (int)(8 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(tvKey);

        EditText etKey = new EditText(this);
        etKey.setHint(isAr() ? "اسم الإعداد" : "Setting name");
        etKey.setTypeface(Typeface.MONOSPACE);
        etKey.setTextSize(13f);
        layout.addView(etKey);

        // Value
        TextView tvVal = new TextView(this);
        tvVal.setText(isAr() ? "القيمة (Value):" : "Value:");
        tvVal.setTextSize(12f);
        tvVal.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
        tvVal.setPadding(0, (int)(8 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(tvVal);

        EditText etVal = new EditText(this);
        etVal.setHint(isAr() ? "أدخل القيمة…" : "Enter value…");
        etVal.setTypeface(Typeface.MONOSPACE);
        etVal.setTextSize(13f);
        layout.addView(etVal);

        builder.setView(layout);
        builder.setPositiveButton(R.string.save, (d, w) -> {
            String key = etKey.getText().toString().trim();
            String val = etVal.getText().toString();
            if (key.isEmpty()) {
                Toast.makeText(this, isAr() ? "المفتاح لا يمكن أن يكون فارغاً" : "Key cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = spType.getSelectedItemPosition();
            SettingType type;
            if (pos == 0) type = SettingType.GLOBAL;
            else if (pos == 1) type = SettingType.SECURE;
            else if (pos == 2) type = SettingType.SYSTEM;
            else type = SettingType.PROP;

            SettingEntry newEntry = new SettingEntry(type, key, val);
            saveSetting(newEntry, val);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void saveSetting(SettingEntry entry, String newValue) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
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
                    List<SettingEntry> byType = entriesByType.get(entry.type);
                    if (byType != null) {
                        byType.add(entry);
                        Collections.sort(byType, (a, b) -> a.key.compareToIgnoreCase(b.key));
                    }
                }
                updateCounts();
                progressBar.setVisibility(View.GONE);
                applyFilter();
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void deleteSetting(SettingEntry entry) {
        if (entry.type == SettingType.PROP) {
            Toast.makeText(this, isAr() ? "لا يمكن حذف خصائص النظام" : "System properties cannot be deleted", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String cmd = "settings delete " + entry.type.alias + " " + escape(entry.key);
            executeSu(cmd);
            runOnUiThread(() -> {
                allEntries.remove(entry);
                List<SettingEntry> byType = entriesByType.get(entry.type);
                if (byType != null) byType.remove(entry);
                updateCounts();
                progressBar.setVisibility(View.GONE);
                applyFilter();
                Toast.makeText(this, isAr() ? "تم الحذف بنجاح" : "Deleted successfully", Toast.LENGTH_SHORT).show();
            });
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

    private static class SettingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        interface OnItemClick {
            void onClick(SettingEntry entry);
        }

        interface OnItemLongClick {
            void onLongClick(SettingEntry entry);
        }

        interface FavoriteChecker {
            boolean isFavorite(SettingEntry entry);
        }

        private final List<SettingEntry> items;
        private final View headerView;
        private final OnItemClick listener;
        private final OnItemLongClick longClickListener;
        private final FavoriteChecker favoriteChecker;
        private boolean showHeader = true;

        SettingAdapter(List<SettingEntry> items, View headerView, OnItemClick listener,
                       OnItemLongClick longClickListener, FavoriteChecker favoriteChecker) {
            this.items = items;
            this.headerView = headerView;
            this.listener = listener;
            this.longClickListener = longClickListener;
            this.favoriteChecker = favoriteChecker;
        }

        void setShowHeader(boolean show) {
            this.showHeader = show;
        }

        @Override
        public int getItemViewType(int position) {
            if (showHeader && position == 0) return TYPE_HEADER;
            return TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(headerView);
            }
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_set_edit_entry, parent, false);
            return new ItemVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_HEADER) {
                return;
            }
            int actualPos = showHeader ? (position - 1) : position;
            if (actualPos < 0 || actualPos >= items.size()) return;

            ItemVH vh = (ItemVH) holder;
            SettingEntry item = items.get(actualPos);
            vh.tvKey.setText(item.key);
            if (item.value.isEmpty()) {
                vh.tvValue.setText("(empty)");
                vh.tvValue.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
                vh.tvValue.setAlpha(0.5f);
            } else {
                vh.tvValue.setText(item.value);
                vh.tvValue.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
                vh.tvValue.setAlpha(1.0f);
            }
            vh.tvBadge.setText(item.type.displayName);
            setBadgeStyle(vh.tvBadge, item.type);

            boolean isFav = favoriteChecker != null && favoriteChecker.isFavorite(item);
            vh.ivFavorite.setVisibility(isFav ? View.VISIBLE : View.GONE);

            vh.itemView.setOnClickListener(v -> listener.onClick(item));
            vh.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(item);
                    return true;
                }
                return false;
            });
        }

        static void setBadgeStyle(TextView tvBadge, SettingType type) {
            GradientDrawable gd = new GradientDrawable();
            float r = tvBadge.getContext().getResources().getDisplayMetrics().density * 6;
            gd.setCornerRadius(r);
            int bgColor;
            int textColor;
            switch (type) {
                case GLOBAL -> {
                    bgColor = 0x332196F3;
                    textColor = 0xFF64B5F6;
                }
                case SECURE -> {
                    bgColor = 0x33FF9800;
                    textColor = 0xFFFFB74D;
                }
                case SYSTEM -> {
                    bgColor = 0x334CAF50;
                    textColor = 0xFF81C784;
                }
                case PROP -> {
                    bgColor = 0x339C27B0;
                    textColor = 0xFFCE93D8;
                }
                default -> {
                    bgColor = 0x33888888;
                    textColor = 0xFFD0D7DE;
                }
            }
            gd.setColor(bgColor);
            tvBadge.setBackground(gd);
            tvBadge.setTextColor(textColor);
        }

        @Override
        public int getItemCount() {
            return (showHeader ? 1 : 0) + items.size();
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            HeaderVH(@NonNull View itemView) {
                super(itemView);
            }
        }

        static class ItemVH extends RecyclerView.ViewHolder {
            final TextView tvKey;
            final TextView tvValue;
            final TextView tvBadge;
            final ImageView ivFavorite;

            ItemVH(@NonNull View itemView) {
                super(itemView);
                tvKey = itemView.findViewById(R.id.tvKey);
                tvValue = itemView.findViewById(R.id.tvValue);
                tvBadge = itemView.findViewById(R.id.tvBadge);
                ivFavorite = itemView.findViewById(R.id.ivFavorite);
            }
        }
    }
}
