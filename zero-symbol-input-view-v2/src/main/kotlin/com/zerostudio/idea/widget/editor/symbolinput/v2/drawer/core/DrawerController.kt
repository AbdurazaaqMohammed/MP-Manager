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

import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerPresentationMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPage
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerColors
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerDimensions
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerMotion
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerShape
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerThemeMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerTypography

/**
 * Public runtime control surface. A concrete DrawerContainer will bind this contract
 * to Android measurement, nested scrolling, touch dispatch, page hosting and animation.
 */
interface DrawerController {
    fun expand(animated: Boolean = true)
    fun collapse(animated: Boolean = true)
    fun moveToAnchor(anchorId: String, animated: Boolean = true)

    /** Re-measure panel content and resolve anchors after asynchronous content-size changes. */
    fun refreshAnchors()

    /** Replace the active strategy and recalculate anchors against current measurements. */
    fun setAnchorStrategy(strategy: DrawerAnchorStrategy)
    fun setPresentationMode(mode: DrawerPresentationMode)

    /** Apply a complete immutable configuration atomically. */
    fun applyConfig(config: DrawerConfig)

    /** Runtime visual/style setters. Null color roles continue to inherit host theme values. */
    fun setStyle(style: DrawerStyle)
    fun setThemeMode(mode: DrawerThemeMode)
    fun setColors(colors: DrawerColors)
    fun setDimensions(dimensions: DrawerDimensions)
    fun setTypography(typography: DrawerTypography)
    fun setMotion(motion: DrawerMotion)
    fun setShape(shape: DrawerShape)

    /** Independently replace either panel; passing null removes that panel. */
    fun setPeekContent(content: DrawerPanelContent?)
    fun setExpandedContent(content: DrawerPanelContent?)

    /** Generic page navigation appearance; the navigation widget itself is optional. */
    fun setTitle(title: CharSequence?)
    fun setPages(pages: List<DrawerPage>, preserveSelection: Boolean = true)
    fun selectPage(pageId: String): Boolean
    fun setNavigationConfig(config: DrawerNavigationConfig)

    fun setDragEnabled(enabled: Boolean)
    fun setNestedScrollEnabled(enabled: Boolean)
    fun setEdgeToEdgeEnabled(enabled: Boolean)
    fun setDismissOnScrimTap(enabled: Boolean)
    fun setDismissOnBack(enabled: Boolean)
    fun setDragHandleEnabled(enabled: Boolean)
    fun setAccessibilityEnabled(enabled: Boolean)

    fun currentState(): DrawerState?
    fun addStateListener(listener: (DrawerState) -> Unit)
    fun removeStateListener(listener: (DrawerState) -> Unit)
}
