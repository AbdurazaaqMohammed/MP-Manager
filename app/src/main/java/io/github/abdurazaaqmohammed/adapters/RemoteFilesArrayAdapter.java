package io.github.abdurazaaqmohammed.adapters;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.utils.FileSize;

/**
 * Renders a {@link RemoteEntry} listing in one of the two panes.
 *
 * <p>Modelled on {@link FtpFilesArrayAdapter} so remote browsing looks identical
 * to the rest of the file browser. Unlike it, this adapter holds no protocol
 * client: everything user-facing goes through a callback, which keeps the
 * protocol out of the view layer and is what lets one adapter serve FTP, WebDAV
 * and eventually S3.
 *
 * <p>{@link #PARENT} is a synthetic entry that navigates up. It is inserted by
 * the controller rather than by the server, matching what the FTP listing does.
 */
public class RemoteFilesArrayAdapter extends RecyclerView.Adapter<RemoteFilesArrayAdapter.ViewHolder> {

    /** Display name of the synthetic "go up" row. */
    public static final String PARENT_NAME = "..";

    /** Callbacks the pane controller supplies. */
    public interface Callbacks {
        /** Enter a directory. */
        void onEnterDirectory(RemoteEntry entry, boolean pane1);

        /** Navigate to the parent. */
        void onNavigateUp(boolean pane1);

        /** Long-press on a real entry; the synthetic parent row has no menu. */
        void onEntryMenu(RemoteEntry entry, View anchor, boolean pane1);
    }

    private final MainActivity context;
    private final List<RemoteEntry> values;
    private final boolean pane1;
    private final Callbacks callbacks;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    public RemoteFilesArrayAdapter(MainActivity context, List<RemoteEntry> values,
                                   boolean pane1, Callbacks callbacks) {
        this.context = context;
        this.values = values;
        this.pane1 = pane1;
        this.callbacks = callbacks;
    }

    public RemoteEntry getItem(int position) {
        return values.get(position);
    }

    public boolean isParentRow(int position) {
        RemoteEntry e = values.get(position);
        return e != null && PARENT_NAME.equals(e.name());
    }

    @Override
    public int getItemCount() {
        return values.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.list_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RemoteEntry entry = values.get(position);
        boolean isParent = isParentRow(position);

        holder.fileNameView.setText(entry.name());
        Drawable ic = ResourcesCompat.getDrawable(context.rss,
                entry.directory() ? R.drawable.ic_folder_mt : R.drawable.baseline_insert_drive_file_24,
                context.getTheme());
        holder.fileIconView.setImageDrawable(ic);

        if (isParent) {
            holder.fileDateView.setText("");
        } else {
            String size = (!entry.directory() && entry.size() >= 0)
                    ? FileSize.getHumanReadableFileSize(entry.size()) : "";
            String date = entry.modifiedAt() > 0
                    ? dateFormat.format(new Date(entry.modifiedAt())) : "";
            holder.fileDateView.setText(date.isEmpty() ? size : (date + " " + size));
        }

        holder.itemView.setOnClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            if (isParent) {
                callbacks.onNavigateUp(pane1);
            } else if (entry.directory()) {
                callbacks.onEnterDirectory(entry, pane1);
            }
            // Tapping a remote file has no meaning until a download is wired
            // up, so it is deliberately inert rather than half-working.
        });

        holder.itemView.setOnLongClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            if (!isParent) {
                callbacks.onEntryMenu(entry, v, pane1);
                return true;
            }
            return false;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView fileIconView;
        final TextView fileNameView;
        final TextView fileDateView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIconView = itemView.findViewById(R.id.fileIcon);
            fileNameView = itemView.findViewById(R.id.fileName);
            fileDateView = itemView.findViewById(R.id.fileDate);
        }
    }
}