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
import org.junit.Test

class SymbolGroupStorageCodecTest {
    @Test
    fun decodeRepairsDuplicateIdsAcrossGroupsWithoutChangingActionsOrStableIds() {
        val groups = listOf(
            SymbolGroup("one", "One", listOf(
                SymbolEntry(10L, "A", SymbolAction.InsertText("a")),
                SymbolEntry(11L, "B", SymbolAction.InsertText("b"))
            )),
            SymbolGroup("two", "Two", listOf(
                SymbolEntry(10L, "C", SymbolAction.InsertText("c")),
                SymbolEntry(1L, "Existing low ID", SymbolAction.InsertText("reserved")),
                SymbolEntry(12L, "D", SymbolAction.InsertText("d"))
            ))
        )

        // Simulate an early V2 snapshot written before repository-wide ID uniqueness.
        val earlyV2 = SymbolGroupBundleCodec.encode(groups)
        val decoded = SymbolGroupStorageCodec.decode(earlyV2)

        assertEquals(listOf(10L, 11L), decoded[0].symbols.map { it.id })
        assertEquals(listOf(2L, 1L, 12L), decoded[1].symbols.map { it.id })
        assertEquals(
            listOf("a", "b", "c", "reserved", "d"),
            decoded.flatMap { it.symbols }.map { (it.shortAction as SymbolAction.InsertText).template }
        )
        assertEquals(5, decoded.flatMap { it.symbols }.map { it.id }.distinct().size)
    }

    @Test
    fun encodeRejectsDuplicateIdsAcrossDifferentGroups() {
        val groups = listOf(
            SymbolGroup("one", "One", listOf(SymbolEntry(7L, "A", SymbolAction.InsertText("a")))),
            SymbolGroup("two", "Two", listOf(SymbolEntry(7L, "B", SymbolAction.InsertText("b"))))
        )

        assertEquals(true, runCatching { SymbolGroupStorageCodec.encode(groups) }.isFailure)
    }

    @Test
    fun decodeKeepsAllAlreadyUniqueIdsUnchanged() {
        val groups = listOf(
            SymbolGroup("one", "One", listOf(SymbolEntry(41L, "A", SymbolAction.InsertText("a")))),
            SymbolGroup("two", "Two", listOf(SymbolEntry(99L, "B", SymbolAction.InsertText("b"))))
        )

        val decoded = SymbolGroupStorageCodec.decode(SymbolGroupStorageCodec.encode(groups))

        assertEquals(listOf(41L, 99L), decoded.flatMap { it.symbols }.map { it.id })
    }
}
