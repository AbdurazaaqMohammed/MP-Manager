package io.github.abdurazaaqmohammed.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

public class PlayerQueueAdapter extends RecyclerView.Adapter<PlayerQueueAdapter.ViewHolder> {

    private final List<MediaItem> items;
    private final int currentIndex;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int index);
        void onRemoveClick(int index);
    }

    public PlayerQueueAdapter(List<MediaItem> items, int currentIndex, OnItemClickListener listener) {
        this.items = items;
        this.currentIndex = currentIndex;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaItem item = items.get(position);
        holder.indexView.setText(String.valueOf(position + 1));
        holder.titleView.setText(item.title != null ? item.title : "Unknown");
        holder.artistView.setText(item.artist != null ? item.artist : "");
        long dur = item.duration;
        holder.durationView.setText(dur > 0 ? String.format("%d:%02d", dur / 60000, (dur / 1000) % 60) : "--:--");
        holder.itemView.setAlpha(position == currentIndex ? 1.0f : 0.6f);
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveClick(position);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView indexView, titleView, artistView, durationView;
        final ImageButton btnRemove;
        ViewHolder(View v) {
            super(v);
            indexView = v.findViewById(R.id.indexView);
            titleView = v.findViewById(R.id.titleView);
            artistView = v.findViewById(R.id.artistView);
            durationView = v.findViewById(R.id.durationView);
            btnRemove = v.findViewById(R.id.btnRemove);
        }
    }
}
