package io.github.abdurazaaqmohammed.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.abdurazaaqmohammed.MPManager.BuildConfig;
import rikka.shizuku.Shizuku;

public final class ShizukuConnection {

    private static volatile IFileService service;
    private static volatile boolean binding;
    private static volatile CountDownLatch latch;

    private ShizukuConnection() {
    }

    private static Shizuku.UserServiceArgs args(Context context) {
        return new Shizuku.UserServiceArgs(new ComponentName(
                context.getPackageName(), ShizukuFileService.class.getName()))
                .daemon(false)
                .processNameSuffix("shizuku")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                if (binder != null && binder.pingBinder()) {
                    IFileService s = IFileService.Stub.asInterface(binder);
                    if (s.version() >= ShizukuFileService.SERVICE_VERSION) {
                        service = s;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                binding = false;
                CountDownLatch l = latch;
                if (l != null) l.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    public static synchronized void ensureBackground(Context context) {
        if (service != null || binding) return;
        final Context app = context.getApplicationContext();
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) return;
        } catch (Throwable e) {
            return;
        }
        binding = true;
        latch = new CountDownLatch(1);
        try {
            Shizuku.bindUserService(args(app), CONNECTION);
        } catch (Throwable e) {
            binding = false;
            CountDownLatch l = latch;
            if (l != null) l.countDown();
        }
    }

    public static IFileService peek() {
        return service;
    }

    public static IFileService await(Context context, long timeoutMs) {
        IFileService s = service;
        if (s != null) return s;
        ensureBackground(context);
        try {
            CountDownLatch l = latch;
            if (l != null) l.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return service;
    }

    public static void unbind(Context context) {
        try {
            Shizuku.unbindUserService(args(context.getApplicationContext()), CONNECTION, true);
        } catch (Throwable ignored) {
        } finally {
            service = null;
            binding = false;
        }
    }
}
