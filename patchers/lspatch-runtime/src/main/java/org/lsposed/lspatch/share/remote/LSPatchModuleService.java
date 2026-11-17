package org.lsposed.lspatch.share.remote;

import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.util.HashMap;

import org.matrix.vector.ipc.IModuleService;
import org.matrix.vector.ipc.IRemotePreferenceCallback;

/**
 * A module's service as an <b>injected process</b> (the hook, running as the host app) sees it — a
 * port of Vector's {@code InjectedModuleService}.
 *
 * <p>It rides on {@code LoadedModule.service}, so the hook reaches it through {@code VectorContext}'s
 * {@code XposedInterface.getRemotePreferences} / remote-file reads rather than through a pushed
 * binder. Read-mostly by design: the process holding it runs as the app it was injected into, not as
 * the module, so it may read the module's remote files but not write them — the write side is {@link
 * LSPatchXposedService}. Preferences and files come from the same shared store both services back
 * onto, which is what makes a companion app's write visible to the hook.</p>
 */
public class LSPatchModuleService extends IModuleService.Stub {

    private static final int PER_USER_RANGE = 100000;

    private final String modulePackageName;
    private final long properties;
    private final RemotePreferenceStore prefs;
    private final RemoteFileStore files;
    private final PreferenceChangeNotifier notifier;

    public LSPatchModuleService(String modulePackageName, long properties, RemotePreferenceStore prefs,
            RemoteFileStore files, PreferenceChangeNotifier notifier) {
        this.modulePackageName = modulePackageName;
        this.properties = properties;
        this.prefs = prefs;
        this.files = files;
        this.notifier = notifier;
    }

    private static int callingUserId() {
        return Binder.getCallingUid() / PER_USER_RANGE;
    }

    @Override
    public long getFrameworkProperties() {
        return properties;
    }

    @Override
    public Bundle requestRemotePreferences(String group, IRemotePreferenceCallback callback) {
        int userId = callingUserId();
        Bundle bundle = new Bundle();
        bundle.putSerializable("map", new HashMap<>(prefs.getModulePrefs(modulePackageName, userId, group)));
        if (callback != null) {
            notifier.subscribe(group, userId, callback);
        }
        return bundle;
    }

    @Override
    public ParcelFileDescriptor openRemoteFile(String path) {
        // Read-only for a hooked process; a null return lets VectorContext raise the documented
        // FileNotFoundException for a missing or refused path.
        return files.openRemoteFile(modulePackageName, callingUserId(), path, true);
    }

    @Override
    public String[] getRemoteFileNames() {
        return files.listRemoteFiles(modulePackageName, callingUserId());
    }
}
