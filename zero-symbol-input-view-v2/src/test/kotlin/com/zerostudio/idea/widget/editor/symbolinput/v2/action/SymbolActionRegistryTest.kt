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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolActionRegistryTest {
    @Test fun numericIdsReverseResolveToRegisteredFunctions() {
        val registry = SymbolActionRegistry<String>()
        val calls = mutableListOf<String>()
        val handle = registry.register(54) { context, payload ->
            calls += "$context:paste:$payload"
            true
        }

        val resolved = registry.get(54)
        assertSame(resolved, registry.get(SymbolActionId.legacy(54)))
        assertTrue(requireNotNull(registry.execute(54, "editor-1", "clipboard")))
        assertEquals(listOf("editor-1:paste:clipboard"), calls)

        handle.close()
        handle.close()
        assertNull(registry.get(54))
        assertNull(registry.execute(54, "editor-1"))
    }

    @Test fun namespacesAllowSameValueWithoutCollisions() {
        val registry = SymbolActionRegistry<Unit>()
        registry.register(SymbolActionId("builtin", "paste")) { _, _ -> true }
        registry.register(SymbolActionId("plugin", "paste")) { _, _ -> false }

        assertEquals(true, registry.execute(SymbolActionId("builtin", "paste"), Unit))
        assertEquals(false, registry.execute(SymbolActionId("plugin", "paste"), Unit))
        assertEquals(2, registry.size)
    }

    @Test fun closingReplacedRegistrationDoesNotRemoveNewHandler() {
        val registry = SymbolActionRegistry<Unit>()
        val id = SymbolActionId.extension("sample.action")
        val old = registry.register(id) { _, _ -> false }
        registry.register(id) { _, _ -> true }

        old.close()
        assertTrue(requireNotNull(registry.execute(id, Unit)))
        assertTrue(registry.unregister(id))
        assertFalse(registry.unregister(id))
    }

    @Test fun namespacedContextIdsCanUseUriLikeStableIdentifiers() {
        val id = SymbolActionId.extension("com.example/editor:paste")
        assertEquals("com.example/editor:paste", id.value)
    }


    @Test fun clearRemovesEveryRegisteredHandlerAndAllowsReRegistration() {
        val registry = SymbolActionRegistry<String>()
        val first = SymbolActionId("builtin", "first")
        val second = SymbolActionId("plugin", "second")
        registry.register(first) { _, _ -> true }
        registry.register(second) { _, _ -> false }
        assertEquals(2, registry.size)
        assertEquals(true, registry.execute(first, "ctx"))
        assertEquals(false, registry.execute(second, "ctx"))

        registry.clear()
        assertEquals(0, registry.size)
        assertNull(registry.get(first))
        assertNull(registry.get(second))
        assertNull(registry.execute(first, "ctx"))

        registry.register(first) { context, payload -> context == "ctx" && payload == "again" }
        assertEquals(true, registry.execute(first, "ctx", "again"))
        assertEquals(1, registry.size)
    }

    @Test fun handlerExceptionsPropagateToTheCallingContext() {
        val registry = SymbolActionRegistry<Unit>()
        val failure = IllegalStateException("handler failure")
        registry.register(SymbolActionId("builtin", "throws")) { _, _ -> throw failure }

        val actual = runCatching {
            registry.execute(SymbolActionId("builtin", "throws"), Unit)
        }.exceptionOrNull()

        assertSame(failure, actual)
    }

    @Test fun invalidIdsAreRejected() {
        assertTrue(runCatching { SymbolActionId("", "x") }.isFailure)
        assertTrue(runCatching { SymbolActionId("plugin", "has space") }.isFailure)
    }
}
