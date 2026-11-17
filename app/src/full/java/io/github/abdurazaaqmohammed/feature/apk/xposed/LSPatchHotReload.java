package io.github.abdurazaaqmohammed.feature.apk.xposed;

import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import org.lsposed.lspatch.share.remote.HotReloadDriver;
import org.lsposed.lspatch.util.LoadedModules;
import org.matrix.vector.ipc.HotReloadOutcome;
import org.matrix.vector.ipc.IHotReloadOutcomeReceiver;
import org.matrix.vector.ipc.IProcessChannel;
import org.matrix.vector.ipc.LoadedModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.service.HookedProcess;
import io.github.libxposed.service.IHotReloadCallback;
import io.github.libxposed.service.IXposedService;

public final class LSPatchHotReload implements HotReloadDriver {
    private static final class Target {
        final long id;
        final int uid;
        final int pid;
        final String name;
        final IProcessChannel channel;
        final Map<String, Long> modules = new ConcurrentHashMap<>();

        Target(int uid, int pid, String name, IProcessChannel channel) {
            id = ((long) uid << 32) | (pid & 0xffffffffL);
            this.uid = uid;
            this.pid = pid;
            this.name = name;
            this.channel = channel;
        }
    }

    private final LSPatchRemoteServices services;
    private final Map<Long, Target> targets = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    LSPatchHotReload(LSPatchRemoteServices services) { this.services = services; }

    public void attach(int uid, int pid, String name, IProcessChannel channel) throws RemoteException {
        Target target = new Target(uid, pid, name, channel);
        targets.put(target.id, target);
        try { channel.asBinder().linkToDeath(() -> targets.remove(target.id, target), 0); }
        catch (RemoteException error) { targets.remove(target.id, target); throw error; }
    }

    public void record(int uid, int pid, List<LoadedModule> modules) {
        Target target = targets.get(((long) uid << 32) | (pid & 0xffffffffL));
        if (target != null) {
            target.modules.clear();
            for (LoadedModule module : modules) target.modules.put(module.packageName, module.versionCode);
        }
    }

    @Override public List<HookedProcess> getRunningTargets(String module) {
        List<HookedProcess> result = new ArrayList<>();
        for (Target target : targets.values()) {
            Long version = target.modules.get(module);
            if (version == null) continue;
            HookedProcess process = new HookedProcess();
            process.targetId = target.id;
            process.uid = target.uid;
            process.pid = target.pid;
            process.processName = target.name;
            process.loadedVersionCode = version;
            process.state = HookedProcess.TARGET_STATE_UP_TO_DATE;
            result.add(process);
        }
        return result;
    }

    public void autoReload(String module) {
        executor.execute(() -> {
            LoadedModule generation = services.repository.load(module, false, services.moduleService(module));
            if (generation == null) return;
            boolean enabled = generation.code.autoHotReload;
            LoadedModules.discard(generation);
            if (enabled) for (HookedProcess target : getRunningTargets(module)) hotReload(module, target.targetId, null, null);
        });
    }

    @Override public void hotReload(String module, long id, Bundle data, IHotReloadCallback callback) {
        Target target = targets.get(id);
        if (target == null || !target.modules.containsKey(module)) {
            report(callback, IXposedService.HOT_RELOAD_PROCESS_DIED, "Target is no longer running this module");
            return;
        }
        if (!services.repository.canRead(target.uid, module)) {
            report(callback, IXposedService.HOT_RELOAD_UNSUPPORTED, "Module is no longer enabled for this app");
            return;
        }
        executor.execute(() -> reload(target, module, data, callback));
    }

    private void reload(Target target, String module, Bundle data, IHotReloadCallback callback) {
        LoadedModule generation = null;
        try {
            generation = services.repository.load(module, false, services.moduleService(module));
            if (generation == null || generation.code.moduleClassNames.size() != 1) {
                report(callback, IXposedService.HOT_RELOAD_UNSUPPORTED, "A modern module with one Java entry point is required");
                return;
            }
            CountDownLatch answered = new CountDownLatch(1);
            AtomicReference<HotReloadOutcome> outcome = new AtomicReference<>();
            IHotReloadOutcomeReceiver receiver = new IHotReloadOutcomeReceiver.Stub() {
                @Override public void onOutcome(HotReloadOutcome value) {
                    if (Binder.getCallingUid() != target.uid) throw new SecurityException("Wrong hot reload target");
                    outcome.set(value);
                    answered.countDown();
                }
            };
            long version = generation.versionCode;
            target.channel.hotReload(module, data, generation, receiver);
            // The oneway call has marshalled the descriptors; our copies are no longer needed.
            LoadedModules.discard(generation);
            generation = null;
            if (!answered.await(30, TimeUnit.SECONDS)) {
                report(callback, IXposedService.HOT_RELOAD_FAILED, "Target did not answer within 30 seconds");
                return;
            }
            HotReloadOutcome value = outcome.get();
            if (value == null) report(callback, IXposedService.HOT_RELOAD_FAILED, "Empty hot reload result");
            else {
                if (value.status == IXposedService.HOT_RELOAD_SUCCEEDED) target.modules.put(module, version);
                report(callback, value.status, value.message);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            report(callback, IXposedService.HOT_RELOAD_FAILED, "Hot reload interrupted");
        } catch (Exception error) {
            report(callback, IXposedService.HOT_RELOAD_FAILED, error.toString());
        } finally {
            LoadedModules.discard(generation);
        }
    }

    private static void report(IHotReloadCallback callback, int status, String message) {
        if (callback == null) return;
        try { callback.onHotReloadResult(status, message); }
        catch (RemoteException error) { Log.w("MP-LSPatch", "Cannot report hot reload", error); }
    }
}
