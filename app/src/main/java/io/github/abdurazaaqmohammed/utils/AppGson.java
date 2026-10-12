package io.github.abdurazaaqmohammed.utils;

import com.google.gson.Gson;

/**
 * The app's single {@link Gson} instance.
 *
 * <p>Building a Gson is not free: it assembles its factory list and registers the
 * built-in type adapters. Several parts of the cold-start path used to do that for
 * themselves (bookmark store, pane cache, sidebar order), so the one instance is
 * built once here and shared. Gson instances are documented as thread-safe, and
 * every caller here uses default configuration, so sharing one is equivalent.
 */
public final class AppGson {

    private static final Gson INSTANCE = new Gson();

    private AppGson() {
    }

    public static Gson get() {
        return INSTANCE;
    }
}
