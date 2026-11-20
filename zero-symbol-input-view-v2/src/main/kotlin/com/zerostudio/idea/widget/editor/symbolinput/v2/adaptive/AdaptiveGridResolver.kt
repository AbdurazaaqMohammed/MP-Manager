/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

data class AdaptiveGridSpec(
    val columnCount: Int,
    val horizontalSpacingDp: Int,
    val verticalSpacingDp: Int = horizontalSpacingDp
) {
    init {
        require(columnCount >= 1)
        require(horizontalSpacingDp in -24..48)
        require(verticalSpacingDp in -24..48)
    }
}

/** Resolves a grid that can reflow inside a fixed-height host without changing host geometry. */
object AdaptiveGridResolver {
    fun resolve(
        window: WindowAdaptiveInfo,
        preferredColumns: Int,
        minimumCellWidthDp: Float,
        baseHorizontalSpacingDp: Int,
        adaptive: Boolean = true,
        baseVerticalSpacingDp: Int = baseHorizontalSpacingDp,
        lockColumnCount: Boolean = false
    ): AdaptiveGridSpec {
        require(preferredColumns >= 1)
        require(minimumCellWidthDp.isFinite() && minimumCellWidthDp > 0f)
        // Settings are validated at persistence boundaries, but DrawerConfig is
        // also a public runtime input and may be supplied by older hosts. Clamp
        // it here so a layout callback can never crash the UI thread because a
        // host passed an out-of-range spacing value.
        val safeHorizontalSpacing = baseHorizontalSpacingDp.coerceIn(-24, 48)
        val safeVerticalSpacing = baseVerticalSpacingDp.coerceIn(-24, 48)
        if (!adaptive) return AdaptiveGridSpec(
            preferredColumns,
            safeHorizontalSpacing,
            safeVerticalSpacing
        )

        val spacingAdjustment = when (window.sizeClass) {
            WindowSizeClass.COMPACT -> 0
            WindowSizeClass.MEDIUM -> 1
            WindowSizeClass.EXPANDED -> 3
        }
        val spacing = (safeHorizontalSpacing + spacingAdjustment).coerceIn(-24, 48)
        val verticalSpacing = (safeVerticalSpacing + spacingAdjustment).coerceIn(-24, 48)
        val effectiveSpacing = spacing.coerceAtLeast(0)
        val columns = if (lockColumnCount) preferredColumns else {
            (window.availableWidthDp / (minimumCellWidthDp + effectiveSpacing))
                .toInt()
                .coerceIn(1, preferredColumns)
        }
        return AdaptiveGridSpec(columns, spacing, verticalSpacing)
    }
}
