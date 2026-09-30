package io.github.abdurazaaqmohammed.plugins.ext;

/**
 * Adds one button to the text editor's floating (selection) menu.
 *
 * <p>The host auto-appends the id to the stored menu order (so it shows up in
 * EditFloatingMenusActivity where users can reorder, disable, or hide it like
 * built-ins) and calls {@link #run} on tap.
 */
public interface EditorAction extends AppExtension {

    /** Stable id, e.g. "mypack.uppercase". */
    String id();

    /** Button title (tooltip + floating-menu editor row). */
    String title();

    /**
     * Host drawable name for the button icon, or "" for a default icon.
     * Pack APK resources are NOT used.
     */
    String iconName();

    /** Called on tap. Never null handle; check {@link EditorHandle#hasSelection}. */
    void run(EditorHandle editor);
}
