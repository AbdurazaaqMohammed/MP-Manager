package io.github.abdurazaaqmohammed.util.misc;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared application thread pools and lifecycle-safe main-thread UI dispatchers.
 */
public final class AppExecutors {

    private static final ExecutorService IO_EXECUTOR =
            Executors.newCachedThreadPool(new NamedDaemonFactory("mp-io-"));

    private static final ExecutorService CPU_EXECUTOR =
            Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors()),
                    new NamedDaemonFactory("mp-cpu-"));

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO_EXECUTOR;
    }

    public static ExecutorService cpu() {
        return CPU_EXECUTOR;
    }

    public static Handler mainHandler() {
        return MAIN_HANDLER;
    }

    /**
     * Posts {@code action} to the main thread only if {@code activity} is still alive.
     */
    public static void runOnUiIfAlive(@Nullable Activity activity, @NonNull Runnable action) {
        if (!isActivityAlive(activity)) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        MAIN_HANDLER.post(() -> {
            if (isActivityAlive(activity)) {
                action.run();
            }
        });
    }

    public static boolean isActivityAlive(@Nullable Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static final class NamedDaemonFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedDaemonFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
