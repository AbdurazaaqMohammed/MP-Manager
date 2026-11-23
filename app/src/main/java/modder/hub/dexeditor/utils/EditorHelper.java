package modder.hub.dexeditor.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import io.github.abdurazaaqmohammed.util.io.CopyUtil;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer;
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.DirectAccessProps;

/**
 * EditorHelper: Shared logic for CodeEditor operations like line manipulation, indentation, etc.
 * Refactored to keep DexEditorActivity clean.
 */
public class EditorHelper {

    public static void copyLine(CodeEditor editor) {
        int line = editor.getCursor().getLeftLine();
        String text = editor.getText().getLineString(line);
        Context context = editor.getContext();
        if(context instanceof Activity a) CopyUtil.copyToClipboard(a, text);
    }

    public static void cutLine(CodeEditor editor) {
        int line = editor.getCursor().getLeftLine();
        String text = editor.getText().getLineString(line);
        Context context = editor.getContext();
        if(context instanceof Activity a) CopyUtil.copyToClipboard(a, text);
        editor.getText().delete(line, 0, line, editor.getText().getColumnCount(line));
        if (line < editor.getText().getLineCount() - 1) {
            editor.getText().delete(line, editor.getText().getColumnCount(line), line + 1, 0);
        }
    }

    public static void deleteLine(CodeEditor editor) {
        int line = editor.getCursor().getLeftLine();
        editor.getText().delete(line, 0, line, editor.getText().getColumnCount(line));
        if (line < editor.getText().getLineCount() - 1) {
            editor.getText().delete(line, editor.getText().getColumnCount(line), line + 1, 0);
        }
    }

    public static void emptyLine(CodeEditor editor) {
        int line = editor.getCursor().getLeftLine();
        editor.getText().delete(line, 0, line, editor.getText().getColumnCount(line));
    }

    public static void duplicateLine(CodeEditor editor) {
        int line = editor.getCursor().getLeftLine();
        String text = editor.getText().getLineString(line);
        editor.getText().insert(line, editor.getText().getColumnCount(line), "\n" + text);
    }

    public static void convertSelectedTextCase(CodeEditor editor, boolean toUpper) {
        if (editor.getCursor().isSelected()) {
            int start = editor.getCursor().getLeft();
            int end = editor.getCursor().getRight();
            String selectedText = editor.getText().substring(start, end);
            editor.getText().replace(start, end, toUpper ? selectedText.toUpperCase() : selectedText.toLowerCase());
        }
    }

    public static void indent(CodeEditor editor, boolean indent) {
        if (editor.getCursor().isSelected()) {
            int startLine = editor.getCursor().getLeftLine();
            int endLine = editor.getCursor().getRightLine();
            editor.getText().beginBatchEdit();
            for (int i = startLine; i <= endLine; i++) {
                if (indent) {
                    editor.getText().insert(i, 0, "\t");
                } else {
                    String line = editor.getText().getLineString(i);
                    if (line.startsWith("\t")) {
                        editor.getText().delete(i, 0, i, 1);
                    } else if (line.startsWith("    ")) {
                        editor.getText().delete(i, 0, i, 4);
                    }
                }
            }
            editor.getText().endBatchEdit();
        } else {
            int line = editor.getCursor().getLeftLine();
            if (indent) {
                editor.getText().insert(line, 0, "\t");
            } else {
                String lineStr = editor.getText().getLineString(line);
                if (lineStr.startsWith("\t")) {
                    editor.getText().delete(line, 0, line, 1);
                } else if (lineStr.startsWith("    ")) {
                    editor.getText().delete(line, 0, line, 4);
                }
            }
        }
    }

    private static final java.util.Map<CodeEditor, Runnable> pendingScans = new java.util.WeakHashMap<>();

    /**
     * Applies unified editor preferences across all CodeEditor instances in MP-Manager
     * (Smali EditorFragment, TextEditor UnifiedEditorFragment, etc.).
     */
    public static void applyCommonEditorPreferences(CodeEditor editor, SharedPreferences prefs) {
        if (editor == null || prefs == null) return;

        // 1. Line spacing
        float lineSpacingMult = 1.1f;
        try {
            lineSpacingMult = Float.parseFloat(prefs.getString("pref_line_spacing", "1.1"));
        } catch (Exception ignored) {}
        editor.setLineSpacing(2.0f, lineSpacingMult);

        // 2. Indent guides
        boolean showIndent = prefs.getBoolean("pref_show_indent_guides", true);
        editor.setBlockLineEnabled(showIndent);
        editor.setBlockLineWidth(1.0f);

        // 3. Highlight current line
        boolean highlightLine = prefs.getBoolean("pref_highlight_current_line", true);
        editor.setHighlightCurrentLine(highlightLine);

        // 4. Pin line numbers
        boolean pinLineNum = prefs.getBoolean("pref_pin_line_numbers", false);
        editor.setPinLineNumber(pinLineNum);

        // 5. Word wrap & anti-word-breaking with two-way key sync
        boolean wordWrap = prefs.getBoolean("wrap_text", prefs.getBoolean("pref_word_wrap", false));
        boolean antiWordBreak = prefs.getBoolean("pref_wrap_without_splitting_words", true);
        editor.setWordwrap(wordWrap, antiWordBreak, true);
        if (prefs.getBoolean("wrap_text", false) != prefs.getBoolean("pref_word_wrap", false)) {
            prefs.edit().putBoolean("wrap_text", wordWrap).putBoolean("pref_word_wrap", wordWrap).apply();
        }

        // 6. Blank symbols (whitespace) & Hide single space
        boolean showBlank = prefs.getBoolean("pref_show_blank_symbols", false);
        boolean hideSingleSpace = prefs.getBoolean("pref_hide_single_space", true);
        int flags = 0;
        if (showBlank) {
            if (hideSingleSpace) {
                // Show leading/trailing whitespace & line separators, but hide solitary inner spaces
                flags = CodeEditor.FLAG_DRAW_WHITESPACE_LEADING | CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING | CodeEditor.FLAG_DRAW_LINE_SEPARATOR;
            } else {
                // Show all whitespace including inner spaces
                flags = CodeEditor.FLAG_DRAW_WHITESPACE_LEADING | CodeEditor.FLAG_DRAW_WHITESPACE_INNER | CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING | CodeEditor.FLAG_DRAW_LINE_SEPARATOR;
            }
        }
        editor.setNonPrintablePaintingFlags(flags);

        // 7. Direct Access Props: Auto-indent, auto-pairing brackets, tab width, delete spaces,
        // sticky scroll, overscroll, bold delimiters, and enter-completion
        DirectAccessProps props = editor.getProps();
        if (props != null) {
            props.autoIndent = prefs.getBoolean("pref_auto_indent", true);
            props.symbolPairAutoCompletion = prefs.getBoolean("pref_auto_pair_brackets", true);
            props.highlightMatchingDelimiters = true;
            props.boldMatchingDelimiters = true;
            props.overScrollEnabled = true;
            props.stickyScroll = prefs.getBoolean("pref_sticky_scroll", true);
            props.stickyScrollMaxLines = 3;
            props.selectCompletionItemOnEnterForSoftKbd = prefs.getBoolean("pref_completion_on_enter", true);
            int tabSize = 4;
            try {
                tabSize = Integer.parseInt(prefs.getString("pref_tab_size", "4"));
            } catch (Exception ignored) {}
            props.deleteMultiSpaces = prefs.getBoolean("pref_use_spaces", true) ? tabSize : 1;
        }

        // 8. Invisible characters (Anti-Obfuscation)
        boolean showInvisible = prefs.getBoolean("pref_show_invisible_characters", false);
        updateInvisibleCharacters(editor, showInvisible);
    }

    /**
     * Scans editor content for hidden zero-width and control characters used in code obfuscation,
     * badging them visually using native TextInlayHint badges.
     */
    public static void updateInvisibleCharacters(CodeEditor editor, boolean enabled) {
        if (editor == null) return;
        synchronized (pendingScans) {
            Runnable old = pendingScans.remove(editor);
            if (old != null) {
                editor.removeCallbacks(old);
            }
        }
        if (!enabled) {
            editor.setInlayHints(null);
            return;
        }

        Content text = editor.getText();
        if (text == null) return;

        Runnable task = () -> {
            new Thread(() -> {
                try {
                    InlayHintsContainer container = new InlayHintsContainer();
                    int lineCount = text.getLineCount();
                    int foundCount = 0;
                    final int MAX_HINTS = 500;

                    for (int line = 0; line < lineCount && foundCount < MAX_HINTS; line++) {
                        String lineStr = text.getLineString(line);
                        if (lineStr == null) continue;
                        int len = lineStr.length();
                        for (int col = 0; col < len && foundCount < MAX_HINTS; col++) {
                            char c = lineStr.charAt(col);
                            String label = getInvisibleCharLabel(c);
                            if (label != null) {
                                container.add(new TextInlayHint(line, col, label));
                                foundCount++;
                            }
                        }
                    }

                    final InlayHintsContainer result = (foundCount > 0) ? container : null;
                    editor.post(() -> {
                        try {
                            editor.setInlayHints(result);
                        } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {
                } finally {
                    synchronized (pendingScans) {
                        pendingScans.remove(editor);
                    }
                }
            }).start();
        };

        synchronized (pendingScans) {
            pendingScans.put(editor, task);
        }
        editor.postDelayed(task, 200);
    }

    public static String getInvisibleCharLabel(char c) {
        return switch (c) {
            case '\u200B' -> "ZWSP";  // Zero-Width Space
            case '\u200C' -> "ZWNJ";  // Zero-Width Non-Joiner
            case '\u200D' -> "ZWJ";   // Zero-Width Joiner
            case '\u200E' -> "LRM";   // Left-to-Right Mark
            case '\u200F' -> "RLM";   // Right-to-Left Mark
            case '\u202A' -> "LRE";   // Left-to-Right Embedding
            case '\u202B' -> "RLE";   // Right-to-Left Embedding
            case '\u202C' -> "PDF";   // Pop Directional Format
            case '\u202D' -> "LRO";   // Left-to-Right Override
            case '\u202E' -> "RLO";   // Right-to-Left Override
            case '\u2060' -> "WJ";    // Word Joiner
            case '\u2061' -> "FA";    // Function Application
            case '\u2062' -> "IT";    // Invisible Times
            case '\u2063' -> "IS";    // Invisible Separator
            case '\u2064' -> "IP";    // Invisible Plus
            case '\uFEFF' -> "BOM";   // Byte Order Mark / ZWNBSP
            case '\u00A0' -> "NBSP";  // Non-Breaking Space
            case '\u00AD' -> "SHY";   // Soft Hyphen
            case '\u2000' -> "NQSP";  // En Quad
            case '\u2001' -> "MQSP";  // Em Quad
            case '\u2002' -> "ENSP";  // En Space
            case '\u2003' -> "EMSP";  // Em Space
            case '\u2004' -> "3/EM";  // Three-Per-Em Space
            case '\u2005' -> "4/EM";  // Four-Per-Em Space
            case '\u2006' -> "6/EM";  // Six-Per-Em Space
            case '\u2007' -> "FSP";   // Figure Space
            case '\u2008' -> "PSP";   // Punctuation Space
            case '\u2009' -> "THSP";  // Thin Space
            case '\u200A' -> "HSP";   // Hair Space
            case '\u202F' -> "NNBSP"; // Narrow No-Break Space
            case '\u205F' -> "MMSP";  // Medium Mathematical Space
            case '\u3000' -> "IDSP";  // Ideographic Space
            case '\u0000' -> "NUL";   // Null character
            default -> null;
        };
    }
}
