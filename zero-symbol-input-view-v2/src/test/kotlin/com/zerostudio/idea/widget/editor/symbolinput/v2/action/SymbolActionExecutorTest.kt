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

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolActionExecutorTest {
    private open class RecordingHost : SymbolActionHost {
        val calls = mutableListOf<String>()
        var lastExtensionId: String? = null
        var lastExtensionPayload: String? = null
        override fun insertText(template: String): Boolean { calls += "insert:$template"; return true }
        override fun replaceSelection(text: String) = true
        override fun surroundSelection(prefix: String, suffix: String) = true
        override fun moveCursor(target: CursorTarget) = true
        override fun delete(kind: DeleteKind) = true
        override fun transformSelection(transform: SelectionTransform) = true
        override fun executeEditorCommand(commandId: String, payload: String?) = true
        override fun performClipboard(operation: ClipboardOperation) = true
        override fun openManager() = true
        override fun executeExtension(extensionId: String, payload: String?): Boolean {
            lastExtensionId = extensionId
            lastExtensionPayload = payload
            return true
        }
    }


    @Test fun clipboardActionsDelegateCopyCutAndPasteToTheHostInOrder() {
        val host = object : RecordingHost() {
            override fun performClipboard(operation: ClipboardOperation): Boolean {
                calls += "clipboard:$operation"
                return true
            }
        }
        val result = SymbolActionExecutor().execute(
            SymbolAction.Composite(listOf(
                SymbolAction.Clipboard(ClipboardOperation.COPY),
                SymbolAction.Clipboard(ClipboardOperation.CUT),
                SymbolAction.Clipboard(ClipboardOperation.PASTE)
            )),
            host
        )
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, result)
        assertEquals(
            listOf("clipboard:COPY", "clipboard:CUT", "clipboard:PASTE"),
            host.calls
        )
    }

    @Test fun executesCompositeInOrder() {
        val host = RecordingHost()
        val result = SymbolActionExecutor().execute(
            SymbolAction.Composite(listOf(SymbolAction.InsertText("a"), SymbolAction.InsertText("b"))), host
        )
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, result)
        assertEquals(listOf("insert:a", "insert:b"), host.calls)
    }


    @Test fun extensionActionsAcceptNamespacedContextIdentifiers() {
        val host = RecordingHost()
        val action = SymbolAction.Extension("com.example/editor:paste", "text")
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, SymbolActionExecutor().execute(action, host))
        assertEquals("com.example/editor:paste", host.lastExtensionId)
        assertEquals("text", host.lastExtensionPayload)
    }

    @Test fun rejectsInvalidCommandIds() {
        val result = SymbolActionExecutor().execute(SymbolAction.EditorCommand("../unsafe"), RecordingHost())
        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
    }

    @Test fun validatesCompositeBeforeExecutingAnyStep() {
        val host = RecordingHost()
        val result = SymbolActionExecutor().execute(
            SymbolAction.Composite(listOf(
                SymbolAction.InsertText("must-not-run"),
                SymbolAction.EditorCommand("../invalid")
            )), host
        )
        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(host.calls.isEmpty())
    }

    @Test fun rejectsOversizedEditorPayload() {
        val result = SymbolActionExecutor(maxTextLength = 3).execute(
            SymbolAction.EditorCommand("editor.custom", "four"), RecordingHost()
        )
        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
    }

    @Test fun reportsPartialCompositeFailure() {
        val host = object : RecordingHost() {
            override fun insertText(template: String): Boolean {
                calls += template
                return template != "bad"
            }
        }
        val result = SymbolActionExecutor().execute(
            SymbolAction.Composite(listOf(
                SymbolAction.InsertText("ok"),
                SymbolAction.InsertText("bad"),
                SymbolAction.InsertText("never")
            )), host
        )
        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.PartialFailure)
        assertEquals(1, (result as com.zerostudio.idea.widget.drawer.action.SymbolActionResult.PartialFailure).completedActions)
        assertEquals(listOf("ok", "bad"), host.calls)
    }
}
