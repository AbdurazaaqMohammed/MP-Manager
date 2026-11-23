package io.github.abdurazaaqmohammed.ui.main;

import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;

import io.github.abdurazaaqmohammed.util.archive.ArchiveBrowseUtil;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.util.io.CopyUtil;
import io.github.abdurazaaqmohammed.util.hash.HashUtil;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.io.SafFs;

import io.github.codehasan.colorpicker.extensions.Extensions;

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
        copyButton.setColorFilter(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
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
        header.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        header.setPadding(0, dp(10), 0, dp(4));
        container.addView(header);
    }

    public interface HashProvider {
        Map<String, String> getHashes();
    }

    public void addVerifySection(LinearLayout container, HashProvider provider) {
        TextView title = new TextView(context);
        title.setText(R.string.verify);
        title.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        title.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        title.setPadding(0, dp(10), 0, dp(4));
        container.addView(title);
        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText input = new EditText(context);
        input.setHint(R.string.verify_hash_hint);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13);
        input.setSingleLine(false);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputRow.addView(input, inputParams);

        MaterialButton pasteBtn = new MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        pasteBtn.setText(android.R.string.paste);
        pasteBtn.setTextSize(12);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pasteParams.setMarginStart(dp(6));
        inputRow.addView(pasteBtn, pasteParams);

        pasteBtn.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence clipText = cm.getPrimaryClip().getItemAt(0).getText();
                    if (clipText != null) {
                        input.setText(clipText.toString().trim());
                        input.setSelection(input.getText().length());
                    }
                }
            } catch (Exception ignored) {}
        });

        container.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButton verifyBtn = new MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        verifyBtn.setText(R.string.verify);
        verifyBtn.setTextSize(12);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(4);
        container.addView(verifyBtn, btnParams);
        TextView result = new TextView(context);
        result.setTextSize(13);
        result.setTypeface(Typeface.MONOSPACE);
        container.addView(result);
        verifyBtn.setOnClickListener(v -> {
            String pasted = input.getText() == null ? "" : input.getText().toString();
            String norm = pasted.toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
            Map<String, String> hashes = null;
            try {
                hashes = provider.getHashes();
            } catch (Exception ignored) {
            }
            String match = null;
            if (!norm.isEmpty() && hashes != null) {
                for (Map.Entry<String, String> e : hashes.entrySet()) {
                    if (e.getValue() == null) continue;
                    String h = e.getValue().toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
                    if (!h.isEmpty() && h.equals(norm)) {
                        match = e.getKey();
                        break;
                    }
                }
            }
            if (match != null) {
                result.setText(context.getString(R.string.hash_match, match));
                result.setTextColor(Color.rgb(0x4C, 0xAF, 0x50));
            } else {
                result.setText(context.getString(R.string.hash_no_match));
                result.setTextColor(Color.rgb(0xF4, 0x43, 0x36));
            }
        });
    }

    public void startChecksumComputation(LinearLayout container, File file) {
        startChecksumComputation(container, file, null);
    }

    public void startChecksumComputation(LinearLayout container, File file, Map<String, String> out) {
        Map<String, TextView> views = new HashMap<>();
        for (String algo : HashUtil.ALGORITHMS) views.put(algo, addChecksumRow(container, algo));
        new Thread(() -> {
            try {
                Map<String, String> hashes = HashUtil.hashAll(file);
                if (out != null) {
                    out.clear();
                    out.putAll(hashes);
                }
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
        startZipEntryChecksumComputation(container, zipFile, entryPath, null);
    }

    public void startZipEntryChecksumComputation(LinearLayout container, File zipFile, String entryPath, Map<String, String> out) {
        Map<String, TextView> views = new HashMap<>();
        for (String algo : HashUtil.ALGORITHMS) views.put(algo, addChecksumRow(container, algo));
        new Thread(() -> {
            File tmpFile = null;
            try {
                tmpFile = File.createTempFile("zip_entry_", ".tmp", context.getCacheDir());
                ArchiveBrowseUtil.extractEntry(zipFile, entryPath, tmpFile);
                Map<String, String> hashes = HashUtil.hashAll(tmpFile);
                if (out != null) {
                    out.clear();
                    out.putAll(hashes);
                }
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
                        addVerifySection(container, () -> results.get(0));
                    } else {
                        Map<String, String> merged = new HashMap<>();
                        for (int i = 0; i < files.size(); i++) {
                            addChecksumFileHeader(container, files.get(i).getName());
                            for (Map.Entry<String, String> e : results.get(i).entrySet()) {
                                TextView valueView = addChecksumRow(container, e.getKey());
                                valueView.setText(e.getValue());
                                merged.put(files.get(i).getName() + " " + e.getKey(), e.getValue());
                            }
                        }
                        addVerifySection(container, () -> merged);
                    }
                    addGenerateSection(container, files);
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
        MaterialButton calcBtn = new MaterialButton(
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

    /** Buttons that write a conventional .sfv (CRC32) / .md5 manifest for the
     *  checked files into their common folder. */
    private void addGenerateSection(LinearLayout container, List<File> files) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        MaterialButton sfvBtn = new MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        sfvBtn.setText(R.string.checksum_generate_sfv);
        sfvBtn.setTextSize(12);
        sfvBtn.setOnClickListener(v -> generateChecksumFile(files, true));
        MaterialButton md5Btn = new MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        md5Btn.setText(R.string.checksum_generate_md5);
        md5Btn.setTextSize(12);
        md5Btn.setOnClickListener(v -> generateChecksumFile(files, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        row.addView(sfvBtn, lp);
        row.addView(md5Btn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(row);
    }

    /**
     * Writes "folder.sfv"/"folder.md5" next to the files. All files must share
     * one folder so manifest entries are plain names; when the target already
     * exists the user confirms the overwrite. The target manifest itself is
     * never an entry — hashing the file that is about to be replaced would
     * embed a permanently wrong checksum.
     */
    private void generateChecksumFile(List<File> files, boolean sfv) {
        File dir = files.get(0).getParentFile();
        boolean sameDir = dir != null;
        if (sameDir) {
            for (File f : files) {
                if (!dir.equals(f.getParentFile())) { sameDir = false; break; }
            }
        }
        if (!sameDir) {
            Extensions.showMessage(context, context.getString(R.string.checksum_same_folder_required));
            return;
        }
        File target = new File(dir, dir.getName() + (sfv ? ".sfv" : ".md5"));
        List<File> hashed = new ArrayList<>();
        for (File f : files) if (!f.equals(target)) hashed.add(f);
        if (hashed.isEmpty()) return;
        for (File file : hashed) {
            if (!HashUtil.canGenerateEntry(file.getName(), sfv)) {
                Extensions.showMessage(context, context.getString(
                        R.string.checksum_unsupported_filename, file.getName()));
                return;
            }
        }
        Runnable write = () -> startManifestGeneration(hashed, target, sfv);
        if (target.exists()) {
            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                    .setTitle(target.getName())
                    .setMessage(context.getString(R.string.checksum_overwrite_confirm, target.getName()))
                    .setPositiveButton(R.string.checksum_overwrite, (d, w) -> write.run())
                    .setNegativeButton(android.R.string.cancel, null)
                    .create());
        } else {
            write.run();
        }
    }

    private void startManifestGeneration(List<File> files, File target, boolean sfv) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.checksum_generating, target.getName()));
        new Thread(() -> {
            try {
                String text = sfv ? HashUtil.generateSfv(files) : HashUtil.generateMd5(files);
                writeManifestFile(target, text);
                context.handler.post(() -> {
                    pm.dismiss();
                    Extensions.showMessage(context, context.getString(R.string.checksum_generated, target.getName()));
                    // The manifest lands in the currently browsed folder: refresh
                    // so it shows up without a manual reload.
                    context.refreshAllPanes();
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    /**
     * Plain write first; when the folder is not app-writable and an elevated
     * file-ops backend is enabled, the manifest is written to a temp file and
     * pushed through the session API instead.
     */
    private void writeManifestFile(File target, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(bytes);
        } catch (IOException plainFailure) {
            if (!PrivilegeManager.Files.fileOpsOn(context)) throw plainFailure;
            File tmp = File.createTempFile("mp_checksum_", null, context.getCacheDir());
            try {
                try (OutputStream out = new FileOutputStream(tmp)) {
                    out.write(bytes);
                }
                PrivilegeManager.Files.uploadFile(context, tmp, target.getAbsolutePath());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
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
            statusView.setText(r.rejected ? context.getString(R.string.checksum_error_invalid_path)
                    : r.missing ? context.getString(R.string.checksum_verify_missing)
                    : r.error == null ? context.getString(R.string.verify_checksum_fail) : r.error);
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

    /** Verifies targets that sit next to the manifest. */
    public void showHashVerifyDialog(File checksumFile) {
        showHashVerifyDialog(checksumFile, checksumFile.getParentFile());
    }

    /**
     * Verifies manifest entries against {@code baseDir}. Entry reads go through
     * {@link #entryOpener}, so siblings in app-readable folders hash directly
     * while root/Shizuku-owned ones are staged per entry — needed when the
     * manifest itself was a staged copy from an elevated folder.
     */
    public void showHashVerifyDialog(File checksumFile, File baseDir) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.verify_progress));
        new Thread(() -> {
            try {
                List<HashUtil.CheckResult> results = HashUtil.verifyChecksumFile(checksumFile,
                        entryOpener(context, baseDir));
                context.handler.post(() -> {
                    pm.dismiss();
                    showVerifyResults(checksumFile.getName(), results);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    /**
     * Entry opener for manifest targets that sit in {@code baseDir}: plain
     * reads when possible, staged elevated copies otherwise. Absent plain
     * targets fail fast as {@link java.io.FileNotFoundException} so they are
     * reported as missing instead of triggering an elevated stage attempt
     * whose generic error would masquerade as a hash mismatch.
     */
    public static HashUtil.EntryOpener entryOpener(android.content.Context context, File baseDir) {
        return rel -> {
            File target = new File(baseDir, rel);
            if (!SafFs.isSafPath(target.getAbsolutePath())
                    && !PrivilegeManager.Files.exists(context, target.getAbsolutePath()))
                throw new FileNotFoundException(target.getAbsolutePath());
            return PrivilegeManager.Staging.openInputStream(context, target);
        };
    }

    /**
     * Verifies a .sfv/.md5 manifest stored inside an archive against its
     * sibling entries: each entry name resolves under the manifest's archive
     * directory and the sibling is extracted to a temp file (deleted on
     * stream close) before hashing.
     */
    public void showZipChecksumVerifyDialog(File archive, String manifestEntryPath) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.verify_progress));
        new Thread(() -> {
            try {
                String algorithm = HashUtil.algorithmForChecksumFile(manifestEntryPath);
                List<HashUtil.CheckResult> results = new ArrayList<>();
                if (algorithm != null) {
                    String base = manifestEntryPath.contains("/")
                            ? manifestEntryPath.substring(0, manifestEntryPath.lastIndexOf('/')) : "";
                    File tmp = File.createTempFile("manifest_", ".tmp", context.getCacheDir());
                    try {
                        ArchiveBrowseUtil.extractEntry(archive, manifestEntryPath, tmp);
                        List<HashUtil.ManifestEntry> entries;
                        try (BufferedReader br = HashUtil.openManifestReader(new FileInputStream(tmp))) {
                            entries = HashUtil.parseManifest(algorithm, br);
                        }
                        results = HashUtil.verifyEntries(algorithm, entries, rel -> {
                            String zipPath = base.isEmpty() ? rel : base + "/" + rel;
                            final File f = File.createTempFile("zipchk_", ".bin", context.getCacheDir());
                            try {
                                ArchiveBrowseUtil.extractEntry(archive, zipPath, f);
                            } catch (Exception e) {
                                //noinspection ResultOfMethodCallIgnored
                                f.delete();
                                FileNotFoundException fnf = new FileNotFoundException(zipPath);
                                fnf.initCause(e);
                                throw fnf;
                            }
                            return deletingStream(f);
                        });
                    } finally {
                        //noinspection ResultOfMethodCallIgnored
                        tmp.delete();
                    }
                }
                String name = manifestEntryPath.contains("/")
                        ? manifestEntryPath.substring(manifestEntryPath.lastIndexOf('/') + 1) : manifestEntryPath;
                List<HashUtil.CheckResult> shown = results;
                context.handler.post(() -> {
                    pm.dismiss();
                    showVerifyResults(name, shown);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    /** Buffered stream over a temp file that removes the file when closed. */
    private InputStream deletingStream(final File f) throws IOException {
        final InputStream in = new BufferedInputStream(new FileInputStream(f), 8192);
        return new FilterInputStream(in) {
            @Override public void close() throws IOException {
                try { in.close(); } finally { //noinspection ResultOfMethodCallIgnored
                    f.delete(); }
            }
        };
    }

    private void showVerifyResults(String name, List<HashUtil.CheckResult> results) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_hash_verify, null);
        LinearLayout container = view.findViewById(R.id.verifyContainer);
        if (results.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(context.getString(R.string.checksum_verify_empty));
            empty.setPadding(0, dp(8), 0, dp(8));
            container.addView(empty);
        } else {
            for (HashUtil.CheckResult r : results) addVerifyRow(container, r);
            int valid = 0, changed = 0, other = 0;
            for (HashUtil.CheckResult r : results) {
                if (r.valid) valid++;
                else if (r.rejected || r.missing || r.actual == null) other++;
                else changed++;
            }
            TextView summary = new TextView(context);
            summary.setText(context.getString(R.string.checksum_verify_summary, valid, changed, other));
            summary.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            summary.setTypeface(null, Typeface.BOLD);
            summary.setPadding(0, dp(8), 0, dp(2));
            container.addView(summary);
        }
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.verify_checksum_title, name))
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .create());
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
