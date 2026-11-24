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
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import android.content.Context
import com.zerostudio.idea.widget.editor.symbolinput.v2.R
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup

/**
 * Full first-run symbol set, ported from the V1 fallback configuration.
 *
 * Keep the old short/long action semantics here: arrow long-press moves to the
 * corresponding line/document boundary, paired delimiters retain their paired
 * long-press insertion, and the manager entry remains a dedicated V2 action.
 */
object SymbolDrawerDefaults {
    fun createGroups(): List<SymbolGroup> = createGroups("Common")

    fun createGroups(context: Context): List<SymbolGroup> =
        createGroups(context.getString(R.string.symbol_drawer_default_group_common))

    fun createCommonGroup(groupTitle: String = "Common"): SymbolGroup =
        SymbolGroup("commonlyUsed", groupTitle, listOf(
            entry(1, "←", SymbolAction.MoveCursor(CursorTarget.LEFT),
                SymbolAction.MoveCursor(CursorTarget.LINE_START)),
            entry(2, "↑", SymbolAction.MoveCursor(CursorTarget.UP),
                SymbolAction.MoveCursor(CursorTarget.DOCUMENT_START)),
            entry(3, "→", SymbolAction.MoveCursor(CursorTarget.RIGHT),
                SymbolAction.MoveCursor(CursorTarget.LINE_END)),
            entry(4, "\"", SymbolAction.InsertText("\"")),
            entry(5, "'", SymbolAction.InsertText("'")),
            entry(6, ".", SymbolAction.InsertText(".")),
            entry(7, ",", SymbolAction.InsertText(",")),
            entry(8, "/", SymbolAction.InsertText("/")),
            entry(9, "//", SymbolAction.InsertText("//")),
            entry(10, ":", SymbolAction.InsertText(":")),
            entry(11, ";", SymbolAction.InsertText(";")),
            entry(12, "↓", SymbolAction.MoveCursor(CursorTarget.DOWN),
                SymbolAction.MoveCursor(CursorTarget.DOCUMENT_END)),
            entry(13, "#", SymbolAction.InsertText("#")),
            entry(14, "+", SymbolAction.InsertText("+")),
            entry(15, "-", SymbolAction.InsertText("-")),
            entry(16, "*", SymbolAction.InsertText("*")),
            entry(17, "=", SymbolAction.InsertText("=")),
            entry(18, "|", SymbolAction.InsertText("|")),
            entry(19, "~", SymbolAction.InsertText("~")),
            entry(20, "(", SymbolAction.InsertText("(")),
            entry(21, ")", SymbolAction.InsertText(")")),
            entry(22, "(\"", SymbolAction.InsertText("(\"")),
            entry(23, "\")", SymbolAction.InsertText("\")")),
            entry(24, "{", SymbolAction.InsertText("{")),
            entry(25, "}", SymbolAction.InsertText("}")),
            entry(26, "()", SymbolAction.InsertText("()"), SymbolAction.InsertText("()")),
            entry(27, "[]", SymbolAction.InsertText("[]"), SymbolAction.InsertText("[]")),
            entry(28, "{}", SymbolAction.InsertText("{}"), SymbolAction.InsertText("{}")),
            entry(29, "<", SymbolAction.InsertText("<")),
            entry(30, ">", SymbolAction.InsertText(">")),
            entry(31, "\\", SymbolAction.InsertText("\\")),
            entry(32, "$", SymbolAction.InsertText("\$\$")),
            entry(33, "&", SymbolAction.InsertText("&")),
            entry(34, "/*", SymbolAction.InsertText("/**")),
            entry(35, "*/", SymbolAction.InsertText("*/")),
            entry(36, "\n", SymbolAction.InsertText("\n")),
            entry(37, "\t", SymbolAction.InsertText("\t")),
            entry(38, "\\", SymbolAction.InsertText("\\")),
            entry(39, "settings", SymbolAction.OpenManager)
        ))

    fun createCustomGroup(context: Context? = null): SymbolGroup =
        SymbolGroup("custom", "Custom", listOf(
            entry(1001, "Search", SymbolAction.Extension("legacy.action.100", null)),
            entry(1002, "Copy", SymbolAction.Clipboard(ClipboardOperation.COPY)),
            entry(1003, "Cut", SymbolAction.Clipboard(ClipboardOperation.CUT)),
            entry(1004, "Paste", SymbolAction.Clipboard(ClipboardOperation.PASTE))
        ))

    fun createSymbolsGroup(context: Context? = null): SymbolGroup =
        SymbolGroup("symbols", "Symbols", listOf(
            entry(1005, "(", SymbolAction.InsertText("("), SymbolAction.InsertText("()")),
            entry(1006, ")", SymbolAction.InsertText(")")),
            entry(1007, "[", SymbolAction.InsertText("["), SymbolAction.InsertText("[]")),
            entry(1008, "]", SymbolAction.InsertText("]")),
            entry(1009, "{", SymbolAction.InsertText("{"), SymbolAction.InsertText("{}")),
            entry(1010, "}", SymbolAction.InsertText("}")),
            entry(1011, "\"", SymbolAction.InsertText("\"")),
            entry(1012, "'", SymbolAction.InsertText("'")),
            entry(1013, "//", SymbolAction.InsertText("//")),
            entry(1014, "/*", SymbolAction.InsertText("/*")),
            entry(1015, "*/", SymbolAction.InsertText("*/")),
            entry(1016, ";", SymbolAction.InsertText(";")),
            entry(1017, ":", SymbolAction.InsertText(":")),
            entry(1018, ",", SymbolAction.InsertText(",")),
            entry(1019, ".", SymbolAction.InsertText(".")),
            entry(1020, "=", SymbolAction.InsertText("=")),
            entry(1021, "+", SymbolAction.InsertText("+")),
            entry(1022, "-", SymbolAction.InsertText("-")),
            entry(1023, "*", SymbolAction.InsertText("*")),
            entry(1024, "/", SymbolAction.InsertText("/")),
            entry(1025, "<", SymbolAction.InsertText("<")),
            entry(1026, ">", SymbolAction.InsertText(">")),
            entry(1027, "!", SymbolAction.InsertText("!")),
            entry(1028, "?", SymbolAction.InsertText("?")),
            entry(1029, "&", SymbolAction.InsertText("&")),
            entry(1030, "|", SymbolAction.InsertText("|")),
            entry(1031, "\\", SymbolAction.InsertText("\\")),
            entry(1032, "$", SymbolAction.InsertText("$")),
            entry(1033, "@", SymbolAction.InsertText("@")),
            entry(1034, "#", SymbolAction.InsertText("#")),
            entry(1035, "←", SymbolAction.MoveCursor(CursorTarget.LEFT), SymbolAction.MoveCursor(CursorTarget.LINE_START)),
            entry(1036, "↑", SymbolAction.MoveCursor(CursorTarget.UP), SymbolAction.MoveCursor(CursorTarget.DOCUMENT_START)),
            entry(1037, "→", SymbolAction.MoveCursor(CursorTarget.RIGHT), SymbolAction.MoveCursor(CursorTarget.LINE_END)),
            entry(1038, "↓", SymbolAction.MoveCursor(CursorTarget.DOWN), SymbolAction.MoveCursor(CursorTarget.DOCUMENT_END)),
            entry(1039, "↵", SymbolAction.InsertText("\n")),
            entry(1040, "↹", SymbolAction.InsertText("\t")),
            entry(1041, "⤺", SymbolAction.EditorCommand("history.undo")),
            entry(1042, "⤻", SymbolAction.EditorCommand("history.redo")),
            entry(1043, "⚙", SymbolAction.OpenManager)
        ))

    fun createDefaultGroups(context: Context? = null): List<SymbolGroup> {
        val commonTitle = context?.getString(R.string.symbol_drawer_default_group_common) ?: "Common"
        return listOf(
            createCustomGroup(context),
            createSymbolsGroup(context),
            createCommonGroup(commonTitle)
        )
    }

    private fun createGroups(groupTitle: String): List<SymbolGroup> = listOf(
        createCommonGroup(groupTitle)
    )

    private fun entry(
        id: Long,
        label: String,
        shortAction: SymbolAction,
        longAction: SymbolAction? = null
    ) = SymbolEntry(
        id = id,
        label = label,
        shortAction = shortAction,
        longAction = longAction,
        legacyShortActionId = legacyId(shortAction),
        legacyShortText = (shortAction as? SymbolAction.InsertText)?.template,
        legacyLongActionId = longAction?.let(::legacyId),
        legacyLongText = (longAction as? SymbolAction.InsertText)?.template
    )

    private fun legacyId(action: SymbolAction): Int = when (action) {
        is SymbolAction.InsertText -> 0
        is SymbolAction.MoveCursor -> when (action.target) {
            CursorTarget.LEFT -> 18
            CursorTarget.RIGHT -> 19
            CursorTarget.UP -> 20
            CursorTarget.DOWN -> 21
            CursorTarget.LINE_START -> 16
            CursorTarget.LINE_END -> 17
            CursorTarget.DOCUMENT_START -> 23
            CursorTarget.DOCUMENT_END -> 24
        }
        SymbolAction.OpenManager -> 22
        is SymbolAction.Clipboard -> when (action.operation) {
            ClipboardOperation.COPY -> 28
            ClipboardOperation.CUT -> 29
            ClipboardOperation.PASTE -> 30
        }
        is SymbolAction.EditorCommand -> when (action.commandId) {
            "history.undo" -> 55
            "history.redo" -> 56
            else -> 0
        }
        is SymbolAction.Extension -> if (action.extensionId == "legacy.action.100") 100 else if (action.extensionId == MpManagerFindReplaceEnvelope.EXTENSION_ID) 101 else 0
        else -> 0
    }
}
