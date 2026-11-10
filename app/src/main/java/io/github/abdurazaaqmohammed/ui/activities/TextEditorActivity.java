package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.aXMLUtils.StringPoolChunk;
import com.apk.axml.serializableItems.ResEntry;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.FilterReader;
import java.io.Reader;
import java.io.InterruptedIOException;
import android.provider.OpenableColumns;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;
import io.github.abdurazaaqmohammed.utils.AbxUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.MinimizedEditors;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

public class TextEditorActivity extends BaseActivity implements UnifiedEditorFragment.EditorCallback {

    private static class EditorTab {
        String title;
        Uri fileUri;
        File file;
        String pendingSearch;
        boolean pendingSearchRegex;
        boolean pendingSearchMatchCase;
        String rootOriginalPath;
        String zipFilePath;
        String zipEntryPath;
        boolean axml;
        boolean abx;
        List<ResEntry> resEntries;
        StringPoolChunk.Encoding poolEncoding;
        String pendingDecoded;
        String content;
        Content document;
        boolean allowLargeText;
        boolean readOnly;
        boolean lightweight;
        long fileSize;
        /** Written by loader threads, read by the UI thread. */
        volatile boolean loaded;
        volatile boolean loading;
        volatile boolean loadFailed;
        boolean modified;
        boolean pendingArchiveUpdate;
        String exportedCopyPath;
        /**
         * Bumped whenever a load is started or superseded. A background read from
         * an earlier attempt compares against it before touching the tab, so a
         * stale staged copy cannot overwrite a newer one with stale content.
         */
        volatile int loadToken;
    }

    private DrawerLayout drawerLayout;
    private FastScrollerRecyclerView tabsRecyclerView;
    private TabRowAdapter tabAdapter;
    private ImageButton btnUndo, btnRedo, btnSave, btnEdit, btnFile;

    private Uri currentFileUri;
    private File currentFile;
    private boolean axml;
    private boolean abx;
    private boolean manualFinish;
    private List<ResEntry> resEntries;

    private final List<EditorTab> tabs = new ArrayList<>();
    private int currentIndex = -1;

    private UnifiedEditorFragment editorFragment;
    private final java.util.concurrent.ExecutorService sessionWriter = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        initViews();
        setupListeners();
        initEditorFragment();
        restoreSession();
        // handleIntent() is what selects and loads the tab for whatever was
        // requested. Selecting the restored tab here as well started a read for
        // it even when the user was opening something else, which surfaced that
        // tab's failure (a gone share-menu file, a revoked permission) as an
        // error next to the file actually being opened.
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
            t.document = f.getEditor().getText();
            t.readOnly = !f.getEditor().isEditable();
        }
        persistSession();
    }

    private File sessionFile() {
        Intent intent = getIntent();
        String source = intent.getStringExtra("rootOriginalPath");
        if (source == null) source = intent.getStringExtra("path");
        if (source == null && intent.getData() != null) source = intent.getData().toString();
        return new File(getCacheDir(), source == null ? "text_editor_session.json"
                : "text_editor_session_" + Integer.toHexString(source.hashCode()) + ".json");
    }

    private void persistSession() {
        try {
            UnifiedEditorFragment f = getFragment();
            JSONArray arr = new JSONArray();
            List<JSONObject> contentRecords = new ArrayList<>();
            List<Content> snapshots = new ArrayList<>();
            int savedCurrent = 0;
            for (int i = 0; i < tabs.size(); i++) {
                EditorTab t = tabs.get(i);
                if (t.axml || t.abx) continue;
                JSONObject o = new JSONObject();
                o.put("title", t.title);
                if (t.file != null) o.put("file", t.file.getPath());
                else if (t.fileUri != null) o.put("uri", t.fileUri.toString());
                if (t.rootOriginalPath != null && !t.rootOriginalPath.isEmpty()) {
                    o.put("rootOriginal", t.rootOriginalPath);
                }
                if (t.zipFilePath != null && !t.zipFilePath.isEmpty()) {
                    o.put("zipFile", t.zipFilePath);
                }
                if (t.zipEntryPath != null && !t.zipEntryPath.isEmpty()) {
                    o.put("zipEntry", t.zipEntryPath);
                }
                o.put("modified", t.modified);
                o.put("readOnly", t.readOnly);
                o.put("lightweight", t.lightweight);
                if (t.pendingArchiveUpdate) o.put("pendingArchive", true);
                boolean untitledWithText = t.file == null && t.fileUri == null && t.loaded
                        && (t.document != null ? t.document.length() > 0 : t.content != null && !t.content.isEmpty());
                if (t.modified || untitledWithText) {
                    Content content = i == currentIndex && f != null && f.getEditor() != null
                            ? f.getEditor().getText() : t.document;
                    if (content != null) {
                        contentRecords.add(o);
                        snapshots.add(content.copyTextShallow());
                    } else o.put("content", t.content == null ? "" : t.content);
                }
                if (i == currentIndex) savedCurrent = arr.length();
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("tabs", arr);
            root.put("current", savedCurrent);
            File file = sessionFile();
            sessionWriter.execute(() -> {
                try {
                    for (int i = 0; i < snapshots.size(); i++) contentRecords.get(i).put("content", snapshots.get(i).toString());
                    File temporary = new File(file.getPath() + ".tmp");
                    try (java.io.Writer writer = new OutputStreamWriter(FileUtils.getOutputStream(temporary), StandardCharsets.UTF_8)) {
                        writer.write(root.toString());
                    }
                    if (!temporary.renameTo(file)) {
                        FileUtils.copyFile(temporary, file);
                        temporary.delete();
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) { }
    }

    private void restoreSession() {
        File file = sessionFile();
        if (!file.exists()) return;
        try (InputStream is = FileUtils.getInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("tabs");
            if ((arr == null || arr.length() == 0)) return;
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
                String savedRootOriginal = o.optString("rootOriginal", null);
                if (savedRootOriginal != null && !savedRootOriginal.isEmpty()) {
                    t.rootOriginalPath = savedRootOriginal;
                    if (t.title == null || !t.title.endsWith(" (root)")) {
                        t.title = new File(savedRootOriginal).getName() + " (root)";
                    }
                }
                String savedZipFile = o.optString("zipFile", null);
                if (savedZipFile != null && !savedZipFile.isEmpty()) t.zipFilePath = savedZipFile;
                String savedZipEntry = o.optString("zipEntry", null);
                if (savedZipEntry != null && !savedZipEntry.isEmpty()) t.zipEntryPath = savedZipEntry;
                t.modified = o.optBoolean("modified", false);
                t.readOnly = o.optBoolean("readOnly", false);
                t.lightweight = o.optBoolean("lightweight", false);
                t.pendingArchiveUpdate = o.optBoolean("pendingArchive", false);
                if (o.has("content")) {
                    t.content = o.getString("content");
                    t.loaded = true;
                } else if (t.file == null && t.fileUri == null) {
                    continue; // nothing restorable for this tab
                }
                tabs.add(t);
            }
            if (!tabs.isEmpty()) {
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

        Toolbar toolbar = findViewById(R.id.toolbar);
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

        btnSave.setOnClickListener(v -> saveButtonFlow());
        btnSave.setOnLongClickListener(v -> {
            EditorTab tab = getCurrentTab();
            if (tab == null) return true;
            UnifiedEditorFragment f = getFragment();
            String text = f != null && f.getEditor() != null ? f.getEditor().getText().toString() : tabText(tab);
            pickSaveCopyDir(tab, text != null ? text : "", true);
            return true;
        });

        btnEdit.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showEditMenu(btnEdit);
        });

        btnFile.setOnClickListener(v -> {
            UnifiedEditorFragment f = getFragment();
            if (f != null) f.showFileMenu(btnFile);
        });

        btnFile.setOnLongClickListener(v -> {
            EditorTab t = getCurrentTab();
            MinimizedEditors.minimize(this, R.string.text_editor, t != null ? t.title : null);
            return true;
        });
    }

    private class TabRowAdapter extends RecyclerView.Adapter<TabRowAdapter.TabVH> {

        private static class TabVH extends RecyclerView.ViewHolder {
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
                    ? MaterialColors.getColor(holder.title, android.R.attr.colorPrimary, Color.BLUE)
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
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(t != null ? t.title : null);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        UnifiedEditorFragment f = getFragment();

        if (currentIndex >= 0 && currentIndex < tabs.size() && currentIndex != index
                && f != null && f.getEditor() != null) {
            EditorTab previous = tabs.get(currentIndex);
            previous.document = f.getEditor().getText();
            previous.readOnly = !f.getEditor().isEditable();
        }

        currentIndex = index;
        EditorTab t = tabs.get(index);
        currentFile = t.file;
        currentFileUri = t.fileUri;
        axml = t.axml;
        abx = t.abx;
        resEntries = t.resEntries;
        if (f != null) f.setAxml(t.axml);
        String fname = t.rootOriginalPath != null ? new File(t.rootOriginalPath).getName()
                : (t.file != null ? t.file.getName() : t.title);
        if (f != null) {
            f.setLargeFileMode(t.lightweight);
            f.setFileName(fname);
        }

        if (f != null && f.getEditor() != null && t.loaded) {
            boolean wasModified = t.modified; // setText fires a change event; preserve real state
            if (t.document == null) t.document = new Content(t.content == null ? "" : t.content);
            if (f.getEditor().getText() != t.document) f.setText(t.document);
            f.getEditor().setEditable(!t.readOnly);
            t.content = null;
            t.modified = wasModified;
        } else if (f != null && f.getEditor() != null) {
            f.setText(new Content(""));
            f.getEditor().setEditable(false);
        }
        // Content is fetched when the tab is actually shown, never up front for
        // every open tab. A tab whose load failed stays retryable because
        // markLoadFailed() clears the loading flag.
        if (!t.loaded && !t.loading) loadTabContent(t);
        updateTitleBar();
        updateTabsList();
        tabsRecyclerView.scrollToPosition(index);
    }

    private void addTabAndOpen(EditorTab tab) {
        tabs.add(tab);
        // selectTab() starts the load, so this tab is read exactly once.
        selectTab(tabs.size() - 1);
        persistSession();
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        UnifiedEditorFragment f = getFragment();
        if (f == null) return;

        Uri uri = null;
        File file = null;
        boolean isAxml = false;
        boolean isAbx = false;
        List<ResEntry> entries = null;
        StringPoolChunk.Encoding axmlEncoding = null;
        String extraText = null;

        if (intent.hasExtra("abx")) {
            isAbx = true;
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
            file = new File(intent.getStringExtra("path"));
            uri = Uri.fromFile(file);
        } else if (intent.hasExtra("axml")) {
            isAxml = true;
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
            if (intent.hasExtra("poolEncoding")) {
                String enc = intent.getStringExtra("poolEncoding");
                axmlEncoding = "UTF8".equals(enc) ? StringPoolChunk.Encoding.UTF8 : StringPoolChunk.Encoding.UNICODE;
            }
            if (intent.hasExtra("resEntries")) {
                //noinspection unchecked
                entries = (List<ResEntry>) intent.getSerializableExtra("resEntries");
            } else if (intent.hasExtra("rssPath")) {
                try(InputStream is = FileUtils.getInputStream(intent.getStringExtra("rssPath")); InputStream is2 = FileUtils.getInputStream(intent.getStringExtra("path"))) {
                    ResourceTableParser rtp = new ResourceTableParser(is);
                    entries = rtp.parse();
                    aXMLDecoder decoder = new aXMLDecoder(is2, entries);
                    extraText = decoder.decodeAsString();
                    axmlEncoding = decoder.getPoolEncoding();
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
        String earlyRootOriginal = intent.getStringExtra("rootOriginalPath");
        if (existing == null && earlyRootOriginal != null && !earlyRootOriginal.isEmpty()) {
            existing = findExistingRootTab(earlyRootOriginal);
        }
        if (existing != null) {
            if (!existing.modified && file != null && existing.file != null
                    && !file.getAbsolutePath().equals(existing.file.getAbsolutePath())
                    && earlyRootOriginal != null && !earlyRootOriginal.isEmpty()
                    && earlyRootOriginal.equals(existing.rootOriginalPath)) {
                existing.file = file;
                existing.fileUri = uri;
                existing.loaded = false;
                existing.content = null;
                existing.document = null;
                existing.loadFailed = false;
                // Supersede a read still in flight against the old staged copy,
                // otherwise it lands afterwards and re-marks the tab loaded.
                existing.loadToken++;
                existing.loading = false;
            } else if (existing.loadFailed) {
                // Retryable: a failed read must not leave a blank tab behind.
                existing.loaded = false;
                existing.content = null;
            }
            String existingZipFile = intent.getStringExtra("zf");
            String existingZipEntry = intent.getStringExtra("zipEntryPath");
            if ((existing.zipFilePath == null || existing.zipFilePath.isEmpty()) && existingZipFile != null && !existingZipFile.isEmpty()) {
                existing.zipFilePath = existingZipFile;
            }
            if ((existing.zipEntryPath == null || existing.zipEntryPath.isEmpty()) && existingZipEntry != null && !existingZipEntry.isEmpty()) {
                existing.zipEntryPath = existingZipEntry;
            }
            int idx = tabs.indexOf(existing);
            selectTab(idx);
            updateTabsList();
            if (intent.hasExtra("search")) {
                existing.pendingSearch = intent.getStringExtra("search");
                existing.pendingSearchRegex = intent.getBooleanExtra("searchRegex", false);
                existing.pendingSearchMatchCase = intent.getBooleanExtra("searchMatchCase", false);
                if (existing.loaded) {
                    UnifiedEditorFragment f2 = getFragment();
                    if (f2 != null) {
                        f2.searchFor(existing.pendingSearch, existing.pendingSearchRegex, existing.pendingSearchMatchCase);
                        existing.pendingSearch = null;
                    }
                }
            }
            return;
        }

        if (file == null && uri == null && extraText == null && !tabs.isEmpty()) {
            // Bare launch with a restored session: keep the previously open tabs
            // and show the one that was active, loading only that one.
            if (currentIndex < 0 || currentIndex >= tabs.size()) currentIndex = 0;
            selectTab(currentIndex);
            return;
        }

        EditorTab tab = new EditorTab();
        tab.file = file;
        tab.fileUri = uri;
        tab.axml = isAxml;
        tab.abx = isAbx;
        tab.resEntries = entries;
        tab.poolEncoding = axmlEncoding;
        String zipFileExtra = intent.getStringExtra("zf");
        String zipEntryExtra = intent.getStringExtra("zipEntryPath");
        if (zipFileExtra != null && !zipFileExtra.isEmpty()) tab.zipFilePath = zipFileExtra;
        if (zipEntryExtra != null && !zipEntryExtra.isEmpty()) tab.zipEntryPath = zipEntryExtra;
        if (intent.hasExtra("search")) {
            tab.pendingSearch = intent.getStringExtra("search");
            tab.pendingSearchRegex = intent.getBooleanExtra("searchRegex", false);
            tab.pendingSearchMatchCase = intent.getBooleanExtra("searchMatchCase", false);
        }
        String rootOriginal = intent.getStringExtra("rootOriginalPath");
        if (rootOriginal != null && !rootOriginal.isEmpty() && file != null
                && !rootOriginal.equals(file.getAbsolutePath())) {
            tab.rootOriginalPath = rootOriginal;
        }
        tab.title = tab.rootOriginalPath != null
                ? new File(tab.rootOriginalPath).getName() + " (root)"
                : resolveTitle(file, uri);
        // Decoded axml / shared text is applied asynchronously via loadTabContent, exactly
        // like regular files — setting editor text synchronously during onCreate gets wiped
        // by the fragment's deferred initialization. If no text arrived with the intent
        // (e.g. zip flow without rssPath decode), readTabText decodes the binary on disk.
        if (extraText != null && !extraText.isEmpty() && (isAxml || isAbx || (file == null && uri == null))) {
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

    private EditorTab findExistingRootTab(String rootOriginalPath) {
        if (rootOriginalPath == null || rootOriginalPath.isEmpty()) return null;
        for (EditorTab tab : tabs) {
            if (rootOriginalPath.equals(tab.rootOriginalPath)) return tab;
        }
        return null;
    }

    private void loadTabContent(EditorTab tab) {
        if (tab == null) return;
        tab.loading = true;
        tab.loadFailed = false;
        final int token = ++tab.loadToken;
        new Thread(() -> {
            try {
                if (tab.pendingDecoded == null && (tab.file != null || tab.fileUri != null) && !ensureReadableFile(tab)) {
                    markLoadFailed(tab, null, token);
                    return;
                }
                tab.fileSize = tabSize(tab);
                if (!tab.allowLargeText && tab.fileSize > MAX_TEXT_OPEN_BYTES) {
                    runOnUiThread(() -> offerLargeFileWarning(tab, token));
                    return;
                }
                tab.readOnly = tab.fileSize > 2L * 1024 * 1024;
                tab.lightweight = tab.fileSize > 512L * 1024;
                if (tab.pendingDecoded != null) {
                    applyLoadedText(tab, tab.pendingDecoded, token);
                    return;
                }
                File cachedFile = tab.file != null
                        ? new File(getCacheDir(), tab.file.getPath().replace(File.separator, ".")) : null;
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() != tab.file.length()) {
                    final Content fileText = readTabText(tab, token);
                    if (fileText == null) {
                        markLoadFailed(tab, null, token);
                        return;
                    }
                    runOnUiThread(() -> {
                        if (tab.loadToken != token) return;
                        new MaterialAlertDialogBuilder(this).setMessage(R.string.rest_chang).setTitle(R.string.unsaved_changes_found)
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                    try {
                                        FileUtils.copyFile(cachedFile, tab.file);
                                    } catch (Exception e) {
                                        new ErrorUtil(this).showError(e);
                                    }
                                    new Thread(() -> {
                                        Content text = readTabText(tab, token);
                                        if (text == null) markLoadFailed(tab, null, token);
                                        else applyLoadedText(tab, text, token);
                                    }).start();
                                })
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> applyLoadedText(tab, fileText, token)).show();
                    });
                } else {
                    Content text = readTabText(tab, token);
                    if (text == null) {
                        markLoadFailed(tab, null, token);
                        return;
                    }
                    // An empty read of a file the backend says is non-empty means
                    // the staged copy was stale or truncated. Take a fresh one.
                    if (text.length() == 0 && tab.rootOriginalPath != null && originalSize(tab) > 0) {
                        boolean restaged = restage(tab);
                        if (restaged) {
                            Content retry = readTabText(tab, token);
                            if (retry != null) text = retry;
                        }
                    }
                    applyLoadedText(tab, text, token);
                }
            } catch (Throwable t) {
                markLoadFailed(tab, t, token);
            }
        }).start();
    }

    /**
     * Makes sure {@code tab.file} still points at something readable.
     *
     * <p>Root and Shizuku tabs read from a disposable staged copy in the app
     * cache. That copy can be gone by the time the tab is opened again (the
     * session is restored from disk pointing at the previous run's path), and
     * reusing it produced a blank editor.
     */
    /** Warning threshold, not a hard limit. */
    private static final long MAX_TEXT_OPEN_BYTES = 10L * 1024 * 1024;

    private long tabSize(EditorTab tab) {
        if (tab.pendingDecoded != null) return (long) tab.pendingDecoded.length() * 2;
        if (tab.file != null) return tab.file.length();
        if (tab.fileUri == null) return 0;
        try {
            if ("file".equals(tab.fileUri.getScheme())) return new File(tab.fileUri.getPath()).length();
            try (android.database.Cursor cursor = getContentResolver().query(tab.fileUri,
                    new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void offerLargeFileWarning(EditorTab tab, int token) {
        if (isFinishing() || isDestroyed() || tab.loadToken != token || !tabs.contains(tab)) return;
        String path = tab.file != null ? tab.file.getAbsolutePath() : null;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.editor_large_file_title)
                .setMessage(getString(R.string.editor_large_file_warning,
                        io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(tab.fileSize)))
                .setPositiveButton(R.string.editor_open_text_anyway, (d, w) -> {
                    tab.allowLargeText = true;
                    tab.loading = false;
                    loadTabContent(tab);
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (path != null) builder.setNeutralButton(R.string.hex_editor, (d, w) -> {
                    if (path == null) return;
                    Intent i = new Intent(this, HexEditorActivity.class)
                            .putExtra("path", path);
                    if (tab.rootOriginalPath != null) {
                        i.putExtra("rootOriginalPath", tab.rootOriginalPath);
                    }
                    if (tab.zipFilePath != null) i.putExtra("zf", tab.zipFilePath);
                    if (tab.zipEntryPath != null) i.putExtra("zipEntryPath", tab.zipEntryPath);
                    startActivity(i);
                });
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> { if (tab.loadToken == token) tab.loading = false; });
        dialog.show();
    }

    private boolean ensureReadableFile(EditorTab tab) {
        boolean stagedCopyGone = tab.file == null || !tab.file.isFile();
        if (!stagedCopyGone && tab.rootOriginalPath != null && tab.file.length() == 0
                && originalSize(tab) > 0) {
            stagedCopyGone = true;
        }
        if (stagedCopyGone && tab.rootOriginalPath != null) {
            return restage(tab);
        }
        if (tab.file != null) return tab.file.isFile() && tab.file.canRead();
        return tab.fileUri != null;
    }

    private boolean restage(EditorTab tab) {
        try {
            File staged = RootStaging.stageForRead(TextEditorActivity.this, tab.rootOriginalPath);
            if (staged == null || !staged.isFile()) return false;
            tab.file = staged;
            tab.fileUri = Uri.fromFile(staged);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private long originalSize(EditorTab tab) {
        try {
            return AccessManager.getSize(this, tab.rootOriginalPath);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * @return the tab's text, or null when it could not be read. A null result
     *         never becomes an empty-but-loaded tab, which is what left the
     *         editor blank until the tab was closed and recreated.
     */
    private Content readTabText(EditorTab tab, int token) {
        if (tab.file == null && tab.fileUri == null) return new Content("");
        // Binary axml must be decoded, not read as UTF-8 text
        if (tab.axml) {
            try (InputStream is = tab.file != null ? FileUtils.getInputStream(tab.file)
                    : getContentResolver().openInputStream(tab.fileUri)) {
                aXMLDecoder decoder = new aXMLDecoder(is, tab.resEntries);
                String text = decoder.decodeAsString();
                tab.poolEncoding = decoder.getPoolEncoding();
                return new Content(text);
            } catch (Exception e) {
                return null;
            }
        }
        boolean isFromFile = tab.file != null;
        try (InputStream raw = isFromFile ? FileUtils.getInputStream(tab.file) : getContentResolver().openInputStream(tab.fileUri);
             BufferedInputStream bis = raw instanceof BufferedInputStream
                     ? (BufferedInputStream) raw : new BufferedInputStream(raw)) {
            if (!tab.abx && AbxUtil.isAbxStream(bis)) {
                tab.abx = true;
                if (getCurrentTab() == tab) abx = true;
            }
            if (tab.abx) {
                try {
                    return new Content(AbxUtil.decodeToString(bis));
                } catch (Exception e) {
                    return null;
                }
            }
            if (!tab.axml && FileUtils.isAxml(bis)) {
                tab.axml = true;
                if (getCurrentTab() == tab) axml = true;
                try {
                    aXMLDecoder decoder = new aXMLDecoder(bis, tab.resEntries);
                    String text = decoder.decodeAsString();
                    tab.poolEncoding = decoder.getPoolEncoding();
                    return new Content(text);
                } catch (Exception e) {
                    return null;
                }
            }
            try (Reader reader = new FilterReader(new InputStreamReader(bis, StandardCharsets.UTF_8)) {
                @Override public int read(char[] buffer, int offset, int count) throws IOException {
                    if (tab.loadToken != token || isDestroyed()) throw new InterruptedIOException();
                    return super.read(buffer, offset, count);
                }
            }) {
                // Sora's streaming reader preserves CRLF/CR/LF and the final newline,
                // builds the line model off-thread, and avoids a second full-file String.
                return ContentIO.createFrom(reader);
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Records a failed load without marking the tab loaded, so selecting it again
     * retries instead of showing an editor permanently stuck on empty text. The
     * message is only surfaced when the failing tab is the one on screen.
     */
    private void markLoadFailed(EditorTab tab, Throwable error, int token) {
        runOnUiThread(() -> {
            if (tab.loadToken != token || !tabs.contains(tab) || isDestroyed()) return;
            tab.loading = false;
            tab.loadFailed = true;
            tab.loaded = false;
            tab.content = null;
            tab.document = null;
            updateTabsList();
            if (getCurrentTab() != tab) return;
            String message = error == null || error.getMessage() == null
                    ? getString(R.string.cannot_open_item) : error.getMessage();
            Extensions.showMessage(this, message);
        });
    }

    private void applyLoadedText(EditorTab tab, CharSequence text, int token) {
        Content document = text instanceof Content ? (Content) text : new Content(text == null ? "" : text);
        runOnUiThread(() -> {
            if (tab.loadToken != token || !tabs.contains(tab) || isFinishing() || isDestroyed()) return;
            tab.loading = false;
            tab.loadFailed = false;
            tab.loaded = true;
            tab.document = document;
            tab.content = null;
            tab.pendingDecoded = null;
            tab.lightweight |= document.length() > 512 * 1024;
            tab.readOnly |= document.length() > 2 * 1024 * 1024;
            for (int line = 0; line < document.getLineCount() && !tab.lightweight; line++) {
                if (document.getColumnCount(line) > 20000) tab.lightweight = true;
            }
            if (getCurrentTab() == tab) {
                UnifiedEditorFragment f = getFragment();
                if (f != null) {
                    boolean wasModified = tab.modified;
                    f.setLargeFileMode(tab.lightweight);
                    f.setText(document);
                    f.getEditor().setEditable(!tab.readOnly);
                    document.setMaxUndoStackSize(tab.lightweight ? 50 : Content.DEFAULT_MAX_UNDO_STACK_SIZE);
                    tab.modified = wasModified;
                    String fname = tab.rootOriginalPath != null ? new File(tab.rootOriginalPath).getName()
                            : (tab.file != null ? tab.file.getName() : tab.title);
                    f.setFileName(fname);
                    if (tab.pendingSearch != null && !tab.pendingSearch.isEmpty()) {
                        f.searchFor(tab.pendingSearch, tab.pendingSearchRegex, tab.pendingSearchMatchCase);
                        tab.pendingSearch = null;
                    }
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
        if (tab.exportedCopyPath != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.changes_made)
                    .setMessage(R.string.confirm_save_exported_or_original)
                    .setPositiveButton(R.string.save_to_original, (dialog, which) -> {
                        if (position == currentIndex) saveFile(() -> removeTabRef(tab));
                        else saveTabText(tab, tabText(tab), () -> removeTabRef(tab));
                    })
                    .setNegativeButton(R.string.keep_exported_copy_only, (dialog, which) -> removeTab(position))
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                    if (position == currentIndex) saveFile(() -> removeTabRef(tab)); // editor holds the latest text
                    else saveTabText(tab, tabText(tab), () -> removeTabRef(tab));
                })
                .setNegativeButton(R.string.dont_save, (dialog, which) -> removeTab(position))
                .setNeutralButton(android.R.string.cancel, null)
                .setMessage(getString(R.string.confirm_save, tab.title))
                .show();
    }

    private void saveTabText(EditorTab tab, String text) {
        saveTabText(tab, text, null);
    }

    private void saveTabText(EditorTab tab, String text, Runnable onDone) {
        if (tab.fileUri == null && tab.file == null) {
            Extensions.showMessage(this, getString(R.string.editor_no_file));
            if (onDone != null) onDone.run();
            return;
        }
        // Root-staged tab: confirm before touching key system paths, then
        // write the staged copy locally and push bytes back via su.
        if (tab.file != null && tab.rootOriginalPath != null) {
            if (RootStaging.needsWriteConfirm(tab.rootOriginalPath)) {
                String target = tab.rootOriginalPath;
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.editor_write_system))
                        .setMessage(getString(R.string.editor_write_system_msg, target))
                        .setPositiveButton(android.R.string.ok, (d, w) -> saveTabTextRoot(tab, text, onDone))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
            saveTabTextRoot(tab, text, onDone);
            return;
        }
        // Plain .xml that looks like Android XML: offer to compile as AXML on save.
        if (!tab.axml && !tab.abx && isXmlFile(tab) && FileUtils.looksLikeAxmlText(text)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.compile_axml_title))
                    .setMessage(getString(R.string.compile_axml_msg))
                    .setPositiveButton(getString(R.string.save_axml), (d, w) -> {
                        tab.axml = true;
                        if (getCurrentTab() == tab) {
                            axml = true;
                            UnifiedEditorFragment f = getFragment();
                            if (f != null) f.setAxml(true);
                        }
                        saveTabText(tab, text, onDone);
                    })
                    .setNegativeButton(getString(R.string.save_plain), (d, w) -> writeTabText(tab, text, onDone))
                    .setNeutralButton(android.R.string.cancel, (d, w) -> {
                        if (onDone != null) { /* keep modified flag, don't close */ }
                    })
                    .show();
            return;
        }
        writeTabText(tab, text, onDone);
    }

    private void writeTabText(EditorTab tab, String text, Runnable onDone) {
        // Compiling a multi-megabyte manifest into binary XML (and the backup
        // copy that precedes it) takes seconds; doing it inline froze the UI and
        // could take the process down with it on very large files.
        if (requiresBackgroundWrite(tab, text)) {
            runWriteAsync(tab, text, onDone);
            return;
        }
        doWriteTabText(tab, text, onDone);
    }

    private boolean requiresBackgroundWrite(EditorTab tab, String text) {
        return text == null || text.length() > BACKGROUND_WRITE_CHARS
                || tab.axml || tab.abx;
    }

    private static final int BACKGROUND_WRITE_CHARS = 64 * 1024;

    private void runWriteAsync(EditorTab tab, String text, Runnable onDone) {
        if (isFinishing() || isDestroyed()) return;
        ProgressManager pm = new ProgressManager(this, true);
        pm.setText(getString(R.string.saving));
        pm.show();
        new Thread(() -> {
            try {
                doWriteTabText(tab, text, onDone);
            } finally {
                runOnUiThread(pm::dismiss);
            }
        }).start();
    }

    private void doWriteTabText(EditorTab tab, String text, Runnable onDone) {
        try {
            backupForSave(tab.file, null);
            try (OutputStream os = (tab.file == null
                    ? getContentResolver().openOutputStream(tab.fileUri, "wt")
                    : FileUtils.getOutputStream(tab.file))) {
                writeTabPayload(tab, text, os);
            }
            tab.content = text;
            tab.modified = false;
            tab.exportedCopyPath = null;
            runOnUiThread(() -> {
                Extensions.showMessage(this, getString(R.string.saved));
                if (onDone != null) onDone.run();
            });
        } catch (Exception e) {
            tab.modified = true;
            if (handleSaveFailureElevated(tab, text, onDone, e)) return;
            runOnUiThread(() -> showSaveFallbackDialog(tab, text, onDone, e));
        }
    }

    /**
     * The direct write failed (read-only grant from a sharing app, or a
     * storage volume that denies writes). When an elevated backend is active
     * and the tab maps to a real path, retry the write through it before
     * showing the fallback dialog.
     *
     * @return true when the elevated write succeeded
     */
    private boolean handleSaveFailureElevated(EditorTab tab, String text, Runnable onDone, Exception cause) {
        if (tab.file == null || !AccessManager.fileOpsOn(this)) return false;
        try {
            File tmp = new File(getCacheDir(), "editor_save_" + System.currentTimeMillis() + ".tmp");
            try (OutputStream os = FileUtils.getOutputStream(tmp)) {
                writeTabPayload(tab, text, os);
            }
            try {
                AccessManager.copyFile(this, tmp.getPath(), tab.file.getAbsolutePath(), true);
                if (!AccessManager.exists(this, tab.file.getAbsolutePath())) throw new IOException("copy failed");
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            tab.content = text;
            tab.modified = false;
            tab.exportedCopyPath = null;
            runOnUiThread(() -> {
                Extensions.showMessage(this, getString(R.string.saved_elevated));
                if (onDone != null) onDone.run();
            });
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Explains why a direct save is impossible and offers ways out: save a
     * copy anywhere via the folder picker, or an elevated write when root or
     * Shizuku can reach the location (Shizuku only for app-data areas, per
     * its backend rules).
     */
    private void showSaveFallbackDialog(EditorTab tab, String text, Runnable onDone, Exception cause) {
        if (isFinishing() || isDestroyed()) return;
        boolean readOnlyProvider = tab.file == null && tab.fileUri != null;
        String reason = readOnlyProvider
                ? getString(R.string.save_read_only_reason)
                : getString(R.string.save_denied_reason);
        String detail = cause == null ? "" : cause.toString() + "\n\n";

        boolean rootAvailable = false;
        try {
            rootAvailable = RootManager.getInstance(this).isRootAvailable() && tab.file != null;
        } catch (Exception ignored) {
        }
        boolean shizukuUsable = ShizukuShell.isGranted()
                && tab.file != null
                && ShizukuFile.isAndroidDataPath(tab.file);

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_denied_title)
                .setMessage(detail + reason)
                .setPositiveButton(R.string.save_copy, (d, w) -> pickSaveCopyDir(tab, text))
                .setNegativeButton(android.R.string.cancel, null);
        if (rootAvailable || shizukuUsable) {
            b.setNeutralButton(R.string.save_elevated, (d, w) -> {
                if (!handleSaveFailureElevated(tab, text, onDone, cause)) {
                    new ErrorUtil(this).showError(cause);
                }
            });
        }
        b.show();
    }

    /** "Save a copy": pick a destination folder and write the buffer there. */
    private void pickSaveCopyDir(EditorTab tab, String text) {
        pickSaveCopyDir(tab, text, false);
    }

    private void pickSaveCopyDir(EditorTab tab, String text, boolean isExportCopy) {
        FilePickerDialog.Properties props =
                new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        props.root = Environment.getExternalStorageDirectory();
        FilePickerDialog picker =
                new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.save_copy));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            File targetDir = new File(files[0]);
            String initialName = tab.file != null ? tab.file.getName()
                    : (tab.title != null ? tab.title : "untitled.txt");
            if (initialName.endsWith(" (root)")) {
                initialName = initialName.substring(0, initialName.length() - 7);
            }

            final String defaultName = initialName;
            final EditText input = new EditText(this);
            input.setText(defaultName);
            input.setSelection(defaultName.length());

            int pad = (int) (18 * getResources().getDisplayMetrics().density);
            FrameLayout container = new FrameLayout(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = pad;
            lp.rightMargin = pad;
            input.setLayoutParams(lp);
            container.addView(input);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.save_copy)
                    .setMessage(targetDir.getAbsolutePath())
                    .setView(container)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String chosenName = input.getText().toString().trim();
                        if (chosenName.isEmpty()) chosenName = defaultName;
                        final String finalName = chosenName;
                        new Thread(() -> {
                            try {
                                File dest = FileUtils.getUnusedFile(new File(targetDir, finalName));
                                try (OutputStream os = FileUtils.getOutputStream(dest)) {
                                    writeTabPayload(tab, text, os);
                                }
                                tab.content = text;
                                if (isExportCopy) {
                                    tab.exportedCopyPath = dest.getAbsolutePath();
                                    tab.modified = true;
                                } else {
                                    tab.modified = false;
                                }
                                runOnUiThread(() -> {
                                    updateTabsList();
                                    Extensions.showMessage(this,
                                            getString(R.string.saved_to_x, dest.getAbsolutePath()));
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> new ErrorUtil(this).showError(e));
                            }
                        }).start();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        picker.show();
    }

    private byte[] encodeTabPayload(EditorTab tab, String text) throws Exception {
        if (tab.axml) return new aXMLEncoder().encodeString(text, this, tab.resEntries, tab.poolEncoding);
        if (tab.abx) return AbxUtil.encodeString(text);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String tabText(EditorTab tab) {
        return tab.document != null ? tab.document.toString() : (tab.content == null ? "" : tab.content);
    }

    private void writeTabPayload(EditorTab tab, String text, OutputStream output) throws Exception {
        if (tab.axml || tab.abx) output.write(encodeTabPayload(tab, text));
        else {
            OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            writer.write(text);
            writer.flush();
        }
    }

    private void saveTabTextRoot(EditorTab tab, String text) {
        saveTabTextRoot(tab, text, null);
    }

    private void saveTabTextRoot(EditorTab tab, String text, Runnable onDone) {
        if (requiresBackgroundWrite(tab, text)) {
            if (isFinishing() || isDestroyed()) return;
            ProgressManager pm = new ProgressManager(this, true);
            pm.setText(getString(R.string.saving));
            pm.show();
            new Thread(() -> {
                try {
                    saveTabTextRootInline(tab, text, onDone);
                } finally {
                    runOnUiThread(pm::dismiss);
                }
            }).start();
            return;
        }
        saveTabTextRootInline(tab, text, onDone);
    }

    private void saveTabTextRootInline(EditorTab tab, String text, Runnable onDone) {
        try {
            backupForSave(tab.file, tab.rootOriginalPath);
            try (OutputStream os = FileUtils.getOutputStream(tab.file)) {
                writeTabPayload(tab, text, os);
            }
        } catch (Exception e) {
            tab.modified = true;
            runOnUiThread(() -> new ErrorUtil(this).showError(e));
            return;
        }
        tab.content = text;
        runOnUiThread(() -> Extensions.showMessage(this, getString(R.string.editor_writing_root)));
        new Thread(() -> {
            try {
                if (text.isEmpty() && originalKnownNonEmpty(tab)) {
                    String target = tab.rootOriginalPath;
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.editor_overwrite_empty))
                            .setMessage(getString(R.string.editor_overwrite_empty_msg, target))
                            .setPositiveButton(getString(R.string.editor_overwrite), (d, w) -> new Thread(() -> doRootWriteBack(tab, text, onDone)).start())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show());
                    return;
                }
                doRootWriteBack(tab, text, onDone);
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    private boolean originalKnownNonEmpty(EditorTab tab) {
        try {
            if (tab.rootOriginalPath == null) {
                File f = tab.file;
                return f != null && f.isFile() && f.length() > 0;
            }
            return AccessManager.getSize(this, tab.rootOriginalPath) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void doRootWriteBack(EditorTab tab, String text) {
        doRootWriteBack(tab, text, null);
    }

    private void doRootWriteBack(EditorTab tab, String text, Runnable onDone) {
        try {
            RootStaging.writeBack(this, tab.file, tab.rootOriginalPath);
            runOnUiThread(() -> {
                tab.modified = false;
                tab.exportedCopyPath = null;
                updateTabsList();
                persistSession();
                Extensions.showMessage(this, getString(R.string.editor_saved_root));
                if (onDone != null) onDone.run();
            });
        } catch (Exception e) {
            runOnUiThread(() -> new ErrorUtil(this).showError(e));
        }
    }

    private void removeTabRef(EditorTab tab) {
        int idx = tabs.indexOf(tab);
        if (idx >= 0) removeTab(idx);
    }

    private void removeTab(int position) {
        if (position < 0 || position >= tabs.size()) return;
        boolean closingCurrent = position == currentIndex;
        EditorTab removed = tabs.remove(position);
        removed.loadToken++;
        removed.document = null;

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
    public boolean isAxmlMode() {
        EditorTab t = getCurrentTab();
        return t != null && t.axml;
    }

    @Override
    public void onToggleAxmlMode() {
        EditorTab t = getCurrentTab();
        if (t == null) return;
        t.axml = !t.axml;
        UnifiedEditorFragment f = getFragment();
        if (f != null) f.setAxml(t.axml);
        axml = t.axml;
        Extensions.showMessage(this, t.axml ? getString(R.string.save_as_axml) : getString(R.string.save_as_plain_xml));
    }

    private boolean isXmlFile(EditorTab tab) {
        try {
            String name = null;
            if (tab.file != null) name = tab.file.getName();
            else if (tab.title != null) name = tab.title;
            if (name == null && currentFile != null) name = currentFile.getName();
            return name != null && name.toLowerCase().endsWith(".xml");
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public void onSaveRequested() {
        // Mapped from the fragment's "Reload file" item: re-read the file from disk
        EditorTab t = getCurrentTab();
        if (t == null || t.axml) return;
        t.loaded = false;
        t.content = null;
        t.document = null;
        t.loading = false;
        loadTabContent(t);
    }

    @Override
    public void onCloseRequested() {
        EditorTab t = getCurrentTab();
        if (t != null && t.modified) {
            if (t.exportedCopyPath != null) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.changes_made)
                        .setMessage(R.string.confirm_save_exported_or_original)
                        .setPositiveButton(R.string.save_to_original, (dialog, which) -> {
                            saveFile(() -> {
                                manualFinish = true;
                                currentFileUri = t.fileUri != null ? t.fileUri : Uri.fromFile(currentFile);
                                Intent resultIntent = new Intent();
                                resultIntent.setData(currentFileUri);
                                resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                if (t.zipFilePath != null) resultIntent.putExtra("zipFilePath", t.zipFilePath);
                                if (t.zipEntryPath != null) resultIntent.putExtra("zipEntryPath", t.zipEntryPath);
                                setEditorResult(757, resultIntent);
                                finish();
                            });
                        })
                        .setNegativeButton(R.string.keep_exported_copy_only, (dialog, which) -> {
                            manualFinish = true;
                            finish();
                        })
                        .setNeutralButton(R.string.minimize, (dialog, which) ->
                                MinimizedEditors.minimize(this, R.string.text_editor, t.title))
                        .show();
                return;
            }
            new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                    .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                        saveFile(() -> {
                            manualFinish = true;
                            currentFileUri = t.fileUri != null ? t.fileUri : Uri.fromFile(currentFile);
                            Intent resultIntent = new Intent();
                            resultIntent.setData(currentFileUri);
                            resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            if (t.zipFilePath != null) resultIntent.putExtra("zipFilePath", t.zipFilePath);
                            if (t.zipEntryPath != null) resultIntent.putExtra("zipEntryPath", t.zipEntryPath);
                            setEditorResult(757, resultIntent);
                            finish();
                        });
                    })
                    .setNegativeButton(R.string.dont_save, (dialog, which) -> {
                        manualFinish = true;
                        finish();
                    })
                    .setNeutralButton(R.string.minimize, (dialog, which) ->
                            MinimizedEditors.minimize(this, R.string.text_editor, t.title))
                    .setMessage(getString(R.string.confirm_save, t.title))
                    .show();
        } else if (!consumePendingArchiveUpdate(t)) {
            finish();
        }
    }

    private boolean consumePendingArchiveUpdate(EditorTab t) {
        try {
            if (t == null || !t.pendingArchiveUpdate) return false;
            t.pendingArchiveUpdate = false;
            if (t.zipFilePath == null || t.zipFilePath.isEmpty()
                    || t.file == null || !t.file.isFile()
                    || !new File(t.zipFilePath).isFile()) {
                persistSession();
                return false;
            }
            persistSession();
            finishWithArchiveResult(t);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onPreferencesRequested() {
        startActivity(new Intent(this, EditorSettingsActivity.class));
    }

    @Override
    protected void onDestroy() {
        sessionWriter.shutdown();
        UnifiedEditorFragment f = getFragment();
        if (f != null && f.getEditor() != null) {
            EditorTab t = getCurrentTab();
            if (!manualFinish && t != null && t.modified && t.exportedCopyPath == null) {
                boolean isFromFile = currentFile != null;
                if (isFromFile) try (OutputStream os = FileUtils.getOutputStream(
                        new File(getCacheDir(), currentFile.getPath().replace(File.separator, ".")))) {
                    ContentIO.writeTo(f.getEditor().getText(), os, StandardCharsets.UTF_8, false);
                } catch (Exception ignored) { }
            }
            f.getEditor().release();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        UnifiedEditorFragment f = getFragment();
        EditorTab t = getCurrentTab();
        if (t != null && t.modified && f != null && f.getEditor() != null) {
            if (t.exportedCopyPath != null) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.changes_made)
                        .setMessage(R.string.confirm_save_exported_or_original)
                        .setPositiveButton(R.string.save_to_original, (dialog, which) -> {
                            saveFile(() -> {
                                manualFinish = true;
                                currentFileUri = t.fileUri != null ? t.fileUri : Uri.fromFile(currentFile);
                                Intent resultIntent = new Intent();
                                resultIntent.setData(currentFileUri);
                                resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                if (t.zipFilePath != null) resultIntent.putExtra("zipFilePath", t.zipFilePath);
                                if (t.zipEntryPath != null) resultIntent.putExtra("zipEntryPath", t.zipEntryPath);
                                setEditorResult(757, resultIntent);
                                finish();
                            });
                        })
                        .setNegativeButton(R.string.keep_exported_copy_only, (dialog, which) -> {
                            manualFinish = true;
                            finish();
                        })
                        .setNeutralButton(R.string.minimize, (dialog, which) ->
                                MinimizedEditors.minimize(this, R.string.text_editor, t.title))
                        .show();
                return;
            }
            new MaterialAlertDialogBuilder(this).setTitle(R.string.changes_made)
                    .setPositiveButton(R.string.save_and_exit, (dialog, which) -> {
                        saveFile(() -> {
                            manualFinish = true;
                            currentFileUri = t.fileUri != null ? t.fileUri : Uri.fromFile(currentFile);
                            Intent resultIntent = new Intent();
                            resultIntent.setData(currentFileUri);
                            resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            if (t.zipFilePath != null) resultIntent.putExtra("zipFilePath", t.zipFilePath);
                            if (t.zipEntryPath != null) resultIntent.putExtra("zipEntryPath", t.zipEntryPath);
                            setEditorResult(757, resultIntent);
                            finish();
                        });
                    })
                    .setNegativeButton(R.string.dont_save, (dialog, which) -> {
                        manualFinish = true;
                        finish();
                    })
                    .setNeutralButton(R.string.minimize, (dialog, which) ->
                            MinimizedEditors.minimize(this, R.string.text_editor, t.title))
                    .setMessage(getString(R.string.confirm_save, t.title))
                    .show();
        } else if (!consumePendingArchiveUpdate(t)) super.onBackPressed();
    }

    private void backupForSave(File directFile, String rootOriginal) {
        try {
            if (!UiPrefs.genBackup(this)) return;
            if (rootOriginal != null) {
                if (AccessManager.fileOpsOn(this) && AccessManager.exists(this, rootOriginal)) {
                    File bak = new File(rootOriginal + ".bak");
                    if (!bak.exists() && !AccessManager.exists(this, rootOriginal + ".bak")) {
                        AccessManager.copyFile(this, rootOriginal, rootOriginal + ".bak", true);
                        AccessManager.preserveTime(this, rootOriginal, rootOriginal + ".bak");
                    }
                }
            } else if (directFile != null && directFile.isFile()) {
                FileUtils.backupOnce(directFile);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveFile() {
        UnifiedEditorFragment f = getFragment();
        if (f == null || f.getEditor() == null) return;
        EditorTab tab = getCurrentTab();
        if (tab == null) return;
        saveFile(null);
    }

    private void saveButtonFlow() {
        UnifiedEditorFragment f = getFragment();
        EditorTab tab = getCurrentTab();
        if (f == null || f.getEditor() == null || tab == null) return;
        saveFile(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                if (tab.zipFilePath == null || tab.zipFilePath.isEmpty()) return;
                if (tab.file == null || !tab.file.isFile()) return;
                if (!new File(tab.zipFilePath).isFile()) return;
                tab.pendingArchiveUpdate = true;
                persistSession();
            } catch (Exception ignored) {}
        });
    }

    private void finishWithArchiveResult(EditorTab tab) {
        if (tab == null) return;
        manualFinish = true;
        Uri dataUri = tab.fileUri != null ? tab.fileUri
                : (tab.file != null ? Uri.fromFile(tab.file)
                : (currentFile != null ? Uri.fromFile(currentFile) : null));
        Intent resultIntent = new Intent();
        if (dataUri != null) {
            resultIntent.setData(dataUri);
            resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        if (tab.zipFilePath != null) resultIntent.putExtra("zipFilePath", tab.zipFilePath);
        if (tab.zipEntryPath != null) resultIntent.putExtra("zipEntryPath", tab.zipEntryPath);
        setEditorResult(757, resultIntent);
        finish();
    }

    private void saveFile(Runnable onDone) {
        UnifiedEditorFragment f = getFragment();
        if (f == null || f.getEditor() == null) {
            if (onDone != null) onDone.run();
            return;
        }
        EditorTab tab = getCurrentTab();
        if (tab == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Content document = f.getEditor().getText();
        if (document.length() > BACKGROUND_WRITE_CHARS && !tab.axml && !tab.abx
                && !isXmlFile(tab) && tab.rootOriginalPath == null) {
            savePlainDocument(tab, document, onDone);
        } else saveTabText(tab, document.toString(), onDone);
        updateTabsList();
        persistSession();
    }

    /** Shallow COW snapshot + streaming UTF-8 output: no full-file String or byte array. */
    private void savePlainDocument(EditorTab tab, Content document, Runnable onDone) {
        if (tab.file == null && tab.fileUri == null) { Extensions.showMessage(this, R.string.editor_no_file); return; }
        Content snapshot = document.copyTextShallow();
        long version = document.getDocumentVersion();
        ProgressManager progress = new ProgressManager(this, false).show();
        progress.setText(getString(R.string.saving));
        new Thread(() -> {
            try {
                backupForSave(tab.file, null);
                try (OutputStream output = tab.file != null ? FileUtils.getOutputStream(tab.file)
                        : getContentResolver().openOutputStream(tab.fileUri, "wt")) {
                    ContentIO.writeTo(snapshot, output, StandardCharsets.UTF_8, false);
                }
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (isDestroyed()) return;
                    tab.modified = document.getDocumentVersion() != version;
                    tab.exportedCopyPath = null;
                    updateTabsList();
                    persistSession();
                    Extensions.showMessage(this, R.string.saved);
                    if (onDone != null && !tab.modified) onDone.run();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (!isDestroyed()) showSaveFallbackDialog(tab, snapshot.toString(), onDone, error);
                });
            }
        }, "text-save").start();
    }
}
