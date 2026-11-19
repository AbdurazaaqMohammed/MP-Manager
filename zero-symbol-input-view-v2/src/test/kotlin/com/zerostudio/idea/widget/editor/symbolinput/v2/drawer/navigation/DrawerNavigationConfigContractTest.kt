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
/*
 * This file is part of ZeroStudio.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation

import org.junit.Assert.*
import org.junit.Test

class DrawerNavigationConfigContractTest {
    @Test fun indicatorAcceptsBoundaryValues() {
        assertEquals(0f, DrawerIndicatorStyle(heightDp = 0f, widthFraction = 0f, cornerRadiusDp = 0f).widthFraction)
        assertEquals(1f, DrawerIndicatorStyle(widthFraction = 1f).widthFraction)
    }

    @Test fun indicatorRejectsInvalidDimensionsAndFractions() {
        assertThrows(IllegalArgumentException::class.java) { DrawerIndicatorStyle(heightDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerIndicatorStyle(widthFraction = -0.01f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerIndicatorStyle(widthFraction = 1.01f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerIndicatorStyle(cornerRadiusDp = -1f) }
    }

    @Test fun navigationConfigValidatesSizingAndTextScales() {
        assertThrows(IllegalArgumentException::class.java) { DrawerNavigationConfig(itemMinWidthDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerNavigationConfig(itemHorizontalPaddingDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerNavigationConfig(itemVerticalPaddingDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerNavigationConfig(titleTextSizeSp = 0f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerNavigationConfig(selectedTitleTextSizeSp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerNavigationConfig(itemMinWidthDp = Float.POSITIVE_INFINITY) }
    }

    @Test fun navigationModesAndVisibilityFlagsAreIndependent() {
        val config = DrawerNavigationConfig(
            enabled = false,
            placement = DrawerNavigationPlacement.HIDDEN,
            scrollable = false,
            showTitles = false,
            showIcons = true,
            selectedTitleBold = true,
            keepSelectedPageVisible = false,
            showInPeekPanel = true
        )
        assertFalse(config.enabled)
        assertEquals(DrawerNavigationPlacement.HIDDEN, config.placement)
        assertFalse(config.scrollable)
        assertFalse(config.showTitles)
        assertTrue(config.showIcons)
        assertTrue(config.selectedTitleBold)
        assertFalse(config.keepSelectedPageVisible)
        assertTrue(config.showInPeekPanel)
    }
}
