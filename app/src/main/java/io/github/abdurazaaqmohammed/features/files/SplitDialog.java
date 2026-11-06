package io.github.abdurazaaqmohammed.features.files;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SplitArchiveUtil;

/**
 * Interactive dialogs for splitting archives/files into parts and merging parts back together.
 */
public final class SplitDialog {

    private static final long[] PRESET_BYTES = {
            10L * 1024 * 1024,        // 10 MB
            25L * 1024 * 1024,        // 25 MB
            50L * 1024 * 1024,        // 50 MB
            100L * 1024 * 1024,      // 100 MB
            250L * 1024 * 1024,      // 250 MB
            500L * 1024 * 1024,      // 500 MB
            1024L * 1024 * 1024,     // 1 GB
            2048L * 1024 * 1024,     // 2 GB
            -1L                      // Custom
    };

    private static final String[] PRESET_LABELS = {
            "10 MB (Discord / Chat)",
            "25 MB (Email)",
            "50 MB",
            "100 MB",
            "250 MB",
            "500 MB",
            "1 GB",
            "2 GB (FAT32 limit)",
            "Custom size…"
    };

    private SplitDialog() {
    }

    public static void showSplit(MainActivity context, File file) {
        if (file == null || !file.isFile()) return;
        long totalLength = file.length();
        if (totalLength <= 1024) {
            Extensions.showMessage(context, "File is too small to split");
            return;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, pad, pad, pad);

        // Header info
        TextView fileInfo = new TextView(context);
        fileInfo.setText(file.getName() + " (" + FileSize.getHumanReadableFileSize(totalLength) + ")");
        fileInfo.setTextSize(15f);
        fileInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(fileInfo);

        // Split Mode RadioGroup
        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(LinearLayout.VERTICAL);
        modeGroup.setPadding(0, Extensions.dp2px(context, 8), 0, Extensions.dp2px(context, 8));

        RadioButton rbBySize = new RadioButton(context);
        rbBySize.setText(R.string.split_by_size);
        rbBySize.setChecked(true);
        modeGroup.addView(rbBySize);

        RadioButton rbByCount = new RadioButton(context);
        rbByCount.setText(R.string.split_by_count);
        modeGroup.addView(rbByCount);
        root.addView(modeGroup);

        // Container for Size mode
        LinearLayout sizeContainer = new LinearLayout(context);
        sizeContainer.setOrientation(LinearLayout.VERTICAL);

        Spinner presetSpinner = new Spinner(context);
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, PRESET_LABELS);
        presetSpinner.setAdapter(presetAdapter);
        // Default to 100MB or smaller depending on total file length
        presetSpinner.setSelection(findDefaultPreset(totalLength));
        sizeContainer.addView(presetSpinner);

        LinearLayout customSizeRow = new LinearLayout(context);
        customSizeRow.setOrientation(LinearLayout.HORIZONTAL);
        customSizeRow.setVisibility(View.GONE);
        customSizeRow.setPadding(0, Extensions.dp2px(context, 4), 0, 0);

        EditText customSizeInput = new EditText(context);
        customSizeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        customSizeInput.setHint("100");
        customSizeInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        customSizeRow.addView(customSizeInput);

        Spinner unitSpinner = new Spinner(context);
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, new String[]{"MB", "GB", "KB"});
        unitSpinner.setAdapter(unitAdapter);
        customSizeRow.addView(unitSpinner);
        sizeContainer.addView(customSizeRow);
        root.addView(sizeContainer);

        // Container for Count mode
        LinearLayout countContainer = new LinearLayout(context);
        countContainer.setOrientation(LinearLayout.VERTICAL);
        countContainer.setVisibility(View.GONE);

        EditText countInput = new EditText(context);
        countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        countInput.setHint("2");
        countInput.setText("2");
        countContainer.addView(countInput);
        root.addView(countContainer);

        // Naming Style Spinner
        String origName = file.getName();
        int dot = origName.lastIndexOf('.');
        String baseName = dot != -1 ? origName.substring(0, dot) : origName;
        String ext = dot != -1 ? origName.substring(dot) : "";

        TextView namingLabel = new TextView(context);
        namingLabel.setText(R.string.split_naming);
        namingLabel.setPadding(0, Extensions.dp2px(context, 8), 0, Extensions.dp2px(context, 4));
        root.addView(namingLabel);

        Spinner namingSpinner = new Spinner(context);
        String[] namingOptions = {
                context.getString(R.string.split_naming_standard) + " (" + origName + ".001…)",
                context.getString(R.string.split_naming_part) + " (" + baseName + ".part1" + ext + "…)"
        };
        ArrayAdapter<String> namingAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, namingOptions);
        namingSpinner.setAdapter(namingAdapter);
        root.addView(namingSpinner);

        // Live preview
        TextView previewText = new TextView(context);
        previewText.setPadding(0, Extensions.dp2px(context, 12), 0, Extensions.dp2px(context, 8));
        previewText.setTextSize(13f);
        root.addView(previewText);

        // Delete original CheckBox
        CheckBox deleteOriginal = new CheckBox(context);
        deleteOriginal.setText(R.string.delete_original_after_split);
        root.addView(deleteOriginal);

        // Calculate and update preview
        final long[] chosenPartSize = new long[]{PRESET_BYTES[presetSpinner.getSelectedItemPosition()]};

        Runnable updatePreview = () -> {
            boolean bySize = rbBySize.isChecked();
            long partBytes;
            if (bySize) {
                int pos = presetSpinner.getSelectedItemPosition();
                if (pos >= 0 && pos < PRESET_BYTES.length - 1) {
                    partBytes = PRESET_BYTES[pos];
                } else {
                    // Custom
                    String s = customSizeInput.getText().toString().trim();
                    try {
                        double val = Double.parseDouble(s);
                        String unit = (String) unitSpinner.getSelectedItem();
                        long mult = "GB".equals(unit) ? 1024L * 1024 * 1024 : ("KB".equals(unit) ? 1024L : 1024L * 1024);
                        partBytes = (long) (val * mult);
                    } catch (Exception e) {
                        partBytes = 100L * 1024 * 1024;
                    }
                }
            } else {
                String cStr = countInput.getText().toString().trim();
                int cnt = 2;
                try {
                    cnt = Math.max(2, Integer.parseInt(cStr));
                } catch (Exception ignored) {}
                partBytes = (long) Math.ceil((double) totalLength / cnt);
            }

            if (partBytes < 1024) partBytes = 1024;
            chosenPartSize[0] = partBytes;

            int parts = (int) Math.ceil((double) totalLength / partBytes);
            String partSizeStr = FileSize.getHumanReadableFileSize(partBytes);
            String preview = context.getString(R.string.split_result_preview, parts, partSizeStr);

            boolean partStyle = namingSpinner.getSelectedItemPosition() == 1;
            String example1 = partStyle ? (baseName + ".part1" + ext) : (origName + ".001");
            String exampleLast = partStyle ? (baseName + ".part" + parts + ext) : (origName + "." + String.format(Locale.ROOT, "%03d", parts));
            preview += "\n" + example1 + " → " + exampleLast;

            previewText.setText(preview);
        };

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                customSizeRow.setVisibility(position == PRESET_BYTES.length - 1 ? View.VISIBLE : View.GONE);
                updatePreview.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        namingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        customSizeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updatePreview.run(); }
        });

        countInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updatePreview.run(); }
        });

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean bySize = rbBySize.isChecked();
            sizeContainer.setVisibility(bySize ? View.VISIBLE : View.GONE);
            countContainer.setVisibility(bySize ? View.GONE : View.VISIBLE);
            updatePreview.run();
        });

        updatePreview.run();

        new AlertDialog.Builder(context)
                .setTitle(R.string.split_dialog_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.split_file, (dialog, which) -> {
                    long partSizeBytes = chosenPartSize[0];
                    boolean isPartStyle = namingSpinner.getSelectedItemPosition() == 1;
                    String namingStyle = isPartStyle ? "part_ext" : "ext_numbered";
                    boolean del = deleteOriginal.isChecked();

                    ProgressManager pm = new ProgressManager(context, true).show();
                    pm.setText(context.getString(R.string.split_in_progress, file.getName()));

                    new Thread(() -> {
                        try {
                            List<File> result = SplitArchiveUtil.splitFile(file, file.getParentFile(), partSizeBytes, namingStyle, pm != null ? pm.listener() : null);
                            if (del && file.exists()) {
                                file.delete();
                            }
                            context.runOnUiThread(() -> {
                                pm.dismiss();
                                Extensions.showMessage(context, context.getString(R.string.split_success, result.size()));
                                context.reloadCurrentFolder();
                            });
                        } catch (Exception e) {
                            context.runOnUiThread(() -> {
                                pm.dismiss();
                                Extensions.showMessage(context, "Split failed: " + e.getMessage());
                            });
                        }
                    }).start();
                })
                .show();
    }

    public static void showMerge(MainActivity context, File file) {
        if (file == null) return;
        List<File> parts = SplitArchiveUtil.getOrderedParts(file);
        if (parts == null || parts.size() < 2) {
            Extensions.showMessage(context, R.string.split_missing_parts);
            return;
        }

        long totalSize = 0;
        for (File p : parts) totalSize += p.length();

        String suggestedOutput = SplitArchiveUtil.getMergedFileName(parts.get(0));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView partsInfo = new TextView(context);
        partsInfo.setText("Parts found: " + parts.size() + " (" + parts.get(0).getName() + " … " + parts.get(parts.size() - 1).getName() + ")");
        partsInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(partsInfo);

        TextView sizeInfo = new TextView(context);
        sizeInfo.setText("Total merged size: " + FileSize.getHumanReadableFileSize(totalSize));
        sizeInfo.setPadding(0, Extensions.dp2px(context, 4), 0, Extensions.dp2px(context, 8));
        root.addView(sizeInfo);

        TextView nameLabel = new TextView(context);
        nameLabel.setText("Output filename:");
        root.addView(nameLabel);

        EditText outputNameInput = new EditText(context);
        outputNameInput.setText(suggestedOutput);
        root.addView(outputNameInput);

        CheckBox deleteParts = new CheckBox(context);
        deleteParts.setText("Delete split parts after merging");
        root.addView(deleteParts);

        new AlertDialog.Builder(context)
                .setTitle(R.string.merge_split_files)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.merge_split_files, (dialog, which) -> {
                    String outName = outputNameInput.getText().toString().trim();
                    if (outName.isEmpty()) outName = suggestedOutput;
                    File outFile = new File(file.getParentFile(), outName);
                    boolean del = deleteParts.isChecked();

                    ProgressManager pm = new ProgressManager(context, true).show();
                    pm.setText(context.getString(R.string.merge_in_progress));

                    new Thread(() -> {
                        try {
                            SplitArchiveUtil.mergeParts(parts, outFile, pm != null ? pm.listener() : null);
                            if (del) {
                                for (File p : parts) {
                                    if (p.exists() && !p.equals(outFile)) p.delete();
                                }
                            }
                            context.runOnUiThread(() -> {
                                pm.dismiss();
                                Extensions.showMessage(context, context.getString(R.string.merge_success, outFile.getName()));
                                context.reloadCurrentFolder();
                            });
                        } catch (Exception e) {
                            context.runOnUiThread(() -> {
                                pm.dismiss();
                                Extensions.showMessage(context, "Merge failed: " + e.getMessage());
                            });
                        }
                    }).start();
                })
                .show();
    }

    private static int findDefaultPreset(long fileLen) {
        if (fileLen <= 50L * 1024 * 1024) return 0; // 10MB
        if (fileLen <= 200L * 1024 * 1024) return 1; // 25MB
        if (fileLen <= 500L * 1024 * 1024) return 2; // 50MB
        if (fileLen <= 2000L * 1024 * 1024) return 3; // 100MB
        return 4; // 250MB
    }
}
