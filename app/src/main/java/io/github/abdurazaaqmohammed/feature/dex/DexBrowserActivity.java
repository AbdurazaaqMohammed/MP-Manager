package io.github.abdurazaaqmohammed.feature.dex;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/**
 * Searchable multi-dex class browser with on-demand method and field detail inspection.
 */
public class DexBrowserActivity extends BaseActivity {

    public static final String EXTRA_APK = "apk_path";

    private File source;
    private DexAnalyzer.Report report;
    private final List<DexAnalyzer.ClassInfo> filtered = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView summary;
    private ProgressBar progress;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra(EXTRA_APK);
        if (path != null) source = new File(path);
        buildUi();
        load();
    }

    private int color(int attr) {
        View v = findViewById(android.R.id.content);
        if (v == null) v = getWindow().getDecorView();
        return MaterialColors.getColor(v, attr, 0xFF121212);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        setContentView(root);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.dex_browser);
        toolbar.setSubtitle(source == null ? getString(R.string.dex_browser_no_file) : source.getName());
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        summary = new TextView(this);
        summary.setTextSize(12f);
        summary.setPadding(dp(14), dp(8), dp(14), dp(4));
        summary.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        root.addView(summary);

        search = new EditText(this);
        search.setHint(R.string.dex_browser_search_hint);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(dp(14), 0, dp(14), dp(6));
        root.addView(search, searchLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetail(filtered.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            copy(filtered.get(position).type);
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void load() {
        if (source == null || !source.isFile()) {
            summary.setText(R.string.dex_browser_no_file);
            progress.setVisibility(View.GONE);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        AppExecutors.io().execute(() -> {
            DexAnalyzer.Report loaded = DexAnalyzer.analyze(this, source,
                    (done, total, name) -> AppExecutors.runOnUiIfAlive(this,
                            () -> summary.setText(getString(R.string.dex_browser_analyzing, name, done, total))));
            AppExecutors.runOnUiIfAlive(this, () -> {
                report = loaded;
                progress.setVisibility(View.GONE);
                summary.setText(loaded.summary());
                applyFilter(search.getText().toString());
            });
        });
    }

    private void applyFilter(String query) {
        if (adapter == null) return;
        filtered.clear();
        adapter.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (report != null) {
            for (DexAnalyzer.ClassInfo info : report.classes) {
                if (q.isEmpty() || info.type.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(info);
                    adapter.add(getString(R.string.dex_browser_item, info.type, info.methods, info.fields));
                }
            }
        }
        adapter.notifyDataSetChanged();
        if (report != null && !q.isEmpty()) {
            summary.setText(getString(R.string.dex_browser_results, report.summary(), filtered.size()));
        } else if (report != null) {
            summary.setText(report.summary());
        }
    }

    private void showDetail(DexAnalyzer.ClassInfo info) {
        progress.setVisibility(View.VISIBLE);
        AppExecutors.io().execute(() -> {
            DexAnalyzer.Detail detail = DexAnalyzer.describe(this, source, info.type);
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                if (detail == null) {
                    new AlertDialog.Builder(this)
                            .setTitle(info.type)
                            .setMessage(R.string.dex_browser_detail_error)
                            .setPositiveButton(R.string.close, null)
                            .show();
                    return;
                }
                TextView body = new TextView(this);
                body.setTextSize(13f);
                body.setTextIsSelectable(true);
                body.setPadding(dp(18), dp(8), dp(18), dp(8));
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.dex_browser_detail_dex, detail.dex)).append('\n');
                sb.append(getString(R.string.dex_browser_detail_super, detail.superType)).append('\n');
                if (!detail.interfaces.isEmpty()) {
                    sb.append(getString(R.string.dex_browser_detail_interfaces)).append('\n');
                    for (String iface : detail.interfaces) sb.append("  ").append(iface).append('\n');
                }
                sb.append('\n').append(getString(R.string.dex_browser_detail_methods, detail.methods.size())).append('\n');
                int methodLimit = Math.min(detail.methods.size(), 500);
                for (int i = 0; i < methodLimit; i++) sb.append("  ").append(detail.methods.get(i)).append('\n');
                if (detail.methods.size() > methodLimit) sb.append("  …\n");
                sb.append('\n').append(getString(R.string.dex_browser_detail_fields, detail.fields.size())).append('\n');
                int fieldLimit = Math.min(detail.fields.size(), 300);
                for (int i = 0; i < fieldLimit; i++) sb.append("  ").append(detail.fields.get(i)).append('\n');
                if (detail.fields.size() > fieldLimit) sb.append("  …\n");
                body.setText(sb.toString());
                ScrollView scroll = new ScrollView(this);
                scroll.addView(body);
                new AlertDialog.Builder(this)
                        .setTitle(info.type)
                        .setView(scroll)
                        .setPositiveButton(R.string.close, null)
                        .setNeutralButton(R.string.dex_browser_copy_name, (d, w) -> copy(info.type))
                        .show();
            });
        });
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("DEX", text));
            Toast.makeText(this, R.string.dex_browser_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
