package io.github.abdurazaaqmohammed.adapters;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.SafFile;
import io.github.abdurazaaqmohammed.utils.SafFs;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * The pane listing for a Storage Access Framework location, the counterpart of
 * {@link FtpFilesArrayAdapter}: the same rows as an on-disk folder, with the
 * actions a provider can actually honour — open, copy out to a real folder,
 * copy in from the other pane, rename, delete, new folder.
 *
 * <p>Clicking a folder navigates through {@link MainActivity#loadFolderInPane}
 * exactly like a directory on disk, so the breadcrumb, the history and the
 * back gesture keep working.</p>
 */
public class SafFilesArrayAdapter extends RecyclerView.Adapter<SafFilesArrayAdapter.ViewHolder> {

    private final MainActivity context;
    private final SafFile[] values;
    private final boolean pane1;
    private final DialogUtil dialogUtil;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    public SafFilesArrayAdapter(MainActivity context, SafFile[] values, boolean pane1) {
        this.context = context;
        this.values = values;
        this.pane1 = pane1;
        this.dialogUtil = new DialogUtil(context);
    }

    public SafFile getItem(int position) {
        return values[position];
    }

    /**
     * A view type of its own, deliberately not the 0 the pane's file adapter
     * uses.
     *
     * <p>RecyclerView keeps its recycled holders in a pool keyed by view type
     * for the whole lifetime of the view, so swapping adapters does not empty
     * it. With a shared type the SAF adapter was handed a
     * {@code MainFilesArrayAdapter$ViewHolder} and crashed with
     * {@code ClassCastException} on the first bind. Distinct types keep the
     * two pools apart in both directions.</p>
     */
    private static final int VIEW_TYPE = 0x534146; // "SAF"

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView fileNameView, fileDateView;
        final ImageView fileIconView;

        ViewHolder(View v) {
            super(v);
            fileNameView = v.findViewById(R.id.fileName);
            fileIconView = v.findViewById(R.id.fileIcon);
            fileDateView = v.findViewById(R.id.fileDate);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_file, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SafFile file = values[position];
        holder.fileNameView.setText(file.getName());
        Drawable icon = ResourcesCompat.getDrawable(context.rss,
                file.isDirectory() ? R.drawable.ic_folder_mt : R.drawable.baseline_insert_drive_file_24,
                context.getTheme());
        holder.fileIconView.setImageDrawable(icon);
        if (file.getName().equals("..")) {
            holder.fileDateView.setText("");
        } else {
            String size = file.isDirectory() ? "" : Formatter.formatShortFileSize(context, file.length());
            String date = file.lastModified() > 0 ? dateFormat.format(new Date(file.lastModified())) : "";
            holder.fileDateView.setText((date + " " + size).trim());
        }

        holder.itemView.setOnClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            // A folder navigates like any other pane; a file opens in whatever
            // app can display it (the pane's on-disk actions all stream by
            // path, which a provider document does not have).
            if (file.isDirectory()) context.loadFolderInPane(file, pane1, true);
            else openDocument(file);
        });
        holder.itemView.setOnLongClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            if (!file.getName().equals("..")) showContextMenu(holder.itemView, file);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return values.length;
    }

    private void showContextMenu(View anchor, SafFile file) {
        PopupMenu menu = new PopupMenu(context, anchor);
        String open = context.getString(R.string.docs_open);
        String copy = context.getString(R.string.docs_copy_out);
        String copyIn = context.getString(R.string.docs_copy_in);
        String rename = context.getString(R.string.rename);
        String delete = context.getString(R.string.delete);
        String props = context.getString(R.string.properties);
        String newFolder = context.getString(R.string.docs_new_folder);
        boolean dir = file.isDirectory();
        if (!dir) menu.getMenu().add(open);
        if (!dir) menu.getMenu().add(copy);
        menu.getMenu().add(copyIn);
        if (file.canWrite()) {
            menu.getMenu().add(rename);
            menu.getMenu().add(delete);
            if (dir) menu.getMenu().add(newFolder);
        }
        menu.getMenu().add(props);
        menu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (TextUtils.isEmpty(title)) return true;
            String picked = title.toString();
            if (picked.equals(open)) openDocument(file);
            else if (picked.equals(copy)) copyOut(file);
            else if (picked.equals(copyIn)) copyIn(file);
            else if (picked.equals(rename)) rename(file);
            else if (picked.equals(delete)) delete(file);
            else if (picked.equals(newFolder)) newFolder(file);
            else if (picked.equals(props)) properties(file);
            return true;
        });
        menu.show();
    }

    /** Hands the document to whatever app can display it, with a read grant. */
    private void openDocument(SafFile file) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(file.getDocumentUri(),
                        TextUtils.isEmpty(file.getMimeType()) ? "*/*" : file.getMimeType())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Extensions.showMessage(context, context.getString(R.string.docs_no_viewer));
        } catch (Exception e) {
            Extensions.showMessage(context, context.getString(R.string.docs_error));
        }
    }

    /** Copies a document out to a real folder on disk. */
    private void copyOut(SafFile file) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        props.root = new File("/storage/emulated/0");
        props.offset = props.root;
        FilePickerDialog picker = new FilePickerDialog(context, props);
        picker.setTitle(context.getString(R.string.docs_copy_out));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            File target = new File(files[0], file.getName());
            copyOutTo(file, target);
        });
        picker.show();
    }

    private void copyOutTo(SafFile file, File target) {
        new Thread(() -> {
            String error = null;
            try {
                SafFs.copyTo(file, target);
            } catch (IOException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String fError = error;
            context.runOnUiThread(() -> {
                if (fError != null) Extensions.showMessage(context, context.getString(R.string.docs_copy_failed, fError));
                else Extensions.showMessage(context, context.getString(R.string.docs_copied, target.getAbsolutePath()));
            });
        }).start();
    }

    /** Copies a real file from disk into this provider folder. */
    private void copyIn(SafFile folder) {
        File other = pane1 ? context.pane2Folder : context.pane1Folder;
        // The picker browses the real filesystem, so a provider folder on the
        // other side gives it nothing to show.
        if (other == null || !other.isDirectory() || SafFs.isSaf(other)) {
            Extensions.showMessage(context, context.getString(R.string.docs_copy_in_hint));
            return;
        }
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.root = other;
        props.offset = other;
        FilePickerDialog picker = new FilePickerDialog(context, props);
        picker.setTitle(context.getString(R.string.docs_copy_in));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            copyInFrom(new File(files[0]), folder);
        });
        picker.show();
    }

    private void copyInFrom(File source, SafFile folder) {
        if (source.isDirectory()) {
            Extensions.showMessage(context, context.getString(R.string.docs_copy_in_dir));
            return;
        }
        new Thread(() -> {
            String error = null;
            try {
                SafFs.copyFrom(source, folder, null);
            } catch (IOException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String fError = error;
            context.runOnUiThread(() -> {
                if (fError != null) Extensions.showMessage(context, context.getString(R.string.docs_copy_failed, fError));
                else {
                    Extensions.showMessage(context, context.getString(R.string.docs_copied_in, source.getName()));
                    context.loadFolderInPane(folder, pane1, false);
                }
            });
        }).start();
    }

    private void rename(SafFile file) {
        EditText input = new EditText(context);
        input.setText(file.getName());
        dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.rename))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.equals(file.getName())) return;
                    new Thread(() -> {
                        boolean ok = SafFs.rename(file, name);
                        context.runOnUiThread(() -> {
                            if (!ok) Extensions.showMessage(context, context.getString(R.string.docs_rename_failed));
                            context.loadFolderInPane(file.getParentFile(), pane1, false);
                        });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void delete(SafFile file) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.delete))
                .setMessage(file.getName())
                .setPositiveButton(android.R.string.ok, (d, w) -> new Thread(() -> {
                    boolean ok = SafFs.delete(file);
                    context.runOnUiThread(() -> {
                        if (!ok) Extensions.showMessage(context, context.getString(R.string.docs_delete_failed));
                        context.loadFolderInPane(file.getParentFile(), pane1, false);
                    });
                }).start())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void newFolder(SafFile parent) {
        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.docs_new_folder));
        dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.docs_new_folder))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    new Thread(() -> {
                        boolean ok = SafFs.createDirectory(parent, name);
                        context.runOnUiThread(() -> {
                            if (!ok) Extensions.showMessage(context, context.getString(R.string.docs_create_failed));
                            context.loadFolderInPane(parent, pane1, false);
                        });
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void properties(SafFile file) {
        String text = file.getName()
                + "\n" + context.getString(R.string.docs_prop_type, file.isDirectory()
                        ? context.getString(R.string.docs_prop_folder)
                        : (TextUtils.isEmpty(file.getMimeType()) ? "?" : file.getMimeType()))
                + "\n" + context.getString(R.string.docs_prop_size,
                        file.isDirectory() ? "-" : Formatter.formatShortFileSize(context, file.length()))
                + "\n" + context.getString(R.string.docs_prop_modified,
                        file.lastModified() > 0 ? dateFormat.format(new Date(file.lastModified())) : "-")
                + "\n" + context.getString(R.string.docs_prop_id, file.getDocId());
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.properties))
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
