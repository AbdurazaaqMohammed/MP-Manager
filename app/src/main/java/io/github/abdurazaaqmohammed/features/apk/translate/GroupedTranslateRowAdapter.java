package io.github.abdurazaaqmohammed.features.apk.translate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Layout-file grouping for translation rows: a full-width band naming the archive entry, then
 * that entry's literals as source | translation pairs.
 *
 * <p>The header is derived rather than stored - {@link #submit} only receives rows, and it walks
 * them in order emitting a header whenever the file path changes. Rows therefore have to arrive
 * already sorted by file, which is how the scanner produces them.
 */
public final class GroupedTranslateRowAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        /** A row was tapped: open the editor dialog. */
        void onRowClicked(TranslateRow row);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    /** Flattened view: String for a header, TranslateRow for a literal. */
    private final List<Object> items = new ArrayList<>();
    private final List<TranslateRow> rows = new ArrayList<>();
    private final Listener listener;

    public GroupedTranslateRowAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TranslateRow> next) {
        rows.clear();
        items.clear();
        if (next != null) {
            String lastFile = null;
            for (TranslateRow row : next) {
                rows.add(row);
                if (row.filePath != null && !row.filePath.equals(lastFile)) {
                    items.add(row.filePath);
                    lastFile = row.filePath;
                }
                items.add(row);
            }
        }
        notifyDataSetChanged();
    }

    public List<TranslateRow> rows() {
        return rows;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_xml_group_header, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.item_xml_translate_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).label.setText((String) item);
            return;
        }
        RowHolder rowHolder = (RowHolder) holder;
        TranslateRow row = (TranslateRow) item;
        rowHolder.source.setText(row.current);
        rowHolder.translation.setText(row.getTranslation());
        rowHolder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at == RecyclerView.NO_POSITION || listener == null) return;
            Object tapped = items.get(at);
            if (tapped instanceof TranslateRow) listener.onRowClicked((TranslateRow) tapped);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView label;

        HeaderHolder(@NonNull View view) {
            super(view);
            label = view.findViewById(R.id.xml_group_label);
        }
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView source;
        final TextView translation;

        RowHolder(@NonNull View view) {
            super(view);
            source = view.findViewById(R.id.xml_row_source);
            translation = view.findViewById(R.id.xml_row_translation);
        }
    }
}
