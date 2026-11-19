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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style

/** Theme behavior of a drawer. FOLLOW_HOST is the default and never overrides the Activity theme. */
enum class DrawerThemeMode { FOLLOW_HOST, LIGHT, DARK }

data class DrawerColorAttributes(
    val surfaceAttr: Int = com.google.android.material.R.attr.colorSurface,
    val primaryTextAttr: Int = com.google.android.material.R.attr.colorOnSurface,
    val secondaryTextAttr: Int = com.google.android.material.R.attr.colorOnSurfaceVariant,
    val accentAttr: Int = androidx.appcompat.R.attr.colorPrimary
)

data class DrawerColors(
    val surface: Int? = null,
    val primaryText: Int? = null,
    val secondaryText: Int? = null,
    val accent: Int? = null,
    val outline: Int? = null,
    val scrim: Int? = null
) {
    /** Explicit values override the host theme; null values inherit from it. */
    fun overlay(override: DrawerColors): DrawerColors = DrawerColors(
        surface = override.surface ?: surface,
        primaryText = override.primaryText ?: primaryText,
        secondaryText = override.secondaryText ?: secondaryText,
        accent = override.accent ?: accent,
        outline = override.outline ?: outline,
        scrim = override.scrim ?: scrim
    )
}

data class DrawerDimensions(
    val peekHeightDp: Float = 56f,
    val expandedMaxHeightDp: Float = 640f,
    val cornerRadiusDp: Float = 24f,
    val horizontalPaddingDp: Float = 16f,
    val verticalPaddingDp: Float = 12f,
    val panelSpacingDp: Float = 8f,
    val handleWidthDp: Float = 36f,
    val handleHeightDp: Float = 4f
) {
    init {
        require(peekHeightDp.isFinite() && expandedMaxHeightDp.isFinite())
        require(peekHeightDp >= 0f && expandedMaxHeightDp >= peekHeightDp)
        require(cornerRadiusDp.isFinite() && horizontalPaddingDp.isFinite())
        require(cornerRadiusDp >= 0f && horizontalPaddingDp >= 0f)
        require(verticalPaddingDp.isFinite() && panelSpacingDp.isFinite())
        require(verticalPaddingDp >= 0f && panelSpacingDp >= 0f)
        require(handleWidthDp.isFinite() && handleHeightDp.isFinite())
        require(handleWidthDp >= 0f && handleHeightDp >= 0f)
    }
}

data class DrawerTypography(
    val titleSizeSp: Float = 18f,
    val bodySizeSp: Float = 14f,
    val captionSizeSp: Float = 12f,
    val titleStyle: Int = android.graphics.Typeface.BOLD,
    val bodyStyle: Int = android.graphics.Typeface.NORMAL,
    val fontFamily: String? = null
) {
    init {
        require(titleSizeSp.isFinite() && bodySizeSp.isFinite() && captionSizeSp.isFinite())
        require(titleSizeSp > 0f && bodySizeSp > 0f && captionSizeSp > 0f)
    }
}

data class DrawerMotion(
    val settleDurationMs: Long = 260L,
    val dragResistance: Float = 0.72f,
    val minimumFlingVelocityDp: Float = 900f,
    val maximumFlingVelocityDp: Float = 6000f,
    val enableSpring: Boolean = true
) {
    init {
        require(settleDurationMs >= 0L)
        require(dragResistance.isFinite() && dragResistance in 0.05f..1f)
        require(minimumFlingVelocityDp.isFinite() && minimumFlingVelocityDp >= 0f)
        require(maximumFlingVelocityDp.isFinite() && maximumFlingVelocityDp >= minimumFlingVelocityDp)
    }
}

data class DrawerShape(
    val topStartRadiusDp: Float = 24f,
    val topEndRadiusDp: Float = 24f,
    val bottomStartRadiusDp: Float = 0f,
    val bottomEndRadiusDp: Float = 0f,
    val outlineWidthDp: Float = 0f
) {
    init {
        require(topStartRadiusDp.isFinite() && topEndRadiusDp.isFinite())
        require(topStartRadiusDp >= 0f && topEndRadiusDp >= 0f)
        require(bottomStartRadiusDp.isFinite() && bottomEndRadiusDp.isFinite())
        require(bottomStartRadiusDp >= 0f && bottomEndRadiusDp >= 0f)
        require(outlineWidthDp.isFinite() && outlineWidthDp >= 0f)
    }
}

/** Complete visual token set. Values are immutable and safe to share between drawers. */
data class DrawerStyle(
    val themeMode: DrawerThemeMode = DrawerThemeMode.FOLLOW_HOST,
    val colorAttributes: DrawerColorAttributes = DrawerColorAttributes(),
    val colors: DrawerColors = DrawerColors(),
    val dimensions: DrawerDimensions = DrawerDimensions(),
    val typography: DrawerTypography = DrawerTypography(),
    val motion: DrawerMotion = DrawerMotion(),
    val shape: DrawerShape = DrawerShape(),
    val elevationDp: Float = 12f,
    val drawScrim: Boolean = true,
    val clipContentToShape: Boolean = true
) {
    init { require(elevationDp.isFinite() && elevationDp >= 0f) }
}
