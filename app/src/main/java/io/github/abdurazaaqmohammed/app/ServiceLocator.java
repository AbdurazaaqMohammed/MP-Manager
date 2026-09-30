package io.github.abdurazaaqmohammed.app;

import android.content.Context;

/**
 * Minimal service locator (convention only, no DI framework per user choice).
 * Features fetch repositories here instead of `new`-ing utils everywhere.
 * Backed by existing utils classes until data/repo/* facades land.
 */
public final class ServiceLocator {

    private static volatile ServiceLocator instance;

    private final Context appContext;

    private ServiceLocator(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static ServiceLocator init(Context context) {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) instance = new ServiceLocator(context);
            }
        }
        return instance;
    }

    public static ServiceLocator get() {
        if (instance == null) throw new IllegalStateException("ServiceLocator.init() not called");
        return instance;
    }

    public Context appContext() {
        return appContext;
    }
}
