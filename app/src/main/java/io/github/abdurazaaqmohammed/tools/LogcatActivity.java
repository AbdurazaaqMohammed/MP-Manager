package io.github.abdurazaaqmohammed.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.utils.LogcatReader;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

/**
 * Logcat viewer over {@link LogcatService}, which owns the capture so it can
 * keep running in the background (notification + separate recents task) and
 * capture MP Manager's own logs even when no activity is on screen. Reading
 * mechanism ported from LogFox (GPL-3.0, see {@link LogcatReader}); UI is
 * this app's programmatic style.
 */
public class LogcatActivity extends BaseActivity implements LogcatReader.Listener {

    /** Level colors, fixed per logcat convention (readable on both themes). */
    private static final int[] LEVEL_COLORS = {
            0xFF9E9E9E, // V
            0xFF26A2C5, // D
            0xFF43A047, // I
            0xFFFFA000, // W
            0xFFE53935, // E
            0xFFAB47BC, // F
            0xFF9E9E9E, // S
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<LogcatReader.LogLine> visible = new ArrayList<>();
    private final LogcatAdapter adapter = new LogcatAdapter();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private FastScrollerRecyclerView list;
    private LinearLayoutManager layoutManager;
    private TextView emptyView;
    private TextView banner;
    private TextInputEditText searchBox;
    private MaterialButton pauseBtn;
    private boolean paused;
    private boolean autoScroll = true;
    private String query = "";
    /** Selected minimum level; -1 = all. Index into LogcatReader.LEVEL_LETTERS. */
    private int minLevel = -1;
    /** Highest line id already shown, so the snapshot + live batches don't overlap. */
    private long lastSeenId = -1;
    private final Runnable searchRunnable = this::applyFilters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, Color.WHITE));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.sidebar_logcat));
        toolbar.setSubtitle(modeSubtitle());
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        banner = new TextView(this);
        banner.setTextSize(12);
        banner.setPadding(dp(16), dp(4), dp(16), dp(4));
        banner.setTextColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        banner.setText(R.string.logcat_starting);
        root.addView(banner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setPadding(dp(12), 0, dp(12), 0);
        TextInputLayout box = UiFields.box(this, getString(R.string.logcat_search_hint));
        searchBox = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
        searchBox.setHint(R.string.logcat_search_hint);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(13);
        box.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                handler.removeCallbacks(searchRunnable);
                handler.postDelayed(searchRunnable, 250);
            }
        });
        filterRow.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Spinner levelSpinner = new Spinner(this);
        levelSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, levelLabels()));
        levelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                minLevel = pos - 1; // index 0 = "All levels"
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        filterRow.addView(levelSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(filterRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout listHost = new FrameLayout(this);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        list = new FastScrollerRecyclerView(this);
        list.setLayoutManager(layoutManager);
        // Default add/remove animations re-layout every insert; during log
        // bursts they draw on top of each other. Kills the overlapping text.
        list.setItemAnimator(null);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                int last = layoutManager.findLastCompletelyVisibleItemPosition();
                autoScroll = last < 0 || last >= adapter.getItemCount() - 2;
            }
        });
        listHost.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        emptyView = new TextView(this);
        emptyView.setText(R.string.logcat_empty);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        listHost.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(listHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(8), dp(4), dp(8), dp(8));
        pauseBtn = controlButton(getString(R.string.logcat_pause), v -> togglePause());
        controls.addView(pauseBtn, rowWeight());
        controls.addView(controlButton(getString(R.string.logcat_clear), v -> clearLogs()), rowWeight());
        controls.addView(controlButton(getString(R.string.logcat_save), v -> saveLogs()), rowWeight());
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // The capture lives in the service: opening this screen is what
        // starts it (never at app launch unless the user turns that on).
        LogcatService.start(this);
        lastSeenId = -1;
        LogcatService.addListener(this);
        hydrateFromService();
        banner.setText(LogcatService.isRunning() ? R.string.logcat_live : R.string.logcat_starting);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogcatService.removeListener(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(LogcatService.PREF_KEEP_RUNNING, true)) {
            LogcatService.stop(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private List<String> levelLabels() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.logcat_level_all));
        labels.add(getString(R.string.logcat_level_verbose));
        labels.add(getString(R.string.logcat_level_debug));
        labels.add(getString(R.string.logcat_level_info));
        labels.add(getString(R.string.logcat_level_warning));
        labels.add(getString(R.string.logcat_level_error));
        labels.add(getString(R.string.logcat_level_fatal));
        labels.add(getString(R.string.logcat_level_silent));
        return labels;
    }

    private int modeSubtitle() {
        LogcatReader.Mode mode = LogcatService.determineMode(this);
        return switch (mode) {
            case ROOT -> R.string.logcat_mode_root;
            case SHIZUKU -> R.string.logcat_mode_shizuku;
            default -> R.string.logcat_mode_own;
        };
    }

    /** Pulls everything the service already captured, then only new lines flow in. */
    private void hydrateFromService() {
        visible.clear();
        List<LogcatReader.LogLine> snapshot = LogcatService.snapshotBuffer();
        for (LogcatReader.LogLine line : snapshot) {
            if (matches(line)) visible.add(line);
        }
        lastSeenId = snapshot.isEmpty() ? -1 : snapshot.get(snapshot.size() - 1).id;
        adapter.notifyDataSetChanged();
        if (!visible.isEmpty()) list.scrollToPosition(visible.size() - 1);
        emptyView.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private LinearLayout.LayoutParams rowWeight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private MaterialButton controlButton(String text, View.OnClickListener onClick) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setOnClickListener(onClick);
        return btn;
    }

    @Override
    public void onLines(List<LogcatReader.LogLine> lines) {
        int added = 0;
        for (LogcatReader.LogLine line : lines) {
            if (line.id <= lastSeenId) continue;
            lastSeenId = line.id;
            if (paused) continue;
            if (matches(line)) {
                visible.add(line);
                added++;
            }
        }
        if (added > 0) {
            adapter.notifyItemRangeInserted(visible.size() - added, added);
            if (autoScroll) list.scrollToPosition(visible.size() - 1);
        }
        emptyView.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onEof(String error) {
        banner.setText(R.string.logcat_stream_ended);
    }

    /**
     * The filter bar searches every aspect of an entry - tag, message,
     * package, uid, pid, tid - which is what the long-press "search by"
     * actions rely on.
     */
    private boolean matches(LogcatReader.LogLine line) {
        if (minLevel >= 0 && line.levelIndex() < minLevel) return false;
        if (query.isEmpty()) return true;
        return (line.tag != null && line.tag.toLowerCase(Locale.ROOT).contains(query))
                || (line.content != null && line.content.toLowerCase(Locale.ROOT).contains(query))
                || (line.packageName != null && line.packageName.toLowerCase(Locale.ROOT).contains(query))
                || (line.uid != null && line.uid.toLowerCase(Locale.ROOT).contains(query))
                || (line.pid != null && line.pid.contains(query))
                || (line.tid != null && line.tid.contains(query));
    }

    private void applyFilters() {
        visible.clear();
        for (LogcatReader.LogLine line : LogcatService.snapshotBuffer()) {
            if (matches(line)) visible.add(line);
        }
        adapter.notifyDataSetChanged();
        if (!paused && !visible.isEmpty()) list.scrollToPosition(visible.size() - 1);
        emptyView.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void togglePause() {
        paused = !paused;
        pauseBtn.setText(paused ? R.string.logcat_resume : R.string.logcat_pause);
        if (!paused) applyFilters();
    }

    private void clearLogs() {
        LogcatService.clearBuffer();
        visible.clear();
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(View.VISIBLE);
    }

    private void showEntryMenu(LogcatReader.LogLine line, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String subject = line.packageName != null ? line.packageName
                : (line.uid != null ? line.uid : "?");
        menu.getMenu().add(0, 1, 0, getString(R.string.logcat_filter_package, subject));
        menu.getMenu().add(0, 2, 1, getString(R.string.logcat_filter_tag, line.tag));
        menu.getMenu().add(0, 3, 2, getString(R.string.logcat_filter_uid, line.uid));
        menu.getMenu().add(0, 4, 3, getString(R.string.logcat_filter_pid, line.pid));
        menu.getMenu().add(0, 5, 4, getString(R.string.logcat_copy));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: setFilterText(subject); break;
                case 2: setFilterText(line.tag); break;
                case 3: setFilterText(line.uid); break;
                case 4: setFilterText(line.pid); break;
                case 5:
                    copyText(formatLine(line));
                    Extensions.showMessage(this, getString(R.string.logcat_copied));
                    break;
            }
            return true;
        });
        menu.show();
    }

    private void setFilterText(String value) {
        if (value == null) return;
        searchBox.setText(value);
        searchBox.setSelection(searchBox.getText().length());
    }

    private String formatLine(LogcatReader.LogLine line) {
        return timeFormat.format(new Date(line.timeMs)) + " "
                + line.level + "/" + (line.packageName != null ? line.packageName : line.uid)
                + " " + line.pid + " " + line.tid + " " + line.tag + ": " + line.content;
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("log", text));
        }
    }

    private void saveLogs() {
        new Thread(() -> {
            String name = "mpmanager_logcat_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
            File out = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), name);
            try {
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(out), StandardCharsets.UTF_8))) {
                    for (LogcatReader.LogLine line : LogcatService.snapshotBuffer()) {
                        w.write(formatLine(line));
                        w.newLine();
                    }
                }
                handler.post(() -> Extensions.showMessage(this,
                        getString(R.string.logcat_saved_to, out.getAbsolutePath())));
            } catch (Exception e) {
                handler.post(() -> Extensions.showMessage(this,
                        getString(R.string.logcat_save_failed, e.getMessage())));
            }
        }, "logcat-save").start();
    }

    private void createHomeShortcut() {
        if (Build.VERSION.SDK_INT < 26) {
            Extensions.showMessage(this, getString(R.string.logcat_shortcut_old_android));
            return;
        }
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                Extensions.showMessage(this, getString(R.string.logcat_shortcut_unsupported));
                return;
            }
            Intent intent = new Intent(this, LogcatActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            ShortcutInfo info =
                    new ShortcutInfo.Builder(this, "logcat_viewer")
                            .setShortLabel(getString(R.string.sidebar_logcat))
                            .setIcon(Icon.createWithResource(this, R.drawable.terminal_24px))
                            .setIntent(intent)
                            .build();
            sm.requestPinShortcut(info, null);
        } catch (Exception e) {
            Extensions.showMessage(this, getString(R.string.logcat_save_failed, e.getMessage()));
        }
    }

    private void showSettingsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, 0);
        CheckBox keepRunning = new CheckBox(this);
        keepRunning.setText(R.string.logcat_keep_running);
        keepRunning.setChecked(prefs.getBoolean(LogcatService.PREF_KEEP_RUNNING, true));
        box.addView(keepRunning);
        CheckBox autoStart = new CheckBox(this);
        autoStart.setText(R.string.logcat_auto_start);
        autoStart.setChecked(prefs.getBoolean(LogcatService.PREF_AUTO_START, false));
        box.addView(autoStart);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logcat_settings)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> prefs.edit()
                        .putBoolean(LogcatService.PREF_KEEP_RUNNING, keepRunning.isChecked())
                        .putBoolean(LogcatService.PREF_AUTO_START, autoStart.isChecked())
                        .apply())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.logcat_add_shortcut);
        menu.add(Menu.NONE, 2, Menu.NONE, R.string.logcat_settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == 1) createHomeShortcut();
        else if (id == 2) showSettingsDialog();
        else return super.onOptionsItemSelected(item);
        return true;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class LogcatAdapter extends RecyclerView.Adapter<LogcatAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout box = new LinearLayout(LogcatActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(8), dp(3), dp(8), dp(3));

            LinearLayout head = new LinearLayout(LogcatActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            TextView time = monoText(11, false);
            time.setId(View.generateViewId());
            head.addView(time, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView level = monoText(11, true);
            level.setId(View.generateViewId());
            level.setPadding(dp(6), 0, dp(6), 0);
            head.addView(level, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView tag = monoText(12, true);
            tag.setId(View.generateViewId());
            tag.setTextColor(MaterialColors.getColor(LogcatActivity.this,
                    android.R.attr.colorPrimary, Color.BLUE));
            head.addView(tag, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(head, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView content = monoText(12, false);
            content.setId(View.generateViewId());
            content.setTextColor(MaterialColors.getColor(LogcatActivity.this,
                    com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            box.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(box, time, level, tag, content);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            LogcatReader.LogLine line = visible.get(position);
            h.currentLine = line;
            h.time.setText(timeFormat.format(new Date(line.timeMs)));
            h.level.setText(String.valueOf(line.level));
            h.level.setTextColor(LEVEL_COLORS[line.levelIndex()]);
            h.tag.setText(line.tag);
            h.content.setText(line.content);
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        private TextView monoText(int sp, boolean bold) {
            TextView tv = new TextView(LogcatActivity.this);
            tv.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextSize(sp);
            return tv;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView time, level, tag, content;
            LogcatReader.LogLine currentLine;

            Holder(View root, TextView time, TextView level, TextView tag, TextView content) {
                super(root);
                this.time = time;
                this.level = level;
                this.tag = tag;
                this.content = content;
                root.setOnLongClickListener(v -> {
                    if (currentLine != null) showEntryMenu(currentLine, root);
                    return true;
                });
            }
        }
    }
}
