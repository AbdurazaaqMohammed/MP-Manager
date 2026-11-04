package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder> {
    private final List<FtpProfile> profiles;
    private final OnProfileClickListener listener;
    private final Runnable onAddClick;

    public interface OnProfileClickListener {
        void onProfileClick(FtpProfile profile);
    }

    /** Long-press on a profile row; used for the sidebar pin toggle. */
    public interface OnProfileLongClickListener {
        void onProfileLongClick(FtpProfile profile, View anchor);
    }

    private final OnProfileLongClickListener longClickListener;

    public ProfileAdapter(List<FtpProfile> profiles, OnProfileClickListener listener, Runnable onAddClick) {
        this(profiles, listener, onAddClick, null);
    }

    public ProfileAdapter(List<FtpProfile> profiles, OnProfileClickListener listener, Runnable onAddClick,
                          OnProfileLongClickListener longClickListener) {
        this.profiles = new ArrayList<>(profiles);
        this.listener = listener;
        this.onAddClick = onAddClick;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        if (position < profiles.size()) {
            FtpProfile profile = profiles.get(position);
            holder.bind(profile, listener);
            if (longClickListener != null) {
                holder.itemView.setOnLongClickListener(v -> {
                    longClickListener.onProfileLongClick(profile, v);
                    return true;
                });
            } else holder.itemView.setOnLongClickListener(null);
        } else {
            holder.itemView.setOnClickListener(v -> onAddClick.run());
            holder.name.setText(R.string.add_profile);
            holder.details.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return profiles.size() + 1; // +1 for the add button
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView details;

        public ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.profile_name);
            details = itemView.findViewById(R.id.profile_details);
        }

        public void bind(FtpProfile profile, OnProfileClickListener listener) {
            name.setText(profile.getName());
            details.setText(String.format("%s:%d (%s)", profile.getIp(), profile.getPort(), profile.getUsername()));
            itemView.setOnClickListener(v -> listener.onProfileClick(profile));
        }
    }
}