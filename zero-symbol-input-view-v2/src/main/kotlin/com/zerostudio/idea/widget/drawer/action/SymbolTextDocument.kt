/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.drawer.action

/**
 * Editor-neutral text document contract.
 *
 * The drawer/action engine never receives an editor, view, controller, syntax
 * tree, or editor-core object. A host supplies this small adapter for the
 * currently focused document. The adapter may be backed by any editor, a
 * plain text field, an in-memory buffer, or a remote document implementation.
 *
 * All offsets are UTF-16 offsets (the indexing convention used by JVM String).
 * [lineEndOffset] excludes the line separator. Implementations must keep the
 * document and selection state coherent when replace() succeeds.
 */
interface SymbolTextDocument {
    val isEditable: Boolean
    val textLength: Int
    val lineCount: Int
    val cursorOffset: Int
    val selectionStart: Int
    val selectionEnd: Int

    fun lineForOffset(offset: Int): Int
    fun lineStartOffset(line: Int): Int
    fun lineEndOffset(line: Int): Int
    fun lineText(line: Int): String
    fun textBetween(start: Int, end: Int): String
    fun charAt(offset: Int): Char

    /** Replace UTF-16 range [start, end) and return false if rejected. */
    fun replace(start: Int, end: Int, replacement: String): Boolean

    /** Set a selection using UTF-16 offsets; equal offsets place the caret. */
    fun setSelection(start: Int, end: Int)

    /** Optional host transaction hooks. Override when the editor supports atomic batches. */
    fun beginEditTransaction() = Unit
    fun endEditTransaction(commit: Boolean) = Unit

    fun selectedText(): String {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, textLength)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, textLength)
        return textBetween(start, end)
    }

    fun positionForOffset(offset: Int): TextDocumentPosition {
        val safeOffset = offset.coerceIn(0, textLength)
        if (lineCount <= 0) return TextDocumentPosition(0, safeOffset)
        val line = lineForOffset(safeOffset).coerceIn(0, lineCount - 1)
        return TextDocumentPosition(line, (safeOffset - lineStartOffset(line)).coerceAtLeast(0))
    }

    fun offsetForPosition(position: TextDocumentPosition): Int {
        if (lineCount <= 0) return 0
        val line = position.line.coerceIn(0, lineCount - 1)
        val start = lineStartOffset(line).coerceIn(0, textLength)
        val end = lineEndOffset(line).coerceIn(start, textLength)
        return (start + position.column.coerceAtLeast(0)).coerceAtMost(end)
    }

    fun setCursorPosition(position: TextDocumentPosition) {
        val offset = offsetForPosition(position)
        setSelection(offset, offset)
    }

    /**
     * Applies non-overlapping edits from right to left so earlier offsets remain
     * stable. The default implementation is best-effort; hosts needing atomic
     * semantics should override this method or implement transaction hooks.
     */
    fun applyEdits(edits: List<TextDocumentEdit>): Boolean {
        if (!isEditable) return false
        if (edits.isEmpty()) return true
        val ordered = edits.sortedByDescending { it.start }
        var previousStart = textLength + 1
        for (edit in ordered) {
            if (edit.start < 0 || edit.end < edit.start || edit.end > textLength ||
                edit.end > previousStart || edit.start == previousStart
            ) return false
            previousStart = edit.start
        }
        beginEditTransaction()
        var finished = false
        return try {
            for (edit in ordered) {
                if (!replace(edit.start, edit.end, edit.replacement)) {
                    endEditTransaction(false)
                    finished = true
                    return false
                }
            }
            endEditTransaction(true)
            finished = true
            true
        } catch (error: Throwable) {
            if (!finished) runCatching { endEditTransaction(false) }
            throw error
        }
    }
}

data class TextDocumentPosition(val line: Int, val column: Int) {
    init { require(line >= 0 && column >= 0) }
}

/** UTF-16 edit range; end is exclusive. */
data class TextDocumentEdit(val start: Int, val end: Int, val replacement: String) {
    init { require(start >= 0 && end >= start) }
}
