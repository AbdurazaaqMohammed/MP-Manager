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
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerStateTest {
    private val anchors = listOf(
        DrawerAnchor("peek", 100, DrawerAnchorState.PEEK),
        DrawerAnchor("half", 500, DrawerAnchorState.HALF_EXPANDED),
        DrawerAnchor("expanded", 900, DrawerAnchorState.EXPANDED)
    )

    @Test fun exposesStablePeekAndExpandedSemantics() {
        val peek = DrawerState("peek", 100, anchors)
        assertTrue(peek.isAtStableAnchor)
        assertTrue(peek.isAtPeekAnchor)
        assertFalse(peek.isExpanded)
        assertEquals(0f, peek.expansionFraction, 0f)

        val expanded = DrawerState("expanded", 900, anchors)
        assertTrue(expanded.isAtStableAnchor)
        assertTrue(expanded.isAtExpandedAnchor)
        assertTrue(expanded.isExpanded)
        assertEquals(1f, expanded.expansionFraction, 0f)
    }

    @Test fun exposesContinuousProgressDuringDragWithoutCallingItStable() {
        val dragging = DrawerState("half", 350, anchors, isDragging = true)
        assertFalse(dragging.isAtStableAnchor)
        assertFalse(dragging.isAtPeekAnchor)
        assertFalse(dragging.isAtExpandedAnchor)
        assertTrue(dragging.isExpanded)
        assertEquals(0.3125f, dragging.expansionFraction, 0.0001f)
    }

    @Test fun clampsProgressAndHandlesSingleAnchorSnapshots() {
        val overExpanded = DrawerState("expanded", 1200, anchors)
        assertEquals(1f, overExpanded.expansionFraction, 0f)

        val single = DrawerState(
            "only",
            120,
            listOf(DrawerAnchor("only", 120, DrawerAnchorState.CUSTOM))
        )
        assertEquals(0f, single.expansionFraction, 0f)
        assertTrue(single.isAtStableAnchor)
    }
}
