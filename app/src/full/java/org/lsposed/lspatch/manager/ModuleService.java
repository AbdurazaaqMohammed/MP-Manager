package org.lsposed.lspatch.manager;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import org.lsposed.lspatch.util.LoadedModules;
import org.matrix.vector.ipc.IFrameworkService;
import org.matrix.vector.ipc.IProcessChannel;
import org.matrix.vector.ipc.LoadedModule;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.feature.apk.xposed.LSPatchRemoteServices;

/** The component name is part of LSPatch's loader protocol and must not change. */
public final class ModuleService extends Service {
    private LSPatchRemoteServices services;

    @Override public void onCreate() {
        super.onCreate();
        services = LSPatchRemoteServices.get(this);
    }

    @Override public IBinder onBind(Intent intent) { return android.os.Build.VERSION.SDK_INT < 28 ? null : binder; }

    private final IFrameworkService.Stub binder = new IFrameworkService.Stub() {
        private final ThreadLocal<List<LoadedModule>> pending = new ThreadLocal<>();

        @Override public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            try { return super.onTransact(code, data, reply, flags); }
            finally {
                List<LoadedModule> modules = pending.get();
                if (modules != null) modules.forEach(LoadedModules::discard);
                pending.remove();
            }
        }

        private Set<String> caller() {
            Set<String> apps = services.repository.callerApps(Binder.getCallingUid());
            if (apps.isEmpty()) throw new SecurityException("Caller is not an app bound to MP Manager");
            return apps;
        }

        private List<LoadedModule> modules(boolean legacy) {
            Set<String> scoped = new LinkedHashSet<>();
            for (String app : caller()) scoped.addAll(services.repository.scope(app));
            List<LoadedModule> result = new ArrayList<>();
            for (String module : scoped) {
                LoadedModule loaded = services.repository.load(module, legacy, services.moduleService(module));
                if (loaded != null) result.add(loaded);
            }
            if (!legacy) {
                services.hotReload.record(Binder.getCallingUid(), Binder.getCallingPid(), result);
                services.push(scoped);
            }
            pending.set(result);
            return result;
        }

        @Override public boolean isLogMuted() { return false; }
        @Override public List<LoadedModule> getLegacyModules() { return modules(true); }
        @Override public List<LoadedModule> getModules() { return modules(false); }
        @Override public String getPrefsPath(String module) {
            caller();
            if (!services.repository.canRead(Binder.getCallingUid(), module)) throw new SecurityException("Module is not scoped to caller");
            return "/data/user/" + (Binder.getCallingUid() / 100000) + "/" + module + "/shared_prefs/";
        }
        @Override public ParcelFileDescriptor openManagerApk() {
            caller();
            try { return ParcelFileDescriptor.open(new File(getApplicationInfo().sourceDir), ParcelFileDescriptor.MODE_READ_ONLY); }
            catch (FileNotFoundException error) { return null; }
        }
        @Override public IBinder requestManagerService() { return null; }
        @Override public void attachProcessChannel(IProcessChannel channel) throws RemoteException {
            Set<String> apps = caller();
            if (channel != null) services.hotReload.attach(Binder.getCallingUid(), Binder.getCallingPid(),
                    String.join(", ", apps), channel);
        }
    };
}
