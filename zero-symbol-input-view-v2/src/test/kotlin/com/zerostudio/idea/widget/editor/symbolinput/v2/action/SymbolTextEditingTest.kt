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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolTextEditingTest {
    @Test fun macroOffsetsAreMeasuredAfterTextAndSelectionSubstitution() {
        assertEquals(
            SymbolTextMacroExpansion("abcdef", 1, 3),
            SymbolTextEditing.expandMacro("a\$Sbc\$Edef", "")
        )
        assertEquals(
            SymbolTextMacroExpansion("aXY", 1, 3),
            SymbolTextEditing.expandMacro("a\$S\$T\$E", "XY")
        )
        assertEquals(
            SymbolTextMacroExpansion("[long]", 6, 6),
            SymbolTextEditing.expandMacro("[\$T]\$S\$E", "long")
        )
    }

    @Test fun macroKeepsEscapedDollarAndUnknownTokensLiteral() {
        assertEquals(
            SymbolTextMacroExpansion("\$Sx", 2, 3),
            SymbolTextEditing.expandMacro("\$\$S\$Sx\$E", "")
        )
        assertEquals(
            SymbolTextMacroExpansion("before\$Qafter", 13, 13),
            SymbolTextEditing.expandMacro("before\$Qafter", "")
        )
    }

    @Test fun macroWithoutMarkersLeavesCursorAtExpandedOutputEnd() {
        assertEquals(
            SymbolTextMacroExpansion("a<selected>b", 12, 12),
            SymbolTextEditing.expandMacro("a\$Tb", "<selected>")
        )
    }

    @Test fun macroExpandsLineWordIndentAndWhitespaceTokens() {
        val expansion = SymbolTextEditing.expandMacro(
            "[\$W]\$N\$TAB\$I\$L\$S\$E",
            selectedText = "selected",
            currentLineText = "    val value = 1",
            currentWordText = "value",
            lineSeparator = "\r\n",
            currentIndent = "    "
        )
        assertEquals("[value]\r\n\t        val value = 1", expansion.text)
        assertEquals(expansion.text.length, expansion.insertionOffset)
        assertEquals(expansion.text.length, expansion.selectionEndOffset)
    }

    @Test fun selectedTextCanBeTransformedByShortAndLongDirectives() {
        assertEquals("HELLO", SymbolTextEditing.expandMacro("\$U", "hello").text)
        assertEquals("hello", SymbolTextEditing.expandMacro("\$V", "HELLO").text)
        assertEquals("olleh", SymbolTextEditing.expandMacro("\$R", "hello").text)
        assertEquals("hello", SymbolTextEditing.expandMacro("\$TRIM", "  hello  ").text)
        assertEquals("Hello", SymbolTextEditing.expandMacro("\$CAP", "hello").text)
        assertEquals("HELLO", SymbolTextEditing.expandMacro("\$UP", "hello").text)
        assertEquals("hello", SymbolTextEditing.expandMacro("\$LOW", "HELLO").text)
        assertEquals("olleh", SymbolTextEditing.expandMacro("\$REV", "hello").text)
    }

    @Test fun cursorMarkerPlacesCaretWithoutSelectingText() {
        assertEquals(
            SymbolTextMacroExpansion("ab", 1, 1),
            SymbolTextEditing.expandMacro("a\$Cb", "")
        )
    }

    @Test fun backwardAndForwardDeletionKeepSurrogatePairsIntact() {
        val emoji = "A😀B"
        assertEquals(SymbolTextRange(1, 3),
            SymbolTextEditing.codePointDeletionRange(emoji, 3, backwards = true))
        assertEquals(SymbolTextRange(1, 3),
            SymbolTextEditing.codePointDeletionRange(emoji, 1, backwards = false))
        assertEquals(SymbolTextRange(0, 1),
            SymbolTextEditing.codePointDeletionRange(emoji, 1, backwards = true))
        assertEquals(SymbolTextRange(3, 4),
            SymbolTextEditing.codePointDeletionRange(emoji, 3, backwards = false))
        assertNull(SymbolTextEditing.codePointDeletionRange(emoji, 0, backwards = true))
        assertNull(SymbolTextEditing.codePointDeletionRange(emoji, emoji.length, backwards = false))
    }

    @Test fun duplicateLineInsertionHandlesFinalAndNonFinalLines() {
        assertEquals(
            SymbolTextInsertion(0, 3, "\nabc"),
            SymbolTextEditing.duplicateLineInsertion(0, 1, 3, "abc")
        )
        assertEquals(
            SymbolTextInsertion(1, 0, "abc\n"),
            SymbolTextEditing.duplicateLineInsertion(0, 2, 3, "abc")
        )
    }
}
