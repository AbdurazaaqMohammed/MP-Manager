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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        popupMenu.getMenu().add("Copy");
        popupMenu.getMenu().add("Move");
        popupMenu.getMenu().add("Rename");
        popupMenu.getMenu().add("Delete");
        popupMenu.getMenu().add("Properties");

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Copy":
                    copyToLocal(file, false);
                    break;
                case "Move":
                    copyToLocal(file, true);
                    break;
                case "Rename":
                    showRenameDialog(file);
                    break;
                case "Delete":
                    showDeleteDialog(file);
                    break;
                case "Properties":
                    showPropertiesDialog(file);
                    break;
            }
            return true;
        });
        popupMenu.show();
    }

    private void showRenameDialog(FTPFileWrapper file) {
        EditText input = new EditText(context);
        input.setText(file.getName());
        dialogUtil.getDialogBuilder()
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = input.getText().toString();
                    if (!newName.isEmpty() && !newName.equals(file.getName())) {
                        String oldPath = file.getParent() + "/" + file.getName();
                        String newPath = file.getParent() + "/" + newName;
                        ftpClient.rename(oldPath, newPath, new OnEZFtpCallBack<Void>() {
                            @Override
                            public void onSuccess(Void response) {
                                context.runOnUiThread(() -> {
                                    Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show();
                                    context.reloadCurrentFolder();
                                });
                            }
                            @Override
                            public void onFail(int code, String msg) {
                                context.runOnUiThread(() -> Toast.makeText(context, "Rename failed: " + msg, Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(FTPFileWrapper file) {
        dialogUtil.getDialogBuilder()
                .setTitle("Delete " + file.getName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    String path = file.getParent() + "/" + file.getName();
                    OnEZFtpCallBack<Void> callback = new OnEZFtpCallBack<Void>() {
                        @Override
                        public void onSuccess(Void response) {
                            context.runOnUiThread(() -> {
                                Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show();
                                context.reloadCurrentFolder();
                            });
                        }
                        @Override
                        public void onFail(int code, String msg) {
                            context.runOnUiThread(() -> Toast.makeText(context, "Delete failed: " + msg, Toast.LENGTH_SHORT).show());
                        }
                    };
                    
                    if (file.isDirectory()) {
                        ftpClient.deleteDirectory(path, callback);
                    } else {
                        ftpClient.deleteFile(path, callback);
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showPropertiesDialog(FTPFileWrapper file) {
        String props = "Name: " + file.getName() + "\n" +
                "Path: " + file.getParent() + "/" + file.getName() + "\n" +
                "Size: " + Formatter.formatFileSize(context, file.length()) + "\n" +
                "Type: " + (file.isDirectory() ? "Directory" : "File");
                
        dialogUtil.getDialogBuilder()
                .setTitle("Properties")
                .setMessage(props)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void copyToLocal(FTPFileWrapper file, boolean isMove) {
        if (file.isDirectory()) {
            Toast.makeText(context, "Directory copy not fully supported yet", Toast.LENGTH_SHORT).show();
            return;
        }
        File destFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        String destPath = destFolder.getAbsolutePath() + "/" + file.getName();
        Toast.makeText(context, (isMove ? "Moving " : "Copying ") + file.getName() + " to " + destFolder.getName(), Toast.LENGTH_SHORT).show();

        ftpClient.downloadFile(file.getFtpFile(), destPath, new OnEZFtpDataTransferCallback() {
            @Override
            public void onStateChanged(int state) { }

            @Override
            public void onTransferred(long totalSize, int transferredSize) { }

            @Override
            public void onErr(int code, String msg) {
                context.runOnUiThread(() -> Toast.makeText(context, "Download failed: " + msg, Toast.LENGTH_SHORT).show());
            }

            public void onErr(int code, Exception e) {
                context.runOnUiThread(() -> Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        if (isMove) {
            String path = file.getParent() + "/" + file.getName();
            ftpClient.deleteFile(path, new OnEZFtpCallBack<Void>() {
                @Override
                public void onSuccess(Void response) {
                    context.runOnUiThread(() -> context.reloadCurrentFolder());
                }
                @Override
                public void onFail(int code, String msg) { }
            });
        }
    }

    public void uploadFiles(List<Object> items) {
        for (Object item : items) {
            if (item instanceof File && !((File) item).isDirectory()) {
                File localFile = (File) item;

                context.handler.post(() -> Toast.makeText(context, "Uploading " + localFile.getName(), Toast.LENGTH_SHORT).show());
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
