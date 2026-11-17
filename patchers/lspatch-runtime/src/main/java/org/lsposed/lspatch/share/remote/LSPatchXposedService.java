package org.lsposed.lspatch.share.remote;

import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.service.HookedProcess;
import io.github.libxposed.service.IHotReloadCallback;
import io.github.libxposed.service.IXposedScopeCallback;
import io.github.libxposed.service.IXposedService;

/**
 * A module's service as its own <b>app</b> (the companion) sees it — libxposed's {@code
 * IXposedService}, ported from Vector's {@code ModuleAppService}.
 *
 * <p>One instance per module, carrying the module package, delivered to the companion by pushing its
 * binder into the companion's exported provider (and, for an embedded module with no companion, handed
 * to the hook in-process so it can still write its own preferences). The full surface: it may read and
 * <b>write</b> preferences and files — the counterpart to {@link LSPatchModuleService}, which may only
 * read — and it drives scope and hot reload through injected collaborators so embedded and manager mode
 * differ only in what they supply, not in this class.</p>
 */
public class LSPatchXposedService extends IXposedService.Stub {

    private static final String TAG = "LSPatch-XposedService";
    private static final int PER_USER_RANGE = 100000;

    private final String modulePackageName;
    private final FrameworkInfo info;
    private final RemotePreferenceStore prefs;
    private final RemoteFileStore files;
    private final PreferenceChangeNotifier notifier;
    private final ScopeSource scopeSource;
    private final HotReloadDriver hotReloadDriver;

    public LSPatchXposedService(String modulePackageName, FrameworkInfo info, RemotePreferenceStore prefs,
            RemoteFileStore files, PreferenceChangeNotifier notifier, ScopeSource scopeSource,
            HotReloadDriver hotReloadDriver) {
        this.modulePackageName = modulePackageName;
        this.info = info;
        this.prefs = prefs;
        this.files = files;
        this.notifier = notifier;
        this.scopeSource = scopeSource;
        this.hotReloadDriver = hotReloadDriver;
    }

    public String getModulePackageName() {
        return modulePackageName;
    }

    private static int callingUserId() {
        return Binder.getCallingUid() / PER_USER_RANGE;
    }

    // framework details
    @Override
    public int getApiVersion() {
        return IXposedService.API_102;
    }

    @Override
    public String getFrameworkName() {
        return info.name;
    }

    @Override
    public String getFrameworkVersion() {
        return info.version;
    }

    @Override
    public long getFrameworkVersionCode() {
        return info.versionCode;
    }

    @Override
    public long getFrameworkProperties() {
        return info.properties;
    }

    // scope utilities
    @Override
    public List<String> getScope() {
        return scopeSource.getScope(modulePackageName);
    }

    @Override
    public void requestScope(List<String> packages, IXposedScopeCallback callback) {
        // A module's scope is which apps were patched with it, and LSPatch cannot patch an app on
        // demand at runtime, so this cannot be granted the way a rooted framework grants it.
        try {
            callback.onScopeRequestFailed("scope is set by patching in LSPatch; it cannot be granted at runtime");
        } catch (Throwable t) {
            Log.w(TAG, "onScopeRequestFailed", t);
        }
    }

    @Override
    public void removeScope(List<String> packages) {
        // Nothing to remove at runtime; a module leaves an app's scope by un-patching it.
    }

    @Override
    public List<HookedProcess> getRunningTargets() {
        return hotReloadDriver.getRunningTargets(modulePackageName);
    }

    @Override
    public void hotReloadModule(long targetId, Bundle data, IHotReloadCallback callback) {
        hotReloadDriver.hotReload(modulePackageName, targetId, data, callback);
    }

    // remote preference utilities
    @Override
    public Bundle requestRemotePreferences(String group) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("map", new HashMap<>(prefs.getModulePrefs(modulePackageName, callingUserId(), group)));
        return bundle;
    }

    @Override
    public void updateRemotePreferences(String group, Bundle diff) {
        int userId = callingUserId();
        // RemotePreferences.Editor always writes "clear" for edit().clear(); handling it as a
        // group-level delete first is what keeps a cleared key from surviving.
        if (diff.getBoolean("clear", false)) {
            prefs.deleteModulePrefs(modulePackageName, userId, group);
        }
        Map<String, Object> values = new HashMap<>();
        Object delete = diff.getSerializable("delete");
        if (delete instanceof Set) {
            for (Object key : (Set<?>) delete) {
                values.put(String.valueOf(key), null);
            }
        }
        Object put = diff.getSerializable("put");
        if (put instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) put).entrySet()) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        prefs.updateModulePrefs(modulePackageName, userId, group, values);
        // Tell subscribed hooks what changed, in the same diff shape the editor produced.
        notifier.notifyChanged(group, userId, diff);
    }

    @Override
    public void deleteRemotePreferences(String group) {
        int userId = callingUserId();
        prefs.deleteModulePrefs(modulePackageName, userId, group);
        Bundle clear = new Bundle();
        clear.putBoolean("clear", true);
        notifier.notifyChanged(group, userId, clear);
    }

    // remote file utilities
    @Override
    public String[] listRemoteFiles() {
        return files.listRemoteFiles(modulePackageName, callingUserId());
    }

    @Override
    public ParcelFileDescriptor openRemoteFile(String name) {
        // Writable for the module app.
        return files.openRemoteFile(modulePackageName, callingUserId(), name, false);
    }

    @Override
    public boolean deleteRemoteFile(String name) {
        return files.deleteRemoteFile(modulePackageName, callingUserId(), name);
    }
}
