package io.github.abdurazaaqmohammed.features.translation;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;

/**
 * Dialog for exporting strings from an APK's strings.xml for translation.
 * Shows format selection (JSON/MTD) and performs the export.
 */
public class TranslationExportDialog {

    private final AppCompatActivity activity;
    private final DialogUtil dialogUtil;
    private final ActivityResultLauncher<Intent> dirPickerLauncher;
    private File selectedApkFile;
    private TranslationExporter.StringEntry[] entries;
    /** Set while the export dialog is open, so the picker can fill the right field. */
    private TextInputEditText outputPathTarget;

    public TranslationExportDialog(AppCompatActivity activity) {
        this.activity = activity;
        this.dialogUtil = new DialogUtil(activity);
        // Single launcher for directory picker; registered once
        this.dirPickerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null && outputPathTarget != null) {
                            String path = getPathFromTreeUri(treeUri);
                            if (path != null) outputPathTarget.setText(path);
                        }
                    }
                });
    }

    /**
     * Shows the translation export dialog.
     * @param apkFile The APK file to export strings from
     */
    public void show(File apkFile) {
        this.selectedApkFile = apkFile;

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_translation_export, null);

        TextInputEditText outputPathInput = view.findViewById(R.id.translation_output_path);
        Spinner formatSpinner = view.findViewById(R.id.translation_format_spinner);
        Button browseButton = view.findViewById(R.id.translation_browse_button);
        outputPathTarget = outputPathInput;

        // Set default output path to Downloads/MP Manager
        File downloadsDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), "MP Manager");
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        outputPathInput.setText(downloadsDir.getAbsolutePath());

        // Setup format spinner
        String[] formats = {"JSON", "MTD"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, formats);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);
        formatSpinner.setSelection(0); // Default to JSON

        // Browse button to pick output directory
        browseButton.setOnClickListener(v ->
                dialogUtil.pickDirectory(activity, outputPathInput, dirPickerLauncher));


        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.export_strings)
                .setView(view)
                .setPositiveButton(R.string.export, (dialog, which) -> {
                    String outputPath = outputPathInput.getText().toString().trim();
                    String format = formatSpinner.getSelectedItem() == null ? "JSON"
                            : formatSpinner.getSelectedItem().toString().toLowerCase(Locale.US);

                    if (outputPath.isEmpty()) {
                        Toast.makeText(activity, R.string.invalid_file_path, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File outputDir = new File(outputPath);
                    if (!outputDir.exists()) {
                        if (!outputDir.mkdirs()) {
                            Toast.makeText(activity, R.string.cannot_create_directory,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    performExport(outputDir, format);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> outputPathTarget = null)
                .show();
    }

    /** Performs the actual export in a background thread. */
    private void performExport(File outputDir, String format) {
        new Thread(() -> {
            try {
                TranslationExporter.ExportResult result = TranslationExporter.exportStrings(
                        activity, selectedApkFile, format.toLowerCase(), outputDir);

                activity.runOnUiThread(() -> {
                    if (result.success) {
                        Toast.makeText(activity,
                                activity.getString(R.string.exported_x,
                                        result.exportedFile.getAbsolutePath()),
                                Toast.LENGTH_LONG).show();
                        outputPathTarget = null;
                    } else {
                        new ErrorUtil(activity).showError(new Exception(result.errorMessage));
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> new ErrorUtil(activity).showError(e));
            }
        }).start();
    }

    /**
     * Creates a simple preview of the strings to be exported.
     */
    public void showPreviewDialog(File apkFile) {
        this.selectedApkFile = apkFile;

        new Thread(() -> {
            try {
                List<TranslationExporter.StringEntry> entries =
                        extractStringsPreview(apkFile);
                this.entries = entries.toArray(new TranslationExporter.StringEntry[0]);

                activity.runOnUiThread(() -> {
                    if (this.entries == null || this.entries.length == 0) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.export_strings)
                                .setMessage(R.string.nothing_to_export)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        return;
                    }

                    View view = LayoutInflater.from(activity).inflate(R.layout.dialog_translation_preview, null);
                    TextView previewText = view.findViewById(R.id.translation_preview_text);
                    TextView countText = view.findViewById(R.id.translation_count);

                    StringBuilder sb = new StringBuilder();
                    int maxLines = Math.min(this.entries.length, 50);
                    for (int i = 0; i < maxLines; i++) {
                        TranslationExporter.StringEntry entry = this.entries[i];
                        sb.append(entry.name).append(" = ").append(entry.value).append('\n');
                    }
                    if (this.entries.length > maxLines) {
                        sb.append("... ")
                                .append(activity.getString(R.string.x_more_strings,
                                        this.entries.length - maxLines));
                    }

                    previewText.setText(sb.toString());
                    countText.setText(activity.getString(R.string.x_strings_found, this.entries.length));

                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.export_strings)
                            .setMessage(R.string.exporting)
                            .setView(view)
                            .setPositiveButton(R.string.export, (dialog, which) -> show(selectedApkFile))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        new ErrorUtil(activity).showError(e));
            }
        }).start();
    }

    private List<TranslationExporter.StringEntry> extractStringsPreview(File apkFile) throws Exception {
        return TranslationExporter.extractStringsFromApk(activity, apkFile);
    }

    /**
     * Best-effort conversion of a picked document tree to a filesystem path. SAF trees are not
     * paths, so when no volume can be mapped the primary volume root is used: the export only
     * needs a writable directory, and SAF-granted access is not required for one the user picks
     * by path in the field above.
     */
    private String getPathFromTreeUri(Uri treeUri) {
        String path = treeUri.getPath();
        if (path == null) return null;
        // content://com.android.externalstorage.documents/tree/primary:Download
        int marker = path.indexOf(':');
        if (marker >= 0 && marker < path.length() - 1) {
            String relative = path.substring(marker + 1);
            File primary = android.os.Environment.getExternalStorageDirectory();
            return primary == null ? null : new File(primary, relative).getAbsolutePath();
        }
        File doc = new File(path);
        return doc.getAbsolutePath();
    }
}
