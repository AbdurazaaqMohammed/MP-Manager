package io.github.abdurazaaqmohammed.feature.crypto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/** Content-first decryption and file analysis workspace. */
public class FileCryptoActivity extends BaseActivity {

    public static final String EXTRA_PATH = "file_crypto_path";
    private static final int REQ_FILE = 7601;
    private static final int REQ_DIR = 7602;

    private TextView sourceInfo;
    private TextView statusText;
    private TextView detailText;
    private TextView keyStatus;
    private EditText passwordInput;
    private EditText keyInput;
    private EditText ivInput;
    private EditText outputInput;
    private CheckBox allBox;
    private ProgressBar progress;
    private ListView list;
    private ArrayAdapter<String> adapter;

    private final List<FileCryptoEngine.Finding> findings = new ArrayList<>();
    private File source;
    private FileCryptoEngine.Finding selected;
    private FileCryptoEngine.Result lastResult;
    private File lastOutput;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("file_crypto_ui", MODE_PRIVATE);
        buildUi();
        String requested = getIntent().getStringExtra(EXTRA_PATH);
        if (requested != null && !requested.isEmpty()) {
            File requestedFile = new File(requested);
            if (requestedFile.exists()) setSource(requestedFile, "");
        }
    }

    private int color(int attr) {
        View v = findViewById(android.R.id.content);
        if (v == null) v = getWindow().getDecorView();
        return MaterialColors.getColor(v, attr, 0xFF121212);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        setContentView(root);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.file_crypto_title);
        toolbar.setSubtitle(R.string.file_crypto_subtitle);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, 1, 0, R.string.crypto_copy_report);
        toolbar.getMenu().add(0, 2, 1, R.string.crypto_smart_analysis);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                copyReport();
                return true;
            }
            if (item.getItemId() == 2) {
                askAi();
                return true;
            }
            return false;
        });
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MaterialCardView sourceCard = card(body);
        LinearLayout sourceBox = column(sourceCard);
        sourceBox.addView(title(getString(R.string.crypto_source)));
        sourceInfo = text(getString(R.string.crypto_no_source));
        sourceInfo.setTextIsSelectable(true);
        sourceBox.addView(sourceInfo);
        LinearLayout sourceRow = row();
        MaterialButton file = button(getString(R.string.crypto_btn_file));
        file.setOnClickListener(v -> pickFile());
        MaterialButton dir = button(getString(R.string.crypto_btn_dir));
        dir.setOnClickListener(v -> pickDir());
        MaterialButton app = button(getString(R.string.crypto_btn_app));
        app.setOnClickListener(v -> pickInstalledApp());
        sourceRow.addView(file);
        sourceRow.addView(dir);
        sourceRow.addView(app);
        sourceBox.addView(sourceRow);

        MaterialCardView actionCard = card(body);
        LinearLayout actions = column(actionCard);
        actions.addView(title(getString(R.string.crypto_actions)));
        allBox = new CheckBox(this);
        allBox.setText(R.string.crypto_show_all);
        allBox.setChecked(prefs.getBoolean("show_all", false));
        allBox.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("show_all", checked).apply());
        actions.addView(allBox);
        LinearLayout actionRow = row();
        MaterialButton scan = button(getString(R.string.crypto_btn_scan));
        scan.setOnClickListener(v -> scanSource());
        MaterialButton decrypt = button(getString(R.string.crypto_btn_decrypt));
        decrypt.setOnClickListener(v -> decryptSelected());
        MaterialButton batch = button(getString(R.string.crypto_btn_batch));
        batch.setOnClickListener(v -> decryptAll());
        actionRow.addView(scan);
        actionRow.addView(decrypt);
        actionRow.addView(batch);
        actions.addView(actionRow);
        LinearLayout actionRow2 = row();
        MaterialButton preview = button(getString(R.string.crypto_btn_preview));
        preview.setOnClickListener(v -> previewSelected());
        MaterialButton save = button(getString(R.string.crypto_btn_save));
        save.setOnClickListener(v -> saveLastResult());
        MaterialButton extract = button(getString(R.string.crypto_btn_extract));
        extract.setOnClickListener(v -> extractSelectedArchive());
        actionRow2.addView(preview);
        actionRow2.addView(save);
        actionRow2.addView(extract);
        actions.addView(actionRow2);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        actions.addView(progress);
        statusText = text(getString(R.string.crypto_pick_source_hint));
        statusText.setTextIsSelectable(true);
        actions.addView(statusText);

        MaterialCardView listCard = card(body);
        LinearLayout listBox = column(listCard);
        listBox.addView(title(getString(R.string.crypto_results)));
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(1));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < findings.size()) {
                selected = findings.get(position);
                showDetails(selected);
            }
        });
        listBox.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        listBox.addView(text(getString(R.string.crypto_tap_result_hint)));

        MaterialCardView keyCard = card(body);
        LinearLayout keys = column(keyCard);
        keys.addView(title(getString(R.string.crypto_keys_section)));
        passwordInput = field(keys, getString(R.string.crypto_pwd_hint), prefs.getString("password", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput = field(keys, getString(R.string.crypto_key_hint), prefs.getString("key", ""),
                InputType.TYPE_CLASS_TEXT);
        ivInput = field(keys, getString(R.string.crypto_iv_hint), prefs.getString("iv", ""),
                InputType.TYPE_CLASS_TEXT);
        keyStatus = text(getString(R.string.crypto_key_status_hint));
        keys.addView(keyStatus);
        LinearLayout keyRow = row();
        MaterialButton useKey = button(getString(R.string.crypto_btn_use_key));
        useKey.setOnClickListener(v -> decryptSelected());
        MaterialButton clearKey = button(getString(R.string.crypto_btn_clear_key));
        clearKey.setOnClickListener(v -> {
            passwordInput.setText("");
            keyInput.setText("");
            ivInput.setText("");
            keyStatus.setText(R.string.crypto_keys_cleared);
        });
        keyRow.addView(useKey);
        keyRow.addView(clearKey);
        keys.addView(keyRow);

        MaterialCardView detailCard = card(body);
        LinearLayout detailBox = column(detailCard);
        detailBox.addView(title(getString(R.string.crypto_details_section)));
        detailText = text(getString(R.string.crypto_no_selection));
        detailText.setTextIsSelectable(true);
        detailBox.addView(detailText);

        MaterialCardView outputCard = card(body);
        LinearLayout outputBox = column(outputCard);
        outputBox.addView(title(getString(R.string.crypto_output_dir_section)));
        outputInput = field(outputBox, getString(R.string.crypto_output_path_hint), prefs.getString("output_dir",
                new File(getExternalFilesDir(null), "decrypted").getAbsolutePath()),
                InputType.TYPE_CLASS_TEXT);
        outputInput.setOnFocusChangeListener((v, focused) -> {
            if (!focused) prefs.edit()
                    .putString("output_dir", outputInput.getText().toString().trim()).apply();
        });
        outputBox.addView(text(getString(R.string.crypto_output_disclaimer)));
    }

    private MaterialCardView card(LinearLayout parent) {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(20));
        c.setCardElevation(dp(1));
        c.setStrokeWidth(dp(1));
        c.setStrokeColor(color(com.google.android.material.R.attr.colorOutline));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        parent.addView(c, lp);
        return c;
    }

    private LinearLayout column(MaterialCardView card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(box);
        return box;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        return r;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView text(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(13f);
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private MaterialButton button(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText field(LinearLayout parent, String hint, String value, int inputType) {
        TextView label = text(hint);
        label.setTextSize(12f);
        parent.addView(label);
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        e.setInputType(inputType);
        parent.addView(e);
        return e;
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_FILE);
    }

    private void pickDir() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(i, REQ_DIR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_FILE) {
            try {
                setSource(copyUriToCache(uri), "");
            } catch (Exception e) {
                toast(getString(R.string.crypto_open_file_failed, e.getMessage()));
            }
        } else if (requestCode == REQ_DIR) {
            File dir = treeUriToFile(uri);
            if (dir != null && dir.isDirectory()) {
                setSource(dir, "");
            } else {
                toast(getString(R.string.crypto_folder_path_failed));
            }
        }
    }

    private File copyUriToCache(Uri uri) throws Exception {
        String name = displayName(uri);
        if (name != null) name = new File(name.replace('\\', '/')).getName();
        if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "selected_" + System.currentTimeMillis() + ".bin";
        }
        File dir = new File(getCacheDir(), "crypto_source");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception(getString(R.string.crypto_temp_dir_failed));
        File out = new File(dir, name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception(getString(R.string.crypto_source_unreadable));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        return out;
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private File treeUriToFile(Uri uri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            String[] parts = docId.split(":", 2);
            if (parts.length == 2) {
                if ("primary".equalsIgnoreCase(parts[0])) {
                    return new File(Environment.getExternalStorageDirectory(), parts[1]);
                }
                return new File("/storage/" + parts[0], parts[1]);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void pickInstalledApp() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<ApplicationInfo> usable = new ArrayList<>();
        for (ApplicationInfo info : apps) {
            if (info.sourceDir != null && new File(info.sourceDir).isFile()) usable.add(info);
        }
        Collections.sort(usable, (a, b) -> pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        String[] labels = new String[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            labels[i] = pm.getApplicationLabel(usable.get(i)) + "\n" + usable.get(i).packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.crypto_pick_app_title)
                .setItems(labels, (d, which) -> {
                    ApplicationInfo info = usable.get(which);
                    setSource(new File(info.sourceDir), info.packageName);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setSource(File file, String label) {
        source = file;
        selected = null;
        lastResult = null;
        findings.clear();
        adapter.clear();
        adapter.notifyDataSetChanged();
        detailText.setText(getString(R.string.crypto_no_selection));
        sourceInfo.setText(getString(R.string.crypto_source) + ": " + file.getName() + "\n" + file.getAbsolutePath()
                + (label == null || label.isEmpty() ? "" : "\n" + label));
        statusText.setText(R.string.crypto_ready_scan);
        scanSource();
    }

    private void scanSource() {
        if (source == null || !source.exists()) {
            toast(getString(R.string.crypto_select_source_first));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        statusText.setText(R.string.crypto_scanning);
        File target = source;
        boolean includeAll = allBox.isChecked();
        AppExecutors.io().execute(() -> {
            List<FileCryptoEngine.Finding> result = FileCryptoEngine.scan(target, includeAll,
                    (message, done, total) -> AppExecutors.runOnUiIfAlive(this,
                            () -> statusText.setText(message)));
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                findings.clear();
                findings.addAll(result);
                adapter.clear();
                for (FileCryptoEngine.Finding f : findings) {
                    adapter.add(f.shortLabel() + "\n" + FileCryptoEngine.formatSize(f.size)
                            + "  ·  " + f.path);
                }
                adapter.notifyDataSetChanged();
                statusText.setText(getString(R.string.crypto_scan_found, findings.size()));
                if (!findings.isEmpty()) {
                    selected = findings.get(0);
                    showDetails(selected);
                } else {
                    selected = null;
                    detailText.setText(R.string.crypto_scan_empty);
                }
            });
        });
    }

    private void showDetails(FileCryptoEngine.Finding f) {
        if (f == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.crypto_field_name)).append(": ").append(f.name).append('\n');
        sb.append(getString(R.string.crypto_field_path)).append(": ").append(f.path).append('\n');
        if (!f.container.isEmpty()) sb.append(getString(R.string.crypto_field_container)).append(": ").append(f.container).append('\n');
        sb.append(getString(R.string.crypto_field_size)).append(": ").append(FileCryptoEngine.formatSize(f.size)).append('\n');
        sb.append(getString(R.string.crypto_field_type)).append(": ").append(f.type).append('\n');
        sb.append(getString(R.string.crypto_field_classification)).append(": ").append(kindLabel(f.kind)).append('\n');
        sb.append(getString(R.string.crypto_field_status)).append(": ").append(f.status).append('\n');
        if (!f.encoding.isEmpty()) sb.append(getString(R.string.crypto_field_encoding)).append(": ").append(f.encoding).append('\n');
        if (!f.cipher.isEmpty()) sb.append(getString(R.string.crypto_field_cipher)).append(": ").append(f.cipher).append('\n');
        sb.append(getString(R.string.crypto_field_entropy)).append(": ").append(String.format(java.util.Locale.US, "%.2f", f.entropy))
                .append("/8.0  ·  ").append(getString(R.string.crypto_field_confidence)).append(": ").append(f.confidence).append("%\n");
        if (!f.detail.isEmpty()) sb.append('\n').append(f.detail);
        if (f.needsKey || f.encrypted) {
            sb.append("\n\n").append(getString(R.string.crypto_details_key_note));
        }
        detailText.setText(sb.toString());
        keyStatus.setText(f.zipEncrypted ? R.string.crypto_status_zip_encrypted
                : f.needsKey ? R.string.crypto_status_needs_key
                : R.string.crypto_status_ready_decode);
    }

    private String kindLabel(FileCryptoEngine.Kind kind) {
        if (kind == FileCryptoEngine.Kind.TEXT) return getString(R.string.crypto_kind_text);
        if (kind == FileCryptoEngine.Kind.ENCODING) return getString(R.string.crypto_kind_encoding);
        if (kind == FileCryptoEngine.Kind.COMPRESSION) return getString(R.string.crypto_kind_compression);
        if (kind == FileCryptoEngine.Kind.ENCRYPTION) return getString(R.string.crypto_kind_encryption);
        if (kind == FileCryptoEngine.Kind.ARCHIVE) return getString(R.string.crypto_kind_archive);
        if (kind == FileCryptoEngine.Kind.BINARY) return getString(R.string.crypto_kind_binary);
        return getString(R.string.crypto_kind_unknown);
    }

    private void previewSelected() {
        if (selected == null) {
            toast(getString(R.string.crypto_select_first));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        FileCryptoEngine.Finding f = selected;
        AppExecutors.io().execute(() -> {
            try {
                byte[] data = readFinding(f);
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    showPreview(getString(R.string.crypto_preview_before, f.name), data);
                });
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    toast(getString(R.string.crypto_read_failed, e.getMessage()));
                });
            }
        });
    }

    private byte[] readFinding(FileCryptoEngine.Finding f) throws Exception {
        if (f.archivePath != null && !f.archivePath.isEmpty()
                && f.entryName != null && !f.entryName.isEmpty()) {
            net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(new File(f.archivePath));
            for (net.lingala.zip4j.model.FileHeader h : z.getFileHeaders()) {
                if (f.entryName.equals(h.getFileName())) {
                    try (InputStream in = z.getInputStream(h)) {
                        return FileCryptoEngine.readLimited(in, FileCryptoEngine.MAX_SCAN_BYTES);
                    }
                }
            }
            throw new Exception(getString(R.string.crypto_entry_not_found));
        }
        return FileCryptoEngine.readFile(new File(f.path));
    }

    private void decryptSelected() {
        if (selected == null) {
            toast(getString(R.string.crypto_select_first));
            return;
        }
        FileCryptoEngine.Finding f = selected;
        String password = passwordInput.getText().toString();
        String key = keyInput.getText().toString();
        String iv = ivInput.getText().toString();
        prefs.edit().putString("password", password).putString("key", key)
                .putString("iv", iv).apply();
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        statusText.setText(getString(R.string.crypto_decoding_item, f.name));
        AppExecutors.io().execute(() -> {
            try {
                byte[] data = f.zipEncrypted ? null : readFinding(f);
                FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, password, key, iv);
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    if (r.success) {
                        lastResult = r;
                        statusText.setText(getString(r.uncertain
                                ? R.string.crypto_method_applied_heuristic
                                : R.string.crypto_method_applied, r.method));
                        showPreview(getString(R.string.crypto_preview_after, f.name), r.data);
                    } else {
                        statusText.setText(r.needsKey ? R.string.crypto_decode_requires_key
                                : R.string.crypto_decode_no_valid);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.crypto_decode_result_title)
                                .setMessage(getString(R.string.crypto_decode_failed_note, r.message))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                });
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setVisibility(View.GONE);
                    statusText.setText(getString(R.string.crypto_decode_failed, e.getMessage()));
                });
            }
        });
    }

    private void decryptAll() {
        if (findings.isEmpty()) {
            toast(getString(R.string.crypto_no_results_scan));
            return;
        }
        String password = passwordInput.getText().toString();
        String key = keyInput.getText().toString();
        String iv = ivInput.getText().toString();
        File outDir = new File(outputInput.getText().toString().trim());
        if (!outDir.exists() && !outDir.mkdirs()) {
            toast(getString(R.string.crypto_output_dir_failed));
            return;
        }
        List<FileCryptoEngine.Finding> targets = new ArrayList<>(findings);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setMax(targets.size());
        progress.setProgress(0);
        AppExecutors.io().execute(() -> {
            int ok = 0;
            int failed = 0;
            int needKey = 0;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                FileCryptoEngine.Finding f = targets.get(i);
                try {
                    byte[] data = f.zipEncrypted ? null : readFinding(f);
                    FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, password, key, iv);
                    if (r.success && r.data != null) {
                        File saved = writeResult(f, r, outDir);
                        if (saved != null) {
                            ok++;
                            lines.add("✔ " + f.name + " → " + r.method + " → " + saved.getName());
                        } else {
                            failed++;
                            lines.add("✘ " + f.name + " -> " + getString(R.string.crypto_failed_to_save_short));
                        }
                    } else {
                        failed++;
                        if (r.needsKey) needKey++;
                        lines.add("✘ " + f.name + " → " + r.message);
                    }
                } catch (Exception e) {
                    failed++;
                    lines.add("✘ " + f.name + " → " + e.getMessage());
                }
                final int done = i + 1;
                AppExecutors.runOnUiIfAlive(this, () -> {
                    progress.setProgress(done);
                    statusText.setText(getString(R.string.crypto_batch_progress, done, targets.size()));
                });
            }
            final int fOk = ok;
            final int fFail = failed;
            final int fKey = needKey;
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                statusText.setText(getString(R.string.crypto_batch_summary,
                        fOk, fFail, fKey, outDir.getAbsolutePath()));
                StringBuilder sb = new StringBuilder();
                for (String line : lines) sb.append(line).append('\n');
                new AlertDialog.Builder(this)
                        .setTitle(R.string.crypto_batch_results_title)
                        .setMessage(sb.length() == 0 ? getString(R.string.crypto_no_results) : sb.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    private File writeResult(FileCryptoEngine.Finding f, FileCryptoEngine.Result r, File outDir) {
        try {
            String base = f.name == null || f.name.isEmpty() ? "result" : f.name;
            base = base.replaceAll("[^A-Za-z0-9._-]", "_");
            String ext = extensionFor(r.outputType, r.data);
            File out = new File(outDir, base + "." + r.method.replaceAll("[^A-Za-z0-9_-]", "_") + ext);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(r.data);
            }
            lastOutput = out;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String extensionFor(String type, byte[] data) {
        if (type.contains("PNG")) return ".png";
        if (type.contains("JPEG")) return ".jpg";
        if (type.contains("GIF")) return ".gif";
        if (type.contains("WEBP")) return ".webp";
        if (type.contains("PDF")) return ".pdf";
        if (type.contains("ZIP")) return ".zip";
        if (type.contains("GZIP")) return ".gz";
        if (type.contains("DEX")) return ".dex";
        if (type.contains("ELF")) return ".so";
        if (type.contains("SQLite")) return ".db";
        if (FileCryptoEngine.isPlainText(data)) return ".txt";
        return ".bin";
    }

    private void showPreview(String title, byte[] data) {
        if (data == null) {
            toast(getString(R.string.crypto_no_preview_data));
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bmp != null) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setImageBitmap(bmp);
            box.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        }
        TextView info = text(getString(R.string.crypto_preview_meta,
                FileCryptoEngine.detectType(data, ""),
                FileCryptoEngine.formatSize(data.length),
                FileCryptoEngine.sha256(data)));
        info.setTextIsSelectable(true);
        box.addView(info);
        TextView body = text(FileCryptoEngine.previewText(data, 20000));
        body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(11f);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.crypto_copy_text, (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("crypto-preview",
                            FileCryptoEngine.previewText(data, 20000)));
                    toast(getString(R.string.crypto_content_copied));
                })
                .create();
        dialog.show();
    }

    private void saveLastResult() {
        if (lastResult == null || lastResult.data == null) {
            toast(getString(R.string.crypto_no_result_to_save));
            return;
        }
        File dir = new File(outputInput.getText().toString().trim());
        if (!dir.exists() && !dir.mkdirs()) {
            toast(getString(R.string.crypto_output_dir_failed));
            return;
        }
        File saved = writeResult(selected, lastResult, dir);
        if (saved == null) {
            toast(getString(R.string.crypto_save_fail_generic));
            return;
        }
        statusText.setText(getString(R.string.crypto_save_success, saved.getAbsolutePath()));
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", saved);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/octet-stream");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.share)));
        } catch (Exception ignored) {
        }
    }

    private void extractSelectedArchive() {
        if (selected == null) {
            toast(getString(R.string.crypto_select_first));
            return;
        }
        File archive = selected.archivePath != null && !selected.archivePath.isEmpty()
                ? new File(selected.archivePath) : new File(selected.path);
        if (!archive.isFile() || !FileCryptoEngine.isZip(archive)) {
            toast(getString(R.string.crypto_not_archive));
            return;
        }
        File outDir = new File(outputInput.getText().toString().trim(),
                FileCryptoEngine.baseName(archive.getName()) + "_extracted");
        if (!outDir.exists() && !outDir.mkdirs()) {
            toast(getString(R.string.crypto_extract_dir_failed));
            return;
        }
        String password = passwordInput.getText().toString();
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        AppExecutors.io().execute(() -> {
            int count = 0;
            String error = "";
            try {
                String canonicalOut = outDir.getCanonicalPath();
                if (!canonicalOut.endsWith(File.separator)) canonicalOut += File.separator;
                net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(archive);
                if (password != null && !password.isEmpty()) z.setPassword(password.toCharArray());
                for (net.lingala.zip4j.model.FileHeader h : z.getFileHeaders()) {
                    if (h.isDirectory()) continue;
                    String safe = h.getFileName().replace('\\', '/');
                    while (safe.startsWith("/")) safe = safe.substring(1);
                    if (safe.isEmpty()) continue;
                    File target = new File(outDir, safe);
                    if (!target.getCanonicalPath().startsWith(canonicalOut)) {
                        throw new java.io.IOException(getString(R.string.crypto_extract_blocked_entry, h.getFileName()));
                    }
                    File parent = target.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (InputStream in = z.getInputStream(h);
                         FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    count++;
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final int extracted = count;
            final String err = error;
            AppExecutors.runOnUiIfAlive(this, () -> {
                progress.setVisibility(View.GONE);
                String successMsg = getString(R.string.crypto_extract_success, extracted, outDir.getAbsolutePath());
                statusText.setText(err.isEmpty() ? successMsg : getString(R.string.crypto_extract_failed, err));
                new AlertDialog.Builder(this)
                        .setTitle(R.string.crypto_extract_title)
                        .setMessage(err.isEmpty() ? successMsg : err)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    private void askAi() {
        if (selected == null) {
            toast(getString(R.string.crypto_select_first));
            return;
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append(getString(R.string.crypto_analysis_header)).append("\n\n");
        prompt.append(getString(R.string.crypto_field_name)).append(": ").append(selected.name).append('\n');
        prompt.append(getString(R.string.crypto_field_path)).append(": ").append(selected.path).append('\n');
        prompt.append(getString(R.string.crypto_field_type)).append(": ").append(selected.type).append('\n');
        prompt.append(getString(R.string.crypto_field_status)).append(": ").append(selected.status).append('\n');
        if (!selected.encoding.isEmpty()) prompt.append(getString(R.string.crypto_field_encoding)).append(": ").append(selected.encoding).append('\n');
        if (!selected.cipher.isEmpty()) prompt.append(getString(R.string.crypto_field_cipher)).append(": ").append(selected.cipher).append('\n');
        prompt.append(getString(R.string.crypto_field_entropy)).append(": ").append(String.format(java.util.Locale.US, "%.2f", selected.entropy)).append('\n');
        if (!selected.detail.isEmpty()) prompt.append(getString(R.string.crypto_field_detail)).append(": ").append(selected.detail).append('\n');

        final String reportText = prompt.toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("crypto-analysis", reportText));
        toast(getString(R.string.crypto_analysis_copied));

        new AlertDialog.Builder(this)
                .setTitle(R.string.crypto_analysis_dialog_title)
                .setMessage(reportText)
                .setPositiveButton(android.R.string.copy, (d, w) -> {
                    cm.setPrimaryClip(ClipData.newPlainText("crypto-analysis", reportText));
                    toast(getString(R.string.crypto_analysis_copied));
                })
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private void copyReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("File Decryption & Cryptographic Analysis Report\n");
        if (source != null) sb.append("Source: ").append(source.getAbsolutePath()).append('\n');
        sb.append("Total findings: ").append(findings.size()).append("\n\n");
        for (FileCryptoEngine.Finding f : findings) {
            sb.append(f.shortLabel()).append('\n');
            sb.append("  ").append(f.path).append('\n');
            if (!f.cipher.isEmpty()) sb.append("  Cipher: ").append(f.cipher).append('\n');
            if (!f.encoding.isEmpty()) sb.append("  Encoding: ").append(f.encoding).append('\n');
            sb.append("  Entropy: ").append(String.format(java.util.Locale.US, "%.2f", f.entropy))
                    .append(" · Confidence: ").append(f.confidence).append("%\n");
            if (!f.detail.isEmpty()) sb.append("  ").append(f.detail.replace("\n", "\n  ")).append('\n');
            sb.append('\n');
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("crypto-report", sb.toString()));
        toast(getString(R.string.crypto_report_copied));
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
    }
}
