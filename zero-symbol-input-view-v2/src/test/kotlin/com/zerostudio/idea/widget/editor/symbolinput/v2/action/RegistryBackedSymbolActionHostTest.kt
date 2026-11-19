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

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryBackedSymbolActionHostTest {
    private class FakeHost : SymbolActionHost {
        val fallbackCalls = mutableListOf<String>()
        override fun insertText(template: String) = false
        override fun replaceSelection(text: String) = false
        override fun surroundSelection(prefix: String, suffix: String) = false
        override fun moveCursor(target: CursorTarget) = false
        override fun delete(kind: DeleteKind) = false
        override fun transformSelection(transform: SelectionTransform) = false
        override fun executeEditorCommand(commandId: String, payload: String?) = false
        override fun performClipboard(operation: ClipboardOperation) = false
        override fun openManager() = false
        override fun executeExtension(extensionId: String, payload: String?): Boolean {
            fallbackCalls += "${extensionId}:${payload}"
            return true
        }
    }

    @Test fun registeredExtensionRunsBeforeHostFallback() {
        val delegate = FakeHost()
        val registry = SymbolActionRegistry<SymbolActionHost>()
        val calls = mutableListOf<String>()
        registry.register(SymbolActionId.extension("sample.insert")) { host, payload ->
            calls += "${payload}:${host === delegate}"
            true
        }

        val host = RegistryBackedSymbolActionHost(delegate, registry)
        assertTrue(host.executeExtension("sample.insert", "hello"))
        assertEquals(listOf("hello:true"), calls)
        assertTrue(delegate.fallbackCalls.isEmpty())
    }

    @Test fun registeredFalseDoesNotRunFallbackTwice() {
        val delegate = FakeHost()
        val registry = SymbolActionRegistry<SymbolActionHost>()
        registry.register(SymbolActionId.extension("sample.decline")) { _, _ -> false }

        assertFalse(RegistryBackedSymbolActionHost(delegate, registry)
            .executeExtension("sample.decline", null))
        assertTrue(delegate.fallbackCalls.isEmpty())
    }

    @Test fun unknownExtensionFallsBackToOriginalHost() {
        val delegate = FakeHost()
        val host = RegistryBackedSymbolActionHost(delegate, SymbolActionRegistry())

        assertTrue(host.executeExtension("plugin.unknown", "data"))
        assertEquals(listOf("plugin.unknown:data"), delegate.fallbackCalls)
    }

    @Test fun legacyNumericRegistrationResolvesWithoutChangingV1Id() {
        val delegate = FakeHost()
        val registry = SymbolActionRegistry<SymbolActionHost>()
        val calls = mutableListOf<Int>()
        registry.register(947) { host, payload ->
            assertTrue(host === delegate)
            calls += payload?.toInt() ?: -1
            true
        }

        assertTrue(RegistryBackedSymbolActionHost(delegate, registry)
            .executeExtension("legacy.action.947", "947"))
        assertEquals(listOf(947), calls)
        assertTrue(delegate.fallbackCalls.isEmpty())
    }


    @Test fun negativeAndOverflowLegacyIdsRemainSafeExtensionFallbacks() {
        val delegate = FakeHost()
        val host = RegistryBackedSymbolActionHost(delegate, SymbolActionRegistry())

        assertTrue(host.executeExtension("legacy.action.-1", "negative"))
        assertTrue(host.executeExtension("legacy.action.2147483648", "overflow"))

        assertEquals(
            listOf(
                "legacy.action.-1:negative",
                "legacy.action.2147483648:overflow"
            ),
            delegate.fallbackCalls
        )
    }

    @Test fun executorDispatchesExtensionThroughRegistryBackedHostExactlyOnce() {
        val delegate = FakeHost()
        val registry = SymbolActionRegistry<SymbolActionHost>()
        val calls = mutableListOf<String>()
        registry.register(SymbolActionId.extension("sample.executor")) { host, payload ->
            assertTrue(host === delegate)
            calls += "handler:$payload"
            true
        }

        val result = SymbolActionExecutor().execute(
            SymbolAction.Extension("sample.executor", "from-executor"),
            RegistryBackedSymbolActionHost(delegate, registry)
        )

        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, result)
        assertEquals(listOf("handler:from-executor"), calls)
        assertTrue(delegate.fallbackCalls.isEmpty())
    }

    @Test fun executorPreservesLegacyNumericActionDispatchAndDeclineSemantics() {
        val delegate = FakeHost()
        val registry = SymbolActionRegistry<SymbolActionHost>()
        var calls = 0
        registry.register(947) { _, payload ->
            calls++
            assertEquals("legacy-payload", payload)
            false
        }

        val result = SymbolActionExecutor().execute(
            SymbolAction.Extension("legacy.action.947", "legacy-payload"),
            RegistryBackedSymbolActionHost(delegate, registry)
        )

        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Unsupported(SymbolAction.Extension(
            "legacy.action.947",
            "legacy-payload"
        )), result)
        assertEquals(1, calls)
        assertTrue(delegate.fallbackCalls.isEmpty())
    }

    @Test fun customFallbackPreservesExistingAppExtensionDispatch() {
        val delegate = FakeHost()
        val registry = SymbolActionRegistry<SymbolActionHost>()
        val fallbackCalls = mutableListOf<String>()
        val host = RegistryBackedSymbolActionHost(delegate, registry) { id, payload ->
            fallbackCalls += "${id}:${payload}"
            true
        }

        assertTrue(host.executeExtension("app.previous", "payload"))
        assertEquals(listOf("app.previous:payload"), fallbackCalls)
        assertTrue(delegate.fallbackCalls.isEmpty())
    }

    @Test fun unknownLegacyNumericIdRetainsOriginalFallback() {
        val delegate = FakeHost()
        assertTrue(RegistryBackedSymbolActionHost(
            delegate,
            SymbolActionRegistry()
        ).executeExtension("legacy.action.948", "legacy"))
        assertEquals(listOf("legacy.action.948:legacy"), delegate.fallbackCalls)
    }
}
