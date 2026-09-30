package io.github.abdurazaaqmohammed.plugins.ext;

/**
 * Safe handle over the text editor's CodeEditor, handed to
 * {@link EditorAction#run}. All offsets are plain document offsets; the host
 * wraps every mutation in a batch edit.
 */
public interface EditorHandle {

    /** Currently selected text, or "" when nothing is selected. */
    String selectedText();

    /** Whether there is a non-empty selection. */
    boolean hasSelection();

    /** Replace the current selection (or insert at cursor when empty). */
    void replaceSelection(String text);

    /** Insert text at the cursor without touching the selection. */
    void insertAtCursor(String text);

    /** Whole document text. */
    String fullText();

    /** Replace the whole document text. */
    void setFullText(String text);
}
