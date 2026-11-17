package org.lsposed.lspatch.manager;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

import org.lsposed.lspatch.IXposedServicePull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.abdurazaaqmohammed.feature.apk.xposed.LSPatchModuleRepository;
import io.github.abdurazaaqmohammed.feature.apk.xposed.LSPatchRemoteServices;

public final class XposedPullService extends Service {
    private final Map<Integer, Long> requests = new ConcurrentHashMap<>();
    private final IXposedServicePull.Stub binder = new IXposedServicePull.Stub() {
        @Override public boolean requestPush() {
            int uid = Binder.getCallingUid();
            long now = SystemClock.elapsedRealtime();
            Long previous = requests.put(uid, now);
            if (previous != null && now - previous < 3000) return false;
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages == null) return false;
            for (var module : new LSPatchModuleRepository(XposedPullService.this).modules()) {
                for (String name : packages) if (module.packageName().equals(name)) {
                    LSPatchRemoteServices.get(XposedPullService.this).push(java.util.List.of(name));
                    return true;
                }
            }
            return false;
        }
    };

    @Override public IBinder onBind(Intent intent) { return binder; }
}
