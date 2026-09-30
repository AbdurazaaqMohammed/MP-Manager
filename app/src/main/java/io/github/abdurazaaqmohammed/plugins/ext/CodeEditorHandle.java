package io.github.abdurazaaqmohammed.plugins.ext;

import io.github.abdurazaaqmohammed.plugins.ext.EditorHandle;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;

/**
 * {@link EditorHandle} backed by the Sora CodeEditor. Every mutation runs
 * inside a batch edit.
 */
public final class CodeEditorHandle implements EditorHandle {

    private final CodeEditor editor;

    public CodeEditorHandle(CodeEditor editor) {
        this.editor = editor;
    }

    @Override
    public String selectedText() {
        try {
            Cursor cursor = editor.getCursor();
            if (cursor == null || !cursor.isSelected()) return "";
            CharSequence sub = editor.getText().subSequence(cursor.getLeft(), cursor.getRight());
            return sub == null ? "" : sub.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public boolean hasSelection() {
        try {
            return editor.getCursor() != null && editor.getCursor().isSelected();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void replaceSelection(String text) {
        if (text == null) text = "";
        try {
            Cursor cursor = editor.getCursor();
            Content content = editor.getText();
            content.beginBatchEdit();
            try {
                if (cursor != null && cursor.isSelected()) {
                    content.replace(cursor.getLeftLine(), cursor.getLeftColumn(),
                            cursor.getRightLine(), cursor.getRightColumn(), text);
                } else if (cursor != null) {
                    content.insert(cursor.getLeftLine(), cursor.getLeftColumn(), text);
                } else {
                    content.insert(0, 0, text);
                }
            } finally {
                content.endBatchEdit();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void insertAtCursor(String text) {
        if (text == null) text = "";
        try {
            Cursor cursor = editor.getCursor();
            Content content = editor.getText();
            content.beginBatchEdit();
            try {
                if (cursor != null) {
                    content.insert(cursor.getLeftLine(), cursor.getLeftColumn(), text);
                } else {
                    content.insert(0, 0, text);
                }
            } finally {
                content.endBatchEdit();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String fullText() {
        try {
            return editor.getText().toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public void setFullText(String text) {
        try {
            editor.setText(text == null ? "" : text);
        } catch (Exception ignored) {
        }
    }
}
