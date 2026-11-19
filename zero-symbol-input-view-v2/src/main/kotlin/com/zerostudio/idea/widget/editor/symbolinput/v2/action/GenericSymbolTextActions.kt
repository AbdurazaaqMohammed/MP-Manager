/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.action

import com.zerostudio.idea.widget.drawer.action.SymbolTextDocument as PublicSymbolTextDocument

/**
 * Editor-independent implementations for common text-editing commands.
 *
 * These functions work only through PublicSymbolTextDocument. Sora, a custom editor,
 * or a future editor core needs to provide one small adapter instead of
 * reimplementing each action.
 */
object GenericSymbolTextActions {
    private const val MAX_DOCUMENT_WIDE_CHARS = 32 * 1024 * 1024
    private const val MAX_DOCUMENT_WIDE_LINES = 200_000

    private val handlers: Map<String, SymbolActionHandler<PublicSymbolTextDocument>> = buildMap {
        fun add(id: String, action: (PublicSymbolTextDocument, String?) -> Boolean) {
            put(id, SymbolActionHandler(action))
        }

        add("line.duplicate") { d, _ -> duplicateLine(d) }
        add("line.delete") { d, _ -> deleteLine(d, currentLine(d)) }
        add("line.clear") { d, _ -> clearLine(d, currentLine(d)) }
        add("line.replace") { d, payload -> replaceLine(d, currentLine(d), payload.orEmpty()) }
        add("insert_payload") { document, payload ->
            if (!document.isEditable) {
                false
            } else {
                val start = minOf(document.selectionStart, document.selectionEnd)
                val end = maxOf(document.selectionStart, document.selectionEnd)
                val replacement = payload.orEmpty()
                if (!document.replace(start, end, replacement)) {
                    false
                } else {
                    val caret = start + replacement.length
                    document.setSelection(caret, caret)
                    true
                }
            }
        }
        add("line.insertAbove") { d, _ -> insertBlankLineAbove(d) }
        add("line.insertBelow") { d, _ -> insertBlankLineBelow(d) }
        add("line.joinNext") { d, _ -> joinWithNextLine(d) }
        add("line.moveUp") { d, _ -> moveLine(d, direction = -1) }
        add("line.moveDown") { d, _ -> moveLine(d, direction = 1) }
        add("line.select") { d, _ -> selectCurrentLine(d) }
        add("line.uppercase") { d, _ -> transformCurrentLine(d) { it.uppercase() } }
        add("line.lowercase") { d, _ -> transformCurrentLine(d) { it.lowercase() } }
        add("line.toggleCase") { d, _ -> transformCurrentLine(d, ::toggleCase) }
        add("line.trimTrailingWhitespace") { d, _ -> trimCurrentLine(d) }
        add("line.trimLeadingWhitespace") { d, _ -> trimLeadingCurrentLine(d) }
        add("line.duplicateAbove") { d, _ -> duplicateLineAbove(d) }
        add("line.toggleComment") { d, payload -> toggleLineComments(d, payload ?: "//") }
        add("editor.toggleLineComment") { d, payload -> toggleLineComments(d, payload ?: "//") }
        add("line.indent") { d, payload -> indentCurrentLine(d, payload ?: "\t") }
        add("line.unindent") { d, _ -> unindentCurrentLine(d) }

        add("selection.delete") { d, _ -> deleteSelection(d) }
        add("selection.duplicate") { d, _ -> duplicateSelection(d) }
        add("selection.uppercase") { d, _ -> transformSelection(d) { it.uppercase() } }
        add("selection.lowercase") { d, _ -> transformSelection(d) { it.lowercase() } }
        add("selection.toggleCase") { d, _ -> transformSelection(d, ::toggleCase) }
        add("selection.wrapParentheses") { d, _ -> wrapSelection(d, "(", ")") }
        add("selection.wrapBrackets") { d, _ -> wrapSelection(d, "[", "]") }
        add("selection.wrapBraces") { d, _ -> wrapSelection(d, "{", "}") }
        add("selection.wrapQuotes") { d, _ -> wrapSelection(d, "\"", "\"") }
        add("selection.wrapSingleQuotes") { d, _ -> wrapSelection(d, "'", "'") }
        add("selection.wrapBackticks") { d, _ -> wrapSelection(d, "\u0060", "\u0060") }
        add("selection.wrapAngleBrackets") { d, _ -> wrapSelection(d, "<", ">") }
        add("selection.wrapAsterisks") { d, _ -> wrapSelection(d, "*", "*") }
        add("selection.wrapUnderscores") { d, _ -> wrapSelection(d, "_", "_") }
        add("selection.wrapHtmlTag") { d, payload -> wrapSelectionWithTag(d, payload) }
        add("selection.indentLines") { d, payload -> indentSelectedLines(d, payload ?: "\t") }
        add("selection.unindentLines") { d, _ -> unindentSelectedLines(d) }
        add("selection.reverseLines") { d, _ -> reorderSelectedLines(d, reverse = true) }
        add("selection.sortLines") { d, _ -> reorderSelectedLines(d, reverse = false) }
        add("selection.comment") { d, payload -> toggleLineComments(d, payload ?: "//") }

        add("cursor.wordLeft") { d, _ -> moveWord(d, backwards = true) }
        add("cursor.wordRight") { d, _ -> moveWord(d, backwards = false) }
        add("cursor.deleteWordBackward") { d, _ -> deleteWord(d, backwards = true) }
        add("cursor.deleteWordForward") { d, _ -> deleteWord(d, backwards = false) }
        add("cursor.selectWord") { d, _ -> selectWord(d) }
        add("cursor.selectLine") { d, _ -> selectCurrentLine(d) }
        add("cursor.lineStart") { d, _ -> moveToLineBoundary(d, start = true) }
        add("cursor.lineEnd") { d, _ -> moveToLineBoundary(d, start = false) }
        add("cursor.selectToLineStart") { d, _ -> extendSelectionToLineBoundary(d, start = true) }
        add("cursor.selectToLineEnd") { d, _ -> extendSelectionToLineBoundary(d, start = false) }
        add("cursor.selectToDocumentStart") { d, _ -> d.setSelection(0, d.cursorOffset); true }
        add("cursor.selectToDocumentEnd") { d, _ -> d.setSelection(d.cursorOffset, d.textLength); true }
        add("cursor.documentStart") { d, _ -> setCaret(d, 0) }
        add("cursor.documentEnd") { d, _ -> setCaret(d, d.textLength) }

        add("document.selectAll") { d, _ ->
            d.setSelection(0, d.textLength)
            true
        }
        add("document.trimTrailingWhitespace") { d, _ -> trimDocument(d) }
        add("document.trimLeadingWhitespace") { d, _ -> trimDocumentLeading(d) }
        add("document.ensureFinalNewline") { d, payload -> ensureFinalNewline(d, payload) }
        add("document.removeBlankLines") { d, _ -> removeBlankLines(d) }
        add("document.toggleComment") { d, payload -> toggleLineComments(d, payload ?: "//") }
    }

    /** Create a reverse-ID registry for all built-in generic text actions. */
    @JvmStatic
    fun createRegistry(): SymbolActionRegistry<PublicSymbolTextDocument> =
        SymbolActionRegistry<PublicSymbolTextDocument>().also { registry ->
            handlers.forEach { (id, handler) ->
                registry.register(SymbolActionId("builtin", id), handler)
            }
        }

    @JvmStatic
    fun supportedCommandIds(): Set<String> = handlers.keys

    private fun currentLine(document: PublicSymbolTextDocument): Int =
        document.lineForOffset(document.cursorOffset.coerceIn(0, document.textLength))
            .coerceIn(0, document.lineCount - 1)

    private fun deleteLine(document: PublicSymbolTextDocument, line: Int): Boolean {
        if (!document.isEditable || document.lineCount <= 0) return false
        val start = document.lineStartOffset(line)
        val end = when {
            line < document.lineCount - 1 -> document.lineStartOffset(line + 1)
            line > 0 -> document.textLength
            else -> document.lineEndOffset(line)
        }
        val previousSeparatorLength = lineSeparatorBefore(document, line)?.length ?: 1
        val deleteStart = if (line == document.lineCount - 1 && line > 0) {
            (start - previousSeparatorLength).coerceAtLeast(0)
        } else start
        if (!document.replace(deleteStart, end, "")) return false
        document.setSelection(deleteStart, deleteStart)
        return true
    }

    private fun clearLine(document: PublicSymbolTextDocument, line: Int): Boolean {
        if (!document.isEditable) return false
        val start = document.lineStartOffset(line)
        val end = document.lineEndOffset(line)
        if (!document.replace(start, end, "")) return false
        document.setSelection(start, start)
        return true
    }

    private fun replaceLine(document: PublicSymbolTextDocument, line: Int, replacement: String): Boolean {
        if (!document.isEditable) return false
        val start = document.lineStartOffset(line)
        val end = document.lineEndOffset(line)
        if (!document.replace(start, end, replacement)) return false
        document.setSelection(start + replacement.length, start + replacement.length)
        return true
    }

    private fun duplicateLine(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val end = document.lineEndOffset(line)
        val content = document.lineText(line)
        val separator = lineSeparatorAfter(document, line) ?: lineSeparatorBefore(document, line) ?: "\n"
        if (!document.replace(end, end, separator + content)) return false
        val caret = end + separator.length + content.length
        document.setSelection(caret, caret)
        return true
    }

    private fun insertBlankLineAbove(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val start = document.lineStartOffset(line)
        val separator = lineSeparatorBefore(document, line) ?: lineSeparatorAfter(document, line) ?: "\n"
        if (!document.replace(start, start, separator)) return false
        document.setSelection(start, start)
        return true
    }

    private fun insertBlankLineBelow(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val end = document.lineEndOffset(line)
        val separator = lineSeparatorAfter(document, line) ?: lineSeparatorBefore(document, line) ?: "\n"
        if (!document.replace(end, end, separator)) return false
        document.setSelection(end + separator.length, end + separator.length)
        return true
    }

    private fun joinWithNextLine(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        if (line >= document.lineCount - 1) return false
        val end = document.lineEndOffset(line)
        val separator = lineSeparatorAfter(document, line) ?: return false
        if (!document.replace(end, end + separator.length, "")) return false
        document.setSelection(end, end)
        return true
    }

    private fun moveLine(document: PublicSymbolTextDocument, direction: Int): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val target = line + direction
        if (target !in 0 until document.lineCount) return false
        val cursorOffset = document.cursorOffset
        if (direction < 0) {
            val previousStart = document.lineStartOffset(target)
            val currentStart = document.lineStartOffset(line)
            val currentEnd = document.lineEndOffset(line)
            val previousText = document.lineText(target)
            val currentText = document.lineText(line)
            val relative = (cursorOffset - currentStart).coerceIn(0, currentText.length)
            val separator = lineSeparatorBefore(document, line) ?: "\n"
            if (!document.replace(previousStart, currentEnd, currentText + separator + previousText)) return false
            val caret = previousStart + currentText.length + separator.length + relative
            document.setSelection(caret, caret)
        } else {
            val currentStart = document.lineStartOffset(line)
            val nextStart = document.lineStartOffset(target)
            val nextEnd = document.lineEndOffset(target)
            val currentText = document.lineText(line)
            val nextText = document.lineText(target)
            val relative = (cursorOffset - currentStart).coerceIn(0, currentText.length)
            val separator = lineSeparatorAfter(document, line) ?: "\n"
            if (!document.replace(currentStart, nextEnd, nextText + separator + currentText)) return false
            val caret = currentStart + nextText.length + separator.length + relative
            document.setSelection(caret, caret)
        }
        return true
    }

    private fun selectCurrentLine(document: PublicSymbolTextDocument): Boolean {
        val line = currentLine(document)
        document.setSelection(document.lineStartOffset(line), document.lineEndOffset(line))
        return true
    }

    private fun transformCurrentLine(
        document: PublicSymbolTextDocument,
        transform: (String) -> String
    ): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        return replaceLine(document, line, transform(document.lineText(line)))
    }

    private fun trimCurrentLine(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val content = document.lineText(line)
        val trimmedLength = content.trimEnd().length
        val end = document.lineEndOffset(line)
        if (trimmedLength == content.length) return false
        return document.replace(document.lineStartOffset(line) + trimmedLength, end, "")
    }

    private fun toggleLineComments(document: PublicSymbolTextDocument, marker: String): Boolean {
        if (!document.isEditable || marker.isEmpty()) return false
        val firstLine = document.lineForOffset(document.selectionStart).coerceIn(0, document.lineCount - 1)
        var lastLine = document.lineForOffset(document.selectionEnd).coerceIn(0, document.lineCount - 1)
        if (document.selectionStart != document.selectionEnd &&
            lastLine > firstLine &&
            document.selectionEnd == document.lineStartOffset(lastLine)
        ) lastLine--
        for (line in lastLine downTo firstLine) {
            val content = document.lineText(line)
            val indentation = content.indexOfFirst { !it.isWhitespace() }.let {
                if (it < 0) content.length else it
            }
            val offset = document.lineStartOffset(line) + indentation
            if (content.substring(indentation).startsWith(marker)) {
                if (!document.replace(offset, offset + marker.length, "")) return false
            } else {
                if (!document.replace(offset, offset, marker)) return false
            }
        }
        return true
    }

    private fun indentCurrentLine(document: PublicSymbolTextDocument, indent: String): Boolean {
        if (!document.isEditable || indent.isEmpty()) return false
        val start = document.lineStartOffset(currentLine(document))
        val previousCursor = document.cursorOffset
        if (!document.replace(start, start, indent)) return false
        val caret = if (previousCursor >= start) previousCursor + indent.length else start + indent.length
        document.setSelection(caret, caret)
        return true
    }

    private fun unindentCurrentLine(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val start = document.lineStartOffset(currentLine(document))
        val content = document.lineText(currentLine(document))
        val count = when {
            content.startsWith("\t") -> 1
            content.startsWith("    ") -> 4
            content.startsWith("   ") -> 3
            content.startsWith("  ") -> 2
            content.startsWith(" ") -> 1
            else -> 0
        }
        if (count == 0) return false
        val previousCursor = document.cursorOffset
        if (!document.replace(start, start + count, "")) return false
        val caret = if (previousCursor >= start + count) previousCursor - count else start
        document.setSelection(caret.coerceAtLeast(start), caret.coerceAtLeast(start))
        return true
    }

    private fun deleteSelection(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable || document.selectionStart == document.selectionEnd) return false
        val start = document.selectionStart
        if (!document.replace(start, document.selectionEnd, "")) return false
        document.setSelection(start, start)
        return true
    }

    private fun duplicateSelection(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable || document.selectionStart == document.selectionEnd) return false
        val start = document.selectionStart
        val end = document.selectionEnd
        val selected = document.textBetween(start, end)
        if (!document.replace(end, end, selected)) return false
        document.setSelection(end, end + selected.length)
        return true
    }

    private fun transformSelection(
        document: PublicSymbolTextDocument,
        transform: (String) -> String
    ): Boolean {
        if (!document.isEditable || document.selectionStart == document.selectionEnd) return false
        val start = document.selectionStart
        val end = document.selectionEnd
        val result = transform(document.textBetween(start, end))
        if (!document.replace(start, end, result)) return false
        document.setSelection(start, start + result.length)
        return true
    }

    private fun wrapSelection(document: PublicSymbolTextDocument, prefix: String, suffix: String): Boolean {
        if (!document.isEditable || document.selectionStart == document.selectionEnd) return false
        val start = document.selectionStart
        val end = document.selectionEnd
        val selected = document.textBetween(start, end)
        if (!document.replace(start, end, prefix + selected + suffix)) return false
        document.setSelection(start + prefix.length, start + prefix.length + selected.length)
        return true
    }


    private fun trimLeadingCurrentLine(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val content = document.lineText(line)
        val count = content.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) content.length else it }
        if (count == 0) return false
        val start = document.lineStartOffset(line)
        val caret = document.cursorOffset
        if (!document.replace(start, start + count, "")) return false
        val nextCaret = (caret - minOf(count, (caret - start).coerceAtLeast(0))).coerceAtLeast(start)
        document.setSelection(nextCaret, nextCaret)
        return true
    }

    private fun duplicateLineAbove(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val line = currentLine(document)
        val start = document.lineStartOffset(line)
        val content = document.lineText(line)
        if (!document.replace(start, start, content + lineSeparator(document, line))) return false
        val caret = start + content.length
        document.setSelection(caret, caret)
        return true
    }

    private fun lineSeparatorBefore(document: PublicSymbolTextDocument, line: Int): String? {
        val start = document.lineStartOffset(line)
        if (start >= 2 && document.charAt(start - 2) == '\r' && document.charAt(start - 1) == '\n') return "\r\n"
        if (start > 0 && document.charAt(start - 1) == '\r') return "\r"
        if (start > 0 && document.charAt(start - 1) == '\n') return "\n"
        return null
    }

    private fun lineSeparatorAfter(document: PublicSymbolTextDocument, line: Int): String? {
        val end = document.lineEndOffset(line)
        if (end >= document.textLength) return null
        return when (document.charAt(end)) {
            '\r' -> if (end + 1 < document.textLength && document.charAt(end + 1) == '\n') "\r\n" else "\r"
            '\n' -> "\n"
            else -> null
        }
    }

    private fun lineSeparator(document: PublicSymbolTextDocument, line: Int): String =
        lineSeparatorBefore(document, line) ?: lineSeparatorAfter(document, line) ?: "\n"

    private fun wrapSelectionWithTag(document: PublicSymbolTextDocument, payload: String?): Boolean {
        val tag = payload?.trim()?.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9:-]{0,63}")) }
            ?: return false
        return wrapSelection(document, "<$tag>", "</$tag>")
    }

    private fun extendSelectionToLineBoundary(document: PublicSymbolTextDocument, start: Boolean): Boolean {
        val line = currentLine(document)
        val boundary = if (start) document.lineStartOffset(line) else document.lineEndOffset(line)
        document.setSelection(if (start) boundary else document.cursorOffset, if (start) document.cursorOffset else boundary)
        return true
    }

    private fun selectedLineRange(document: PublicSymbolTextDocument): IntRange? {
        val start = document.selectionStart
        val end = document.selectionEnd
        if (start == end) return null
        val first = document.lineForOffset(start).coerceIn(0, document.lineCount - 1)
        var last = document.lineForOffset(end).coerceIn(0, document.lineCount - 1)
        if (last > first && end == document.lineStartOffset(last)) last--
        return if (last >= first) first..last else null
    }

    private fun indentSelectedLines(document: PublicSymbolTextDocument, indent: String): Boolean {
        if (!document.isEditable || indent.isEmpty()) return false
        val range = selectedLineRange(document) ?: return false
        val oldStart = document.selectionStart
        val oldEnd = document.selectionEnd
        for (line in range.last downTo range.first) {
            val start = document.lineStartOffset(line)
            if (!document.replace(start, start, indent)) return false
        }
        document.setSelection(oldStart + indent.length, oldEnd + indent.length * range.count())
        return true
    }

    private fun unindentSelectedLines(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable) return false
        val range = selectedLineRange(document) ?: return false
        val oldStart = document.selectionStart
        val oldEnd = document.selectionEnd
        val firstStart = document.lineStartOffset(range.first)
        var firstRemoved = 0
        var totalRemoved = 0
        for (line in range.last downTo range.first) {
            val start = document.lineStartOffset(line)
            val content = document.lineText(line)
            val count = when {
                content.startsWith("\t") -> 1
                content.startsWith("    ") -> 4
                content.startsWith("   ") -> 3
                content.startsWith("  ") -> 2
                content.startsWith(" ") -> 1
                else -> 0
            }
            if (count > 0) {
                if (line == range.first) firstRemoved = count
                totalRemoved += count
                if (!document.replace(start, start + count, "")) return false
            }
        }
        if (totalRemoved == 0) return false
        val startShift = minOf(firstRemoved, (oldStart - firstStart).coerceAtLeast(0))
        document.setSelection(
            (oldStart - startShift).coerceAtLeast(0),
            (oldEnd - totalRemoved).coerceAtLeast(oldStart - startShift)
        )
        return true
    }

    private fun reorderSelectedLines(document: PublicSymbolTextDocument, reverse: Boolean): Boolean {
        if (!document.isEditable || document.textLength > MAX_DOCUMENT_WIDE_CHARS ||
            document.lineCount > MAX_DOCUMENT_WIDE_LINES
        ) return false
        val range = selectedLineRange(document) ?: return false
        if (range.count() < 2) return false
        val original = range.map(document::lineText)
        val reordered = if (reverse) original.reversed() else original.sorted()
        if (original == reordered) return false
        for (offset in range.count() - 1 downTo 0) {
            val line = range.first + offset
            if (!document.replace(
                    document.lineStartOffset(line),
                    document.lineEndOffset(line),
                    reordered[offset]
                )
            ) return false
        }
        return true
    }

    private fun trimDocumentLeading(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable || document.textLength > MAX_DOCUMENT_WIDE_CHARS ||
            document.lineCount > MAX_DOCUMENT_WIDE_LINES
        ) return false
        val oldStart = document.selectionStart
        val oldEnd = document.selectionEnd
        var startShift = 0
        var endShift = 0
        var changed = false
        for (line in document.lineCount - 1 downTo 0) {
            val content = document.lineText(line)
            val count = content.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) content.length else it }
            if (count == 0) continue
            val start = document.lineStartOffset(line)
            if (start < oldStart) startShift += minOf(count, oldStart - start)
            if (start < oldEnd) endShift += minOf(count, oldEnd - start)
            if (!document.replace(start, start + count, "")) return false
            changed = true
        }
        if (changed) {
            document.setSelection(
                (oldStart - startShift).coerceIn(0, document.textLength),
                (oldEnd - endShift).coerceIn(0, document.textLength)
            )
        }
        return changed
    }

    private fun ensureFinalNewline(document: PublicSymbolTextDocument, payload: String?): Boolean {
        if (!document.isEditable || document.textLength == 0) return false
        val last = document.charAt(document.textLength - 1)
        if (last == '\n' || last == '\r') return false
        val separator = when (payload) {
            "\r\n" -> "\r\n"
            "\r" -> "\r"
            else -> "\n"
        }
        val oldLength = document.textLength
        val oldStart = document.selectionStart
        val oldEnd = document.selectionEnd
        if (!document.replace(oldLength, oldLength, separator)) return false
        document.setSelection(
            if (oldStart == oldLength) oldLength + separator.length else oldStart,
            if (oldEnd == oldLength) oldLength + separator.length else oldEnd
        )
        return true
    }

    private fun moveToLineBoundary(document: PublicSymbolTextDocument, start: Boolean): Boolean {
        val line = currentLine(document)
        val offset = if (start) document.lineStartOffset(line) else document.lineEndOffset(line)
        document.setSelection(offset, offset)
        return true
    }

    private fun moveWord(document: PublicSymbolTextDocument, backwards: Boolean): Boolean {
        var offset = document.cursorOffset.coerceIn(0, document.textLength)
        if (backwards) {
            while (offset > 0 && !isWordCharacter(document.charAt(offset - 1))) offset--
            while (offset > 0 && isWordCharacter(document.charAt(offset - 1))) offset--
        } else {
            while (offset < document.textLength && !isWordCharacter(document.charAt(offset))) offset++
            while (offset < document.textLength && isWordCharacter(document.charAt(offset))) offset++
        }
        document.setSelection(offset, offset)
        return true
    }

    private fun deleteWord(document: PublicSymbolTextDocument, backwards: Boolean): Boolean {
        if (!document.isEditable) return false
        val caret = document.cursorOffset.coerceIn(0, document.textLength)
        val boundary = wordBoundary(document, caret, backwards)
        val start = minOf(caret, boundary)
        val end = maxOf(caret, boundary)
        if (start == end) return false
        if (!document.replace(start, end, "")) return false
        document.setSelection(start, start)
        return true
    }

    private fun selectWord(document: PublicSymbolTextDocument): Boolean {
        if (document.textLength == 0) return false
        var caret = document.cursorOffset.coerceIn(0, document.textLength)
        if (caret == document.textLength || (caret < document.textLength && !isWordCharacter(document.charAt(caret)))) {
            if (caret == 0 || !isWordCharacter(document.charAt(caret - 1))) return false
            caret--
        }
        if (!isWordCharacter(document.charAt(caret))) return false
        var start = caret
        var end = caret + 1
        while (start > 0 && isWordCharacter(document.charAt(start - 1))) start--
        while (end < document.textLength && isWordCharacter(document.charAt(end))) end++
        document.setSelection(start, end)
        return true
    }

    private fun wordBoundary(document: PublicSymbolTextDocument, caret: Int, backwards: Boolean): Int {
        var offset = caret
        if (backwards) {
            while (offset > 0 && !isWordCharacter(document.charAt(offset - 1))) offset--
            while (offset > 0 && isWordCharacter(document.charAt(offset - 1))) offset--
        } else {
            while (offset < document.textLength && !isWordCharacter(document.charAt(offset))) offset++
            while (offset < document.textLength && isWordCharacter(document.charAt(offset))) offset++
        }
        return offset
    }

    private fun trimDocument(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable ||
            document.textLength > MAX_DOCUMENT_WIDE_CHARS ||
            document.lineCount > MAX_DOCUMENT_WIDE_LINES
        ) return false
        var changed = false
        for (line in document.lineCount - 1 downTo 0) {
            val content = document.lineText(line)
            val trimmedLength = content.trimEnd().length
            if (trimmedLength < content.length) {
                val end = document.lineEndOffset(line)
                if (!document.replace(document.lineStartOffset(line) + trimmedLength, end, "")) return false
                changed = true
            }
        }
        return changed
    }

    private fun removeBlankLines(document: PublicSymbolTextDocument): Boolean {
        if (!document.isEditable || document.textLength > MAX_DOCUMENT_WIDE_CHARS ||
            document.lineCount > MAX_DOCUMENT_WIDE_LINES
        ) return false
        var changed = false
        for (line in document.lineCount - 1 downTo 0) {
            if (document.lineCount <= 1) break
            if (document.lineText(line).isBlank()) {
                if (!deleteLine(document, line)) return false
                changed = true
            }
        }
        return changed
    }

    private fun setCaret(document: PublicSymbolTextDocument, offset: Int): Boolean {
        val safe = offset.coerceIn(0, document.textLength)
        document.setSelection(safe, safe)
        return true
    }

    private fun toggleCase(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(if (character.isLowerCase()) character.uppercaseChar() else character.lowercaseChar())
        }
    }

    private fun isWordCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '_'
}
