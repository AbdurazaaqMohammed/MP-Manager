package io.github.abdurazaaqmohammed.adapters;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final MainActivity context;
    private final List<FileMenuOrder.MenuItem> items;
    private final boolean isInZip;
    private final boolean grid;
    private final OnItemClickListener listener;

    public DialogAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean isInZip, OnItemClickListener listener) {
        this(context, items, isInZip, false, listener);
    }

    public DialogAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean isInZip, boolean grid, OnItemClickListener listener) {
        this.context = context;
        this.items = items;
        this.isInZip = isInZip;
        this.grid = grid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(grid ? R.layout.item_file_menu_action_grid : R.layout.item_file_menu_action, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileMenuOrder.MenuItem item = items.get(position);
        holder.label.setText(item.label());
        Drawable drawable = ResourcesCompat.getDrawable(context.getResources(), FileMenuOrder.iconFor(context, item.id(), false, false), null);
        if (drawable != null) {
            int i = Extensions.dp2px(context, 24);
            drawable.setBounds(0, 0, i, i);
            DrawableCompat.setTint(drawable, MaterialColors.getColor(holder.label, com.google.android.material.R.attr.colorPrimary));
        }
        holder.label.setCompoundDrawablesRelative(drawable, null, null, null);
        holder.label.setCompoundDrawablePadding(Extensions.dp2px(context, 8));

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
        final TextView label;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.menuItemLabel);
        }
    }
}
