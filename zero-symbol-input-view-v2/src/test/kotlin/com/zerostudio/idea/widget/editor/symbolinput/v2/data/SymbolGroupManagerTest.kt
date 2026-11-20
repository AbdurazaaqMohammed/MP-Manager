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

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolGroupManagerTest {
    private class MemoryRepository(initial: List<SymbolGroup>) : SymbolGroupRepository {
        var stored = initial
        var failWrites = false
        override fun loadGroups() = stored
        override fun saveGroups(groups: List<SymbolGroup>) {
            check(!failWrites) { "simulated persistence failure" }
            stored = groups
        }
    }

    @Test
    fun moveSymbolBetweenGroupsPreservesSymbolIdentityAndAction() {
        val symbol = SymbolEntry(7L, "{}", SymbolAction.InsertText("{}"))
        val repository = MemoryRepository(
            listOf(
                SymbolGroup("a", "A", listOf(symbol)),
                SymbolGroup("b", "B", emptyList())
            )
        )
        val manager = SymbolGroupManager(repository)

        manager.moveSymbolToGroup("a", 7L, "b")

        assertTrue(manager.groups[0].symbols.isEmpty())
        assertEquals(symbol, manager.groups[1].symbols.single())
    }

    @Test
    fun duplicateGroupCreatesNewGroupAndSymbolIdentities() {
        val source = SymbolGroup("a", "A", listOf(SymbolEntry(1L, "x", SymbolAction.InsertText("x"))))
        val repository = MemoryRepository(listOf(source))
        val manager = SymbolGroupManager(repository, newId = { "copy" }, newSymbolId = { 2L })

        val duplicate = manager.duplicateGroup("a")

        assertEquals("copy", duplicate.id)
        assertEquals(2L, duplicate.symbols.single().id)
        assertEquals(2, repository.stored.size)
    }

    @Test
    fun replaceAllPersistsValidatedSnapshot() {
        val repository = MemoryRepository(emptyList())
        val manager = SymbolGroupManager(repository)
        val replacement = listOf(SymbolGroup("new", "New", emptyList()))

        manager.replaceAll(replacement)

        assertEquals(replacement, repository.stored)
    }

    @Test
    fun groupReorderingPersistsNewOrder() {
        val repository = MemoryRepository(
            listOf(
                SymbolGroup("a", "A", emptyList()),
                SymbolGroup("b", "B", emptyList())
            )
        )
        val manager = SymbolGroupManager(repository)

        manager.moveGroup(0, 1)

        assertEquals(listOf("b", "a"), repository.stored.map { it.id })
    }
    @Test
    fun reorderSymbolsPersistsOneValidatedOrderAndSkipsIdenticalWrites() {
        val initial = SymbolGroup(
            "group",
            "Group",
            listOf(
                SymbolEntry(1L, "one", SymbolAction.InsertText("1")),
                SymbolEntry(2L, "two", SymbolAction.InsertText("2")),
                SymbolEntry(3L, "three", SymbolAction.InsertText("3"))
            )
        )
        var writes = 0
        val repository = object : SymbolGroupRepository {
            var stored = listOf(initial)
            override fun loadGroups() = stored
            override fun saveGroups(groups: List<SymbolGroup>) {
                writes++
                stored = groups
            }
        }
        val manager = SymbolGroupManager(repository)

        manager.reorderSymbols("group", listOf(3L, 1L, 2L))
        manager.reorderSymbols("group", listOf(3L, 1L, 2L))

        assertEquals(1, writes)
        assertEquals(listOf(3L, 1L, 2L), manager.groups.single().symbols.map { it.id })
    }
    @Test
    fun duplicateGroupRetriesCollidingGroupAndSymbolIds() {
        val source = SymbolGroup(
            "source",
            "Source",
            listOf(
                SymbolEntry(1L, "one", SymbolAction.InsertText("1")),
                SymbolEntry(2L, "two", SymbolAction.InsertText("2"))
            )
        )
        val groupIds = listOf("source", "copy").iterator()
        val symbolIds = listOf(1L, 2L, 3L, 4L).iterator()
        val repository = MemoryRepository(listOf(source))
        val manager = SymbolGroupManager(
            repository,
            newId = { groupIds.next() },
            newSymbolId = { symbolIds.next() }
        )

        val duplicate = manager.duplicateGroup("source")

        assertEquals("copy", duplicate.id)
        assertEquals(listOf(3L, 4L), duplicate.symbols.map { it.id })
        assertEquals(2, repository.stored.size)
    }

    @Test
    fun failedPersistenceDoesNotPublishNewInMemorySnapshot() {
        val original = SymbolGroup("source", "Source", emptyList())
        val repository = MemoryRepository(listOf(original))
        val manager = SymbolGroupManager(repository)
        repository.failWrites = true

        val failure = runCatching { manager.addGroup("New") }

        assertTrue(failure.isFailure)
        assertEquals(listOf(original), manager.groups)
        assertEquals(listOf(original), repository.stored)
    }

    @Test
    fun removingUnknownGroupIsAnIdempotentNoOpWithoutWriting() {
        val original = SymbolGroup("source", "Source", emptyList())
        var writes = 0
        val countingRepository = object : SymbolGroupRepository {
            override fun loadGroups() = listOf(original)
            override fun saveGroups(groups: List<SymbolGroup>) { writes++ }
        }
        val manager = SymbolGroupManager(countingRepository)

        manager.removeGroup("missing")

        assertEquals(0, writes)
        assertEquals(listOf(original), manager.groups)
    }

    @Test
    fun addingAutoIdSymbolRetriesCollisionsWithinItsGroup() {
        val original = SymbolEntry(9L, "existing", SymbolAction.InsertText("old"))
        val repository = MemoryRepository(listOf(SymbolGroup("group", "Group", listOf(original))))
        val generatedIds = listOf(9L, 10L).iterator()
        val manager = SymbolGroupManager(repository, newSymbolId = { generatedIds.next() })

        manager.addSymbol("group", SymbolEntry(0L, "new", SymbolAction.InsertText("new")))

        assertEquals(listOf(9L, 10L), manager.groups.single().symbols.map { it.id })
    }

}
