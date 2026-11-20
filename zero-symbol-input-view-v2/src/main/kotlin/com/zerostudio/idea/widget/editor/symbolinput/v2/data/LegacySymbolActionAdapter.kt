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

package com.zerostudio.idea.widget.editor.symbolinput.v2.data

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.LegacySymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction

/**
 * Compatibility boundary used only while importing first-generation data.
 * Unknown legacy IDs remain explicit host extensions instead of being silently
 * discarded or becoming V2's runtime action protocol.
 */
object LegacySymbolActionAdapter {
    fun convert(legacy: LegacySymbolAction): SymbolAction {
        val payload = legacy.payload
        return when (legacy.type) {
            0 -> SymbolAction.InsertText(payload.orEmpty())
            // IDs 1/2 are retained in imported data, but the current V1
            // SymbolActionExecutor has no dispatch branches for them. Keep them
            // as unresolved legacy extensions rather than inventing clipboard
            // behavior during migration.
            1, 2 -> SymbolAction.Extension("legacy.action.${legacy.type}", payload)
            // MP Manager's V1 copies/cuts the current line via the clipboard.
            3 -> SymbolAction.Delete(DeleteKind.CURRENT_LINE)
            34 -> SymbolAction.EditorCommand("line.cut")
            4 -> SymbolAction.EditorCommand("line.clear")
            7 -> SymbolAction.TransformSelection(SelectionTransform.UPPERCASE)
            8 -> SymbolAction.TransformSelection(SelectionTransform.LOWERCASE)
            9 -> SymbolAction.EditorCommand("editor.indent")
            11 -> SymbolAction.EditorCommand("editor.toggleLineComment")
            16 -> SymbolAction.MoveCursor(CursorTarget.LINE_START)
            17 -> SymbolAction.MoveCursor(CursorTarget.LINE_END)
            18 -> SymbolAction.MoveCursor(CursorTarget.LEFT)
            19 -> SymbolAction.MoveCursor(CursorTarget.RIGHT)
            20 -> SymbolAction.MoveCursor(CursorTarget.UP)
            21 -> SymbolAction.MoveCursor(CursorTarget.DOWN)
            22 -> SymbolAction.OpenManager
            23 -> SymbolAction.MoveCursor(CursorTarget.DOCUMENT_START)
            24 -> SymbolAction.MoveCursor(CursorTarget.DOCUMENT_END)
            25 -> SymbolAction.EditorCommand("selection.selectAll")
            28, 52 -> SymbolAction.Clipboard(ClipboardOperation.COPY)
            29, 53 -> SymbolAction.Clipboard(ClipboardOperation.CUT)
            30, 54 -> SymbolAction.Clipboard(ClipboardOperation.PASTE)
            32 -> SymbolAction.EditorCommand("editor.format")
            33 -> SymbolAction.EditorCommand("line.copy")
            35 -> SymbolAction.EditorCommand("line.duplicate")
            36 -> SymbolAction.EditorCommand("line.replace", payload)
            37 -> SymbolAction.InsertText(payload ?: "\n")
            38 -> SymbolAction.InsertText(payload ?: "\t")
            39 -> SymbolAction.EditorCommand("editor.unindent")
            in 40..51 -> SymbolAction.InsertText(payload ?: "<F${legacy.type - 39}>")
            55 -> SymbolAction.EditorCommand("history.undo")
            56 -> SymbolAction.EditorCommand("history.redo")
            else -> SymbolAction.Extension("legacy.action.${legacy.type}", payload)
        }
    }
}
