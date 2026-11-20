package io.github.abdurazaaqmohammed.ui.editor;

import android.app.Activity;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lingala.zip4j.model.FileHeader;

import io.github.abdurazaaqmohammed.util.archive.ZipCompat;
import io.github.abdurazaaqmohammed.ui.editor.CompareTextActivity;

public class CompareZipDialog {
    private final Activity context;
    private final File zip1;
    private final File zip2;

    private record DiffItem(String text, String fileName, String status) {

        @Override
            public String toString() {
                return text;
            }
        }

    public CompareZipDialog(Activity context, File zip1, File zip2) {
        this.context = context;
        this.zip1 = zip1;
        this.zip2 = zip2;
    }

    public void show() {
        List<DiffItem> differences = new ArrayList<>();
        try {
            // ZipCompat decodes entry names with the per-entry charset
            // heuristic, so legacy Chinese names compare and display correctly
            // instead of as replacement garbage.
            Map<String, FileHeader> entries1 = new HashMap<>();
            for (FileHeader fh : ZipCompat.listHeaders(zip1)) {
                entries1.put(fh.getFileName(), fh);
            }

            Map<String, FileHeader> entries2 = new HashMap<>();
            for (FileHeader fh : ZipCompat.listHeaders(zip2)) {
                entries2.put(fh.getFileName(), fh);
            }

            for (Map.Entry<String, FileHeader> e : entries1.entrySet()) {
                String name = e.getKey();
                FileHeader ze1 = e.getValue();
                FileHeader ze2 = entries2.get(name);

                if (ze2 == null) {
                    differences.add(new DiffItem("[" + context.getString(R.string.removed) + "] " + name + " (" + ze1.getUncompressedSize() + " bytes)", name, "[Removed]"));
                } else {
                    if (ze1.getCrc() != ze2.getCrc() || ze1.getUncompressedSize() != ze2.getUncompressedSize()) {
                        differences.add(new DiffItem("[" + context.getString(R.string.modified) + "] " + name + " (Size: " + ze1.getUncompressedSize() + " -> " + ze2.getUncompressedSize() + " bytes)", name, "[Modified]"));
                    }
                    entries2.remove(name);
                }
            }

            for (Map.Entry<String, FileHeader> e : entries2.entrySet()) {
                differences.add(new DiffItem("[" + context.getString(R.string.added) + "] " + e.getKey() + " (" + e.getValue().getUncompressedSize() + " bytes)", e.getKey(), "[Added]"));
            }

        } catch (Exception e) {
            differences.add(new DiffItem(context.getString(R.string.error_reading_zip_files) + e.getMessage(), "", "Error"));
        }

        if (differences.isEmpty()) {
            differences.add(new DiffItem(context.getString(R.string.no_differences_found), "", "Info"));
        }

        ListView listView = new ListView(context);
        listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, differences));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            DiffItem item = differences.get(position);
            if ("[Modified]".equals(item.status)) {
                String ext = FilenameUtils.getExtension(item.fileName).toLowerCase();
                boolean isZipInner = ext.equals("zip") || ext.equals("apk") || ext.equals("jar");
                boolean isArscInner = ext.equals("arsc");
                boolean isTextInner = !isZipInner && !isArscInner;

                if (isTextInner) {
                    context.startActivity(new Intent(context, CompareTextActivity.class)
                            .putExtra("file1", item.fileName)
                            .putExtra("file2", item.fileName)
                            .putExtra("isZip1", true)
                            .putExtra("isZip2", true)
                            .putExtra("zip1", zip1.getAbsolutePath())
                            .putExtra("zip2", zip2.getAbsolutePath())
                    );
                } else if (isZipInner) {
                    extractAndCompareZip(item.fileName);
                } else if (isArscInner) {
                    // Compare nested ARSC
                    extractAndCompareArsc(item.fileName);
                }
            }
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.zip_differences)
                .setView(listView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void extractAndCompareZip(String innerFileName) {
        try {
            File tmp1 = new File(context.getCacheDir(), "cmp1_" + innerFileName.replace("/", "_"));
            File tmp2 = new File(context.getCacheDir(), "cmp2_" + innerFileName.replace("/", "_"));

            extractZipEntry(zip1, innerFileName, tmp1);
            extractZipEntry(zip2, innerFileName, tmp2);

            new CompareZipDialog(context, tmp1, tmp2).show();
        } catch (Exception e) {
            Extensions.showMessage(context, "Error extracting inner zip: " + e.getMessage());
        }
    }

    private void extractAndCompareArsc(String innerFileName) {
        try {
            File tmp1 = new File(context.getCacheDir(), "cmp1_" + innerFileName.replace("/", "_"));
            File tmp2 = new File(context.getCacheDir(), "cmp2_" + innerFileName.replace("/", "_"));

            extractZipEntry(zip1, innerFileName, tmp1);
            extractZipEntry(zip2, innerFileName, tmp2);

            new CompareArscDialog(context, tmp1.getAbsolutePath(), tmp2.getAbsolutePath()).show();
        } catch (Exception e) {
            Extensions.showMessage(context, "Error extracting inner arsc: " + e.getMessage());
        }
    }

    private void extractZipEntry(File zip, String entryName, File out) throws Exception {
        ZipCompat.extractEntry(zip, entryName, out);
    }
}
