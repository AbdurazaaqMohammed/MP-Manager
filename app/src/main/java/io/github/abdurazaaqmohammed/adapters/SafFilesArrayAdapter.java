package io.github.abdurazaaqmohammed.adapters;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.ApkManifestEditor;
import io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler;
import io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.features.files.FileOpener;
import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SafFile;
import io.github.abdurazaaqmohammed.utils.SafFs;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * The pane listing for a Storage Access Framework location, the counterpart of
 * {@link FtpFilesArrayAdapter}: the same rows as an on-disk folder.
 *
 * <p>Clicking a folder navigates through {@link MainActivity#loadFolderInPane}
 * exactly like a directory on disk, so the breadcrumb, the history and the
 * back gesture keep working. Clicking a file runs the same
 * {@link io.github.abdurazaaqmohammed.features.files.FileOpener} dispatch a
 * regular pane uses — the document is staged into the app's cache first, so
 * the built-in text/XML editors, image viewer, APK info, archive browser and
 * everything else work on provider files, with edits streamed back on save.</p>
 *
 * <p>The long-press menu mirrors the on-disk pane: copy/move to the other pane
 * (recursively, in any direction the other pane supports), share, open with,
 * rename, delete, new file/folder, compress to a zip in the other pane and
 * extract into the other pane.</p>
 */
public class SafFilesArrayAdapter extends RecyclerView.Adapter<SafFilesArrayAdapter.ViewHolder> {

    private final MainActivity context;
    private final SafFile[] values;
    private final boolean pane1;
    private final DialogUtil dialogUtil;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
    private final FileOpener fileOpener;

    public SafFilesArrayAdapter(MainActivity context, SafFile[] values, boolean pane1) {
        this.context = context;
        this.values = values;
        this.pane1 = pane1;
        this.dialogUtil = new DialogUtil(context);
        // The same open dispatch a regular pane uses, built without a
        // FileOperationsHelper (that one needs the main pane's adapter for its
        // selection state). Dex files fall back to "open with" here.
        ApkManifestEditor manifestEditor = new ApkManifestEditor(context, dialogUtil, context.uiHelper);
        ApkToolsHandler apkTools = new ApkToolsHandler(context, dialogUtil, context.uiHelper, pane1, manifestEditor);
        this.fileOpener = new FileOpener(context, dialogUtil, pane1, apkTools,
                new ChecksumDialogs(context, dialogUtil), null);
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
            // A folder navigates like any other pane; a file goes through the
            // app's normal open dispatch (staged out of the provider first).
            if (file.isDirectory()) context.loadFolderInPane(file, pane1, true);
            else fileOpener.handleFileClick(file, file.getName());
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
        String direction = pane1 ? "->" : "<-";
        String copy = FileMenuOrder.labelFor(context, FileMenuOrder.COPY, direction);
        String move = FileMenuOrder.labelFor(context, FileMenuOrder.MOVE, direction);
        String open = context.getString(R.string.docs_open);
        String openWith = context.getString(R.string.open_with);
        String share = context.getString(R.string.share);
        String rename = context.getString(R.string.rename);
        String delete = context.getString(R.string.delete);
        String props = context.getString(R.string.properties);
        String newFolder = context.getString(R.string.docs_new_folder);
        String newFile = context.getString(R.string.saf_new_file);
        String compress = context.getString(R.string.compress);
        String extract = context.getString(R.string.extract);

        menu.getMenu().add(openWith);
        menu.getMenu().add(open);
        menu.getMenu().add(share);
        menu.getMenu().add(copy);
        menu.getMenu().add(move);
        if (file.canWrite()) {
            menu.getMenu().add(rename);
            menu.getMenu().add(delete);
        }
        if (file.isDirectory()) {
            menu.getMenu().add(newFolder);
            menu.getMenu().add(newFile);
        }
        menu.getMenu().add(compress);
        if (isArchive(file)) menu.getMenu().add(extract);
        menu.getMenu().add(props);
        menu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (TextUtils.isEmpty(title)) return true;
            String picked = title.toString();
            if (picked.equals(openWith)) openWithChooser(file);
            else if (picked.equals(share)) share(file);
            else if (picked.equals(open)) openDocument(file);
            else if (picked.equals(copy)) transferToOtherPane(file, false);
            else if (picked.equals(move)) transferToOtherPane(file, true);
            else if (picked.equals(rename)) rename(file);
            else if (picked.equals(delete)) delete(file);
            else if (picked.equals(newFolder)) newFolder(file);
            else if (picked.equals(newFile)) newFile(file);
            else if (picked.equals(compress)) compressToOtherPane(file);
            else if (picked.equals(extract)) extractToOtherPane(file);
            else if (picked.equals(props)) properties(file);
            return true;
        });
        menu.show();
    }

    private boolean isArchive(SafFile file) {
        if (file.isDirectory()) return false;
        if (ArchiveUtil.isSupportedArchive(file.getName())) return true;
        try {
            return ArchiveBrowseUtil.isBrowsable(file);
        } catch (Exception e) {
            return false;
        }
    }

    private void showDone() {
        Extensions.showMessage(context, context.getString(R.string.saf_done));
    }

    private void showError(Exception e) {
        String msg = e.getMessage() == null || e.getMessage().isEmpty() ? e.toString() : e.getMessage();
        Extensions.showMessage(context, context.getString(R.string.saf_op_failed, msg));
    }

    // ------------------------------------------------------------------
    // open / share
    // ------------------------------------------------------------------

    /** The system "open with" chooser on the document's own content URI. */
    private void openWithChooser(SafFile file) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(file.getDocumentUri(),
                        TextUtils.isEmpty(file.getMimeType()) ? "*/*" : file.getMimeType())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, file.getName()));
        } catch (Exception e) {
            Extensions.showMessage(context, context.getString(R.string.docs_no_viewer));
        }
    }

    /** Shares the document straight from the provider, no copy needed. */
    private void share(SafFile file) {
        if (file.isDirectory()) {
            Extensions.showMessage(context, context.getString(R.string.docs_copy_in_dir));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType(TextUtils.isEmpty(file.getMimeType()) ? "*/*" : file.getMimeType())
                .putExtra(Intent.EXTRA_STREAM, file.getDocumentUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, file.getName()));
        } catch (Exception e) {
            Extensions.showMessage(context, context.getString(R.string.docs_error));
        }
    }

    /** Hands the document to whatever app can display it, with a read grant. */
    private void openDocument(SafFile file) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(file.getDocumentUri(),
                        TextUtils.isEmpty(file.getMimeType()) ? "*/*" : file.getMimeType())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Extensions.showMessage(context, context.getString(R.string.docs_no_viewer));
        }
    }

    // ------------------------------------------------------------------
    // copy / move to the other pane
    // ------------------------------------------------------------------

    private File otherPaneFolder() {
        return pane1 ? context.pane2Folder : context.pane1Folder;
    }

    private RecyclerView.Adapter otherPaneAdapter() {
        return ((RecyclerView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
    }

    private void transferToOtherPane(SafFile file, boolean move) {
        File other = otherPaneFolder();
        RecyclerView.Adapter otherAdapter = otherPaneAdapter();
        if (other == null && !(otherAdapter instanceof FtpFilesArrayAdapter)) {
            Extensions.showMessage(context, context.getString(R.string.docs_copy_in_hint));
            return;
        }
        if (otherAdapter instanceof MainFilesArrayAdapter && ((MainFilesArrayAdapter) otherAdapter).isInZip) {
            // The other pane is browsing inside an archive, not a folder.
            Extensions.showMessage(context, context.getString(R.string.docs_copy_in_hint));
            return;
        }
        if (move && other != null && file.getPath().equals(other.getPath())) {
            Extensions.showMessage(context, context.getString(R.string.saf_same_location));
            return;
        }
        new Thread(() -> {
            Exception error = null;
            ProgressManager pm = null;
            try {
                boolean onUi = context instanceof android.app.Activity
                        && !((android.app.Activity) context).isFinishing();
                if (onUi) {
                    pm = new ProgressManager(context, false);
                    pm.setOperation(move ? R.string.op_moving : R.string.op_copying);
                    pm.setFile(file);
                    pm.show();
                }
                FileUtils.TransferListener listener = pm == null ? null : pm.listener();
                if (otherAdapter instanceof FtpFilesArrayAdapter) {
                    // The FTP pane uploads from real files: stage the document
                    // (or each child of the folder) into cache and hand the
                    // copies to the FTP uploader. The upload reads the staged
                    // copy, so a move can delete the source right away.
                    java.util.List<Object> toUpload = new java.util.ArrayList<>();
                    if (file.isDirectory()) {
                        java.util.List<SafFile> kids = SafFs.children(file);
                        if (kids != null) {
                            for (SafFile kid : kids) {
                                if (!kid.isDirectory()) {
                                    toUpload.add(RootStaging.stageForRead(context, kid.getPath()));
                                }
                            }
                        }
                    } else {
                        toUpload.add(RootStaging.stageForRead(context, file.getPath()));
                    }
                    if (toUpload.isEmpty()) throw new IOException(context.getString(R.string.docs_copy_in_dir));
                    ((FtpFilesArrayAdapter) otherAdapter).uploadFiles(toUpload);
                } else if (other instanceof SafFile) {
                    SafFs.copySafToSaf(file, (SafFile) other, listener);
                } else {
                    File target = FileUtils.getUnusedFile(new File(other, file.getName()));
                    if (file.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        target.mkdirs();
                        java.util.List<SafFile> kids = SafFs.children(file);
                        if (kids == null) throw new IOException("Cannot list " + file.getName());
                        for (SafFile kid : kids) SafFs.copyToRecursive(kid, new File(target, kid.getName()), listener);
                    } else {
                        SafFs.copyToRecursive(file, target, listener);
                    }
                }
                if (move) SafFs.delete(file);
            } catch (Exception e) {
                error = e;
            }
            final Exception fError = error;
            final ProgressManager fPm = pm;
            context.runOnUiThread(() -> {
                try {
                    if (fPm != null) fPm.dismiss();
                } catch (Exception ignored) {
                }
                if (fError != null) showError(fError);
                else {
                    showDone();
                    context.loadFolderInPane(file.getParentFile(), pane1, false);
                }
            });
        }).start();
    }

    // ------------------------------------------------------------------
    // compress / extract
    // ------------------------------------------------------------------

    /** Zips the item into the other pane (streamed; the zip lands in a real folder or in the provider). */
    private void compressToOtherPane(SafFile file) {
        new Thread(() -> {
            File other = otherPaneFolder();
            Exception error = null;
            File tempZip = null;
            try {
                File cacheDir = new File(context.getCacheDir(), "saf_transfer");
                //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();
                File realDir = other instanceof SafFile ? cacheDir : other;
                if (realDir == null) throw new IOException("No destination pane");
                tempZip = FileUtils.getUnusedFile(new File(realDir, file.getName() + ".zip"));
                try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)))) {
                    zipAdd(zos, file, file.isDirectory() ? "" : file.getName());
                }
                if (other instanceof SafFile) {
                    SafFs.copyFrom(tempZip, (SafFile) other, "application/zip");
                    //noinspection ResultOfMethodCallIgnored
                    tempZip.delete();
                    tempZip = null;
                }
            } catch (Exception e) {
                error = e;
            }
            final Exception fError = error;
            final File fTemp = tempZip;
            context.runOnUiThread(() -> {
                if (fTemp != null) {
                    //noinspection ResultOfMethodCallIgnored
                    fTemp.delete();
                }
                if (fError != null) showError(fError);
                else {
                    showDone();
                    File other1 = otherPaneFolder();
                    if (other1 != null && !SafFs.isSaf(other1)) {
                        // The zip was written into a plain folder: refresh whichever
                        // pane shows it so the new archive appears.
                        context.loadFolderInPane(other1, !pane1, false);
                    }
                }
            });
        }).start();
    }

    /** Writes {@code file} (recursively) into {@code zos} under {@code entryName}. */
    private void zipAdd(ZipOutputStream zos, SafFile file, String entryName) throws IOException {
        if (file.isDirectory()) {
            if (!entryName.isEmpty()) {
                ZipEntry entry = new ZipEntry(entryName + "/");
                if (file.lastModified() > 0) entry.setTime(file.lastModified());
                zos.putNextEntry(entry);
                zos.closeEntry();
            }
            java.util.List<SafFile> kids = SafFs.children(file);
            if (kids != null) {
                for (SafFile kid : kids) {
                    zipAdd(zos, kid, entryName.isEmpty() ? kid.getName() : entryName + "/" + kid.getName());
                }
            }
        } else {
            ZipEntry entry = new ZipEntry(entryName);
            if (file.lastModified() > 0) entry.setTime(file.lastModified());
            zos.putNextEntry(entry);
            try (java.io.InputStream in = SafFs.openInput(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    /** Extracts an archive into the other pane (streamed out of the provider via a staged copy). */
    private void extractToOtherPane(SafFile file) {
        new Thread(() -> {
            File other = otherPaneFolder();
            Exception error = null;
            File staged = null;
            File tempOut = null;
            try {
                staged = RootStaging.stageForRead(context, file.getPath());
                String folderName = file.getName();
                String lower = folderName.toLowerCase(Locale.ROOT);
                for (String ext : new String[]{".tar.gz", ".tar.bz2", ".tar.xz"}) {
                    if (lower.endsWith(ext)) {
                        folderName = folderName.substring(0, folderName.length() - ext.length());
                        break;
                    }
                }
                int dot = folderName.lastIndexOf('.');
                if (dot > 0) folderName = folderName.substring(0, dot);

                boolean destIsSaf = other instanceof SafFile;
                File destDir;
                if (destIsSaf) {
                    tempOut = new File(context.getCacheDir(), "saf_extract_" + System.currentTimeMillis());
                    //noinspection ResultOfMethodCallIgnored
                    tempOut.mkdirs();
                    destDir = tempOut;
                } else {
                    if (other == null) throw new IOException("No destination pane");
                    destDir = FileUtils.getUnusedFile(new File(other, folderName));
                }
                boolean keepTime = androidx.preference.PreferenceManager
                        .getDefaultSharedPreferences(context).getBoolean("preserve_mtime", true);
                ArchiveUtil.extract(staged, destDir, keepTime);
                if (destIsSaf) {
                    File[] kids = tempOut.listFiles();
                    if (kids != null) {
                        for (File kid : kids) SafFs.copyFromRecursive(kid, (SafFile) other, null);
                    }
                }
            } catch (Exception e) {
                error = e;
            } finally {
                if (staged != null) {
                    //noinspection ResultOfMethodCallIgnored
                    staged.delete();
                }
                if (tempOut != null) FileUtils.deleteTree(tempOut, null);
            }
            final Exception fError = error;
            context.runOnUiThread(() -> {
                if (fError != null) showError(fError);
                else {
                    showDone();
                    if (other instanceof SafFile) {
                        context.loadFolderInPane(other, !pane1, false);
                    }
                }
            });
        }).start();
    }

    // ------------------------------------------------------------------
    // mutations
    // ------------------------------------------------------------------

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

    private void newFile(SafFile parent) {
        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.saf_new_file));
        dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.saf_new_file))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    new Thread(() -> {
                        String ext = MimeTypeMap.getFileExtensionFromUrl(name).toLowerCase(Locale.ROOT);
                        String mime = ext.isEmpty() ? null
                                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                        boolean ok = SafFs.createFile(parent, name, mime);
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
