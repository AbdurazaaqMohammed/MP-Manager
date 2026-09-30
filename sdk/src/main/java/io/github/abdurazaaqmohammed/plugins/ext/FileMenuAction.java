package io.github.abdurazaaqmohammed.plugins.ext;

import android.content.Context;

import java.io.File;
import java.util.List;

/**
 * Adds one item to the file long-press menu.
 *
 * <p>The host shows it only when {@link #visibleFor} returns true for the
 * current selection, sorts it with the same order list (so it appears in
 * "File menu order" and can be rearranged/reset like built-ins), and calls
 * {@link #run} on tap. Only offered for real files, never inside archives.
 */
public interface FileMenuAction extends AppExtension {

    /** Stable id, e.g. "mypack.hash". Also used in the persisted menu order. */
    String id();

    /** Menu label. */
    String label();

    /**
     * Host drawable name for the menu icon (e.g. "tag_24px"), or "" for none.
     * Pack APK resources are NOT used.
     */
    String iconName();

    /** Whether this action applies to the current selection. */
    boolean visibleFor(List<File> files);

    /** Called on tap with the selected files. Receives an Activity context. */
    void run(Context context, List<File> files);
}
