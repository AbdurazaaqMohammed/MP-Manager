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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config

import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.ThreeStageAnchorStrategy
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerPresentationMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.preset.DrawerPreset
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.preset.DrawerPresets
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerIndicatorShape
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerIndicatorStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationPlacement
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerConfigTest {
    @Test fun builderExposesAnchorAndInteractionSetters() {
        val config = DrawerConfig.builder()
            .setAnchorStrategy(ThreeStageAnchorStrategy())
            .setDragEnabled(false)
            .setNestedScrollEnabled(false)
            .setDismissOnBack(false)
            .setDismissOnScrimTap(false)
            .setDragHandleEnabled(false)
            .build()

        assertTrue(config.anchorStrategy is ThreeStageAnchorStrategy)
        assertFalse(config.enableDrag)
        assertFalse(config.enableNestedScroll)
        assertFalse(config.dismissOnBack)
        assertFalse(config.dismissOnScrimTap)
        assertFalse(config.dragHandleEnabled)
    }

    @Test fun builderSupportsGranularStyleSetters() {
        val config = DrawerConfig.builder()
            .setThemeMode(DrawerThemeMode.DARK)
            .setSurfaceColor(0xff101010.toInt())
            .setPrimaryTextColor(0xffffffff.toInt())
            .setCornerRadiusDp(28f)
            .setTitleTextSizeSp(20f)
            .setSettleDurationMs(300L)
            .setDragResistance(0.6f)
            .build()

        assertEquals(DrawerThemeMode.DARK, config.style.themeMode)
        assertEquals(0xff101010.toInt(), config.style.colors.surface)
        assertEquals(0xffffffff.toInt(), config.style.colors.primaryText)
        assertEquals(28f, config.style.dimensions.cornerRadiusDp)
        assertEquals(20f, config.style.typography.titleSizeSp)
        assertEquals(300L, config.style.motion.settleDurationMs)
        assertEquals(0.6f, config.style.motion.dragResistance)
    }

    @Test fun navigationOptionsAreConfigurableIndependently() {
        val config = DrawerConfig.builder()
            .setNavigationEnabled(true)
            .setNavigationPlacement(DrawerNavigationPlacement.BOTTOM)
            .setNavigationIndicator(DrawerIndicatorStyle(
                shape = DrawerIndicatorShape.PILL, heightDp = 4f, widthFraction = 0.8f
            ))
            .setNavigationScrollable(false)
            .setNavigationShowTitles(false)
            .build()

        assertTrue(config.navigationConfig.enabled)
        assertEquals(DrawerNavigationPlacement.BOTTOM, config.navigationConfig.placement)
        assertEquals(DrawerIndicatorShape.PILL, config.navigationConfig.indicator.shape)
        assertFalse(config.navigationConfig.scrollable)
        assertFalse(config.navigationConfig.showTitles)
    }

    @Test fun presetsRemainDistinctAndOverrideable() {
        val compact = DrawerPresets.config(DrawerPreset.COMPACT)
        val full = DrawerPresets.config(DrawerPreset.FULL_SCREEN)
        assertTrue(compact.style.dimensions.expandedMaxHeightDp < full.style.dimensions.expandedMaxHeightDp)
        assertEquals(DrawerPresentationMode.FULL_SCREEN, full.presentationMode)
        assertEquals(0f, full.style.shape.topStartRadiusDp)
        assertEquals(0f, full.style.shape.topEndRadiusDp)
        assertFalse(full.style.drawScrim)
        assertTrue(DrawerConfig.builder().setStyle(compact.style).setPeekHeightDp(72f)
            .build().style.dimensions.peekHeightDp == 72f)
    }
}
