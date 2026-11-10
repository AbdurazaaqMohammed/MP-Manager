package io.github.abdurazaaqmohammed.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.ApkManifestEditor;
import io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler;
import io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.adapters.main.FileIconLoader;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.adapters.main.FileRowStyle;
import io.github.abdurazaaqmohammed.features.files.FileOpener;
import io.github.abdurazaaqmohammed.features.files.network.RemoteFile;
import io.github.abdurazaaqmohammed.features.files.network.RemotePath;
import io.github.abdurazaaqmohammed.features.files.network.NetworkFileMenu;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.utils.FileSize;

public final class RemoteFilesArrayAdapter extends RecyclerView.Adapter<RemoteFilesArrayAdapter.Holder> {
    private static final int VIEW_TYPE = 0x4e4554;
    private final MainActivity activity;
    private final boolean pane1;
    public final RemoteFile folder;
    public final RemoteFile[] files;
    private final FileRowStyle rowStyle;
    private final FileIconLoader icons;

    public RemoteFilesArrayAdapter(MainActivity activity, RemoteFile folder, RemoteFile[] files, boolean pane1) {
        this.activity = activity;
        this.folder = folder;
        this.files = files;
        this.pane1 = pane1;
        rowStyle = new FileRowStyle(activity);
        icons = new FileIconLoader(activity, false, true);
    }

    public void goUp() {
        RemoteFile parent = folder.getParentFile();
        if (parent != null) activity.loadFolderInPane(parent, pane1, true);
        else activity.loadFolderInPane(android.os.Environment.getExternalStorageDirectory(), pane1, true);
    }

    @Override public int getItemCount() { return files.length + 1; }
    @Override public int getItemViewType(int position) { return VIEW_TYPE; }
    public static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, detail;
        final ImageView icon, badge;
        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.fileName);
            detail = view.findViewById(R.id.fileDate);
            icon = view.findViewById(R.id.fileIcon);
            badge = view.findViewById(R.id.appBadge);
        }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(activity).inflate(R.layout.list_file, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        rowStyle.apply(holder.name, holder.icon);
        holder.badge.setVisibility(View.GONE);
        holder.badge.setImageDrawable(null);
        holder.badge.setTag(null);
        if (position == 0) {
            holder.name.setText("..");
            holder.name.setAlpha(1f);
            holder.icon.setAlpha(1f);
            icons.setupMetadataView("..", true, 0, 0, true, holder.icon, holder.detail);
            holder.itemView.setOnClickListener(v -> { activity.setCurrentPane(pane1 ? 1 : 2); goUp(); });
            holder.itemView.setOnLongClickListener(null);
            return;
        }
        RemoteFile file = files[position - 1];
        holder.name.setText(file.getName());
        icons.setupMetadataView(file.getName(), file.isDirectory(), file.length(), file.lastModified(),
                false, holder.icon, holder.detail);
        holder.name.setAlpha(file.isHidden() ? 0.55f : 1f);
        holder.icon.setAlpha(file.isHidden() ? 0.55f : 1f);
        holder.itemView.setOnClickListener(v -> {
            activity.setCurrentPane(pane1 ? 1 : 2);
            if (file.isDirectory()) activity.loadFolderInPane(file, pane1, true);
            else activity.getNetwork().open(file, opened -> openLocal(opened, file.getName()));
        });
        holder.itemView.setOnLongClickListener(v -> {
            activity.setCurrentPane(pane1 ? 1 : 2);
            Set<String> disabled = new HashSet<>();
            if (!activity.getNetwork().canCopyOut(file, pane1)) {
                disabled.add(FileMenuOrder.COPY);
                disabled.add(FileMenuOrder.MOVE);
            }
            NetworkFileMenu.show(activity, file.getName(), pane1, disabled, action -> {
                switch (action) {
                    case FileMenuOrder.COPY -> activity.getNetwork().copyOut(file, pane1, false);
                    case FileMenuOrder.MOVE -> activity.getNetwork().copyOut(file, pane1, true);
                    case FileMenuOrder.RENAME -> rename(file);
                    case FileMenuOrder.DELETE -> activity.dialogUtil.getDialogBuilder().setTitle(activity.getString(R.string.delete_confirm, file.getName()))
                            .setMessage(R.string.network_recursive_delete)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok, (d, w) -> activity.getNetwork().runOperation(
                                    activity.getString(R.string.delete), () -> file.connection.client.delete(file.entry.path, file.isDirectory()),
                                    activity::refreshAllPanes)).show();
                    case FileMenuOrder.PROPERTIES -> activity.dialogUtil.getDialogBuilder().setTitle(R.string.properties)
                            .setMessage(file.displayPath() + "\n" + (file.isDirectory() ? activity.getString(R.string.folder)
                                    : FileSize.getHumanReadableFileSize(file.length())))
                            .setPositiveButton(android.R.string.ok, null).show();
                }
            });
            return true;
        });
    }

    private void rename(RemoteFile file) {
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setText(file.getName());
        activity.dialogUtil.getDialogBuilder().setTitle(R.string.rename)
                .setView(UiFields.wrap(activity, name, null, 16)).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> activity.getNetwork().runOperation(activity.getString(R.string.rename),
                        () -> file.connection.client.rename(file.entry.path, RemotePath.child(folder.entry.path, name.getText().toString())),
                        activity::refreshAllPanes)).show();
    }

    private void openLocal(File file, String name) {
        ApkManifestEditor manifest = new ApkManifestEditor(activity, activity.dialogUtil, activity.uiHelper);
        ApkToolsHandler tools = new ApkToolsHandler(activity, activity.dialogUtil, activity.uiHelper, pane1, manifest);
        new FileOpener(activity, activity.dialogUtil, pane1, tools,
                new ChecksumDialogs(activity, activity.dialogUtil), null).handleFileClick(file, name);
    }
}
