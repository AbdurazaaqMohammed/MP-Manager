package io.github.abdurazaaqmohammed.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;

import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;

public class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final MainActivity context;
    private final List<FileMenuOrder.MenuItem> items;
    private final boolean isInZip;
    private final OnItemClickListener listener;

    public DialogAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean isInZip, OnItemClickListener listener) {
        this.context = context;
        this.items = items;
        this.isInZip = isInZip;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_file_menu_action, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileMenuOrder.MenuItem item = items.get(position);
        holder.label.setText(item.label());
        holder.icon.setImageResource(FileMenuOrder.iconFor(context, item.id(), false, false));
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true);
        DrawableCompat.setTint(holder.icon.getDrawable(), tv.data);
        boolean disabled = (FileMenuOrder.MOVE.equals(item.id()) && context.pane1Folder == context.pane2Folder)
                || ((FileMenuOrder.COMPRESS.equals(item.id()) || FileMenuOrder.BOOKMARK.equals(item.id())) && isInZip);
        holder.itemView.setAlpha(disabled ? 0.38f : 1f);
        holder.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onItemClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.menuItemIcon);
            label = itemView.findViewById(R.id.menuItemLabel);
        }
    }
}
