package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;
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
 * The language-series list, i.e. the first thing the translation mode shows.
 *
 * <p>Tapping a row enters that language's strings; long-pressing offers MT's config operations
 * (copy / add / delete), which is where a new language pack is created from.
 */
public final class LocaleAdapter extends RecyclerView.Adapter<LocaleAdapter.Holder> {

    public interface Listener {
        void onOpen(LocaleRow row);

        void onLongPress(LocaleRow row);
    }

    private final List<LocaleRow> rows = new ArrayList<>();
    private final Listener listener;

    public LocaleAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<LocaleRow> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    public LocaleRow row(int position) {
        return position >= 0 && position < rows.size() ? rows.get(position) : null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_arsc_locale, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocaleRow row = rows.get(position);
        holder.name.setText(row.name);
        holder.qualifier.setText(row.isDefault()
                ? holder.itemView.getContext().getString(R.string.xlate_config_default)
                : row.qualifier);
        holder.tag.setText(label(holder, row));
        holder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at != RecyclerView.NO_POSITION && listener != null) listener.onOpen(rows.get(at));
        });
        holder.itemView.setOnLongClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at == RecyclerView.NO_POSITION || listener == null) return false;
            listener.onLongPress(rows.get(at));
            return true;
        });
    }

    /** Wording lives here rather than in the model so it can come from the string resources. */
    private String label(Holder holder, LocaleRow row) {
        Context context = holder.itemView.getContext();
        return switch (row.state) {
            case DEFAULT -> context.getString(R.string.xlate_state_default);
            case EMPTY -> context.getString(R.string.xlate_state_empty);
            case COMPLETE -> context.getString(R.string.xlate_state_complete);
            case PARTIAL -> context.getString(R.string.xlate_state_partial,
                    row.filled, row.total, row.percent());
        };
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView qualifier;
        final TextView tag;

        Holder(@NonNull View view) {
            super(view);
            name = view.findViewById(R.id.xlate_locale_name);
            qualifier = view.findViewById(R.id.xlate_locale_qualifier);
            tag = view.findViewById(R.id.xlate_locale_tag);
        }
    }
}
