/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveGridResolverTest {
    @Test fun compactWindowsReflowToFitMinimumCellWidth() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(360f, 800f),
            preferredColumns = 12,
            minimumCellWidthDp = 40f,
            baseHorizontalSpacingDp = 2
        )
        assertEquals(8, spec.columnCount)
        assertEquals(2, spec.horizontalSpacingDp)
        assertEquals(2, spec.verticalSpacingDp)
    }

    @Test fun expandedWindowsUseMoreColumnsWithoutExceedingConfiguredMaximum() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(1000f, 800f),
            preferredColumns = 18,
            minimumCellWidthDp = 40f,
            baseHorizontalSpacingDp = 2
        )
        assertEquals(18, spec.columnCount)
        assertEquals(5, spec.horizontalSpacingDp)
        assertEquals(5, spec.verticalSpacingDp)
    }

    @Test fun fixedDimensionModePreservesConfiguredColumnsAndSpacing() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(360f, 800f),
            preferredColumns = 12,
            minimumCellWidthDp = 40f,
            baseHorizontalSpacingDp = -8,
            adaptive = false
        )
        assertEquals(12, spec.columnCount)
        assertEquals(-8, spec.horizontalSpacingDp)
        assertEquals(-8, spec.verticalSpacingDp)
    }


    @Test fun horizontalAndVerticalSpacingCanUseDifferentBaseValues() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(1000f, 800f),
            preferredColumns = 12,
            minimumCellWidthDp = 40f,
            baseHorizontalSpacingDp = 2,
            baseVerticalSpacingDp = 6
        )
        assertEquals(5, spec.horizontalSpacingDp)
        assertEquals(9, spec.verticalSpacingDp)
    }


    @Test fun explicitColumnPreferenceIsNotSilentlyReducedOnCompactWindows() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(360f, 800f),
            preferredColumns = 6,
            minimumCellWidthDp = 48f,
            baseHorizontalSpacingDp = 2,
            lockColumnCount = true
        )
        assertEquals(6, spec.columnCount)
    }

    @Test fun acceptsFullPersistedSpacingRangeWithoutCrashingDuringLayout() {
        val minimum = AdaptiveGridResolver.resolve(
            WindowAdaptiveInfo(360f, 800f), 10, 40f, -24,
            baseVerticalSpacingDp = -24, lockColumnCount = true
        )
        assertEquals(-24, minimum.horizontalSpacingDp)
        assertEquals(-24, minimum.verticalSpacingDp)
        val maximum = AdaptiveGridResolver.resolve(
            WindowAdaptiveInfo(1000f, 800f), 10, 40f, 48,
            baseVerticalSpacingDp = 48, lockColumnCount = true
        )
        assertEquals(48, maximum.horizontalSpacingDp)
        assertEquals(48, maximum.verticalSpacingDp)
    }

    @Test fun outOfRangeRuntimeSpacingIsClampedInsteadOfCrashingLayout() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(360f, 800f),
            preferredColumns = 10,
            minimumCellWidthDp = 40f,
            baseHorizontalSpacingDp = -100,
            baseVerticalSpacingDp = 100,
            lockColumnCount = true
        )
        assertEquals(-24, spec.horizontalSpacingDp)
        assertEquals(48, spec.verticalSpacingDp)
    }

    @Test fun gridAlwaysKeepsAtLeastOneColumn() {
        val spec = AdaptiveGridResolver.resolve(
            window = WindowAdaptiveInfo(100f, 800f),
            preferredColumns = 12,
            minimumCellWidthDp = 48f,
            baseHorizontalSpacingDp = 24
        )
        assertEquals(1, spec.columnCount)
    }
}
