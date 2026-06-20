package io.github.abdurazaaqmohammed.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;

public class CompareZipDialog {
    private final Context context;
    private final File zip1;
    private final File zip2;

    private static class DiffItem {
        String text;
        String fileName;
        String status;

        DiffItem(String text, String fileName, String status) {
            this.text = text;
            this.fileName = fileName;
            this.status = status;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public CompareZipDialog(Context context, File zip1, File zip2) {
        this.context = context;
        this.zip1 = zip1;
        this.zip2 = zip2;
    }

    public void show() {
        List<DiffItem> differences = new ArrayList<>();
        try (ZipFile zf1 = new ZipFile(zip1); ZipFile zf2 = new ZipFile(zip2)) {
            Map<String, ZipEntry> entries1 = new HashMap<>();
            Enumeration<? extends ZipEntry> e1 = zf1.entries();
            while (e1.hasMoreElements()) {
                ZipEntry ze = e1.nextElement();
                entries1.put(ze.getName(), ze);
            }

            Map<String, ZipEntry> entries2 = new HashMap<>();
            Enumeration<? extends ZipEntry> e2 = zf2.entries();
            while (e2.hasMoreElements()) {
                ZipEntry ze = e2.nextElement();
                entries2.put(ze.getName(), ze);
            }

            for (Map.Entry<String, ZipEntry> e : entries1.entrySet()) {
                String name = e.getKey();
                ZipEntry ze1 = e.getValue();
                ZipEntry ze2 = entries2.get(name);

                if (ze2 == null) {
                    differences.add(new DiffItem("[Removed] " + name + " (" + ze1.getSize() + " bytes)", name, "[Removed]"));
                } else {
                    if (ze1.getCrc() != ze2.getCrc() || ze1.getSize() != ze2.getSize()) {
                        differences.add(new DiffItem("[Modified] " + name + " (Size: " + ze1.getSize() + " -> " + ze2.getSize() + " bytes)", name, "[Modified]"));
                    }
                    entries2.remove(name);
                }
            }

            for (Map.Entry<String, ZipEntry> e : entries2.entrySet()) {
                differences.add(new DiffItem("[Added] " + e.getKey() + " (" + e.getValue().getSize() + " bytes)", e.getKey(), "[Added]"));
            }

        } catch (Exception e) {
            differences.add(new DiffItem("Error reading ZIP files: " + e.getMessage(), "", "Error"));
        }

        if (differences.isEmpty()) {
            differences.add(new DiffItem("No differences found.", "", "Info"));
        }

        ListView listView = new ListView(context);
        listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, differences));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            DiffItem item = differences.get(position);
            if ("[Modified]".equals(item.status)) {
                String ext = org.apache.commons.io.FilenameUtils.getExtension(item.fileName).toLowerCase();
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

        new AlertDialog.Builder(context)
                .setTitle("ZIP Differences")
                .setView(listView)
                .setPositiveButton("Close", null)
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
            Toast.makeText(context, "Error extracting inner zip: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(context, "Error extracting inner arsc: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void extractZipEntry(File zip, String entryName, File out) throws Exception {
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry ze = zf.getEntry(entryName);
            if (ze != null) {
                try (InputStream is = zf.getInputStream(ze);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            } else {
                throw new Exception("Entry not found: " + entryName);
            }
        }
    }
}
