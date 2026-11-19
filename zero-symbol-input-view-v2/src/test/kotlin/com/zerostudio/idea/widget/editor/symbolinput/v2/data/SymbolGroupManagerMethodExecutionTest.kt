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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct execution coverage for every public SymbolGroupManager command. */
class SymbolGroupManagerMethodExecutionTest {
    private class Repository(initial: List<SymbolGroup>) : SymbolGroupRepository {
        var stored = initial
        var saveCount = 0
        var failNextSave = false
        override fun loadGroups(): List<SymbolGroup> = stored
        override fun saveGroups(groups: List<SymbolGroup>) {
            if (failNextSave) {
                failNextSave = false
                error("synthetic persistence failure")
            }
            stored = groups.toList()
            saveCount++
        }
    }

    private fun entry(id: Long, label: String = "symbol-$id") =
        SymbolEntry(id, label, SymbolAction.InsertText(label))

    private fun initial() = listOf(
        SymbolGroup("a", "Alpha", listOf(entry(1), entry(2))),
        SymbolGroup("b", "Beta", listOf(entry(3))),
        SymbolGroup("c", "Gamma", emptyList())
    )

    @Test
    fun reloadAndReplaceAllInvokeRepositoryAndPublishSnapshots() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository)
        val external = listOf(SymbolGroup("external", "External", listOf(entry(9))))
        repository.stored = external

        manager.reload()
        assertEquals(external, manager.groups)

        val replacement = listOf(SymbolGroup("replacement", "Replacement", listOf(entry(10))))
        manager.replaceAll(replacement)
        assertEquals(replacement, manager.groups)
        assertEquals(replacement, repository.stored)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun moveGroupMoveSymbolAndReorderSymbolsChangePersistedOrder() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository)

        manager.moveGroup(0, 2)
        assertEquals(listOf("b", "c", "a"), manager.groups.map { it.id })

        manager.moveSymbol("a", 0, 1)
        assertEquals(listOf(2L, 1L), manager.groups.single { it.id == "a" }.symbols.map { it.id })

        manager.reorderSymbols("a", listOf(1L, 2L))
        assertEquals(listOf(1L, 2L), manager.groups.single { it.id == "a" }.symbols.map { it.id })
        assertEquals(manager.groups, repository.stored)
        assertEquals(3, repository.saveCount)
    }

    @Test
    fun copyAndMoveMultipleSymbolsAreAtomicAndPreserveActionPayloads() {
        var nextId = 100L
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository, newSymbolId = { ++nextId })

        val copies = manager.copySymbols("a", linkedSetOf(1L, 2L), "b")
        assertEquals(listOf("symbol-1", "symbol-2"), copies.map { it.label })
        assertEquals(listOf(101L, 102L), copies.map { it.id })
        assertTrue(copies.zip(listOf(entry(1), entry(2))).all { (copy, source) ->
            copy.shortAction == source.shortAction
        })

        manager.moveSymbolsToGroup("a", setOf(2L), "c")
        assertEquals(listOf(1L), manager.groups.single { it.id == "a" }.symbols.map { it.id })
        assertEquals(listOf(2L), manager.groups.single { it.id == "c" }.symbols.map { it.id })
        assertEquals(listOf(3L, 101L, 102L), manager.groups.single { it.id == "b" }.symbols.map { it.id })
        assertEquals(manager.groups, repository.stored)
        assertEquals(2, repository.saveCount)
    }

    @Test
    fun duplicateIdsAndInvalidOrdersAreRejectedBeforePersistence() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository)
        val before = manager.groups
        val writesBefore = repository.saveCount

        assertTrue(runCatching {
            manager.replaceAll(listOf(
                SymbolGroup("duplicate", "One", emptyList()),
                SymbolGroup("duplicate", "Two", emptyList())
            ))
        }.isFailure)
        assertTrue(runCatching { manager.moveGroup(-1, 0) }.isFailure)
        assertTrue(runCatching { manager.moveSymbol("a", 0, 99) }.isFailure)
        assertTrue(runCatching { manager.reorderSymbols("a", listOf(1L, 1L)) }.isFailure)
        assertTrue(runCatching { manager.reorderSymbols("a", listOf(1L)) }.isFailure)

        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(writesBefore, repository.saveCount)
    }

    @Test
    fun addDuplicateRenameAndRemoveGroupExecuteAndPersist() {
        var groupId = 0
        var symbolId = 10L
        val repository = Repository(initial())
        val manager = SymbolGroupManager(
            repository,
            newId = { "created-${++groupId}" },
            newSymbolId = { ++symbolId }
        )

        val added = manager.addGroup("  Delta  ", index = 1)
        assertEquals("created-1", added.id)
        assertEquals("Delta", added.title)
        assertEquals(listOf("a", "created-1", "b", "c"), manager.groups.map { it.id })

        val duplicated = manager.duplicateGroup("a", index = 0)
        assertEquals("created-2", duplicated.id)
        assertEquals("Alpha copy", duplicated.title)
        assertEquals(listOf(11L, 12L), duplicated.symbols.map { it.id })
        assertEquals(listOf("created-2", "a", "created-1", "b", "c"), manager.groups.map { it.id })

        manager.renameGroup("created-1", "  Delta renamed  ")
        assertEquals("Delta renamed", manager.groups.single { it.id == "created-1" }.title)

        manager.removeGroup("created-2")
        assertFalse(manager.groups.any { it.id == "created-2" })
        assertEquals(manager.groups, repository.stored)
        assertEquals(4, repository.saveCount)
    }

    @Test
    fun updateSymbolCannotIntroduceAnIdOwnedByAnotherGroup() {
        val repository = Repository(listOf(
            SymbolGroup("a", "Alpha", listOf(entry(1))),
            SymbolGroup("b", "Beta", listOf(entry(2)))
        ))
        val manager = SymbolGroupManager(repository)
        val before = manager.groups

        assertTrue(runCatching {
            manager.updateSymbol("a", entry(2, "collision"))
        }.isFailure)
        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun addSymbolAllocatesIdsAgainstTheWholeRepositoryAndRejectsForeignExplicitIds() {
        var nextId = 1L
        val repository = Repository(listOf(
            SymbolGroup("a", "Alpha", listOf(entry(1))),
            SymbolGroup("b", "Beta", listOf(entry(2)))
        ))
        val manager = SymbolGroupManager(repository, newSymbolId = { ++nextId })

        manager.addSymbol("a", entry(0, "generated"))
        assertEquals(3L, manager.groups.first { it.id == "a" }.symbols.last().id)

        val before = manager.groups
        assertTrue(runCatching { manager.addSymbol("a", entry(2, "collision")) }.isFailure)
        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun copyingAndMovingSymbolsAvoidIdsOwnedByUnrelatedGroups() {
        var nextId = 2L
        val repository = Repository(listOf(
            SymbolGroup("a", "Alpha", listOf(entry(1))),
            SymbolGroup("b", "Beta", emptyList()),
            SymbolGroup("c", "Gamma", listOf(entry(2)))
        ))
        val manager = SymbolGroupManager(repository, newSymbolId = { ++nextId })

        val copied = manager.copySymbol("a", 1L, "b")
        assertEquals(3L, copied.id)

        // The generator first proposes ID 2 (owned by C), then retries with 3.
        // Moving source ID 1 is safe because the source entry is removed atomically.
        manager.moveSymbolToGroup("a", 1L, "b")
        assertEquals(listOf(3L, 1L), manager.groups.first { it.id == "b" }.symbols.map { it.id })
        assertEquals(listOf(2L), manager.groups.first { it.id == "c" }.symbols.map { it.id })
        assertEquals(manager.groups, repository.stored)
    }

    @Test
    fun movingSubsetPreservesUnselectedSourceSymbolsAndStableIds() {
        val candidates = ArrayDeque(listOf(3L))
        val repository = Repository(listOf(
            SymbolGroup("source", "Source", listOf(entry(1), entry(2))),
            SymbolGroup("target", "Target", emptyList())
        ))
        val manager = SymbolGroupManager(repository, newSymbolId = { candidates.removeFirst() })

        manager.moveSymbolsToGroup("source", setOf(1L), "target")

        assertEquals(listOf(2L), manager.groups.single { it.id == "source" }.symbols.map { it.id })
        assertEquals(listOf(1L), manager.groups.single { it.id == "target" }.symbols.map { it.id })
        assertEquals(3L, candidates.first())
        assertEquals(manager.groups, repository.stored)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun movingSubsetPreservesUnselectedSourceIdsAndDoesNotWriteOnInvalidSelection() {
        val repository = Repository(listOf(
            SymbolGroup("source", "Source", listOf(entry(1), entry(2))),
            SymbolGroup("target", "Target", emptyList())
        ))
        val manager = SymbolGroupManager(repository)
        val before = manager.groups

        assertTrue(runCatching {
            manager.moveSymbolsToGroup("source", setOf(1L, 99L), "target")
        }.isFailure)
        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun duplicateGroupGeneratesSymbolIdsUniqueAcrossAllGroups() {
        val repository = Repository(listOf(
            SymbolGroup("source", "Source", listOf(entry(1))),
            SymbolGroup("other", "Other", listOf(entry(2)))
        ))
        val candidates = ArrayDeque(listOf(2L, 3L))
        val manager = SymbolGroupManager(
            repository,
            newId = { "duplicate" },
            newSymbolId = { candidates.removeFirst() }
        )

        val duplicate = manager.duplicateGroup("source")

        assertEquals(listOf(3L), duplicate.symbols.map { it.id })
        assertEquals(1L, manager.groups.first { it.id == "source" }.symbols.single().id)
        assertEquals(2L, manager.groups.first { it.id == "other" }.symbols.single().id)
        assertEquals(manager.groups, repository.stored)
    }

    @Test
    fun addUpdateRemoveAndCopySymbolExecuteWithStableIds() {
        var nextId = 40L
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository, newSymbolId = { ++nextId })

        val generated = entry(0, "generated")
        manager.addSymbol("a", generated, index = 1)
        val inserted = manager.groups.single { it.id == "a" }.symbols[1]
        assertEquals(41L, inserted.id)
        assertEquals("generated", inserted.label)

        val changed = inserted.copy(label = "renamed", shortAction = SymbolAction.ReplaceSelection("new"))
        manager.updateSymbol("a", changed)
        assertEquals(changed, manager.groups.single { it.id == "a" }.symbols[1])

        val copied = manager.copySymbol("a", 1L, "b")
        assertEquals(42L, copied.id)
        assertEquals("symbol-1", copied.label)
        assertEquals(SymbolAction.InsertText("symbol-1"), copied.shortAction)

        manager.removeSymbol("a", 2L)
        assertFalse(manager.groups.single { it.id == "a" }.symbols.any { it.id == 2L })
        assertTrue(manager.groups.single { it.id == "b" }.symbols.any { it.id == copied.id })
        assertEquals(manager.groups, repository.stored)
        assertEquals(4, repository.saveCount)
    }

    @Test
    fun commandsThatDoNotChangeTheSnapshotDoNotPersist() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository)
        val before = manager.groups

        manager.moveGroup(0, 0)
        manager.moveSymbol("a", 0, 0)
        manager.reorderSymbols("a", listOf(1L, 2L))
        manager.updateSymbol("a", entry(1))
        manager.renameGroup("a", "Alpha")
        manager.replaceAll(before)

        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun failedRepositoryWriteDoesNotPublishInMemoryChanges() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository, newId = { "never-published" })
        val before = manager.groups
        repository.failNextSave = true

        assertTrue(runCatching { manager.addGroup("Will fail") }.isFailure)
        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun invalidCommandsAreRejectedWithoutPartialWrites() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository)
        val before = manager.groups

        assertTrue(runCatching { manager.addGroup("  ") }.isFailure)
        assertTrue(runCatching { manager.renameGroup("a", " ") }.isFailure)
        assertTrue(runCatching { manager.renameGroup("missing", "Valid") }.isFailure)
        assertTrue(runCatching { manager.duplicateGroup("missing") }.isFailure)
        assertTrue(runCatching { manager.updateSymbol("a", entry(999)) }.isFailure)
        assertTrue(runCatching { manager.removeSymbol("a", 999) }.isFailure)
        assertTrue(runCatching { manager.copySymbols("a", emptySet(), "b") }.isFailure)
        assertTrue(runCatching { manager.removeSymbols("a", emptySet()) }.isFailure)
        assertTrue(runCatching { manager.moveSymbolsToGroup("a", setOf(1L), "a") }.isFailure)
        assertTrue(runCatching { manager.moveSymbolToGroup("a", 999L, "b") }.isFailure)

        assertEquals(before, manager.groups)
        assertEquals(before, repository.stored)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun removeUnknownGroupIsAnIdempotentNoOp() {
        val repository = Repository(initial())
        val manager = SymbolGroupManager(repository)
        manager.removeGroup("not-present")
        assertEquals(initial(), manager.groups)
        assertEquals(0, repository.saveCount)
        assertFalse(manager.groups.isEmpty())
    }
}
