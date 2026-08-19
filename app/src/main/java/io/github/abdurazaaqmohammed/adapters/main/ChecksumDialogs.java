package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;

public class ChecksumDialogs {

    private final MainActivity context;
    private final DialogUtil dialogUtil;

    public ChecksumDialogs(MainActivity context, DialogUtil dialogUtil) {
        this.context = context;
        this.dialogUtil = dialogUtil;
    }

    public TextView addChecksumRow(LinearLayout container, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        labelView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));

        TextView valueView = new TextView(context);
        valueView.setText("—");
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setTextSize(12);
        valueView.setTextIsSelectable(true);
        valueView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        valueView.setOnLongClickListener(v -> {
            CopyUtil.copyToClipboard(context, valueView.getText());
            return true;
        });
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton copyButton = new ImageButton(context);
        copyButton.setImageResource(R.drawable.baseline_content_copy_24);
        copyButton.setBackgroundColor(Color.TRANSPARENT);
        copyButton.setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        int pad = dp(8);
        copyButton.setPadding(pad, pad, pad, pad);
        copyButton.setContentDescription(context.getString(android.R.string.copy));
        copyButton.setOnClickListener(v -> CopyUtil.copyToClipboard(context, valueView.getText()));
        copyButton.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));

        row.addView(labelView);
        row.addView(valueView);
        row.addView(copyButton);
        container.addView(row);
        return valueView;
    }

    public void addChecksumFileHeader(LinearLayout container, String fileName) {
        TextView header = new TextView(context);
        header.setText(fileName);
        header.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        header.setPadding(0, dp(10), 0, dp(4));
        container.addView(header);
    }

    public void startChecksumComputation(LinearLayout container, File file) {
        Map<String, TextView> views = new HashMap<>();
        for (String algo : HashUtil.ALGORITHMS) views.put(algo, addChecksumRow(container, algo));
        new Thread(() -> {
            try {
                Map<String, String> hashes = HashUtil.hashAll(file);
                context.handler.post(() -> {
                    for (Map.Entry<String, String> e : hashes.entrySet()) {
                        TextView tv = views.get(e.getKey());
                        if (tv != null) tv.setText(e.getValue());
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> {
                    for (TextView tv : views.values()) tv.setText("—");
                });
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void addCrc32Row(LinearLayout container, long crc32Value) {
        if (crc32Value < 0) return;
        TextView crcView = addChecksumRow(container, "CRC32");
        crcView.setText(HashUtil.crc32Hex(crc32Value));
    }

    public void startZipEntryChecksumComputation(LinearLayout container, File zipFile, String entryPath) {
        Map<String, TextView> views = new HashMap<>();
        for (String algo : HashUtil.ALGORITHMS) views.put(algo, addChecksumRow(container, algo));
        new Thread(() -> {
            File tmpFile = null;
            try {
                tmpFile = File.createTempFile("zip_entry_", ".tmp", context.getCacheDir());
                try (ZipFile zf = new ZipFile(zipFile)) {
                    FileHeader fh = zf.getFileHeader(entryPath);
                    if (fh == null) {
                        for (TextView tv : views.values()) tv.setText("—");
                        return;
                    }
                    try (InputStream is = zf.getInputStream(fh);
                         FileOutputStream fos = new FileOutputStream(tmpFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                    }
                }
                Map<String, String> hashes = HashUtil.hashAll(tmpFile);
                context.handler.post(() -> {
                    for (Map.Entry<String, String> e : hashes.entrySet()) {
                        TextView tv = views.get(e.getKey());
                        if (tv != null) tv.setText(e.getValue());
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> {
                    for (TextView tv : views.values()) tv.setText("—");
                });
                new ErrorUtil(context).showError(e);
            } finally {
                if (tmpFile != null) tmpFile.delete();
            }
        }).start();
    }

    public void showChecksumsDialog(List<File> files) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.computing_checksums));
        new Thread(() -> {
            try {
                List<Map<String, String>> results = new ArrayList<>();
                for (File f : files) {
                    pm.setText(context.getString(R.string.hashing, f.getName()));
                    results.add(HashUtil.hashAll(f));
                }
                context.handler.post(() -> {
                    pm.dismiss();
                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_checksums, null);
                    LinearLayout container = view.findViewById(R.id.checksumContainer);
                    if (files.size() == 1) {
                        for (Map.Entry<String, String> e : results.get(0).entrySet()) {
                            TextView valueView = addChecksumRow(container, e.getKey());
                            valueView.setText(e.getValue());
                        }
                    } else {
                        for (int i = 0; i < files.size(); i++) {
                            addChecksumFileHeader(container, files.get(i).getName());
                            for (Map.Entry<String, String> e : results.get(i).entrySet()) {
                                TextView valueView = addChecksumRow(container, e.getKey());
                                valueView.setText(e.getValue());
                            }
                        }
                    }
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(files.size() > 1 ? context.getString(R.string.checksums) : context.getString(R.string.checksums_of, files.get(0).getName()))
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void showZipEntryChecksumsDialog(ZipEntryInfo entry) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_checksums, null);
        LinearLayout container = view.findViewById(R.id.checksumContainer);
        addCrc32Row(container, entry.computeCrc32());
        addChecksumFileHeader(container, "Full checksums (on-demand)");
        com.google.android.material.button.MaterialButton calcBtn = new com.google.android.material.button.MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        calcBtn.setText(context.getString(R.string.compute));
        calcBtn.setTextSize(12);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(4);
        container.addView(calcBtn, btnParams);
        calcBtn.setOnClickListener(v -> {
            container.removeAllViews();
            addCrc32Row(container, entry.computeCrc32());
            addChecksumFileHeader(container, "Full checksums");
            startZipEntryChecksumComputation(container, entry.getZipFile(), entry.getFullPath());
        });
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.checksums_of, entry.getName()))
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .create());
    }

    private void addVerifyRow(LinearLayout container, HashUtil.CheckResult r) {
        int color = r.valid ? Color.rgb(0x4C, 0xAF, 0x50) : Color.rgb(0xF4, 0x43, 0x36);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(6), 0, dp(6));

        ImageView icon = new ImageView(context);
        icon.setImageResource(r.valid ? R.drawable.baseline_check_circle_24 : R.drawable.baseline_remove_circle_24);
        icon.setColorFilter(color);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(context);
        nameView.setText(r.fileName);
        nameView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        nameView.setTypeface(null, Typeface.BOLD);
        info.addView(nameView);

        if (r.valid) {
            TextView statusView = new TextView(context);
            statusView.setText(context.getString(R.string.verify_checksum_pass) + " · " + r.algorithm + ": " + r.actual);
            statusView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            statusView.setTextColor(color);
            info.addView(statusView);
        } else {
            TextView statusView = new TextView(context);
            statusView.setText(r.error == null ? context.getString(R.string.verify_checksum_fail) : r.error);
            statusView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            statusView.setTextColor(color);
            info.addView(statusView);
            TextView expectedView = new TextView(context);
            expectedView.setText(context.getString(R.string.expected_hash, r.expected));
            expectedView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            expectedView.setTypeface(Typeface.MONOSPACE);
            info.addView(expectedView);
            if (r.actual != null) {
                TextView actualView = new TextView(context);
                actualView.setText(context.getString(R.string.actual_hash, r.actual));
                actualView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                actualView.setTypeface(Typeface.MONOSPACE);
                info.addView(actualView);
            }
        }

        row.addView(icon);
        row.addView(info);
        container.addView(row);
    }

    public void showHashVerifyDialog(File checksumFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.verify_progress));
        new Thread(() -> {
            try {
                List<HashUtil.CheckResult> results = HashUtil.verifyChecksumFile(checksumFile);
                context.handler.post(() -> {
                    pm.dismiss();
                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_hash_verify, null);
                    LinearLayout container = view.findViewById(R.id.verifyContainer);
                    if (results.isEmpty()) {
                        TextView empty = new TextView(context);
                        empty.setText(context.getString(R.string.checksum_verify_empty));
                        empty.setPadding(0, dp(8), 0, dp(8));
                        container.addView(empty);
                    } else {
                        for (HashUtil.CheckResult r : results) addVerifyRow(container, r);
                    }
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(context.getString(R.string.verify_checksum_title, checksumFile.getName()))
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void showCompareHashesDialog(File file1, File file2) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.computing_checksums));
        new Thread(() -> {
            try {
                Map<String, String> h1 = HashUtil.hashAll(file1);
                Map<String, String> h2 = HashUtil.hashAll(file2);
                context.handler.post(() -> {
                    pm.dismiss();
                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_hash_verify, null);
                    LinearLayout container = view.findViewById(R.id.verifyContainer);
                    for (String algo : HashUtil.ALGORITHMS) {
                        String a = h1.get(algo);
                        String b = h2.get(algo);
                        boolean same = a != null && a.equals(b);
                        int color = same ? Color.rgb(0x4C, 0xAF, 0x50) : Color.rgb(0xF4, 0x43, 0x36);
                        LinearLayout row = new LinearLayout(context);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.TOP);
                        row.setPadding(0, dp(6), 0, dp(6));

                        ImageView icon = new ImageView(context);
                        icon.setImageResource(same ? R.drawable.baseline_check_circle_24 : R.drawable.baseline_remove_circle_24);
                        icon.setColorFilter(color);
                        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));

                        LinearLayout info = new LinearLayout(context);
                        info.setOrientation(LinearLayout.VERTICAL);
                        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        TextView algoView = new TextView(context);
                        algoView.setText(algo + " · " + (same ? context.getString(R.string.hash_equal) : context.getString(R.string.hash_not_equal)));
                        algoView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                        algoView.setTypeface(null, Typeface.BOLD);
                        algoView.setTextColor(color);
                        info.addView(algoView);

                        TextView hash1View = new TextView(context);
                        hash1View.setText(file1.getName() + "\n" + a);
                        hash1View.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                        hash1View.setTypeface(Typeface.MONOSPACE);
                        info.addView(hash1View);

                        TextView hash2View = new TextView(context);
                        hash2View.setText(file2.getName() + "\n" + b);
                        hash2View.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
                        hash2View.setTypeface(Typeface.MONOSPACE);
                        info.addView(hash2View);

                        row.addView(icon);
                        row.addView(info);
                        container.addView(row);
                    }
                    dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                            .setTitle(context.getString(R.string.compare_hashes))
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .create());
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
