package io.github.abdurazaaqmohammed.tools;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.ActivityLogStore;

/**
 * Shows which activities and dialogs the accessibility service has seen.
 *
 * <p>Following a screen's navigation is the slow part of reading an APK, and this
 * records it: launch the app, tap through it, then read back the class names with
 * their packages to look them up in the decompiled source.
 */
public class ActivityLogActivity extends BaseActivity {

    private final List<ActivityLogStore.Entry> all = new ArrayList<>();
    private final List<ActivityLogStore.Entry> shown = new ArrayList<>();
    private Adapter adapter;
    private TextView status;
    private TextView count;
    private TextView empty;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        MaterialToolbar toolbar = findViewById(R.id.log_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(R.string.activity_log_copy)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.getMenu().add(R.string.activity_log_clear)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if (getString(R.string.activity_log_copy).contentEquals(title)) {
                copyAll();
            } else {
                confirmClear();
            }
            return true;
        });

        status = findViewById(R.id.log_status);
        count = findViewById(R.id.log_count);
        empty = findViewById(R.id.log_empty);
        RecyclerView list = findViewById(R.id.log_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        CheckBox ignoreSelf = findViewById(R.id.log_ignore_self);
        ignoreSelf.setChecked(ActivityLogStore.ignoreSelf());
        ignoreSelf.setOnCheckedChangeListener((b, checked) -> ActivityLogStore.setIgnoreSelf(checked));

        findViewById(R.id.log_search).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim();
                applyFilter();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean running = isServiceEnabled();
        status.setText(running ? R.string.activity_log_running : R.string.activity_log_not_running);
        status.setTextColor(getColor(running
                ? com.google.android.material.R.color.primary
                : com.google.android.material.R.color.error));
        status.setOnClickListener(v -> {
            if (!running) openAccessibilitySettings();
        });
        all.clear();
        all.addAll(ActivityLogStore.snapshot());
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        if (query.isEmpty()) {
            shown.addAll(all);
        } else {
            String q = query.toLowerCase(Locale.ENGLISH);
            for (ActivityLogStore.Entry e : all) {
                if (e.className.toLowerCase(Locale.ENGLISH).contains(q)
                        || e.packageName.toLowerCase(Locale.ENGLISH).contains(q)) {
                    shown.add(e);
                }
            }
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        count.setText(getString(R.string.activity_log_summary, shown.size()));
    }

    private boolean isServiceEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String self = getPackageName() + "/" + ActivityLoggerService.class.getName();
            for (String s : enabled.split(":")) {
                if (s.equalsIgnoreCase(self) || s.equalsIgnoreCase(ActivityLoggerService.class.getName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void openAccessibilitySettings() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.activity_log_enable_title)
                .setMessage(R.string.activity_log_enable_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.activity_log_open_settings, (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {
                    }
                })
                .show();
    }

    private void copyAll() {
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.activity_log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("activity-log",
                    ActivityLogStore.asText()));
            Toast.makeText(this, getString(R.string.activity_log_copied, all.size()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.activity_log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.activity_log_clear)
                .setMessage(getString(R.string.activity_log_confirm_clear, all.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    ActivityLogStore.clear();
                    refresh();
                })
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView cls;
            final TextView meta;

            Holder(@NonNull View v) {
                super(v);
                cls = v.findViewById(R.id.log_class);
                meta = v.findViewById(R.id.log_meta);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_activity_log, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ActivityLogStore.Entry e = shown.get(position);
            holder.cls.setText((e.isWindow ? "▸ " : "") + e.className);
            CharSequence rel = DateUtils.getRelativeTimeSpanString(e.time,
                    System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
            holder.meta.setText(getString(R.string.activity_log_item_meta, e.packageName,
                    rel.toString()));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}