package io.github.abdurazaaqmohammed.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CompareArscDialog {
    private final Context context;
    private final String file1;
    private final String file2;

    public CompareArscDialog(Context context, String file1, String file2) {
        this.context = context;
        this.file1 = file1;
        this.file2 = file2;
    }

    public void show() {
        List<String> differences = new ArrayList<>();
        try {
            TableBlock tb1 = loadTableBlock(file1);
            TableBlock tb2 = loadTableBlock(file2);

            if (tb1.getStringPool().size() != tb2.getStringPool().size()) {
                differences.add("String Pool count: " + tb1.getStringPool().size() + " -> " + tb2.getStringPool().size());
            }

            int p1Count = tb1.getPackageArray().size();
            int p2Count = tb2.getPackageArray().size();
            if (p1Count != p2Count) {
                differences.add("Package count: " + p1Count + " -> " + p2Count);
            }

            for (int i = 0; i < Math.max(p1Count, p2Count); i++) {
                PackageBlock pb1 = i < p1Count ? tb1.getPackageArray().get(i) : null;
                PackageBlock pb2 = i < p2Count ? tb2.getPackageArray().get(i) : null;

                if (pb1 != null && pb2 != null) {
                    if (!pb1.getName().equals(pb2.getName())) {
                        differences.add("Package name at index " + i + ": " + pb1.getName() + " -> " + pb2.getName());
                    }
                    // Simple type count diff
                    if (pb1.getSpecTypePairArray().size() != pb2.getSpecTypePairArray().size()) {
                        differences.add("Package " + pb1.getName() + " SpecType count: " + pb1.getSpecTypePairArray().size() + " -> " + pb2.getSpecTypePairArray().size());
                    }
                } else if (pb1 != null) {
                    differences.add("[Removed Package] " + pb1.getName());
                } else if (pb2 != null) {
                    differences.add("[Added Package] " + pb2.getName());
                }
            }

            if (differences.isEmpty()) {
                differences.add("No structural differences found in packages and string counts.");
            }
        } catch (Exception e) {
            differences.add("Error parsing ARSC files: " + e.getMessage());
        }

        ListView listView = new ListView(context);
        listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, differences));

        new MaterialAlertDialogBuilder(context)
                .setTitle("ARSC Differences")
                .setView(listView)
                .setPositiveButton("Close", null)
                .show();
    }

    private TableBlock loadTableBlock(String path) throws Exception {
        if (path.toLowerCase().endsWith(".apk")) {
            try (ZipFile zf = new ZipFile(path)) {
                ZipEntry ze = zf.getEntry("resources.arsc");
                if (ze != null) {
                    try (InputStream is = zf.getInputStream(ze)) {
                        return TableBlock.load(is);
                    }
                }
            }
            throw new Exception("resources.arsc not found in APK");
        } else {
            return TableBlock.load(new File(path));
        }
    }
}
