package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;

import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;

public class TextEditorActivity extends AppCompatActivity implements UnifiedEditorFragment.EditorCallback {

    private DrawerLayout drawerLayout;
    private ImageButton btnUndo, btnRedo, btnSave, btnEdit, btnFile;

    private Uri currentFileUri;
    private File currentFile;
    private boolean saved;
    private boolean axml;
    private boolean manualFinish;
    private List<ResEntry> resEntries;

    private UnifiedEditorFragment editorFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        initViews();
        setupListeners();
        initEditorFragment();
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editorFragment != null) {
            editorFragment.loadBottomBarFunctions();
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);
        btnEdit = findViewById(R.id.btn_edit);
        btnFile = findViewById(R.id.btn_file);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void initEditorFragment() {
        FragmentManager fm = getSupportFragmentManager();
        editorFragment = (UnifiedEditorFragment) fm.findFragmentById(R.id.editor_container);
        if (editorFragment == null) {
            editorFragment = UnifiedEditorFragment.newInstance("", "Editor", null, UnifiedEditorFragment.TYPE_TEXT);
            fm.beginTransaction().replace(R.id.editor_container, editorFragment).commit();
            fm.executePendingTransactions();
        }
        editorFragment.setCallback(this);
    }

    private UnifiedEditorFragment getFragment() {
        if (editorFragment == null) {
            editorFragment = (UnifiedEditorFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.editor_container);
        }
        return editorFragment;
    }

    private void setupListeners() {
        btnUndo.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null && f.getEditor() != null) {
                if (f.getEditor().canUndo()) f.getEditor().undo();
                updateUndoRedo(f.getEditor().canUndo(), f.getEditor().canRedo());
            }
        });

        btnRedo.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null && f.getEditor() != null) {
                if (f.getEditor().canRedo()) f.getEditor().redo();
                updateUndoRedo(f.getEditor().canUndo(), f.getEditor().canRedo());
            }
        });

        btnSave.setOnClickListener(v -> saveFile());

        btnEdit.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showEditMenu(btnEdit);
        });

        btnFile.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showFileMenu(btnFile);
        });
    }

    public void updateUndoRedo(boolean canUndo, boolean canRedo) {
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
    }

    @Override
    public void onContentModified(String className) {
        saved = false;
    }

    @Override
    public void onUndoRedoChanged(boolean canUndo, boolean canRedo) {
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
    }

    @Override
    public void onSaveRequested() {
        handleIntent(getIntent());
    }

    @Override
    public void onCloseRequested() {
        if (!saved && getFragment() != null && getFragment().getEditor() != null
                && getFragment().getEditor().canUndo()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                    .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                        manualFinish = true;
                        saveFile();
                        currentFileUri = Uri.fromFile(currentFile);
                        Intent resultIntent = new Intent();
                        resultIntent.setData(currentFileUri);
                        resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        setResult(757, resultIntent);
                        finish();
                    })
                    .setNegativeButton(R.string.dont_save, (dialog, which) -> {
                        manualFinish = true;
                        finish();
                    })
                    .setNeutralButton(android.R.string.cancel, null)
                    .setMessage(getString(R.string.confirm_save, currentFile != null ? currentFile.getName() : "file"))
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public void onPreferencesRequested() {
        startActivity(new Intent(this, EditorSettingsActivity.class));
    }

    @Override
    public void onDestroy() {
        UnifiedEditorFragment f = getFragment();
        if (f != null && f.getEditor() != null) {
            f.getEditor().release();
            if (!manualFinish && f.getEditor().canUndo()) {
                boolean isFromFile = currentFile != null;
                if (isFromFile) try (OutputStream os = FileUtils.getOutputStream(
                        new File(getCacheDir(), currentFile.getPath().replace(File.separator, ".")))) {
                    os.write(f.getEditor().getText().toString().getBytes(Charset.forName("UTF-8")));
                } catch (Exception ignored) { }
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        UnifiedEditorFragment f = getFragment();
        if (f != null && f.getEditor() != null && !saved && f.getEditor().canUndo()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                    .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                        manualFinish = true;
                        saveFile();
                        currentFileUri = Uri.fromFile(currentFile);
                        Intent resultIntent = new Intent();
                        resultIntent.setData(currentFileUri);
                        resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        setResult(757, resultIntent);
                        finish();
                    })
                    .setNegativeButton(R.string.dont_save, (dialog, which) -> {
                        manualFinish = true;
                        finish();
                    })
                    .setNeutralButton(android.R.string.cancel, null)
                    .setMessage(getString(R.string.confirm_save, currentFile != null ? currentFile.getName() : "file"))
                    .show();
        } else super.onBackPressed();
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        UnifiedEditorFragment f = getFragment();
        if (f == null) return;

        if (intent.hasExtra("axml")) {
            axml = true;
            f.setAxml(true);
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                f.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
            }
            if (intent.hasExtra("resEntries")) {
                //noinspection unchecked
                resEntries = (List<ResEntry>) intent.getSerializableExtra("resEntries");
            } else if (intent.hasExtra("rssPath")) {
                try(InputStream is = FileUtils.getInputStream(intent.getStringExtra("rssPath")); InputStream is2 = FileUtils.getInputStream(intent.getStringExtra("path"))) {
                    ResourceTableParser rtp = new ResourceTableParser(is);
                    resEntries = rtp.parse();
                    f.setText(new aXMLDecoder(is2, resEntries).decodeAsString());
                } catch (Exception e) { new ErrorUtil(this).showError(e); }
            }
            currentFile = new File(intent.getStringExtra("path"));
            currentFileUri = Uri.fromFile(currentFile);
        } else if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)
                || Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri != null) {
                currentFileUri = uri;
                if ("file".equals(uri.getScheme())) currentFile = new File(uri.getPath());
                loadFile();
            } else if (intent.hasExtra("path")) {
                currentFile = new File(intent.getStringExtra("path"));
                currentFileUri = Uri.fromFile(currentFile);
                loadFile();
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                f.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
            }
        } else if (intent.hasExtra("path")) {
            currentFile = new File(intent.getStringExtra("path"));
            currentFileUri = Uri.fromFile(currentFile);
            loadFile();
        }
    }

    private void loadFile() {
        if (currentFileUri == null && currentFile == null) return;
        UnifiedEditorFragment f = getFragment();
        if (f == null) return;

        boolean isFromFile = currentFile != null;
        File cachedFile = new File(getCacheDir(), (currentFile.getPath()).replace(File.separator, "."));
        if (isFromFile && cachedFile.exists() && cachedFile.length() != currentFile.length()) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.rest_chang).setTitle(R.string.unsaved_changes_found)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        try {
                            FileUtils.copyFile(cachedFile, currentFile);
                            loadEditorText();
                        } catch (Exception e) {
                            new ErrorUtil(this).showError(e);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> loadEditorText()).show();
        } else loadEditorText();
    }

    private void loadEditorText() {
        UnifiedEditorFragment f = getFragment();
        if (f == null) return;

        boolean isFromFile = currentFile != null;
        try (InputStream is = isFromFile ? FileUtils.getInputStream(currentFile)
                : getContentResolver().openInputStream(currentFileUri);
             InputStreamReader isr = new InputStreamReader(is, Charset.forName("UTF-8"));
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            f.setText(sb);
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void saveFile() {
        if (currentFileUri == null && currentFile == null) {
            Extensions.showMessage(this, "No file to save");
            return;
        }
        UnifiedEditorFragment f = getFragment();
        if (f == null || f.getEditor() == null) return;

        try (OutputStream os = (currentFile == null
                ? getContentResolver().openOutputStream(currentFileUri, "wt")
                : FileUtils.getOutputStream(currentFile))) {
            final String text = f.getEditor().getText().toString();
            os.write(axml ? new aXMLEncoder().encodeString(text, this, resEntries) : text.getBytes(Charset.forName("UTF-8")));
            saved = true;
            f.markSaved();
            f.getEditor().getText().addContentListener(new ContentListener() {
                @Override
                public void beforeReplace(Content content) { }

                @Override
                public void afterInsert(Content content, int i, int i1, int i2, int i3, CharSequence charSequence) {
                    saved = false;
                    content.removeContentListener(this);
                }

                @Override
                public void afterDelete(Content content, int i, int i1, int i2, int i3, CharSequence charSequence) {
                    saved = false;
                    content.removeContentListener(this);
                }
            });
        } catch (Exception e) {
            saved = false;
            new ErrorUtil(this).showError(e);
        }
    }
}
