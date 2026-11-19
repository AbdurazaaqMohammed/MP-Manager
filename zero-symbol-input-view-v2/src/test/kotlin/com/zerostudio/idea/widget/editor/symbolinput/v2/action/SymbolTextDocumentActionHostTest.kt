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

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform
import org.junit.Assert.*
import org.junit.Test

class SymbolTextDocumentActionHostTest {
    private class Document(var value: String) : SymbolTextDocument {
        override var isEditable: Boolean = true
        override val textLength get() = value.length
        override val lineCount get() = value.split("\n").size
        override var cursorOffset: Int = 0
            private set
        override var selectionStart: Int = 0
            private set
        override var selectionEnd: Int = 0
            private set

        override fun lineForOffset(offset: Int): Int =
            value.substring(0, offset.coerceIn(0, value.length)).count { it == '\n' }

        override fun lineStartOffset(line: Int): Int {
            if (line <= 0) return 0
            var current = 0
            repeat(line) {
                val next = value.indexOf('\n', current)
                if (next < 0) return value.length
                current = next + 1
            }
            return current
        }

        override fun lineEndOffset(line: Int): Int {
            val start = lineStartOffset(line)
            val end = value.indexOf('\n', start)
            return if (end < 0) value.length else end
        }

        override fun lineText(line: Int): String =
            value.substring(lineStartOffset(line), lineEndOffset(line))

        override fun textBetween(start: Int, end: Int): String = value.substring(start, end)
        override fun charAt(offset: Int): Char = value[offset]

        override fun replace(start: Int, end: Int, replacement: String): Boolean {
            if (!isEditable || start !in 0..value.length || end !in start..value.length) return false
            value = value.substring(0, start) + replacement + value.substring(end)
            return true
        }

        override fun setSelection(start: Int, end: Int) {
            selectionStart = start.coerceIn(0, value.length)
            selectionEnd = end.coerceIn(0, value.length)
            cursorOffset = selectionEnd
        }
    }

    @Test fun insertTextExpandsMacroAndPlacesSelection() {
        val document = Document("selected")
        document.setSelection(0, 8)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.insertText("before(\$S\$T\$E)"))
        assertEquals("before(selected)", document.value)
        assertEquals(7, document.selectionStart)
        assertEquals(15, document.selectionEnd)
    }

    @Test fun insertTextUsesCurrentLineWordIndentAndDocumentNewlineContext() {
        val document = Document("    val value = 1")
        document.setSelection(10, 10)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.insertText("[\$W]\$N\$TAB\$I\$W"))
        assertEquals("    val va[value]\n\t    valuelue = 1", document.value)
    }

    @Test fun currentLineMacroPreservesItsLeadingIndentation() {
        val document = Document("    val value = 1")
        document.setSelection(10, 10)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.insertText("\$L"))
        assertEquals("    val va    val value = 1lue = 1", document.value)
    }

    @Test fun middleLineInsertionUsesCrLfSeparator() {
        val document = Document("first\r\nlast")
        document.setSelection(2, 2)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.insertText("\$N"))
        assertEquals("fi\r\nrst\r\nlast", document.value)
    }

    @Test fun finalLineInsertionPreservesPreviousCrLfSeparator() {
        val document = Document("first\r\nlast")
        document.setSelection(document.value.length, document.value.length)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.insertText("x\$N"))
        assertEquals("first\r\nlastx\r\n", document.value)
    }

    @Test fun surroundSelectionKeepsInnerTextSelected() {
        val document = Document("hello")
        document.setSelection(1, 4)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.surroundSelection("<", ">"))
        assertEquals("h<ell>o", document.value)
        assertEquals(2, document.selectionStart)
        assertEquals(5, document.selectionEnd)
    }

    @Test fun genericEditorCommandUsesBuiltInTextRegistry() {
        val document = Document("one")
        document.setSelection(3, 3)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.executeEditorCommand("line.duplicate", null))
        assertEquals("one\none", document.value)
    }

    @Test fun selectionTransformUsesDocumentAdapter() {
        val document = Document("hello")
        document.setSelection(1, 4)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.transformSelection(SelectionTransform.UPPERCASE))
        assertEquals("hELLo", document.value)
    }

    @Test fun cursorMovementUsesGenericOffsets() {
        val document = Document("abc")
        document.setSelection(2, 2)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.moveCursor(CursorTarget.LEFT))
        assertEquals(1, document.cursorOffset)
    }
    @Test fun backwardDeleteRemovesWholeSupplementaryCodePoint() {
        val document = Document("a😀b")
        document.setSelection(3, 3)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.delete(com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind.BACKWARD))
        assertEquals("ab", document.value)
    }

    @Test fun deletingFinalCrLfLineRemovesWholeSeparator() {
        val document = Document("first\r\nlast")
        document.setSelection(document.value.length, document.value.length)
        val host = SymbolTextDocumentActionHost(document)
        assertTrue(host.delete(com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind.CURRENT_LINE))
        assertEquals("first", document.value)
    }

}
