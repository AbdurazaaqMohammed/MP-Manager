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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation

/** Placement is independent of the page host and may be changed by a consumer. */
enum class DrawerNavigationPlacement { TOP, BOTTOM, HIDDEN }

/** Visual indicator contract for TabLayout-like navigation widgets. */
enum class DrawerIndicatorShape { LINE, PILL, NONE }

data class DrawerIndicatorStyle(
    val shape: DrawerIndicatorShape = DrawerIndicatorShape.LINE,
    val heightDp: Float = 3f,
    val widthFraction: Float = 0.72f,
    val cornerRadiusDp: Float = 3f,
    val color: Int? = null,
    val animate: Boolean = true
) {
    init {
        require(heightDp.isFinite() && heightDp >= 0f)
        require(widthFraction.isFinite() && widthFraction in 0f..1f)
        require(cornerRadiusDp.isFinite() && cornerRadiusDp >= 0f)
    }
}

/** Configuration for optional page/group navigation; no dependency on SymbolGroup. */
data class DrawerNavigationConfig(
    val enabled: Boolean = true,
    val placement: DrawerNavigationPlacement = DrawerNavigationPlacement.TOP,
    val indicator: DrawerIndicatorStyle = DrawerIndicatorStyle(),
    val scrollable: Boolean = true,
    val showTitles: Boolean = true,
    val showIcons: Boolean = false,
    val itemMinWidthDp: Float = 64f,
    val itemHorizontalPaddingDp: Float = 12f,
    val itemVerticalPaddingDp: Float = 10f,
    val titleTextSizeSp: Float = 14f,
    val selectedTitleTextSizeSp: Float = 14f,
    val selectedTitleBold: Boolean = false,
    val keepSelectedPageVisible: Boolean = true,
    val showInPeekPanel: Boolean = false
) {
    init {
        require(itemMinWidthDp.isFinite() && itemMinWidthDp >= 0f)
        require(itemHorizontalPaddingDp.isFinite() && itemVerticalPaddingDp.isFinite())
        require(itemHorizontalPaddingDp >= 0f && itemVerticalPaddingDp >= 0f)
        require(titleTextSizeSp.isFinite() && selectedTitleTextSizeSp.isFinite())
        require(titleTextSizeSp > 0f && selectedTitleTextSizeSp > 0f)
    }
}
