package io.github.abdurazaaqmohammed.adapters;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.lilincpp.github.libezftp.IEZFtpClient;
import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import java.io.File;
import java.util.List;

import com.lilincpp.github.libezftp.callback.OnEZFtpDataTransferCallback;

public class FtpFilesArrayAdapter extends RecyclerView.Adapter<FtpFilesArrayAdapter.ViewHolder> {

    private final MainActivity context;
    private final FTPFileWrapper[] values;
    private final boolean pane1;
    private final IEZFtpClient ftpClient;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
    private final DialogUtil dialogUtil;

    public FtpFilesArrayAdapter(MainActivity context, FTPFileWrapper[] values, boolean pane1, IEZFtpClient ftpClient) {
        this.context = context;
        this.values = values;
        this.pane1 = pane1;
        this.ftpClient = ftpClient;
        this.dialogUtil = new DialogUtil(context);
    }

    public FTPFileWrapper getItem(int position) { return values[position]; }

    @Override
    public int getItemCount() { return values.length; }

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
        FTPFileWrapper file = values[position];
        holder.fileNameView.setText(file.getName());

        Drawable ic = ResourcesCompat.getDrawable(context.rss, file.isDirectory() ? R.drawable.ic_folder_mt : R.drawable.baseline_insert_drive_file_24, context.getTheme());
        holder.fileIconView.setImageDrawable(ic);

        if (file.getName().equals("..")) {
            holder.fileDateView.setText("");
        } else {
            String sizeStr = file.isDirectory() ? "" : FileSize.getHumanReadableFileSize(file.length());
            Date date = file.getFtpFile().getModifiedDate();
            String dateStr = date != null ? dateFormat.format(date) : "";
            holder.fileDateView.setText(dateStr + " " + sizeStr);
        }

        holder.itemView.setOnClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            context.loadFolderInPane(file, pane1, true);
        });

        holder.itemView.setOnLongClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            if (!file.getName().equals("..")) {
                showContextMenu(holder.itemView, file);
            }
            return true;
        });
    }

    private void showContextMenu(View anchor, FTPFileWrapper file) {
        PopupMenu popupMenu = new PopupMenu(context, anchor);
        String cp = context.rss.getString(android.R.string.copy);
        String mv = context.rss.getString(R.string.move);
        String rn = context.rss.getString(R.string.rename);
        String dl = context.rss.getString(R.string.delete);
        String props = context.rss.getString(R.string.properties);
        popupMenu.getMenu().add(cp);
        popupMenu.getMenu().add(mv);
        popupMenu.getMenu().add(rn);
        popupMenu.getMenu().add(dl);
        popupMenu.getMenu().add(props);

        popupMenu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (TextUtils.isEmpty(title)) return true;
            String string = title.toString();
            if (string.equals(cp)) {
                copyToLocal(file, false);
            } else if (string.equals(mv)) {
                copyToLocal(file, true);
            } else if (string.equals(rn)) {
                showRenameDialog(file);
            } else if (string.equals(dl)) {
                showDeleteDialog(file);
            } else if (string.equals(props)) {
                showPropertiesDialog(file);
            }
            return true;
        });
        popupMenu.show();
    }

    private void showRenameDialog(FTPFileWrapper file) {
        EditText input = new EditText(context);
        input.setText(file.getName());
        dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.rename))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = input.getText().toString();
                    if (!newName.isEmpty() && !newName.equals(file.getName())) {
                        String oldPath = file.getParent() + "/" + file.getName();
                        String newPath = file.getParent() + "/" + newName;
                        ftpClient.rename(oldPath, newPath, new OnEZFtpCallBack<>() {
                            @Override
                            public void onSuccess(Void response) {
                                context.runOnUiThread(() -> {
                                    Extensions.showMessage(context, R.string.renamed_successfully);
                                    context.reloadCurrentFolder();
                                });
                            }

                            @Override
                            public void onFail(int code, String msg) {
                                Extensions.showMessage(context, context.rss.getString(R.string.rename_failed, msg));
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(FTPFileWrapper file) {
        dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.delete_confirm, file.getName()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String path = file.getParent() + "/" + file.getName();
                    OnEZFtpCallBack<Void> callback = new OnEZFtpCallBack<>() {
                        @Override
                        public void onSuccess(Void response) {
                            context.runOnUiThread(() -> {
                                Extensions.showMessage(context, R.string.deleted_successfully);
                                context.reloadCurrentFolder();
                            });
                        }

                        @Override
                        public void onFail(int code, String msg) {
                            Extensions.showMessage(context, context.rss.getString(R.string.delete_failed, msg));
                        }
                    };
                    
                    if (file.isDirectory()) {
                        ftpClient.deleteDirectory(path, callback);
                    } else {
                        ftpClient.deleteFile(path, callback);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPropertiesDialog(FTPFileWrapper file) {
        String props = "Name: " + file.getName() + "\n" +
                "Path: " + file.getParent() + "/" + file.getName() + "\n" +
                "Size: " + Formatter.formatFileSize(context, file.length()) + "\n" +
                "Type: " + (file.isDirectory() ? "Directory" : "File");
                
        dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.properties))
                .setMessage(props)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void copyToLocal(FTPFileWrapper file, boolean isMove) {
        if (file.isDirectory()) {
            Extensions.showMessage(context, "Directory copy not fully supported yet");
            return;
        }
        File destFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        String destPath = destFolder.getAbsolutePath() + "/" + file.getName();
        Extensions.showMessage(context, (isMove ? "Moving " : "Copying ") + file.getName() + " to " + destFolder.getName());

        ftpClient.downloadFile(file.getFtpFile(), destPath, new OnEZFtpDataTransferCallback() {
            @Override
            public void onStateChanged(int state) { }

            @Override
            public void onTransferred(long totalSize, int transferredSize) { }

            @Override
            public void onErr(int code, String msg) {
                Extensions.showMessage(context, context.rss.getString(R.string.download_failed, msg));
            }

            public void onErr(int code, Exception e) {
                Extensions.showMessage(context, context.rss.getString(R.string.download_failed, e.getMessage()));
            }
        });

        if (isMove) {
            String path = file.getParent() + "/" + file.getName();
            ftpClient.deleteFile(path, new OnEZFtpCallBack<>() {
                @Override
                public void onSuccess(Void response) {
                    context.runOnUiThread(context::reloadCurrentFolder);
                }

                @Override
                public void onFail(int code, String msg) {
                }
            });
        }
    }

    public void uploadFiles(List<Object> items) {
        for (Object item : items) {
            if (item instanceof File && !((File) item).isDirectory()) {
                File localFile = (File) item;

                Extensions.showMessage(context, context.getString(R.string.uploading, localFile.getName()));
                ftpClient.uploadFile(localFile.getAbsolutePath(), new OnEZFtpDataTransferCallback() {
                    @Override
                    public void onStateChanged(int state) { 
                        if (state == COMPLETED) {
                            context.runOnUiThread(() -> {
                                String parent = values[1].getParent();
                                context.fetchFtpDirAndLoad(TextUtils.isEmpty(parent) ? File.separator : parent, pane1);
                            });
                        }
                    }
                    @Override
                    public void onTransferred(long totalSize, int transferredSize) { }
                    @Override
                    public void onErr(int code, String msg) { }
                    public void onErr(int code, Exception e) { }
                });
            }
        }
    }
}
