package io.github.abdurazaaqmohammed.feature.apk.xposed;

import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import org.lsposed.lspatch.share.LSPConfig;
import org.lsposed.lspatch.share.remote.FrameworkInfo;
import org.lsposed.lspatch.share.remote.LSPatchModuleService;
import org.lsposed.lspatch.share.remote.LSPatchXposedService;
import org.lsposed.lspatch.share.remote.PreferenceChangeNotifier;
import org.lsposed.lspatch.share.remote.RemoteFileStore;
import org.lsposed.lspatch.share.remote.RemotePreferenceStore;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.IXposedService;

public final class LSPatchRemoteServices {
    private static volatile LSPatchRemoteServices instance;
    private final Context context;
    public final LSPatchModuleRepository repository;
    public final LSPatchHotReload hotReload;
    private final RemotePreferenceStore preferences;
    private final RemoteFileStore files;
    private final Map<String, PreferenceChangeNotifier> notifiers = new ConcurrentHashMap<>();
    private final Map<String, LSPatchModuleService> readers = new ConcurrentHashMap<>();
    private final Map<String, LSPatchXposedService> companions = new ConcurrentHashMap<>();
    private final ExecutorService pushes = Executors.newFixedThreadPool(2);

    private LSPatchRemoteServices(Context context) {
        this.context = context.getApplicationContext();
        repository = new LSPatchModuleRepository(this.context);
        preferences = new RemotePreferenceStore(this.context);
        files = new RemoteFileStore(this.context);
        hotReload = new LSPatchHotReload(this);
    }

    public static LSPatchRemoteServices get(Context context) {
        if (instance == null) synchronized (LSPatchRemoteServices.class) {
            if (instance == null) instance = new LSPatchRemoteServices(context);
        }
        return instance;
    }

    private PreferenceChangeNotifier notifier(String module) {
        return notifiers.computeIfAbsent(module, name -> new PreferenceChangeNotifier());
    }

    public LSPatchModuleService moduleService(String module) {
        return readers.computeIfAbsent(module, name -> new LSPatchModuleService(name,
                IXposedService.PROP_CAP_REMOTE, preferences, files, notifier(name)) {
            @Override public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (!repository.canRead(Binder.getCallingUid(), name)) throw new SecurityException("Module is not scoped to caller");
                return super.onTransact(code, data, reply, flags);
            }
        });
    }

    public LSPatchXposedService companionService(String module) {
        return companions.computeIfAbsent(module, name -> new LSPatchXposedService(name,
                new FrameworkInfo("LSPatch (MP Manager)", LSPConfig.instance.VERSION_NAME,
                        LSPConfig.instance.VERSION_CODE, IXposedService.PROP_CAP_REMOTE),
                preferences, files, notifier(name), repository::appsForModule, hotReload) {
            @Override public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (!repository.isModuleCaller(Binder.getCallingUid(), name)) throw new SecurityException("Only the module may use its companion service");
                return super.onTransact(code, data, reply, flags);
            }
        });
    }

    public void push(Collection<String> modules) {
        for (String module : modules) pushes.execute(() -> push(module));
    }

    public void push(String module) {
        try {
            Bundle extras = new Bundle();
            extras.putBinder("binder", companionService(module).asBinder());
            context.getContentResolver().call(Uri.parse("content://" + module + IXposedService.AUTHORITY_SUFFIX),
                    IXposedService.SEND_BINDER, null, extras);
        } catch (RuntimeException error) {
            Log.d("MP-LSPatch", "No companion provider for " + module, error);
        }
    }
}
