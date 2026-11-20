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
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolDrawerDockingLayoutResolverTest {
    @Test fun settingsDrivenResolverHonorsManualBottomAndSideEdge() {
        val window = window(1000, 700)
        val bottom = SymbolDrawerDockingLayoutResolver.resolve(
            settings = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings(
                largeScreenLayoutMode = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.LargeScreenLayoutMode.BOTTOM
            ),
            window = window,
            bottomCollapsedHeightDp = 54f,
            bottomExpandedHeightDp = 286f
        )
        val left = SymbolDrawerDockingLayoutResolver.resolve(
            settings = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings(
                largeScreenLayoutMode = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.LargeScreenLayoutMode.SIDE,
                sideDockEdge = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SideDockEdge.LEFT
            ),
            window = window,
            bottomCollapsedHeightDp = 54f,
            bottomExpandedHeightDp = 286f
        )
        assertEquals(SymbolDrawerDocking.BOTTOM, bottom.docking)
        assertEquals(SymbolDrawerDocking.LEFT, left.docking)
    }

    @Test fun bottomModePreservesLegacyHeightAnchorsExactly() {
        val spec = SymbolDrawerDockingLayoutResolver.resolve(
            docking = SymbolDrawerDocking.BOTTOM,
            window = window(1200, 800),
            bottomCollapsedHeightDp = 54f,
            bottomExpandedHeightDp = 286f
        )
        assertEquals(54f, spec.collapsedExtentDp, 0f)
        assertEquals(286f, spec.expandedExtentDp, 0f)
        assertEquals(1200f, spec.crossAxisExtentDp, 0f)
        assertEquals(0f, spec.sideRailWidthDp, 0f)
    }

    @Test fun sideModeUsesFullAvailableWindowHeightAndKeepsEditorSpace() {
        val spec = SymbolDrawerDockingLayoutResolver.resolve(
            docking = SymbolDrawerDocking.LEFT,
            window = window(1000, 700),
            bottomCollapsedHeightDp = 54f,
            bottomExpandedHeightDp = 286f
        )
        assertEquals(700f, spec.crossAxisExtentDp, 0f)
        assertEquals(72f, spec.collapsedExtentDp, 0f)
        assertEquals(480f, spec.expandedExtentDp, 0f)
        assertEquals(520f, spec.remainingEditorWidthDp, 0f)
        assertTrue(spec.isSideDocked)
    }

    @Test fun sideWidthNeverConsumesTheMinimumEditorViewport() {
        val spec = SymbolDrawerDockingLayoutResolver.resolve(
            docking = SymbolDrawerDocking.RIGHT,
            window = window(700, 900),
            bottomCollapsedHeightDp = 50f,
            bottomExpandedHeightDp = 250f
        )
        assertEquals(340f, spec.expandedExtentDp, 0f)
        assertEquals(360f, spec.remainingEditorWidthDp, 0f)
    }

    @Test fun narrowWindowClampsExpandedWidthToAvailableSpace() {
        val spec = SymbolDrawerDockingLayoutResolver.resolve(
            docking = SymbolDrawerDocking.LEFT,
            window = window(500, 700),
            bottomCollapsedHeightDp = 50f,
            bottomExpandedHeightDp = 250f
        )
        assertEquals(72f, spec.collapsedExtentDp, 0f)
        assertEquals(140f, spec.expandedExtentDp, 0f)
        assertEquals(360f, spec.remainingEditorWidthDp, 0f)
    }

    private fun window(width: Int, height: Int) = WindowAdaptiveInfo(
        widthDp = width.toFloat(),
        heightDp = height.toFloat()
    )
}
