package io.github.abdurazaaqmohammed.plugins.api;

import io.github.abdurazaaqmohammed.plugins.ext.AppExtension;

import java.util.Collections;
import java.util.List;

/**
 * Entry point of a downloadable tool pack APK. Each pack module implements
 * this in a class named in the pack catalog; the host loads it with
 * DexClassLoader and registers every tool it returns.
 */
public interface ToolPack {

    /** Stable pack id, e.g. "math". Must match the catalog entry. */
    String packId();

    /** Pack version code. Must match the catalog entry to install. */
    int version();

    /** Tool implementations shipped in this pack. */
    List<ToolPlugin> tools();

    /**
     * Host extensions shipped in this pack (sidebar items, settings rows,
     * file-menu/editor/APK actions). Defaults to none; override to contribute.
     */
    default List<AppExtension> extensions() {
        return Collections.emptyList();
    }
}
