package io.github.abdurazaaqmohammed.tools;

import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
import io.github.abdurazaaqmohammed.utils.ArchivePasswordStore;

/**
 * Keeps the passwords worth remembering for archives, in the order they should
 * be tried, and shows which one last worked.
 */
public class PasswordManagerActivity extends BaseActivity {

    private final List<String> items = new ArrayList<>();
    private Adapter adapter;
    private TextView count;
    private TextView empty;
    private TextView recent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_manager);

        MaterialToolbar toolbar = findViewById(R.id.pw_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        count = findViewById(R.id.pw_count);
        empty = findViewById(R.id.pw_empty);
        recent = findViewById(R.id.pw_recent);

        RecyclerView list = findViewById(R.id.pw_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        MaterialButton add = findViewById(R.id.pw_add);
        add.setOnClickListener(v -> promptAdd());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items.clear();
        items.addAll(ArchivePasswordStore.list(this));
        adapter.notifyDataSetChanged();
        boolean none = items.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        findViewById(R.id.pw_list).setVisibility(none ? View.GONE : View.VISIBLE);
        count.setText(getString(R.string.password_manager_count, items.size()));

        ArchivePasswordStore.Match m = ArchivePasswordStore.lastMatch(this);
        if (m == null) {
            recent.setText(getString(R.string.password_manager_no_match) + "\n"
                    + getString(R.string.password_manager_no_match_detail));
        } else {
            CharSequence rel = DateUtils.getRelativeTimeSpanString(m.time,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            recent.setText(getString(R.string.password_manager_recent_value, m.archiveName,
                    m.index + 1, Math.max(m.total, m.index + 1), rel.toString()));
        }
    }

    private void promptAdd() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.password_manager_add_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.password_manager_add, (d, w) -> {
                    String pw = input.getText() == null ? "" : input.getText().toString();
                    if (pw.isEmpty()) return;
                    if (ArchivePasswordStore.add(this, pw)) {
                        Toast.makeText(this, R.string.password_manager_added, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.password_manager_duplicate, Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                })
                .show();
    }

    private void confirmDelete(int index) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.password_manager_delete_title)
                .setMessage(R.string.password_manager_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    ArchivePasswordStore.remove(this, items.get(index));
                    Toast.makeText(this, R.string.password_manager_delete_any, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView value;
            final TextView position;
            final ImageButton up;
            final ImageButton down;
            final ImageButton delete;

            Holder(@NonNull View v) {
                super(v);
                value = v.findViewById(R.id.pw_value);
                position = v.findViewById(R.id.pw_position);
                up = v.findViewById(R.id.pw_up);
                down = v.findViewById(R.id.pw_down);
                delete = v.findViewById(R.id.pw_delete);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_password, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final int index = position;
            holder.value.setText("••••••");
            holder.value.setOnClickListener(v -> {
                // Tap to reveal: these are the user's own passwords, and masking
                // every entry makes the list unreadable at a glance.
                holder.value.setText(items.get(index));
                holder.value.setOnClickListener(v2 ->
                        holder.value.setText("••••••"));
            });
            holder.position.setText(getString(R.string.password_manager_position,
                    index + 1, items.size()));
            holder.up.setEnabled(index > 0);
            holder.up.setOnClickListener(v -> {
                ArchivePasswordStore.moveUp(PasswordManagerActivity.this, index);
                refresh();
            });
            holder.down.setEnabled(index < items.size() - 1);
            holder.down.setOnClickListener(v -> {
                ArchivePasswordStore.moveDown(PasswordManagerActivity.this, index);
                refresh();
            });
            holder.delete.setOnClickListener(v -> confirmDelete(index));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}