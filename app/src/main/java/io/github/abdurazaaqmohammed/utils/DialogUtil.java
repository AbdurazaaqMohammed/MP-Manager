package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class DialogUtil {
    private final Activity context;

    public DialogUtil(Activity c) {
        this.context = c;
    }

    public MaterialAlertDialogBuilder getDialogBuilder() {
        return new MaterialAlertDialogBuilder(context);
    }

    public void styleAlertDialog(AlertDialog ad) {
        context.runOnUiThread(ad::show);
    }

    /**
     * Starts a directory picker and hands the result to the given launcher.
     */
    public void pickDirectory(AppCompatActivity activity, TextInputEditText outputPathInput,
            ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Enter output directory path manually", Toast.LENGTH_SHORT).show();
        }
    }

    private static String getPathFromTreeUri(Uri treeUri) {
        if (treeUri == null) return null;
        if ("content".equals(treeUri.getScheme())) {
            // Best-effort: some providers expose a path via getData()
            return treeUri.getPath();
        }
        return treeUri.getPath();
    }
}