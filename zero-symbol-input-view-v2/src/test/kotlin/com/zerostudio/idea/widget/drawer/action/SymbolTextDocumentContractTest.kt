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
package com.zerostudio.idea.widget.drawer.action

import org.junit.Assert.*
import org.junit.Test

class SymbolTextDocumentContractTest {
    private class Buffer(var value: String) : SymbolTextDocument {
        private var caret = 0
        override val isEditable = true
        override val textLength get() = value.length
        override val lineCount get() = value.split("\n").size
        override val cursorOffset get() = caret
        override val selectionStart get() = caret
        override val selectionEnd get() = caret
        private fun starts(): List<Int> = buildList {
            add(0)
            value.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
        }
        override fun lineForOffset(offset: Int): Int =
            starts().indexOfLast { it <= offset.coerceIn(0, value.length) }.coerceAtLeast(0)
        override fun lineStartOffset(line: Int) = starts()[line]
        override fun lineEndOffset(line: Int): Int {
            val start = lineStartOffset(line)
            val next = starts().getOrNull(line + 1) ?: value.length
            return if (next > start && value[next - 1] == '\n') next - 1 else next
        }
        override fun lineText(line: Int) = textBetween(lineStartOffset(line), lineEndOffset(line))
        override fun textBetween(start: Int, end: Int) = value.substring(start, end)
        override fun charAt(offset: Int) = value[offset]
        override fun replace(start: Int, end: Int, replacement: String): Boolean {
            value = value.replaceRange(start, end, replacement)
            caret = start + replacement.length
            return true
        }
        override fun setSelection(start: Int, end: Int) { caret = end }
    }

    @Test fun convertsBetweenUtf16OffsetsAndLineColumn() {
        val document = Buffer("abc\nxy")
        assertEquals(TextDocumentPosition(1, 1), document.positionForOffset(5))
        assertEquals(5, document.offsetForPosition(TextDocumentPosition(1, 1)))
        assertEquals(6, document.offsetForPosition(TextDocumentPosition(1, 99)))
    }

    @Test fun appliesNonOverlappingEditsFromRightToLeft() {
        val document = Buffer("abcdef")
        assertTrue(document.applyEdits(listOf(
            TextDocumentEdit(1, 2, "XX"),
            TextDocumentEdit(4, 6, "!")
        )))
        assertEquals("aXXcd!", document.value)
    }


    @Test fun rejectsMultipleInsertionsAtSameOffsetAsAmbiguous() {
        val document = Buffer("abcd")
        assertFalse(document.applyEdits(listOf(
            TextDocumentEdit(2, 2, "X"),
            TextDocumentEdit(2, 2, "Y")
        )))
        assertEquals("abcd", document.value)
    }

    @Test fun rejectsOverlappingEditsBeforeMutatingDocument() {
        val document = Buffer("abcdef")
        assertFalse(document.applyEdits(listOf(
            TextDocumentEdit(1, 4, "x"),
            TextDocumentEdit(3, 5, "y")
        )))
        assertEquals("abcdef", document.value)
    }
}
