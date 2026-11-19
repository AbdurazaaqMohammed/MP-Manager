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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.preset

import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerPresentationMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategies
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerDimensions
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerShape
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerStyle

/** Named baseline layouts. Every preset remains overridable through DrawerConfig.Builder. */
enum class DrawerPreset { STANDARD, COMPACT, FULL_SCREEN, SPLIT_PANEL }

object DrawerPresets {
    @JvmStatic
    fun config(preset: DrawerPreset): DrawerConfig {
        val builder = DrawerConfig.builder()
        when (preset) {
            DrawerPreset.STANDARD -> builder
                .setAnchorStrategy(DrawerAnchorStrategies.existingHeight())
                .setStyle(DrawerStyle())
            DrawerPreset.COMPACT -> builder
                .setAnchorStrategy(DrawerAnchorStrategies.contentBased())
                .setStyle(DrawerStyle(
                    dimensions = DrawerDimensions(
                        peekHeightDp = 48f,
                        expandedMaxHeightDp = 420f,
                        cornerRadiusDp = 20f,
                        horizontalPaddingDp = 12f,
                        verticalPaddingDp = 8f,
                        panelSpacingDp = 6f
                    ),
                    shape = DrawerShape(20f, 20f)
                ))
            DrawerPreset.FULL_SCREEN -> builder
                .setPresentationMode(DrawerPresentationMode.FULL_SCREEN)
                .setAnchorStrategy(DrawerAnchorStrategies.threeStage(0.5f, true))
                .setStyle(DrawerStyle(
                    dimensions = DrawerDimensions(
                        peekHeightDp = 64f,
                        expandedMaxHeightDp = 1000f,
                        cornerRadiusDp = 0f,
                        horizontalPaddingDp = 16f,
                        verticalPaddingDp = 12f
                    ),
                    shape = DrawerShape(0f, 0f),
                    drawScrim = false,
                    clipContentToShape = false,
                    elevationDp = 0f
                ))
            DrawerPreset.SPLIT_PANEL -> builder
                .setAnchorStrategy(DrawerAnchorStrategies.threeStage())
                .setStyle(DrawerStyle(
                    dimensions = DrawerDimensions(
                        peekHeightDp = 56f,
                        expandedMaxHeightDp = 720f,
                        cornerRadiusDp = 24f,
                        horizontalPaddingDp = 16f,
                        verticalPaddingDp = 12f,
                        panelSpacingDp = 12f
                    )
                ))
        }
        return builder.build()
    }
}
