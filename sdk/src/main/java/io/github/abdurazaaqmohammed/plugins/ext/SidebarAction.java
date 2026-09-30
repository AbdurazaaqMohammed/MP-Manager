package io.github.abdurazaaqmohammed.plugins.ext;

import android.content.Context;

/**
 * Adds one item to the main app sidebar's tools section.
 *
 * <p>The host appends it after the built-in tools, persists its position with
 * the same order list, and honors hide/organize like built-ins. {@link #open}
 * receives an Activity context (MainActivity).
 */
public interface SidebarAction extends AppExtension {

    /** Stable id, e.g. "mypack.mytool". */
    String id();

    /** Row label shown in the sidebar. */
    String title();

    /**
     * Host drawable name for the row icon (e.g. "tools_24px"), or "" for the
     * default tools icon. Pack APK resources are NOT used.
     */
    String iconName();

    /** Called on tap. Typically starts an activity or opens a tool screen. */
    void open(Context context);
}
