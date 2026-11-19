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

import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategies
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategy
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerColorAttributes
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerColors
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerDimensions
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerMotion
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerShape
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerThemeMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerTypography

/** Declarative, feature-neutral configuration consumed by a future DrawerContainer. */
data class DrawerConfig(
    val anchorStrategy: DrawerAnchorStrategy = DrawerAnchorStrategies.existingHeight(),
    val style: DrawerStyle = DrawerStyle(),
    val peekContent: DrawerPanelContent? = null,
    val expandedContent: DrawerPanelContent? = null,
    val enableDrag: Boolean = true,
    val enableNestedScroll: Boolean = true,
    val enableEdgeToEdge: Boolean = false,
    val dismissOnScrimTap: Boolean = true,
    val dismissOnBack: Boolean = true,
    val dragHandleEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = true,
    val navigationConfig: DrawerNavigationConfig = DrawerNavigationConfig(),
    val presentationMode: DrawerPresentationMode = DrawerPresentationMode.BOTTOM_SHEET
) {
    class Builder {
        private var anchorStrategy: DrawerAnchorStrategy = DrawerAnchorStrategies.existingHeight()
        private var style: DrawerStyle = DrawerStyle()
        private var peekContent: DrawerPanelContent? = null
        private var expandedContent: DrawerPanelContent? = null
        private var enableDrag = true
        private var enableNestedScroll = true
        private var enableEdgeToEdge = false
        private var dismissOnScrimTap = true
        private var dismissOnBack = true
        private var dragHandleEnabled = true
        private var accessibilityEnabled = true
        private var navigationConfig = DrawerNavigationConfig()
        private var presentationMode = DrawerPresentationMode.BOTTOM_SHEET

        fun setAnchorStrategy(value: DrawerAnchorStrategy) = apply { anchorStrategy = value }
        fun setPresentationMode(value: DrawerPresentationMode) = apply { presentationMode = value }
        fun setPeekContent(value: DrawerPanelContent?) = apply { peekContent = value }
        fun setExpandedContent(value: DrawerPanelContent?) = apply { expandedContent = value }
        fun setDragEnabled(value: Boolean) = apply { enableDrag = value }
        fun setNestedScrollEnabled(value: Boolean) = apply { enableNestedScroll = value }
        fun setEdgeToEdgeEnabled(value: Boolean) = apply { enableEdgeToEdge = value }
        fun setDismissOnScrimTap(value: Boolean) = apply { dismissOnScrimTap = value }
        fun setDismissOnBack(value: Boolean) = apply { dismissOnBack = value }
        fun setDragHandleEnabled(value: Boolean) = apply { dragHandleEnabled = value }
        fun setAccessibilityEnabled(value: Boolean) = apply { accessibilityEnabled = value }
        fun setNavigationConfig(value: DrawerNavigationConfig) = apply { navigationConfig = value }
        fun setNavigationEnabled(value: Boolean) = apply {
            navigationConfig = navigationConfig.copy(enabled = value)
        }
        fun setNavigationPlacement(value: com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationPlacement) = apply {
            navigationConfig = navigationConfig.copy(placement = value)
        }
        fun setNavigationIndicator(value: com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerIndicatorStyle) = apply {
            navigationConfig = navigationConfig.copy(indicator = value)
        }
        fun setNavigationScrollable(value: Boolean) = apply {
            navigationConfig = navigationConfig.copy(scrollable = value)
        }
        fun setNavigationShowTitles(value: Boolean) = apply {
            navigationConfig = navigationConfig.copy(showTitles = value)
        }

        fun setStyle(value: DrawerStyle) = apply { style = value }
        fun setThemeMode(value: DrawerThemeMode) = apply {
            style = style.copy(themeMode = value)
        }
        fun setFollowHostTheme() = setThemeMode(DrawerThemeMode.FOLLOW_HOST)
        fun setColors(value: DrawerColors) = apply { style = style.copy(colors = value) }
        fun setColorAttributes(value: DrawerColorAttributes) = apply {
            style = style.copy(colorAttributes = value)
        }
        fun setSurfaceColor(value: Int?) = apply {
            style = style.copy(colors = style.colors.copy(surface = value))
        }
        fun setPrimaryTextColor(value: Int?) = apply {
            style = style.copy(colors = style.colors.copy(primaryText = value))
        }
        fun setSecondaryTextColor(value: Int?) = apply {
            style = style.copy(colors = style.colors.copy(secondaryText = value))
        }
        fun setAccentColor(value: Int?) = apply {
            style = style.copy(colors = style.colors.copy(accent = value))
        }
        fun setOutlineColor(value: Int?) = apply {
            style = style.copy(colors = style.colors.copy(outline = value))
        }
        fun setScrimColor(value: Int?) = apply {
            style = style.copy(colors = style.colors.copy(scrim = value))
        }
        fun setDimensions(value: DrawerDimensions) = apply { style = style.copy(dimensions = value) }
        fun setTypography(value: DrawerTypography) = apply { style = style.copy(typography = value) }
        fun setMotion(value: DrawerMotion) = apply { style = style.copy(motion = value) }
        fun setShape(value: DrawerShape) = apply { style = style.copy(shape = value) }
        fun setElevationDp(value: Float) = apply { style = style.copy(elevationDp = value) }
        fun setScrimEnabled(value: Boolean) = apply { style = style.copy(drawScrim = value) }
        fun setClipContentToShape(value: Boolean) = apply { style = style.copy(clipContentToShape = value) }

        fun setPeekHeightDp(value: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(peekHeightDp = value))
        }
        fun setExpandedMaxHeightDp(value: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(expandedMaxHeightDp = value))
        }
        fun setCornerRadiusDp(value: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(cornerRadiusDp = value),
                shape = style.shape.copy(topStartRadiusDp = value, topEndRadiusDp = value))
        }
        fun setHorizontalPaddingDp(value: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(horizontalPaddingDp = value))
        }
        fun setVerticalPaddingDp(value: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(verticalPaddingDp = value))
        }
        fun setPanelSpacingDp(value: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(panelSpacingDp = value))
        }
        fun setHandleSizeDp(width: Float, height: Float) = apply {
            style = style.copy(dimensions = style.dimensions.copy(
                handleWidthDp = width, handleHeightDp = height
            ))
        }
        fun setTitleTextSizeSp(value: Float) = apply {
            style = style.copy(typography = style.typography.copy(titleSizeSp = value))
        }
        fun setBodyTextSizeSp(value: Float) = apply {
            style = style.copy(typography = style.typography.copy(bodySizeSp = value))
        }
        fun setCaptionTextSizeSp(value: Float) = apply {
            style = style.copy(typography = style.typography.copy(captionSizeSp = value))
        }
        fun setFontFamily(value: String?) = apply {
            style = style.copy(typography = style.typography.copy(fontFamily = value))
        }
        fun setCornerRadiiDp(topStart: Float, topEnd: Float, bottomStart: Float, bottomEnd: Float) = apply {
            style = style.copy(shape = style.shape.copy(
                topStartRadiusDp = topStart, topEndRadiusDp = topEnd,
                bottomStartRadiusDp = bottomStart, bottomEndRadiusDp = bottomEnd
            ))
        }
        fun setOutlineWidthDp(value: Float) = apply {
            style = style.copy(shape = style.shape.copy(outlineWidthDp = value))
        }
        fun setFlingVelocityDp(minimum: Float, maximum: Float) = apply {
            style = style.copy(motion = style.motion.copy(
                minimumFlingVelocityDp = minimum, maximumFlingVelocityDp = maximum
            ))
        }
        fun setSpringEnabled(value: Boolean) = apply {
            style = style.copy(motion = style.motion.copy(enableSpring = value))
        }
        fun setSettleDurationMs(value: Long) = apply {
            style = style.copy(motion = style.motion.copy(settleDurationMs = value))
        }
        fun setDragResistance(value: Float) = apply {
            style = style.copy(motion = style.motion.copy(dragResistance = value))
        }

        fun build(): DrawerConfig = DrawerConfig(
            anchorStrategy, style, peekContent, expandedContent, enableDrag,
            enableNestedScroll, enableEdgeToEdge, dismissOnScrimTap, dismissOnBack,
            dragHandleEnabled, accessibilityEnabled, navigationConfig, presentationMode
        )
    }

    companion object {
        @JvmStatic fun builder() = Builder()
    }
}
