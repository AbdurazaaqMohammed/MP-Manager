package io.github.abdurazaaqmohammed.features.apk.translate;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Editable list of string resources.
 *
 * <p>Rows are recycled, so the {@link TextWatcher} installed on each bound field is detached
 * before the holder is reused. Without that, typing in one row rewrites whichever row happened to
 * be bound to the recycled view - the classic RecyclerView two-way-binding bug.
 */
public final class TranslateRowAdapter extends RecyclerView.Adapter<TranslateRowAdapter.Holder> {

    public interface Listener {
        void onRowChanged(TranslateRow row);

        void onSelectionChanged();

        void onRowClicked(TranslateRow row);
    }

    private final List<TranslateRow> rows = new ArrayList<>();
    private final Listener listener;
    /** Rows whose text was touched, so a search filter can be told apart from a revert. */
    private final Set<String> dirtyKeys = new HashSet<>();

    public TranslateRowAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TranslateRow> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    /** Forgets which rows were hand-edited, used when the row set is rebuilt from the arsc. */
    public void resetDirty() {
        dirtyKeys.clear();
    }

    public List<TranslateRow> rows() {
        return rows;
    }

    public void selectAll(boolean selected) {
        for (TranslateRow row : rows) row.setSelected(selected);
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged();
    }

    /** Puts every row back to the value its config holds, i.e. MT's undo for the whole screen. */
    public void revertAll() {
        for (TranslateRow row : rows) {
            row.revert();
        }
        dirtyKeys.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged();
    }

    /**
     * Re-reads translations from the rows without rebuilding them, used after an engine run.
     *
     * <p>Safe to call while a field has focus: rebinding detaches the watcher before
     * {@code setText}, so the origin badge an engine just set is not overwritten with MANUAL.
     */
    public void refreshValues() {
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_translate_row, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TranslateRow row = rows.get(position);
        holder.key.setText(row.key);
        holder.source.setText(row.current);
        holder.tag.setText(row.originTag());
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(row.isSelected());
        holder.check.setOnCheckedChangeListener((button, checked) -> {
            int at = holder.getBindingAdapterPosition();
            if (at == RecyclerView.NO_POSITION) return;
            rows.get(at).setSelected(checked);
            if (listener != null) listener.onSelectionChanged();
        });

        holder.input.removeTextChangedListener(holder.watcher);
        holder.input.setText(row.getTranslation());
        holder.input.setSelection(holder.input.getText() == null
                ? 0 : holder.input.getText().length());
        holder.input.addTextChangedListener(holder.watcher);
        holder.watcher.row = row;

        holder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at != RecyclerView.NO_POSITION && listener != null) {
                listener.onRowClicked(rows.get(at));
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        final MaterialCheckBox check;
        final TextView key;
        final TextView source;
        final TextView tag;
        final TextInputEditText input;
        final Watcher watcher = new Watcher();

        Holder(@NonNull View view) {
            super(view);
            check = view.findViewById(R.id.xlate_row_check);
            key = view.findViewById(R.id.xlate_row_key);
            source = view.findViewById(R.id.xlate_row_source);
            tag = view.findViewById(R.id.xlate_row_tag);
            input = view.findViewById(R.id.xlate_row_input);
        }
    }

    final class Watcher implements TextWatcher {
        TranslateRow row;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (row == null) return;
            // An engine run and a human edit both land here; both are "the user changed it".
            row.setTranslation(editable == null ? "" : editable.toString(),
                    TranslateRow.Origin.MANUAL);
            dirtyKeys.add(row.key);
            if (listener != null) listener.onRowChanged(row);
        }
    }
}
