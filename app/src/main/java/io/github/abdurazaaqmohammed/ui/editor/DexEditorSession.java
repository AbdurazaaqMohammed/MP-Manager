package io.github.abdurazaaqmohammed.ui.editor;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.ViewModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.data.DexWorkspaceStore;
import modder.hub.dexeditor.utils.ClassTree;

/** Keeps the DEX tree and its files alive across configuration changes and minimized tasks. */
public class DexEditorSession extends ViewModel {
    private static final String WORKSPACE_EXTRA = "dexEditorWorkspace";
    private final List<DexWorkspaceStore.Lease> inputs = new ArrayList<>();
    private DexWorkspaceStore.Lease workspace;
    private List<String> paths;
    private final Object loadLock = new Object();
    private volatile ClassTree tree;
    private volatile boolean closed;
    private volatile boolean finishedCleanly;

    public synchronized void initialize(Context context, Intent intent, Bundle state) throws IOException {
        if (workspace != null) return;
        paths = intent.getStringArrayListExtra("SelectedDexFiles");
        DexWorkspaceStore.initialize(context.getFilesDir());
        if (paths == null || paths.isEmpty()) throw new IOException("No DEX files provided");
        String previous = state == null ? null : state.getString(WORKSPACE_EXTRA);
        if (previous == null) previous = intent.getStringExtra(WORKSPACE_EXTRA);
        File directory = previous == null ? null : DexWorkspaceStore.workspaceFor(new File(previous));
        File root = new File(context.getFilesDir(), DexWorkspaceStore.ROOT_NAME).getCanonicalFile();
        if (directory != null && directory.getParentFile().equals(root)
                && directory.getName().startsWith(DexWorkspaceStore.EDITOR_PREFIX) && directory.isDirectory()) {
            workspace = DexWorkspaceStore.retain(directory);
        } else {
            workspace = DexWorkspaceStore.create(context.getFilesDir(), DexWorkspaceStore.EDITOR_PREFIX);
        }
        intent.putExtra(WORKSPACE_EXTRA, workspace.directory.getPath());
        // Claim before consuming a launch/result lease; a deferred delete may already be queued.
        for (String path : paths) inputs.add(DexWorkspaceStore.retain(new File(path)));
        for (String path : paths) DexWorkspaceStore.finishResult(new File(path));
        DexWorkspaceStore.cleanup(context.getFilesDir(), context.getCacheDir());
    }

    public ClassTree load() throws Exception {
        synchronized (loadLock) {
            if (closed) throw new IOException("DEX editor session has closed");
            if (tree == null) tree = new ClassTree(paths, workspace.directory.getPath());
            return tree;
        }
    }

    public ClassTree tree() {
        return tree;
    }

    public synchronized String workDir() {
        return workspace == null ? null : workspace.directory.getPath();
    }

    public void saveWorkspace(Bundle state) {
        state.putString(WORKSPACE_EXTRA, workDir());
    }

    public boolean isClosed() {
        return closed;
    }

    public void setFinishedCleanly(boolean finished) {
        finishedCleanly = finished;
    }

    public synchronized void keepResult() {
        if (paths != null) for (String path : paths) DexWorkspaceStore.keepForResult(new File(path));
    }

    @Override
    protected void onCleared() {
        closed = true;
        // Loading or a final autosave may still own the tree; never block the UI to join it.
        new Thread(() -> {
            synchronized (loadLock) {
                if (tree != null) tree.clearAll();
                tree = null;
                if (workspace != null) {
                    if (finishedCleanly) DexWorkspaceStore.deleteWhenUnused(workspace.directory);
                    workspace.close();
                }
                // Mark before releasing inputs so discard and delayed result delivery both clean safely.
                if (finishedCleanly && paths != null) {
                    for (String path : paths) DexWorkspaceStore.finishEditorInput(new File(path));
                }
                for (DexWorkspaceStore.Lease input : inputs) input.close();
                inputs.clear();
            }
        }, "dex-session-close").start();
    }
}
