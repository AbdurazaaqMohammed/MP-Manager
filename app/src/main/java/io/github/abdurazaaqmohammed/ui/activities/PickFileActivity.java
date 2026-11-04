package io.github.abdurazaaqmohammed.ui.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;

/**
 * MP Manager as the picker window itself.
 *
 * <p>When another app asks the system for a file — {@code ACTION_GET_CONTENT},
 * {@code ACTION_OPEN_DOCUMENT}, {@code ACTION_PICK} or
 * {@code ACTION_OPEN_DOCUMENT_TREE} — MP Manager is offered alongside the
 * system Files app. Choosing it opens the same {@link FilePickerDialog} the
 * rest of the app uses, so the user browses with the app's own list, including
 * folders that need root or Shizuku to be read at all.</p>
 *
 * <p>The picked path is returned as a {@code content://} document URI under
 * {@link io.github.abdurazaaqmohammed.provider.StorageDocumentsProvider}, with
 * a read grant attached. The caller therefore reads the bytes through that
 * provider, which streams them straight out of root/Shizuku when needed — no
 * copy has to be made before the file is handed over, and a file in
 * {@code Android/data} is as openable as one on the sdcard.</p>
 */
public class PickFileActivity extends AppCompatActivity {

    /** Must match the authority declared for the provider in the manifest. */
    public static final String AUTHORITY = "io.github.abdurazaaqmohammed.MPManager.documents";

    private boolean delivered;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent == null ? null : intent.getAction();
        boolean tree = Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action);
        // Only the document actions promise a persistable grant; for the older
        // GET_CONTENT/PICK hand-off a plain read grant is what callers expect.
        boolean document = Intent.ACTION_OPEN_DOCUMENT.equals(action) || tree;
        boolean multiple = !tree && intent != null
                && intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);

        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_type = tree ? FilePickerDialog.DIR_SELECT : FilePickerDialog.FILE_SELECT;
        props.selection_mode = multiple
                ? FilePickerDialog.MULTI_MODE : FilePickerDialog.SINGLE_MODE;
        props.root = startDirectory();
        props.extensions = extensionsFor(intent);
        props.preferenceKey = "saf_pick";

        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.select));
        picker.setOnCancelListener(() -> {
            if (delivered) return;
            delivered = true;
            finishCanceled();
        });
        picker.setDialogSelectionListener(files -> deliver(files, tree, document));
        picker.show();
    }

    /** Turns the picked path(s) into a result the calling app can read. */
    private void deliver(String[] files, boolean tree, boolean asDocument) {
        if (delivered) return;
        delivered = true;
        if (files == null || files.length == 0) {
            finishCanceled();
            return;
        }

        Intent result = new Intent();
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (asDocument) flags |= Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

        if (tree) {
            String path = files[0];
            if (path == null || path.isEmpty()) {
                finishCanceled();
                return;
            }
            // A tree URI lets the caller keep and re-enumerate this folder later;
            // StorageDocumentsProvider serves children of any exported path.
            result.setData(DocumentsContract.buildTreeDocumentUri(AUTHORITY, path));
        } else {
            ArrayList<Uri> uris = new ArrayList<>(files.length);
            for (String path : files) {
                if (path == null || path.isEmpty()) continue;
                uris.add(DocumentsContract.buildDocumentUri(AUTHORITY, path));
            }
            if (uris.isEmpty()) {
                finishCanceled();
                return;
            }
            result.setData(uris.get(0));
            if (uris.size() > 1) {
                // Multi-select reaches callers through ClipData; EXTRA_STREAM is
                // kept for apps that still read it from a GET_CONTENT result.
                ClipData clip = null;
                for (Uri uri : uris) {
                    ClipData.Item item = new ClipData.Item(uri);
                    if (clip == null) {
                        clip = new ClipData(
                                new ClipDescription(getString(R.string.select),
                                        new String[]{typeOf(uri)}),
                                item);
                    } else {
                        clip.addItem(item);
                    }
                }
                result.setClipData(clip);
                result.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
        }

        result.addFlags(flags);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void finishCanceled() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private String typeOf(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            if (type != null && !type.isEmpty()) return type;
        } catch (Exception ignored) {
        }
        return "application/octet-stream";
    }

    private static File startDirectory() {
        try {
            File dir = Environment.getExternalStorageDirectory();
            if (dir != null) return dir;
        } catch (Throwable ignored) {
        }
        return new File("/");
    }

    /**
     * Restricts the listing to what the caller asked for, when that can be said
     * with extensions. A wildcard family maps to its common extensions; anything
     * more exotic (several MIME types at once, {@code application/*}) is shown
     * unfiltered rather than filtered wrongly.
     */
    private static String[] extensionsFor(Intent intent) {
        if (intent == null) return null;
        String[] declared = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        if (declared != null && declared.length == 1) {
            String[] single = extensionsFor(declared[0]);
            if (single != null) return single;
        }
        return extensionsFor(intent.getType());
    }

    private static String[] extensionsFor(String mimeType) {
        if (mimeType == null || mimeType.isEmpty() || "*/*".equals(mimeType)) return null;
        if (!mimeType.contains("*")) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            return extension == null ? null : new String[]{extension};
        }
        int slash = mimeType.indexOf('/');
        String family = slash > 0 ? mimeType.substring(0, slash) : mimeType;
        switch (family) {
            case "image":
                return new String[]{"png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "avif", "svg"};
            case "video":
                return new String[]{"mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m4v"};
            case "audio":
                return new String[]{"mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr"};
            case "text":
                return new String[]{"txt", "md", "json", "xml", "csv", "log", "html", "htm", "yaml", "ini"};
            default:
                return null;
        }
    }
}
