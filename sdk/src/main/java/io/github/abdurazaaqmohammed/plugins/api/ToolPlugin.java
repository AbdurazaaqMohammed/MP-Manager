package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Contract for functional tools. ToolRunnerActivity should become a generic
 * host: resolve plugin by id, call createView(), no per-tool branches.
 * New tools implement this; legacy ToolRegistry entries are exposed
 * via PluginRegistry as bridge plugins until migrated.
 */
public interface ToolPlugin {

    String id();

    String title(Context context);

    String subtitle(Context context);

    int iconRes(Context context);

    String category(Context context);

    /**
     * Build the tool UI inside container. Called on the main thread.
     */
    View createView(Context context, ViewGroup container);

    default void onDestroy() {
    }

    /** NFC tag intents and similar, forwarded by the host. Default no-op. */
    default void onNewIntent(Intent intent) {
    }

    /** Activity results (e.g. external scanners), forwarded by the host. */
    default void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    /**
     * When true, the host sizes this tool to exactly fill the viewport
     * (viewport height minus host padding) instead of scrolling it.
     * For paged tools with their own fixed bars and inner scrolling.
     * Default false keeps legacy scrolling behavior.
     */
    default boolean fillViewport() {
        return false;
    }
}
