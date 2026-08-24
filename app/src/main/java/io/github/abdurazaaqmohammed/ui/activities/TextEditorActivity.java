package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

public class TextEditorActivity extends AppCompatActivity implements UnifiedEditorFragment.EditorCallback {

    private static class EditorTab {
        String title;
        Uri fileUri;
        File file;
        boolean axml;
        List<ResEntry> resEntries;
        String pendingDecoded;
        String content;
        boolean loaded;
        boolean loading;
        boolean modified;
    }

    private DrawerLayout drawerLayout;
    private FastScrollerRecyclerView tabsRecyclerView;
    private TabRowAdapter tabAdapter;
    private ImageButton btnUndo, btnRedo, btnSave, btnEdit, btnFile;

    private Uri currentFileUri;
    private File currentFile;
    private boolean axml;
    private boolean manualFinish;
    private List<ResEntry> resEntries;

    private final List<EditorTab> tabs = new java.util.ArrayList<>();
    private int currentIndex = -1;
    private boolean sessionRestored;

    private UnifiedEditorFragment editorFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        initViews();
        setupListeners();
        initEditorFragment();
        restoreSession();
        if (sessionRestored && currentIndex >= 0 && currentIndex < tabs.size()) selectTab(currentIndex);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editorFragment != null) {
            editorFragment.loadBottomBarFunctions();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        UnifiedEditorFragment f = getFragment();
        EditorTab t = getCurrentTab();
        if (f != null && f.getEditor() != null && t != null) {
            t.content = f.getEditor().getText().toString();
        }
        persistSession();
    }

    // ------------------------------------------------------------------ session persistence

    private File sessionFile() {
        return new File(getCacheDir(), "text_editor_session.json");
    }

    private void persistSession() {
        try {
            UnifiedEditorFragment f = getFragment();
            JSONArray arr = new JSONArray();
            int savedCurrent = 0;
            for (int i = 0; i < tabs.size(); i++) {
                EditorTab t = tabs.get(i);
                if (t.axml) continue;
                JSONObject o = new JSONObject();
                o.put("title", t.title);
                if (t.file != null) o.put("file", t.file.getPath());
                else if (t.fileUri != null) o.put("uri", t.fileUri.toString());
                o.put("modified", t.modified);
                boolean untitledWithText = t.file == null && t.fileUri == null && t.loaded
                        && t.content != null && !t.content.isEmpty();
                if (t.modified || untitledWithText) {
                    String content;
                    if (i == currentIndex && f != null && f.getEditor() != null) content = f.getCode();
                    else content = t.content == null ? "" : t.content;
                    o.put("content", content);
                }
                if (i == currentIndex) savedCurrent = arr.length();
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("tabs", arr);
            root.put("current", savedCurrent);
            File file = sessionFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (OutputStream os = FileUtils.getOutputStream(file)) {
                os.write(root.toString().getBytes(Charset.forName("UTF-8")));
            }
        } catch (Exception ignored) { }
    }

    private void restoreSession() {
        File file = sessionFile();
        if (!file.exists()) return;
        try (InputStream is = FileUtils.getInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("tabs");
            if (arr == null || arr.length() == 0) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                EditorTab t = new EditorTab();
                t.title = o.optString("title", getString(android.R.string.untitled));
                String filePath = o.has("file") ? o.getString("file") : null;
                if (filePath != null) {
                    t.file = new File(filePath);
                    t.fileUri = Uri.fromFile(t.file);
                } else {
                    String uriStr = o.optString("uri", null);
                    if (uriStr != null) t.fileUri = Uri.parse(uriStr);
                }
                t.modified = o.optBoolean("modified", false);
                if (o.has("content")) {
                    t.content = o.getString("content");
                    t.loaded = true;
                } else if (t.file == null && t.fileUri == null) {
                    continue; // nothing restorable for this tab
                }
                tabs.add(t);
            }
            if (!tabs.isEmpty()) {
                sessionRestored = true;
                currentIndex = Math.max(0, Math.min(root.optInt("current", 0), tabs.size() - 1));
                updateTabsList();
            }
        } catch (Exception e) {
            // Corrupt session cache: start fresh instead of blocking the editor
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnSave = findViewById(R.id.btn_save);
        btnEdit = findViewById(R.id.btn_edit);
        btnFile = findViewById(R.id.btn_file);

        tabsRecyclerView = findViewById(R.id.tabs_recycler_view);
        tabsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tabAdapter = new TabRowAdapter();
        tabsRecyclerView.setAdapter(tabAdapter);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void initEditorFragment() {
        FragmentManager fm = getSupportFragmentManager();
        editorFragment = (UnifiedEditorFragment) fm.findFragmentById(R.id.editor_container);
        if (editorFragment == null) {
            // Pass null (not "") for content: a non-null initialContentText makes the fragment's
            // posted init runnable wipe whatever text we set synchronously (e.g. decoded axml)
            editorFragment = UnifiedEditorFragment.newInstance(null, "Editor", null, UnifiedEditorFragment.TYPE_TEXT);
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

    private EditorTab getCurrentTab() {
        if (currentIndex >= 0 && currentIndex < tabs.size()) return tabs.get(currentIndex);
        return null;
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

    private class TabRowAdapter extends RecyclerView.Adapter<TabRowAdapter.TabVH> {

        private class TabVH extends RecyclerView.ViewHolder {
            final TextView title;
            final View close;

            TabVH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tab_title);
                close = itemView.findViewById(R.id.tab_close);
            }
        }

        @NonNull
        @Override
        public TabVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new TabVH(getLayoutInflater().inflate(R.layout.editor_tab_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull TabVH holder, int position) {
            EditorTab tab = tabs.get(position);
            holder.title.setText(getTabLabel(tab));
            holder.title.setTextColor(position == currentIndex
                    ? MaterialColors.getColor(holder.title, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
                    : MaterialColors.getColor(holder.title, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
            holder.close.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0) closeTab(pos);
            });
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos >= 0) {
                    selectTab(pos);
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            });
        }

        @Override
        public int getItemCount() {
            return tabs.size();
        }
    }

    private void updateTabsList() {
        if (tabAdapter != null) tabAdapter.notifyDataSetChanged();
    }

    private String getTabLabel(EditorTab tab) {
        return (tab.modified ? "\u25CF " : "") + tab.title;
    }

    private void updateTitleBar() {
        EditorTab t = getCurrentTab();
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(t != null ? t.title : null);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        UnifiedEditorFragment f = getFragment();

        if (currentIndex >= 0 && currentIndex < tabs.size() && currentIndex != index
                && f != null && f.getEditor() != null) {
            tabs.get(currentIndex).content = f.getCode();
        }

        currentIndex = index;
        EditorTab t = tabs.get(index);
        currentFile = t.file;
        currentFileUri = t.fileUri;
        axml = t.axml;
        resEntries = t.resEntries;

        if (f != null && f.getEditor() != null && t.loaded) {
            boolean wasModified = t.modified; // setText fires a change event; preserve real state
            f.setText(t.content == null ? "" : t.content);
            t.modified = wasModified;
        }
        if (!t.loaded && !t.loading && (t.file != null || t.fileUri != null)) loadTabContent(t);
        updateTitleBar();
        updateTabsList();
        tabsRecyclerView.scrollToPosition(index);
    }

    private void addTabAndOpen(EditorTab tab) {
        tabs.add(tab);
        selectTab(tabs.size() - 1);

        if (!tab.loaded) loadTabContent(tab);
        persistSession();
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        UnifiedEditorFragment f = getFragment();
        if (f == null) return;

        Uri uri = null;
        File file = null;
        boolean isAxml = false;
        List<ResEntry> entries = null;
        String extraText = null;

        if (intent.hasExtra("axml")) {
            isAxml = true;
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
            if (intent.hasExtra("resEntries")) {
                //noinspection unchecked
                entries = (List<ResEntry>) intent.getSerializableExtra("resEntries");
            } else if (intent.hasExtra("rssPath")) {
                try(InputStream is = FileUtils.getInputStream(intent.getStringExtra("rssPath")); InputStream is2 = FileUtils.getInputStream(intent.getStringExtra("path"))) {
                    ResourceTableParser rtp = new ResourceTableParser(is);
                    entries = rtp.parse();
                    extraText = new aXMLDecoder(is2, entries).decodeAsString();
                } catch (Exception e) { new ErrorUtil(this).showError(e); }
            }
            file = new File(intent.getStringExtra("path"));
            uri = Uri.fromFile(file);
        } else if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)
                || Intent.ACTION_SEND.equals(action)) {
            uri = intent.getData();
            if (uri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri != null) {
                if ("file".equals(uri.getScheme())) file = new File(uri.getPath());
            } else if (intent.hasExtra("path")) {
                file = new File(intent.getStringExtra("path"));
                uri = Uri.fromFile(file);
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        } else if (intent.hasExtra("path")) {
            file = new File(intent.getStringExtra("path"));
            uri = Uri.fromFile(file);
        }

        EditorTab existing = findExistingTab(file, uri);
        if (existing != null) {
            int idx = tabs.indexOf(existing);
            selectTab(idx);
            updateTabsList();
            return;
        }

        if (file == null && uri == null && extraText == null && !tabs.isEmpty()) {
            // Bare launch with a restored session: keep the previously open tabs
            updateTabsList();
            return;
        }

        EditorTab tab = new EditorTab();
        tab.file = file;
        tab.fileUri = uri;
        tab.axml = isAxml;
        tab.resEntries = entries;
        tab.title = resolveTitle(file, uri);
        // Decoded axml / shared text is applied asynchronously via loadTabContent, exactly
        // like regular files — setting editor text synchronously during onCreate gets wiped
        // by the fragment's deferred initialization. If no text arrived with the intent
        // (e.g. zip flow without rssPath decode), readTabText decodes the binary on disk.
        if (extraText != null && !extraText.isEmpty() && (isAxml || (file == null && uri == null))) {
            tab.pendingDecoded = extraText;
        }
        addTabAndOpen(tab);
    }

    private String resolveTitle(File file, Uri uri) {
        if (file != null) return file.getName();
        if (uri != null) {
            String last = uri.getLastPathSegment();
            if (last != null && !last.isEmpty()) {
                int slash = last.lastIndexOf('/');
                return slash >= 0 ? last.substring(slash + 1) : last;
            }
            return uri.toString();
        }
        return getString(android.R.string.untitled);
    }

    private EditorTab findExistingTab(File file, Uri uri) {
        for (EditorTab tab : tabs) {
            if (file != null && file.equals(tab.file)) return tab;
            if (file == null && uri != null && uri.equals(tab.fileUri) && tab.file == null) return tab;
        }
        return null;
    }

    private void loadTabContent(EditorTab tab) {
        tab.loading = true;
        new Thread(() -> {
            if (tab.pendingDecoded != null) {
                applyLoadedText(tab, tab.pendingDecoded);
                return;
            }
            boolean isFromFile = tab.file != null;
            File cachedFile = isFromFile ? new File(getCacheDir(), (tab.file.getPath()).replace(File.separator, ".")) : null;
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() != tab.file.length()) {
                final String fileText = readTabText(tab);
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this).setMessage(R.string.rest_chang).setTitle(R.string.unsaved_changes_found)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            try {
                                FileUtils.copyFile(cachedFile, tab.file);
                            } catch (Exception e) {
                                new ErrorUtil(this).showError(e);
                            }
                            new Thread(() -> applyLoadedText(tab, readTabText(tab))).start();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> applyLoadedText(tab, fileText)).show());
            } else {
                applyLoadedText(tab, readTabText(tab));
            }
        }).start();
    }

    private String readTabText(EditorTab tab) {
        // Binary axml must be decoded, not read as UTF-8 text
        if (tab.axml) {
            try (InputStream is = tab.file != null ? FileUtils.getInputStream(tab.file)
                    : getContentResolver().openInputStream(tab.fileUri)) {
                return new aXMLDecoder(is, tab.resEntries).decodeAsString();
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
                return "";
            }
        }
        boolean isFromFile = tab.file != null;
        try (InputStream is = isFromFile ? FileUtils.getInputStream(tab.file)
                : getContentResolver().openInputStream(tab.fileUri);
             InputStreamReader isr = new InputStreamReader(is, Charset.forName("UTF-8"));
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            runOnUiThread(() -> new ErrorUtil(this).showError(e));
            return "";
        }
    }

    private void applyLoadedText(EditorTab tab, String text) {
        tab.loading = false;
        runOnUiThread(() -> {
            tab.content = text == null ? "" : text;
            tab.loaded = true;
            if (getCurrentTab() == tab) {
                UnifiedEditorFragment f = getFragment();
                if (f != null) {
                    boolean wasModified = tab.modified;
                    f.setText(tab.content);
                    tab.modified = wasModified;
                }
            }
            updateTabsList();
        });
    }

    private void closeTab(final int position) {
        if (position < 0 || position >= tabs.size()) return;
        final EditorTab tab = tabs.get(position);
        if (!tab.modified) {
            removeTab(position);
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                    if (position == currentIndex) saveFile(); // editor holds the latest text
                    else saveTabText(tab, tab.content);       // content was stashed when switching away
                    removeTab(position);
                })
                .setNegativeButton(R.string.dont_save, (dialog, which) -> removeTab(position))
                .setNeutralButton(android.R.string.cancel, null)
                .setMessage(getString(R.string.confirm_save, tab.title))
                .show();
    }

    private void saveTabText(EditorTab tab, String text) {
        if (tab.fileUri == null && tab.file == null) {
            Extensions.showMessage(this, "No file to save");
            return;
        }
        try (OutputStream os = (tab.file == null
                ? getContentResolver().openOutputStream(tab.fileUri, "wt")
                : FileUtils.getOutputStream(tab.file))) {
            os.write(tab.axml ? new aXMLEncoder().encodeString(text, this, tab.resEntries) : text.getBytes(Charset.forName("UTF-8")));
            tab.content = text;
            tab.modified = false;
        } catch (Exception e) {
            tab.modified = true;
            new ErrorUtil(this).showError(e);
        }
    }

    private void removeTab(int position) {
        if (position < 0 || position >= tabs.size()) return;
        boolean closingCurrent = position == currentIndex;
        tabs.remove(position);

        if (tabs.isEmpty()) {
            currentIndex = -1;
            persistSession();
            finish();
            return;
        }

        int next = Math.min(position, tabs.size() - 1);
        if (closingCurrent) {
            selectTab(next);
        } else if (position < currentIndex) {
            currentIndex--;
        }
        updateTitleBar();
        updateTabsList();
        persistSession();
    }

    public void updateUndoRedo(boolean canUndo, boolean canRedo) {
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
    }

    @Override
    public void onContentModified(String className) {
        EditorTab t = getCurrentTab();
        if (t != null && !t.loaded) return;
        if (t != null) {
            t.modified = true;
            updateTabsList();
        }
    }

    @Override
    public void onUndoRedoChanged(boolean canUndo, boolean canRedo) {
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
    }

    @Override
    public void onSaveRequested() {
        // Mapped from the fragment's "Reload file" item: re-read the file from disk
        EditorTab t = getCurrentTab();
        if (t == null || t.axml) return;
        new Thread(() -> applyLoadedText(t, readTabText(t))).start();
    }

    @Override
    public void onCloseRequested() {
        EditorTab t = getCurrentTab();
        if (t != null && t.modified) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                    .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                        manualFinish = true;
                        saveFile();
                        currentFileUri = t.fileUri != null ? t.fileUri : Uri.fromFile(currentFile);
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
                    .setMessage(getString(R.string.confirm_save, t.title))
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
    protected void onDestroy() {
        UnifiedEditorFragment f = getFragment();
        if (f != null && f.getEditor() != null) {
            f.getEditor().release();
            EditorTab t = getCurrentTab();
            if (!manualFinish && t != null && t.modified) {
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
        EditorTab t = getCurrentTab();
        if (t != null && t.modified && f != null && f.getEditor() != null) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                    .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                        manualFinish = true;
                        saveFile();
                        currentFileUri = t.fileUri != null ? t.fileUri : Uri.fromFile(currentFile);
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
                    .setMessage(getString(R.string.confirm_save, t.title))
                    .show();
        } else super.onBackPressed();
    }

    private void saveFile() {
        UnifiedEditorFragment f = getFragment();
        if (f == null || f.getEditor() == null) return;
        EditorTab tab = getCurrentTab();
        if (tab == null) return;
        saveTabText(tab, f.getEditor().getText().toString());
        updateTabsList();
        persistSession();
    }
}
