/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowAdaptiveInfoTest {
    @Test fun classifiesCurrentWindowWidthAtCanonicalBreakpoints() {
        assertEquals(WindowSizeClass.COMPACT, WindowAdaptiveInfo(599f, 800f).sizeClass)
        assertEquals(WindowSizeClass.MEDIUM, WindowAdaptiveInfo(600f, 800f).sizeClass)
        assertEquals(WindowSizeClass.MEDIUM, WindowAdaptiveInfo(839f, 800f).sizeClass)
        assertEquals(WindowSizeClass.EXPANDED, WindowAdaptiveInfo(840f, 800f).sizeClass)
    }

    @Test fun insetsReduceAvailableWindowBeforeClassification() {
        val info = WindowAdaptiveInfo(900f, 700f, insets = WindowInsetsDp(left = 40f, right = 40f))
        assertEquals(820f, info.availableWidthDp)
        assertEquals(WindowSizeClass.MEDIUM, info.sizeClass)
    }

    @Test fun pixelFactoryUsesDensity() {
        val info = WindowAdaptiveInfo.fromPixels(1200, 1800, 2f)
        assertEquals(600f, info.widthDp)
        assertEquals(WindowSizeClass.MEDIUM, info.sizeClass)
    }

    @Test fun rotationReclassifiesUsingTheNewWindowWidth() {
        val portrait = WindowAdaptiveInfo(430f, 900f)
        val landscape = WindowAdaptiveInfo(900f, 430f)
        assertEquals(WindowSizeClass.COMPACT, portrait.sizeClass)
        assertEquals(WindowSizeClass.EXPANDED, landscape.sizeClass)
    }

    @Test fun splitScreenUsesTheResizedAppWindowInsteadOfPhysicalDisplayWidth() {
        val fullWindow = WindowAdaptiveInfo(1200f, 800f)
        val splitWindow = WindowAdaptiveInfo(560f, 800f)
        assertEquals(WindowSizeClass.EXPANDED, fullWindow.sizeClass)
        assertEquals(WindowSizeClass.COMPACT, splitWindow.sizeClass)
    }

    @Test fun foldingFeatureCanChangeWithoutChangingWindowDimensions() {
        val flat = WindowAdaptiveInfo(800f, 900f)
        val folded = flat.copy(
            foldingFeature = FoldingFeatureInfo(
                posture = WindowPosture.HALF_OPENED,
                isSeparating = true,
                boundsLeftDp = 390f,
                boundsRightDp = 410f,
                boundsBottomDp = 900f
            )
        )
        assertEquals(flat.widthDp, folded.widthDp)
        assertEquals(WindowPosture.UNKNOWN, flat.foldingFeature.posture)
        assertEquals(WindowPosture.HALF_OPENED, folded.foldingFeature.posture)
    }
}
