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

package com.zerostudio.idea.widget.editor.symbolinput.v2.state

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerGeometryTest {
    private val config = DrawerConfig(columns = 4, collapsedRows = 2, cellHeightDp = 30)

    @Test
    fun collapsedHeightUsesConfiguredRows() {
        assertEquals(70, DrawerGeometry.collapsedHeightPx(config, 1f))
    }

    @Test
    fun collapsedHeightTracksConfiguredRowsInsteadOfAViewHardcodedRowCount() {
        listOf(1, 2, 3, 5, 8).forEach { rows ->
            val configured = config.copy(collapsedRows = rows)
            assertEquals(
                DrawerGeometry.gridHeightPx(rows, configured, 1f),
                DrawerGeometry.collapsedHeightPx(configured, 1f)
            )
        }
    }

    @Test
    fun expandedHeightUsesLargestGroup() {
        val groups = listOf(group("a", 5), group("b", 9))
        assertEquals(102, DrawerGeometry.expandedHeightPx(groups, config, 1f))
    }

    @Test
    fun minimumTouchTargetContributesToGridHeight() {
        val accessible = config.copy(minimumTouchTargetDp = 48)
        assertEquals(106, DrawerGeometry.collapsedHeightPx(accessible, 1f))
    }

    @Test
    fun expandedHeightRespectsConfiguredMaximum() {
        val limited = config.copy(maxExpandedHeightDp = 120)
        val groups = listOf(group("large", 100))
        assertEquals(94, DrawerGeometry.expandedHeightPx(groups, limited, 1f))
    }

    @Test
    fun indicatorRevealIsClampedAndInterpolated() {
        assertEquals(0f, DrawerGeometry.indicatorReveal(0f), 0.0001f)
        assertEquals(0.315f, DrawerGeometry.indicatorReveal(0.315f), 0.0001f)
        assertEquals(1f, DrawerGeometry.indicatorReveal(1f), 0.0001f)
    }

    private fun group(id: String, count: Int) = SymbolGroup(
        id = id,
        title = id,
        symbols = (0 until count).map {
            SymbolEntry(it.toLong(), it.toString(), SymbolAction.InsertText(it.toString()))
        }
    )
}
