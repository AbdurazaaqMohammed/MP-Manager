/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style

import org.junit.Assert.*
import org.junit.Test

class DrawerStyleContractTest {
    @Test fun colorOverlayUsesNonNullOverridePerRole() {
        val base = DrawerColors(1, 2, 3, 4, 5, 6)
        val result = base.overlay(DrawerColors(surface = 10, accent = 40, scrim = 60))
        assertEquals(DrawerColors(10, 2, 3, 40, 5, 60), result)
        assertEquals(DrawerColors(1, 2, 3, 4, 5, 6), base.overlay(DrawerColors()))
    }

    @Test fun dimensionsAcceptZeroAndRejectInvalidRanges() {
        assertEquals(0f, DrawerDimensions(peekHeightDp = 0f, expandedMaxHeightDp = 0f).peekHeightDp)
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(peekHeightDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(peekHeightDp = 60f, expandedMaxHeightDp = 59f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(cornerRadiusDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(horizontalPaddingDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(verticalPaddingDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(panelSpacingDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(handleWidthDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(handleHeightDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerDimensions(cornerRadiusDp = Float.POSITIVE_INFINITY) }
    }

    @Test fun typographyRequiresPositiveFiniteSizes() {
        assertThrows(IllegalArgumentException::class.java) { DrawerTypography(titleSizeSp = 0f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerTypography(bodySizeSp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerTypography(captionSizeSp = Float.NaN) }
    }

    @Test fun motionValidatesDurationResistanceAndVelocityOrdering() {
        assertThrows(IllegalArgumentException::class.java) { DrawerMotion(settleDurationMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { DrawerMotion(dragResistance = 0f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerMotion(dragResistance = 1.1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerMotion(minimumFlingVelocityDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerMotion(maximumFlingVelocityDp = 10f, minimumFlingVelocityDp = 11f) }
    }

    @Test fun shapeValidatesEveryRadiusAndOutline() {
        assertThrows(IllegalArgumentException::class.java) { DrawerShape(topStartRadiusDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerShape(topEndRadiusDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerShape(bottomStartRadiusDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerShape(bottomEndRadiusDp = -1f) }
        assertThrows(IllegalArgumentException::class.java) { DrawerShape(outlineWidthDp = -1f) }
    }

    @Test fun styleValidatesElevationAndRetainsIndependentTokens() {
        assertThrows(IllegalArgumentException::class.java) { DrawerStyle(elevationDp = -0.1f) }
        val style = DrawerStyle(
            themeMode = DrawerThemeMode.DARK,
            colors = DrawerColors(surface = 0xff000000.toInt()),
            drawScrim = false,
            clipContentToShape = false
        )
        assertEquals(DrawerThemeMode.DARK, style.themeMode)
        assertFalse(style.drawScrim)
        assertFalse(style.clipContentToShape)
        assertEquals(0xff000000.toInt(), style.colors.surface)
    }
}
