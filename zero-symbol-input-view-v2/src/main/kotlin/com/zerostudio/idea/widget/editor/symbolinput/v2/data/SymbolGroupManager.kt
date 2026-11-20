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

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import java.util.UUID

/**
 * Repository-backed command model for a future manager UI.
 * All operations produce new immutable snapshots and persist once per command.
 */
class SymbolGroupManager(
    private val repository: SymbolGroupRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val newSymbolId: () -> Long = { UUID.randomUUID().mostSignificantBits }
) {
    // Detach the manager snapshot from repositories that reuse a mutable list instance.
    var groups: List<SymbolGroup> = repository.loadGroups().toList()
        private set

    init {
        validateSnapshot(groups)
    }

    fun reload() {
        // Read and validate before publishing: an external/corrupt repository
        // snapshot must never replace the last known-good in-memory state.
        val snapshot = repository.loadGroups().toList()
        validateSnapshot(snapshot)
        groups = snapshot
    }

    fun addGroup(title: String, index: Int = groups.size): SymbolGroup {
        require(title.isNotBlank()) { "Group title cannot be blank" }
        val group = SymbolGroup(nextUniqueGroupId(), title.trim(), emptyList())
        val next = groups.toMutableList().apply { add(index.coerceIn(0, size), group) }
        commit(next)
        return group
    }

    fun replaceAll(value: List<SymbolGroup>) {
        commit(value.toList())
    }

    fun duplicateGroup(groupId: String, index: Int = groups.size): SymbolGroup {
        val source = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        // Symbol IDs are globally stable within the repository, not merely within one group.
        // Seed from every group so a duplicated group cannot reuse an ID owned by its source.
        val usedSymbolIds = groups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .mapTo(mutableSetOf()) { it.id }
        val duplicate = source.copy(
            id = nextUniqueGroupId(),
            title = "${source.title} copy",
            symbols = source.symbols.map { symbol ->
                symbol.copy(id = nextUniqueSymbolId(usedSymbolIds)).also { usedSymbolIds += it.id }
            }
        )
        val next = groups.toMutableList().apply { add(index.coerceIn(0, size), duplicate) }
        commit(next)
        return duplicate
    }

    fun renameGroup(groupId: String, title: String) {
        require(title.isNotBlank()) { "Group title cannot be blank" }
        require(groups.any { it.id == groupId }) { "Group not found: $groupId" }
        commit(groups.map { if (it.id == groupId) it.copy(title = title.trim()) else it })
    }

    fun removeGroup(groupId: String) {
        // UI commands may outlive a stale selection (for example after an
        // external reload). Treat removal of an already-missing group as an
        // idempotent no-op and, importantly, do not issue a redundant write.
        if (groups.none { it.id == groupId }) return
        commit(groups.filterNot { it.id == groupId })
    }

    fun moveGroup(fromIndex: Int, toIndex: Int) {
        require(fromIndex in groups.indices && toIndex in groups.indices)
        val next = groups.toMutableList()
        val moved = next.removeAt(fromIndex)
        next.add(toIndex, moved)
        commit(next)
    }

    fun addSymbol(groupId: String, symbol: SymbolEntry, index: Int? = null) {
        updateGroup(groupId) { group ->
            val usedIds = groups.asSequence()
                .flatMap { it.symbols.asSequence() }
                .mapTo(mutableSetOf()) { it.id }
            val safeSymbol = if (symbol.id == 0L) {
                symbol.copy(id = nextUniqueSymbolId(usedIds))
            } else {
                require(groups.asSequence()
                    .filterNot { it.id == groupId }
                    .flatMap { it.symbols.asSequence() }
                    .none { it.id == symbol.id }) {
                    "Symbol ID ${symbol.id} is already used by another group"
                }
                symbol
            }
            val items = group.symbols.toMutableList()
            items.add((index ?: items.size).coerceIn(0, items.size), safeSymbol)
            group.copy(symbols = items)
        }
    }

    fun updateSymbol(groupId: String, symbol: SymbolEntry) {
        updateGroup(groupId) { group ->
            require(group.symbols.any { it.id == symbol.id }) {
                "Symbol not found: ${symbol.id}"
            }
            require(groups.asSequence()
                .filterNot { it.id == groupId }
                .flatMap { it.symbols.asSequence() }
                .none { it.id == symbol.id }) {
                "Symbol ID ${symbol.id} is already used by another group"
            }
            group.copy(symbols = group.symbols.map { if (it.id == symbol.id) symbol else it })
        }
    }

    fun removeSymbol(groupId: String, symbolId: Long) {
        updateGroup(groupId) { group ->
            require(group.symbols.any { it.id == symbolId }) {
                "Symbol not found: $symbolId"
            }
            group.copy(symbols = group.symbols.filterNot { it.id == symbolId })
        }
    }

    fun moveSymbol(groupId: String, fromIndex: Int, toIndex: Int) {
        updateGroup(groupId) { group ->
            require(fromIndex in group.symbols.indices && toIndex in group.symbols.indices)
            val items = group.symbols.toMutableList()
            val moved = items.removeAt(fromIndex)
            items.add(toIndex, moved)
            group.copy(symbols = items)
        }
    }

    fun copySymbol(sourceGroupId: String, symbolId: Long, targetGroupId: String): SymbolEntry =
        copySymbols(sourceGroupId, setOf(symbolId), targetGroupId).single()

    /** Copy a set of symbols in one validated, single-write transaction. */
    fun copySymbols(sourceGroupId: String, symbolIds: Set<Long>, targetGroupId: String): List<SymbolEntry> {
        require(symbolIds.isNotEmpty()) { "No symbols selected" }
        val source = groups.firstOrNull { it.id == sourceGroupId }
            ?: error("Source group not found: $sourceGroupId")
        val target = groups.firstOrNull { it.id == targetGroupId }
            ?: error("Target group not found: $targetGroupId")
        val selected = source.symbols.filter { it.id in symbolIds }
        require(selected.size == symbolIds.size) { "One or more selected symbols no longer exist" }
        val usedIds = groups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .mapTo(mutableSetOf()) { it.id }
        val copies = selected.map { symbol ->
            symbol.copy(id = nextUniqueSymbolId(usedIds)).also { usedIds += it.id }
        }
        commit(groups.map { if (it.id == targetGroupId) it.copy(symbols = it.symbols + copies) else it })
        return copies
    }

    /** Remove multiple symbols atomically; no write occurs for stale selection IDs. */
    fun removeSymbols(groupId: String, symbolIds: Set<Long>): Int {
        require(symbolIds.isNotEmpty()) { "No symbols selected" }
        val group = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        require(group.symbols.count { it.id in symbolIds } == symbolIds.size) {
            "One or more selected symbols no longer exist"
        }
        commit(groups.map { current ->
            if (current.id == groupId) current.copy(symbols = current.symbols.filterNot { it.id in symbolIds })
            else current
        })
        return symbolIds.size
    }

    /** Move multiple symbols atomically while retaining IDs when the target has no collision. */
    fun moveSymbolsToGroup(sourceGroupId: String, symbolIds: Set<Long>, targetGroupId: String) {
        require(sourceGroupId != targetGroupId) { "Source and target groups must differ" }
        require(symbolIds.isNotEmpty()) { "No symbols selected" }
        val source = groups.firstOrNull { it.id == sourceGroupId }
            ?: error("Source group not found: $sourceGroupId")
        val target = groups.firstOrNull { it.id == targetGroupId }
            ?: error("Target group not found: $targetGroupId")
        val moved = source.symbols.filter { it.id in symbolIds }
        require(moved.size == symbolIds.size) { "One or more selected symbols no longer exist" }
        // IDs owned by the moving source selection become free after removal.
        // Every other group still reserves its IDs, including groups unrelated
        // to the destination.
        // Only the selected source entries are released. Unselected entries
        // in the same source group still own their IDs and must remain reserved.
        val usedIds = groups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .map { it.id }
            .filterNot { it in symbolIds }
            .toMutableSet()
        val normalized = moved.map { symbol ->
            if (symbol.id in usedIds) symbol.copy(id = nextUniqueSymbolId(usedIds))
            else symbol.also { usedIds += it.id }
        }
        commit(groups.map { group ->
            when (group.id) {
                sourceGroupId -> group.copy(symbols = group.symbols.filterNot { it.id in symbolIds })
                targetGroupId -> group.copy(symbols = group.symbols + normalized)
                else -> group
            }
        })
    }

    fun moveSymbolToGroup(sourceGroupId: String, symbolId: Long, targetGroupId: String) =
        moveSymbolsToGroup(sourceGroupId, setOf(symbolId), targetGroupId)

    /** Reorder by stable IDs; the supplied order must contain every symbol exactly once. */
    fun reorderSymbols(groupId: String, orderedIds: List<Long>) {
        val group = groups.firstOrNull { it.id == groupId } ?: error("Group not found: $groupId")
        require(orderedIds.size == group.symbols.size &&
            orderedIds.toSet().size == orderedIds.size &&
            orderedIds.toSet() == group.symbols.mapTo(mutableSetOf()) { it.id }
        ) { "Order must contain every symbol ID exactly once" }
        val byId = group.symbols.associateBy { it.id }
        commit(groups.map { current ->
            if (current.id == groupId) current.copy(symbols = orderedIds.map { byId.getValue(it) })
            else current
        })
    }

    private fun nextUniqueGroupId(): String {
        val usedIds = groups.mapTo(mutableSetOf()) { it.id }
        repeat(MAX_ID_GENERATION_ATTEMPTS) {
            val candidate = newId()
            if (candidate.isNotBlank() && candidate !in usedIds) return candidate
        }
        error("Unable to generate a unique group ID")
    }

    private fun nextUniqueSymbolId(usedIds: Set<Long>): Long {
        repeat(MAX_ID_GENERATION_ATTEMPTS) {
            val candidate = newSymbolId()
            if (candidate != 0L && candidate !in usedIds) return candidate
        }
        error("Unable to generate a unique symbol ID")
    }

    private fun updateGroup(groupId: String, transform: (SymbolGroup) -> SymbolGroup) {
        require(groups.any { it.id == groupId }) { "Group not found: $groupId" }
        commit(groups.map { if (it.id == groupId) transform(it) else it })
    }

    private companion object {
        const val MAX_ID_GENERATION_ATTEMPTS = 32
    }

    private fun validateSnapshot(snapshot: List<SymbolGroup>) {
        require(snapshot.all { it.id.isNotBlank() && it.title.isNotBlank() }) {
            "Group IDs and titles must not be blank"
        }
        require(snapshot.map { it.id }.distinct().size == snapshot.size) {
            "Group IDs must be unique"
        }
        val allSymbolIds = mutableSetOf<Long>()
        snapshot.forEach { group ->
            require(group.symbols.all { it.id != 0L && it.label.isNotEmpty() }) {
                "Symbol IDs and labels must not be blank in group ${group.id}"
            }
            group.symbols.forEach { symbol ->
                require(allSymbolIds.add(symbol.id)) {
                    "Symbol ID ${symbol.id} is duplicated across groups"
                }
            }
        }
    }

    private fun commit(next: List<SymbolGroup>) {
        // Treat commands that produce an identical immutable snapshot as no-ops.
        // This avoids redundant disk writes from repeated taps, stale UI events,
        // or reorder requests that leave the existing order unchanged.
        if (next == groups) return
        validateSnapshot(next)
        // Persist first: a failed write must not publish an in-memory snapshot
        // that the repository cannot reproduce after reload.
        val snapshot = next.toList()
        repository.saveGroups(snapshot)
        groups = snapshot
    }
}
