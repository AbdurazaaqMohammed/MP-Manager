package io.github.abdurazaaqmohammed.features.apk.translate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Single-column list of string resources, one per row.
 *
 * <p>No field in the row. Editing happens in a dialog the activity opens, which is what MT
 * Manager does and what this scale needs: a config can hold tens of thousands of entries, and a
 * RecyclerView row per entry cannot hold a live {@code EditText} without the list paying for it
 * on every scroll.
 *
 * <p>In multi-select mode the checkbox column appears and a tap toggles the row instead of
 * opening the editor, so a long list can be staged in bulk.
 */
public final class TranslateRowAdapter extends RecyclerView.Adapter<TranslateRowAdapter.Holder> {

    public interface Listener {
        /** Row tapped outside multi-select mode: open the editor. */
        void onRowClicked(TranslateRow row);

        /** Selection changed, in either mode. */
        void onSelectionChanged();
    }

    private final List<TranslateRow> rows = new ArrayList<>();
    private final Listener listener;
    private boolean multiSelect;

    public TranslateRowAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TranslateRow> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    public List<TranslateRow> rows() {
        return rows;
    }

    /**
     * Turns the checkbox column on or off.
     *
     * <p>Leaving the mode clears the selection: those checkboxes would otherwise still be
     * checked, invisible, and Apply would write rows the user can no longer see chosen.
     */
    public void setMultiSelect(boolean enabled) {
        if (multiSelect == enabled) return;
        multiSelect = enabled;
        if (!enabled) {
            for (TranslateRow row : rows) row.setSelected(false);
            if (listener != null) listener.onSelectionChanged();
        }
        notifyDataSetChanged();
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void selectAll(boolean selected) {
        for (TranslateRow row : rows) row.setSelected(selected);
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged();
    }

    /** Puts every row back to the value its config holds, i.e. undo for the whole screen. */
    public void revertAll() {
        for (TranslateRow row : rows) row.revert();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged();
    }

    public int selectedCount() {
        int n = 0;
        for (TranslateRow row : rows) if (row.isSelected()) n++;
        return n;
    }

    /** Rows to act on: the selection while multi-select is on, everything otherwise. */
    public List<TranslateRow> actionable() {
        if (!multiSelect) return new ArrayList<>(rows);
        List<TranslateRow> out = new ArrayList<>();
        for (TranslateRow row : rows) if (row.isSelected()) out.add(row);
        return out;
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
        holder.source.setText(row.current);

        holder.check.setVisibility(multiSelect ? View.VISIBLE : View.GONE);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(row.isSelected());
        holder.check.setOnCheckedChangeListener((button, checked) -> {
            int at = holder.getBindingAdapterPosition();
            if (at == RecyclerView.NO_POSITION) return;
            rows.get(at).setSelected(checked);
            if (listener != null) listener.onSelectionChanged();
        });

        holder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at == RecyclerView.NO_POSITION || listener == null) return;
            TranslateRow tapped = rows.get(at);
            if (multiSelect) {
                // Toggle rather than open the editor; the checkbox reflects it on the next bind.
                tapped.setSelected(!tapped.isSelected());
                notifyItemChanged(at);
                listener.onSelectionChanged();
            } else {
                listener.onRowClicked(tapped);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        final MaterialCheckBox check;
        final TextView source;

        Holder(@NonNull View view) {
            super(view);
            check = view.findViewById(R.id.xlate_row_check);
            source = view.findViewById(R.id.xlate_row_source);
        }
    }
}