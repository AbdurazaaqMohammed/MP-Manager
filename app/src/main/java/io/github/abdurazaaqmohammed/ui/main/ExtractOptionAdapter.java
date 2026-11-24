package io.github.abdurazaaqmohammed.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ExtractOptionAdapter extends RecyclerView.Adapter<ExtractOptionAdapter.OptionHolder> {

    public interface OnOptionClickListener {
        void onOptionClick(int position);
    }

    private final LayoutInflater inflater;
    private final String[] options;
    private final int[] icons;
    private final OnOptionClickListener listener;

    public ExtractOptionAdapter(Context context, String[] options, int[] icons, OnOptionClickListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.options = options;
        this.icons = icons;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OptionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new OptionHolder(inflater.inflate(R.layout.item_dropdown_option, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull OptionHolder holder, int position) {
        holder.icon.setImageResource(icons[position]);
        holder.text.setText(options[position]);
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onOptionClick(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return options.length;
    }

    static final class OptionHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;

        OptionHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.optionIcon);
            text = itemView.findViewById(R.id.optionText);
        }
    }
}
