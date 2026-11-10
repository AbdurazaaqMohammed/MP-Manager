package io.github.abdurazaaqmohammed.adapters;

import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;

import com.lilincpp.github.libezftp.IEZFtpClient;
import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;

import java.util.HashSet;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.adapters.main.ApkManifestEditor;
import io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler;
import io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.adapters.main.FileIconLoader;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.adapters.main.FileRowStyle;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.features.files.network.NetworkFileMenu;
import io.github.abdurazaaqmohammed.utils.SafFile;
import io.github.abdurazaaqmohammed.features.files.FileOpener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.lilincpp.github.libezftp.callback.OnEZFtpDataTransferCallback;

public class FtpFilesArrayAdapter extends RecyclerView.Adapter<FtpFilesArrayAdapter.ViewHolder> {

    /** Built on first use; same dispatch the regular pane uses to open files. */
    private FileOpener opener;

    private synchronized FileOpener opener() {
        if (opener == null) {
            DialogUtil dialogUtil = new DialogUtil(context);
            ApkManifestEditor manifestEditor = new ApkManifestEditor(context, dialogUtil, context.uiHelper);
            ApkToolsHandler apkTools = new ApkToolsHandler(context, dialogUtil, context.uiHelper, pane1, manifestEditor);
            opener = new FileOpener(context, dialogUtil, pane1, apkTools, new ChecksumDialogs(context, dialogUtil), null);
        }
        return opener;
    }

    private final MainActivity context;
    private final FTPFileWrapper[] values;
    private final boolean pane1;
    private final IEZFtpClient ftpClient;
    private final FileRowStyle rowStyle;
    private final FileIconLoader icons;
    private final DialogUtil dialogUtil;

    public FtpFilesArrayAdapter(MainActivity context, FTPFileWrapper[] values, boolean pane1, IEZFtpClient ftpClient) {
        this.context = context;
        this.values = values;
        this.pane1 = pane1;
        this.ftpClient = ftpClient;
        this.dialogUtil = new DialogUtil(context);
        rowStyle = new FileRowStyle(context);
        icons = new FileIconLoader(context, false, true);
    }

    public FTPFileWrapper getItem(int position) { return values[position]; }

    /**
     * Same reasoning as the SAF adapter: the recycled-holder pool is keyed by
     * view type and outlives an adapter swap, so sharing type 0 with the pane's
     * file adapter could hand an FTP row a foreign ViewHolder.
     */
    private static final int VIEW_TYPE = 0x465450; // "FTP"

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE;
    }

    @Override
    public int getItemCount() { return values.length; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView fileNameView, fileDateView;
        final ImageView fileIconView, badge;
        ViewHolder(View v) {
            super(v);
            fileNameView = v.findViewById(R.id.fileName);
            fileIconView = v.findViewById(R.id.fileIcon);
            fileDateView = v.findViewById(R.id.fileDate);
            badge = v.findViewById(R.id.appBadge);
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
        rowStyle.apply(holder.fileNameView, holder.fileIconView);
        holder.fileNameView.setText(file.getName());
        holder.badge.setVisibility(View.GONE);
        holder.badge.setImageDrawable(null);
        holder.badge.setTag(null);
        boolean up = "..".equals(file.getName());
        icons.setupMetadataView(file.getName(), file.isDirectory(), file.length(), file.lastModified(),
                up, holder.fileIconView, holder.fileDateView);
        float alpha = !up && file.getName().startsWith(".") ? 0.55f : 1f;
        holder.fileNameView.setAlpha(alpha);
        holder.fileIconView.setAlpha(alpha);

        holder.itemView.setOnClickListener(v -> {
            context.setCurrentPane(pane1 ? 1 : 2);
            // A folder navigates like any other pane; a file is downloaded to
            // the app cache and opened through the regular dispatch (editors,
            // viewers, archive browser all work on the downloaded copy).
            if (up || file.isDirectory()) context.loadFolderInPane(file, pane1, true);
            else downloadAndOpen(file);
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
        Set<String> disabled = new HashSet<>();
        if (file.isDirectory() || !hasLocalDestination()) {
            disabled.add(FileMenuOrder.COPY);
            disabled.add(FileMenuOrder.MOVE);
        }
        NetworkFileMenu.show(context, file.getName(), pane1, disabled, action -> {
            switch (action) {
                case FileMenuOrder.COPY -> copyToLocal(file, false);
                case FileMenuOrder.MOVE -> copyToLocal(file, true);
                case FileMenuOrder.RENAME -> showRenameDialog(file);
                case FileMenuOrder.DELETE -> showDeleteDialog(file);
                case FileMenuOrder.PROPERTIES -> showPropertiesDialog(file);
            }
        });
    }

    private boolean hasLocalDestination() {
        File folder = pane1 ? context.pane2Folder : context.pane1Folder;
        MainFilesArrayAdapter adapter = context.paneFilesAdapter(!pane1);
        return adapter != null && !adapter.isInZip && folder != null
                && !(folder instanceof SafFile) && !(folder instanceof FTPFileWrapper)
                && !(folder instanceof io.github.abdurazaaqmohammed.features.files.network.RemoteFile);
    }

    private void showRenameDialog(FTPFileWrapper file) {
        EditText input = new EditText(context);
        input.setText(file.getName());
        dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.rename))
                .setView(UiFields.wrap(context, input, null, 16))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = input.getText().toString();
                    if (!newName.isEmpty() && !newName.equals(file.getName())) {
                        try { io.github.abdurazaaqmohammed.features.files.network.RemotePath.validateName(newName); }
                        catch (IllegalArgumentException e) { new io.github.abdurazaaqmohammed.utils.ErrorUtil(context).showError(e); return; }
                        String oldPath = file.remotePath();
                        String newPath = file.remoteParent() + (file.remoteParent().endsWith("/") ? "" : "/") + newName;
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
                    String path = file.remotePath();
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
                "Path: " + file.displayPath() + "\n" +
                "Size: " + Formatter.formatFileSize(context, file.length()) + "\n" +
                "Type: " + (file.isDirectory() ? "Directory" : "File");
                
        dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.properties))
                .setMessage(props)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Downloads a file into the cache and opens it with the app's regular open dispatch. */
    private void downloadAndOpen(FTPFileWrapper file) {
        File[] opened = {null};
        context.getNetwork().runOperation(context.getString(R.string.network_downloading), () -> {
            io.github.abdurazaaqmohammed.features.files.network.RemotePath.validateName(file.getName());
            File dir = new File(context.getCacheDir(), "ftp_" + java.util.UUID.randomUUID());
            if (!dir.mkdir()) throw new IOException("Cannot create download cache");
            opened[0] = new File(dir, file.getName());
            try { download(file, opened[0]); }
            catch (Exception e) { io.github.abdurazaaqmohammed.utils.FileUtils.deleteTree(dir, null); throw e; }
        }, () -> opener().handleFileClick(opened[0], file.getName()));
    }

    private void copyToLocal(FTPFileWrapper file, boolean isMove) {
        if (file.isDirectory() || !hasLocalDestination()) return;
        File destFolder = pane1 ? context.pane2Folder : context.pane1Folder;
        context.getNetwork().runOperation(context.getString(isMove ? R.string.move : android.R.string.copy), () -> {
            io.github.abdurazaaqmohammed.features.files.network.RemotePath.validateName(file.getName());
            File destination = new File(destFolder, file.getName());
            if (destination.exists()) throw new IOException("Destination already exists");
            File part = File.createTempFile(".ftp-", ".part", destFolder);
            try {
                download(file, part);
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Transfer cancelled");
                java.nio.file.Files.move(part.toPath(), destination.toPath());
                if (isMove) {
                    CountDownLatch deleted = new CountDownLatch(1);
                    String[] failure = {null};
                    ftpClient.deleteFile(file.remotePath(), new OnEZFtpCallBack<>() {
                        @Override public void onSuccess(Void response) { deleted.countDown(); }
                        @Override public void onFail(int code, String message) { failure[0] = message; deleted.countDown(); }
                    });
                    if (!deleted.await(60, TimeUnit.SECONDS)) throw new IOException("Source deletion timed out");
                    if (failure[0] != null) throw new IOException(failure[0]);
                }
            } finally { if (part.exists()) part.delete(); }
        }, context::refreshAllPanes);
    }

    private void download(FTPFileWrapper file, File output) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        boolean[] completed = {false};
        String[] failure = {null};
        ftpClient.downloadFile(file.getFtpFile(), output.getAbsolutePath(), new OnEZFtpDataTransferCallback() {
            @Override public void onStateChanged(int state) {
                if (state == COMPLETED) { completed[0] = true; finished.countDown(); }
                else if (state == ERROR || state == ABORTED) finished.countDown();
            }
            @Override public void onTransferred(long total, int bytes) {}
            @Override public void onErr(int code, String message) { failure[0] = message; finished.countDown(); }
        });
        if (!finished.await(60, TimeUnit.SECONDS)) throw new IOException("FTP download timed out");
        if (!completed[0]) throw new IOException(failure[0] == null ? "FTP download failed" : failure[0]);
        if (!output.isFile() || output.length() != file.length()) throw new IOException("Incomplete FTP download");
    }

    public void uploadFiles(List<Object> items) {
        for (Object item : items) {
            if (item instanceof File localFile && !localFile.isDirectory()) {

                Extensions.showMessage(context, context.getString(R.string.uploading, localFile.getName()));
                String parent = values.length > 0 ? values[0].remoteParent() : "/";
                ftpClient.uploadFile(localFile.getAbsolutePath(), parent + (parent.endsWith("/") ? "" : "/") + localFile.getName(), new OnEZFtpDataTransferCallback() {
                    @Override
                    public void onStateChanged(int state) { 
                        if (state == COMPLETED) {
                            context.runOnUiThread(() -> {
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
