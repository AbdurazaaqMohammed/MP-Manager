package io.github.abdurazaaqmohammed.features.apk.translate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.abdurazaaqmohammed.MPManager.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lists the XML entries inside an APK. Tapping one asks the activity to decode and show it,
 * long-pressing is intentionally left to the activity so it can offer "open in editor".
 */
public final class ApkXmlEntryAdapter extends RecyclerView.Adapter<ApkXmlEntryAdapter.Holder> {

    public interface OnEntryClick {
        void onEntryClick(ApkXmlCatalog.Item item, int position);
    }

    private final List<ApkXmlCatalog.Item> shown = new ArrayList<>();
    private final OnEntryClick click;

    public ApkXmlEntryAdapter(OnEntryClick click) {
        this.click = click;
    }

    public void submit(List<ApkXmlCatalog.Item> items) {
        shown.clear();
        if (items != null) shown.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_apk_xml_entry, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ApkXmlCatalog.Item item = shown.get(position);
        holder.name.setText(item.displayName());
        holder.path.setText(item.path);
        String size = formatSize(item.size);
        if (item.binary) {
            holder.meta.setText(holder.itemView.getContext()
                    .getString(R.string.xlate_xml_entry_axml, size));
        } else {
            holder.meta.setText(holder.itemView.getContext()
                    .getString(R.string.xlate_xml_entry_text, size));
        }
        holder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at != RecyclerView.NO_POSITION && click != null) click.onEntryClick(item, at);
        });
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView path;
        final TextView meta;

        Holder(@NonNull View view) {
            super(view);
            name = view.findViewById(R.id.xlate_xml_name);
            path = view.findViewById(R.id.xlate_xml_path);
            meta = view.findViewById(R.id.xlate_xml_meta);
        }
    }
}
