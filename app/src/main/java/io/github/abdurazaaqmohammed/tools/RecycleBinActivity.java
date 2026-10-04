package io.github.abdurazaaqmohammed.tools;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.RecycleBin;

/**
 * Lists what has been deleted and lets it be put back.
 *
 * <p>Delete is recoverable, so this is where a mistake gets undone: each entry
 * knows where it came from. Purging is immediate and irreversible, which is why
 * it asks first and why emptying the whole bin spells out how much is going.
 */
public class RecycleBinActivity extends BaseActivity {

    private final List<RecycleBin.Entry> entries = new ArrayList<>();
    private Adapter adapter;
    private TextView summary;
    private TextView emptyView;
    private RecyclerView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycle_bin);

        MaterialToolbar toolbar = findViewById(R.id.bin_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(R.string.recycle_bin_empty_action)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            confirmEmpty();
            return true;
        });

        summary = findViewById(R.id.bin_summary);
        emptyView = findViewById(R.id.bin_empty);
        list = findViewById(R.id.bin_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        entries.clear();
        entries.addAll(RecycleBin.list(this));
        adapter.notifyDataSetChanged();
        boolean empty = entries.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            summary.setText(R.string.recycle_bin_summary_empty);
        } else {
            summary.setText(getString(R.string.recycle_bin_summary, entries.size(),
                    formatSize(RecycleBin.totalSize(this))));
        }
    }

    private void confirmEmpty() {
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.recycle_bin_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recycle_bin_empty_action)
                .setMessage(getString(R.string.recycle_bin_confirm_empty, entries.size(),
                        formatSize(RecycleBin.totalSize(this))))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.recycle_bin_purge, (d, w) -> {
                    RecycleBin.empty(this);
                    refresh();
                })
                .show();
    }

    private void restore(RecycleBin.Entry entry) {
        boolean ok = RecycleBin.restore(this, entry);
        Toast.makeText(this, ok ? R.string.recycle_bin_restored : R.string.recycle_bin_restore_failed,
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void purge(RecycleBin.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recycle_bin_purge)
                .setMessage(getString(R.string.recycle_bin_confirm_purge, entry.originalName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.recycle_bin_purge, (d, w) -> {
                    RecycleBin.purge(entry);
                    refresh();
                })
                .show();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ENGLISH, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView path;
            final MaterialButton restore;
            final MaterialButton purge;

            Holder(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.bin_item_name);
                path = v.findViewById(R.id.bin_item_path);
                restore = v.findViewById(R.id.bin_restore);
                purge = v.findViewById(R.id.bin_purge);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recycle_bin, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            RecycleBin.Entry e = entries.get(position);
            holder.name.setText(e.originalName + (e.wasDirectory() ? "/" : ""));
            String when = DateUtils.getRelativeTimeSpanString(e.deletedAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.path.setText(getString(R.string.recycle_bin_item_meta,
                    e.originalPath, when, formatSize(e.size())));
            holder.restore.setOnClickListener(v -> restore(e));
            holder.purge.setOnClickListener(v -> purge(e));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }
}