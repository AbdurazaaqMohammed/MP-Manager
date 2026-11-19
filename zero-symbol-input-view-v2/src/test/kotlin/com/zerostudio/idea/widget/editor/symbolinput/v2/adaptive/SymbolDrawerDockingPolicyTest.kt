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

import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.LargeScreenLayoutMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SideDockEdge
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolDrawerDockingPolicyTest {
    @Test fun automaticModeUsesWindowWidthNotDeviceIdentity() {
        val settings = SymbolDrawerSettings()
        assertEquals(SymbolDrawerDocking.BOTTOM, SymbolDrawerDockingPolicy.resolve(settings, window(599)))
        assertEquals(SymbolDrawerDocking.RIGHT, SymbolDrawerDockingPolicy.resolve(settings, window(600)))
    }

    @Test fun automaticModeUsesAvailableWidthAfterInsets() {
        val settings = SymbolDrawerSettings()
        val insetWindow = WindowAdaptiveInfo(
            widthDp = 620f,
            heightDp = 900f,
            insets = com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowInsetsDp(
                left = 16f,
                right = 16f
            )
        )
        assertEquals(
            SymbolDrawerDocking.BOTTOM,
            SymbolDrawerDockingPolicy.resolve(settings, insetWindow)
        )
    }

    @Test fun manualBottomOverridesLargeWindow() {
        val settings = SymbolDrawerSettings(largeScreenLayoutMode = LargeScreenLayoutMode.BOTTOM)
        assertEquals(SymbolDrawerDocking.BOTTOM, SymbolDrawerDockingPolicy.resolve(settings, window(1000)))
    }

    @Test fun manualSideAndEdgeAreRespectedOnCompactWindow() {
        val settings = SymbolDrawerSettings(
            largeScreenLayoutMode = LargeScreenLayoutMode.SIDE,
            sideDockEdge = SideDockEdge.LEFT
        )
        assertEquals(SymbolDrawerDocking.LEFT, SymbolDrawerDockingPolicy.resolve(settings, window(400)))
    }

    @Test fun sideWidthIsBounded() {
        assertEquals(56, SymbolDrawerDockingPolicy.sideWidthDp(20))
        assertEquals(88, SymbolDrawerDockingPolicy.sideWidthDp(200))
    }

    private fun window(width: Int) = WindowAdaptiveInfo.fromPixels(
        widthPx = width,
        heightPx = 1200,
        density = 1f
    )
}
