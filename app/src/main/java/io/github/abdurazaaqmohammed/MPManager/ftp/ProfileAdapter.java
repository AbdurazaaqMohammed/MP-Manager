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
    private List<FtpProfile> profiles;
    private OnProfileClickListener listener;
    private Runnable onAddClick;

    public interface OnProfileClickListener {
        void onProfileClick(FtpProfile profile);
    }

    public ProfileAdapter(List<FtpProfile> profiles, OnProfileClickListener listener, Runnable onAddClick) {
        this.profiles = new ArrayList<>(profiles);
        this.listener = listener;
        this.onAddClick = onAddClick;
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
        } else {
            // This is the "Add" button
            holder.itemView.setOnClickListener(v -> onAddClick.run());
            holder.name.setText("Add New Profile");
            holder.details.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return profiles.size() + 1; // +1 for the add button
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView details;

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