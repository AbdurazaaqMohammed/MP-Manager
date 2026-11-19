package io.github.abdurazaaqmohammed.ui.editor.symbols

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.gson.JsonParser
import com.zerostudio.idea.widget.drawer.action.SymbolActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.GenericSymbolTextActions
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionId
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolTextDocument
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolTextEditing
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement

/** Sora stays in the host app; the drawer library has no editor dependency. */
class SoraSymbolActionHost(
    private val editor: CodeEditor,
    private val commentMarker: String,
    private val onOpenManager: Runnable,
    private val customAction: CustomAction
) : SymbolActionHost {
    fun interface CustomAction {
        fun execute(id: Int, text: String?, secondText: String?, options: String?): Boolean
    }

    private val textActions = GenericSymbolTextActions.createRegistry()
    private val document = SoraDocument(editor)

    override fun insertText(template: String): Boolean {
        if (!editor.isEditable) return false
        val line = editor.text.getLineString(editor.cursor.leftLine)
        val column = editor.cursor.leftColumn.coerceIn(0, line.length)
        fun wordCharacter(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'
        var wordStart = column
        var wordEnd = column
        while (wordStart > 0 && wordCharacter(line[wordStart - 1])) wordStart--
        while (wordEnd < line.length && wordCharacter(line[wordEnd])) wordEnd++
        val expansion = SymbolTextEditing.expandMacro(template, selectedText(), line,
            line.substring(wordStart, wordEnd), editor.lineSeparator.content)
        val start = editor.cursor.left().index
        editor.commitText(expansion.text, false, false)
        document.setSelection(start + expansion.insertionOffset, start + expansion.selectionEndOffset)
        return true
    }

    override fun replaceSelection(text: String): Boolean {
        if (!editor.isEditable) return false
        editor.commitText(text, false, false)
        return true
    }

    override fun surroundSelection(prefix: String, suffix: String): Boolean {
        if (!editor.isEditable) return false
        val start = editor.cursor.left().index
        val selected = selectedText()
        editor.commitText(prefix + selected + suffix, false, false)
        document.setSelection(start + prefix.length, start + prefix.length + selected.length)
        return true
    }

    override fun moveCursor(target: CursorTarget): Boolean {
        editor.moveSelection(when (target) {
            CursorTarget.LEFT -> SelectionMovement.LEFT
            CursorTarget.RIGHT -> SelectionMovement.RIGHT
            CursorTarget.UP -> SelectionMovement.UP
            CursorTarget.DOWN -> SelectionMovement.DOWN
            CursorTarget.LINE_START -> SelectionMovement.LINE_START
            CursorTarget.LINE_END -> SelectionMovement.LINE_END
            CursorTarget.DOCUMENT_START -> SelectionMovement.TEXT_START
            CursorTarget.DOCUMENT_END -> SelectionMovement.TEXT_END
        })
        return true
    }

    override fun delete(kind: DeleteKind): Boolean {
        if (!editor.isEditable) return false
        if (kind == DeleteKind.CURRENT_LINE) return executeEditorCommand("line.delete", null)
        var start = document.selectionStart
        var end = document.selectionEnd
        if (start == end) {
            when (kind) {
                DeleteKind.BACKWARD, DeleteKind.FORWARD -> {
                    val range = SymbolTextEditing.codePointDeletionRange(editor.text, start,
                        backwards = kind == DeleteKind.BACKWARD) ?: return false
                    start = range.start
                    end = range.end
                }
                DeleteKind.TO_LINE_START -> start = document.lineStartOffset(editor.cursor.leftLine)
                DeleteKind.TO_LINE_END -> end = document.lineEndOffset(editor.cursor.leftLine)
                else -> return false
            }
        }
        return start < end && document.replace(start, end, "")
    }

    override fun transformSelection(transform: SelectionTransform): Boolean {
        if (!editor.isEditable || !editor.cursor.isSelected) return false
        val text = selectedText()
        val start = document.selectionStart
        val transformed = when (transform) {
            SelectionTransform.UPPERCASE -> text.uppercase(java.util.Locale.ROOT)
            SelectionTransform.LOWERCASE -> text.lowercase(java.util.Locale.ROOT)
            SelectionTransform.TOGGLE_CASE -> text.map {
                if (it.isLowerCase()) it.uppercaseChar() else it.lowercaseChar()
            }.joinToString("")
        }
        if (!replaceSelection(transformed)) return false
        // commitText collapses the caret after replacing the selection; restore
        // the transformed region so repeated transforms and follow-up actions
        // stay scoped, matching insertText/surroundSelection behavior.
        document.setSelection(start, start + transformed.length)
        return true
    }

    override fun executeEditorCommand(commandId: String, payload: String?): Boolean {
        if (commandId == "line.copy" || commandId == "line.cut") {
            if (commandId == "line.cut" && !editor.isEditable) return false
            val clipboard = editor.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("text", editor.text.getLineString(editor.cursor.leftLine)))
            return commandId == "line.copy" || executeEditorCommand("line.delete", null)
        }
        val effectivePayload = if (commandId in setOf("editor.toggleLineComment", "line.toggleComment", "selection.comment", "document.toggleComment"))
            payload ?: commentMarker else payload
        textActions.execute(SymbolActionId("builtin", commandId), document, effectivePayload)?.let { return it }
        if (!editor.isEditable && commandId !in setOf("select_all", "selection.selectAll")) return false
        when (commandId) {
            "undo", "history.undo" -> if (editor.canUndo()) editor.undo() else return false
            "redo", "history.redo" -> if (editor.canRedo()) editor.redo() else return false
            "select_all", "selection.selectAll" -> editor.selectAll()
            "indent", "editor.indent" -> editor.indentOrCommitTab()
            "unindent", "editor.unindent" -> editor.unindentSelection()
            "format", "editor.format" -> editor.formatCodeAsync()
            else -> return false
        }
        return true
    }

    override fun performClipboard(operation: ClipboardOperation): Boolean {
        when (operation) {
            ClipboardOperation.COPY -> editor.copyText()
            ClipboardOperation.CUT -> { if (!editor.isEditable) return false; editor.cutText() }
            ClipboardOperation.PASTE -> { if (!editor.isEditable) return false; editor.pasteText() }
        }
        return true
    }

    override fun openManager(): Boolean { onOpenManager.run(); return true }

    override fun executeExtension(extensionId: String, payload: String?): Boolean {
        val id = extensionId.removePrefix("legacy.action.").toIntOrNull() ?: return false
        if (id !in 100..102 || (!editor.isEditable && id != 100)) return false
        // MP Manager's V1 adds replacement/options fields to the upstream wire schema.
        val data = payload?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
        if (data?.get("mpManager")?.asBoolean == true) {
            fun text(key: String) = data.get(key)?.takeUnless { it.isJsonNull }?.asString
            return customAction.execute(id, text("text"), text("secondText"), text("options"))
        }
        return customAction.execute(id, payload, null, null)
    }

    private fun selectedText(): String = editor.text.subSequence(document.selectionStart, document.selectionEnd).toString()

    private class SoraDocument(private val editor: CodeEditor) : SymbolTextDocument {
        private val content get() = editor.text
        override val isEditable get() = editor.isEditable
        override val textLength get() = content.length
        override val lineCount get() = content.lineCount
        override val cursorOffset get() = editor.cursor.right().index
        override val selectionStart get() = editor.cursor.left().index
        override val selectionEnd get() = editor.cursor.right().index
        override fun lineForOffset(offset: Int) = content.indexer.getCharPosition(offset.coerceIn(0, content.length)).line
        override fun lineStartOffset(line: Int) = content.indexer.getCharPosition(line, 0).index
        override fun lineEndOffset(line: Int) = content.indexer.getCharPosition(line, content.getColumnCount(line)).index
        override fun lineText(line: Int) = content.getLineString(line)
        override fun textBetween(start: Int, end: Int) = content.subSequence(start, end).toString()
        override fun charAt(offset: Int) = content.subSequence(offset, offset + 1)[0]
        override fun replace(start: Int, end: Int, replacement: String): Boolean {
            if (!editor.isEditable) return false
            val from = content.indexer.getCharPosition(start)
            val to = content.indexer.getCharPosition(end)
            content.replace(from.line, from.column, to.line, to.column, replacement)
            return true
        }
        override fun setSelection(start: Int, end: Int) {
            val from = content.indexer.getCharPosition(minOf(start, end).coerceIn(0, content.length))
            val to = content.indexer.getCharPosition(maxOf(start, end).coerceIn(0, content.length))
            editor.setSelectionRegion(from.line, from.column, to.line, to.column)
        }
    }
}
